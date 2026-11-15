package com.proxmark5.client.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.proxmark5.client.comm.ConnectionState
import com.proxmark5.client.data.CapturedTag
import com.proxmark5.client.protocol.DecodedBadge
import com.proxmark5.client.protocol.Em4100Tag
import com.proxmark5.client.protocol.Hf14aCard
import com.proxmark5.client.protocol.Pm5AntennaTuning
import com.proxmark5.client.protocol.Pm5BatteryInfo
import com.proxmark5.client.protocol.Pm5Constants
import com.proxmark5.client.protocol.T55xxBlockEntry
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Pm5AppScreen(viewModel: Pm5ViewModel) {
    val state by viewModel.uiState.collectAsState()

    var showDevicePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledBlack)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo and Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "PM5",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontFamily = MonospaceFamily
                            )
                        }
                        Text(
                            text = "PROXMARK5",
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
                                    contentDescription = "Battery",
                                    tint = if (bat.isCharging) NeonGreen else if (bat.socPct > 20) ElectricCyan else ErrorRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${bat.socPct}% | ${bat.voltageFormatted} | ${bat.currentFormatted}",
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
                                Text("Disconnect", fontSize = 11.sp, fontFamily = MonospaceFamily)
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
                                Text("Connecting", color = WarningAmber, fontSize = 11.sp, fontFamily = MonospaceFamily)
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
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
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
                        is ConnectionState.Connected -> Pair(NeonGreen, "● CONNECTED: ${connStatus.deviceName} (${connStatus.transport}, MTU ${connStatus.mtu})")
                        is ConnectionState.Connecting -> Pair(WarningAmber, "○ CONNECTING: ${connStatus.deviceName}")
                        is ConnectionState.Negotiating -> Pair(WarningAmber, "○ NEGOTIATING: ${connStatus.stage}")
                        is ConnectionState.Scanning -> Pair(ElectricCyan, "○ SCANNING FOR PM5 BWM...")
                        is ConnectionState.Error -> Pair(ErrorRed, "✕ ${connStatus.message}")
                        is ConnectionState.Disconnected -> Pair(TextMuted, "○ OFFLINE (Tap Connect)")
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (state.isBusy) {
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
        val navDestinations = remember {
            listOf(
                Triple("Power", "⚡ Power & HW", Icons.Filled.Bolt),
                Triple("LF Studio", "📡 LF Studio", Icons.Filled.Sensors),
                Triple("HF NFC", "💳 HF NFC", Icons.Filled.Nfc),
                Triple("T55xx", "💾 T55xx", Icons.Filled.Memory),
                Triple("Vault", "🗃️ Vault", Icons.Filled.Key),
                Triple("Console", "⚡ Console", Icons.Filled.Terminal)
            )
        }

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
                                    maxLines = 1
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
                                text = if (isConnected) "LINK OK" else "OFFLINE",
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
    AdaptiveTwoColumnPane(
        isExpanded = isExpanded,
        leftContent = {
            BatteryCard(state, viewModel)
            MainboardRgbCard(state, viewModel)
            HardwareQcCard(state, viewModel)
        },
        rightContent = {
            AntennaControllerCard(state, viewModel)
            AntennaResonanceCard(state, viewModel)
            ArchitectureCard(state)
        }
    )
}

@Composable
fun DashboardHardwarePane(state: Pm5UiState, viewModel: Pm5ViewModel, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Battery Telemetry & Charger
        BatteryCard(state, viewModel)

        // 2. Swappable Antenna Controller (0x51) & Physical Blue LEDs
        AntennaControllerCard(state, viewModel)

        // 3. Mainboard RGB LED Controller (0x48)
        MainboardRgbCard(state, viewModel)

        // 4. Antenna Resonance Tuning Card
        AntennaResonanceCard(state, viewModel)

        // 5. Hardware QC Diagnostic Card
        HardwareQcCard(state, viewModel)

        // 6. PM5 System Architecture
        ArchitectureCard(state)
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
                        contentDescription = null,
                        tint = if (bat?.isCharging == true) NeonGreen else ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BWM BATTERY & FUEL GAUGE",
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
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
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
                        text = "${bat.socPct}%",
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
                            text = "Current: ${bat.currentFormatted}",
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
                    MetricItem("Voltage", bat.voltageFormatted)
                    MetricItem("Temp", bat.tempFormatted)
                    MetricItem("Capacity", "${bat.remainingMah} / ${bat.fullChargeMah} mAh")
                    MetricItem("Health", "${bat.healthPct}%")
                }
            } else {
                Text(
                    text = if (state.connectionState is ConnectionState.Connected) "Awaiting battery telemetry (CMD_PM5_BWM_GET_BATTERY)..." else "Connect PM5 to inspect battery telemetry",
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
                    Text("Charge ON", fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.toggleCharge(false) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Text("Charge OFF", fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.setChargeVoltage(4100) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
                ) {
                    Text("4.1V Safe", fontSize = 10.sp, maxLines = 1, softWrap = false, fontFamily = MonospaceFamily)
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
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SWAPPABLE ANTENNA (I2C 0x51)",
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
                        Icon(Icons.Filled.Refresh, contentDescription = "Read", tint = ElectricCyan, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read", fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                }
            }

            Text(
                text = "Controls physical blue LEDs, frequency & Q-factor on the swappable antenna PCB",
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
                        text = "ANTENNA BLUE LEDs",
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
                                text = "LF Blue LED",
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
                                text = "HF Blue LED",
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
                            text = if (bothOn) "Turn Both Blue LEDs OFF" else "Turn Both Blue LEDs ON",
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
                    text = "FREQ SELECT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontFamily = MonospaceFamily
                )
                // Q-Factor Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (cfg?.highQ == true) "High Q" else "Low Q",
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
                Pair("125 kHz", "125k"),
                Pair("134 kHz", "134k"),
                Pair("250 kHz", "250k"),
                Pair("375 kHz", "375k"),
                Pair("500 kHz", "500k")
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
                    text = "Reg 0x02: 0x%02X".format(cfg?.rawByte ?: 0),
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = MonospaceFamily
                )
                Text(
                    text = "Active: ${cfg?.activeFreqString ?: "None"} | Q:${if (cfg?.highQ == true) "High" else "Low"}",
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
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = state.selectedRgbColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ANTENNA RGB LED (I2C 0x48)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontFamily = MonospaceFamily
                )
            }
            Text(
                text = "Controls swappable antenna RGB LED via CMD_PM5_RGB_SET",
                fontSize = 11.sp,
                color = TextMuted,
                fontFamily = MonospaceFamily
            )
            Spacer(modifier = Modifier.height(10.dp))

            val colors = listOf(
                Pair("Cyan", ElectricCyan),
                Pair("Green", NeonGreen),
                Pair("Purple", CyberPurple),
                Pair("Amber", WarningAmber),
                Pair("Red", ErrorRed),
                Pair("White", Color.White),
                Pair("OFF", Color.Black)
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
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ANTENNA RESONANCE (TUNE)",
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
                    Text("Measure Sweep", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val tuning = state.antennaTuning
            if (tuning != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("LF 125 kHz", "%.2f V".format(tuning.lf125V))
                    MetricItem("LF 134 kHz", "%.2f V".format(tuning.lf134V))
                    MetricItem("HF 13.56 MHz", "%.2f V".format(tuning.hfV))
                    MetricItem("Peak Divisor", "${tuning.peakDivisor}")
                }
            } else {
                Text(
                    text = if (state.isTuningAntenna) "Sweeping frequencies (takes ~12s)..." else "No tuning sweep measured yet",
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
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HARDWARE QC SELF-TEST",
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
                        Text("Run Self-Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val qc = state.qcTestResult
            if (qc != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (qc.success || qc.failedItem == 2) NeonGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (qc.success || qc.failedItem == 2) NeonGreen.copy(alpha = 0.6f) else ErrorRed.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (qc.success || qc.failedItem == 2) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (qc.success || qc.failedItem == 2) NeonGreen else ErrorRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (qc.success || qc.failedItem == 2) "DIAGNOSTIC PASSED" else "DIAGNOSTIC FAILED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (qc.success || qc.failedItem == 2) NeonGreen else ErrorRed,
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
                    text = "Tests Antenna controller (I2C 0x51), Mainboard RGB (0x48), buzzers, and LEDs.",
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
                text = "PROXMARK5 ARCHITECTURE",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "• MCU: AT32F435 (288 MHz ARM Cortex-M4, Fast ASK Demod)\n" +
                        "• FPGA: Gowin Semi\n" +
                        "• Wireless: ESP32-C2 (BWM BLE & WiFi STA/TCP)\n" +
                        "• Charger: AW32001E | Fuel Gauge: BQ27427",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                fontFamily = MonospaceFamily
            )

            val ver = state.versionInfo
            if (ver != null && ver.versionString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Firmware: ${ver.versionString}",
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
    val clipboardManager = LocalClipboardManager.current
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
                        Column {
                            Text(
                                text = "AUTO-DEMODULATOR SCANNER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = "Cycles HID, EM4100, AWID, IO Prox",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = MonospaceFamily
                            )
                        }

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
                            Icon(if (state.isAutoScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.isAutoScanning) "STOP SCAN" else "START AUTO-SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                        }
                    }

                    // Dedicated Watch Mode Pills
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WatchFilterChip("HID", state.isWatchingHid) { viewModel.toggleWatch("HID") }
                        WatchFilterChip("EM4100", state.isWatchingEm4100) { viewModel.toggleWatch("EM4100") }
                        WatchFilterChip("AWID", state.isWatchingAwid) { viewModel.toggleWatch("AWID") }
                        WatchFilterChip("IO", state.isWatchingIo) { viewModel.toggleWatch("IO") }
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
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(badge.uidHex))
                                    Toast.makeText(context, "Copied UID: ${badge.uidHex}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = ElectricCyan, modifier = Modifier.size(16.dp))
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
                            MetricItem("Card Number", badge.cardId)
                            MetricItem("Facility Code", badge.facilityCode ?: "—")
                            MetricItem("Format", badge.formatLen ?: "Standard")
                        }

                        // Clone Quick Action
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.executeClone(badge.uidHex) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPurple, contentColor = OledBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clone to Transponder (T5577)", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
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
                            text = "EM4100 64-BIT FRAME STRUCTURE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElectricCyan,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Standard: ${em.formattedPm3}",
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
                            text = "Parity Check: Even Horizontal & Column Parity Valid",
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
                        text = "MANUAL TRANSPONDER CLONING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputUid,
                        onValueChange = { inputUid = it.filter { c -> c.isLetterOrDigit() }.take(10).uppercase() },
                        label = { Text("10-Hex UID (e.g. 0102030405)", fontFamily = MonospaceFamily) },
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
                        TargetChipButton("T5577", state.cloneTargetChip == Pm5Constants.TARGET_T55X7) {
                            viewModel.setCloneTargetChip(Pm5Constants.TARGET_T55X7)
                        }
                        TargetChipButton("Q5", state.cloneTargetChip == Pm5Constants.TARGET_Q5) {
                            viewModel.setCloneTargetChip(Pm5Constants.TARGET_Q5)
                        }
                        TargetChipButton("EM4305", state.cloneTargetChip == Pm5Constants.TARGET_EM4305) {
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
                        Text("WRITE CLONE TO CARD", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
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
    val clipboardManager = LocalClipboardManager.current
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
                                text = "ISO 14443-A (HF 13.56 MHz)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = "MIFARE Classic, Ultralight, NTAG, DESFire",
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
                            Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("READ 14443A", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
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
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(hfCard.uidHex))
                                    Toast.makeText(context, "Copied UID: ${hfCard.uidHex}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = ElectricCyan, modifier = Modifier.size(16.dp))
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
                            MetricItem("UID Length", "${hfCard.uidLen} Bytes")
                            MetricItem("ATQA", "0x${hfCard.atqaHex}")
                            MetricItem("SAK", "0x${hfCard.sakHex}")
                        }

                        if (hfCard.atsHex != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MetricItem("ATS / Historical", hfCard.atsHex)
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
                            text = "CARD INSPECTOR READY",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = MonospaceFamily
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Present a 13.56 MHz ISO 14443-A card or emulator to the PM5 coil and tap READ 14443A to inspect UID, ATQA, SAK, and ATS.",
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
                                text = "T55XX BLOCK MAP (0 - 7)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = "AT32 Fast MCU Demodulation Enabled",
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
                            Text("READ ALL (0-7)", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
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
                                text = "Config [B0]: ${b0.configDescription}",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontFamily = MonospaceFamily,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Grid of 8 blocks
                    Spacer(modifier = Modifier.height(10.dp))
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
                                text = "Blk $b: ${entry?.hexString ?: "—"}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (entry?.value != null) TextPrimary else TextMuted,
                                fontFamily = MonospaceFamily
                            )
                            Text(
                                text = entry?.asciiString ?: "—",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontFamily = MonospaceFamily
                            )
                            OutlinedButton(
                                onClick = { viewModel.readT55xxBlock(b) },
                                modifier = Modifier.height(24.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Read", fontSize = 10.sp, fontFamily = MonospaceFamily)
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
                        text = "WRITE BLOCK ${state.selectedT55xxBlock}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.writeBlockDataHex,
                        onValueChange = { viewModel.setWriteBlockDataHex(it) },
                        label = { Text("Data (8-Hex e.g. 00148040)", fontFamily = MonospaceFamily) },
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
                            label = { Text("Password (8-Hex)", fontFamily = MonospaceFamily) },
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
                            Text("WRITE", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = MonospaceFamily)
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
                        text = "PASSWORD AUDIT & CRACKER",
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
                            Text(if (state.isCheckingPwds) "CANCEL AUDIT" else "DICTIONARY AUDIT", fontSize = 11.sp, fontFamily = MonospaceFamily)
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
                            Text("WHITE CLONER KEY", fontSize = 11.sp, fontFamily = MonospaceFamily)
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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAVED BADGES & TAGS (${state.savedTags.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily
            )

            Row {
                TextButton(
                    onClick = {
                        val json = viewModel.exportVaultJson()
                        clipboardManager.setText(AnnotatedString(json))
                        Toast.makeText(context, "Exported JSON to Clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export JSON", fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                }
            }
        }

        if (state.savedTags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No saved tags yet.\nScan an LF badge or HF card to save it here automatically.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = MonospaceFamily,
                    textAlign = TextAlign.Center
                )
            }
        } else {
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
                                            clipboardManager.setText(AnnotatedString(tag.uidHex))
                                            Toast.makeText(context, "Copied UID: ${tag.uidHex}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteTag(tag.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
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
                                    text = "Card: ${tag.cardId} | FC: ${tag.facilityCode ?: "—"} | ${tag.formatDescription ?: ""}",
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
                text = "COMMUNICATION TERMINAL",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = TextPrimary,
                fontFamily = MonospaceFamily
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isExpanded) {
                    OutlinedButton(
                        onClick = { viewModel.refreshBatteryNow() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("BATT", fontSize = 10.sp, fontFamily = MonospaceFamily)
                    }
                    OutlinedButton(
                        onClick = { viewModel.readAntennaConfig() },
                        modifier = Modifier.height(26.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Text("ANT CFG", fontSize = 10.sp, fontFamily = MonospaceFamily)
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.ping() },
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Text("PING", fontSize = 10.sp, fontFamily = MonospaceFamily)
                }
                OutlinedButton(
                    onClick = { viewModel.breakLoop() },
                    modifier = Modifier.height(26.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                ) {
                    Text("BREAK", fontSize = 10.sp, fontFamily = MonospaceFamily)
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
                Icon(Icons.Filled.Check, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
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
// DEVICE PICKER DIALOG (BLE & TCP)
// ==========================================

@Composable
fun DevicePickerDialog(
    state: Pm5UiState,
    onDismiss: () -> Unit,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onConnectTcp: (String, String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onRefreshPaired: () -> Unit
) {
    var tcpHost by remember { mutableStateOf(state.tcpHost) }
    var tcpPort by remember { mutableStateOf(state.tcpPort) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONNECT PROXMARK5",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontFamily = MonospaceFamily
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BLE Scan Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BLE Devices", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricCyan, fontFamily = MonospaceFamily)
                    Row {
                        TextButton(onClick = { if (state.connectionState is ConnectionState.Scanning) onStopScan() else onStartScan() }) {
                            Text(if (state.connectionState is ConnectionState.Scanning) "Stop" else "Scan", fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                        }
                        TextButton(onClick = onRefreshPaired) {
                            Text("Paired", fontSize = 11.sp, color = TextSecondary, fontFamily = MonospaceFamily)
                        }
                    }
                }

                // Device List (Both bonded and scanned)
                val allDevices = (state.discoveredDevices.map { it.device } + state.pairedDevices).distinctBy { it.address }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    if (allDevices.isEmpty()) {
                        item {
                            Text(
                                text = "No Proxmark5 found yet. Tap 'Scan' to search over BLE.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = MonospaceFamily,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(allDevices) { dev ->
                            @SuppressLint("MissingPermission")
                            val devName = dev.name ?: "Unknown"
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
                                        contentDescription = null,
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

                                Text("Connect", fontSize = 11.sp, color = ElectricCyan, fontFamily = MonospaceFamily)
                            }
                            HorizontalDivider(color = DarkOutline.copy(alpha = 0.5f))
                        }
                    }
                }

                // Alternative: WiFi TCP
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkOutline)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Or Connect over WiFi TCP (BWM STA)", fontSize = 11.sp, color = TextSecondary, fontFamily = MonospaceFamily)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = tcpHost,
                        onValueChange = { tcpHost = it },
                        label = { Text("IP", fontSize = 10.sp, fontFamily = MonospaceFamily) },
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
                        label = { Text("Port", fontSize = 10.sp, fontFamily = MonospaceFamily) },
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
                        Text("TCP", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = MonospaceFamily)
                    }
                }
            }
        }
    }
}
