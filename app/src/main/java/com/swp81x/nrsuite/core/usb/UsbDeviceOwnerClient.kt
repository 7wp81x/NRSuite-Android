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
    private const val BIND_TIMEOUT_MS = 1_000L

    // Android/MIUI can leave the remote owner process marked "bad" for a short
    // period after force-stop or task removal. Retry long enough for the
    // framework to recreate it instead of immediately exposing a hard error.
    private val BIND_RETRY_DELAYS_MS = longArrayOf(0L, 500L, 1_000L, 1_500L, 2_000L, 3_000L)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var service: IUsbOwnerService? = null

    private val bindLock = Any()
    private var activeConnection: ServiceConnection? = null

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

    private fun binderAlive(binder: IUsbOwnerService?): Boolean =
        binder?.asBinder()?.isBinderAlive == true

    private fun ensureBound(): IUsbOwnerService? {
        service?.takeIf(::binderAlive)?.let { return it }

        val context = appContext ?: return null
        synchronized(bindLock) {
            service?.takeIf(::binderAlive)?.let { return it }

            // Drop a stale binding before creating a fresh one. After a
            // force-stop, the old ProcessRecord can temporarily report "process
            // is bad"; repeated bind attempts with backoff let the framework
            // finish clearing that state.
            service = null
            activeConnection?.let { stale ->
                runCatching { context.unbindService(stale) }
                activeConnection = null
            }

            val intent = Intent(context, UsbOwnerService::class.java)
            BIND_RETRY_DELAYS_MS.forEachIndexed { index, delayMs ->
                if (delayMs > 0L) {
                    runCatching { Thread.sleep(delayMs) }
                }
                val binder = bindOnce(context, intent)
                if (binderAlive(binder)) {
                    service = binder
                    Log.i(TAG, "USB owner service connected after ${index + 1} bind attempt(s)")
                    return binder
                }
                Log.w(
                    TAG,
                    "USB owner service bind attempt ${index + 1}/" +
                        "${BIND_RETRY_DELAYS_MS.size} failed",
                )
            }
            return null
        }
    }

    private fun bindOnce(context: Context, intent: Intent): IUsbOwnerService? {
        val latch = CountDownLatch(1)
        var binderRef: IUsbOwnerService? = null
        var connected = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                binderRef = binder?.let { IUsbOwnerService.Stub.asInterface(it) }
                connected = binderRef != null
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                // Release any waiter before taking bindLock: ensureBound holds
                // bindLock while waiting on this latch.
                latch.countDown()
                synchronized(bindLock) {
                    if (binderRef != null && service === binderRef) {
                        service = null
                    }
                }
            }
        }

        val bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.e(TAG, "bindService failed", t)
            false
        }
        if (!bound) return null

        val connectedInTime = try {
            latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (connectedInTime && connected && binderAlive(binderRef)) {
            activeConnection = connection
            return binderRef
        }

        runCatching { context.unbindService(connection) }
        return null
    }
}
