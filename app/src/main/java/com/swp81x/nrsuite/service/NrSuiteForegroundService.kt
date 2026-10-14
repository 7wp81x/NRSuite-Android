package com.swp81x.nrsuite.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.IBinder
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications

class NrSuiteForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        createNotificationChannel()
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "NRSuite operation active"
        startForeground(NrSuiteNotifications.operationNotificationId(), buildNotification(text))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NRSuite:bridge",
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld != true) {
            runCatching { wakeLock?.acquire() }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun buildNotification(text: String): Notification =
        NrSuiteNotifications.buildOperationNotification(this, text)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NRSuite operations",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps long-running NRSuite radio operations alive."
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.swp81x.nrsuite.action.START_FOREGROUND"
        const val ACTION_STOP = "com.swp81x.nrsuite.action.STOP_FOREGROUND"
        const val EXTRA_TEXT = "extra_text"
        private const val CHANNEL_ID = "nrsuite_operations"
        private const val NOTIFICATION_ID = 1001
    }
}
