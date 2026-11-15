package com.proxmark5.client.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.comm.DiscoveredBleDevice
import com.proxmark5.client.comm.Pm5BleManager
import com.proxmark5.client.comm.T55xxPwdCheckResult
import com.proxmark5.client.comm.T55xxReadResult
import com.proxmark5.client.comm.VersionInfo
import com.proxmark5.client.data.CapturedTag
import com.proxmark5.client.data.TagStorageManager
import com.proxmark5.client.protocol.DecodedBadge
import com.proxmark5.client.protocol.Em4100Decoder
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
    val discoveredDevices: List<DiscoveredBleDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val tcpHost: String = "192.168.4.1",
    val tcpPort: String = "18888",
    val isBusy: Boolean = false,
    val statusBanner: String? = null,
    val versionInfo: VersionInfo? = null,

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
    val t55xxDownlinkMode: Int = Pm5Constants.T55XX_DLMODE_FIXED,
    val lastReadResult: T55xxReadResult? = null,
    val writeBlockDataHex: String = "",
    val writeStatus: String? = null,
    val isCheckingPwds: Boolean = false,
    val chkPwdStatus: String? = null,
    val foundPassword: Long? = null,
    val whiteClonerCalculatedPwd: Long? = null,
    val timingConfig: T55xxTimingConfig = T55xxTimingConfig.defaultTimings(persist = false),

    // Saved Vault & Console Logs
    val savedTags: List<CapturedTag> = emptyList(),
    val logs: List<String> = emptyList()
)

class Pm5ViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    val pm5Manager = Pm5BleManager(application, viewModelScope)
    private val storageManager = TagStorageManager(application)

    private val _uiState = MutableStateFlow(Pm5UiState())
    val uiState: StateFlow<Pm5UiState> = _uiState.asStateFlow()

    private var batteryPollingJob: Job? = null
    private var autoScanJob: Job? = null
    private var dictionaryJob: Job? = null

    init {
        refreshPairedDevices()
        loadSavedTags()

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
                    startBatteryPolling()
                    fetchVersion()
                    readAntennaConfig()
                } else {
                    stopBatteryPolling()
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
                    statusBanner = "Detected: ${badge.technology} [${badge.uidHex}]"
                )
                // If EM4100, parse full tag
                if (badge.technology.contains("EM4100", ignoreCase = true)) {
                    val em = Em4100Decoder.fromHex(badge.uidHex)
                    if (em != null) {
                        _uiState.value = _uiState.value.copy(lastEm4100Tag = em)
                    }
                }
                // Save to Vault
                storageManager.save(
                    CapturedTag(
                        uidHex = badge.uidHex,
                        technology = badge.technology,
                        frequency = "LF",
                        cardId = badge.cardId,
                        facilityCode = badge.facilityCode,
                        formatDescription = badge.formatLen
                    )
                )
                loadSavedTags()
            }
        }

        // Observe HF Card detections
        viewModelScope.launch {
            pm5Manager.hfCardFlow.collect { card ->
                _uiState.value = _uiState.value.copy(
                    lastHfCard = card,
                    statusBanner = "HF Card Detected: ${card.cardType} [${card.uidHex}]"
                )
                // Save to Vault
                storageManager.save(
                    CapturedTag(
                        uidHex = card.uidHex,
                        technology = card.cardType,
                        frequency = "HF",
                        formatDescription = "SAK: 0x${card.sakHex}, ATQA: ${card.atqaHex}"
                    )
                )
                loadSavedTags()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        val sorted = bonded.sortedByDescending {
            val n = it.name ?: ""
            n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(pairedDevices = sorted)
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

    fun connectTcp(host: String = _uiState.value.tcpHost, portStr: String = _uiState.value.tcpPort) {
        val port = portStr.toIntOrNull() ?: Pm5Constants.DEFAULT_WIFI_PORT
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            pm5Manager.connectTcp(host, port)
            _uiState.value = _uiState.value.copy(isBusy = false)
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
                if (s.connectionState is ConnectionState.Connected && !s.isBusy && !lfWatchActive) {
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
                statusBanner = if (ok) "RGB set to R:$r G:$g B:$b" else "Failed to set RGB"
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
                statusBanner = if (cfg != null) "Antenna register: 0x%02X (%s)".format(cfg.rawByte, cfg.activeFreqString) else "Antenna controller (0x51) not responding"
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
                statusBanner = if (ok) "Antenna Blue LEDs: LF=${if (lfEnabled) "ON" else "OFF"} | HF=${if (hfEnabled) "ON" else "OFF"}" else "Failed to update antenna LEDs"
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
                    statusBanner = "Antenna tuned to $freq"
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
                    statusBanner = "Antenna Q-factor set to ${if (highQ) "High Q" else "Low Q"}"
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
                statusBanner = if (ok) "Charge ${if (enable) "Enabled" else "Disabled"}" else "Failed to toggle charge"
            )
            delay(300)
            pm5Manager.readBattery()
        }
    }

    fun setChargeVoltage(millivolts: Int) {
        viewModelScope.launch {
            val ok = pm5Manager.setChargeVoltage(millivolts)
            _uiState.value = _uiState.value.copy(
                statusBanner = if (ok) "Charge target set to $millivolts mV" else "Failed to set charge voltage"
            )
        }
    }

    fun setBleName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val ok = pm5Manager.setBleName(name)
            _uiState.value = _uiState.value.copy(
                bleAdvertisingName = name,
                statusBanner = if (ok) "BLE name updated to '$name' (BWM restarting...)" else "Failed to set BLE name"
            )
        }
    }

    fun measureAntennaTuning() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTuningAntenna = true, statusBanner = "Sweeping antenna frequencies (12s)...")
            val tuning = pm5Manager.measureAntennaTuning(16000)
            _uiState.value = _uiState.value.copy(
                antennaTuning = tuning,
                isTuningAntenna = false,
                statusBanner = if (tuning != null) {
                    "Tuning: LF125=%.2fV, LF134=%.2fV, HF=%.2fV, Peak=%.2fV".format(tuning.lf125V, tuning.lf134V, tuning.hfV, tuning.peakV)
                } else "Antenna tuning failed"
            )
        }
    }

    private fun fetchVersion() {
        viewModelScope.launch {
            val ver = pm5Manager.readVersion()
            _uiState.value = _uiState.value.copy(versionInfo = ver)
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
                statusBanner = if (active) "LF Watch active: $tech (125 kHz)" else "LF Watch stopped"
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
            statusBanner = "Auto-Scanner active: sweeping HID, EM4100, AWID, IO..."
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
                    statusBanner = "Auto-Scanner sweeping: $mode (125 kHz)..."
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
                statusBanner = "Auto-Scan stopped"
            )
        }
    }

    fun setCloneTargetChip(chip: Int) {
        _uiState.value = _uiState.value.copy(cloneTargetChip = chip)
    }

    fun executeClone(uidHex: String) {
        val em = Em4100Decoder.fromHex(uidHex)
        if (em == null) {
            _uiState.value = _uiState.value.copy(cloneStatus = "Invalid 10-hex UID format")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, cloneStatus = "Cloning ${em.uidHex} to transponder...")
            val res = pm5Manager.cloneEm4100(
                uid = em.uidLong,
                targetChip = _uiState.value.cloneTargetChip,
                clock = _uiState.value.cloneClock,
                addElectra = _uiState.value.cloneAddElectra
            )
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                cloneStatus = res.getOrElse { it.message ?: "Clone failed" },
                statusBanner = res.getOrNull()
            )
        }
    }

    // --- HF Studio ---

    fun scanIso14443a() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningHf = true, hfStatus = "Scanning for ISO 14443-A card...")
            val card = pm5Manager.readIso14443a(4000)
            _uiState.value = _uiState.value.copy(
                isScanningHf = false,
                hfStatus = if (card != null) "Found ${card.cardType} (UID: ${card.uidHex})" else "No ISO14443A card response"
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
        _uiState.value = _uiState.value.copy(t55xxDownlinkMode = mode)
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
                statusBanner = "Block $block: ${result.hexValue}"
            )
        }
    }

    fun readAllT55xxBlocks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusBanner = "Reading all blocks 0-7 (fast MCU ASK)...")
            for (b in 0..7) {
                readT55xxBlock(b)
                delay(100)
            }
            _uiState.value = _uiState.value.copy(isBusy = false, statusBanner = "All blocks read completed")
        }
    }

    fun writeT55xxBlock() {
        val block = _uiState.value.selectedT55xxBlock
        val valHex = _uiState.value.writeBlockDataHex
        val value = valHex.toLongOrNull(16)
        if (value == null) {
            _uiState.value = _uiState.value.copy(writeStatus = "Invalid 8-hex data value")
            return
        }
        val pwd = parsePassword()
        val usePwd = _uiState.value.t55xxUsePwd
        val page = _uiState.value.selectedT55xxPage
        val dlMode = _uiState.value.t55xxDownlinkMode

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, writeStatus = "Writing Block $block...")
            val res = pm5Manager.writeT55xxBlock(block, value, page, pwd, usePwd, false, dlMode)
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                writeStatus = res.getOrElse { it.message ?: "Write failed" },
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
            _uiState.value = _uiState.value.copy(isCheckingPwds = false, chkPwdStatus = "Dictionary cancelled")
            return
        }

        dictionaryJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isCheckingPwds = true,
                foundPassword = null,
                chkPwdStatus = "Auditing ${T55xxPayloadBuilder.DEFAULT_DICTIONARY.size} standard keys..."
            )

            val dictionary = T55xxPayloadBuilder.DEFAULT_DICTIONARY
            for ((idx, pwd) in dictionary.withIndex()) {
                val hex = "%08X".format(pwd)
                _uiState.value = _uiState.value.copy(chkPwdStatus = "[$idx/${dictionary.size}] Testing 0x$hex...")
                val read = pm5Manager.readT55xxBlock(0, 0, pwd, usePwd = true, timeoutMs = 1500)
                if (read.rawValue != null && read.status == Pm5Constants.PM3_SUCCESS) {
                    _uiState.value = _uiState.value.copy(
                        isCheckingPwds = false,
                        foundPassword = pwd,
                        t55xxPasswordHex = hex,
                        t55xxUsePwd = true,
                        chkPwdStatus = "Found Valid Password: 0x$hex!",
                        statusBanner = "Valid Password: 0x$hex"
                    )
                    return@launch
                }
                delay(80)
            }

            _uiState.value = _uiState.value.copy(
                isCheckingPwds = false,
                chkPwdStatus = "Dictionary finished. No matching default password found."
            )
        }
    }

    fun calculateWhiteClonerKey(uidHex: String) {
        val uid = uidHex.toLongOrNull(16) ?: return
        val calculated = T55xxPayloadBuilder.calculateWhiteClonerPassword(uid)
        _uiState.value = _uiState.value.copy(
            whiteClonerCalculatedPwd = calculated,
            t55xxPasswordHex = "%08X".format(calculated),
            t55xxUsePwd = true,
            statusBanner = "Calculated White Cloner Password: 0x%08X".format(calculated)
        )
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
                statusBanner = "Ping: ok=${res.success}, latency=${res.latencyMs}ms"
            )
        }
    }

    // --- Saved Tags ---

    private fun loadSavedTags() {
        _uiState.value = _uiState.value.copy(savedTags = storageManager.loadAll())
    }

    fun deleteTag(id: String) {
        _uiState.value = _uiState.value.copy(savedTags = storageManager.delete(id))
    }

    fun clearVault() {
        _uiState.value = _uiState.value.copy(savedTags = storageManager.clear())
    }

    fun exportVaultJson(): String {
        return storageManager.exportJson()
    }
}
