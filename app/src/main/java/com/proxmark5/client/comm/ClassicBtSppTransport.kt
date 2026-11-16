package com.proxmark5.client.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.proxmark5.client.protocol.Pm5Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Classic Bluetooth SPP (RFCOMM Serial Port Profile) transport for PM3 RDV4 Blueshark / HC-06.
 *
 * Uses standard SPP UUID 00001101-0000-1000-8000-00805F9B34FB with insecure RFCOMM fallback.
 */
class ClassicBtSppTransport(
    private val device: BluetoothDevice,
    private val adapter: BluetoothAdapter?,
    override val deviceName: String,
    override val address: String
) : ProxmarkTransport {

    override val kind: TransportKind = TransportKind.CLASSIC_BT_SPP
    override val maxWriteChunkBytes: Int = 512
    override val interChunkDelayMs: Long = 2L

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var inStream: InputStream? = null

    @Volatile
    private var outStream: OutputStream? = null

    @Volatile
    private var open: Boolean = false

    private var readJob: Job? = null
    private val writeMutex = Mutex()

    override val isConnected: Boolean
        get() = open && socket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(
        scope: CoroutineScope,
        onBytesReceived: (ByteArray, Int) -> Unit,
        onDisconnected: (String?) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            adapter?.cancelDiscovery()
        } catch (_: SecurityException) {
        }

        val candidates = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(Pm5Constants.SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(Pm5Constants.SPP_UUID) },
            {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
            {
                device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            }
        )

        var connectedSocket: BluetoothSocket? = null
        var lastError: Exception? = null

        for (factory in candidates) {
            var candidate: BluetoothSocket? = null
            try {
                candidate = factory()
                candidate.connect()
                connectedSocket = candidate
                break
            } catch (e: IOException) {
                lastError = e
                try {
                    candidate?.close()
                } catch (_: Exception) {
                }
                // Wait briefly so HC-06 L2CAP state finishes closing before the next channel attempt.
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                }
            } catch (e: ReflectiveOperationException) {
                lastError = e
            }
        }

        if (connectedSocket == null) {
            Log.e("PM3Spp", "SPP connect failed for $address: ${lastError?.message}")
            return@withContext false
        }

        socket = connectedSocket
        inStream = connectedSocket.inputStream
        outStream = connectedSocket.outputStream
        open = true

        readJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(2048)
            var closeReason: String? = null
            try {
                while (isActive && open) {
                    val stream = inStream ?: break
                    val read = stream.read(buf)
                    if (read <= 0) {
                        closeReason = "RFCOMM EOF"
                        break
                    }
                    onBytesReceived(buf, read)
                }
            } catch (e: IOException) {
                if (open) {
                    closeReason = e.message ?: "RFCOMM read error"
                }
            } finally {
                val wasOpen = open
                close()
                if (wasOpen) {
                    onDisconnected(closeReason)
                }
            }
        }
        true
    }

    override suspend fun write(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!open) return@withContext false
        writeMutex.withLock {
            val stream = outStream ?: return@withLock false
            try {
                var offset = 0
                while (offset < bytes.size) {
                    val chunkSize = minOf(maxWriteChunkBytes, bytes.size - offset)
                    stream.write(bytes, offset, chunkSize)
                    stream.flush()
                    offset += chunkSize
                    if (interChunkDelayMs > 0 && offset < bytes.size) {
                        delay(interChunkDelayMs)
                    }
                }
                true
            } catch (e: IOException) {
                Log.e("PM3Spp", "SPP write failed: ${e.message}")
                false
            }
        }
    }

    override fun close() {
        open = false
        readJob?.cancel()
        readJob = null
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
    }
}
