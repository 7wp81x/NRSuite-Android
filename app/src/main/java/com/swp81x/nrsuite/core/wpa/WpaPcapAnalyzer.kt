package com.swp81x.nrsuite.core.wpa

import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.core.wifi.PcapSsidParser

data class WpaPcapAnalysis(
    val candidates: List<WpaHandshakeCandidate>,
    val detectedSsids: List<DetectedSsid>,
) {
    val completeCandidates: List<WpaHandshakeCandidate>
        get() = candidates.filter { it.handshake.isComplete }
}

/**
 * Parse all complete M1/M2 exchanges and SSID advertisements from one capture.
 */
fun analyzeWpaPcap(reader: PcapReader): WpaPcapAnalysis {
    val collector = WpaHandshakeCollector()
    val detected = LinkedHashMap<String, DetectedSsid>()
    reader.forEachPacket { frame ->
        collector.addFrame(frame)
        PcapSsidParser.parse(frame)?.let { ssid ->
            detected["${ssid.ssid}|${ssid.bssid}"] = ssid
        }
        true
    }
    val ssidByBssid = linkedMapOf<String, String>()
    detected.values.forEach { entry ->
        if (entry.bssid.isNotBlank()) {
            ssidByBssid.putIfAbsent(entry.bssid.uppercase(), entry.ssid)
        }
    }
    val candidates = collector.finish().map { handshake ->
        WpaHandshakeCandidate(
            handshake = handshake,
            ssid = handshake.bssid?.toHexMac()?.let { ssidByBssid[it.uppercase()] },
        )
    }
    return WpaPcapAnalysis(
        candidates = candidates,
        detectedSsids = detected.values.toList(),
    )
}

private fun ByteArray.toHexMac(): String =
    joinToString(":") { value -> "%02X".format(value.toInt() and 0xFF) }
