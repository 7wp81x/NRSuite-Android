package com.swp81x.nrsuite.core.flasher

/**
 * One control-line operation in an ESP ROM reset sequence.
 *
 * A null field means that line is left unchanged. Only the requested line is
 * toggled for each step so the sequence mirrors esptool's setDTR/setRTS calls.
 */
internal data class ResetControlStep(
    val dtr: Boolean? = null,
    val rts: Boolean? = null,
    val delayAfterMs: Long = 0L,
)

/**
 * Host-side DTR/RTS reset sequences copied from esptool's reset.py semantics.
 *
 * ESP32 auto-reset circuits invert these lines at the transistor level. The
 * comments in esptool therefore describe DTR=false as IO0/BOOT high and
 * RTS=true as EN/reset low. Keeping the ordered calls and delays identical to
 * esptool avoids board-specific timing guesses.
 */
internal object Esp32ResetSequences {
    const val ESPTOOL_CLASSIC_RESET_HOLD_MS = 100L
    const val ESPTOOL_CLASSIC_RELEASE_DELAY_MS = 50L
    const val ESPTOOL_CLASSIC_EXTRA_RELEASE_DELAY_MS = 550L
    const val ESPTOOL_USB_JTAG_STEP_MS = 100L

    fun classic(releaseDelayMs: Long = ESPTOOL_CLASSIC_RELEASE_DELAY_MS): List<ResetControlStep> {
        return listOf(
            // IO0=HIGH.
            ResetControlStep(dtr = false),
            // EN=LOW, chip in reset.
            ResetControlStep(rts = true, delayAfterMs = ESPTOOL_CLASSIC_RESET_HOLD_MS),
            // IO0=LOW while still in reset.
            ResetControlStep(dtr = true),
            // EN=HIGH, chip out of reset. The longer variant is esptool's
            // "extra_delay" strategy for adapters that need more settle time.
            ResetControlStep(rts = false, delayAfterMs = releaseDelayMs),
            // IO0=HIGH, done.
            ResetControlStep(dtr = false),
        )
    }

    fun usbJtagSerial(): List<ResetControlStep> {
        return listOf(
            // Idle.
            ResetControlStep(rts = false),
            ResetControlStep(dtr = false, delayAfterMs = ESPTOOL_USB_JTAG_STEP_MS),
            // Set IO0 and prepare reset.
            ResetControlStep(dtr = true),
            ResetControlStep(rts = false, delayAfterMs = ESPTOOL_USB_JTAG_STEP_MS),
            // EN=LOW. Set RTS twice like esptool so Windows and USB-serial
            // bridges that only propagate DTR on an RTS change see the state.
            ResetControlStep(rts = true),
            ResetControlStep(dtr = false),
            ResetControlStep(rts = true, delayAfterMs = ESPTOOL_USB_JTAG_STEP_MS),
            // Chip out of reset.
            ResetControlStep(dtr = false),
            ResetControlStep(rts = false),
        )
    }
}
