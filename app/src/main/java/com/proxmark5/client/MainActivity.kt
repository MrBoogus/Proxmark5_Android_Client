package com.proxmark5.client

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.ui.Pm5AppScreen
import com.proxmark5.client.ui.Pm5ViewModel
import com.proxmark5.client.ui.theme.Proxmark5Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val viewModel: Pm5ViewModel by viewModels()

    private var debugReceiverRegistered = false

    private val debugReceiver: BroadcastReceiver? = if (BuildConfig.DEBUG) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!BuildConfig.DEBUG) return
                when (intent.action) {
                    "com.proxmark5.client.CONNECT_FIRST" -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            viewModel.refreshPairedDevices()
                            val paired = viewModel.uiState.value.pairedDevices
                            val dev = paired.firstOrNull {
                                val n = try { it.name ?: "" } catch (_: SecurityException) { "" }
                                n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
                            }
                            if (dev != null) {
                                viewModel.selectDevice(dev)
                                viewModel.connectSelectedDevice()
                            } else {
                                viewModel.startBleScan()
                            }
                        }
                    }
                    "com.proxmark5.client.SCAN_AND_CONNECT" -> viewModel.startBleScan()
                    "com.proxmark5.client.READ_BATTERY" -> viewModel.refreshBatteryNow()
                    "com.proxmark5.client.SET_RGB" -> {
                        val r = intent.getIntExtra("r", 0)
                        val g = intent.getIntExtra("g", 255)
                        val b = intent.getIntExtra("b", 255)
                        viewModel.setRgbColor(Color(r, g, b))
                    }
                    "com.proxmark5.client.SWITCH_TAB" -> {
                        val tab = intent.getIntExtra("tab", 0)
                        viewModel.setSelectedTab(tab)
                    }
                    "com.proxmark5.client.AUTO_SCAN" -> viewModel.startAutoScan()
                    "com.proxmark5.client.STOP_SCAN" -> viewModel.stopAutoScan()
                    "com.proxmark5.client.TOGGLE_WATCH" -> {
                        val tech = intent.getStringExtra("tech") ?: "HID"
                        viewModel.toggleWatch(tech)
                    }
                    "com.proxmark5.client.SCAN_HF" -> viewModel.scanIso14443a()
                    "com.proxmark5.client.READ_T55XX_ALL" -> viewModel.readAllT55xxBlocks()
                    "com.proxmark5.client.EXECUTE_CLONE" -> {
                        val uid = intent.getStringExtra("uid") ?: "0102030405"
                        viewModel.executeClone(uid)
                    }
                    "com.proxmark5.client.WHITE_CLONER_KEY" -> {
                        val uid = intent.getStringExtra("uid") ?: "0102030405"
                        viewModel.calculateWhiteClonerKey(uid)
                    }
                    "com.proxmark5.client.MEASURE_ANTENNA" -> viewModel.measureAntennaTuning()
                    "com.proxmark5.client.READ_ANTENNA" -> viewModel.readAntennaConfig()
                    "com.proxmark5.client.SET_ANTENNA_LEDS" -> {
                        val lf = intent.getBooleanExtra("lf", true)
                        val hf = intent.getBooleanExtra("hf", true)
                        viewModel.setAntennaLeds(lf, hf)
                    }
                    "com.proxmark5.client.SET_ANTENNA_FREQ" -> {
                        val freq = intent.getStringExtra("freq") ?: "125k"
                        viewModel.setAntennaFreq(freq)
                    }
                    "com.proxmark5.client.SET_ANTENNA_Q" -> {
                        val highQ = intent.getBooleanExtra("highQ", false)
                        viewModel.setAntennaQ(highQ)
                    }
                    "com.proxmark5.client.TOGGLE_CHARGE" -> {
                        val enable = intent.getBooleanExtra("enable", true)
                        viewModel.toggleCharge(enable)
                    }
                    "com.proxmark5.client.RUN_QC_TEST" -> viewModel.runHardwareQcTest()
                    "com.proxmark5.client.PING" -> viewModel.ping()
                    "com.proxmark5.client.CONNECT_SPP" -> {
                        val mac = intent.getStringExtra("mac") ?: "20:24:09:20:02:58"
                        viewModel.connectClassicBtAddress(mac)
                    }
                    "com.proxmark5.client.CONNECT_TCP" -> {
                        val host = intent.getStringExtra("host") ?: "127.0.0.1"
                        val port = intent.getStringExtra("port") ?: "18888"
                        viewModel.connectTcp(host, port)
                    }
                    "com.proxmark5.client.DISCONNECT" -> viewModel.disconnect()
                    "com.proxmark5.client.RECONNECT_LAST" -> viewModel.reconnectLastDevice()
                    "com.proxmark5.client.READ_RDV4_FLASH" -> viewModel.readRdv4FlashInfo()
                    "com.proxmark5.client.READ_SMARTCARD_ATR" -> viewModel.readSmartcardAtr()
                }
            }
        }
    } else null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            viewModel.refreshPairedDevices()
            autoConnectProxmarkIfBonded()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Keep screen awake only while a scan, watch loop, or sweep is actively running.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val activeOperation = state.connectionState is ConnectionState.Scanning ||
                        state.isAutoScanning ||
                        state.isWatchingEm4100 ||
                        state.isWatchingHid ||
                        state.isWatchingAwid ||
                        state.isWatchingIo ||
                        state.isScanningHf ||
                        state.isTuningAntenna ||
                        state.isCheckingPwds ||
                        state.isRunningQcTest
                    if (activeOperation) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        // Request runtime permissions
        requestPermissionsIfNeeded()

        // Register DUMP-permission-gated debug receiver in debug builds only so adb shell
        // broadcasts work without exposing automation hooks to third-party apps.
        val receiver = debugReceiver
        if (BuildConfig.DEBUG && receiver != null) {
            val filter = IntentFilter().apply {
                addAction("com.proxmark5.client.CONNECT_FIRST")
                addAction("com.proxmark5.client.SCAN_AND_CONNECT")
                addAction("com.proxmark5.client.READ_BATTERY")
                addAction("com.proxmark5.client.SET_RGB")
                addAction("com.proxmark5.client.SWITCH_TAB")
                addAction("com.proxmark5.client.AUTO_SCAN")
                addAction("com.proxmark5.client.STOP_SCAN")
                addAction("com.proxmark5.client.TOGGLE_WATCH")
                addAction("com.proxmark5.client.SCAN_HF")
                addAction("com.proxmark5.client.READ_T55XX_ALL")
                addAction("com.proxmark5.client.EXECUTE_CLONE")
                addAction("com.proxmark5.client.WHITE_CLONER_KEY")
                addAction("com.proxmark5.client.MEASURE_ANTENNA")
                addAction("com.proxmark5.client.READ_ANTENNA")
                addAction("com.proxmark5.client.SET_ANTENNA_LEDS")
                addAction("com.proxmark5.client.SET_ANTENNA_FREQ")
                addAction("com.proxmark5.client.SET_ANTENNA_Q")
                addAction("com.proxmark5.client.TOGGLE_CHARGE")
                addAction("com.proxmark5.client.RUN_QC_TEST")
                addAction("com.proxmark5.client.PING")
                addAction("com.proxmark5.client.CONNECT_SPP")
                addAction("com.proxmark5.client.CONNECT_TCP")
                addAction("com.proxmark5.client.DISCONNECT")
                addAction("com.proxmark5.client.RECONNECT_LAST")
                addAction("com.proxmark5.client.READ_RDV4_FLASH")
                addAction("com.proxmark5.client.READ_SMARTCARD_ATR")
            }
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                "android.permission.DUMP",
                null,
                ContextCompat.RECEIVER_EXPORTED
            )
            debugReceiverRegistered = true
        }

        // Set Jetpack Compose Content
        setContent {
            Proxmark5Theme {
                Pm5AppScreen(viewModel = viewModel)
            }
        }

        if (hasRequiredBlePermissions()) {
            autoConnectProxmarkIfBonded()
        }
    }

    private fun hasRequiredBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun autoConnectProxmarkIfBonded() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1000)
            if (viewModel.uiState.value.connectionState is ConnectionState.Disconnected) {
                if (viewModel.reconnectLastDevice()) {
                    return@launch
                }
                viewModel.refreshPairedDevices()
                val pm5Dev = viewModel.uiState.value.pairedDevices.firstOrNull {
                    val n = try { it.name ?: "" } catch (_: SecurityException) { "" }
                    n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
                }
                if (pm5Dev != null) {
                    viewModel.selectDevice(pm5Dev)
                    viewModel.connectSelectedDevice()
                } else {
                    viewModel.startBleScan()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val receiver = debugReceiver
        if (debugReceiverRegistered && receiver != null) {
            try {
                unregisterReceiver(receiver)
            } catch (_: Exception) {}
            debugReceiverRegistered = false
        }
    }
}
