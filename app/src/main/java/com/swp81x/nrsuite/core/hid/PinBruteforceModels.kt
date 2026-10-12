package com.swp81x.nrsuite.core.hid

import android.net.Uri

enum class PinBruteforceMode(val wireName: String) {
    STANDALONE_NATIVE_HID("standalone_native_hid"),
}

enum class PinBruteforceSource(val wireName: String) {
    RANGE("range"),
    WORDLIST("wordlist"),
}

data class PinBruteforceUiState(
    val state: String = "idle",
    val mode: PinBruteforceMode = PinBruteforceMode.STANDALONE_NATIVE_HID,
    val source: PinBruteforceSource? = null,
    val pinLength: Int = 4,
    val start: String = "0000",
    val end: String = "9999",
    val charset: String = "digits",
    val customChars: String = "0123456789",
    val prefix: String = "",
    val suffix: String = "",
    val timeoutMs: Int = 1000,
    val attemptsBeforeTimeout: Int = 0,
    val cooldownMs: Int = 30000,
    val interAttemptMs: Int = 400,
    val autoResume: Boolean = true,
    val sessionKey: String = "default",
    val currentSequence: Int = 0,
    val attemptsTotal: Int = 0,
    val attemptsInCycle: Int = 0,
    val lastPin: String = "",
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
    val running: Boolean get() = state == "running" || state == "armed"
}

data class PinAttemptRecord(
    val sequence: Int,
    val pin: String,
    val attemptsTotal: Int,
    val timestamp: String,
)
