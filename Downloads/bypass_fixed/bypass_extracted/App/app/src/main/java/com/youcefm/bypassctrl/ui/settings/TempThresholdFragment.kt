package com.youcefm.bypassctrl.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.youcefm.bypassctrl.R
import java.io.File

/**
 * Temperature threshold settings fragment.
 * Reads/writes /data/bypass/temp_config (format: enabled:trigger:resume)
 */
class TempThresholdFragment : Fragment() {

    private lateinit var tempEnabledSwitch: MaterialSwitch
    private lateinit var tempTriggerSlider: Slider
    private lateinit var tempResumeSlider: Slider
    private lateinit var tempTriggerValue: TextView
    private lateinit var tempResumeValue: TextView

    private val tempConfigFile = File("/data/bypass/temp_config")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_temp_threshold, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tempEnabledSwitch = view.findViewById(R.id.temp_enabled_switch)
        tempTriggerSlider = view.findViewById(R.id.temp_trigger_slider)
        tempResumeSlider = view.findViewById(R.id.temp_resume_slider)
        tempTriggerValue = view.findViewById(R.id.temp_trigger_value)
        tempResumeValue = view.findViewById(R.id.temp_resume_value)

        loadConfig()
        setupListeners()
    }

    private fun loadConfig() {
        val config = readTempConfig()
        tempEnabledSwitch.isChecked = config.enabled
        tempTriggerSlider.value = config.trigger.toFloat()
        tempResumeSlider.value = config.resume.toFloat()
        updateValueLabels(config.trigger, config.resume)
    }

    private fun setupListeners() {
        tempEnabledSwitch.setOnCheckedChangeListener { _, _ ->
            saveConfig()
        }

        tempTriggerSlider.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            // Ensure trigger >= resume + 2 (minimum hysteresis)
            val resumeVal = tempResumeSlider.value.toInt()
            if (intValue < resumeVal + 2) {
                tempResumeSlider.value = (intValue - 2).coerceAtLeast(30).toFloat()
            }
            updateValueLabels(intValue, tempResumeSlider.value.toInt())
            saveConfig()
        }

        tempResumeSlider.addOnChangeListener { _, value, _ ->
            val intValue = value.toInt()
            // Ensure resume <= trigger - 2
            val triggerVal = tempTriggerSlider.value.toInt()
            if (intValue > triggerVal - 2) {
                tempTriggerSlider.value = (intValue + 2).coerceAtMost(60).toFloat()
            }
            updateValueLabels(tempTriggerSlider.value.toInt(), intValue)
            saveConfig()
        }
    }

    private fun updateValueLabels(trigger: Int, resume: Int) {
        tempTriggerValue.text = "${trigger}°C"
        tempResumeValue.text = "${resume}°C"
    }

    private fun saveConfig() {
        val enabled = if (tempEnabledSwitch.isChecked) 1 else 0
        val trigger = tempTriggerSlider.value.toInt()
        val resume = tempResumeSlider.value.toInt()
        val config = "$enabled:$trigger:$resume"

        // Write via su
        try {
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo $config > /data/bypass/temp_config")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class TempConfig(val enabled: Boolean, val trigger: Int, val resume: Int)

    private fun readTempConfig(): TempConfig {
        return try {
            val content = tempConfigFile.readText().trim()
            val parts = content.split(":")
            TempConfig(
                enabled = parts.getOrNull(0)?.toIntOrNull() == 1,
                trigger = parts.getOrNull(1)?.toIntOrNull() ?: 40,
                resume = parts.getOrNull(2)?.toIntOrNull() ?: 38
            )
        } catch (e: Exception) {
            TempConfig(enabled = false, trigger = 40, resume = 38)
        }
    }
}
