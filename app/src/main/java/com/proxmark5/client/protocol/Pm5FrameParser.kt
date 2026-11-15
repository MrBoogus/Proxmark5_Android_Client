package com.proxmark5.client.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streaming frame reassembly parser for Proxmark5 NG response frames.
 *
 * Scans continuous stream for magic sequence ("PM3b" / 0x50, 0x4D, 0x33, 0x62),
 * parses 10-byte preamble, payload, and 2-byte postamble, and performs
 * ISO 14443-A CRC-16 validation.
 */
class Pm5FrameParser(
    private val onFrameReceived: (Pm5ResponseFrame) -> Unit,
    private val onParseError: ((String) -> Unit)? = null
) {
    private val accumulator = ByteArrayOutputStream()

    // Wire representation of 0x62334D50 ("PM3b") in little-endian
    private val magicBytes = byteArrayOf(0x50.toByte(), 0x4D.toByte(), 0x33.toByte(), 0x62.toByte())

    @Synchronized
    fun onBytesReceived(data: ByteArray, length: Int) {
        if (length <= 0) return
        accumulator.write(data, 0, length)
        processBuffer()
    }

    @Synchronized
    fun reset() {
        accumulator.reset()
    }

    private fun processBuffer() {
        var bytes = accumulator.toByteArray()

        while (true) {
            if (bytes.size < Pm5Constants.RESPONSE_PREAMBLE_SIZE) {
                break
            }

            // Find magic sequence index
            val magicIndex = findMagic(bytes)
            if (magicIndex < 0) {
                // Keep only trailing 3 bytes in case magic spans across chunk boundaries
                val keepLen = minOf(bytes.size, 3)
                val tail = bytes.copyOfRange(bytes.size - keepLen, bytes.size)
                accumulator.reset()
                accumulator.write(tail)
                break
            }

            // If magic is not at start, discard leading trash
            if (magicIndex > 0) {
                bytes = bytes.copyOfRange(magicIndex, bytes.size)
                accumulator.reset()
                accumulator.write(bytes)
            }

            if (bytes.size < Pm5Constants.RESPONSE_PREAMBLE_SIZE) {
                break
            }

            // Parse preamble
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = bb.int
            val lenAndNg = bb.short.toInt() and 0xFFFF
            val payloadLen = lenAndNg and 0x7FFF
            val isNg = (lenAndNg and 0x8000) != 0
            val status = bb.get()
            val reason = bb.get()
            val cmd = bb.short.toInt() and 0xFFFF

            // Sanity check length
            if (payloadLen > Pm5Constants.MAX_PAYLOAD_SIZE) {
                onParseError?.invoke("Payload length $payloadLen exceeds max; resyncing")
                // Drop 1 byte and continue
                bytes = bytes.copyOfRange(1, bytes.size)
                accumulator.reset()
                accumulator.write(bytes)
                continue
            }

            val totalFrameSize = Pm5Constants.RESPONSE_PREAMBLE_SIZE + payloadLen + Pm5Constants.POSTAMBLE_SIZE
            if (bytes.size < totalFrameSize) {
                // Wait for remainder of frame to arrive
                break
            }

            // Extract payload
            val payload = ByteArray(payloadLen)
            bb.get(payload)

            // Extract postamble CRC
            val crc = bb.short.toInt() and 0xFFFF

            // Validate CRC
            val isValidCrc = Pm5Crc16.validate(bytes, 0, Pm5Constants.RESPONSE_PREAMBLE_SIZE + payloadLen, crc)
            if (!isValidCrc) {
                onParseError?.invoke(
                    "Invalid CRC on cmd 0x%04X: got 0x%04X, expected 0x%04X".format(
                        cmd,
                        crc,
                        Pm5Crc16.compute(bytes, 0, Pm5Constants.RESPONSE_PREAMBLE_SIZE + payloadLen)
                    )
                )
                // Skip magic to resync
                bytes = bytes.copyOfRange(magicBytes.size, bytes.size)
                accumulator.reset()
                accumulator.write(bytes)
                continue
            }

            // Successfully received frame
            val frame = Pm5ResponseFrame(
                magic = magic,
                length = payloadLen,
                ng = isNg,
                status = status,
                reason = reason,
                cmd = cmd,
                payload = payload,
                crc = crc
            )

            // Slide buffer past this frame
            val remaining = bytes.size - totalFrameSize
            if (remaining > 0) {
                bytes = bytes.copyOfRange(totalFrameSize, bytes.size)
                accumulator.reset()
                accumulator.write(bytes)
            } else {
                accumulator.reset()
                bytes = ByteArray(0)
            }

            onFrameReceived(frame)
        }
    }

    private fun findMagic(data: ByteArray): Int {
        val limit = data.size - 4
        for (i in 0..limit) {
            if (data[i] == magicBytes[0] &&
                data[i + 1] == magicBytes[1] &&
                data[i + 2] == magicBytes[2] &&
                data[i + 3] == magicBytes[3]
            ) {
                return i
            }
        }
        return -1
    }
}
