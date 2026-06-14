package com.youcefm.bypassctrl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat

/**
 * Invisible activity that posts notifications from the daemon.
 * Called via: am start -n com.youcefm.bypassctrl/.NotificationActivity --es title "..." --es text "..."
 * Works even when the main app is killed from recents.
 */
class NotificationActivity : AppCompatActivity() {

    companion object {
        const val CHANNEL_ID = "bypass_alerts"
        const val CHANNEL_NAME = "Bypass Alerts"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra("action")

        // Handle channel pre-creation from service.sh
        if (action == "create_channel") {
            createNotificationChannel()
            finish()
            return
        }

        val title = intent.getStringExtra("title") ?: "Bypass Alert"
        val text = intent.getStringExtra("text") ?: ""

        createNotificationChannel()
        showNotification(title, text)

        // Close immediately — no UI shown
        finish()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .getNotificationChannel(CHANNEL_ID)

            // Only create if doesn't exist
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Charging bypass trigger notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setBypassDnd(true)
                }

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun showNotification(title: String, text: String) {
        // Check permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.areNotificationsEnabled()) {
                // Permission denied — silently fail, daemon will use cmd notification
                return
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_bypass)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use current time as unique ID to allow multiple notifications
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
