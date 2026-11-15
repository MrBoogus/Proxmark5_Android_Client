package com.proxmark5.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class T55xxModeTiming(
    val startGap: Int,
    val writeGap: Int,
    val write0: Int,
    val write1: Int,
    val readGap: Int,
    val write2: Int = 0,
    val write3: Int = 0
)

data class T55xxTimingConfig(
    val modes: List<T55xxModeTiming>,
    val persist: Boolean = false
) {
    init {
        require(modes.size == 4) { "T55xx timing configuration requires exactly 4 downlink modes" }
    }

    /**
     * Serializes into 58-byte t55xx_setconfig_t struct:
     * - conf: 4 modes * 7 uint16_t LE = 56 bytes
     * - persist: uint8_t = 1 byte
     * - rfu: uint8_t = 1 byte
     */
    fun toWireBytes(): ByteArray {
        val bb = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN)
        for (m in modes) {
            bb.putShort(m.startGap.toShort())
            bb.putShort(m.writeGap.toShort())
            bb.putShort(m.write0.toShort())
            bb.putShort(m.write1.toShort())
            bb.putShort(m.readGap.toShort())
            bb.putShort(m.write2.toShort())
            bb.putShort(m.write3.toShort())
        }
        bb.put(if (persist) 1.toByte() else 0.toByte())
        bb.put(0.toByte()) // rfu
        return bb.array()
    }

    companion object {
        fun defaultTimings(persist: Boolean = false): T55xxTimingConfig {
            return T55xxTimingConfig(
                modes = listOf(
                    // Mode 0: Fixed Bit Length
                    T55xxModeTiming(
                        startGap = 29 * 8, // 232
                        writeGap = 17 * 8, // 136
                        write0   = 15 * 8, // 120
                        write1   = 47 * 8, // 376
                        readGap  = 15 * 8, // 120
                        write2   = 0,
                        write3   = 0
                    ),
                    // Mode 1: Long Leading Reference
                    T55xxModeTiming(
                        startGap = 29 * 8,
                        writeGap = 17 * 8,
                        write0   = 15 * 8,
                        write1   = 47 * 8,
                        readGap  = 15 * 8,
                        write2   = 0,
                        write3   = 0
                    ),
                    // Mode 2: Leading Zero
                    T55xxModeTiming(
                        startGap = 29 * 8,
                        writeGap = 17 * 8,
                        write0   = 15 * 8,
                        write1   = 47 * 8,
                        readGap  = 15 * 8,
                        write2   = 0,
                        write3   = 0
                    ),
                    // Mode 3: 1 of 4
                    T55xxModeTiming(
                        startGap = 29 * 8,
                        writeGap = 17 * 8,
                        write0   = 15 * 8,
                        write1   = 31 * 8, // 248
                        readGap  = 15 * 8,
                        write2   = 47 * 8, // 376
                        write3   = 63 * 8  // 504
                    )
                ),
                persist = persist
            )
        }
    }
}

data class T55xxBlockEntry(
    val blockNo: Int,
    val page: Int = 0,
    val value: Long? = null,
    val hexString: String = "—",
    val binaryString: String = "—",
    val asciiString: String = "—",
    val isDecodedConfig: Boolean = false,
    val configDescription: String? = null,
    val lastReadStatus: String = "Not read"
)

object T55xxPayloadBuilder {

    /**
     * Builds wire payload for CMD_LF_T55XX_READBL (8 bytes packed)
     * struct p {
     *   uint32_t password;
     *   uint8_t  blockno;
     *   uint8_t  page;
     *   bool     pwdmode;
     *   uint8_t  downlink_mode;
     * } PACKED;
     */
    fun buildReadBlock(
        blockNo: Int,
        page: Int = 0,
        password: Long = 0L,
        usePwd: Boolean = false,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED
    ): ByteArray {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt((password and 0xFFFFFFFFL).toInt())
        bb.put((blockNo and 0xFF).toByte())
        bb.put((page and 0x01).toByte())
        bb.put(if (usePwd) 1.toByte() else 0.toByte())
        bb.put((downlinkMode and 0x03).toByte())
        return bb.array()
    }

    /**
     * Builds wire payload for CMD_LF_T55XX_WRITEBL (10 bytes packed t55xx_write_block_t)
     * typedef struct {
     *   uint32_t data;
     *   uint32_t pwd;
     *   uint8_t  blockno;
     *   uint8_t  flags; // 0x01 = PwdMode, 0x02 = Page, 0x04 = testMode, 0x18 = downlink_mode << 3
     * } PACKED t55xx_write_block_t;
     */
    fun buildWriteBlock(
        blockNo: Int,
        value: Long,
        page: Int = 0,
        password: Long = 0L,
        usePwd: Boolean = false,
        testMode: Boolean = false,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED
    ): ByteArray {
        val bb = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt((value and 0xFFFFFFFFL).toInt())
        bb.putInt((password and 0xFFFFFFFFL).toInt())
        bb.put((blockNo and 0xFF).toByte())

        var flags = 0
        if (usePwd) flags = flags or 0x01
        if ((page and 1) != 0) flags = flags or 0x02
        if (testMode) flags = flags or 0x04
        flags = flags or ((downlinkMode and 3) shl 3)
        bb.put(flags.toByte())
        return bb.array()
    }

    /**
     * Builds 5-byte wakeup payload:
     * - pwd: uint32 LE
     * - flags: uint8 (downlink_mode << 3)
     */
    fun buildWakeup(
        password: Long = 0L,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED
    ): ByteArray {
        val bb = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt((password and 0xFFFFFFFFL).toInt())
        val flags = (downlinkMode and 3) shl 3
        bb.put(flags.toByte())
        return bb.array()
    }

    /**
     * Builds 1-byte reset read payload:
     * - flags: uint8 (downlink_mode << 3)
     */
    fun buildResetRead(
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED
    ): ByteArray {
        val flags = ((downlinkMode and 3) shl 3).toByte()
        return byteArrayOf(flags)
    }

    /**
     * Builds 133-byte t55xx_test_block_t struct for CMD_LF_T55XX_DANGERRAW
     */
    fun buildDangerRaw(
        bitString: String,
        timeUs: Long
    ): ByteArray {
        val bb = ByteBuffer.allocate(133).order(ByteOrder.LITTLE_ENDIAN)
        val cleanBits = bitString.trim().filter { it == '0' || it == '1' }
        val bitLen = minOf(cleanBits.length, 128)

        val dataBytes = ByteArray(128)
        for (i in 0 until bitLen) {
            dataBytes[i] = if (cleanBits[i] == '1') 1.toByte() else 0.toByte()
        }

        bb.put(dataBytes)
        bb.put(bitLen.toByte())
        bb.putInt((timeUs and 0xFFFFFFFFL).toInt())
        return bb.array()
    }

    /**
     * White Chinese handheld cloner algorithm
     */
    fun calculateWhiteClonerPassword(uid: Long): Long {
        val id = (uid and 0xFFFFFFFFL).toInt()
        val r1 = Integer.rotateLeft(id and 0x000000EC, 8)
        val r2 = Integer.rotateLeft((id.toLong() and 0x86000000L).toInt(), 16)
        val xorVal = (id and 0x86EE00EC.toInt()) xor r1 xor r2
        val pwd = 0x10303 + xorVal
        return pwd.toLong() and 0xFFFFFFFFL
    }

    /**
     * Decodes Atmel T55xx Block 0 configuration register into human-readable description
     */
    fun decodeT55xxBlock0(config: Long): String {
        val maxBlock = (config shr 4) and 0x07
        val modBits = ((config shr 12) and 0x0F).toInt()
        val rateBits = ((config shr 16) and 0x0F).toInt()
        val pwd = ((config shr 23) and 1) == 1L
        val aor = ((config shr 21) and 1) == 1L

        val modStr = when (modBits) {
            0 -> "Direct"
            1 -> "PSK1"
            2 -> "PSK2"
            3 -> "PSK3"
            4 -> "FSK1"
            5 -> "FSK2"
            6 -> "FSK1a"
            7 -> "FSK2a"
            8 -> "Manchester"
            10 -> "Biphase"
            else -> "Mod:$modBits"
        }

        val rateStr = when (rateBits) {
            0 -> "RF/8"
            1 -> "RF/16"
            2 -> "RF/32"
            3 -> "RF/40"
            4 -> "RF/50"
            5 -> "RF/64"
            6 -> "RF/100"
            7 -> "RF/128"
            else -> "Rate:$rateBits"
        }

        val flags = mutableListOf<String>()
        flags.add(modStr)
        flags.add(rateStr)
        flags.add("MaxBlk:$maxBlock")
        if (pwd) flags.add("PWD-Protected")
        if (aor) flags.add("AOR")
        if (config == 0x00148040L) flags.add("EM4100 Standard")

        return flags.joinToString(", ")
    }

    /**
     * Common default T55xx passwords from official pm3 repository dictionary
     */
    val DEFAULT_DICTIONARY: List<Long> = listOf(
        0x00000000L,
        0x51243648L,
        0x000D8787L,
        0x19920427L,
        0x002BCFCFL,
        0x50524F58L, // 'PROX'
        0xF9DCEBA0L,
        0x65857569L,
        0x05D73B9FL,
        0x89A69E60L,
        0x314159E0L,
        0x7B3D5C48L,
        0xAA55BBBBL,
        0xA5B4C3D2L,
        0x1C0B5848L,
        0x00434343L,
        0x50415353L, // 'PASS'
        0x444E4752L, // 'DNGR'
        0x4E457854L, // 'NExT'
        0x44B44CAEL,
        0x88661858L,
        0xE9920427L,
        0x575F4F4BL,
        0x50520901L,
        0x20206666L,
        0x5469616EL,
        0x7686962AL,
        0xC0F5009AL,
        0x07CEE75DL,
        0xFEEDBEEFL,
        0xDEADC0DEL,
        0xA9EF2AFCL,
        0x11111111L,
        0x22222222L,
        0x33333333L,
        0x44444444L,
        0x55555555L,
        0x66666666L,
        0x77777777L,
        0x88888888L,
        0x99999999L,
        0xAAAAAAAAL,
        0xBBBBBBBBL,
        0xCCCCCCCCL,
        0xDDDDDDDDL,
        0xEEEEEEEEL,
        0xFFFFFFFFL,
        0x01020304L,
        0x12345678L,
        0x87654321L,
        0x12341234L,
        0x69696969L,
        0x12121212L,
        0x12344321L,
        0x1234ABCDL,
        0x11112222L,
        0x13131313L,
        0x10041004L,
        0x31415926L,
        0xABCD1234L,
        0x20002000L,
        0x19721972L,
        0xAA55AA55L,
        0x55AA55AAL,
        0xA1856618L
    )
}
