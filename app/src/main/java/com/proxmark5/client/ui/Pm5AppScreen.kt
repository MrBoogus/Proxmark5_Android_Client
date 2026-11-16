package com.proxmark5.client.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxmark5.client.R
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.comm.DiscoveredUsbDevice
import com.proxmark5.client.ui.theme.AccentOrange
import com.proxmark5.client.ui.theme.CyberPurple
import com.proxmark5.client.ui.theme.DarkOutline
import com.proxmark5.client.ui.theme.DarkSurface
import com.proxmark5.client.ui.theme.DarkSurfaceCard
import com.proxmark5.client.ui.theme.DarkSurfaceElevated
import com.proxmark5.client.ui.theme.ElectricCyan
import com.proxmark5.client.ui.theme.ErrorRed
import com.proxmark5.client.ui.theme.MonospaceFamily
import com.proxmark5.client.ui.theme.NeonGreen
import com.proxmark5.client.ui.theme.OledBlack
import com.proxmark5.client.ui.theme.TextMuted
import com.proxmark5.client.ui.theme.TextPrimary
import com.proxmark5.client.ui.theme.TextSecondary
import com.proxmark5.client.ui.theme.WarningAmber
import com.proxmark5.client.protocol.Pm5Constants

private fun copySensitiveToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
    val clip = ClipData.newPlainText(label, value).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(sensitiveKey, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(
            context,
            context.getString(R.string.toast_copied_uid, value),
            Toast.LENGTH_SHORT
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pm5AppScreen(viewModel: Pm5ViewModel) {
    val state by viewModel.uiState.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = OledBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo and Title
                    val isRdv4Active = state.connectionState is ConnectionState.Connected &&
                        state.deviceCapabilities.capabilitiesKnown &&
                        state.deviceCapabilities.isRdv4
                    val isGenericPm3Active = state.connectionState is ConnectionState.Connected &&
                        !state.deviceCapabilities.isPm5 &&
                        !isRdv4Active
                    val brandShortRes = when {
                        isRdv4Active -> R.string.brand_rdv4_short
                        isGenericPm3Active -> R.string.brand_pm3_short
                        else -> R.string.brand_short
                    }
                    val brandTitleRes = when {
                        isRdv4Active -> R.string.brand_rdv4_title
                        isGenericPm3Active -> R.string.brand_pm3_title
                        else -> R.string.brand_title
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(brandShortRes),
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = MonospaceFamily
                            )
                        }
                        Text(
                            text = stringResource(brandTitleRes),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            fontFamily = MonospaceFamily
                        )
                    }

                    // Battery Telemetry Chip
                    val bat = state.batteryInfo
                    if (state.connectionState is ConnectionState.Connected && bat != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (bat.isCharging) NeonGreen.copy(alpha = 0.18f) else DarkSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (bat.isCharging) NeonGreen.copy(alpha = 0.6f) else DarkOutline
                            ),
                            modifier = Modifier.clickable { viewModel.refreshBatteryNow() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (bat.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.Bolt,
                                    contentDescription = stringResource(R.string.cd_battery_status),
                                    tint = if (bat.isCharging) NeonGreen else if (bat.socPct > 20) ElectricCyan else ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.top_battery_chip,
                                        bat.socPct,
                                        bat.voltageFormatted,
                                        bat.currentFormatted
                                    ),
                                    color = if (bat.isCharging) NeonGreen else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = MonospaceFamily
                                )
                            }
                        }
                    }

                    // Connect / Disconnect button
                    when (state.connectionState) {
                        is ConnectionState.Connected -> {
                            OutlinedButton(
                                onClick = { viewModel.disconnect() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(stringResource(R.string.btn_disconnect), fontSize = 11.sp, fontFamily = MonospaceFamily)
                            }
                        }
                        is ConnectionState.Connecting, is ConnectionState.Negotiating -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = WarningAmber,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.status_connecting_short), color = WarningAmber, fontSize = 11.sp, fontFamily = MonospaceFamily)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { showDevicePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OledBlack),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = stringResource(R.string.cd_bluetooth_connect), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_connect), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                            }
                        }
                    }
                }

                // Sub-bar status pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val connStatus = state.connectionState
                    val (statusColor, statusText) = when (connStatus) {
                        is ConnectionState.Connected -> Pair(
                            NeonGreen,
                            stringResource(R.string.status_connected_detail, connStatus.deviceName, connStatus.transport, connStatus.mtu)
                        )
                        is ConnectionState.Connecting -> Pair(
                            WarningAmber,
                            stringResource(R.string.status_connecting_detail, connStatus.deviceName)
                        )
                        is ConnectionState.Negotiating -> Pair(
                            WarningAmber,
                            stringResource(R.string.status_negotiating_detail, connStatus.stage)
                        )
                        is ConnectionState.Scanning -> Pair(
                            ElectricCyan,
                            stringResource(R.string.status_scanning_detail)
                        )
                        is ConnectionState.Error -> Pair(
                            ErrorRed,
                            stringResource(R.string.status_error_detail, connStatus.message)
                        )
                        is ConnectionState.Disconnected -> Pair(
                            TextMuted,
                            stringResource(R.string.status_offline_detail)
                        )
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (state.isBusy) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            color = ElectricCyan,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isFoldableExpanded = configuration.screenWidthDp >= 600
        val useHardwareLabel = state.connectionState is ConnectionState.Connected && !state.deviceCapabilities.isPm5
        val navDestinations = listOf(
            Triple(
                stringResource(if (useHardwareLabel) R.string.tab_hardware_short else R.string.tab_power_short),
                stringResource(if (useHardwareLabel) R.string.tab_hardware_full else R.string.tab_power_full),
                Icons.Filled.Bolt
            ),
            Triple(stringResource(R.string.tab_lf_short), stringResource(R.string.tab_lf_full), Icons.Filled.Sensors),
            Triple(stringResource(R.string.tab_hf_short), stringResource(R.string.tab_hf_full), Icons.Filled.Nfc),
            Triple(stringResource(R.string.tab_t55xx_short), stringResource(R.string.tab_t55xx_full), Icons.Filled.Memory),
            Triple(stringResource(R.string.tab_vault_short), stringResource(R.string.tab_vault_full), Icons.Filled.Key),
            Triple(stringResource(R.string.tab_console_short), stringResource(R.string.tab_console_full), Icons.Filled.Terminal)
        )

        if (isFoldableExpanded) {
            // FOLDABLE UNFOLDED WORKSPACE: NavigationRail + Full-Width Responsive 2-Column Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(OledBlack)
            ) {
                NavigationRail(
                    containerColor = DarkSurface,
                    contentColor = ElectricCyan,
                    modifier = Modifier.width(84.dp)
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    navDestinations.forEachIndexed { idx, (shortLabel, _, icon) ->
                        val selected = state.selectedTab == idx
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.setSelectedTab(idx) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = shortLabel,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = shortLabel,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = MonospaceFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = OledBlack,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Compact live transport/battery pill inside NavigationRail footer
                    val isConnected = state.connectionState is ConnectionState.Connected
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isConnected) NeonGreen.copy(alpha = 0.14f) else DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isConnected) NeonGreen.copy(alpha = 0.5f) else DarkOutline
                        ),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isConnected) NeonGreen else TextMuted,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(if (isConnected) R.string.rail_link_ok else R.string.rail_link_offline),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) NeonGreen else TextMuted,
                                fontFamily = MonospaceFamily,
                                maxLines = 1
                            )
                        }
                    }
                }

                VerticalDivider(color = DarkOutline, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(OledBlack)
                ) {
                    AdaptiveStatusBanner(state.statusBanner)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        when (state.selectedTab) {
                            0 -> DashboardTab(state, viewModel, isExpanded = true)
                            1 -> LfBadgeStudioTab(state, viewModel, isExpanded = true)
                            2 -> HfNfcStudioTab(state, viewModel, isExpanded = true)
                            3 -> T55xxSuiteTab(state, viewModel, isExpanded = true)
                            4 -> TagVaultTab(state, viewModel, isExpanded = true)
                            5 -> ConsoleTerminalTab(state, viewModel, isExpanded = true)
                        }
                    }
                }
            }
        } else {
            // COMPACT SINGLE-PANE WORKSPACE (Cover Screen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(OledBlack)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = state.selectedTab,
                    containerColor = DarkSurfaceCard,
                    contentColor = ElectricCyan,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                            color = ElectricCyan,
                            height = 3.dp
                        )
                    }
                ) {
                    navDestinations.forEachIndexed { idx, (_, label, _) ->
                        Tab(
                            selected = state.selectedTab == idx,
                            onClick = { viewModel.setSelectedTab(idx) },
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (state.selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                    color = if (state.selectedTab == idx) ElectricCyan else TextSecondary,
                                    fontFamily = MonospaceFamily
                                )
                            }
                        )
                    }
                }

                AdaptiveStatusBanner(state.statusBanner)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    when (state.selectedTab) {
                        0 -> DashboardTab(state, viewModel, isExpanded = false)
                        1 -> LfBadgeStudioTab(state, viewModel, isExpanded = false)
                        2 -> HfNfcStudioTab(state, viewModel, isExpanded = false)
                        3 -> T55xxSuiteTab(state, viewModel, isExpanded = false)
                        4 -> TagVaultTab(state, viewModel, isExpanded = false)
                        5 -> ConsoleTerminalTab(state, viewModel, isExpanded = false)
                    }
                }
            }
        }
    }

    if (showDevicePicker) {
        LaunchedEffect(Unit) {
            viewModel.refreshPairedDevices()
            viewModel.refreshUsbDevices()
        }
        DevicePickerDialog(
            state = state,
            onDismiss = { showDevicePicker = false },
            onReconnectLast = {
                viewModel.reconnectLastDevice()
                showDevicePicker = false
            },
            onSelectDevice = { dev ->
                viewModel.selectDevice(dev)
                viewModel.connectSelectedDevice()
                showDevicePicker = false
            },
            onSelectClassicBt = { dev ->
                viewModel.connectClassicBtDevice(dev)
                showDevicePicker = false
            },
            onSelectUsb = { usb ->
                viewModel.connectUsbDevice(usb)
                showDevicePicker = false
            },
            onConnectTcp = { host, port ->
                viewModel.setTcpHost(host)
                viewModel.setTcpPort(port)
                viewModel.connectTcp(host, port)
                showDevicePicker = false
            },
            onStartScan = { viewModel.startBleScan() },
            onStopScan = { viewModel.stopBleScan() },
            onRefreshPaired = {
                viewModel.refreshPairedDevices()
                viewModel.refreshUsbDevices()
            }
        )
    }
}

@Composable
private fun AdaptiveStatusBanner(banner: String?) {
    AnimatedVisibility(visible = banner != null) {
        Surface(
            color = DarkSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = banner ?: "",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFamily,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AdaptiveTwoColumnPane(
    isExpanded: Boolean,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    if (isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                leftContent()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rightContent()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leftContent()
            rightContent()
        }
    }
}

// ==========================================
// TAB 0: DASHBOARD & HARDWARE COMMAND CENTER
// ==========================================

@Composable
fun DashboardTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    val connected = state.connectionState is ConnectionState.Connected
    val showPm5Cards = !connected || (state.deviceCapabilities.capabilitiesKnown && state.deviceCapabilities.isPm5)
    val showRdv4Peripherals = connected && state.deviceCapabilities.capabilitiesKnown &&
        (state.deviceCapabilities.hwAvailableFlash || state.deviceCapabilities.hwAvailableSmartcard)

    AdaptiveTwoColumnPane(
        isExpanded = isExpanded,
        leftContent = {
            DeviceIdentityAndLinkCard(state)
            if (showPm5Cards) {
                BatteryCard(state, viewModel)
                MainboardRgbCard(state, viewModel)
                HardwareQcCard(state, viewModel)
            }
            if (showRdv4Peripherals) {
                Rdv4PeripheralsCard(state, viewModel)
            }
        },
        rightContent = {
            if (showPm5Cards) {
                AntennaControllerCard(state, viewModel)
            }
            AntennaResonanceCard(state, viewModel)
            if (showPm5Cards) {
                ArchitectureCard(state)
            }
        }
    )
}

@Composable
fun DashboardHardwarePane(state: Pm5UiState, viewModel: Pm5ViewModel, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val connected = state.connectionState is ConnectionState.Connected
    val showPm5Cards = !connected || (state.deviceCapabilities.capabilitiesKnown && state.deviceCapabilities.isPm5)
    val showRdv4Peripherals = connected && state.deviceCapabilities.capabilitiesKnown &&
        (state.deviceCapabilities.hwAvailableFlash || state.deviceCapabilities.hwAvailableSmartcard)

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DeviceIdentityAndLinkCard(state)

        if (showPm5Cards) {
            BatteryCard(state, viewModel)
            AntennaControllerCard(state, viewModel)
            MainboardRgbCard(state, viewModel)
        }

        if (showRdv4Peripherals) {
            Rdv4PeripheralsCard(state, viewModel)
        }

        AntennaResonanceCard(state, viewModel)

        if (showPm5Cards) {
            HardwareQcCard(state, viewModel)
            ArchitectureCard(state)
        }
    }
}

@Composable
fun DeviceIdentityAndLinkCard(state: Pm5UiState) {
    val conn = state.connectionState as? ConnectionState.Connected
    val cap = state.deviceCapabilities
    val ver = state.versionInfo
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = stringResource(R.string.cd_device_identity),
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.title_device_identity),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontFamily = MonospaceFamily
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Model", cap.modelLabel)
                MetricItem("Transport", conn?.transport ?: "Offline")
                MetricItem(
                    "Capabilities",
                    if (cap.capabilitiesKnown) "v${cap.version}" else if (connectedOrNull(conn)) "Fallback" else "—"
                )
            }
            if (cap.capabilitiesKnown) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("BigBuf", "${cap.bigbufSize} B")
                    MetricItem("Max Frame", "${cap.maxCmdDataSize} B")
                    MetricItem("Baud", "${cap.baudrate}")
                }
            }
            if (ver != null && ver.versionString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_firmware_version, ver.versionString),
                    fontSize = 11.sp,
                    color = NeonGreen,
                    lineHeight = 15.sp,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

private fun connectedOrNull(conn: ConnectionState.Connected?): Boolean = conn != null

@Composable
fun Rdv4PeripheralsCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    val cap = state.deviceCapabilities
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = stringResource(R.string.cd_rdv4_peripherals),
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.title_rdv4_peripherals),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontFamily = MonospaceFamily
                )
            }
            Text(
                text = stringResource(R.string.desc_rdv4_peripherals),
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = MonospaceFamily
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cap.hwAvailableFlash) {
                    OutlinedButton(
                        onClick = { viewModel.readRdv4FlashInfo() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                    ) {
                        Text(stringResource(R.string.btn_read_flash_info), fontSize = 11.sp, fontFamily = MonospaceFamily)
                    }
                }
                if (cap.hwAvailableSmartcard) {
                    OutlinedButton(
                        onClick = { viewModel.readSmartcardAtr() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                    ) {
                        Text(stringResource(R.string.btn_read_smart_atr), fontSize = 11.sp, fontFamily = MonospaceFamily)
                    }
                }
            }
            state.rdv4FlashInfo?.let { flash ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Flash: ${flash.manufacturerName} (0x%02X / dev 0x%02X, JEDEC 0x%04X, ${flash.totalBytes / 1024} KB)"
                        .format(flash.manufacturerId, flash.deviceId, flash.jedecId),
                    fontSize = 11.sp,
                    color = NeonGreen,
                    fontFamily = MonospaceFamily
                )
            }
            state.smartcardAtrInfo?.let { atr ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (atr.cardPresent) "Smartcard ATR: ${atr.atrHex}" else "Smartcard: ${atr.message}",
                    fontSize = 11.sp,
                    color = if (atr.cardPresent) NeonGreen else TextSecondary,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

@Composable
fun BatteryCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    val bat = state.batteryInfo
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (bat?.isCharging == true) NeonGreen.copy(alpha = 0.5f) else DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = stringResource(R.string.cd_battery_gauge),
                        tint = if (bat?.isCharging == true) NeonGreen else ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.title_battery_gauge),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                }

                TextButton(
                    onClick = { viewModel.refreshBatteryNow() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_battery), tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_refresh), fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (bat != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.percent_fmt, bat.socPct),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bat.socPct > 20) NeonGreen else ErrorRed,
                        fontFamily = MonospaceFamily
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = bat.chargeStatusLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (bat.isCharging) NeonGreen else AccentOrange,
                            fontFamily = MonospaceFamily
                        )
                        Text(
                            text = stringResource(R.string.label_current, bat.currentFormatted),
                            fontSize = 12.sp,
                            color = if (bat.currentMa > 0) NeonGreen else AccentOrange,
                            fontFamily = MonospaceFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (bat.socPct.coerceIn(0, 100) / 100.0f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (bat.isCharging) NeonGreen else ElectricCyan,
                    trackColor = DarkSurfaceElevated
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem(stringResource(R.string.metric_voltage), bat.voltageFormatted)
                    MetricItem(stringResource(R.string.metric_temp), bat.tempFormatted)
                    MetricItem(stringResource(R.string.metric_capacity), stringResource(R.string.capacity_mah_fmt, bat.remainingMah, bat.fullChargeMah))
                    MetricItem(stringResource(R.string.metric_health), stringResource(R.string.percent_fmt, bat.healthPct))
                }
            } else {
                Text(
                    text = stringResource(
                        if (state.connectionState is ConnectionState.Connected) {
                            R.string.awaiting_battery_telemetry
                        } else {
                            R.string.connect_battery_prompt
                        }
                    ),
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = MonospaceFamily
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkOutline)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.toggleCharge(true) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.btn_charge_on), fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.toggleCharge(false) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.btn_charge_off), fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.setChargeVoltage(4100) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Text(stringResource(R.string.btn_charge_safe_41v), fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
                }
            }
        }
    }
}

@Composable
fun AntennaControllerCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    val cfg = state.antennaConfig
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (cfg?.lfLed == true || cfg?.hfLed == true) ElectricCyan.copy(alpha = 0.8f) else DarkOutline
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SettingsInputAntenna,
                        contentDescription = stringResource(R.string.cd_antenna_controller),
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.title_antenna_controller),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                }

                TextButton(
                    onClick = { viewModel.readAntennaConfig() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (state.isReadingAntenna) {
                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_read_antenna), tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_read), fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                }
            }

            Text(
                text = stringResource(R.string.desc_antenna_controller),
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = MonospaceFamily
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Physical Antenna Board Blue LEDs Control
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.title_antenna_leds),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LF Blue LED Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = cfg?.lfLed == true,
                                onCheckedChange = { viewModel.toggleAntennaLed(isLf = true, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ElectricCyan,
                                    checkedTrackColor = ElectricCyan.copy(alpha = 0.35f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurface
                                ),
                                modifier = Modifier.scale(0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.label_lf_blue_led),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cfg?.lfLed == true) ElectricCyan else TextSecondary,
                                fontFamily = MonospaceFamily
                            )
                        }

                        // HF Blue LED Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = cfg?.hfLed == true,
                                onCheckedChange = { viewModel.toggleAntennaLed(isLf = false, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ElectricCyan,
                                    checkedTrackColor = ElectricCyan.copy(alpha = 0.35f),
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurface
                                ),
                                modifier = Modifier.scale(0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.label_hf_blue_led),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cfg?.hfLed == true) ElectricCyan else TextSecondary,
                                fontFamily = MonospaceFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Toggle Both Button
                    val bothOn = cfg?.lfLed == true && cfg.hfLed
                    OutlinedButton(
                        onClick = { viewModel.setAntennaLeds(!bothOn, !bothOn) },
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (bothOn) ElectricCyan else DarkOutline),
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(if (bothOn) R.string.btn_leds_off else R.string.btn_leds_on),
                            fontSize = 10.sp,
                            color = if (bothOn) ElectricCyan else TextPrimary,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Antenna Operating Frequency & Q-Factor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_freq_select),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontFamily = MonospaceFamily
                )
                // Q-Factor Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(if (cfg?.highQ == true) R.string.label_high_q else R.string.label_low_q),
                        fontSize = 11.sp,
                        color = if (cfg?.highQ == true) WarningAmber else TextMuted,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = cfg?.highQ == true,
                        onCheckedChange = { viewModel.setAntennaQ(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = WarningAmber,
                            checkedTrackColor = WarningAmber.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val freqs = listOf(
                Pair(stringResource(R.string.freq_125k), "125k"),
                Pair(stringResource(R.string.freq_134k), "134k"),
                Pair(stringResource(R.string.freq_250k), "250k"),
                Pair(stringResource(R.string.freq_375k), "375k"),
                Pair(stringResource(R.string.freq_500k), "500k")
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                freqs.forEach { (label, key) ->
                    val isSelected = when (key) {
                        "125k" -> cfg?.freq125k == true
                        "134k" -> cfg?.freq134k == true
                        "250k" -> cfg?.freq250k == true
                        "375k" -> cfg?.freq375k == true
                        "500k" -> cfg?.freq500k == true
                        else -> false
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else DarkOutline
                        ),
                        modifier = Modifier
                            .clickable { viewModel.setAntennaFreq(key) }
                            .padding(2.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) ElectricCyan else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Register Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.label_reg_02_fmt, cfg?.rawByte ?: 0),
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = MonospaceFamily
                )
                Text(
                    text = stringResource(
                        R.string.label_active_freq_q,
                        cfg?.activeFreqString ?: stringResource(R.string.label_none),
                        stringResource(if (cfg?.highQ == true) R.string.label_q_short_high else R.string.label_q_short_low)
                    ),
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainboardRgbCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, contentDescription = stringResource(R.string.cd_rgb_led), tint = state.selectedRgbColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.title_antenna_rgb),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontFamily = MonospaceFamily
                )
            }
            Text(
                text = stringResource(R.string.desc_antenna_rgb),
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = MonospaceFamily
            )
            Spacer(modifier = Modifier.height(10.dp))

            val colors = listOf(
                Pair(stringResource(R.string.color_cyan), ElectricCyan),
                Pair(stringResource(R.string.color_green), NeonGreen),
                Pair(stringResource(R.string.color_purple), CyberPurple),
                Pair(stringResource(R.string.color_amber), WarningAmber),
                Pair(stringResource(R.string.color_red), ErrorRed),
                Pair(stringResource(R.string.color_white), Color.White),
                Pair(stringResource(R.string.color_off), Color.Black)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { (name, col) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (col == Color.Black) DarkSurfaceElevated else col.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.selectedRgbColor == col) ElectricCyan else col.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier
                            .clickable { viewModel.setRgbColor(col) }
                            .padding(2.dp)
                    ) {
                        Text(
                            text = name,
                            color = if (col == Color.Black) TextPrimary else col,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AntennaResonanceCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.cd_antenna_tune), tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.title_antenna_resonance),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                }

                Button(
                    onClick = { viewModel.measureAntennaTuning() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OledBlack),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(stringResource(R.string.btn_measure_sweep), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val tuning = state.antennaTuning
            if (tuning != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem(stringResource(R.string.freq_125k), stringResource(R.string.voltage_v_fmt, tuning.lf125V))
                    MetricItem(stringResource(R.string.freq_134k), stringResource(R.string.voltage_v_fmt, tuning.lf134V))
                    MetricItem("HF 13.56 MHz", stringResource(R.string.voltage_v_fmt, tuning.hfV))
                    MetricItem(stringResource(R.string.metric_peak_divisor), "${tuning.peakDivisor}")
                }
            } else {
                Text(
                    text = stringResource(if (state.isTuningAntenna) R.string.sweeping_frequencies else R.string.no_tuning_sweep),
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

@Composable
fun HardwareQcCard(state: Pm5UiState, viewModel: Pm5ViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = stringResource(R.string.cd_hardware_qc),
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.title_hardware_qc),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                }

                Button(
                    onClick = { viewModel.runHardwareQcTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = OledBlack),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    enabled = !state.isRunningQcTest
                ) {
                    if (state.isRunningQcTest) {
                        CircularProgressIndicator(color = OledBlack, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.btn_run_self_test), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val qc = state.qcTestResult
            if (qc != null) {
                val passed = qc.success || qc.failedItem == 2
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (passed) NeonGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (passed) NeonGreen.copy(alpha = 0.6f) else ErrorRed.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (passed) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = stringResource(if (passed) R.string.cd_diagnostic_passed else R.string.cd_diagnostic_failed),
                                tint = if (passed) NeonGreen else ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(if (passed) R.string.qc_passed else R.string.qc_failed),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (passed) NeonGreen else ErrorRed,
                                fontFamily = MonospaceFamily
                            )
                        }
                        Text(
                            text = qc.message,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.desc_hardware_qc),
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

@Composable
fun ArchitectureCard(state: Pm5UiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.title_architecture),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.desc_architecture),
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                fontFamily = MonospaceFamily
            )

            val ver = state.versionInfo
            if (ver != null && ver.versionString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.label_firmware_version, ver.versionString),
                    fontSize = 11.sp,
                    color = NeonGreen,
                    fontFamily = MonospaceFamily
                )
            }
        }
    }
}

// ==========================================
// TAB 1: LF BADGE STUDIO (125 kHz)
// ==========================================

@Composable
fun LfBadgeStudioTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    val context = LocalContext.current

    AdaptiveTwoColumnPane(
        isExpanded = isExpanded,
        leftContent = {
            // Auto-Scan & Live Demodulator Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (state.isAutoScanning) NeonGreen.copy(alpha = 0.6f) else DarkOutline
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.title_auto_demod),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = stringResource(R.string.desc_auto_demod),
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = MonospaceFamily
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (state.isAutoScanning) viewModel.stopAutoScan() else viewModel.startAutoScan()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isAutoScanning) ErrorRed else NeonGreen,
                                contentColor = OledBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                if (state.isAutoScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_start_scan),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(if (state.isAutoScanning) R.string.btn_stop_scan else R.string.btn_start_auto_scan),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MonospaceFamily
                            )
                        }
                    }

                    // Dedicated Watch Mode Pills
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WatchFilterChip(stringResource(R.string.watch_hid), state.isWatchingHid) { viewModel.toggleWatch("HID") }
                        WatchFilterChip(stringResource(R.string.watch_em4100), state.isWatchingEm4100) { viewModel.toggleWatch("EM4100") }
                        WatchFilterChip(stringResource(R.string.watch_awid), state.isWatchingAwid) { viewModel.toggleWatch("AWID") }
                        WatchFilterChip(stringResource(R.string.watch_io), state.isWatchingIo) { viewModel.toggleWatch("IO") }
                    }
                }
            }

            // Decoded Badge Result Card
            val badge = state.decodedBadge
            if (badge != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = badge.technology.uppercase(),
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = MonospaceFamily
                            )
                            val sensitiveLabel = stringResource(R.string.label_sensitive_uid)
                            IconButton(
                                onClick = {
                                    copySensitiveToClipboard(context, sensitiveLabel, badge.uidHex)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_copy_uid), tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = badge.uidHex,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MetricItem(stringResource(R.string.metric_card_number), badge.cardId)
                            MetricItem(stringResource(R.string.metric_facility_code), badge.facilityCode ?: stringResource(R.string.label_dash))
                            MetricItem(stringResource(R.string.metric_format), badge.formatLen ?: stringResource(R.string.label_standard))
                        }

                        // Clone Quick Action
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.executeClone(badge.uidHex) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_clone_tag), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_clone_to_t5577), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
                        }
                    }
                }
            }

            // EM4100 64-bit Frame Studio
            val em = state.lastEm4100Tag
            if (em != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.title_em4100_frame),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElectricCyan,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.label_standard_fmt, em.formattedPm3),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 64-bit binary view
                        Surface(
                            color = OledBlack,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = em.parity.raw64Bits.chunked(8).joinToString("\n"),
                                color = NeonGreen,
                                fontSize = 12.sp,
                                fontFamily = MonospaceFamily,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.label_parity_valid),
                            fontSize = 11.sp,
                            color = NeonGreen,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        },
        rightContent = {
            // Manual Clone Station
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                var inputUid by remember { mutableStateOf("") }
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.title_manual_clone),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputUid,
                        onValueChange = { inputUid = it.filter { c -> c.isLetterOrDigit() }.take(10).uppercase() },
                        label = { Text(stringResource(R.string.hint_uid_10hex), fontFamily = MonospaceFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TargetChipButton(stringResource(R.string.chip_t5577), state.cloneTargetChip == Pm5Constants.TARGET_T55X7) {
                            viewModel.setCloneTargetChip(Pm5Constants.TARGET_T55X7)
                        }
                        TargetChipButton(stringResource(R.string.chip_q5), state.cloneTargetChip == Pm5Constants.TARGET_Q5) {
                            viewModel.setCloneTargetChip(Pm5Constants.TARGET_Q5)
                        }
                        TargetChipButton(stringResource(R.string.chip_em4305), state.cloneTargetChip == Pm5Constants.TARGET_EM4305) {
                            viewModel.setCloneTargetChip(Pm5Constants.TARGET_EM4305)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.executeClone(inputUid) },
                        enabled = inputUid.length >= 8,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OledBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_write_clone), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
                    }

                    if (state.cloneStatus != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.cloneStatus,
                            color = if (state.cloneStatus.contains("succeeded", ignoreCase = true)) NeonGreen else ErrorRed,
                            fontSize = 11.sp,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        }
    )
}

// ==========================================
// TAB 2: HF NFC STUDIO (13.56 MHz)
// ==========================================

@Composable
fun HfNfcStudioTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    val context = LocalContext.current

    AdaptiveTwoColumnPane(
        isExpanded = isExpanded,
        leftContent = {
            // ISO 14443-A Scanner Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.title_iso14443a),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = stringResource(R.string.desc_iso14443a),
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = MonospaceFamily
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.scanIso14443a() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Sensors, contentDescription = stringResource(R.string.cd_scan_hf), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_read_14443a), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                        }
                    }

                    if (state.hfStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.hfStatus,
                            color = if (state.isScanningHf) WarningAmber else NeonGreen,
                            fontSize = 12.sp,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        },
        rightContent = {
            // Detected HF Card View
            val hfCard = state.lastHfCard
            if (hfCard != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = hfCard.cardType,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = MonospaceFamily
                            )
                            val sensitiveLabel = stringResource(R.string.label_sensitive_uid)
                            IconButton(
                                onClick = {
                                    copySensitiveToClipboard(context, sensitiveLabel, hfCard.uidHex)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_copy_uid), tint = ElectricCyan, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = hfCard.uidHex,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MetricItem(stringResource(R.string.metric_uid_length), stringResource(R.string.uid_bytes_fmt, hfCard.uidLen))
                            MetricItem(stringResource(R.string.metric_atqa), stringResource(R.string.hex_prefix_fmt, hfCard.atqaHex))
                            MetricItem(stringResource(R.string.metric_sak), stringResource(R.string.hex_prefix_fmt, hfCard.sakHex))
                        }

                        if (hfCard.atsHex != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MetricItem(stringResource(R.string.metric_ats), hfCard.atsHex)
                        }
                    }
                }
            } else if (isExpanded) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.title_hf_inspector_ready),
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.desc_hf_inspector_ready),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        }
    )
}

// ==========================================
// TAB 3: T55XX TRANSPONDER SUITE
// ==========================================

@Composable
fun T55xxSuiteTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    AdaptiveTwoColumnPane(
        isExpanded = isExpanded,
        leftContent = {
            // Block Map Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.title_t55xx_map),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = stringResource(R.string.desc_t55xx_map),
                                fontSize = 11.sp,
                                color = NeonGreen,
                                fontFamily = MonospaceFamily
                            )
                        }

                        Button(
                            onClick = { viewModel.readAllT55xxBlocks() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = OledBlack),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(stringResource(R.string.btn_read_all_blocks), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                        }
                    }

                    // Block 0 Configuration Decoder
                    val b0 = state.t55xxBlocks[0]
                    if (b0?.configDescription != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.label_config_b0_fmt, b0.configDescription),
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontFamily = MonospaceFamily,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Grid of 8 blocks
                    Spacer(modifier = Modifier.height(10.dp))
                    val dash = stringResource(R.string.label_dash)
                    for (b in 0..7) {
                        val entry = state.t55xxBlocks[b]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(if (state.selectedT55xxBlock == b) DarkSurfaceElevated else Color.Transparent)
                                .clickable { viewModel.selectT55xxBlock(b) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.label_blk_fmt, b, entry?.hexString ?: dash),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (entry?.value != null) TextPrimary else TextMuted,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = entry?.asciiString ?: dash,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = MonospaceFamily
                            )
                            OutlinedButton(
                                onClick = { viewModel.readT55xxBlock(b) },
                                modifier = Modifier.height(24.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                            ) {
                                Text(stringResource(R.string.btn_read), fontSize = 10.sp, fontFamily = MonospaceFamily)
                            }
                        }
                    }
                }
            }
        },
        rightContent = {
            // Block Write & Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.title_write_block_fmt, state.selectedT55xxBlock),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.writeBlockDataHex,
                        onValueChange = { viewModel.setWriteBlockDataHex(it) },
                        label = { Text(stringResource(R.string.hint_block_data_hex), fontFamily = MonospaceFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = DarkOutline,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedTextField(
                            value = state.t55xxPasswordHex,
                            onValueChange = { viewModel.setT55xxPasswordHex(it) },
                            label = { Text(stringResource(R.string.hint_password_hex), fontFamily = MonospaceFamily) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = DarkOutline,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.writeT55xxBlock() },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text(stringResource(R.string.btn_write), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
                        }
                    }

                    if (state.writeStatus != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.writeStatus,
                            color = if (state.writeStatus.contains("success", ignoreCase = true)) NeonGreen else ErrorRed,
                            fontSize = 11.sp,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }

            // Password Recovery & Audit Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.title_password_audit),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.runPasswordDictionary() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isCheckingPwds) ErrorRed else CyberPurple,
                                contentColor = OledBlack
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(if (state.isCheckingPwds) R.string.btn_cancel_audit else R.string.btn_dictionary_audit),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = MonospaceFamily
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val uid = state.decodedBadge?.uidHex ?: "0102030405"
                                viewModel.calculateWhiteClonerKey(uid)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.btn_white_cloner_key),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = MonospaceFamily
                            )
                        }
                    }

                    if (state.chkPwdStatus != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.chkPwdStatus,
                            color = if (state.foundPassword != null) NeonGreen else TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = MonospaceFamily
                        )
                    }
                }
            }
        }
    )
}

// ==========================================
// TAB 4: TAG VAULT & WALLET
// ==========================================

@Composable
fun TagVaultTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    val context = LocalContext.current
    val exportFilename = stringResource(R.string.default_vault_export_filename)
    val exportVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportVaultToUri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_saved_tags_fmt, state.savedTags.size),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily
            )

            Row {
                TextButton(
                    onClick = { exportVaultLauncher.launch(exportFilename) }
                ) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_export_vault), modifier = Modifier.size(14.dp), tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_export_json), fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                }
            }
        }

        if (state.vaultError != null) {
            Surface(
                color = ErrorRed.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = state.vaultError,
                    color = ErrorRed,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFamily,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (state.savedTags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.vault_empty_message),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = MonospaceFamily,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val dash = stringResource(R.string.label_dash)
            val sensitiveLabel = stringResource(R.string.label_sensitive_uid)
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isExpanded) 2 else 1),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItems(state.savedTags, key = { it.id }) { tag ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (tag.frequency == "LF") NeonGreen.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = tag.frequency,
                                            color = if (tag.frequency == "LF") NeonGreen else ElectricCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = MonospaceFamily,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tag.technology,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        fontFamily = MonospaceFamily
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            copySensitiveToClipboard(context, sensitiveLabel, tag.uidHex)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.cd_copy_uid), tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteTag(tag.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_tag), tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tag.uidHex,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )

                            if (tag.cardId.isNotEmpty() || tag.facilityCode != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.vault_tag_detail_fmt,
                                        tag.cardId,
                                        tag.facilityCode ?: dash,
                                        tag.formatDescription ?: ""
                                    ),
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontFamily = MonospaceFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 5: CONSOLE & PACKET TERMINAL
// ==========================================

@Composable
fun ConsoleTerminalTab(state: Pm5UiState, viewModel: Pm5ViewModel, isExpanded: Boolean = false) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_comm_terminal),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily,
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isExpanded) {
                    OutlinedButton(
                        onClick = { viewModel.refreshBatteryNow() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_batt), fontSize = 10.sp, fontFamily = MonospaceFamily)
                    }
                    OutlinedButton(
                        onClick = { viewModel.readAntennaConfig() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(stringResource(R.string.btn_ant_cfg), fontSize = 10.sp, fontFamily = MonospaceFamily)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.ping() },
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text(stringResource(R.string.btn_ping), fontSize = 10.sp, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.breakLoop() },
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text(stringResource(R.string.btn_break), fontSize = 10.sp, fontFamily = MonospaceFamily)
                }
            }
        }

        // Live Log Output
        Surface(
            color = OledBlack,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline),
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                reverseLayout = true
            ) {
                items(state.logs.reversed()) { line ->
                    val color = when {
                        line.startsWith("[BLE") || line.startsWith("[COMM") -> ElectricCyan
                        line.startsWith("[WiFi") -> NeonGreen
                        line.contains("ERR", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> ErrorRed
                        line.contains("TAG ID", ignoreCase = true) || line.contains("AWID Found", ignoreCase = true) -> NeonGreen
                        else -> TextSecondary
                    }
                    Text(
                        text = line,
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// REUSABLE COMPONENTS
// ==========================================

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMuted, fontFamily = MonospaceFamily)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFamily = MonospaceFamily)
    }
}

@Composable
fun WatchFilterChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) NeonGreen.copy(alpha = 0.2f) else DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) NeonGreen else DarkOutline),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.cd_selected), tint = NeonGreen, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = if (selected) NeonGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = MonospaceFamily
            )
        }
    }
}

@Composable
fun TargetChipButton(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) ElectricCyan.copy(alpha = 0.25f) else DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ElectricCyan else DarkOutline),
        modifier = Modifier.clickable { onSelect() }
    ) {
        Text(
            text = label,
            color = if (selected) ElectricCyan else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontFamily = MonospaceFamily
        )
    }
}

// ==========================================
// DEVICE PICKER DIALOG (BLE, BT SPP, USB & TCP)
// ==========================================

@Composable
fun DevicePickerDialog(
    state: Pm5UiState,
    onDismiss: () -> Unit,
    onReconnectLast: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onSelectClassicBt: (BluetoothDevice) -> Unit,
    onSelectUsb: (DiscoveredUsbDevice) -> Unit,
    onConnectTcp: (String, String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRefreshPaired: () -> Unit
) {
    var tcpHost by remember { mutableStateOf(state.tcpHost) }
    var tcpPort by remember { mutableStateOf(state.tcpPort) }
    val scrollState = rememberScrollState()
    val unknownLabel = stringResource(R.string.label_unknown_device)

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.title_connect_pm5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_dialog), tint = TextMuted)
                    }
                }

                // Quick Reconnect Last Device
                state.lastConnectedDevice?.let { saved ->
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onReconnectLast,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.btn_reconnect_last),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = saved.displayLabel,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = MonospaceFamily
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BLE Scan Controls (PM5 BWM)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.label_ble_devices), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricCyan, fontFamily = MonospaceFamily)
                    Row {
                        TextButton(onClick = { if (state.connectionState is ConnectionState.Scanning) onStopScan() else onStartScan() }) {
                            Text(
                                text = stringResource(if (state.connectionState is ConnectionState.Scanning) R.string.btn_stop else R.string.btn_scan),
                                fontSize = 11.sp,
                                color = ElectricCyan,
                                fontFamily = MonospaceFamily
                            )
                        }
                        TextButton(onClick = onRefreshPaired) {
                            Text(stringResource(R.string.btn_paired), fontSize = 11.sp, color = TextSecondary, fontFamily = MonospaceFamily)
                        }
                    }
                }

                val bleDevices = (state.discoveredDevices.map { it.device } + state.pairedDevices.filter { dev ->
                    val t = try { dev.type } catch (_: SecurityException) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }
                    t != BluetoothDevice.DEVICE_TYPE_CLASSIC
                }).distinctBy { it.address }

                if (bleDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_pm5_found_yet),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    bleDevices.forEach { dev ->
                        @SuppressLint("MissingPermission")
                        val devName = dev.name ?: unknownLabel
                        val isPm5 = devName.contains("Proxmark", ignoreCase = true) || devName.contains("PM5", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDevice(dev) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPm5) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                                    contentDescription = stringResource(if (isPm5) R.string.cd_device_proxmark else R.string.cd_device_bluetooth),
                                    tint = if (isPm5) NeonGreen else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = devName,
                                        fontWeight = if (isPm5) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPm5) NeonGreen else TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = MonospaceFamily
                                    )
                                    Text(text = dev.address, fontSize = 10.sp, color = TextMuted, fontFamily = MonospaceFamily)
                                }
                            }
                            Text(stringResource(R.string.btn_connect), fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                        }
                        HorizontalDivider(color = DarkOutline.copy(alpha = 0.5f))
                    }
                }

                // Classic BT SPP (RDV4 Blueshark / HC-06)
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.label_classic_bt_spp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = MonospaceFamily)
                Spacer(modifier = Modifier.height(4.dp))
                if (state.classicBtDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_classic_bt_found),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    state.classicBtDevices.forEach { dev ->
                        @SuppressLint("MissingPermission")
                        val devName = dev.name ?: unknownLabel
                        val isPm3 = devName.contains("PM3", ignoreCase = true) ||
                            devName.contains("RDV4", ignoreCase = true) ||
                            devName.contains("Blueshark", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectClassicBt(dev) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = stringResource(R.string.cd_device_bluetooth),
                                    tint = if (isPm3) NeonGreen else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = devName,
                                        fontWeight = if (isPm3) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isPm3) NeonGreen else TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = MonospaceFamily
                                    )
                                    Text(text = dev.address, fontSize = 10.sp, color = TextMuted, fontFamily = MonospaceFamily)
                                }
                            }
                            Text(stringResource(R.string.btn_connect), fontSize = 11.sp, color = NeonGreen, fontFamily = MonospaceFamily)
                        }
                        HorizontalDivider(color = DarkOutline.copy(alpha = 0.5f))
                    }
                }

                // USB OTG Serial (PM3 CDC)
                Spacer(modifier = Modifier.height(10.dp))
                Text(stringResource(R.string.label_usb_otg), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricCyan, fontFamily = MonospaceFamily)
                Spacer(modifier = Modifier.height(4.dp))
                if (state.usbDevices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_usb_device_found),
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    state.usbDevices.forEach { usb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectUsb(usb) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Usb,
                                    contentDescription = stringResource(R.string.label_usb_otg),
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(text = usb.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 12.sp, fontFamily = MonospaceFamily)
                                    Text(text = "%04X:%04X %s".format(usb.vendorId, usb.productId, usb.devicePath), fontSize = 10.sp, color = TextMuted, fontFamily = MonospaceFamily)
                                }
                            }
                            Text(stringResource(R.string.btn_connect), fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                        }
                        HorizontalDivider(color = DarkOutline.copy(alpha = 0.5f))
                    }
                }

                // Alternative: WiFi TCP
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkOutline)
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.label_wifi_tcp_alt), fontSize = 11.sp, color = TextSecondary, fontFamily = MonospaceFamily)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = tcpHost,
                        onValueChange = { tcpHost = it },
                        label = { Text(stringResource(R.string.hint_ip), fontSize = 10.sp, fontFamily = MonospaceFamily) },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = tcpPort,
                        onValueChange = { tcpPort = it },
                        label = { Text(stringResource(R.string.hint_port), fontSize = 10.sp, fontFamily = MonospaceFamily) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = { onConnectTcp(tcpHost, tcpPort) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = OledBlack),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.btn_tcp), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                    }
                }
            }
        }
    }
}
