package com.youcefm.bypassctrl

object SessionTracker {
    private var startTime: Long = 0
    private var startLevel: Int = -1
    private var energyMah: Float = 0f
    private var lastUpdate: Long = 0
    private var lastCurrentMa: Float = 0f
    private var isTracking = false

    fun update(level: Int, currentMa: Float, status: String) {
        val now = System.currentTimeMillis()
        val currentAbs = kotlin.math.abs(currentMa)

        if (status.equals("Charging", ignoreCase = true) && currentAbs > 0) {
            if (!isTracking) {
                startTime = now
                startLevel = level
                energyMah = 0f
                isTracking = true
                lastUpdate = now
                lastCurrentMa = currentAbs
            } else {
                val elapsedHours = (now - lastUpdate) / 3_600_000f
                energyMah += (lastCurrentMa + currentAbs) / 2f * elapsedHours
                lastUpdate = now
                lastCurrentMa = currentAbs
            }
        } else {
            isTracking = false
        }
    }

    fun getDuration(): String {
        if (!isTracking || startTime == 0L) return "--"
        val mins = ((System.currentTimeMillis() - startTime) / 60_000).toInt()
        return if (mins < 60) "$mins min" else "${mins / 60}h ${mins % 60}m"
    }

    fun getEnergyAdded(): String {
        if (!isTracking) return "--"
        return String.format("%.0f mAh", energyMah)
    }

    fun getTimeToFull(currentLevel: Int): String {
        if (!isTracking || startLevel < 0 || currentLevel <= startLevel || currentLevel >= 100) return "--"
        val elapsedMin = ((System.currentTimeMillis() - startTime) / 60_000f)
        val percentGained = currentLevel - startLevel
        if (percentGained <= 0) return "--"
        val minPerPercent = elapsedMin / percentGained
        val remaining = 100 - currentLevel
        val mins = (remaining * minPerPercent).toInt()
        return if (mins < 60) "~$mins min" else "~${mins / 60}h ${mins % 60}m"
    }

    fun isTracking(): Boolean = isTracking
}
