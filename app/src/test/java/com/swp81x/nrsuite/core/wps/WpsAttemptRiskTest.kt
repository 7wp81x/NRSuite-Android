package com.swp81x.nrsuite.core.wps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WpsAttemptRiskTest {

    @Test
    fun `NACK without M1-M3 is medium and says no crackable material was captured`() {
        val assessment = assess(
            session(stage = "NACK", nackSeen = true, frames = 2),
        )

        assertEquals(WpsAttemptRisk.MEDIUM, assessment.risk)
        assertTrue(
            assessment.reasons.any {
                it.contains("no M1-M3 material captured", ignoreCase = true)
            },
        )
    }

    @Test
    fun `M1-M3 followed by NACK remains high risk`() {
        val assessment = assess(
            session(
                stage = "NACK",
                m1Seen = true,
                m2Seen = true,
                m3Seen = true,
                nackSeen = true,
                frames = 5,
            ),
        )

        assertEquals(WpsAttemptRisk.HIGH, assessment.risk)
        assertTrue(
            assessment.reasons.any {
                it.contains("WPS M1/M2/M3 exchange observed", ignoreCase = true)
            },
        )
        assertTrue(assessment.reasons.any { it.contains("after M1/M2/M3 material") })
    }

    @Test
    fun `M7 and M8 indicate likely PSK transfer and raise high risk`() {
        val assessment = assess(
            session(
                stage = "M8",
                m1Seen = true,
                m2Seen = true,
                m3Seen = true,
                m4Seen = true,
                m5Seen = true,
                m6Seen = true,
                m7Seen = true,
                m8Seen = true,
                frames = 10,
            ),
        )

        assertEquals(WpsAttemptRisk.HIGH, assessment.risk)
        assertTrue(
            assessment.reasons.any {
                it.contains("WPA PSK likely transferred", ignoreCase = true)
            },
        )
    }

    @Test
    fun `M7 without M8 is only medium credential-transfer evidence`() {
        val assessment = assess(
            session(stage = "M7", m7Seen = true, frames = 7),
        )

        assertEquals(WpsAttemptRisk.MEDIUM, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("without M8", ignoreCase = true) })
    }

    @Test
    fun `partial M1-M3 evidence is medium`() {
        val assessment = assess(
            session(stage = "M2", m1Seen = true, m2Seen = true, frames = 3),
        )

        assertEquals(WpsAttemptRisk.MEDIUM, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("Partial WPS M1-M3 evidence") })
    }

    private fun assess(session: WpsAttemptSession): WpsAttemptRiskAssessment =
        assessWpsAttemptRisk(
            session = session,
            targetSessionCount = 1,
            distinctClientCount = 1,
            lockState = WpsLockState.UNKNOWN,
            lockConfidence = WpsLockConfidence.NONE,
            lockTransitioned = false,
        )

    private fun session(
        stage: String,
        m1Seen: Boolean = false,
        m2Seen: Boolean = false,
        m3Seen: Boolean = false,
        m4Seen: Boolean = false,
        m5Seen: Boolean = false,
        m6Seen: Boolean = false,
        m7Seen: Boolean = false,
        m8Seen: Boolean = false,
        m2dSeen: Boolean = false,
        nackSeen: Boolean = false,
        frames: Int = 1,
    ): WpsAttemptSession = WpsAttemptSession(
        sessionId = 1L,
        bssid = "AA:BB:CC:DD:EE:FF",
        client = "11:22:33:44:55:66",
        ssid = "Test AP",
        channel = 6,
        rssi = -50,
        stage = stage,
        messageType = 0,
        m1Seen = m1Seen,
        m2Seen = m2Seen,
        m3Seen = m3Seen,
        m4Seen = m4Seen,
        m5Seen = m5Seen,
        m6Seen = m6Seen,
        m7Seen = m7Seen,
        m8Seen = m8Seen,
        m2dSeen = m2dSeen,
        nackSeen = nackSeen,
        frames = frames,
        firstSeenMs = 100L,
        lastSeenMs = 1_100L,
        durationMs = 1_000L,
    )
}
