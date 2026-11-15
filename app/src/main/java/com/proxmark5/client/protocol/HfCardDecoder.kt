package com.proxmark5.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Hf14aCard(
    val uidHex: String,
    val uidLen: Int,
    val atqaHex: String,
    val sakHex: String,
    val atsHex: String?,
    val cardType: String,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Hf14aCard
        return uidHex == other.uidHex && atqaHex == other.atqaHex && sakHex == other.sakHex
    }

    override fun hashCode(): Int {
        var result = uidHex.hashCode()
        result = 31 * result + atqaHex.hashCode()
        result = 31 * result + sakHex.hashCode()
        return result
    }
}

object HfCardDecoder {

    /**
     * Parses iso14a_raw_resp_t + iso14a_card_select_t from CMD_HF_ISO14443A_READER response payload.
     * Wire layout:
     *   [0..1] uint16_t len (LE)
     *   [2]    uint8_t  sel (0 = no card / failed, 1 = OK w/ ATS, 2 = OK no ATS, 3 = proprietary)
     *   [3]    uint8_t  rfu
     *   [4..]  iso14a_card_select_t (uid[10], uidlen, atqa[2], sak, ats_len, ats[256])
     */
    fun parseIso14a(payload: ByteArray): Hf14aCard? {
        if (payload.size < 4 + 15) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val respLen = bb.short.toInt() and 0xFFFF
        val sel = bb.get().toInt() and 0xFF
        bb.get() // rfu
        if (sel == 0 || respLen < 15) return null

        val uidBytes = ByteArray(10)
        bb.get(uidBytes)
        val uidLen = bb.get().toInt() and 0xFF
        val atqa0 = bb.get().toInt() and 0xFF
        val atqa1 = bb.get().toInt() and 0xFF
        val sak = bb.get().toInt() and 0xFF
        val atsLen = if (bb.hasRemaining()) bb.get().toInt() and 0xFF else 0

        if (uidLen !in 1..10) return null
        val uidSlice = uidBytes.copyOfRange(0, uidLen)
        val uidHex = uidSlice.joinToString("") { "%02X".format(it) }

        val atqaHex = "%02X%02X".format(atqa1, atqa0)
        val sakHex = "%02X".format(sak)

        var atsHex: String? = null
        if (atsLen > 0 && bb.remaining() >= atsLen) {
            val atsBytes = ByteArray(atsLen)
            bb.get(atsBytes)
            atsHex = atsBytes.joinToString("") { "%02X".format(it) }
        }

        val cardType = identify14a(atqaHex, sak, uidLen)

        return Hf14aCard(
            uidHex = uidHex,
            uidLen = uidLen,
            atqaHex = atqaHex,
            sakHex = sakHex,
            atsHex = atsHex,
            cardType = cardType,
            rawBytes = payload
        )
    }

    fun identify14a(atqa: String, sak: Int, uidLen: Int): String {
        return when (sak) {
            0x08 -> "MIFARE Classic 1K"
            0x18 -> "MIFARE Classic 4K"
            0x00 -> if (uidLen == 7) "MIFARE Ultralight / NTAG21x" else "MIFARE Ultralight"
            0x20 -> "MIFARE DESFire / ISO 14443-4"
            0x28 -> "JCOP Smartcard / Emulated Card"
            0x09 -> "MIFARE Mini (0.3K)"
            0x88 -> "Infineon MIFARE Classic 1K"
            0x01 -> "MIFARE Plus 2K (SL1)"
            0x11 -> "MIFARE Plus 4K (SL1)"
            0x24 -> "MIFARE Plus 2K (SL2)"
            0x10 -> "MIFARE Plus 2K (SL3)"
            0x19 -> "MIFARE Classic 2K"
            else -> "ISO 14443-A (SAK: 0x%02X, ATQA: %s)".format(sak, atqa)
        }
    }
}
