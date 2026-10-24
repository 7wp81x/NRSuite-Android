package com.swp81x.nrsuite.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications

/**
 * Keeps the process alive while a large IR database download/extraction is in
 * progress. Progress text is updated through NrSuiteNotifications using the
 * same notification ID.
 */
class IrDatabaseForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Preparing IR database..."
        runCatching {
            startForeground(
                NrSuiteNotifications.irDatabaseNotificationId(),
                NrSuiteNotifications.buildIrDatabaseNotification(
                    context = this,
                    title = "NRSuite IR database",
                    text = text,
                    indeterminate = true,
                    ongoing = true,
                ),
            )
        }.onFailure {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.swp81x.nrsuite.action.START_IR_DATABASE"
        private const val ACTION_STOP = "com.swp81x.nrsuite.action.STOP_IR_DATABASE"
        private const val EXTRA_TEXT = "extra_text"

        fun start(context: Context, text: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, IrDatabaseForegroundService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_TEXT, text)
                    },
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, IrDatabaseForegroundService::class.java))
            }
        }
    }
}
