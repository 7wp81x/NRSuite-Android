package com.swp81x.nrsuite

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.core.usb.UsbDeviceOwnerClient
import com.swp81x.nrsuite.core.notifications.NrSuiteNotifications

/**
 * Application-scoped owner for the long-running NRSuite bridge controller.
 *
 * The controller intentionally survives Activity/ViewModel destruction while
 * the foreground service keeps the process alive. USB attach/detach receivers
 * are registered here too, so state resets do not depend on the UI being
 * composed.
 */
class NrSuiteApplication : Application() {

    val controller: MainViewModel by lazy { MainViewModel(this) }

    private val attachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                controller.onUsbDeviceAttached(device)
            }
        }
    }

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null) controller.onUsbDeviceDetached(device)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Application.onCreate() also runs in the dedicated :usb process. Only
        // the main app process should own the bridge controller and register
        // the global USB receivers; otherwise the :usb process creates a
        // second MainViewModel and emits duplicate attach/detach logs.
        if (!isMainProcess()) return

        NrSuiteNotifications.createChannels(this)
        UsbDeviceOwnerClient.init(this)
        runCatching {
            ContextCompat.registerReceiver(
                this,
                attachReceiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { error ->
            Log.w("NRSuite", "USB attach receiver registration failed", error)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                detachReceiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { error ->
            Log.w("NRSuite", "USB detach receiver registration failed", error)
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val activityManager = getSystemService(ActivityManager::class.java)
            activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
        }
        return processName == null || processName == packageName
    }
}
