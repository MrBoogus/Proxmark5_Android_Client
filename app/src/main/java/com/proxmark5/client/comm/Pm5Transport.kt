package com.proxmark5.client.comm

import android.bluetooth.BluetoothDevice
import com.proxmark5.client.protocol.Pm5ResponseFrame

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

data class VersionInfo(
    val chipId: Long,
    val sectionSize: Long,
    val versionString: String,
    val totalFlashSize: Long? = null
)

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
