package com.proxmark5.client.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bluetooth Low Energy GATT transport for Proxmark5 BWM (service 0xAE86 / characteristic 0xAE88).
 */
class BleGattTransport(
    private val gatt: BluetoothGatt,
    private val characteristic: BluetoothGattCharacteristic,
    override val deviceName: String,
    override val address: String,
    initialChunkSize: Int = 20
) : ProxmarkTransport {

    override val kind: TransportKind = TransportKind.BLE_GATT

    @Volatile
    override var maxWriteChunkBytes: Int = initialChunkSize.coerceIn(20, 509)
        private set

    override val interChunkDelayMs: Long = 12L

    @Volatile
    private var open: Boolean = true

    override val isConnected: Boolean
        get() = open

    private val writeMutex = Mutex()

    fun updateChunkSize(newChunkSize: Int) {
        maxWriteChunkBytes = newChunkSize.coerceIn(20, 509)
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(bytes: ByteArray): Boolean {
        if (!open) return false
        return writeMutex.withLock {
            if (!open) return@withLock false
            var offset = 0
            while (offset < bytes.size) {
                val chunkSize = minOf(maxWriteChunkBytes, bytes.size - offset)
                val chunk = bytes.copyOfRange(offset, offset + chunkSize)

                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                var initiated = writeChunkInternal(chunk)
                if (!initiated) {
                    Log.w("PM5Ble", "writeCharacteristic not initiated immediately, waiting 35ms and retrying...")
                    delay(35)
                    initiated = writeChunkInternal(chunk)
                }

                if (!initiated) {
                    Log.e("PM5Ble", "writeCharacteristic retry failed at offset $offset")
                    return@withLock false
                }

                // Pacing between chunks for BWM ESP32 UART
                delay(interChunkDelayMs)
                offset += chunkSize
            }
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeChunkInternal(chunk: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = chunk
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        open = false
        try {
            gatt.disconnect()
            gatt.close()
        } catch (_: Exception) {
        }
    }
}
