package com.swp81x.nrsuite.core.handshake

import com.swp81x.nrsuite.core.defense.ClientObservation
import com.swp81x.nrsuite.core.wifi.NetworkTarget

/**
 * Ranks AP/client pairs for Auto Handshake.
 *
 * The score intentionally weighs RSSI first, then observed client activity,
 * then WPA2-PSK suitability. Captured targets remain in the history but are
 * only made [AutoHandshakeTargetStatus.PENDING] again after they disappear
 * from a scan and later reappear.
 */
object AutoHandshakeRanker {

    fun refresh(
        history: List<AutoHandshakeTarget>,
        aps: List<NetworkTarget>,
        clients: List<ClientObservation>,
        config: AutoHandshakeConfig,
        nowMs: Long,
    ): List<AutoHandshakeTarget> {
        val byBssid = LinkedHashMap<String, AutoHandshakeTarget>()
        history.forEach { target -> byBssid[target.bssidKey] = target }

        val seenKeys = aps.map { it.bssidKey() }.toSet()

        // Mark previously seen targets as out of range when they are absent.
        byBssid.keys.toList().forEach { key ->
            val target = byBssid[key] ?: return@forEach
            if (key !in seenKeys) {
                byBssid[key] = target.copy(inRange = false)
            }
        }

        aps.distinctBy { it.bssidKey() }.forEach { ap ->
            val key = ap.bssidKey()
            val previous = byBssid[key]
            val wasOutOfRange = previous != null && !previous.inRange
            val lastEndedAt = previous?.lastAttempt?.endedAtMs ?: 0L
            val cooldownPassed = nowMs - lastEndedAt >= config.failedTargetCooldownMs

            if (!isSupportedSecurity(ap.security)) {
                byBssid[key] = (previous ?: AutoHandshakeTarget(ap)).copy(
                    target = ap,
                    status = AutoHandshakeTargetStatus.UNSUPPORTED,
                    score = 0.0,
                    inRange = true,
                    lastSeenAtMs = nowMs,
                )
                return@forEach
            }

            val observed = clients.filter { client ->
                val directBssid = client.bssid?.equals(ap.bssid, ignoreCase = true) == true
                val ssidMatch = ap.ssid.isNotBlank() &&
                    client.ssid?.equals(ap.ssid, ignoreCase = true) == true
                directBssid || ssidMatch
            }

            val status = when {
                previous == null -> AutoHandshakeTargetStatus.PENDING
                previous.status == AutoHandshakeTargetStatus.CAPTURED && wasOutOfRange ->
                    AutoHandshakeTargetStatus.PENDING
                previous.status == AutoHandshakeTargetStatus.CAPTURED ->
                    AutoHandshakeTargetStatus.CAPTURED
                previous.status == AutoHandshakeTargetStatus.UNSUPPORTED ->
                    AutoHandshakeTargetStatus.PENDING
                previous.status == AutoHandshakeTargetStatus.NO_HANDSHAKE && wasOutOfRange ->
                    AutoHandshakeTargetStatus.PENDING
                previous.status == AutoHandshakeTargetStatus.NO_HANDSHAKE && !cooldownPassed ->
                    AutoHandshakeTargetStatus.NO_HANDSHAKE
                previous.status == AutoHandshakeTargetStatus.SKIPPED && !wasOutOfRange ->
                    AutoHandshakeTargetStatus.SKIPPED
                else -> AutoHandshakeTargetStatus.PENDING
            }

            byBssid[key] = (previous ?: AutoHandshakeTarget(ap)).copy(
                target = ap,
                observedClients = observed.map { it.mac }.distinct().take(32),
                deauthClient = observed.firstOrNull {
                    it.bssid?.equals(ap.bssid, ignoreCase = true) == true
                }?.mac,
                score = score(ap, observed),
                status = status,
                inRange = true,
                lastSeenAtMs = nowMs,
            )
        }

        return byBssid.values.sortedByDescending { it.score }
    }

    fun eligibleTargets(
        targets: List<AutoHandshakeTarget>,
        maxTargets: Int,
    ): List<AutoHandshakeTarget> = targets
        .asSequence()
        .filter {
            it.inRange &&
                (it.status == AutoHandshakeTargetStatus.PENDING ||
                    it.status == AutoHandshakeTargetStatus.INTERRUPTED)
        }
        .sortedWith(
            compareByDescending<AutoHandshakeTarget> { it.target.rssi }
                .thenByDescending { it.score },
        )
        .take(maxTargets.coerceAtLeast(1))
        .toList()

    fun isSupportedSecurity(security: String): Boolean {
        val normalized = security.trim().uppercase()
        return normalized in setOf(
            "WPA2-PSK",
            "WPA/WPA2-PSK",
            "WPA2/WPA3-PSK",
        )
    }

    private fun NetworkTarget.bssidKey(): String =
        bssid.uppercase().replace(":", "")

    private fun score(ap: NetworkTarget, clients: List<ClientObservation>): Double {
        val rssiScore = ((ap.rssi + 95).toDouble() / 55.0).coerceIn(0.0, 1.0)

        val directClients = clients.count {
            it.bssid?.equals(ap.bssid, ignoreCase = true) == true
        }
        val ssidMatches = clients.count {
            ap.ssid.isNotBlank() && it.ssid?.equals(ap.ssid, ignoreCase = true) == true
        }
        val clientScore = ((directClients * 0.6) + (ssidMatches * 0.2)).coerceIn(0.0, 1.0)

        val securityScore = when (ap.security.trim().uppercase()) {
            "WPA2-PSK" -> 1.0
            "WPA/WPA2-PSK" -> 0.9
            "WPA2/WPA3-PSK" -> 0.7
            else -> 0.0
        }

        return (0.55 * rssiScore) + (0.35 * clientScore) + (0.10 * securityScore)
    }
}
