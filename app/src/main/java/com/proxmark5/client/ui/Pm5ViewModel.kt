package com.proxmark5.client.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxmark5.client.R
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.comm.DiscoveredBleDevice
import com.proxmark5.client.comm.DiscoveredUsbDevice
import com.proxmark5.client.comm.LastDeviceRecord
import com.proxmark5.client.comm.LastDeviceStore
import com.proxmark5.client.comm.Pm5BleManager
import com.proxmark5.client.comm.T55xxPwdCheckResult
import com.proxmark5.client.comm.T55xxReadResult
import com.proxmark5.client.comm.TransportKind
import com.proxmark5.client.comm.VersionInfo
import com.proxmark5.client.data.CapturedTag
import com.proxmark5.client.data.TagStorageManager
import com.proxmark5.client.data.VaultResult
import com.proxmark5.client.protocol.DecodedBadge
import com.proxmark5.client.protocol.DeviceCapabilities
import com.proxmark5.client.protocol.Em4100Decoder
import com.proxmark5.client.protocol.Rdv4FlashInfo
import com.proxmark5.client.protocol.SmartcardAtrInfo
import com.proxmark5.client.protocol.Em4100Tag
import com.proxmark5.client.protocol.Hf14aCard
import com.proxmark5.client.protocol.Pm5AntennaConfig
import com.proxmark5.client.protocol.Pm5AntennaTuning
import com.proxmark5.client.protocol.Pm5BatteryInfo
import com.proxmark5.client.protocol.Pm5Constants
import com.proxmark5.client.protocol.Pm5QcTestResult
import com.proxmark5.client.protocol.T55xxBlockEntry
import com.proxmark5.client.protocol.T55xxPayloadBuilder
import com.proxmark5.client.protocol.T55xxTimingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Pm5UiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val classicBtDevices: List<BluetoothDevice> = emptyList(),
    val usbDevices: List<DiscoveredUsbDevice> = emptyList(),
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val lastConnectedDevice: LastDeviceRecord? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val tcpHost: String = "192.168.4.1",
    val tcpPort: String = "18888",
    val isBusy: Boolean = false,
    val statusBanner: String? = null,
    val versionInfo: VersionInfo? = null,
    val deviceCapabilities: DeviceCapabilities = DeviceCapabilities.unknown(),
    val rdv4FlashInfo: Rdv4FlashInfo? = null,
    val smartcardAtrInfo: SmartcardAtrInfo? = null,

    // Navigation Tab
    // 0: Dashboard & Power, 1: LF Badge Studio, 2: HF NFC Studio, 3: T55xx Suite, 4: Tag Vault, 5: Terminal
    val selectedTab: Int = 0,

    // PM5 BWM Power & Telemetry
    val batteryInfo: Pm5BatteryInfo? = null,
    val antennaTuning: Pm5AntennaTuning? = null,
    val isTuningAntenna: Boolean = false,
    val selectedRgbColor: Color = Color.Cyan,
    val bleAdvertisingName: String = "Proxmark5",

    // Swappable Antenna Controller (0x51) & Diagnostics
    val antennaConfig: Pm5AntennaConfig? = null,
    val isReadingAntenna: Boolean = false,
    val qcTestResult: Pm5QcTestResult? = null,
    val isRunningQcTest: Boolean = false,

    // LF Studio
    val isWatchingEm4100: Boolean = false,
    val isWatchingHid: Boolean = false,
    val isWatchingAwid: Boolean = false,
    val isWatchingIo: Boolean = false,
    val isAutoScanning: Boolean = false,
    val decodedBadge: DecodedBadge? = null,
    val lastEm4100Tag: Em4100Tag? = null,
    val cloneTargetChip: Int = Pm5Constants.TARGET_T55X7,
    val cloneClock: Int = 64,
    val cloneAddElectra: Boolean = false,
    val cloneStatus: String? = null,

    // HF Studio
    val lastHfCard: Hf14aCard? = null,
    val isScanningHf: Boolean = false,
    val hfStatus: String? = null,

    // T55xx Transponder Suite
    val t55xxBlocks: Map<Int, T55xxBlockEntry> = (0..7).associateWith { T55xxBlockEntry(blockNo = it) },
    val selectedT55xxBlock: Int = 0,
    val selectedT55xxPage: Int = 0,
    val t55xxPasswordHex: String = "",
    val t55xxUsePwd: Boolean = false,
    val t55xxTestMode: Boolean = false,
    val t55xxDownlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED,
    val lastReadResult: T55xxReadResult? = null,
    val writeBlockDataHex: String = "",
    val writeStatus: String? = null,
    val isCheckingPwds: Boolean = false,
    val chkPwdStatus: String? = null,
    val foundPassword: Long? = null,
    val whiteClonerUidHex: String = "00000000",
    val whiteClonerCalculatedPwd: Long? = null,
    val timingConfig: T55xxTimingConfig = T55xxTimingConfig.defaultTimings(persist = false),
    val timingStatus: String? = null,
    val dangerRawUnlocked: Boolean = false,
    val dangerRawBitstream: String = "",
    val dangerRawTimeUs: String = "1000",
    val dangerRawStatus: String? = null,

    // Saved Vault & Console Logs
    val savedTags: List<CapturedTag> = emptyList(),
    val vaultError: String? = null,
    val logs: List<String> = emptyList()
)

class Pm5ViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    val pm5Manager = Pm5BleManager(application, viewModelScope)
    private val storageManager = TagStorageManager(application)
    private val lastDeviceStore = LastDeviceStore(application)

    private val _uiState = MutableStateFlow(Pm5UiState())
    val uiState: StateFlow<Pm5UiState> = _uiState.asStateFlow()

    private var batteryPollingJob: Job? = null
    private var autoScanJob: Job? = null
    private var dictionaryJob: Job? = null

    private fun str(@StringRes resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun pluralStr(@PluralsRes resId: Int, quantity: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(resId, quantity, *args)

    init {
        _uiState.value = _uiState.value.copy(lastConnectedDevice = lastDeviceStore.load())
        refreshPairedDevices()
        refreshUsbDevices()
        loadSavedTags()

        viewModelScope.launch {
            pm5Manager.capabilitiesFlow.collect { cap ->
                _uiState.value = _uiState.value.copy(deviceCapabilities = cap)
            }
        }

        // Observe connection state
        viewModelScope.launch {
            pm5Manager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    isWatchingHid = pm5Manager.isWatchingHid,
                    isWatchingEm4100 = pm5Manager.isWatchingEm4100,
                    isWatchingAwid = pm5Manager.isWatchingAwid,
                    isWatchingIo = pm5Manager.isWatchingIo
                )
                if (state is ConnectionState.Connected) {
                    persistLastConnectedDevice(state)
                    negotiateConnectedDevice()
                } else {
                    stopBatteryPolling()
                    if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                        autoScanJob?.cancel()
                        dictionaryJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            isBusy = false,
                            isAutoScanning = false,
                            isScanningHf = false,
                            isTuningAntenna = false,
                            isRunningQcTest = false,
                            isCheckingPwds = false,
                            rdv4FlashInfo = null,
                            smartcardAtrInfo = null
                        )
                    }
                }
            }
        }

        // Observe antenna config
        viewModelScope.launch {
            pm5Manager.antennaConfigFlow.collect { cfg ->
                if (cfg != null) {
                    _uiState.value = _uiState.value.copy(antennaConfig = cfg)
                }
            }
        }

        // Observe discovered devices
        viewModelScope.launch {
            pm5Manager.discoveredDevices.collect { devs ->
                _uiState.value = _uiState.value.copy(discoveredDevices = devs)
            }
        }

        // Observe logs
        viewModelScope.launch {
            pm5Manager.logFlow.collect { line ->
                val current = _uiState.value.logs.toMutableList()
                current.add(line)
                if (current.size > 250) current.removeAt(0)
                _uiState.value = _uiState.value.copy(logs = current)
            }
        }

        // Observe battery flow
        viewModelScope.launch {
            pm5Manager.batteryFlow.collect { info ->
                if (info != null) {
                    _uiState.value = _uiState.value.copy(batteryInfo = info)
                }
            }
        }

        // Observe badge detections
        viewModelScope.launch {
            pm5Manager.badgeFlow.collect { badge ->
                _uiState.value = _uiState.value.copy(
                    decodedBadge = badge,
                    statusBanner = str(R.string.status_badge_detected, badge.technology, badge.uidHex)
                )
                // If EM4100, parse full tag
                if (badge.technology.contains("EM4100", ignoreCase = true)) {
                    val em = Em4100Decoder.fromHex(badge.uidHex)
                    if (em != null) {
                        _uiState.value = _uiState.value.copy(lastEm4100Tag = em)
                    }
                }
                // Save to Vault (runs off main thread via TagStorageManager)
                val vaultRes = storageManager.save(
                    CapturedTag(
                        uidHex = badge.uidHex,
                        technology = badge.technology,
                        frequency = "LF",
                        cardId = badge.cardId,
                        facilityCode = badge.facilityCode,
                        formatDescription = badge.formatLen
                    )
                )
                applyVaultResult(vaultRes)
            }
        }

        // Observe HF Card detections
        viewModelScope.launch {
            pm5Manager.hfCardFlow.collect { card ->
                _uiState.value = _uiState.value.copy(
                    lastHfCard = card,
                    statusBanner = str(R.string.status_hf_card_detected, card.cardType, card.uidHex)
                )
                // Save to Vault (runs off main thread via TagStorageManager)
                val vaultRes = storageManager.save(
                    CapturedTag(
                        uidHex = card.uidHex,
                        technology = card.cardType,
                        frequency = "HF",
                        formatDescription = str(R.string.format_hf_iso14443a, card.sakHex, card.atqaHex)
                    )
                )
                applyVaultResult(vaultRes)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        try {
            val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            val sortedBle = bonded.sortedByDescending {
                val n = try { it.name ?: "" } catch (_: SecurityException) { "" }
                n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
            }
            val classic = bonded.filter { dev ->
                try {
                    dev.type != BluetoothDevice.DEVICE_TYPE_LE
                } catch (_: SecurityException) {
                    false
                }
            }.sortedByDescending { dev ->
                val n = try { dev.name ?: "" } catch (_: SecurityException) { "" }
                n.contains("PM3", ignoreCase = true) ||
                    n.contains("RDV4", ignoreCase = true) ||
                    n.contains("Blueshark", ignoreCase = true) ||
                    n.contains("HC-0", ignoreCase = true)
            }
            _uiState.value = _uiState.value.copy(
                pairedDevices = sortedBle,
                classicBtDevices = classic
            )
        } catch (_: SecurityException) {
            _uiState.value = _uiState.value.copy(
                pairedDevices = emptyList(),
                classicBtDevices = emptyList()
            )
        }
    }

    fun refreshUsbDevices() {
        _uiState.value = _uiState.value.copy(usbDevices = pm5Manager.scanUsbDevices())
    }

    fun onPermissionsDenied() {
        _uiState.value = _uiState.value.copy(
            statusBanner = str(R.string.status_permissions_denied)
        )
    }

    fun startBleScan() {
        pm5Manager.startScan(bluetoothAdapter)
    }

    fun stopBleScan() {
        pm5Manager.stopScan(bluetoothAdapter)
    }

    fun selectDevice(device: BluetoothDevice) {
        _uiState.value = _uiState.value.copy(selectedDevice = device)
    }

    fun connectSelectedDevice() {
        val dev = _uiState.value.selectedDevice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            pm5Manager.connectBle(dev)
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun connectClassicBtDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            pm5Manager.connectClassicBt(device, bluetoothAdapter)
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectClassicBtAddress(mac: String) {
        val adapter = bluetoothAdapter ?: return
        val dev = try {
            adapter.bondedDevices?.firstOrNull { it.address.equals(mac, ignoreCase = true) }
                ?: adapter.getRemoteDevice(mac)
        } catch (_: Exception) {
            null
        } ?: return
        connectClassicBtDevice(dev)
    }

    fun connectUsbDevice(usb: DiscoveredUsbDevice) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            pm5Manager.connectUsb(usb.device)
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun connectTcp(host: String = _uiState.value.tcpHost, portStr: String = _uiState.value.tcpPort) {
        val port = portStr.toIntOrNull() ?: Pm5Constants.DEFAULT_WIFI_PORT
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            pm5Manager.connectTcp(host, port)
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun reconnectLastDevice(): Boolean {
        val saved = lastDeviceStore.load() ?: return false
        when (saved.transportKind) {
            TransportKind.BLE_GATT -> {
                val adapter = bluetoothAdapter ?: return false
                val dev = try {
                    adapter.getRemoteDevice(saved.addressOrHost)
                } catch (_: Exception) {
                    null
                } ?: return false
                selectDevice(dev)
                connectSelectedDevice()
                return true
            }
            TransportKind.CLASSIC_BT_SPP -> {
                connectClassicBtAddress(saved.addressOrHost)
                return true
            }
            TransportKind.USB_CDC -> {
                refreshUsbDevices()
                val usb = _uiState.value.usbDevices.firstOrNull() ?: return false
                connectUsbDevice(usb)
                return true
            }
            TransportKind.WIFI_TCP -> {
                val port = if (saved.port > 0) saved.port else Pm5Constants.DEFAULT_WIFI_PORT
                _uiState.value = _uiState.value.copy(tcpHost = saved.addressOrHost, tcpPort = port.toString())
                connectTcp(saved.addressOrHost, port.toString())
                return true
            }
        }
    }

    private fun persistLastConnectedDevice(state: ConnectionState.Connected) {
        val record = when (state.transport) {
            TransportKind.BLE_GATT.label -> LastDeviceRecord(
                transportKind = TransportKind.BLE_GATT,
                addressOrHost = state.address,
                port = 0,
                displayName = state.deviceName
            )
            TransportKind.CLASSIC_BT_SPP.label -> LastDeviceRecord(
                transportKind = TransportKind.CLASSIC_BT_SPP,
                addressOrHost = state.address,
                port = 0,
                displayName = state.deviceName
            )
            TransportKind.USB_CDC.label -> LastDeviceRecord(
                transportKind = TransportKind.USB_CDC,
                addressOrHost = state.address,
                port = 0,
                displayName = state.deviceName
            )
            TransportKind.WIFI_TCP.label -> {
                val parts = state.address.split(":")
                val host = parts.getOrElse(0) { _uiState.value.tcpHost }
                val port = parts.getOrNull(1)?.toIntOrNull() ?: Pm5Constants.DEFAULT_WIFI_PORT
                LastDeviceRecord(
                    transportKind = TransportKind.WIFI_TCP,
                    addressOrHost = host,
                    port = port,
                    displayName = state.deviceName
                )
            }
            else -> return
        }
        lastDeviceStore.save(record)
        _uiState.value = _uiState.value.copy(lastConnectedDevice = record)
    }

    private fun negotiateConnectedDevice() {
        viewModelScope.launch {
            val (cap, ver) = pm5Manager.negotiateDevice()
            _uiState.value = _uiState.value.copy(
                deviceCapabilities = cap,
                versionInfo = ver
            )
            if (cap.capabilitiesKnown && cap.isPm5) {
                startBatteryPolling()
                if (cap.compiledWithBwm) {
                    pm5Manager.readBattery()
                }
                readAntennaConfig()
            } else {
                stopBatteryPolling()
                _uiState.value = _uiState.value.copy(
                    batteryInfo = null,
                    antennaConfig = null
                )
            }
        }
    }

    fun readRdv4FlashInfo() {
        viewModelScope.launch {
            val info = pm5Manager.readRdv4FlashInfo()
            _uiState.value = _uiState.value.copy(
                rdv4FlashInfo = info,
                statusBanner = if (info != null) {
                    "SPI Flash: ${info.manufacturerName} (JEDEC 0x%04X), ${info.totalBytes / 1024} KB".format(info.jedecId)
                } else {
                    "SPI Flash info unavailable"
                }
            )
        }
    }

    fun readSmartcardAtr() {
        viewModelScope.launch {
            val info = pm5Manager.readSmartcardAtr()
            _uiState.value = _uiState.value.copy(
                smartcardAtrInfo = info,
                statusBanner = if (info.cardPresent) "Smartcard ATR: ${info.atrHex}" else info.message
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            pm5Manager.disconnect()
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun setTcpHost(host: String) {
        _uiState.value = _uiState.value.copy(tcpHost = host)
    }

    fun setTcpPort(port: String) {
        _uiState.value = _uiState.value.copy(tcpPort = port)
    }

    // --- Battery & Power ---

    private fun startBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val s = _uiState.value
                val lfWatchActive = s.isAutoScanning || s.isWatchingHid || s.isWatchingEm4100 || s.isWatchingAwid || s.isWatchingIo
                val allowBwmPoll = s.deviceCapabilities.capabilitiesKnown &&
                    s.deviceCapabilities.isPm5 &&
                    s.deviceCapabilities.compiledWithBwm
                if (allowBwmPoll && s.connectionState is ConnectionState.Connected && !s.isBusy && !lfWatchActive) {
                    pm5Manager.readBattery()
                }
                delay(4000)
            }
        }
    }

    private fun stopBatteryPolling() {
        batteryPollingJob?.cancel()
        batteryPollingJob = null
    }

    fun refreshBatteryNow() {
        viewModelScope.launch {
            pm5Manager.readBattery()
        }
    }

    fun setRgbColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedRgbColor = color)
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        viewModelScope.launch {
            val ok = pm5Manager.setRgbColor(r, g, b)
            _uiState.value = _uiState.value.copy(
                statusBanner = if (ok) str(R.string.status_rgb_updated, r, g, b) else str(R.string.status_rgb_failed)
            )
        }
    }

    fun readAntennaConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReadingAntenna = true)
            val cfg = pm5Manager.readAntennaConfig()
            _uiState.value = _uiState.value.copy(
                isReadingAntenna = false,
                antennaConfig = cfg,
                statusBanner = if (cfg != null) {
                    str(R.string.status_antenna_cfg_read, cfg.rawByte, cfg.activeFreqString)
                } else {
                    str(R.string.status_antenna_cfg_failed)
                }
            )
        }
    }

    fun toggleAntennaLed(isLf: Boolean, enabled: Boolean) {
        val current = _uiState.value.antennaConfig ?: Pm5AntennaConfig()
        val newLf = if (isLf) enabled else current.lfLed
        val newHf = if (!isLf) enabled else current.hfLed
        setAntennaLeds(newLf, newHf)
    }

    fun setAntennaLeds(lfEnabled: Boolean, hfEnabled: Boolean) {
        val current = _uiState.value.antennaConfig ?: Pm5AntennaConfig()
        val optimistic = current.copy(lfLed = lfEnabled, hfLed = hfEnabled)
        _uiState.value = _uiState.value.copy(antennaConfig = optimistic)
        viewModelScope.launch {
            val ok = pm5Manager.setAntennaLeds(lfEnabled, hfEnabled)
            _uiState.value = _uiState.value.copy(
                antennaConfig = if (ok) optimistic else current,
                statusBanner = if (ok) {
                    str(
                        R.string.status_antenna_leds_updated,
                        str(if (lfEnabled) R.string.led_state_on else R.string.led_state_off),
                        str(if (hfEnabled) R.string.led_state_on else R.string.led_state_off)
                    )
                } else {
                    str(R.string.status_antenna_leds_failed)
                }
            )
        }
    }

    fun setAntennaFreq(freq: String) {
        viewModelScope.launch {
            val ok = pm5Manager.setAntennaFreq(freq)
            if (ok) {
                val cfg = pm5Manager.readAntennaConfig()
                _uiState.value = _uiState.value.copy(
                    antennaConfig = cfg,
                    statusBanner = str(R.string.status_antenna_freq_set, freq)
                )
            }
        }
    }

    fun setAntennaQ(highQ: Boolean) {
        viewModelScope.launch {
            val ok = pm5Manager.setAntennaQ(highQ)
            if (ok) {
                val cfg = pm5Manager.readAntennaConfig()
                _uiState.value = _uiState.value.copy(
                    antennaConfig = cfg,
                    statusBanner = str(
                        R.string.status_antenna_q_set,
                        str(if (highQ) R.string.label_high_q else R.string.label_low_q)
                    )
                )
            }
        }
    }

    fun runHardwareQcTest() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunningQcTest = true)
            val result = pm5Manager.runHardwareQcTest(1000)
            _uiState.value = _uiState.value.copy(
                isRunningQcTest = false,
                qcTestResult = result,
                statusBanner = result.message
            )
        }
    }

    fun toggleCharge(enable: Boolean) {
        viewModelScope.launch {
            val ok = pm5Manager.setChargeEnable(enable)
            _uiState.value = _uiState.value.copy(
                statusBanner = if (ok) {
                    str(if (enable) R.string.status_charging_enabled else R.string.status_charging_disabled)
                } else {
                    str(R.string.status_charge_toggle_failed)
                }
            )
            delay(300)
            pm5Manager.readBattery()
        }
    }

    fun setChargeVoltage(millivolts: Int) {
        viewModelScope.launch {
            val ok = pm5Manager.setChargeVoltage(millivolts)
            _uiState.value = _uiState.value.copy(
                statusBanner = if (ok) {
                    str(R.string.status_cutoff_voltage_set, millivolts)
                } else {
                    str(R.string.status_charge_voltage_failed)
                }
            )
        }
    }

    fun setBleName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val ok = pm5Manager.setBleName(name)
            _uiState.value = _uiState.value.copy(
                bleAdvertisingName = name,
                statusBanner = if (ok) str(R.string.status_ble_name_updated, name) else str(R.string.status_ble_name_failed)
            )
        }
    }

    fun measureAntennaTuning() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTuningAntenna = true, statusBanner = str(R.string.status_measuring_antenna))
            val tuning = pm5Manager.measureAntennaTuning(16000)
            _uiState.value = _uiState.value.copy(
                antennaTuning = tuning,
                isTuningAntenna = false,
                statusBanner = if (tuning != null) {
                    str(R.string.status_antenna_tuned, tuning.lf125V, tuning.lf134V, tuning.hfV, tuning.peakV)
                } else {
                    str(R.string.status_antenna_tuning_failed)
                }
            )
        }
    }

    private fun fetchVersion() {
        viewModelScope.launch {
            val ver = pm5Manager.readVersion()
            val updatedCap = if (_uiState.value.deviceCapabilities.capabilitiesKnown) {
                _uiState.value.deviceCapabilities.copy(fallbackVersionText = ver?.versionString ?: "")
            } else {
                DeviceCapabilities.fromVersionFallback(ver)
            }
            _uiState.value = _uiState.value.copy(
                versionInfo = ver,
                deviceCapabilities = updatedCap
            )
        }
    }

    // --- LF Badge Studio ---

    fun toggleWatch(tech: String) {
        autoScanJob?.cancel()
        autoScanJob = null
        viewModelScope.launch {
            val active = when (tech) {
                "HID" -> pm5Manager.toggleWatchHid()
                "EM4100" -> pm5Manager.toggleWatchEm4100()
                "AWID" -> pm5Manager.toggleWatchAwid()
                "IO" -> pm5Manager.toggleWatchIo()
                else -> false
            }
            _uiState.value = _uiState.value.copy(
                isWatchingHid = tech == "HID" && active,
                isWatchingEm4100 = tech == "EM4100" && active,
                isWatchingAwid = tech == "AWID" && active,
                isWatchingIo = tech == "IO" && active,
                isAutoScanning = false,
                statusBanner = if (active) str(R.string.status_watch_started, tech) else str(R.string.status_watch_stopped)
            )
        }
    }

    fun startAutoScan() {
        if (_uiState.value.isAutoScanning) {
            stopAutoScan()
            return
        }
        autoScanJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isAutoScanning = true,
            isWatchingHid = false,
            isWatchingEm4100 = false,
            isWatchingAwid = false,
            isWatchingIo = false,
            statusBanner = str(R.string.status_autoscan_started)
        )

        autoScanJob = viewModelScope.launch(Dispatchers.IO) {
            val modes = listOf("HID", "EM4100", "AWID", "IO")
            var idx = 0
            while (isActive && _uiState.value.isAutoScanning) {
                val mode = modes[idx % modes.size]
                val active = when (mode) {
                    "HID" -> pm5Manager.toggleWatchHid()
                    "EM4100" -> pm5Manager.toggleWatchEm4100()
                    "AWID" -> pm5Manager.toggleWatchAwid()
                    "IO" -> pm5Manager.toggleWatchIo()
                    else -> false
                }
                _uiState.value = _uiState.value.copy(
                    isWatchingHid = mode == "HID" && active,
                    isWatchingEm4100 = mode == "EM4100" && active,
                    isWatchingAwid = mode == "AWID" && active,
                    isWatchingIo = mode == "IO" && active,
                    statusBanner = str(R.string.status_autoscan_sweeping, mode)
                )
                delay(3200)
                pm5Manager.breakLoop()
                delay(150)
                idx++
            }
        }
    }

    fun stopAutoScan() {
        autoScanJob?.cancel()
        autoScanJob = null
        viewModelScope.launch {
            pm5Manager.breakLoop()
            _uiState.value = _uiState.value.copy(
                isAutoScanning = false,
                isWatchingHid = false,
                isWatchingEm4100 = false,
                isWatchingAwid = false,
                isWatchingIo = false,
                statusBanner = str(R.string.status_autoscan_stopped)
            )
        }
    }

    fun setCloneTargetChip(chip: Int) {
        _uiState.value = _uiState.value.copy(cloneTargetChip = chip)
    }

    fun executeClone(uidHex: String) {
        val em = Em4100Decoder.fromHex(uidHex)
        if (em == null) {
            _uiState.value = _uiState.value.copy(cloneStatus = str(R.string.status_clone_invalid_uid))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                lastEm4100Tag = em,
                cloneStatus = str(R.string.status_cloning_in_progress, em.uidHex)
            )
            val res = pm5Manager.cloneEm4100(
                uid = em.uidLong,
                targetChip = _uiState.value.cloneTargetChip,
                clock = _uiState.value.cloneClock,
                addElectra = _uiState.value.cloneAddElectra
            )
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                cloneStatus = res.getOrElse { it.message ?: str(R.string.status_clone_failed) },
                statusBanner = res.getOrNull()
            )
        }
    }

    // --- HF Studio ---

    fun scanIso14443a() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningHf = true, hfStatus = str(R.string.status_scanning_hf))
            val card = pm5Manager.readIso14443a(4000)
            _uiState.value = _uiState.value.copy(
                isScanningHf = false,
                hfStatus = if (card != null) {
                    str(R.string.status_hf_card_found, card.cardType, card.uidHex)
                } else {
                    str(R.string.status_no_hf_card)
                }
            )
        }
    }

    // --- T55xx Transponder Suite ---

    fun selectT55xxBlock(block: Int) {
        _uiState.value = _uiState.value.copy(selectedT55xxBlock = block)
    }

    fun selectT55xxPage(page: Int) {
        _uiState.value = _uiState.value.copy(selectedT55xxPage = page)
    }

    fun setT55xxPasswordHex(pwdHex: String) {
        _uiState.value = _uiState.value.copy(t55xxPasswordHex = pwdHex.filter { it.isLetterOrDigit() }.take(8).uppercase())
    }

    fun setT55xxUsePwd(use: Boolean) {
        _uiState.value = _uiState.value.copy(t55xxUsePwd = use)
    }

    fun setT55xxDownlinkMode(mode: Int) {
        _uiState.value = _uiState.value.copy(t55xxDownlinkMode = mode.coerceIn(0, 3))
    }

    fun setT55xxTestMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(t55xxTestMode = enabled)
    }

    fun setWhiteClonerUidHex(uidHex: String) {
        val cleaned = uidHex.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(10).uppercase()
        _uiState.value = _uiState.value.copy(whiteClonerUidHex = cleaned)
    }

    fun setWriteBlockDataHex(dataHex: String) {
        _uiState.value = _uiState.value.copy(writeBlockDataHex = dataHex.filter { it.isLetterOrDigit() }.take(8).uppercase())
    }

    private fun parsePassword(): Long {
        val hex = _uiState.value.t55xxPasswordHex
        return if (hex.isNotEmpty()) hex.toLongOrNull(16) ?: 0L else 0L
    }

    fun readT55xxBlock(block: Int) {
        val pwd = parsePassword()
        val usePwd = _uiState.value.t55xxUsePwd
        val page = _uiState.value.selectedT55xxPage
        val dlMode = _uiState.value.t55xxDownlinkMode

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val result = pm5Manager.readT55xxBlock(block, page, pwd, usePwd, dlMode)
            val map = _uiState.value.t55xxBlocks.toMutableMap()
            val configDesc = if (block == 0 && result.rawValue != null) {
                T55xxPayloadBuilder.decodeT55xxBlock0(result.rawValue)
            } else null

            map[block] = T55xxBlockEntry(
                blockNo = block,
                page = page,
                value = result.rawValue,
                hexString = result.hexValue,
                binaryString = result.binaryValue,
                asciiString = result.asciiValue,
                isDecodedConfig = (block == 0 && configDesc != null),
                configDescription = configDesc,
                lastReadStatus = result.message
            )

            _uiState.value = _uiState.value.copy(
                isBusy = false,
                t55xxBlocks = map,
                lastReadResult = result,
                statusBanner = str(R.string.status_t55xx_block_result, block, result.hexValue)
            )
        }
    }

    fun readAllT55xxBlocks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusBanner = str(R.string.status_reading_t55xx_all))
            for (b in 0..7) {
                readT55xxBlock(b)
                delay(100)
            }
            _uiState.value = _uiState.value.copy(isBusy = false, statusBanner = str(R.string.status_t55xx_dump_complete))
        }
    }

    fun writeT55xxBlock() {
        val block = _uiState.value.selectedT55xxBlock
        val valHex = _uiState.value.writeBlockDataHex
        val value = valHex.toLongOrNull(16)
        if (value == null) {
            _uiState.value = _uiState.value.copy(writeStatus = str(R.string.status_write_invalid_hex))
            return
        }
        val pwd = parsePassword()
        val usePwd = _uiState.value.t55xxUsePwd
        val testMode = _uiState.value.t55xxTestMode
        val page = _uiState.value.selectedT55xxPage
        val dlMode = _uiState.value.t55xxDownlinkMode

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, writeStatus = str(R.string.status_writing_t55xx_block, block))
            val res = pm5Manager.writeT55xxBlock(block, value, page, pwd, usePwd, testMode, dlMode)
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                writeStatus = res.getOrElse { it.message ?: str(R.string.status_write_failed) },
                statusBanner = res.getOrNull()
            )
            // Re-read block to confirm
            if (res.isSuccess) {
                delay(150)
                readT55xxBlock(block)
            }
        }
    }

    fun runPasswordDictionary() {
        if (_uiState.value.isCheckingPwds) {
            dictionaryJob?.cancel()
            _uiState.value = _uiState.value.copy(isCheckingPwds = false, chkPwdStatus = str(R.string.status_dictionary_cancelled))
            return
        }

        dictionaryJob = viewModelScope.launch(Dispatchers.IO) {
            val dictCount = T55xxPayloadBuilder.DEFAULT_DICTIONARY.size
            _uiState.value = _uiState.value.copy(
                isCheckingPwds = true,
                foundPassword = null,
                chkPwdStatus = pluralStr(R.plurals.status_testing_passwords, dictCount, dictCount)
            )

            val dictionary = T55xxPayloadBuilder.DEFAULT_DICTIONARY
            val dlMode = _uiState.value.t55xxDownlinkMode
            for ((idx, pwd) in dictionary.withIndex()) {
                val hex = "%08X".format(pwd)
                _uiState.value = _uiState.value.copy(
                    chkPwdStatus = str(R.string.status_testing_password_entry, idx, dictionary.size, hex)
                )
                val read = pm5Manager.readT55xxBlock(0, 0, pwd, usePwd = true, downlinkMode = dlMode, timeoutMs = 4000)
                if (read.rawValue != null && read.status == Pm5Constants.PM3_SUCCESS) {
                    _uiState.value = _uiState.value.copy(
                        isCheckingPwds = false,
                        foundPassword = pwd,
                        t55xxPasswordHex = hex,
                        t55xxUsePwd = true,
                        chkPwdStatus = str(R.string.status_password_found, hex),
                        statusBanner = str(R.string.status_password_recovered_banner, hex)
                    )
                    return@launch
                }
                delay(80)
            }

            _uiState.value = _uiState.value.copy(
                isCheckingPwds = false,
                chkPwdStatus = str(R.string.status_no_matching_password)
            )
        }
    }

    fun calculateWhiteClonerKey(uidHex: String = _uiState.value.whiteClonerUidHex) {
        val uid = uidHex.toLongOrNull(16) ?: return
        val calculated = T55xxPayloadBuilder.calculateWhiteClonerPassword(uid)
        _uiState.value = _uiState.value.copy(
            whiteClonerUidHex = uidHex.uppercase(),
            whiteClonerCalculatedPwd = calculated,
            t55xxPasswordHex = "%08X".format(calculated),
            t55xxUsePwd = true,
            statusBanner = str(R.string.status_white_cloner_derived, calculated)
        )
    }

    fun runFlashPasswordCheck() {
        if (!_uiState.value.deviceCapabilities.hwAvailableFlash) {
            _uiState.value = _uiState.value.copy(chkPwdStatus = "SPI Flash dictionary unavailable on this hardware")
            return
        }
        val dlMode = _uiState.value.t55xxDownlinkMode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingPwds = true, chkPwdStatus = "Checking RDV4 SPI flash dictionary...")
            val res = pm5Manager.checkT55xxPasswordsInFlash(dlMode)
            _uiState.value = _uiState.value.copy(
                isCheckingPwds = false,
                foundPassword = res.candidateVal,
                t55xxPasswordHex = res.candidateHex ?: _uiState.value.t55xxPasswordHex,
                t55xxUsePwd = res.found || _uiState.value.t55xxUsePwd,
                chkPwdStatus = res.message,
                statusBanner = res.message
            )
        }
    }

    fun wakeupT55xx() {
        val pwd = parsePassword()
        val dlMode = _uiState.value.t55xxDownlinkMode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val res = pm5Manager.wakeupT55xx(pwd, dlMode)
            val msg = res.getOrElse { it.message ?: "Wakeup failed" }
            _uiState.value = _uiState.value.copy(isBusy = false, statusBanner = msg, chkPwdStatus = msg)
        }
    }

    fun resetReadT55xx() {
        val dlMode = _uiState.value.t55xxDownlinkMode
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val res = pm5Manager.resetReadT55xx(dlMode)
            val msg = res.getOrElse { it.message ?: "Reset read failed" }
            _uiState.value = _uiState.value.copy(isBusy = false, statusBanner = msg)
        }
    }

    fun updateTimingForSelectedMode(
        startGap: Int? = null,
        writeGap: Int? = null,
        write0: Int? = null,
        write1: Int? = null,
        readGap: Int? = null
    ) {
        val mode = _uiState.value.t55xxDownlinkMode.coerceIn(0, 3)
        val modes = _uiState.value.timingConfig.modes.toMutableList()
        val curr = modes[mode]
        modes[mode] = curr.copy(
            startGap = startGap ?: curr.startGap,
            writeGap = writeGap ?: curr.writeGap,
            write0 = write0 ?: curr.write0,
            write1 = write1 ?: curr.write1,
            readGap = readGap ?: curr.readGap
        )
        _uiState.value = _uiState.value.copy(timingConfig = _uiState.value.timingConfig.copy(modes = modes))
    }

    fun applyT55xxTimingConfig() {
        val cfg = _uiState.value.timingConfig
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val res = pm5Manager.setT55xxConfig(cfg)
            val msg = res.getOrElse { it.message ?: "Set config failed" }
            _uiState.value = _uiState.value.copy(isBusy = false, timingStatus = msg, statusBanner = msg)
        }
    }

    fun setDangerRawUnlocked(unlocked: Boolean) {
        _uiState.value = _uiState.value.copy(dangerRawUnlocked = unlocked)
    }

    fun setDangerRawBitstream(bits: String) {
        _uiState.value = _uiState.value.copy(dangerRawBitstream = bits.filter { it == '0' || it == '1' }.take(128))
    }

    fun setDangerRawTimeUs(timeUs: String) {
        _uiState.value = _uiState.value.copy(dangerRawTimeUs = timeUs.filter { it.isDigit() }.take(8))
    }

    fun executeDangerRaw() {
        val s = _uiState.value
        if (!s.dangerRawUnlocked) {
            _uiState.value = s.copy(dangerRawStatus = "Locked: acknowledge warning before sending raw downlink")
            return
        }
        if (s.dangerRawBitstream.isEmpty()) {
            _uiState.value = s.copy(dangerRawStatus = "Enter binary bits (0/1) first")
            return
        }
        val timeUs = s.dangerRawTimeUs.toLongOrNull() ?: 1000L
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            val res = pm5Manager.dangerRawT55xx(s.dangerRawBitstream, timeUs)
            val msg = res.getOrElse { it.message ?: "DangerRaw failed" }
            _uiState.value = _uiState.value.copy(isBusy = false, dangerRawStatus = msg, statusBanner = msg)
        }
    }

    fun breakLoop() {
        viewModelScope.launch {
            pm5Manager.breakLoop()
        }
    }

    fun ping() {
        viewModelScope.launch {
            val res = pm5Manager.executeRawTransaction(
                Pm5Constants.CMD_PING,
                "CMD_PING",
                "PING".toByteArray(Charsets.US_ASCII),
                2000
            )
            _uiState.value = _uiState.value.copy(
                statusBanner = str(R.string.status_ping_echo, res.success.toString(), res.latencyMs)
            )
        }
    }

    // --- Saved Tags ---

    private fun applyVaultResult(result: VaultResult) {
        when (result) {
            is VaultResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    savedTags = result.tags,
                    vaultError = null
                )
            }
            is VaultResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    savedTags = result.cachedTags,
                    vaultError = result.message,
                    statusBanner = result.message
                )
            }
        }
    }

    private fun loadSavedTags() {
        viewModelScope.launch {
            applyVaultResult(storageManager.loadAll())
        }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch {
            applyVaultResult(storageManager.delete(id))
        }
    }

    fun clearVault() {
        viewModelScope.launch {
            applyVaultResult(storageManager.clear())
        }
    }

    fun exportVaultToUri(uri: Uri) {
        viewModelScope.launch {
            val exportRes = storageManager.exportJson()
            exportRes.fold(
                onSuccess = { json ->
                    val writeResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = json.toByteArray(Charsets.UTF_8)
                            getApplication<Application>().contentResolver
                                .openOutputStream(uri, "wt")
                                ?.use { out ->
                                    out.write(bytes)
                                    out.flush()
                                } ?: error(str(R.string.status_vault_export_open_failed))
                            bytes.size
                        }
                    }
                    writeResult.fold(
                        onSuccess = { byteCount ->
                            _uiState.value = _uiState.value.copy(
                                statusBanner = pluralStr(R.plurals.status_vault_exported, byteCount, byteCount)
                            )
                        },
                        onFailure = { err ->
                            _uiState.value = _uiState.value.copy(
                                statusBanner = str(R.string.status_vault_export_failed, err.message ?: str(R.string.error_io))
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        vaultError = err.message,
                        statusBanner = err.message ?: str(R.string.status_vault_export_failed_generic)
                    )
                }
            )
        }
    }

    override fun onCleared() {
        stopBatteryPolling()
        autoScanJob?.cancel()
        dictionaryJob?.cancel()
        pm5Manager.stopScan(bluetoothAdapter)
        pm5Manager.shutdown()
        super.onCleared()
    }
}
