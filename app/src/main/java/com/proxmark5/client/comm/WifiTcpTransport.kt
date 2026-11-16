package com.proxmark5.client.comm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wi-Fi TCP socket transport for Proxmark5 BWM STA mode or forwarded TCP bridge.
 */
class WifiTcpTransport(
    private val host: String,
    private val port: Int
) : ProxmarkTransport {

    override val kind: TransportKind = TransportKind.WIFI_TCP
    override val deviceName: String = "PM5-WiFi"
    override val address: String = "$host:$port"
    override val maxWriteChunkBytes: Int = 4096
    override val interChunkDelayMs: Long = 0L

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var inStream: InputStream? = null

    @Volatile
    private var outStream: OutputStream? = null

    @Volatile
    private var open: Boolean = false

    private var readJob: Job? = null
    private val writeMutex = Mutex()

    override val isConnected: Boolean
        get() = open && socket?.isConnected == true && socket?.isClosed == false

    suspend fun connect(
        scope: CoroutineScope,
        onBytesReceived: (ByteArray, Int) -> Unit,
        onDisconnected: (String?) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), 6000)
            sock.tcpNoDelay = true
            sock.keepAlive = true
            socket = sock
            inStream = sock.getInputStream()
            outStream = sock.getOutputStream()
            open = true

            readJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(2048)
                var closeReason: String? = null
                try {
                    while (isActive && open) {
                        val stream = inStream ?: break
                        val read = stream.read(buf)
                        if (read <= 0) {
                            closeReason = "TCP stream closed"
                            break
                        }
                        onBytesReceived(buf, read)
                    }
                } catch (e: IOException) {
                    if (open) {
                        closeReason = e.message ?: "TCP read error"
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
        } catch (e: Exception) {
            Log.e("PM5Tcp", "TCP connect failed: ${e.message}")
            close()
            false
        }
    }

    override suspend fun write(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!open) return@withContext false
        writeMutex.withLock {
            val stream = outStream ?: return@withLock false
            try {
                stream.write(bytes)
                stream.flush()
                true
            } catch (e: IOException) {
                Log.e("PM5Tcp", "TCP write failed: ${e.message}")
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
