package com.swp81x.nrsuite.core.credentials

/**
 * Where a credential result came from.
 *
 * PORTAL is reserved for the later rogue-AP/captive-portal credential review
 * feature. Evil Twin sessions currently use EVIL_TWIN.
 */
enum class CredentialSource {
    EVIL_TWIN,
    WPA_CRACKER,
    PORTAL,
    IMPORTED,
}

enum class CredentialStatus {
    CORRECT,
    UNVERIFIED,
    PENDING,
    INVALID_LENGTH,
}

data class CapturedCredential(
    val value: String,
    val capturedAt: String,
    val status: CredentialStatus,
    val source: CredentialSource = CredentialSource.EVIL_TWIN,
    val details: Map<String, String> = emptyMap(),
)

data class CredentialSession(
    val id: String,
    val source: CredentialSource,
    val ssid: String,
    val bssid: String,
    val channel: Int?,
    val startedAt: String,
    val endedAt: String?,
    val pcapPath: String?,
    val credentials: List<CapturedCredential>,
)
