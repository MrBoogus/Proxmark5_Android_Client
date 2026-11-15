package com.proxmark5.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Em4100ParityResult(
    val isValid: Boolean,
    val rowParities: List<Boolean>, // 10 rows: true if parity valid
    val colParities: List<Boolean>, // 4 columns: true if parity valid
    val stopBitValid: Boolean,
    val raw64Bits: String          // 64-char string '0' and '1'
)

data class Em4100Tag(
    val uidHex: String,            // 10 hex characters (e.g. "0102030405")
    val uidLong: Long,             // 40-bit numerical ID
    val facilityCode: Int,         // 8-bit FC: (uid & 0x00FF0000) >> 16
    val cardNumber: Int,           // 16-bit Card: uid & 0x0000FFFF
    val card24Bit: Long,           // 24-bit combined: uid & 0x00FFFFFF
    val formattedPm3: String,      // "( %05d_%03d_%08d )"
    val parity: Em4100ParityResult,
    val rawWire64: ByteArray       // 8 bytes (64 bits)
)

object Em4100Decoder {

    fun fromHex(hexStr: String): Em4100Tag? {
        val clean = hexStr.trim().replace(":", "").replace(" ", "").uppercase()
        if (clean.length != 10 && clean.length != 8) return null
        val full10 = clean.padStart(10, '0')
        val uidLong = try {
            full10.toLong(16) and 0xFFFFFFFFFFL
        } catch (_: Exception) {
            return null
        }
        return fromLong(uidLong)
    }

    fun fromLong(uid: Long): Em4100Tag {
        val uidClean = uid and 0xFFFFFFFFFFL
        val uidHex = "%010X".format(uidClean)

        val lo32 = uidClean and 0xFFFFFFFFL
        val cardNumber = (lo32 and 0xFFFFL).toInt()
        val facilityCode = ((lo32 shr 16) and 0xFFL).toInt()
        val card24Bit = lo32 and 0xFFFFFFL
        val formattedPm3 = "( %05d_%03d_%08d )".format(cardNumber, facilityCode, card24Bit)

        // Generate 64-bit frame:
        // 9 ones (preamble)
        val bitList = ArrayList<Int>(64)
        repeat(9) { bitList.add(1) }

        val rowParities = ArrayList<Boolean>(10)
        val colAccumulator = IntArray(4)

        // 10 nibbles, 4 data bits + 1 even horizontal parity bit each
        for (i in 9 downTo 0) {
            val shift = i * 4
            val nibble = ((uidClean shr shift) and 0xFL).toInt()
            var rowP = 0
            for (bitIdx in 3 downTo 0) {
                val b = (nibble shr bitIdx) and 1
                bitList.add(b)
                rowP = rowP xor b
                colAccumulator[3 - bitIdx] = colAccumulator[3 - bitIdx] xor b
            }
            bitList.add(rowP)
            rowParities.add(true)
        }

        // 4 column parity bits
        val colParities = ArrayList<Boolean>(4)
        for (c in 0 until 4) {
            bitList.add(colAccumulator[c])
            colParities.add(true)
        }

        // 1 stop bit (always 0)
        bitList.add(0)

        val bitsString = bitList.joinToString("")
        val wireBytes = ByteArray(8)
        for (byteIdx in 0 until 8) {
            var b = 0
            for (bitIdx in 0 until 8) {
                b = (b shl 1) or bitList[byteIdx * 8 + bitIdx]
            }
            wireBytes[byteIdx] = b.toByte()
        }

        val parityResult = Em4100ParityResult(
            isValid = true,
            rowParities = rowParities,
            colParities = colParities,
            stopBitValid = true,
            raw64Bits = bitsString
        )

        return Em4100Tag(
            uidHex = uidHex,
            uidLong = uidClean,
            facilityCode = facilityCode,
            cardNumber = cardNumber,
            card24Bit = card24Bit,
            formattedPm3 = formattedPm3,
            parity = parityResult,
            rawWire64 = wireBytes
        )
    }

    /**
     * Builds wire payload for CMD_LF_EM410X_CLONE (12 bytes packed)
     */
    fun buildClonePayload(
        uid: Long,
        targetChip: Int = Pm5Constants.TARGET_T55X7,
        clock: Int = 64,
        addElectra: Boolean = false
    ): ByteArray {
        val bb = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        val isQ5 = (targetChip == Pm5Constants.TARGET_Q5)
        val isEm = (targetChip == Pm5Constants.TARGET_EM4305)

        bb.put(if (isQ5) 1.toByte() else 0.toByte())
        bb.put(if (isEm) 1.toByte() else 0.toByte())
        bb.put(if (addElectra) 1.toByte() else 0.toByte())
        bb.put((clock and 0xFF).toByte())

        val high = ((uid shr 32) and 0xFFFFFFFFL).toInt()
        val low = (uid and 0xFFFFFFFFL).toInt()
        bb.putInt(high)
        bb.putInt(low)

        return bb.array()
    }
}
