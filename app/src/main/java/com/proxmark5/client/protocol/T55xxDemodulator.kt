package com.proxmark5.client.protocol

import kotlin.math.abs

enum class T55xxModulation(val label: String, val block0Code: Int) {
    MANCHESTER("Manchester", 0x08),
    BIPHASE("Biphase", 0x10)
}

sealed interface T55xxDemodResult {
    data class Success(
        val blockValue: Long,
        val clock: Int,
        val modulation: T55xxModulation,
        val confidence: Double,
        val peakToPeak: Int
    ) : T55xxDemodResult

    data class Failure(
        val reason: String,
        val peakToPeak: Int = 0
    ) : T55xxDemodResult
}

/**
 * Pure-Kotlin client-side T55xx BigBuf ASK (Manchester / Biphase) demodulator with strict
 * amplitude, transition-coherence, 32-bit periodicity, Block 0 register plausibility, and
 * 2-read agreement checks.
 *
 * Returns [T55xxDemodResult.Failure] ("could not decode") whenever uncertain instead of guessing.
 */
object T55xxDemodulator {
    const val DEFAULT_SKIP_SAMPLES = 256
    const val MIN_SAMPLES = 1024
    const val MIN_PEAK_TO_PEAK = 24
    const val MIN_TRANSITION_QUALITY = 0.90

    private val CANDIDATE_CLOCKS = intArrayOf(32, 64, 16, 40, 50, 100, 128, 8)

    fun demodulate(
        samples: ByteArray,
        isBlock0: Boolean,
        skipSamples: Int = DEFAULT_SKIP_SAMPLES
    ): T55xxDemodResult {
        if (samples.size < MIN_SAMPLES || skipSamples >= samples.size - 512) {
            return T55xxDemodResult.Failure("could not decode")
        }

        val adc = IntArray(samples.size) { samples[it].toInt() and 0xFF }
        var minVal = 255
        var maxVal = 0
        for (i in skipSamples until adc.size) {
            val v = adc[i]
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val pkPk = maxVal - minVal
        if (pkPk < MIN_PEAK_TO_PEAK) {
            return T55xxDemodResult.Failure("could not decode", peakToPeak = pkPk)
        }

        val mid = (minVal + maxVal) / 2.0
        val polar = IntArray(adc.size) { idx -> if (adc[idx] >= mid) 1 else -1 }

        // Evaluate candidate ASK modulations & bit clocks, prioritizing zero-shift frame alignment
        for (mod in listOf(T55xxModulation.MANCHESTER, T55xxModulation.BIPHASE)) {
            for (clk in CANDIDATE_CLOCKS) {
                val res = tryClockAndModulation(polar, skipSamples, clk, mod, isBlock0, pkPk)
                if (res != null) return res
            }
        }

        return T55xxDemodResult.Failure("could not decode", peakToPeak = pkPk)
    }

    /**
     * Requires two independent BigBuf captures to both demodulate validly and agree on the
     * decoded 32-bit block value, clock, and modulation.
     */
    fun verifyTwoReads(
        read1: ByteArray,
        read2: ByteArray,
        isBlock0: Boolean,
        skipSamples: Int = DEFAULT_SKIP_SAMPLES
    ): T55xxDemodResult {
        val r1 = demodulate(read1, isBlock0 = isBlock0, skipSamples = skipSamples)
        if (r1 !is T55xxDemodResult.Success) {
            return T55xxDemodResult.Failure(
                reason = "could not decode",
                peakToPeak = (r1 as? T55xxDemodResult.Failure)?.peakToPeak ?: 0
            )
        }
        val r2 = demodulate(read2, isBlock0 = isBlock0, skipSamples = skipSamples)
        if (r2 !is T55xxDemodResult.Success) {
            return T55xxDemodResult.Failure(
                reason = "could not decode",
                peakToPeak = (r2 as? T55xxDemodResult.Failure)?.peakToPeak ?: 0
            )
        }
        if (r1.blockValue != r2.blockValue || r1.clock != r2.clock || r1.modulation != r2.modulation) {
            return T55xxDemodResult.Failure(
                reason = "could not decode",
                peakToPeak = minOf(r1.peakToPeak, r2.peakToPeak)
            )
        }
        return r1
    }

    private fun tryClockAndModulation(
        polar: IntArray,
        skipSamples: Int,
        clk: Int,
        mod: T55xxModulation,
        isBlock0: Boolean,
        pkPk: Int
    ): T55xxDemodResult.Success? {
        val avail = polar.size - skipSamples
        val blockCount = minOf(3, avail / (clk * 32))
        if (blockCount < 2) return null
        val bitCount = blockCount * 32

        val step = maxOf(1, clk / 16)
        var bestPhase = -1
        var bestQuality = -1.0
        var bestBits: IntArray? = null

        var phase = 0
        while (phase < clk) {
            val start = skipSamples + phase
            if (start + bitCount * clk <= polar.size) {
                val attempt = when (mod) {
                    T55xxModulation.MANCHESTER -> decodeManchester(polar, start, clk, bitCount)
                    T55xxModulation.BIPHASE -> decodeBiphase(polar, start, clk, bitCount)
                }
                if (attempt != null && attempt.second > bestQuality) {
                    bestQuality = attempt.second
                    bestPhase = phase
                    bestBits = attempt.first
                }
            }
            phase += step
        }

        // Fine-tune phase +/- step around best candidate
        if (bestPhase >= 0 && step > 1) {
            val lo = (bestPhase - step).coerceAtLeast(0)
            val hi = (bestPhase + step).coerceAtMost(clk - 1)
            for (p in lo..hi) {
                val start = skipSamples + p
                if (start + bitCount * clk <= polar.size) {
                    val attempt = when (mod) {
                        T55xxModulation.MANCHESTER -> decodeManchester(polar, start, clk, bitCount)
                        T55xxModulation.BIPHASE -> decodeBiphase(polar, start, clk, bitCount)
                    }
                    if (attempt != null && attempt.second > bestQuality) {
                        bestQuality = attempt.second
                        bestBits = attempt.first
                    }
                }
            }
        }

        val bits = bestBits ?: return null
        if (bestQuality < MIN_TRANSITION_QUALITY) return null

        // Check exact 32-bit repetition across all decoded blocks in window
        val words = LongArray(blockCount) { blkIdx -> packWord32(bits, blkIdx * 32) }
        for (k in 1 until words.size) {
            if (words[k] != words[0]) return null
        }

        val rawWord = words[0]
        if (rawWord == 0L || rawWord == 0xFFFFFFFFL) return null

        val resolvedWord = if (isBlock0) {
            resolveAlignedBlock0(rawWord, clk, mod) ?: return null
        } else {
            rawWord
        }

        return T55xxDemodResult.Success(
            blockValue = resolvedWord,
            clock = clk,
            modulation = mod,
            confidence = bestQuality,
            peakToPeak = pkPk
        )
    }

    private fun decodeManchester(
        polar: IntArray,
        start: Int,
        clk: Int,
        bitCount: Int
    ): Pair<IntArray, Double>? {
        val half = clk / 2
        if (half < 2) return null
        val bits = IntArray(bitCount)
        var validBits = 0

        for (b in 0 until bitCount) {
            val cellStart = start + b * clk
            var sumFirst = 0
            var sumSecond = 0
            for (i in 0 until half) sumFirst += polar[cellStart + i]
            for (i in half until clk) sumSecond += polar[cellStart + i]
            val meanFirst = sumFirst.toDouble() / half
            val meanSecond = sumSecond.toDouble() / (clk - half)

            // IEEE/T55x7 Manchester: '1' = High then Low; '0' = Low then High
            if (abs(meanFirst) >= 0.65 && abs(meanSecond) >= 0.65 && meanFirst * meanSecond < 0.0) {
                validBits++
            }
            bits[b] = if (meanFirst >= meanSecond) 1 else 0
        }

        return bits to (validBits.toDouble() / bitCount)
    }

    private fun decodeBiphase(
        polar: IntArray,
        start: Int,
        clk: Int,
        bitCount: Int
    ): Pair<IntArray, Double>? {
        val half = clk / 2
        if (half < 2) return null
        val bits = IntArray(bitCount)
        var validCells = 0
        var prevEndSign = if (start >= half) {
            var s = 0
            for (i in 1..half) s += polar[start - i]
            if (s >= 0) 1 else -1
        } else {
            0
        }

        for (b in 0 until bitCount) {
            val cellStart = start + b * clk
            var sumFirst = 0
            var sumSecond = 0
            for (i in 0 until half) sumFirst += polar[cellStart + i]
            for (i in half until clk) sumSecond += polar[cellStart + i]
            val meanFirst = sumFirst.toDouble() / half
            val meanSecond = sumSecond.toDouble() / (clk - half)
            val signFirst = if (meanFirst >= 0.0) 1 else -1
            val signSecond = if (meanSecond >= 0.0) 1 else -1

            val boundaryOk = (prevEndSign == 0) || (signFirst != prevEndSign)
            if (boundaryOk && abs(meanFirst) >= 0.60 && abs(meanSecond) >= 0.60) {
                validCells++
            }
            bits[b] = if (signFirst != signSecond) 1 else 0
            prevEndSign = signSecond
        }

        return bits to (validCells.toDouble() / bitCount)
    }

    private fun packWord32(bits: IntArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 32) {
            value = (value shl 1) or (bits[offset + i].toLong() and 1L)
        }
        return value and 0xFFFFFFFFL
    }

    private fun resolveAlignedBlock0(
        word: Long,
        detectedClk: Int,
        detectedMod: T55xxModulation
    ): Long? {
        fun matchesSelfConsistentBlock0(candidate: Long): Boolean {
            if (!T55xxPayloadBuilder.isPlausibleBlock0Config(candidate)) return false
            val decoded = T55xxPayloadBuilder.decodeBlock0Structured(candidate)
            return decoded.bitRateClock == detectedClk && decoded.modulationCode == detectedMod.block0Code
        }

        // First prefer direct unshifted alignment
        if (matchesSelfConsistentBlock0(word)) return word

        // Otherwise check cyclic shifts within the 32-bit cycle only if unique
        val w32 = word.toInt()
        val matches = mutableListOf<Long>()
        for (shift in 1 until 32) {
            val rotated = ((w32 shl shift) or (w32 ushr (32 - shift))).toLong() and 0xFFFFFFFFL
            if (matchesSelfConsistentBlock0(rotated)) {
                matches.add(rotated)
            }
        }
        return if (matches.size == 1) matches[0] else null
    }
}
