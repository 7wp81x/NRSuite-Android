package com.swp81x.nrsuite.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications

/**
 * Foreground service for offline WPA wordlist cracking.
 *
 * Cracking is CPU-only and can continue while the app is backgrounded, so it
 * uses a dataSync foreground service rather than the USB connectedDevice
 * service used by radio operations.
 */
class WpaCrackerForegroundService : Service() {
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
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "WPA cracker active"
        val progress = intent.getIntExtra(EXTRA_PROGRESS, NO_PROGRESS)
            .takeIf { it in 0..100 }
        val indeterminate = intent.getBooleanExtra(EXTRA_INDETERMINATE, false)
        val notification = NrSuiteNotifications.buildCrackerNotification(
            this,
            text,
            progress,
            indeterminate,
        )
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NrSuiteNotifications.crackerNotificationId(),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NrSuiteNotifications.crackerNotificationId(), notification)
            }
        }.isSuccess
        if (!started) {
            releaseWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }
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
                "NRSuite:cracker",
            ).apply { setReferenceCounted(false) }
        }
        if (wakeLock?.isHeld != true) runCatching { wakeLock?.acquire() }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "NRSuite operations",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps long-running NRSuite operations alive."
                    setShowBadge(false)
                },
            )
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
        const val ACTION_START = "com.swp81x.nrsuite.action.START_CRACKER_FOREGROUND"
        const val EXTRA_TEXT = "cracker_text"
        const val EXTRA_PROGRESS = "cracker_progress"
        const val EXTRA_INDETERMINATE = "cracker_indeterminate"
        private const val NO_PROGRESS = -1
        private const val CHANNEL_ID = "nrsuite_operations"
    }
}
