package com.proxmark5.client.comm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB OTG CDC-ACM serial transport for Proxmark3 / Proxmark5 devices (VID/PID 0x9AC4:0x4B8F,
 * 0x2D2D:0x504D, or any composite CDC-Data bulk IN/OUT interface).
 */
class UsbCdcTransport(
    private val context: Context,
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    override val deviceName: String,
    override val address: String
) : ProxmarkTransport {

    override val kind: TransportKind = TransportKind.USB_CDC
    override val maxWriteChunkBytes: Int = 512
    override val interChunkDelayMs: Long = 0L

    @Volatile
    private var connection: UsbDeviceConnection? = null

    @Volatile
    private var dataInterface: UsbInterface? = null

    @Volatile
    private var commInterface: UsbInterface? = null

    @Volatile
    private var endpointIn: UsbEndpoint? = null

    @Volatile
    private var endpointOut: UsbEndpoint? = null

    @Volatile
    private var open: Boolean = false

    private var readJob: Job? = null
    private val writeMutex = Mutex()

    override val isConnected: Boolean
        get() = open && connection != null

    suspend fun connect(
        scope: CoroutineScope,
        onBytesReceived: (ByteArray, Int) -> Unit,
        onDisconnected: (String?) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!usbManager.hasPermission(device)) {
            val granted = requestUsbPermission(context, usbManager, device)
            if (!granted) {
                Log.w("PM3Usb", "USB permission denied for ${device.deviceName}")
                return@withContext false
            }
        }

        val endpoints = resolveEndpoints(device)
        if (endpoints == null) {
            Log.e("PM3Usb", "No suitable bulk IN/OUT USB endpoints on ${device.deviceName}")
            return@withContext false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e("PM3Usb", "UsbManager.openDevice failed for ${device.deviceName}")
            return@withContext false
        }

        val (commIf, dataIf, epIn, epOut) = endpoints
        if (commIf != null && commIf !== dataIf) {
            conn.claimInterface(commIf, true)
        }
        if (!conn.claimInterface(dataIf, true)) {
            Log.e("PM3Usb", "Failed to claim USB CDC data interface")
            if (commIf != null && commIf !== dataIf) {
                try { conn.releaseInterface(commIf) } catch (_: Exception) {}
            }
            conn.close()
            return@withContext false
        }

        val ctrlIndex = commIf?.id ?: dataIf.id
        // CDC SET_LINE_CODING (0x20): 115200 8N1
        val lineCoding = buildLineCoding(115200)
        conn.controlTransfer(0x21, 0x20, 0, ctrlIndex, lineCoding, lineCoding.size, 1000)
        // CDC SET_CONTROL_LINE_STATE (0x22): DTR (bit 0) + RTS (bit 1) = 0x0003
        conn.controlTransfer(0x21, 0x22, 0x0003, ctrlIndex, null, 0, 1000)

        connection = conn
        commInterface = commIf
        dataInterface = dataIf
        endpointIn = epIn
        endpointOut = epOut
        open = true

        readJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(maxOf(epIn.maxPacketSize, 2048))
            var reason: String? = null
            try {
                while (isActive && open) {
                    val activeConn = connection ?: break
                    val activeIn = endpointIn ?: break
                    val read = activeConn.bulkTransfer(activeIn, buf, buf.size, 250)
                    if (read > 0) {
                        onBytesReceived(buf, read)
                    }
                }
            } catch (e: Exception) {
                if (open) {
                    reason = e.message ?: "USB read error"
                }
            } finally {
                val wasOpen = open
                close()
                if (wasOpen) {
                    onDisconnected(reason ?: "USB disconnected")
                }
            }
        }
        true
    }

    override suspend fun write(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!open) return@withContext false
        writeMutex.withLock {
            val activeConn = connection ?: return@withLock false
            val activeOut = endpointOut ?: return@withLock false
            var offset = 0
            while (offset < bytes.size) {
                val chunkSize = minOf(maxWriteChunkBytes, bytes.size - offset)
                val chunk = bytes.copyOfRange(offset, offset + chunkSize)
                val sent = activeConn.bulkTransfer(activeOut, chunk, chunk.size, 1500)
                if (sent <= 0) {
                    Log.e("PM3Usb", "USB bulkTransfer write failed at offset $offset (res=$sent)")
                    return@withLock false
                }
                offset += sent
            }
            true
        }
    }

    override fun close() {
        open = false
        readJob?.cancel()
        readJob = null
        val conn = connection
        val dIf = dataInterface
        val cIf = commInterface
        connection = null
        dataInterface = null
        commInterface = null
        endpointIn = null
        endpointOut = null
        if (conn != null) {
            if (dIf != null) {
                try { conn.releaseInterface(dIf) } catch (_: Exception) {}
            }
            if (cIf != null && cIf !== dIf) {
                try { conn.releaseInterface(cIf) } catch (_: Exception) {}
            }
            try { conn.close() } catch (_: Exception) {}
        }
    }

    private data class CdcEndpoints(
        val commInterface: UsbInterface?,
        val dataInterface: UsbInterface,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint
    )

    private fun resolveEndpoints(dev: UsbDevice): CdcEndpoints? {
        var commIf: UsbInterface? = null
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                commIf = iface
                break
            }
        }

        // Pass 1: prefer CDC Data interface (class 0x0A)
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                extractBulkPair(iface)?.let { (epIn, epOut) ->
                    return CdcEndpoints(commIf, iface, epIn, epOut)
                }
            }
        }

        // Pass 2: fallback to any interface carrying both bulk IN and bulk OUT endpoints
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            extractBulkPair(iface)?.let { (epIn, epOut) ->
                return CdcEndpoints(commIf, iface, epIn, epOut)
            }
        }
        return null
    }

    private fun extractBulkPair(iface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN && epIn == null) {
                    epIn = ep
                } else if (ep.direction == UsbConstants.USB_DIR_OUT && epOut == null) {
                    epOut = ep
                }
            }
        }
        return if (epIn != null && epOut != null) epIn to epOut else null
    }

    companion object {
        const val VID_PM3_MAIN = 0x9AC4
        const val PID_PM3_MAIN = 0x4B8F
        const val VID_PM3_ALT = 0x2D2D
        const val PID_PM3_ALT = 0x504D
        private const val ACTION_USB_PERMISSION = "com.proxmark5.client.USB_PERMISSION"

        fun isProxmarkUsbDevice(vendorId: Int, productId: Int): Boolean {
            return (vendorId == VID_PM3_MAIN && productId == PID_PM3_MAIN) ||
                (vendorId == VID_PM3_ALT && productId == PID_PM3_ALT)
        }

        fun hasBulkSerialInterface(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                    iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                ) {
                    var hasIn = false
                    var hasOut = false
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                            if (ep.direction == UsbConstants.USB_DIR_OUT) hasOut = true
                        }
                    }
                    if (hasIn && hasOut) return true
                }
            }
            return false
        }

        fun buildLineCoding(
            baudrate: Int = 115200,
            stopBits: Int = 0,
            parity: Int = 0,
            dataBits: Int = 8
        ): ByteArray {
            return ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(baudrate)
                .put(stopBits.toByte())
                .put(parity.toByte())
                .put(dataBits.toByte())
                .array()
        }

        suspend fun requestUsbPermission(
            context: Context,
            usbManager: UsbManager,
            device: UsbDevice
        ): Boolean {
            if (usbManager.hasPermission(device)) return true
            val appCtx = context.applicationContext
            val deferred = CompletableDeferred<Boolean>()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        deferred.complete(granted)
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permIntent = Intent(ACTION_USB_PERMISSION).setPackage(appCtx.packageName)
            val pi = PendingIntent.getBroadcast(appCtx, 0, permIntent, flags)

            return try {
                usbManager.requestPermission(device, pi)
                withTimeoutOrNull(15_000L) { deferred.await() } ?: false
            } finally {
                try {
                    appCtx.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }
        }
    }
}
