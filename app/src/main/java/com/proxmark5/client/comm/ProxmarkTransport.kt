package com.proxmark5.client.comm

/**
 * Physical communication medium used to reach a Proxmark device.
 */
enum class TransportKind(val label: String) {
    BLE_GATT("BLE"),
    CLASSIC_BT_SPP("BT SPP"),
    USB_CDC("USB OTG"),
    WIFI_TCP("WiFi TCP")
}

/**
 * Shared byte-stream transport abstraction beneath the PM3 NG protocol layer.
 */
interface ProxmarkTransport {
    val kind: TransportKind
    val deviceName: String
    val address: String
    val maxWriteChunkBytes: Int
    val interChunkDelayMs: Long
    val isConnected: Boolean

    suspend fun write(bytes: ByteArray): Boolean
    fun close()
}
