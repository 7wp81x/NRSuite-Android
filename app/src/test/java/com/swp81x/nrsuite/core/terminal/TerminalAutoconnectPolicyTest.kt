package com.swp81x.nrsuite.core.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAutoconnectPolicyTest {
    @Test
    fun `attempts only when enabled and saved configuration exists`() {
        val disconnected = TerminalConnectionState.Disconnected()
        assertTrue(shouldAttemptTerminalAutoconnect(true, true, disconnected))
        assertFalse(shouldAttemptTerminalAutoconnect(false, true, disconnected))
        assertFalse(shouldAttemptTerminalAutoconnect(true, false, disconnected))
    }

    @Test
    fun `never replaces a connecting connected or failed session`() {
        assertFalse(
            shouldAttemptTerminalAutoconnect(
                autoconnectEnabled = true,
                savedConfigurationAvailable = true,
                state = TerminalConnectionState.Connecting,
            ),
        )
        assertFalse(
            shouldAttemptTerminalAutoconnect(
                autoconnectEnabled = true,
                savedConfigurationAvailable = true,
                state = TerminalConnectionState.Connected("127.0.0.1", 8022, "nrsuite"),
            ),
        )
        assertFalse(
            shouldAttemptTerminalAutoconnect(
                autoconnectEnabled = true,
                savedConfigurationAvailable = true,
                state = TerminalConnectionState.Failed("offline"),
            ),
        )
    }
}
