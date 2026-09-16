package com.swp81x.nrsuite.core.wpa

data class WpaHandshake(
    var bssid: ByteArray? = null,
    var station: ByteArray? = null,
    var aNonce: ByteArray? = null,
    var sNonce: ByteArray? = null,
    var eapolM2: ByteArray? = null,
    var mic: ByteArray? = null,
    var keyDescriptorVersion: Int = 2,
) {
    val isComplete: Boolean
        get() = bssid != null &&
            station != null &&
            aNonce != null &&
            sNonce != null &&
            eapolM2 != null &&
            mic != null

    fun copyHandshake(): WpaHandshake = copy(
        bssid = bssid?.copyOf(),
        station = station?.copyOf(),
        aNonce = aNonce?.copyOf(),
        sNonce = sNonce?.copyOf(),
        eapolM2 = eapolM2?.copyOf(),
        mic = mic?.copyOf(),
    )
}

data class EvilTwinResult(
    val password: String,
    val status: Status,
) {
    enum class Status { CORRECT, INCORRECT, PENDING }
}
