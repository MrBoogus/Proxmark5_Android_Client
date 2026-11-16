package com.proxmark5.client.comm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.proxmark5.client.protocol.BadgeParser
import com.proxmark5.client.protocol.DecodedBadge
import com.proxmark5.client.protocol.DeviceCapabilities
import com.proxmark5.client.protocol.Em4100Decoder
import com.proxmark5.client.protocol.Hf14aCard
import com.proxmark5.client.protocol.HfCardDecoder
import com.proxmark5.client.protocol.Pm5AntennaConfig
import com.proxmark5.client.protocol.Pm5AntennaTuning
import com.proxmark5.client.protocol.Pm5AutoOffStatus
import com.proxmark5.client.protocol.Pm5BatteryInfo
import com.proxmark5.client.protocol.Pm5CommandFrameBuilder
import com.proxmark5.client.protocol.Pm5Constants
import com.proxmark5.client.protocol.Pm5FrameParser
import com.proxmark5.client.protocol.Pm5QcTestResult
import com.proxmark5.client.protocol.Pm5ResponseFrame
import com.proxmark5.client.protocol.Rdv4FlashInfo
import com.proxmark5.client.protocol.SmartcardAtrInfo
import com.proxmark5.client.protocol.T55xxBlockEntry
import com.proxmark5.client.protocol.T55xxPayloadBuilder
import com.proxmark5.client.protocol.T55xxTimingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Pm5BleManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices.asStateFlow()

    private val _logFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    private val _badgeFlow = MutableSharedFlow<DecodedBadge>(extraBufferCapacity = 16)
    val badgeFlow: SharedFlow<DecodedBadge> = _badgeFlow.asSharedFlow()

    private val _hfCardFlow = MutableSharedFlow<Hf14aCard>(extraBufferCapacity = 16)
    val hfCardFlow: SharedFlow<Hf14aCard> = _hfCardFlow.asSharedFlow()

    private val _batteryFlow = MutableStateFlow<Pm5BatteryInfo?>(null)
    val batteryFlow: StateFlow<Pm5BatteryInfo?> = _batteryFlow.asStateFlow()

    private val _antennaConfigFlow = MutableStateFlow<Pm5AntennaConfig?>(null)
    val antennaConfigFlow: StateFlow<Pm5AntennaConfig?> = _antennaConfigFlow.asStateFlow()

    private val _capabilitiesFlow = MutableStateFlow(DeviceCapabilities.unknown())
    val capabilitiesFlow: StateFlow<DeviceCapabilities> = _capabilitiesFlow.asStateFlow()

    // Active abstracted transport (BLE / Classic BT SPP / USB OTG / WiFi TCP)
    @Volatile
    private var activeTransport: ProxmarkTransport? = null

    @Volatile
    private var negotiatedMtu: Int = 23
    private var writeChunkSize: Int = 20

    // Transaction & parser synchronization
    private val parserLock = Any()
    private val transactionMutex = Mutex()
    private var pendingTransaction: CompletableDeferred<Pm5ResponseFrame>? = null
    private var pendingCmd: Int = -1
    @Volatile
    private var breakAckDeferred: CompletableDeferred<Pm5ResponseFrame>? = null

    // Watch flags
    var isWatchingHid = false; private set
    var isWatchingEm4100 = false; private set
    var isWatchingAwid = false; private set
    var isWatchingIo = false; private set

    private val frameParser = Pm5FrameParser(
        onFrameReceived = { frame -> handleIncomingFrame(frame) },
        onParseError = { err ->
            Log.w("PM5Client", "[CRC/PARSE ERR] $err")
            scope.launch { _logFlow.emit("[PARSE ERR] $err") }
        }
    )

    // BLE Scanner
    private var isScanning = false
    var autoConnectOnScan: Boolean = true
    private var scanAdapter: BluetoothAdapter? = null
    private var scanTimeoutJob: Job? = null

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { dev ->
                val name = try { dev.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                val addr = try { dev.address ?: "" } catch (_: SecurityException) { "" }
                val isPm5 = name.contains("Proxmark", ignoreCase = true) ||
                        name.contains("PM5", ignoreCase = true) ||
                        result.scanRecord?.serviceUuids?.any { it.uuid == Pm5Constants.BLE_SERVICE_UUID } == true

                val current = _discoveredDevices.value.toMutableList()
                val idx = current.indexOfFirst { it.address == addr }
                val discovered = DiscoveredBleDevice(
                    device = dev,
                    name = name,
                    address = addr,
                    rssi = result.rssi,
                    isProxmark5 = isPm5
                )
                if (idx >= 0) {
                    current[idx] = discovered
                } else {
                    if (isPm5) current.add(0, discovered) else current.add(discovered)
                }
                _discoveredDevices.value = current

                if (autoConnectOnScan && isPm5 && _connectionState.value is ConnectionState.Scanning) {
                    stopScan(scanAdapter)
                    scope.launch {
                        connectBle(dev)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            scanTimeoutJob?.cancel()
            scanTimeoutJob = null
            scope.launch { _logFlow.emit("[BLE SCAN] Failed with error code $errorCode") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(adapter: BluetoothAdapter?, autoConnect: Boolean = true) {
        if (autoConnect && _connectionState.value is ConnectionState.Connected) {
            return
        }
        scanAdapter = adapter
        autoConnectOnScan = autoConnect
        if (adapter == null || !adapter.isEnabled) {
            scope.launch { _logFlow.emit("[BLE SCAN] Bluetooth is disabled") }
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        scanTimeoutJob?.cancel()
        _discoveredDevices.value = emptyList()
        isScanning = true
        _connectionState.value = ConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            scope.launch { _logFlow.emit("[BLE SCAN] Scanning for Proxmark5 BWM (15s timeout)...") }
            scanTimeoutJob = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                if (isScanning) {
                    _logFlow.emit("[BLE SCAN] Scan timed out after 15s")
                    stopScan(scanAdapter)
                }
            }
        } catch (e: Exception) {
            isScanning = false
            scope.launch { _logFlow.emit("[BLE SCAN ERROR] ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(adapter: BluetoothAdapter?) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        if (!isScanning) return
        isScanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scope.launch { _logFlow.emit("[BLE SCAN] Stopped") }
        } catch (_: Exception) {}
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // --- BLE Connection ---

    @SuppressLint("MissingPermission")
    suspend fun connectBle(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val devName = device.name ?: Pm5Constants.DEFAULT_BLE_NAME
        _connectionState.value = ConnectionState.Connecting(devName)
        scope.launch { _logFlow.emit("[BLE] Connecting to $devName...") }

        val gattConnectedDeferred = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    scope.launch {
                        _logFlow.emit("[BLE] GATT Connected. Requesting Priority & MTU 512...")
                        _connectionState.value = ConnectionState.Negotiating("Setting high priority & MTU 512")
                    }
                    gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt?.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val activeBle = activeTransport as? BleGattTransport
                    if (activeBle != null) {
                        activeBle.close()
                        if (activeTransport === activeBle) {
                            activeTransport = null
                        }
                    } else {
                        try {
                            gatt?.close()
                        } catch (_: Exception) {}
                    }
                    isWatchingHid = false
                    isWatchingEm4100 = false
                    isWatchingAwid = false
                    isWatchingIo = false
                    synchronized(parserLock) { frameParser.reset() }
                    pendingTransaction?.cancel()
                    breakAckDeferred?.cancel()
                    scope.launch {
                        _logFlow.emit("[BLE] Disconnected (status: $status)")
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    gattConnectedDeferred.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                negotiatedMtu = mtu
                writeChunkSize = if (mtu > 23) (mtu - 3) else 20
                (activeTransport as? BleGattTransport)?.updateChunkSize(writeChunkSize)
                scope.launch {
                    _logFlow.emit("[BLE] MTU negotiated: $mtu bytes (Chunk size: $writeChunkSize bytes)")
                    _connectionState.value = ConnectionState.Negotiating("Discovering BWM services")
                }
                gatt?.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                    scope.launch { _logFlow.emit("[BLE ERROR] Service discovery failed ($status)") }
                    gattConnectedDeferred.complete(false)
                    return
                }

                val service = gatt.getService(Pm5Constants.BLE_SERVICE_UUID)
                if (service == null) {
                    scope.launch { _logFlow.emit("[BLE ERROR] Proxmark5 Service (0xAE86) not found on device") }
                    gattConnectedDeferred.complete(false)
                    return
                }

                val char = service.getCharacteristic(Pm5Constants.BLE_DATA_CHAR_UUID)
                if (char == null) {
                    scope.launch { _logFlow.emit("[BLE ERROR] BWM Data Characteristic (0xAE88) not found") }
                    gattConnectedDeferred.complete(false)
                    return
                }

                activeTransport = BleGattTransport(
                    gatt = gatt,
                    characteristic = char,
                    deviceName = devName,
                    address = device.address,
                    initialChunkSize = writeChunkSize
                )
                val notifSet = gatt.setCharacteristicNotification(char, true)
                Log.i("PM5Ble", "setCharacteristicNotification on 0xAE88: $notifSet")
                scope.launch { _logFlow.emit("[BLE] Set notification on 0xAE88: $notifSet") }

                val cccd = char.getDescriptor(Pm5Constants.BLE_CCCD_UUID)
                if (cccd != null) {
                    val wRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        if (gatt.writeDescriptor(cccd)) 0 else -1
                    }
                    Log.i("PM5Ble", "writeDescriptor on CCCD: result=$wRes")
                    if (wRes != 0) {
                        _connectionState.value = ConnectionState.Connected(
                            deviceName = devName,
                            address = device.address,
                            transport = TransportKind.BLE_GATT.label,
                            mtu = negotiatedMtu
                        )
                        gattConnectedDeferred.complete(true)
                    }
                } else {
                    _connectionState.value = ConnectionState.Connected(
                        deviceName = devName,
                        address = device.address,
                        transport = TransportKind.BLE_GATT.label,
                        mtu = negotiatedMtu
                    )
                    gattConnectedDeferred.complete(true)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                Log.i("PM5Ble", "onDescriptorWrite: status=$status, desc=${descriptor?.uuid}")
                scope.launch {
                    _logFlow.emit("[BLE] CCCD Notifications Enabled (status: $status)")
                }
                _connectionState.value = ConnectionState.Connected(
                    deviceName = devName,
                    address = device.address,
                    transport = TransportKind.BLE_GATT.label,
                    mtu = negotiatedMtu
                )
                gattConnectedDeferred.complete(true)
            }

            // Android 13+ (API 33+)
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                feedParserBytes(value, value.size)
            }

            // Android 12 and lower
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic?.value
                if (value != null && value.isNotEmpty()) {
                    feedParserBytes(value, value.size)
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                // WRITE_TYPE_NO_RESPONSE pacing is handled inside BleGattTransport
            }
        }

        try {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Failed to initiate BLE connection: ${e.message}")
            return@withContext false
        }

        val success = withTimeoutOrNull(15000) {
            gattConnectedDeferred.await()
        } ?: false

        if (!success) {
            disconnect()
            _connectionState.value = ConnectionState.Error("BLE connection timeout or handshake rejected")
            return@withContext false
        }

        return@withContext true
    }

    // --- Classic Bluetooth SPP Connection (RDV4 / HC-06) ---

    @SuppressLint("MissingPermission")
    suspend fun connectClassicBt(
        device: BluetoothDevice,
        adapter: BluetoothAdapter? = null
    ): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        val devName = try { device.name ?: "PM3-RDV4" } catch (_: SecurityException) { "PM3-RDV4" }
        val devAddr = try { device.address ?: "" } catch (_: SecurityException) { "" }
        _connectionState.value = ConnectionState.Connecting(devName)
        scope.launch { _logFlow.emit("[BT SPP] Connecting to $devName ($devAddr)...") }

        val transport = ClassicBtSppTransport(
            device = device,
            adapter = adapter,
            deviceName = devName,
            address = devAddr
        )
        val ok = transport.connect(
            scope = scope,
            onBytesReceived = { bytes, len -> feedParserBytes(bytes, len) },
            onDisconnected = { reason ->
                if (activeTransport === transport) {
                    activeTransport = null
                }
                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
                    scope.launch { _logFlow.emit("[BT SPP] Disconnected (${reason ?: "closed"})") }
                }
            }
        )
        if (!ok) {
            disconnect()
            _connectionState.value = ConnectionState.Error("Classic BT SPP connection failed for $devName")
            return@withContext false
        }

        activeTransport = transport
        writeChunkSize = transport.maxWriteChunkBytes
        _connectionState.value = ConnectionState.Connected(
            deviceName = devName,
            address = devAddr,
            transport = TransportKind.CLASSIC_BT_SPP.label,
            mtu = transport.maxWriteChunkBytes
        )
        scope.launch { _logFlow.emit("[BT SPP] Connected to $devName ($devAddr)") }
        true
    }

    // --- USB OTG CDC-ACM Connection ---

    fun scanUsbDevices(): List<DiscoveredUsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return usbManager.deviceList.values
            .filter { dev ->
                UsbCdcTransport.isProxmarkUsbDevice(dev.vendorId, dev.productId) ||
                    UsbCdcTransport.hasBulkSerialInterface(dev)
            }
            .map { dev ->
                val label = dev.productName?.takeIf { it.isNotBlank() }
                    ?: "PM3 USB (%04X:%04X)".format(dev.vendorId, dev.productId)
                DiscoveredUsbDevice(
                    device = dev,
                    name = label,
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    devicePath = dev.deviceName
                )
            }
    }

    suspend fun connectUsb(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            _connectionState.value = ConnectionState.Error("USB Host service unavailable")
            return@withContext false
        }
        disconnect()
        val devName = device.productName?.takeIf { it.isNotBlank() }
            ?: "PM3 USB (%04X:%04X)".format(device.vendorId, device.productId)
        val devAddr = "%04X:%04X@%s".format(device.vendorId, device.productId, device.deviceName)
        _connectionState.value = ConnectionState.Connecting(devName)
        scope.launch { _logFlow.emit("[USB] Connecting to $devName ($devAddr)...") }

        val transport = UsbCdcTransport(
            context = context,
            usbManager = usbManager,
            device = device,
            deviceName = devName,
            address = devAddr
        )
        val ok = transport.connect(
            scope = scope,
            onBytesReceived = { bytes, len -> feedParserBytes(bytes, len) },
            onDisconnected = { reason ->
                if (activeTransport === transport) {
                    activeTransport = null
                }
                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
                    scope.launch { _logFlow.emit("[USB] ${reason ?: "Disconnected"}") }
                }
            }
        )
        if (!ok) {
            disconnect()
            _connectionState.value = ConnectionState.Error("USB OTG connection failed for $devName")
            return@withContext false
        }

        activeTransport = transport
        writeChunkSize = transport.maxWriteChunkBytes
        _connectionState.value = ConnectionState.Connected(
            deviceName = devName,
            address = devAddr,
            transport = TransportKind.USB_CDC.label,
            mtu = transport.maxWriteChunkBytes
        )
        scope.launch { _logFlow.emit("[USB] Connected to $devName ($devAddr)") }
        true
    }

    // --- WiFi TCP Connection ---

    suspend fun connectTcp(host: String, port: Int = Pm5Constants.DEFAULT_WIFI_PORT): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _connectionState.value = ConnectionState.Connecting("WiFi ($host:$port)")
        scope.launch { _logFlow.emit("[WiFi] Connecting to TCP $host:$port...") }

        val transport = WifiTcpTransport(host = host, port = port)
        val ok = transport.connect(
            scope = scope,
            onBytesReceived = { bytes, len -> feedParserBytes(bytes, len) },
            onDisconnected = { reason ->
                if (activeTransport === transport) {
                    activeTransport = null
                }
                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
                    scope.launch { _logFlow.emit("[WiFi] ${reason ?: "TCP stream closed"}") }
                }
            }
        )
        if (!ok) {
            disconnect()
            _connectionState.value = ConnectionState.Error("WiFi TCP connection failed")
            return@withContext false
        }

        activeTransport = transport
        writeChunkSize = transport.maxWriteChunkBytes
        _connectionState.value = ConnectionState.Connected(
            deviceName = transport.deviceName,
            address = transport.address,
            transport = TransportKind.WIFI_TCP.label,
            mtu = transport.maxWriteChunkBytes
        )
        scope.launch {
            _logFlow.emit("[WiFi] Connected to BWM TCP server!")
        }
        true
    }

    // --- Disconnect & Cleanup ---

    private fun feedParserBytes(bytes: ByteArray, length: Int) {
        synchronized(parserLock) {
            frameParser.onBytesReceived(bytes, length)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeTransportsInternal() {
        try {
            activeTransport?.close()
        } catch (_: Exception) {}
        activeTransport = null

        pendingTransaction?.cancel()
        pendingTransaction = null
        breakAckDeferred?.cancel()
        breakAckDeferred = null

        synchronized(parserLock) {
            frameParser.reset()
        }
        isWatchingHid = false
        isWatchingEm4100 = false
        isWatchingAwid = false
        isWatchingIo = false
        _batteryFlow.value = null
        _antennaConfigFlow.value = null
        _capabilitiesFlow.value = DeviceCapabilities.unknown()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        closeTransportsInternal()
        scope.launch { _logFlow.emit("[COMM] Disconnected") }
    }

    /**
     * Synchronous resource cleanup safe to invoke from ViewModel.onCleared()
     * once viewModelScope is being cancelled.
     */
    fun shutdown() {
        stopScan(scanAdapter)
        closeTransportsInternal()
    }

    // --- Outbound Packet Transmission ---

    suspend fun sendBytes(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val transport = activeTransport ?: return@withContext false
        transport.write(data)
    }

    // --- Transaction Engine ---

    private fun handleIncomingFrame(frame: Pm5ResponseFrame) {
        val cmd = frame.cmd
        val status = frame.status.toInt()
        if (cmd != Pm5Constants.CMD_PM5_BWM_GET_BATTERY) {
            Log.i("PM5Rx", "RX cmd=0x%04X status=%d len=%d".format(cmd, status, frame.payload.size))
        }

        // 1. Debug Print String (0x0100): payload = [uint16 flag (LE)][ASCII bytes...]
        if (cmd == Pm5Constants.CMD_DEBUG_PRINT_STRING) {
            val textBytes = if (frame.payload.size > 2) {
                frame.payload.copyOfRange(2, frame.payload.size)
            } else {
                frame.payload
            }
            val rawText = String(textBytes, Charsets.US_ASCII)
                .replace("\u0000", "")
            val cleanText = Regex("\\u001B\\[[;\\d]*[A-Za-z]").replace(rawText, "").trim()
            if (cleanText.isNotEmpty()) {
                scope.launch { _logFlow.emit(cleanText) }
                val badge = BadgeParser.parse(cleanText)
                if (badge != null) {
                    scope.launch { _badgeFlow.emit(badge) }
                }
            }
        }

        // 2. Battery Telemetry Response
        if (cmd == Pm5Constants.CMD_PM5_BWM_GET_BATTERY) {
            val battery = Pm5BatteryInfo.fromBytes(frame.payload)
            if (battery != null) {
                _batteryFlow.value = battery
            }
        }

        // 3. BigBuf download chunk routing (0x0208)
        if (cmd == Pm5Constants.CMD_DOWNLOADED_BIGBUF) {
            onBigBufChunkFrame?.invoke(frame.payload)
        }

        // 4. Awaited BREAK_LOOP abort frame completion
        if (cmd != Pm5Constants.CMD_DEBUG_PRINT_STRING &&
            (status == Pm5Constants.PM3_EOPABORTED || cmd == Pm5Constants.CMD_BREAK_LOOP)
        ) {
            breakAckDeferred?.complete(frame)
        }

        // 5. Pending Transaction match (note: HF 14443A emission happens in readIso14443a() to avoid duplicates)
        if (pendingCmd >= 0 && pendingCmd == cmd) {
            pendingTransaction?.complete(frame)
        }
    }

    @Volatile
    private var onBigBufChunkFrame: ((ByteArray) -> Unit)? = null

    suspend fun executeTransaction(
        cmd: Int,
        payload: ByteArray? = null,
        timeoutMs: Long = 5000
    ): Pm5ResponseFrame? = withContext(Dispatchers.IO) {
        transactionMutex.withLock {
            val frameBytes = Pm5CommandFrameBuilder.build(cmd, payload)
            val deferred = CompletableDeferred<Pm5ResponseFrame>()
            pendingCmd = cmd
            pendingTransaction = deferred

            try {
                val sent = sendBytes(frameBytes)
                if (!sent) {
                    pendingCmd = -1
                    pendingTransaction = null
                    return@withContext null
                }
                withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            } finally {
                pendingCmd = -1
                pendingTransaction = null
            }
        }
    }

    suspend fun executeRawTransaction(
        cmd: Int,
        cmdName: String,
        payload: ByteArray? = null,
        timeoutMs: Long = 5000
    ): RawTransactionResult = withContext(Dispatchers.IO) {
        val txBytes = Pm5CommandFrameBuilder.build(cmd, payload)
        val txHex = txBytes.joinToString(" ") { "%02X".format(it) }
        val startNs = System.nanoTime()
        val rxFrame = executeTransaction(cmd, payload, timeoutMs)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000

        if (rxFrame != null) {
            val status = rxFrame.status.toInt()
            val reason = rxFrame.reason.toInt()
            val rxHex = rxFrame.payload.joinToString(" ") { "%02X".format(it) }
            RawTransactionResult(
                cmd = cmd,
                cmdName = cmdName,
                txBytesHex = txHex,
                rxFrame = rxFrame,
                rxStatus = status,
                rxReason = reason,
                rxPayloadHex = rxHex,
                latencyMs = latencyMs,
                success = (status == Pm5Constants.PM3_SUCCESS),
                statusText = Pm5Constants.statusToString(status)
            )
        } else {
            RawTransactionResult(
                cmd = cmd,
                cmdName = cmdName,
                txBytesHex = txHex,
                rxFrame = null,
                rxStatus = null,
                rxReason = null,
                rxPayloadHex = "",
                latencyMs = latencyMs,
                success = false,
                statusText = "TIMEOUT ($timeoutMs ms)"
            )
        }
    }

    // --- Device Negotiation & Read-Only Peripherals ---

    suspend fun negotiateDevice(): Pair<DeviceCapabilities, VersionInfo?> {
        val capResp = executeTransaction(Pm5Constants.CMD_CAPABILITIES, null, 2500)
        val ver = readVersion()
        val cap = if (capResp != null && capResp.status.toInt() == Pm5Constants.PM3_SUCCESS) {
            DeviceCapabilities.fromCapabilitiesPayload(capResp.payload, ver)
        } else {
            DeviceCapabilities.fromVersionFallback(ver)
        }
        _capabilitiesFlow.value = cap
        scope.launch {
            _logFlow.emit("[CAP] model=${cap.modelLabel} v=${cap.version} known=${cap.capabilitiesKnown} pm5=${cap.isPm5} rdv4=${cap.isRdv4} bwm=${cap.compiledWithBwm}")
        }
        return cap to ver
    }

    suspend fun readRdv4FlashInfo(): Rdv4FlashInfo? {
        if (!_capabilitiesFlow.value.hwAvailableFlash) return null
        val resp = executeTransaction(Pm5Constants.CMD_FLASHMEM_GET_INFO, null, 2500) ?: return null
        if (resp.status.toInt() != Pm5Constants.PM3_SUCCESS) return null
        return Rdv4FlashInfo.fromPayload(resp.payload)
    }

    suspend fun readSmartcardAtr(): SmartcardAtrInfo {
        if (!_capabilitiesFlow.value.hwAvailableSmartcard) {
            return SmartcardAtrInfo(false, "—", "Smartcard reader not reported by device")
        }
        val resp = executeTransaction(Pm5Constants.CMD_SMART_ATR, null, 3000)
            ?: return SmartcardAtrInfo(false, "—", "Smartcard ATR timed out")
        return SmartcardAtrInfo.fromPayload(resp.status.toInt(), resp.payload)
    }

    private fun isPm5HardwareAllowed(): Boolean {
        val cap = _capabilitiesFlow.value
        return cap.capabilitiesKnown && cap.isPm5
    }

    // --- PM5 Hardware & Battery API (strictly gated to PM5) ---

    suspend fun readBattery(): Pm5BatteryInfo? {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return null
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_GET_BATTERY, null, 2500) ?: return null
        val info = Pm5BatteryInfo.fromBytes(resp.payload)
        if (info != null) {
            _batteryFlow.value = info
        }
        return info
    }

    suspend fun setRgbColor(r: Int, g: Int, b: Int): Boolean {
        if (!isPm5HardwareAllowed()) return false
        val payload = byteArrayOf(
            (r and 0xFF).toByte(),
            (g and 0xFF).toByte(),
            (b and 0xFF).toByte()
        )
        val resp = executeTransaction(Pm5Constants.CMD_PM5_RGB_SET, payload, 2000)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    suspend fun readAntennaConfig(): Pm5AntennaConfig? {
        if (!isPm5HardwareAllowed()) return null
        val payload = byteArrayOf(0x01.toByte()) // 1 = map reg
        val resp = executeTransaction(Pm5Constants.CMD_ANT_CONTROL_READ, payload, 2000)
        if (resp != null && resp.status.toInt() == Pm5Constants.PM3_SUCCESS && resp.payload.isNotEmpty()) {
            val raw = resp.payload[0].toInt() and 0xFF
            val cfg = Pm5AntennaConfig.fromRaw(raw)
            _antennaConfigFlow.value = cfg
            return cfg
        }
        return null
    }

    suspend fun writeAntennaConfig(cfg: Pm5AntennaConfig): Boolean {
        if (!isPm5HardwareAllowed()) return false
        val payload = byteArrayOf(cfg.toByte(), 0x01.toByte()) // byte 0 = data, byte 1 = reg_type (1 = map reg)
        val resp = executeTransaction(Pm5Constants.CMD_ANT_CONTROL_WRITE, payload, 2000)
        val ok = (resp != null && resp.status.toInt() == Pm5Constants.PM3_SUCCESS)
        if (ok) {
            _antennaConfigFlow.value = cfg
        }
        return ok
    }

    suspend fun setAntennaLeds(lfLed: Boolean, hfLed: Boolean): Boolean {
        if (!isPm5HardwareAllowed()) return false
        val current = _antennaConfigFlow.value ?: readAntennaConfig() ?: Pm5AntennaConfig()
        val updated = current.copy(
            hfLed = hfLed,
            lfLed = lfLed
        )
        return writeAntennaConfig(updated)
    }

    suspend fun setAntennaFreq(freq: String): Boolean {
        if (!isPm5HardwareAllowed()) return false
        val current = _antennaConfigFlow.value ?: readAntennaConfig() ?: Pm5AntennaConfig()
        val updated = current.copy(
            freq125k = (freq == "125k"),
            freq134k = (freq == "134k"),
            freq250k = (freq == "250k"),
            freq375k = (freq == "375k"),
            freq500k = (freq == "500k")
        )
        return writeAntennaConfig(updated)
    }

    suspend fun setAntennaQ(highQ: Boolean): Boolean {
        if (!isPm5HardwareAllowed()) return false
        val current = _antennaConfigFlow.value ?: readAntennaConfig() ?: Pm5AntennaConfig()
        val updated = current.copy(highQ = highQ)
        return writeAntennaConfig(updated)
    }

    suspend fun runHardwareQcTest(timeoutMs: Long = 1000): Pm5QcTestResult {
        if (!isPm5HardwareAllowed()) {
            return Pm5QcTestResult(false, -1, "HW QC is PM5-only")
        }
        val bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(timeoutMs.toInt())
        val resp = executeTransaction(Pm5Constants.CMD_PM5_QC_TEST_HW, bb.array(), timeoutMs + 2000)
        if (resp == null) {
            return Pm5QcTestResult(false, -1, "Timeout executing HW QC test")
        }
        val failedItem = if (resp.payload.isNotEmpty()) resp.payload[0].toInt() and 0xFF else -1
        val ok = resp.status.toInt() == Pm5Constants.PM3_SUCCESS
        val msg = when {
            ok -> "All hardware checks passed (Antenna 0x51 & RGB 0x48 verified)"
            failedItem == 0 -> "Antenna Controller (I2C 0x51) read failure"
            failedItem == 1 -> "RGB Controller (I2C 0x48) write failure"
            failedItem == 2 -> "Mainboard test completed / button interrupt"
            else -> "HW QC test returned status ${resp.status} (code $failedItem)"
        }
        return Pm5QcTestResult(ok, failedItem, msg)
    }

    suspend fun setChargeEnable(enable: Boolean): Boolean {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return false
        val payload = byteArrayOf(if (enable) 1.toByte() else 0.toByte())
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_CHARGE_EN, payload, 2000)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    suspend fun setChargeVoltage(millivolts: Int): Boolean {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return false
        val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort((millivolts and 0xFFFF).toShort())
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_SET_VCHG, bb.array(), 2000)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    suspend fun setAutoOff(enabled: Boolean, idleSec: Long = 0, unplug: Boolean = true): Boolean {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return false
        val bb = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0x02.toByte()) // SET action
        bb.put(if (enabled) 1.toByte() else 0.toByte())
        bb.putInt((idleSec and 0xFFFFFFFFL).toInt())
        bb.put(if (unplug) 1.toByte() else 0.toByte())
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_AUTOOFF, bb.array(), 2500)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    suspend fun setBleName(name: String): Boolean {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return false
        val nameBytes = name.trim().take(15).toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(1 + nameBytes.size)
        payload[0] = 0x01.toByte() // SET action
        System.arraycopy(nameBytes, 0, payload, 1, nameBytes.size)
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_BLE_NAME, payload, 3000)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    suspend fun setPowersave(enabled: Boolean): Boolean {
        if (!isPm5HardwareAllowed() || !_capabilitiesFlow.value.compiledWithBwm) return false
        val payload = byteArrayOf(0x01.toByte(), if (enabled) 1.toByte() else 0.toByte())
        val resp = executeTransaction(Pm5Constants.CMD_PM5_BWM_POWERSAVE, payload, 2000)
        return resp?.status?.toInt() == Pm5Constants.PM3_SUCCESS
    }

    // --- Antenna Tuning & Diagnostics ---

    suspend fun measureAntennaTuning(timeoutMs: Long = 15000): Pm5AntennaTuning? {
        val resp = executeTransaction(Pm5Constants.CMD_MEASURE_ANTENNA_TUNING, null, timeoutMs) ?: return null
        if (resp.payload.size >= 28) {
            val bb = ByteBuffer.wrap(resp.payload).order(ByteOrder.LITTLE_ENDIAN)
            val lf134 = bb.int.toLong() and 0xFFFFFFFFL
            val lf125 = bb.int.toLong() and 0xFFFFFFFFL
            bb.int // lfconf (skip)
            val hf = bb.int.toLong() and 0xFFFFFFFFL
            val peak = bb.int.toLong() and 0xFFFFFFFFL
            bb.int // peak_f (skip)
            val divisor = bb.int.toLong() and 0xFFFFFFFFL
            Log.i("Pm5Antenna", "Tuning parsed: LF125=${lf125}mV, LF134=${lf134}mV, HF=${hf}mV, Peak=${peak}mV, Div=$divisor")
            return Pm5AntennaTuning(lf125, lf134, hf, peak, divisor)
        }
        return null
    }

    suspend fun readVersion(): VersionInfo? {
        val resp = executeTransaction(Pm5Constants.CMD_VERSION, null, 3000) ?: return null
        return VersionInfo.fromPayload(resp.payload)
    }

    /**
     * Send CMD_BREAK_LOOP (0x0118), await the abort/EOPABORTED reply frame up to [timeoutMs]
     * when a loop was active (or when [forceAwait] is true), and reset the frame parser so no
     * late/partial bytes leak into the next command.
     */
    suspend fun breakLoop(timeoutMs: Long = 2000L, forceAwait: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val wasWatching = isWatchingHid || isWatchingEm4100 || isWatchingAwid || isWatchingIo
        isWatchingHid = false
        isWatchingEm4100 = false
        isWatchingAwid = false
        isWatchingIo = false

        var ackReceived = false
        transactionMutex.withLock {
            val deferred = CompletableDeferred<Pm5ResponseFrame>()
            breakAckDeferred = deferred
            try {
                val sent = sendBytes(Pm5CommandFrameBuilder.build(Pm5Constants.CMD_BREAK_LOOP, null))
                if (sent && (wasWatching || forceAwait)) {
                    ackReceived = withTimeoutOrNull(timeoutMs) { deferred.await() } != null
                }
            } finally {
                breakAckDeferred = null
                synchronized(parserLock) {
                    frameParser.reset()
                }
            }
        }
        ackReceived
    }

    /**
     * Download [length] bytes from device BigBuf starting at [offset] in paced [sliceBytes]
     * slices (2048 B default) so slow UART bridges like the RDV4 Blueshark BT FIFO never overflow.
     */
    suspend fun downloadBigBuf(
        offset: Int = 0,
        length: Int = 12000,
        sliceBytes: Int = 2048,
        timeoutPerSliceMs: Long = 3000L
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (length <= 0) return@withContext ByteArray(0)
        val out = ByteArray(length)
        var sliceOffset = 0
        while (sliceOffset < length) {
            val want = minOf(sliceBytes, length - sliceOffset)
            var sliceOk = false
            for (attempt in 0..1) {
                val sliceBuf = ByteArray(want)
                var maxWritten = 0
                onBigBufChunkFrame = { chunkPayload ->
                    if (chunkPayload.size > 4) {
                        val bb = ByteBuffer.wrap(chunkPayload, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkOff = bb.int
                        val dataLen = chunkPayload.size - 4
                        if (chunkOff in 0 until want) {
                            val copyLen = minOf(dataLen, want - chunkOff)
                            System.arraycopy(chunkPayload, 4, sliceBuf, chunkOff, copyLen)
                            maxWritten = maxOf(maxWritten, chunkOff + copyLen)
                        }
                    }
                }
                val req = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(offset + sliceOffset)
                    .putInt(want)
                    .array()
                val doneFrame = try {
                    executeTransaction(Pm5Constants.CMD_DOWNLOAD_BIGBUF, req, timeoutPerSliceMs)
                } finally {
                    onBigBufChunkFrame = null
                }
                if (doneFrame != null && doneFrame.status.toInt() == Pm5Constants.PM3_SUCCESS && maxWritten >= want) {
                    System.arraycopy(sliceBuf, 0, out, sliceOffset, want)
                    sliceOk = true
                    break
                }
                synchronized(parserLock) {
                    frameParser.reset()
                }
            }
            if (!sliceOk) return@withContext null
            sliceOffset += want
        }
        out
    }

    // --- LF Operations ---

    suspend fun toggleWatchHid(): Boolean {
        if (isWatchingHid) {
            breakLoop()
            isWatchingHid = false
            return false
        } else {
            breakLoop()
            delay(100)
            isWatchingHid = true
            sendBytes(Pm5CommandFrameBuilder.build(Pm5Constants.CMD_LF_HID_WATCH, null))
            return true
        }
    }

    suspend fun toggleWatchEm4100(): Boolean {
        if (isWatchingEm4100) {
            breakLoop()
            isWatchingEm4100 = false
            return false
        } else {
            breakLoop()
            delay(100)
            isWatchingEm4100 = true
            sendBytes(Pm5CommandFrameBuilder.build(Pm5Constants.CMD_LF_EM410X_WATCH, null))
            return true
        }
    }

    suspend fun toggleWatchAwid(): Boolean {
        if (isWatchingAwid) {
            breakLoop()
            isWatchingAwid = false
            return false
        } else {
            breakLoop()
            delay(100)
            isWatchingAwid = true
            sendBytes(Pm5CommandFrameBuilder.build(Pm5Constants.CMD_LF_AWID_WATCH, null))
            return true
        }
    }

    suspend fun toggleWatchIo(): Boolean {
        if (isWatchingIo) {
            breakLoop()
            isWatchingIo = false
            return false
        } else {
            breakLoop()
            delay(100)
            isWatchingIo = true
            sendBytes(Pm5CommandFrameBuilder.build(Pm5Constants.CMD_LF_IO_WATCH, null))
            return true
        }
    }

    suspend fun cloneEm4100(
        uid: Long,
        targetChip: Int = Pm5Constants.TARGET_T55X7,
        clock: Int = 64,
        addElectra: Boolean = false
    ): Result<String> {
        val payload = Em4100Decoder.buildClonePayload(uid, targetChip, clock, addElectra)
        val resp = executeTransaction(Pm5Constants.CMD_LF_EM410X_CLONE, payload, 5000)
            ?: return Result.failure(Exception("Clone command timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("Clone succeeded to target chip %s".format(
                when (targetChip) {
                    Pm5Constants.TARGET_T55X7 -> "T5577"
                    Pm5Constants.TARGET_Q5 -> "Q5"
                    Pm5Constants.TARGET_EM4305 -> "EM4305"
                    else -> "T55xx"
                }
            ))
        } else {
            Result.failure(Exception("Clone failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    // --- Fast T55xx Transponder Suite ---

    suspend fun readT55xxBlock(
        blockNo: Int,
        page: Int = 0,
        password: Long = 0L,
        usePwd: Boolean = false,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED,
        timeoutMs: Long = 4000
    ): T55xxReadResult {
        val payload = T55xxPayloadBuilder.buildReadBlock(
            blockNo = blockNo,
            page = page,
            password = password,
            usePwd = usePwd,
            downlinkMode = downlinkMode
        )
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_READBL, payload, timeoutMs)
            ?: return T55xxReadResult(
                blockNo = blockNo,
                page = page,
                rawValue = null,
                hexValue = "—",
                binaryValue = "—",
                status = Pm5Constants.PM3_ETIMEOUT,
                message = "Read timed out"
            )

        val status = resp.status.toInt()
        if (resp.payload.size >= 4) {
            val bb = ByteBuffer.wrap(resp.payload).order(ByteOrder.LITTLE_ENDIAN)
            val raw = bb.int.toLong() and 0xFFFFFFFFL
            val hex = "%08X".format(raw)
            val bin = raw.toString(2).padStart(32, '0')
            val ascii = rawToAscii(raw)
            return T55xxReadResult(
                blockNo = blockNo,
                page = page,
                rawValue = raw,
                hexValue = hex,
                binaryValue = bin,
                asciiValue = ascii,
                status = status,
                message = if (status == Pm5Constants.PM3_SUCCESS) "Read OK (Fast MCU ASK)" else Pm5Constants.statusToString(status)
            )
        }

        return T55xxReadResult(
            blockNo = blockNo,
            page = page,
            rawValue = null,
            hexValue = "—",
            binaryValue = "—",
            status = status,
            message = Pm5Constants.statusToString(status)
        )
    }

    suspend fun writeT55xxBlock(
        blockNo: Int,
        value: Long,
        page: Int = 0,
        password: Long = 0L,
        usePwd: Boolean = false,
        testMode: Boolean = false,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED,
        timeoutMs: Long = 4000
    ): Result<String> {
        val payload = T55xxPayloadBuilder.buildWriteBlock(
            blockNo = blockNo,
            value = value,
            page = page,
            password = password,
            usePwd = usePwd,
            testMode = testMode,
            downlinkMode = downlinkMode
        )
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_WRITEBL, payload, timeoutMs)
            ?: return Result.failure(Exception("Write timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("Block $blockNo written successfully with 0x%08X".format(value))
        } else {
            Result.failure(Exception("Write failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    suspend fun wakeupT55xx(
        password: Long = 0L,
        downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED
    ): Result<String> {
        val payload = T55xxPayloadBuilder.buildWakeup(password, downlinkMode)
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_WAKEUP, payload, 2000)
            ?: return Result.failure(Exception("Wakeup timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("AOR Wakeup sent with password 0x%08X".format(password))
        } else {
            Result.failure(Exception("Wakeup failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    suspend fun resetReadT55xx(downlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED): Result<String> {
        val payload = T55xxPayloadBuilder.buildResetRead(downlinkMode)
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_RESET_READ, payload, 2500)
            ?: return Result.failure(Exception("Reset read timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("Reset read executed")
        } else {
            Result.failure(Exception("Reset read failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    suspend fun setT55xxConfig(config: T55xxTimingConfig): Result<String> {
        val payload = config.toWireBytes()
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_SET_CONFIG, payload, 3000)
            ?: return Result.failure(Exception("Set config timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("T55xx Timings configured (Persist=${config.persist})")
        } else {
            Result.failure(Exception("Set config failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    suspend fun dangerRawT55xx(bitString: String, timeUs: Long): Result<String> {
        val payload = T55xxPayloadBuilder.buildDangerRaw(bitString, timeUs)
        val resp = executeTransaction(Pm5Constants.CMD_LF_T55XX_DANGERRAW, payload, 3000)
            ?: return Result.failure(Exception("DangerRaw timed out"))

        val status = resp.status.toInt()
        return if (status == Pm5Constants.PM3_SUCCESS) {
            Result.success("DangerRaw bitstream transmitted (${bitString.length} bits, ${timeUs}µs)")
        } else {
            Result.failure(Exception("DangerRaw failed: ${Pm5Constants.statusToString(status)}"))
        }
    }

    // --- HF Operations ---

    suspend fun readIso14443a(timeoutMs: Long = 4000): Hf14aCard? {
        // iso14a_raw_cmd_t (16 bytes LE):
        //   flags = ISO14A_CONNECT (1) | ISO14A_CLEARTRACE (1 << 17)
        //   timeout = 0, wait_us = 0, len = 0, lenbits = 0
        val payload = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x00020001)
            .putInt(0)
            .putInt(0)
            .putShort(0)
            .putShort(0)
            .array()
        val resp = executeTransaction(Pm5Constants.CMD_HF_ISO14443A_READER, payload, timeoutMs) ?: return null
        scope.launch {
            _logFlow.emit("[HF 14443A] status=${Pm5Constants.statusToString(resp.status.toInt())} len=${resp.payload.size}")
        }
        if (resp.payload.isNotEmpty()) {
            val card = HfCardDecoder.parseIso14a(resp.payload)
            if (card != null) {
                _hfCardFlow.emit(card)
                return card
            }
        }
        return null
    }

    private fun rawToAscii(value: Long): String {
        val b0 = ((value shr 24) and 0xFF).toInt()
        val b1 = ((value shr 16) and 0xFF).toInt()
        val b2 = ((value shr 8) and 0xFF).toInt()
        val b3 = (value and 0xFF).toInt()
        return buildString {
            append(if (b0 in 32..126) b0.toChar() else '.')
            append(if (b1 in 32..126) b1.toChar() else '.')
            append(if (b2 in 32..126) b2.toChar() else '.')
            append(if (b3 in 32..126) b3.toChar() else '.')
        }
    }
}
