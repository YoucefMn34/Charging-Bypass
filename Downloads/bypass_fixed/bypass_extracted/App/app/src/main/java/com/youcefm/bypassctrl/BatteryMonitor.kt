package com.youcefm.bypassctrl

data class BatteryInfo(
    val mode: String,
    val status: String,
    val level: String,
    val levelRaw: Int,
    val temp: String,
    val tempRaw: Float,
    val voltage: String,
    val voltageRaw: Float,
    val current: String,
    val currentRaw: Float,
    val power: String,
    val powerRaw: Float,
    val cc: String,
    val ccRaw: Float,
    val isPlugged: Boolean
)

object BatteryMonitor {

    fun readInfo(): BatteryInfo {
        val mode = ShellExecutor.exec("cat /data/bypass/state").trim().ifEmpty { "charge" }
        val status = ShellExecutor.exec("cat /sys/class/power_supply/battery/status").trim()
        val levelRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/capacity").trim().toIntOrNull() ?: 0
        val tempRawTenths = ShellExecutor.exec("cat /sys/class/power_supply/battery/temp").trim().toFloatOrNull() ?: 0f
        val voltRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/voltage_now").trim().toFloatOrNull() ?: 0f
        val currRaw = ShellExecutor.exec("cat /sys/class/power_supply/battery/current_now").trim().toFloatOrNull() ?: 0f
        val ccRawMicro = ShellExecutor.exec("cat /sys/class/power_supply/battery/constant_charge_current").trim().toFloatOrNull() ?: 0f
        val usbOnline = ShellExecutor.exec("cat /sys/class/power_supply/usb/online").trim()

        val tempC = tempRawTenths / 10f
        val voltV = voltRaw / 1_000_000f
        val currMa = kotlin.math.abs(currRaw / 1000f)
        val powerW = kotlin.math.abs(voltV * (currRaw / 1_000_000f))
        val ccA = ccRawMicro / 1_000_000f

        return BatteryInfo(
            mode = mode.uppercase(),
            status = status.ifEmpty { "N/A" },
            level = "$levelRaw%",
            levelRaw = levelRaw,
            temp = String.format("%.1f°C", tempC),
            tempRaw = tempC,
            voltage = String.format("%.3fV", voltV),
            voltageRaw = voltV,
            current = String.format("%.0fmA", currMa),
            currentRaw = currMa,
            power = String.format("%.2fW", powerW),
            powerRaw = powerW,
            cc = parseCC(ccRawMicro),
            ccRaw = ccA,
            isPlugged = usbOnline == "1"
        )
    }

    private fun parseCC(rawMicroA: Float): String {
        return when {
            rawMicroA == 0f -> "0"
            rawMicroA >= 1_000_000f -> String.format("%.2f A", rawMicroA / 1_000_000f)
            rawMicroA >= 1_000f -> String.format("%.0f mA", rawMicroA / 1_000f)
            else -> String.format("%.0f µA", rawMicroA)
        }
    }

    fun readLogs(): String {
        return ShellExecutor.exec("tail -n 30 /data/bypass/daemon.log")
    }

    fun setMode(isBypass: Boolean) {
        val value = if (isBypass) "bypass" else "charge"
        ShellExecutor.exec("echo $value > /data/bypass/state")
    }

    /** Write threshold config to daemon. Format: "enabled:value" e.g. "1:80" */
    fun setThreshold(enabled: Boolean, value: Int) {
        val cfg = "${if (enabled) 1 else 0}:$value"
        ShellExecutor.exec("echo $cfg > /data/bypass/config")
    }


// Add these methods to your existing BatteryMonitor object/class

    /**
     * Write temperature threshold config to daemon.
     * Format: enabled:trigger:resume (e.g., "1:40:38")
     */
    fun setTempThreshold(enabled: Boolean, trigger: Int, resume: Int) {
        val config = "${if (enabled) 1 else 0}:$trigger:$resume"
        ShellExecutor.exec("echo $config > /data/bypass/temp_config")
    }

    /**
     * Read current temperature threshold config from daemon.
     */
    fun readTempThreshold(): TempThresholdConfig {
        val result = ShellExecutor.exec("cat /data/bypass/temp_config 2>/dev/null || echo 0:40:38")
        val parts = result.trim().split(":")
        return TempThresholdConfig(
            enabled = parts.getOrNull(0)?.toIntOrNull() == 1,
            trigger = parts.getOrNull(1)?.toIntOrNull() ?: 40,
            resume = parts.getOrNull(2)?.toIntOrNull() ?: 38
        )
    }

    data class TempThresholdConfig(
        val enabled: Boolean,
        val trigger: Int,
        val resume: Int
    )

}
