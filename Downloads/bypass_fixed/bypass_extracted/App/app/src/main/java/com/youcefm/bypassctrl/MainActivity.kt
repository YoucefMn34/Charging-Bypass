package com.youcefm.bypassctrl

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.TransitionManager
import com.youcefm.bypassctrl.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val lastValues = mutableMapOf<Int, Float>()
    private var isLogExpanded = false
    private val prefs by lazy { getSharedPreferences("blitz_prefs", MODE_PRIVATE) }
    private lateinit var qsToastReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkRoot()
        requestNotificationPermission()
        setupToggleButton()
        setupLogToggle()
        setupThresholdSlider()
        setupTempThresholdSlider()  // NEW: Temperature threshold UI
        setupPullToRefresh()
        setupQsToastReceiver()
        startMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(qsToastReceiver) } catch (_: Exception) {}
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale dialog then request
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("Bypass alerts need notifications to show when the app is closed.")
                        .setPositiveButton("Allow") { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                100
                            )
                        }
                        .setNegativeButton("Deny", null)
                        .show()
                }
                else -> {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        100
                    )
                }
            }
        }
    }
    private fun checkRoot() {
        val result = ShellExecutor.exec("id")
        if (!result.contains("uid=0")) {
            Toast.makeText(this, "Root access not granted!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupToggleButton() {
        binding.btnToggle.setOnClickListener {
            triggerHaptic()
            binding.btnToggle.isEnabled = false
            val current = binding.tvMode.text.toString()
            val targetBypass = current != "BYPASS"

            lifecycleScope.launch(Dispatchers.IO) {
                BatteryMonitor.setMode(targetBypass)
                delay(500)
                withContext(Dispatchers.Main) {
                    binding.btnToggle.isEnabled = true
                }
            }
        }
    }

    private fun setupLogToggle() {
        binding.tvLog.visibility = View.GONE
        binding.logArrow.rotation = 0f

        binding.logHeader.setOnClickListener {
            isLogExpanded = !isLogExpanded
            TransitionManager.beginDelayedTransition(binding.cardLog)
            binding.tvLog.visibility = if (isLogExpanded) View.VISIBLE else View.GONE
            binding.logArrow.animate()
                .rotation(if (isLogExpanded) 180f else 0f)
                .setDuration(200)
                .start()
        }
    }

    // === PERCENTAGE THRESHOLD (existing) ===
    private fun setupThresholdSlider() {
        val enabled = prefs.getBoolean("threshold_enabled", false)
        val value = prefs.getInt("threshold_value", 80)

        binding.switchThreshold.isChecked = enabled
        binding.sliderThreshold.value = value.toFloat()
        binding.tvThresholdValue.text = "$value%"

        // Sync with daemon on startup
        BatteryMonitor.setThreshold(enabled, value)

        binding.sliderThreshold.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvThresholdValue.text = "$intVal%"
            prefs.edit().putInt("threshold_value", intVal).apply()
            BatteryMonitor.setThreshold(binding.switchThreshold.isChecked, intVal)
        }

        binding.switchThreshold.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("threshold_enabled", isChecked).apply()
            binding.sliderThreshold.isEnabled = isChecked
            binding.tvThresholdValue.alpha = if (isChecked) 1.0f else 0.5f
            val currentValue = prefs.getInt("threshold_value", 80)
            BatteryMonitor.setThreshold(isChecked, currentValue)
        }

        binding.sliderThreshold.isEnabled = enabled
        binding.tvThresholdValue.alpha = if (enabled) 1.0f else 0.5f
    }

    // === TEMPERATURE THRESHOLD (NEW) ===
    private fun setupTempThresholdSlider() {
        val enabled = prefs.getBoolean("temp_threshold_enabled", false)
        val trigger = prefs.getInt("temp_trigger_value", 40)
        val resume = prefs.getInt("temp_resume_value", 38)

        binding.switchTempThreshold.isChecked = enabled
        binding.sliderTempTrigger.value = trigger.toFloat()
        binding.sliderTempResume.value = resume.toFloat()
        binding.tvTempTriggerValue.text = "${trigger}°C"
        binding.tvTempResumeValue.text = "${resume}°C"

        // Sync with daemon on startup
        BatteryMonitor.setTempThreshold(enabled, trigger, resume)

        // Trigger slider listener
        binding.sliderTempTrigger.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvTempTriggerValue.text = "${intVal}°C"
            prefs.edit().putInt("temp_trigger_value", intVal).apply()

            // Ensure trigger >= resume + 2
            val resumeVal = binding.sliderTempResume.value.toInt()
            if (intVal < resumeVal + 2) {
                binding.sliderTempResume.value = (intVal - 2).coerceAtLeast(30).toFloat()
                binding.tvTempResumeValue.text = "${binding.sliderTempResume.value.toInt()}°C"
            }

            BatteryMonitor.setTempThreshold(
                binding.switchTempThreshold.isChecked,
                binding.sliderTempTrigger.value.toInt(),
                binding.sliderTempResume.value.toInt()
            )
        }

        // Resume slider listener
        binding.sliderTempResume.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvTempResumeValue.text = "${intVal}°C"
            prefs.edit().putInt("temp_resume_value", intVal).apply()

            // Ensure resume <= trigger - 2
            val triggerVal = binding.sliderTempTrigger.value.toInt()
            if (intVal > triggerVal - 2) {
                binding.sliderTempTrigger.value = (intVal + 2).coerceAtMost(60).toFloat()
                binding.tvTempTriggerValue.text = "${binding.sliderTempTrigger.value.toInt()}°C"
            }

            BatteryMonitor.setTempThreshold(
                binding.switchTempThreshold.isChecked,
                binding.sliderTempTrigger.value.toInt(),
                binding.sliderTempResume.value.toInt()
            )
        }

        // Enable switch listener
        binding.switchTempThreshold.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("temp_threshold_enabled", isChecked).apply()
            binding.sliderTempTrigger.isEnabled = isChecked
            binding.sliderTempResume.isEnabled = isChecked
            binding.tvTempTriggerValue.alpha = if (isChecked) 1.0f else 0.5f
            binding.tvTempResumeValue.alpha = if (isChecked) 1.0f else 0.5f
            val currentTrigger = prefs.getInt("temp_trigger_value", 40)
            val currentResume = prefs.getInt("temp_resume_value", 38)
            BatteryMonitor.setTempThreshold(isChecked, currentTrigger, currentResume)
        }

        binding.sliderTempTrigger.isEnabled = enabled
        binding.sliderTempResume.isEnabled = enabled
        binding.tvTempTriggerValue.alpha = if (enabled) 1.0f else 0.5f
        binding.tvTempResumeValue.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.md_theme_primary)
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                refreshData()
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupQsToastReceiver() {
        qsToastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val mode = intent.getStringExtra("mode") ?: return
                Toast.makeText(context, "QS Tile: Switched to $mode", Toast.LENGTH_SHORT).show()
            }
        }
        val filter = IntentFilter("com.youcefm.bypassctrl.TOGGLE")

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+ — must use ContextCompat with explicit flags
                ContextCompat.registerReceiver(this, qsToastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                registerReceiver(qsToastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            }
            else -> {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(qsToastReceiver, filter)
            }
        }
    }

    private fun startMonitoring() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshData()
                    delay(2000)
                }
            }
        }
    }

    private suspend fun refreshData() {
        val info = withContext(Dispatchers.IO) { BatteryMonitor.readInfo() }
        val logs = withContext(Dispatchers.IO) { BatteryMonitor.readLogs() }

        withContext(Dispatchers.Main) {
            updateStatusUI(info)
            updateStatsUI(info)
            updateSessionUI(info)
            binding.tvLog.text = logs.ifEmpty { "No logs available. Is the daemon running?" }
        }
    }

    private fun updateStatusUI(info: BatteryInfo) {
        val isBypass = info.mode == "BYPASS"
        val isCharging = info.status.equals("Charging", ignoreCase = true)

        val targetColor = ContextCompat.getColor(
            this,
            if (isBypass) R.color.bypass_active else R.color.charge_active
        )

        if (binding.tvMode.currentTextColor != targetColor) {
            binding.tvMode.animateColor(targetColor)
        }

        val targetStroke = ContextCompat.getColor(
            this,
            if (isBypass) R.color.bypass_active else R.color.charge_active
        )
        if (binding.cardStatus.strokeColor != targetStroke) {
            val animator = ValueAnimator.ofObject(
                ArgbEvaluator(),
                binding.cardStatus.strokeColor,
                targetStroke
            )
            animator.duration = 300
            animator.addUpdateListener {
                binding.cardStatus.strokeColor = it.animatedValue as Int
            }
            animator.start()
        }

        binding.tvMode.text = info.mode
        binding.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(targetColor)

        binding.batteryIcon.batteryLevel = info.levelRaw
        binding.batteryIcon.isBypass = isBypass
        binding.batteryIcon.isCharging = isCharging
        binding.batteryIcon.invalidate()

        binding.powerFlow.isBypass = isBypass
        binding.powerFlow.isPlugged = info.isPlugged
        binding.powerFlow.invalidate()
    }

    private fun updateStatsUI(info: BatteryInfo) {
        animateNumber(R.id.tvLevel, info.levelRaw.toFloat(), "%.0f%%", info.level)
        setText(R.id.tvStatus, info.status)
        animateNumber(R.id.tvTemp, info.tempRaw, "%.1f°C", info.temp, getTempColor(info.tempRaw))
        animateNumber(R.id.tvVoltage, info.voltageRaw, "%.3fV", info.voltage)
        animateNumber(R.id.tvCurrent, info.currentRaw, "%.0fmA", info.current)
        animateNumber(R.id.tvPower, info.powerRaw, "%.2fW", info.power)
        animateNumber(R.id.tvCC, info.ccRaw, "%.2fA", info.cc)
    }

    private fun updateSessionUI(info: BatteryInfo) {
        SessionTracker.update(info.levelRaw, info.currentRaw, info.status)

        if (SessionTracker.isTracking() && info.status.equals("Charging", ignoreCase = true)) {
            if (binding.cardSession.visibility != View.VISIBLE) {
                binding.cardSession.fadeIn()
            }
            binding.tvDuration.text = SessionTracker.getDuration()
            binding.tvEnergy.text = SessionTracker.getEnergyAdded()
            binding.tvTimeToFull.text = SessionTracker.getTimeToFull(info.levelRaw)
        } else {
            if (binding.cardSession.visibility == View.VISIBLE) {
                binding.cardSession.fadeOut()
            }
        }
    }

    private fun animateNumber(viewId: Int, newRaw: Float, format: String, displayText: String, textColor: Int? = null) {
        val view = findViewById<TextView>(viewId)
        val oldRaw = lastValues[viewId] ?: newRaw
        lastValues[viewId] = newRaw
        view.animateNumber(oldRaw, newRaw, format)
        textColor?.let { view.setTextColor(it) }
    }

    private fun setText(viewId: Int, text: String) {
        findViewById<TextView>(viewId).text = text
    }

    private fun getTempColor(tempC: Float): Int {
        return when {
            tempC >= 45f -> ContextCompat.getColor(this, R.color.temp_hot)
            tempC >= 42f -> ContextCompat.getColor(this, R.color.temp_warm)
            tempC >= 37f -> ContextCompat.getColor(this, R.color.temp_mild)
            else -> ContextCompat.getColor(this, R.color.temp_cool)
        }
    }

    private fun triggerHaptic() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
}
