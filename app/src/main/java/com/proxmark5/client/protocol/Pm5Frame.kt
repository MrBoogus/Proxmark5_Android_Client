package com.proxmark5.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Representation of an incoming NG response frame from Proxmark5
 */
data class Pm5ResponseFrame(
    val magic: Int,
    val length: Int,
    val ng: Boolean,
    val status: Byte,
    val reason: Byte,
    val cmd: Int,
    val payload: ByteArray,
    val crc: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pm5ResponseFrame
        if (magic != other.magic) return false
        if (length != other.length) return false
        if (ng != other.ng) return false
        if (status != other.status) return false
        if (reason != other.reason) return false
        if (cmd != other.cmd) return false
        if (!payload.contentEquals(other.payload)) return false
        if (crc != other.crc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = magic
        result = 31 * result + length
        result = 31 * result + ng.hashCode()
        result = 31 * result + status
        result = 31 * result + reason
        result = 31 * result + cmd
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crc
        return result
    }
}

/**
 * Builder for outgoing Proxmark5 NG command frames
 */
object Pm5CommandFrameBuilder {

    /**
     * Serializes a command into the wire format:
     * - Preamble (8 bytes):
     *     magic (4 bytes): 0x61334D50 ("PM3a")
     *     length_and_ng (2 bytes, little-endian): length (15 bits) | (ng=1 << 15)
     *     cmd (2 bytes, little-endian): opcode
     * - Payload (len bytes)
     * - Postamble (2 bytes, little-endian): CRC16 or COMMANDNG_POSTAMBLE_MAGIC
     */
    fun build(cmd: Int, payload: ByteArray? = null, useCrc: Boolean = true): ByteArray {
        val payloadLen = payload?.size ?: 0
        val totalSize = Pm5Constants.COMMAND_PREAMBLE_SIZE + payloadLen + Pm5Constants.POSTAMBLE_SIZE

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Magic 0x61334D50 ("PM3a")
        buffer.putInt(Pm5Constants.COMMANDNG_PREAMBLE_MAGIC)

        // length (15 bits) + ng bit (bit 15 = 1)
        val lenAndNg = (payloadLen and 0x7FFF) or 0x8000
        buffer.putShort(lenAndNg.toShort())

        // cmd (16 bits)
        buffer.putShort((cmd and 0xFFFF).toShort())

        // Payload
        if (payload != null && payloadLen > 0) {
            buffer.put(payload)
        }

        // Postamble (CRC16 over preamble + payload)
        val frameBytes = buffer.array()
        val crc = if (useCrc) {
            Pm5Crc16.compute(frameBytes, 0, Pm5Constants.COMMAND_PREAMBLE_SIZE + payloadLen)
        } else {
            Pm5Constants.COMMANDNG_POSTAMBLE_MAGIC
        }

        buffer.putShort((crc and 0xFFFF).toShort())

        return frameBytes
    }
}
