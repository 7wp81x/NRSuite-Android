package com.swp81x.nrsuite.core.ir

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.ConsumerIrManager

class IrPhoneTransmitter(context: Context) {
    private val manager =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    fun capabilities(): IrPhoneCapabilities {
        val emitter = manager ?: return IrPhoneCapabilities()
        val hasEmitter = runCatching { emitter.hasIrEmitter() }.getOrDefault(false)
        val ranges = if (hasEmitter) {
            runCatching {
                emitter.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        return IrPhoneCapabilities(hasEmitter = hasEmitter, carrierRanges = ranges)
    }

    @SuppressLint("MissingPermission")
    fun transmit(frequencyHz: Int, pattern: IntArray): Result<Unit> {
        val emitter = manager ?: return Result.failure(
            IllegalStateException("This phone does not expose an IR emitter service."),
        )
        if (!runCatching { emitter.hasIrEmitter() }.getOrDefault(false)) {
            return Result.failure(IllegalStateException("This phone has no IR emitter."))
        }
        if (pattern.isEmpty()) {
            return Result.failure(IllegalArgumentException("IR pattern is empty."))
        }
        return runCatching {
            val selectedFrequency = nearestSupportedFrequency(emitter, frequencyHz)
            emitter.transmit(selectedFrequency, pattern)
        }
    }

    private fun nearestSupportedFrequency(
        emitter: ConsumerIrManager,
        requestedHz: Int,
    ): Int {
        val ranges = runCatching { emitter.carrierFrequencies?.toList().orEmpty() }
            .getOrDefault(emptyList())
        if (ranges.isEmpty()) return requestedHz
        if (ranges.any { requestedHz in it.minFrequency..it.maxFrequency }) return requestedHz
        return ranges.minByOrNull { range ->
            when {
                requestedHz < range.minFrequency -> range.minFrequency - requestedHz
                requestedHz > range.maxFrequency -> requestedHz - range.maxFrequency
                else -> 0
            }
        }?.let { range ->
            requestedHz.coerceIn(range.minFrequency, range.maxFrequency)
        } ?: requestedHz
    }
}
