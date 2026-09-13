package com.agung.smartgrinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.GrinderViewModel
import com.agung.smartgrinder.ble.ConnectionState
import com.agung.smartgrinder.ble.GrinderMode
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.screens.*
import com.agung.smartgrinder.ui.theme.status

private enum class Tab(val label: String, val icon: String) {
    GRIND("Grind", "⚙️"),
    ESPRESSO("Espresso", "☕"),
    SCALE("Scale", "⚖️"),
    TIMER("Timer", "⏱️"),
    BREW("Brew", "📊"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: GrinderViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(Tab.GRIND) }
    var showSettings by remember { mutableStateOf(false) }

    val connectionState by viewModel.connectionState.collectAsState()
    val status by viewModel.status.collectAsState()
    val cupProfiles by viewModel.cupProfiles.collectAsState()
    val calibrationStatus by viewModel.calibrationStatus.collectAsState()
    val otaStatus by viewModel.otaStatus.collectAsState()
    val shotSamples by viewModel.shotSamples.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()
    val smoothingAlpha by viewModel.smoothingAlpha.collectAsState()

    var autoConnectAttempted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && !autoConnectAttempted) {
            autoConnectAttempted = true
            viewModel.connectToSavedDevice()
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            viewModel.loadAllCupProfiles()
        }
    }

    // Grinder mode mirrors the Grind/Espresso tab - Scale/Timer/Brew are
    // client-side utilities that just read the always-on weight stream and
    // don't touch the firmware's mode.
    LaunchedEffect(selectedTab, connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            when (selectedTab) {
                Tab.GRIND -> viewModel.setMode(GrinderMode.GRINDER)
                Tab.ESPRESSO -> viewModel.setMode(GrinderMode.ESPRESSO)
                else -> {}
            }
        }
    }

    if (!permissionsGranted) {
        PermissionRequest(onRequestPermissions)
        return
    }

    if (showSettings) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) { Text("⬅️") }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                SettingsScreen(
                    connectionState = connectionState,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    darkTheme = darkTheme,
                    onSetDarkTheme = viewModel::setDarkTheme,
                    smoothingAlpha = smoothingAlpha,
                    onSetSmoothingAlpha = viewModel::setSmoothingAlpha,
                    currentWeightG = status?.weightG,
                    onTare = viewModel::tare,
                    calPointCount = calibrationStatus?.pointCount ?: 0,
                    calLastPointRaw = calibrationStatus?.lastPointRaw,
                    calLastPointWeightG = calibrationStatus?.lastPointWeightG,
                    calLastSaveOk = calibrationStatus?.lastSaveOk,
                    onCalAddPoint = viewModel::calAddPoint,
                    onCalClear = viewModel::calClear,
                    onCalSave = viewModel::calSave,
                    otaStatus = otaStatus,
                    onOtaStart = viewModel::otaStart,
                    onOtaCancel = viewModel::otaCancel
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { showSettings = true }) { Text("⚙️") }
                }
            )
        },
        bottomBar = { BottomNav(selectedTab, onSelect = { selectedTab = it }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Tab.GRIND -> GrindScreen(
                    status = status,
                    cupProfiles = cupProfiles,
                    onSetTargetWeight = viewModel::setTargetWeight,
                    onStart = viewModel::start,
                    onStop = viewModel::stop,
                    onEStop = viewModel::emergencyStop,
                    onSelectCupProfile = viewModel::selectCupProfile,
                    onSaveCupProfile = viewModel::saveCupProfile
                )
                Tab.ESPRESSO -> EspressoScreen(
                    status = status,
                    shotSamples = shotSamples,
                    onSetTargetWeight = viewModel::setTargetWeight,
                    onStart = viewModel::start,
                    onStop = viewModel::stop,
                    onEStop = viewModel::emergencyStop
                )
                Tab.SCALE -> ScaleScreen(status = status, onTare = viewModel::tare)
                Tab.TIMER -> TimerScreen(status = status)
                Tab.BREW -> BrewScreen(status = status)
            }
        }
    }
}

/**
 * Flat bottom nav matching the mockup's convention: no Material3 pill/ripple
 * indicator behind the icon, just a top accent line + brand-color text on
 * the active tab. The default NavigationBar's indicatorColor defaults to
 * colorScheme.secondaryContainer, which reads as an off-brand purple chip
 * against this dark/coffee palette - hence the custom row instead.
 */
@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.status.navBackground)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .drawBehind {
                        if (isSelected) {
                            drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3.dp.toPx())
                        }
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tab.icon, fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth permission needed to find the grinder.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        AppButton("Grant permission", onClick = onRequest)
    }
}
