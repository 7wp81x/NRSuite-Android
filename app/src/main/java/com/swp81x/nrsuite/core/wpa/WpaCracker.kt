package com.swp81x.nrsuite.core.wpa

import java.io.BufferedReader
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException

/**
 * Offline wordlist runner for WPA2-PSK handshakes.
 *
 * PBKDF2 is the bottleneck, so candidates are distributed across a small pool
 * of worker coroutines. The pool size is intentionally capped to avoid
 * saturating low-end devices and thermal-throttling immediately.
 */
object WpaCracker {

    data class Progress(
        val tested: Long,
        val elapsedMs: Long,
        val candidatesPerSecond: Double,
    )

    suspend fun crack(
        handshake: WpaHandshake,
        ssid: String,
        wordlist: BufferedReader,
        initialTested: Long = 0L,
        shouldStop: () -> Boolean,
        shouldPause: () -> Boolean = { false },
        onProgress: (Progress) -> Unit,
    ): String? {
        if (!handshake.isComplete || ssid.isBlank()) return null

        val startNanos = System.nanoTime()
        val tested = AtomicLong(initialTested)
        val found = AtomicReference<String?>(null)
        val channel = Channel<String>(capacity = WORK_QUEUE_CAPACITY)
        val parallelism = defaultParallelism()

        try {
            coroutineScope {
                val producer = launch(Dispatchers.IO) {
                    try {
                        var firstLine = true
                        while (!shouldStop() && found.get() == null) {
                            awaitWhilePaused(shouldStop, shouldPause)
                            if (shouldStop()) break
                            var candidate = wordlist.readLine() ?: break
                            if (firstLine) {
                                candidate = candidate.removePrefix("\uFEFF")
                                firstLine = false
                            }
                            if (candidate.isEmpty()) continue
                            channel.send(candidate)
                        }
                    } catch (_: ClosedSendChannelException) {
                        // A worker found the password and closed the queue.
                    } finally {
                        channel.close()
                    }
                }

                repeat(parallelism) {
                    launch(Dispatchers.Default) {
                        for (candidate in channel) {
                            awaitWhilePaused(shouldStop, shouldPause)
                            if (shouldStop()) {
                                channel.close()
                                break
                            }
                            if (found.get() != null) break

                            val testedNow = tested.incrementAndGet()
                            if (candidate.length in 8..63) {
                                val matches = runCatching {
                                    WpaHandshakeVerifier.verify(handshake, ssid, candidate)
                                }.getOrDefault(false)
                                if (matches) {
                                    found.compareAndSet(null, candidate)
                                    channel.close()
                                    break
                                }
                            }

                            if ((testedNow - initialTested) % PROGRESS_INTERVAL == 0L) {
                                emitProgress(startNanos, testedNow, onProgress)
                            }
                        }
                    }
                }

                producer.join()
            }
        } finally {
            runCatching { wordlist.close() }
        }

        emitProgress(startNanos, tested.get(), onProgress)
        return found.get()
    }

    private suspend fun awaitWhilePaused(
        shouldStop: () -> Boolean,
        shouldPause: () -> Boolean,
    ) {
        while (shouldPause() && !shouldStop()) {
            delay(PAUSE_POLL_MS)
        }
    }

    private fun emitProgress(
        startNanos: Long,
        tested: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val elapsedNanos = (System.nanoTime() - startNanos).coerceAtLeast(1L)
        val elapsedMs = elapsedNanos / 1_000_000L
        val perSecond = if (elapsedMs > 0) {
            (tested.toDouble() * 1000.0) / elapsedMs.toDouble()
        } else {
            0.0
        }
        onProgress(Progress(tested, elapsedMs, perSecond))
    }

    private fun defaultParallelism(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, MAX_WORKERS)

    private const val WORK_QUEUE_CAPACITY = 64
    private const val PROGRESS_INTERVAL = 25L
    private const val MAX_WORKERS = 4
    private const val PAUSE_POLL_MS = 25L
}
