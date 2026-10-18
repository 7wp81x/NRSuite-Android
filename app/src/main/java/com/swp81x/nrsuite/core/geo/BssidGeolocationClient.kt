package com.swp81x.nrsuite.core.geo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BssidGeolocationResult(
    val bssid: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val source: String,
)

internal fun normalizeBssid(input: String): String? {
    val compact = input.trim()
        .uppercase()
        .replace(":", "")
        .replace("-", "")
        .replace(".", "")
    if (compact.length != 12 || compact.any { it.digitToIntOrNull(16) == null }) return null
    val normalized = compact.chunked(2).joinToString(":")
    if (normalized == "00:00:00:00:00:00" || normalized == "FF:FF:FF:FF:FF:FF") return null
    return normalized
}

/**
 * Minimal independent implementation of the WLOC request/response format used
 * by Apple's geolocation service. This intentionally does not depend on the
 * WaveDigger implementation; only the wire-level protocol behavior is used.
 */
object BssidGeolocationClient {
    private const val GLOBAL_ENDPOINT = "https://gs-loc.apple.com/clls/wloc"
    private const val CHINA_ENDPOINT = "https://gs-loc-cn.apple.com/clls/wloc"

    private const val INITIAL_WLOC_BYTES_HEX =
        "0001000a656e2d3030315f3030310013636f6d2e6170706c652e6c6f636174696f6e64000c31372e352e312e323146393000000001000000"

    private val initialWlocBytes: ByteArray by lazy {
        INITIAL_WLOC_BYTES_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    suspend fun lookup(rawBssid: String): BssidGeolocationResult? = withContext(Dispatchers.IO) {
        val bssid = normalizeBssid(rawBssid)
            ?: throw IllegalArgumentException("Invalid BSSID")

        val request = buildWlocRequest(bssid)
        val endpoints = listOf(
            "global" to GLOBAL_ENDPOINT,
            "china" to CHINA_ENDPOINT,
        )
        var lastError: Throwable? = null

        for ((source, endpoint) in endpoints) {
            try {
                val response = postWloc(endpoint, request)
                val result = decodeWlocResponse(response, bssid, source)
                if (result != null) return@withContext result
            } catch (t: Throwable) {
                lastError = t
            }
        }

        if (lastError != null && lastError !is NotFoundException) {
            throw IOException("Location service failed: ${lastError.message ?: "unknown error"}", lastError)
        }
        null
    }

    private fun buildWlocRequest(bssid: String): ByteArray {
        val wifiDevice = ProtoWriter().apply {
            writeString(1, bssid)
        }.toByteArray()

        val deviceType = ProtoWriter().apply {
            writeString(1, "iPhone OS17.5/21F79")
            writeString(2, "iPhone12,1")
        }.toByteArray()

        val message = ProtoWriter().apply {
            writeBytes(2, wifiDevice)
            writeSInt32(3, 0)
            writeSInt32(4, -1)
            writeBytes(33, deviceType)
        }.toByteArray()

        require(message.size <= 0xFF) { "WLOC request is too large" }
        return initialWlocBytes + byteArrayOf(message.size.toByte()) + message
    }

    private fun postWloc(endpoint: String, body: ByteArray): ByteArray {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 12_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Charset", "utf-8")
            setRequestProperty("Accept-Language", "en-us")
            setRequestProperty("User-Agent", "locationd/2890.16.16 CFNetwork/1496.0.7 Darwin/23.5.0")
        }

        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_NOT_FOUND) throw NotFoundException()
                throw IOException("HTTP $status ${connection.responseMessage.orEmpty()}".trim())
            }

            var response = connection.inputStream.use { it.readBytes() }
            if (response.size >= 2 &&
                response[0] == 0x1f.toByte() &&
                response[1] == 0x8b.toByte()
            ) {
                response = GZIPInputStream(ByteArrayInputStream(response)).use { it.readBytes() }
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private class NotFoundException : IOException("BSSID not found")

    private data class ProtoField(
        val number: Int,
        val wireType: Int,
        val varint: Long? = null,
        val bytes: ByteArray? = null,
    )

    private class ProtoWriter {
        private val output = ByteArrayOutputStream()

        fun writeString(field: Int, value: String) {
            writeBytes(field, value.toByteArray(Charsets.UTF_8))
        }

        fun writeBytes(field: Int, value: ByteArray) {
            writeVarint(((field shl 3) or 2).toLong())
            writeVarint(value.size.toLong())
            output.write(value)
        }

        fun writeSInt32(field: Int, value: Int) {
            writeVarint((field shl 3).toLong())
            writeVarint(((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL)
        }

        fun toByteArray(): ByteArray = output.toByteArray()

        private fun writeVarint(value: Long) {
            var remaining = value
            while (true) {
                if ((remaining and 0x7fL.inv()) == 0L) {
                    output.write(remaining.toInt())
                    return
                }
                output.write(((remaining and 0x7fL) or 0x80L).toInt())
                remaining = remaining ushr 7
            }
        }
    }

    internal fun decodeWlocResponse(
        response: ByteArray,
        expectedBssid: String,
        source: String,
    ): BssidGeolocationResult? {
        if (response.size <= 10) return null
        val fields = parseFields(response.copyOfRange(10, response.size))
        val expected = normalizeBssid(expectedBssid) ?: return null

        for (field in fields) {
            if (field.number != 2 || field.wireType != 2) continue
            val deviceFields = parseFields(field.bytes ?: continue)
            val bssid = deviceFields.firstOrNull { it.number == 1 && it.wireType == 2 }
                ?.bytes
                ?.toString(Charsets.UTF_8)
                ?.let(::normalizeBssid)
                ?: continue
            if (bssid != expected) continue

            val locationBytes = deviceFields.firstOrNull { it.number == 2 && it.wireType == 2 }
                ?.bytes
                ?: return null
            val locationFields = parseFields(locationBytes)
            val latitudeRaw = locationFields.firstOrNull { it.number == 1 && it.wireType == 0 }
                ?.varint
                ?: return null
            val longitudeRaw = locationFields.firstOrNull { it.number == 2 && it.wireType == 0 }
                ?.varint
                ?: return null
            val latitude = latitudeRaw / 100_000_000.0
            val longitude = longitudeRaw / 100_000_000.0
            if (latitude == -180.0 && longitude == -180.0) return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

            val accuracy = locationFields
                .firstOrNull { it.number == 3 && it.wireType == 0 }
                ?.varint
                ?.takeIf { it > 0 }
                ?.toDouble()

            return BssidGeolocationResult(
                bssid = bssid,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracy,
                source = source,
            )
        }
        return null
    }

    private fun parseFields(data: ByteArray): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        var index = 0
        while (index < data.size) {
            val (tag, afterTag) = readVarint(data, index) ?: break
            index = afterTag
            val number = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            when (wireType) {
                0 -> {
                    val (value, next) = readVarint(data, index) ?: break
                    fields += ProtoField(number, wireType, varint = value)
                    index = next
                }
                1 -> {
                    if (index + 8 > data.size) break
                    index += 8
                }
                2 -> {
                    val (lengthValue, next) = readVarint(data, index) ?: break
                    val length = lengthValue.toInt()
                    if (length < 0 || next + length > data.size) break
                    fields += ProtoField(
                        number,
                        wireType,
                        bytes = data.copyOfRange(next, next + length),
                    )
                    index = next + length
                }
                5 -> {
                    if (index + 4 > data.size) break
                    index += 4
                }
                else -> break
            }
        }
        return fields
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (index < data.size && shift < 64) {
            val byte = data[index].toInt() and 0xFF
            result = result or ((byte and 0x7f).toLong() shl shift)
            index++
            if ((byte and 0x80) == 0) return result to index
            shift += 7
        }
        return null
    }
}
