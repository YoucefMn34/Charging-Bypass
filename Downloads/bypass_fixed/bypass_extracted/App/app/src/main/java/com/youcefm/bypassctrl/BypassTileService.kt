package com.youcefm.bypassctrl

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BypassTileService : TileService() {
    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val current = ShellExecutor.exec("cat /data/bypass/state").trim()
        val target = if (current == "bypass") "charge" else "bypass"
        ShellExecutor.exec("echo $target > /data/bypass/state")

        // Send broadcast to show toast in app (if running)
        val modeText = if (target == "bypass") "BYPASS" else "CHARGE"
        sendBroadcast(
            android.content.Intent("com.youcefm.bypassctrl.TOGGLE")
                .putExtra("mode", modeText)
                .setPackage(packageName)
        )

        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val current = ShellExecutor.exec("cat /data/bypass/state").trim()
        if (current == "bypass") {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Bypass"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bypass)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Charge"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_bypass)
        }
        tile.updateTile()
    }
}
