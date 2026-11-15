package com.proxmark5.client

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.protocol.Pm5Constants
import com.proxmark5.client.ui.Pm5AppScreen
import com.proxmark5.client.ui.Pm5ViewModel
import com.proxmark5.client.ui.theme.Proxmark5Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    val viewModel: Pm5ViewModel by viewModels()

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.proxmark5.client.CONNECT_FIRST" -> {
                    Log.i("Pm5Test", "Broadcast: CONNECT_FIRST")
                    lifecycleScope.launch(Dispatchers.IO) {
                        viewModel.refreshPairedDevices()
                        val paired = viewModel.uiState.value.pairedDevices
                        for (d in paired) {
                            Log.i("Pm5Test", "Bonded device: name='${d.name}', addr='${d.address}', type=${d.type}")
                        }
                        val dev = paired.firstOrNull {
                            val n = it.name ?: ""
                            n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
                        }

                        if (dev != null) {
                            Log.i("Pm5Test", "Connecting to ${dev.name} (${dev.address})...")
                            viewModel.selectDevice(dev)
                            viewModel.connectSelectedDevice()
                        } else {
                            Log.w("Pm5Test", "No paired Proxmark5 device found in bonded devices. Starting BLE scan...")
                            viewModel.startBleScan()
                        }
                    }
                }
                "com.proxmark5.client.SCAN_AND_CONNECT" -> {
                    Log.i("Pm5Test", "Broadcast: SCAN_AND_CONNECT")
                    viewModel.startBleScan()
                }
                "com.proxmark5.client.READ_BATTERY" -> {
                    Log.i("Pm5Test", "Broadcast: READ_BATTERY")
                    viewModel.refreshBatteryNow()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val bat = viewModel.pm5Manager.readBattery()
                        Log.i("Pm5Test", "Battery info: SoC=${bat?.socPct}%, Volt=${bat?.voltageMv}mV, Curr=${bat?.currentMa}mA, Temp=${bat?.tempC}C, charging=${bat?.isCharging}")
                    }
                }
                "com.proxmark5.client.SET_RGB" -> {
                    val r = intent.getIntExtra("r", 0)
                    val g = intent.getIntExtra("g", 255)
                    val b = intent.getIntExtra("b", 255)
                    Log.i("Pm5Test", "Broadcast: SET_RGB ($r, $g, $b)")
                    viewModel.setRgbColor(androidx.compose.ui.graphics.Color(r, g, b))
                }
                "com.proxmark5.client.SWITCH_TAB" -> {
                    val tab = intent.getIntExtra("tab", 0)
                    Log.i("Pm5Test", "Switching to tab $tab")
                    viewModel.setSelectedTab(tab)
                }
                "com.proxmark5.client.AUTO_SCAN" -> {
                    Log.i("Pm5Test", "Broadcast: AUTO_SCAN")
                    viewModel.startAutoScan()
                }
                "com.proxmark5.client.STOP_SCAN" -> {
                    Log.i("Pm5Test", "Broadcast: STOP_SCAN")
                    viewModel.stopAutoScan()
                }
                "com.proxmark5.client.TOGGLE_WATCH" -> {
                    val tech = intent.getStringExtra("tech") ?: "HID"
                    Log.i("Pm5Test", "Broadcast: TOGGLE_WATCH ($tech)")
                    viewModel.toggleWatch(tech)
                }
                "com.proxmark5.client.SCAN_HF" -> {
                    Log.i("Pm5Test", "Broadcast: SCAN_HF")
                    viewModel.scanIso14443a()
                }
                "com.proxmark5.client.READ_T55XX_ALL" -> {
                    Log.i("Pm5Test", "Broadcast: READ_T55XX_ALL")
                    viewModel.readAllT55xxBlocks()
                }
                "com.proxmark5.client.EXECUTE_CLONE" -> {
                    val uid = intent.getStringExtra("uid") ?: "0102030405"
                    Log.i("Pm5Test", "Broadcast: EXECUTE_CLONE ($uid)")
                    viewModel.executeClone(uid)
                }
                "com.proxmark5.client.WHITE_CLONER_KEY" -> {
                    val uid = intent.getStringExtra("uid") ?: "0102030405"
                    Log.i("Pm5Test", "Broadcast: WHITE_CLONER_KEY ($uid)")
                    viewModel.calculateWhiteClonerKey(uid)
                }
                "com.proxmark5.client.MEASURE_ANTENNA" -> {
                    Log.i("Pm5Test", "Broadcast: MEASURE_ANTENNA")
                    viewModel.measureAntennaTuning()
                }
                "com.proxmark5.client.READ_ANTENNA" -> {
                    Log.i("Pm5Test", "Broadcast: READ_ANTENNA")
                    viewModel.readAntennaConfig()
                }
                "com.proxmark5.client.SET_ANTENNA_LEDS" -> {
                    val lf = intent.getBooleanExtra("lf", true)
                    val hf = intent.getBooleanExtra("hf", true)
                    Log.i("Pm5Test", "Broadcast: SET_ANTENNA_LEDS (LF=$lf, HF=$hf)")
                    viewModel.setAntennaLeds(lf, hf)
                }
                "com.proxmark5.client.SET_ANTENNA_FREQ" -> {
                    val freq = intent.getStringExtra("freq") ?: "125k"
                    Log.i("Pm5Test", "Broadcast: SET_ANTENNA_FREQ ($freq)")
                    viewModel.setAntennaFreq(freq)
                }
                "com.proxmark5.client.SET_ANTENNA_Q" -> {
                    val highQ = intent.getBooleanExtra("highQ", false)
                    Log.i("Pm5Test", "Broadcast: SET_ANTENNA_Q (highQ=$highQ)")
                    viewModel.setAntennaQ(highQ)
                }
                "com.proxmark5.client.TOGGLE_CHARGE" -> {
                    val enable = intent.getBooleanExtra("enable", true)
                    Log.i("Pm5Test", "Broadcast: TOGGLE_CHARGE ($enable)")
                    viewModel.toggleCharge(enable)
                }
                "com.proxmark5.client.RUN_QC_TEST" -> {
                    Log.i("Pm5Test", "Broadcast: RUN_QC_TEST")
                    viewModel.runHardwareQcTest()
                }
                "com.proxmark5.client.EXPORT_VAULT" -> {
                    val json = viewModel.exportVaultJson()
                    Log.i("Pm5Test", "EXPORT_VAULT: $json")
                }
                "com.proxmark5.client.PING" -> {
                    Log.i("Pm5Test", "Broadcast: PING")
                    viewModel.ping()
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        Log.i("Pm5Client", "Permissions result: allGranted=$allGranted, details=$perms")
        if (allGranted) {
            viewModel.refreshPairedDevices()
            autoConnectProxmarkIfBonded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions
        requestPermissionsIfNeeded()

        // Register test intent filter
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
            addAction("com.proxmark5.client.EXPORT_VAULT")
            addAction("com.proxmark5.client.PING")
        }
        ContextCompat.registerReceiver(
            this,
            testReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Set Jetpack Compose Content
        setContent {
            Proxmark5Theme {
                Pm5AppScreen(viewModel = viewModel)
            }
        }

        autoConnectProxmarkIfBonded()
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
                    val n = it.name ?: ""
                    n.contains("Proxmark", ignoreCase = true) || n.contains("PM5", ignoreCase = true)
                }
                if (pm5Dev != null) {
                    Log.i("Pm5Client", "Auto-connecting to bonded Proxmark5: ${pm5Dev.name} (${pm5Dev.address})")
                    viewModel.selectDevice(pm5Dev)
                    viewModel.connectSelectedDevice()
                } else {
                    Log.i("Pm5Client", "No bonded Proxmark5 found. Starting BLE scan for Proxmark5...")
                    viewModel.startBleScan()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(testReceiver)
        } catch (_: Exception) {}
    }
}
