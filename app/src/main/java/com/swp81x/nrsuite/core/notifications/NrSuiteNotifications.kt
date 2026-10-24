package com.swp81x.nrsuite.core.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.swp81x.nrsuite.MainActivity
import com.swp81x.nrsuite.R

object NrSuiteNotifications {
    const val CHANNEL_OPERATIONS = "nrsuite_operations"
    const val CHANNEL_DOWNLOADS = "nrsuite_downloads"
    const val CHANNEL_ALERTS = "nrsuite_alerts"

    private const val OPERATION_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID = 2001
    private const val CREDENTIAL_NOTIFICATION_ID = 2002
    private const val BLE_NOTIFICATION_ID = 2003
    private const val IR_DATABASE_NOTIFICATION_ID = 3001
    private const val IR_SEARCH_NOTIFICATION_ID = 3002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OPERATIONS,
                "NRSuite operations",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps long-running NRSuite radio and USB operations alive."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                "NRSuite downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background database and update downloads."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "NRSuite alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Credential captures, BLE connections, and other task events."
            },
        )
    }

    fun buildOperationNotification(context: Context, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(R.drawable.ic_stat_nrsuite)
            .setContentTitle("NRSuite")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notifyCredential(context: Context, title: String, text: String) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context).notify(
            CREDENTIAL_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_nrsuite)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(mainActivityIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyBleConnected(context: Context, peer: String) {
        if (!notificationsAllowed(context)) return
        val label = peer.ifBlank { "BLE device" }
        NotificationManagerCompat.from(context).notify(
            BLE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_nrsuite)
                .setContentTitle("BLE device connected")
                .setContentText(label)
                .setContentIntent(mainActivityIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyTaskAlert(context: Context, title: String, text: String) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context).notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat_nrsuite)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mainActivityIntent(context))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }


    fun buildIrDatabaseNotification(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int? = null,
        indeterminate: Boolean = false,
        ongoing: Boolean = true,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_ir)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (ongoing) {
            if (progressPercent == null) {
                builder.setProgress(0, 0, indeterminate)
            } else {
                builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
            }
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun notifyIrDatabaseProgress(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int? = null,
        indeterminate: Boolean = false,
    ) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context).notify(
            IR_DATABASE_NOTIFICATION_ID,
            buildIrDatabaseNotification(
                context = context,
                title = title,
                text = text,
                progressPercent = progressPercent,
                indeterminate = indeterminate,
                ongoing = true,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyIrDatabaseResult(context: Context, title: String, text: String) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context).notify(
            IR_DATABASE_NOTIFICATION_ID,
            buildIrDatabaseNotification(
                context = context,
                title = title,
                text = text,
                ongoing = false,
            ),
        )
    }


    @SuppressLint("MissingPermission")
    fun notifyIrSearchProgress(
        context: Context,
        title: String,
        text: String,
        progressPercent: Int?,
    ) {
        if (!notificationsAllowed(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(R.drawable.ic_stat_ir)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(mainActivityIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progressPercent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        }
        NotificationManagerCompat.from(context).notify(
            IR_SEARCH_NOTIFICATION_ID,
            builder.build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun notifyIrSearchResult(context: Context, title: String, text: String) {
        if (!notificationsAllowed(context)) return
        NotificationManagerCompat.from(context).notify(
            IR_SEARCH_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
                .setSmallIcon(R.drawable.ic_stat_ir)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(mainActivityIntent(context))
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build(),
        )
    }

    fun irDatabaseNotificationId(): Int = IR_DATABASE_NOTIFICATION_ID

    fun operationNotificationId(): Int = OPERATION_NOTIFICATION_ID

    private fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun mainActivityIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
