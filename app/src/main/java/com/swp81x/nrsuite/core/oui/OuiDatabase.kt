package com.swp81x.nrsuite.core.oui

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val DEFAULT_OUI_CSV_URL = "https://standards-oui.ieee.org/oui/oui.csv"

data class OuiVendor(
    val ouiPrefix: String,
    val vendor: String,
)

data class MacLookupResult(
    val normalizedMac: String,
    val ouiPrefix: String?,
    val vendor: String?,
    val locallyAdministered: Boolean,
    val multicast: Boolean,
    val broadcast: Boolean,
    val databaseReady: Boolean,
)

sealed interface OuiDatabaseStatus {
    data object NotDownloaded : OuiDatabaseStatus
    data object Downloading : OuiDatabaseStatus
    data class Ready(
        val vendorCount: Int,
        val updatedAt: String,
    ) : OuiDatabaseStatus
    data class Error(val message: String) : OuiDatabaseStatus
}

class OuiDatabaseRepository(
    private val dataFile: File,
    private val csvUrl: String = DEFAULT_OUI_CSV_URL,
) {
    private val _status = MutableStateFlow<OuiDatabaseStatus>(OuiDatabaseStatus.NotDownloaded)
    val status: StateFlow<OuiDatabaseStatus> = _status.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val vendors = HashMap<String, String>()

    fun isReady(): Boolean = vendors.isNotEmpty()

    suspend fun load() {
        if (!dataFile.exists()) {
            _status.value = OuiDatabaseStatus.NotDownloaded
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                parseCsv(dataFile.readText())
            }
            check(vendors.isNotEmpty()) { "OUI database contained no entries" }
            _status.value = OuiDatabaseStatus.Ready(
                vendorCount = vendors.size,
                updatedAt = dataFile.lastModified().toReadableTime(),
            )
        }.onFailure { error ->
            _status.value = OuiDatabaseStatus.Error(error.message ?: "Could not read OUI database")
        }
    }

    suspend fun download() {
        _status.value = OuiDatabaseStatus.Downloading
        _progress.value = 0f
        runCatching {
            val text = withContext(Dispatchers.IO) {
                val connection = (URL(csvUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NRSuite/1.0 (Android)")
                    setRequestProperty("Accept", "text/csv,application/octet-stream,*/*")
                }
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("OUI database server returned HTTP $responseCode")
                }
                val totalBytes = connection.contentLengthLong
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (totalBytes > 0) {
                            _progress.value = (readTotal * 100f / totalBytes).coerceIn(0f, 100f)
                        }
                    }
                }
                connection.disconnect()
                output.toString(Charsets.UTF_8.name())
            }
            withContext(Dispatchers.IO) {
                dataFile.parentFile?.mkdirs()
                dataFile.writeText(text)
                parseCsv(text)
            }
            check(vendors.isNotEmpty()) { "OUI database contained no entries" }
            _progress.value = 100f
            _status.value = OuiDatabaseStatus.Ready(
                vendorCount = vendors.size,
                updatedAt = dataFile.lastModified().toReadableTime(),
            )
        }.onFailure { error ->
            _status.value = OuiDatabaseStatus.Error(
                error.message ?: "OUI database download failed"
            )
        }
        _progress.value = null
    }

    fun lookup(mac: String): OuiVendor? {
        val normalized = normalizeMac(mac) ?: return null
        val prefix = normalized.split(":").take(3).joinToString(":")
        val vendor = vendors[prefix] ?: return null
        return OuiVendor(ouiPrefix = prefix, vendor = vendor)
    }

    fun detailedLookup(mac: String): MacLookupResult? {
        val normalized = normalizeMac(mac) ?: return null
        val prefix = normalized.split(":").take(3).joinToString(":")
        val firstOctet = normalized.substringBefore(":").toIntOrNull(16) ?: 0
        return MacLookupResult(
            normalizedMac = normalized,
            ouiPrefix = prefix,
            vendor = vendors[prefix],
            locallyAdministered = (firstOctet and 0x02) != 0,
            multicast = (firstOctet and 0x01) != 0,
            broadcast = normalized == "FF:FF:FF:FF:FF:FF",
            databaseReady = vendors.isNotEmpty(),
        )
    }

    private fun parseCsv(raw: String) {
        val parsed = HashMap<String, String>()
        raw.lineSequence().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val fields = parseCsvLine(line)
            if (fields.size < 3) return@forEach
            val assignment = fields[1].trim().trim('"').uppercase()
            val organization = fields[2].trim().trim('"')
            if (assignment.length != 6 || assignment.any { it.digitToIntOrNull(16) == null }) {
                return@forEach
            }
            val prefix = "${assignment.substring(0, 2)}:" +
                "${assignment.substring(2, 4)}:" +
                assignment.substring(4, 6).uppercase()
            parsed[prefix] = organization
        }
        vendors.clear()
        vendors.putAll(parsed)
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                inQuotes && char == '"' -> inQuotes = false
                !inQuotes && char == '"' -> inQuotes = true
                !inQuotes && char == ',' -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        fields += current.toString()
        return fields
    }

    private fun normalizeMac(input: String): String? {
        val parts = input.trim().uppercase().replace("-", ":").split(":")
        if (parts.size != 6) return null
        if (parts.any { it.length != 2 || it.toIntOrNull(16) == null }) return null
        return parts.joinToString(":")
    }

    private fun Long.toReadableTime(): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
