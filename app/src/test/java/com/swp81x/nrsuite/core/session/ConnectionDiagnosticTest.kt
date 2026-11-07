package com.swp81x.nrsuite.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionDiagnosticTest {
    @Test
    fun heartbeatWithFailedHandshakeRecommendsReplugInsteadOfFlash() {
        val diagnostic = baseDiagnostic(
            sawProtocolFrame = true,
            sawNrsuiteHeartbeat = true,
        )

        assertEquals(
            ConnectionDiagnosticRecommendation.REPLUG_AND_RETRY,
            diagnostic.recommendation,
        )
        assertTrue(diagnostic.summary.contains("Unplug and replug", ignoreCase = true))
    }

    @Test
    fun rawHeartbeatWithoutProtocolFrameStillDoesNotRecommendFlashing() {
        val diagnostic = baseDiagnostic(
            sawProtocolFrame = false,
            sawNrsuiteHeartbeat = true,
            sawDeviceActivity = true,
        )

        assertEquals(
            ConnectionDiagnosticRecommendation.REPLUG_AND_RETRY,
            diagnostic.recommendation,
        )
    }

    @Test
    fun romBootLogStillRecommendsFirmwareFlash() {
        val diagnostic = baseDiagnostic(
            sawProtocolFrame = false,
            sawRomBootLog = true,
        )

        assertEquals(
            ConnectionDiagnosticRecommendation.FLASH_FIRMWARE,
            diagnostic.recommendation,
        )
    }

    @Test
    fun silentPortWithoutProtocolResponseRecommendsFirmwareFlash() {
        val diagnostic = baseDiagnostic(
            sawProtocolFrame = false,
            sawNrsuiteHeartbeat = false,
        )

        assertEquals(
            ConnectionDiagnosticRecommendation.FLASH_FIRMWARE,
            diagnostic.recommendation,
        )
    }

    @Test
    fun transportErrorWithoutProtocolResponseRecommendsRetry() {
        val diagnostic = baseDiagnostic(
            sawProtocolFrame = false,
            transportError = "USB read failed",
        )

        assertEquals(
            ConnectionDiagnosticRecommendation.RETRY,
            diagnostic.recommendation,
        )
    }

    private fun baseDiagnostic(
        sawProtocolFrame: Boolean,
        sawNrsuiteHeartbeat: Boolean = false,
        sawDeviceActivity: Boolean = false,
        sawRomBootLog: Boolean = false,
        transportError: String? = null,
    ) = ConnectionDiagnostic(
        permissionGranted = true,
        deviceDetected = true,
        portOpened = true,
        handshakeAttempts = 2,
        sawDeviceActivity = sawDeviceActivity,
        sawRomBootLog = sawRomBootLog,
        sawInvalidFirmwareHeader = false,
        sawProtocolFrame = sawProtocolFrame,
        sawNrsuiteHeartbeat = sawNrsuiteHeartbeat,
        transportError = transportError,
    )
}
