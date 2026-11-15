package com.proxmark5.client.protocol

/**
 * CRC-16/ISO14443-A calculation matching Proxmark Iceman firmware:
 * - Polynomial: 0x1021 (CCITT)
 * - Initial: 0xC6C6
 * - Refin: true (reflected input byte)
 * - Refout: true (reflected output word)
 * - Result: first = (rem & 0xFF), second = ((rem >> 8) & 0xFF)
 *   combined postamble word = ((first << 8) | second)
 */
object Pm5Crc16 {

    private fun reflect8(b: Int): Int {
        var v = b and 0xFF
        v = ((v and 0xF0) shr 4) or ((v and 0x0F) shl 4)
        v = ((v and 0xCC) shr 2) or ((v and 0x33) shl 2)
        v = ((v and 0xAA) shr 1) or ((v and 0x55) shl 1)
        return v and 0xFF
    }

    private fun reflect16(w: Int): Int {
        var v = w and 0xFFFF
        v = ((v and 0xFF00) ushr 8) or ((v and 0x00FF) shl 8)
        v = ((v and 0xF0F0) ushr 4) or ((v and 0x0F0F) shl 4)
        v = ((v and 0xCCCC) ushr 2) or ((v and 0x3333) shl 2)
        v = ((v and 0xAAAA) ushr 1) or ((v and 0x5555) shl 1)
        return v and 0xFFFF
    }

    /**
     * Compute CRC_14443_A over slice [offset .. offset + length - 1].
     */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        if (length <= 0) return 0
        var rem = 0xC6C6
        val poly = 0x1021

        for (i in offset until (offset + length)) {
            val c = reflect8(data[i].toInt())
            rem = rem xor (c shl 8)
            for (j in 0 until 8) {
                rem = if ((rem and 0x8000) != 0) {
                    ((rem shl 1) xor poly) and 0xFFFF
                } else {
                    (rem shl 1) and 0xFFFF
                }
            }
        }

        rem = reflect16(rem)
        val first = rem and 0xFF
        val second = (rem ushr 8) and 0xFF
        return ((first shl 8) or second) and 0xFFFF
    }

    /**
     * Validates received CRC against either calculated CRC16 or RESPONSENG_POSTAMBLE_MAGIC (0x3362)
     */
    fun validate(data: ByteArray, offset: Int, length: Int, expectedCrc: Int): Boolean {
        if (expectedCrc == Pm5Constants.RESPONSENG_POSTAMBLE_MAGIC) {
            return true
        }
        val calculated = compute(data, offset, length)
        return calculated == (expectedCrc and 0xFFFF)
    }
}
