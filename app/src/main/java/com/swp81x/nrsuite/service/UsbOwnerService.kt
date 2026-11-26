package com.swp81x.nrsuite.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs in a dedicated `:usb` process and owns the Android UsbDeviceConnection.
 *
 * This mirrors the Termux:API no-root architecture: the process that opens the
 * Android USB device is not the same process that wraps the fd with libusb.
 * The fd returned here goes through Binder/SCM_RIGHTS to the main app process.
 */
class UsbOwnerService : Service() {

    private val usbManager: UsbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private data class OwnedConnection(
        val connection: UsbDeviceConnection,
        val parcelFileDescriptor: ParcelFileDescriptor,
    )

    private val connections = ConcurrentHashMap<String, OwnedConnection>()
    private val ownerLock = Any()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "UsbOwnerService onCreate pid=${android.os.Process.myPid()}")
    }

    private val binder = object : IUsbOwnerService.Stub() {
        override fun openDevice(deviceName: String?): ParcelFileDescriptor? {
            if (deviceName.isNullOrBlank()) return null
            synchronized(ownerLock) {
                // Do not silently steal ownership from Serial Monitor or another
                // active session. Callers must close their current session first.
                if (connections.containsKey(deviceName)) {
                    Log.w(TAG, "openDevice: already owned: $deviceName")
                    return null
                }

                val device = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName }
                if (device == null) {
                    Log.w(TAG, "openDevice: not found: $deviceName")
                    return null
                }
                if (!usbManager.hasPermission(device)) {
                    Log.w(TAG, "openDevice: no permission: $deviceName")
                    return null
                }

                val connection = usbManager.openDevice(device)
                if (connection == null) {
                    Log.w(TAG, "openDevice: UsbManager.openDevice returned null: $deviceName")
                    return null
                }

                val originalFd = connection.fileDescriptor
                if (originalFd < 0) {
                    runCatching { connection.close() }
                    Log.w(TAG, "openDevice: invalid fd: $originalFd")
                    return null
                }

                return try {
                    // Keep the owning PFD alive in this process for the whole
                    // session. Return a duplicate to Binder: the generated AIDL
                    // stub may close the PFD it writes after transferring it, so
                    // returning the owner directly would close the service-side
                    // descriptor.
                    val ownerPfd = ParcelFileDescriptor.fromFd(originalFd)
                    val clientPfd = ownerPfd.dup()
                    connections[deviceName] = OwnedConnection(connection, ownerPfd)
                    Log.i(
                        TAG,
                        "openDevice: opened $deviceName pid=${android.os.Process.myPid()}",
                    )
                    clientPfd
                } catch (t: Throwable) {
                    runCatching { connection.close() }
                    Log.e(TAG, "openDevice: fd handoff failed for $deviceName", t)
                    null
                }
            }
        }

        override fun closeDevice(deviceName: String?) {
            if (deviceName.isNullOrBlank()) return
            synchronized(ownerLock) {
                connections.remove(deviceName)?.let { owned ->
                    runCatching { owned.connection.close() }
                    runCatching { owned.parcelFileDescriptor.close() }
                    Log.i(TAG, "closeDevice: closed $deviceName")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "UsbOwnerService onBind pid=${android.os.Process.myPid()}")
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "UsbOwnerService onDestroy pid=${android.os.Process.myPid()}")
        synchronized(ownerLock) {
            connections.values.forEach { owned ->
                runCatching { owned.connection.close() }
                runCatching { owned.parcelFileDescriptor.close() }
            }
            connections.clear()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NRSuiteUsbOwner"
    }
}
