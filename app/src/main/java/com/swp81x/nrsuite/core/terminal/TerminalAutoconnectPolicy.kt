package com.swp81x.nrsuite.core.terminal

/**
 * Autoconnect must never replace an in-flight or live session, and it must not
 * run without both the user setting and a saved key/connection configuration.
 */
internal fun shouldAttemptTerminalAutoconnect(
    autoconnectEnabled: Boolean,
    savedConfigurationAvailable: Boolean,
    state: TerminalConnectionState,
): Boolean =
    autoconnectEnabled &&
        savedConfigurationAvailable &&
        state is TerminalConnectionState.Disconnected
