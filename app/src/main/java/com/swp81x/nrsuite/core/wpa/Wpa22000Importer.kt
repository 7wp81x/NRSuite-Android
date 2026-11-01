package com.swp81x.nrsuite.core.wpa

enum class Wpa22000RecordKind {
    EAPOL,
    PMKID,
    MALFORMED,
}

data class Wpa22000Record(
    val lineNumber: Int,
    val kind: Wpa22000RecordKind,
    val ssid: String? = null,
    val bssid: String? = null,
    val station: String? = null,
    val handshake: WpaHandshake? = null,
    val error: String? = null,
)

data class Wpa22000ImportResult(
    val records: List<Wpa22000Record>,
) {
    val crackable: List<Wpa22000Record>
        get() = records.filter { it.kind == Wpa22000RecordKind.EAPOL && it.handshake != null }
    val pmkidCount: Int
        get() = records.count { it.kind == Wpa22000RecordKind.PMKID }
    val malformedCount: Int
        get() = records.count { it.kind == Wpa22000RecordKind.MALFORMED }
}

/**
 * Parse Hashcat mode 22000 text into app-native WPA handshakes.
 *
 * WPA*02 EAPOL records are fully reconstructed for offline wordlist
 * verification. WPA*01 PMKID records are recognized and reported separately;
 * PMKID verification is not implemented yet and must not be silently treated
 * as a crackable EAPOL handshake.
 */
fun parseWpa22000(
    lines: Sequence<String>,
): Wpa22000ImportResult {
    val records = mutableListOf<Wpa22000Record>()
    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        records += parseWpa22000Line(index + 1, line)
    }
    return Wpa22000ImportResult(records)
}

private fun parseWpa22000Line(lineNumber: Int, line: String): Wpa22000Record {
    val fields = line.split('*')
    if (fields.size < 2 || fields[0] != "WPA") {
        return malformed(lineNumber, "Not a Hashcat WPA line")
    }
    return when (fields[1]) {
        "02" -> parseEapolRecord(lineNumber, fields)
        "01" -> parsePmkidRecord(lineNumber, fields)
        else -> malformed(lineNumber, "Unsupported 22000 record type ${fields[1]}")
    }
}

private fun parseEapolRecord(
    lineNumber: Int,
    fields: List<String>,
): Wpa22000Record {
    if (fields.size != 9) {
        return malformed(lineNumber, "WPA*02 line must contain 9 fields")
    }
    val mic = decodeHex(fields[2], expectedBytes = 16)
        ?: return malformed(lineNumber, "Invalid MIC")
    val bssid = decodeMac(fields[3])
        ?: return malformed(lineNumber, "Invalid AP MAC")
    val station = decodeMac(fields[4])
        ?: return malformed(lineNumber, "Invalid station MAC")
    val ssidBytes = decodeHex(fields[5])
        ?: return malformed(lineNumber, "Invalid ESSID hex")
    if (ssidBytes.isEmpty() || ssidBytes.size > 32) {
        return malformed(lineNumber, "ESSID must be 1-32 bytes")
    }
    val aNonce = decodeHex(fields[6], expectedBytes = 32)
        ?: return malformed(lineNumber, "Invalid ANonce")
    val eapol = decodeHex(fields[7])
        ?: return malformed(lineNumber, "Invalid EAPOL hex")
    if (eapol.size < MIN_EAPOL_BYTES) {
        return malformed(lineNumber, "EAPOL frame is too short")
    }
    if (fields[8].length < 2 || fields[8].any { it.digitToIntOrNull(16) == null }) {
        return malformed(lineNumber, "Invalid message-pair field")
    }

    val keyInfo = ((eapol[5].toInt() and 0xFF) shl 8) or
        (eapol[6].toInt() and 0xFF)
    val keyDescriptorVersion = keyInfo and 0x7
    if (keyDescriptorVersion != 2) {
        return malformed(
            lineNumber,
            "Unsupported EAPOL key descriptor version $keyDescriptorVersion",
        )
    }
    val sNonce = eapol.copyOfRange(SNONCE_OFFSET, SNONCE_OFFSET + 32)
    if (sNonce.all { it == 0.toByte() }) {
        return malformed(lineNumber, "EAPOL SNonce is missing")
    }
    val replayCounter = readUnsignedLongBigEndian(eapol, REPLAY_OFFSET)

    val handshake = WpaHandshake(
        m1Bssid = bssid,
        m1Station = station,
        aNonce = aNonce,
        m1ReplayCounter = replayCounter,
        m2Bssid = bssid,
        m2Station = station,
        sNonce = sNonce,
        eapolM2 = eapol,
        mic = mic,
        m2ReplayCounter = replayCounter,
        keyDescriptorVersion = keyDescriptorVersion,
    )
    val ssid = ssidBytes.toString(Charsets.UTF_8)
    return Wpa22000Record(
        lineNumber = lineNumber,
        kind = Wpa22000RecordKind.EAPOL,
        ssid = ssid,
        bssid = bssid.toMacString(),
        station = station.toMacString(),
        handshake = handshake,
    )
}

private fun parsePmkidRecord(
    lineNumber: Int,
    fields: List<String>,
): Wpa22000Record {
    if (fields.size < 7) {
        return malformed(lineNumber, "WPA*01 line is missing fields")
    }
    val bssid = decodeMac(fields[3])?.toMacString()
    val station = decodeMac(fields[4])?.toMacString()
    val ssidBytes = decodeHex(fields[5])
    return Wpa22000Record(
        lineNumber = lineNumber,
        kind = Wpa22000RecordKind.PMKID,
        ssid = ssidBytes?.toString(Charsets.UTF_8),
        bssid = bssid,
        station = station,
        error = "PMKID verification is not supported yet",
    )
}

private fun malformed(lineNumber: Int, reason: String): Wpa22000Record =
    Wpa22000Record(
        lineNumber = lineNumber,
        kind = Wpa22000RecordKind.MALFORMED,
        error = reason,
    )

private fun decodeMac(value: String): ByteArray? =
    decodeHex(value, expectedBytes = 6)

private fun decodeHex(value: String, expectedBytes: Int? = null): ByteArray? {
    val clean = value.trim().replace(":", "").replace("-", "")
    if (clean.length % 2 != 0 || clean.any { it.digitToIntOrNull(16) == null }) return null
    val bytes = ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
    return if (expectedBytes == null || bytes.size == expectedBytes) bytes else null
}

private fun readUnsignedLongBigEndian(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (index in offset until offset + 8) {
        value = (value shl 8) or (bytes[index].toLong() and 0xFF)
    }
    return value
}

private fun ByteArray.toMacString(): String =
    joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }

private const val REPLAY_OFFSET = 9
private const val SNONCE_OFFSET = 17
private const val MIN_EAPOL_BYTES = 99
