package com.proxmark5.client.comm

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice
import com.proxmark5.client.protocol.Pm5ResponseFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState
    data class Negotiating(val stage: String) : ConnectionState
    data class Connected(
        val deviceName: String,
        val address: String,
        val transport: String,
        val mtu: Int = 23
    ) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Parsed CMD_VERSION (0x0107) firmware reply matching armsrc/appmain.c SendVersion():
 * [0..3]  uint32_t id (chip ID)
 * [4..7]  uint32_t section_size (flash bootloader/OS size)
 * [8..11] uint32_t versionstr_len
 * [12..]  char versionstr[versionstr_len] + optional uint32_t flash_size
 */
data class VersionInfo(
    val chipId: Long,
    val sectionSize: Long,
    val versionString: String,
    val totalFlashSize: Long? = null
) {
    companion object {
        private val ANSI_REGEX = Regex("\\u001B\\[[;\\d]*[A-Za-z]")

        fun fromPayload(payload: ByteArray): VersionInfo? {
            if (payload.size < 12) return null
            val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val chipId = bb.int.toLong() and 0xFFFFFFFFL
            val sectionSize = bb.int.toLong() and 0xFFFFFFFFL
            val declaredLenLong = bb.int.toLong() and 0xFFFFFFFFL
            val availableAfterHeader = payload.size - 12
            val strLen = declaredLenLong.coerceIn(0L, availableAfterHeader.toLong()).toInt()
            val verStr = if (strLen > 0) {
                val raw = String(payload, 12, strLen, Charsets.UTF_8)
                ANSI_REGEX.replace(raw, "").replace("\u0000", "").trim()
            } else {
                ""
            }
            val trailingOffset = 12 + strLen
            val totalFlash = if (payload.size - trailingOffset >= 4) {
                ByteBuffer.wrap(payload, trailingOffset, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int.toLong() and 0xFFFFFFFFL
            } else {
                null
            }
            return VersionInfo(
                chipId = chipId,
                sectionSize = sectionSize,
                versionString = verStr,
                totalFlashSize = totalFlash
            )
        }
    }
}

data class T55xxReadResult(
    val blockNo: Int,
    val page: Int,
    val rawValue: Long?,
    val hexValue: String,
    val binaryValue: String,
    val asciiValue: String = "—",
    val status: Int,
    val message: String
)

data class T55xxPwdCheckResult(
    val found: Boolean,
    val candidateHex: String?,
    val candidateVal: Long?,
    val status: Int,
    val message: String
)

data class RawTransactionResult(
    val cmd: Int,
    val cmdName: String,
    val txBytesHex: String,
    val rxFrame: Pm5ResponseFrame?,
    val rxStatus: Int?,
    val rxReason: Int?,
    val rxPayloadHex: String,
    val latencyMs: Long,
    val success: Boolean,
    val statusText: String
)

data class DiscoveredBleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isProxmark5: Boolean = false
)

data class DiscoveredUsbDevice(
    val device: UsbDevice,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val devicePath: String
)
