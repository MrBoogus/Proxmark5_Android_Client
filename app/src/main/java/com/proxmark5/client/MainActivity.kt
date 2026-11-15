package com.proxmark5.client

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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

    private val debugReceiver = object : BroadcastReceiver() {
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
            }
        }
    }

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
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Request runtime permissions
        requestPermissionsIfNeeded()

        // Register non-exported debug receiver in debug builds only
        if (BuildConfig.DEBUG) {
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
            }
            ContextCompat.registerReceiver(
                this,
                debugReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
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
        if (debugReceiverRegistered) {
            try {
                unregisterReceiver(debugReceiver)
            } catch (_: Exception) {}
            debugReceiverRegistered = false
        }
    }
}
