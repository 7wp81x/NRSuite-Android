package com.swp81x.nrsuite.core.hid

import android.net.Uri

enum class BlePinBruteforceSource(val wireName: String) {
    RANGE("range"),
    WORDLIST("wordlist"),
}

data class BlePinBruteforceUiState(
    val state: String = "idle",
    val source: BlePinBruteforceSource? = null,
    val configured: Boolean = false,
    val bleConnected: Boolean = false,
    val bleAdvertising: Boolean = false,
    val bleState: String = "stopped",
    val bonded: Boolean = false,
    val peer: String = "",
    val deviceName: String = "NRSuite PIN Keyboard",
    val autoReconnect: Boolean = true,
    val autoStartOnConnect: Boolean = false,
    val pauseOnDisconnect: Boolean = true,
    val pinLength: Int = 4,
    val start: String = "0000",
    val end: String = "9999",
    val timeoutMs: Int = 1000,
    val maxAttempts: Int = 10000,
    val persistEvery: Int = 1,
    val estimatedTotal: Long = 0L,
    val progressPercent: Int = 0,
    val stopReason: String = "",
    val attemptsBeforeTimeout: Int = 0,
    val cooldownMs: Int = 30000,
    val interAttemptMs: Int = 400,
    val typingDelayMs: Int = 20,
    val startDelayMs: Int = 500,
    val autoResume: Boolean = true,
    val sessionKey: String = "default",
    val currentSequence: Int = 0,
    val attemptsTotal: Int = 0,
    val attemptsInCycle: Int = 0,
    val lastPin: String = "",
    val nextPin: String = "",
    val cooldownActive: Boolean = false,
    val cooldownRemainingMs: Long = 0L,
    val lastError: String = "",
    val startedAt: Int = 0,
    val updatedAt: Int = 0,
    val persistedInNvs: Boolean = false,
    val wordlistUri: Uri? = null,
    val wordlistName: String? = null,
    val wordlistLines: Int = 0,
    val wordlistBytes: Long = 0L,
    val wordlistUploading: Boolean = false,
    val wordlistUploadProgress: Int = 0,
    val wordlistUploadedToDevice: Boolean = false,
    val wordlistError: String? = null,
    val starting: Boolean = false,
) {
    val running: Boolean get() = state == "running"
    val paused: Boolean get() = state == "paused"
    val finished: Boolean get() = state == "done" || state == "error"
    val bleReady: Boolean get() = bleConnected
}

data class BlePinAttemptRecord(
    val sequence: Int,
    val pin: String,
    val attemptsTotal: Int,
    val timestamp: String,
    val skipped: Boolean = false,
)
