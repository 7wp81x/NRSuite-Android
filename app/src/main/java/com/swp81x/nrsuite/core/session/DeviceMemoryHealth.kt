package com.swp81x.nrsuite.core.session

/**
 * Current heap pressure reported by firmware telemetry.
 *
 * Only the live free-heap and largest-block values are used. Historical
 * minimum-free-heap values must not drive persistent UI warnings because they
 * can remain low after a transient allocation has already been released.
 */
enum class DeviceMemoryPressure {
    NORMAL,
    WARNING,
    CRITICAL,
}

data class DeviceMemoryHealth(
    val pressure: DeviceMemoryPressure,
    val freeHeap: Long?,
    val largestBlock: Long?,
) {
    val shouldShowBanner: Boolean
        get() = pressure != DeviceMemoryPressure.NORMAL

    companion object {
        const val WARNING_FREE_HEAP_BYTES = 48L * 1024L
        const val WARNING_LARGEST_BLOCK_BYTES = 28L * 1024L
        const val CRITICAL_FREE_HEAP_BYTES = 20L * 1024L
        const val CRITICAL_LARGEST_BLOCK_BYTES = 12L * 1024L

        fun evaluate(freeHeap: Long?, largestBlock: Long?): DeviceMemoryHealth {
            val critical = (freeHeap != null && freeHeap < CRITICAL_FREE_HEAP_BYTES) ||
                (largestBlock != null && largestBlock < CRITICAL_LARGEST_BLOCK_BYTES)
            val warning = (freeHeap != null && freeHeap < WARNING_FREE_HEAP_BYTES) ||
                (largestBlock != null && largestBlock < WARNING_LARGEST_BLOCK_BYTES)
            return DeviceMemoryHealth(
                pressure = when {
                    critical -> DeviceMemoryPressure.CRITICAL
                    warning -> DeviceMemoryPressure.WARNING
                    else -> DeviceMemoryPressure.NORMAL
                },
                freeHeap = freeHeap,
                largestBlock = largestBlock,
            )
        }
    }
}

/**
 * Firmware refuses first-time BLE HID initialization below these thresholds.
 * The values mirror the guard in firmware/src/main.cpp.
 */
data class BleHidHeapFailure(
    val freeHeap: Long?,
    val largestBlock: Long?,
    val message: String,
) {
    companion object {
        const val REQUIRED_FREE_HEAP_BYTES = 90L * 1024L
        const val REQUIRED_LARGEST_BLOCK_BYTES = 48L * 1024L
    }
}

private val BLE_HID_HEAP_MESSAGE = Regex(
    pattern = """free\s*=\s*(\d+)\s+largest\s*=\s*(\d+)""",
    option = RegexOption.IGNORE_CASE,
)

/**
 * Recognizes the firmware's human-readable low-heap rejection and extracts the
 * values when present. ViewModel code may also receive structured response
 * fields from newer firmware and should prefer those.
 */
fun parseBleHidHeapFailure(message: String?): BleHidHeapFailure? {
    val text = message?.trim().orEmpty()
    if (!text.contains("insufficient heap for BLE HID", ignoreCase = true)) return null
    val match = BLE_HID_HEAP_MESSAGE.find(text)
    return BleHidHeapFailure(
        freeHeap = match?.groupValues?.getOrNull(1)?.toLongOrNull(),
        largestBlock = match?.groupValues?.getOrNull(2)?.toLongOrNull(),
        message = text,
    )
}
