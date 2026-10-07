package com.swp81x.nrsuite.core.usb

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.swp81x.nrsuite.service.IUsbOwnerService
import com.swp81x.nrsuite.service.UsbOwnerService
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Main-process client for [UsbOwnerService].
 *
 * The service runs in a separate process and owns the UsbDeviceConnection.
 * Binder transfers the fd to this process with the same SCM_RIGHTS semantics
 * used by Termux:API's parent/child fd handoff.
 */
internal object UsbDeviceOwnerClient {

    private const val TAG = "NRSuiteUsbOwner"
    private const val BIND_TIMEOUT_MS = 5_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var service: IUsbOwnerService? = null

    @Volatile
    private var bindLatch: CountDownLatch? = null

    private val bindLock = Any()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { IUsbOwnerService.Stub.asInterface(it) }
            bindLatch?.countDown()
            Log.i(TAG, "USB owner service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bindLatch?.countDown()
            Log.w(TAG, "USB owner service disconnected")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    class Session internal constructor(
        private val binder: IUsbOwnerService,
        private val deviceName: String,
        val parcelFileDescriptor: ParcelFileDescriptor,
    ) {
        val fd: Int
            get() = parcelFileDescriptor.fd

        fun close() {
            runCatching { binder.closeDevice(deviceName) }
            runCatching { parcelFileDescriptor.close() }
        }
    }

    @Throws(IOException::class)
    fun openDevice(deviceName: String): Session {
        val binder = ensureBound() ?: throw IOException("USB owner service is unavailable")
        val pfd = try {
            binder.openDevice(deviceName)
        } catch (t: Throwable) {
            throw IOException("USB owner service open failed for $deviceName", t)
        } ?: throw IOException("USB owner service could not open $deviceName")

        if (pfd.fd < 0) {
            runCatching { pfd.close() }
            throw IOException("USB owner service returned invalid fd for $deviceName")
        }
        Log.i(TAG, "received USB fd=${pfd.fd} for $deviceName")
        return Session(binder, deviceName, pfd)
    }

    private fun ensureBound(): IUsbOwnerService? {
        service?.let { return it }

        val context = appContext ?: return null
        synchronized(bindLock) {
            service?.let { return it }

            val latch = CountDownLatch(1)
            bindLatch = latch

            val intent = Intent(context, UsbOwnerService::class.java)
            val bound = try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (t: Throwable) {
                Log.e(TAG, "bindService failed", t)
                false
            }
            if (!bound) {
                bindLatch = null
                return null
            }

            return try {
                latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                service
            } catch (t: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            } finally {
                bindLatch = null
            }
        }
    }
}
