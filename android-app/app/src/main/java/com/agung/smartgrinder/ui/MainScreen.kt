package com.agung.smartgrinder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.GrinderViewModel
import com.agung.smartgrinder.ble.*

@Composable
fun MainScreen(
    viewModel: GrinderViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val status by viewModel.status.collectAsState()
    val cupProfiles by viewModel.cupProfiles.collectAsState()
    val calibrationStatus by viewModel.calibrationStatus.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            viewModel.loadAllCupProfiles()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (!permissionsGranted) {
            PermissionRequest(onRequestPermissions)
            return@Surface
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ConnectionRow(connectionState, viewModel::connect, viewModel::disconnect) }

            if (status != null) {
                item { StatusCard(status!!) }
                item { TargetWeightRow(status!!.targetWeightG, viewModel::setTargetWeight) }
                item { ModeRow(status!!.mode, viewModel::setMode) }
                item { ControlButtonsRow(viewModel::start, viewModel::stop, viewModel::emergencyStop) }

                item { HorizontalDivider() }
                item { TareRow(viewModel::tare) }
                item {
                    CalibrationSection(
                        pointCount = calibrationStatus?.pointCount ?: 0,
                        lastPointRaw = calibrationStatus?.lastPointRaw,
                        lastPointWeightG = calibrationStatus?.lastPointWeightG,
                        lastSaveOk = calibrationStatus?.lastSaveOk,
                        onAddPoint = viewModel::calAddPoint,
                        onClear = viewModel::calClear,
                        onSave = viewModel::calSave
                    )
                }
                item { CornerCheckSection(currentWeightG = status!!.weightG) }

                item { HorizontalDivider() }
                item {
                    Text("Dosing cups", style = MaterialTheme.typography.titleMedium)
                }
                items(cupProfiles.size) { id ->
                    CupProfileEditor(
                        id = id,
                        profile = cupProfiles[id],
                        isActive = status!!.activeCupProfileId == id,
                        currentWeightG = status!!.weightG,
                        onSelect = { viewModel.selectCupProfile(id) },
                        onSave = { name, weight, tolerance -> viewModel.saveCupProfile(id, name, weight, tolerance) }
                    )
                }
            } else {
                item {
                    Text(
                        "Not connected - tap Connect above and place the phone near the grinder.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@Composable
private fun ConnectionRow(state: ConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (state) {
                ConnectionState.DISCONNECTED -> "Disconnected"
                ConnectionState.SCANNING -> "Scanning..."
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.CONNECTED -> "Connected"
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (state == ConnectionState.CONNECTED) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect, enabled = state == ConnectionState.DISCONNECTED) { Text("Connect") }
        }
    }
}

@Composable
private fun StatusCard(status: GrinderStatus) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1fg".format(status.weightG), fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(status.state.label, style = MaterialTheme.typography.titleMedium)
            Text(status.mode.name, style = MaterialTheme.typography.bodyMedium)
            if (status.errorCode != 0) {
                Spacer(Modifier.height(4.dp))
                Text("error code: ${status.errorCode}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TargetWeightRow(currentTarget: Float, onSet: (Float) -> Unit) {
    var text by remember(currentTarget) { mutableStateOf(currentTarget.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Target dose (g)") },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { text.toFloatOrNull()?.let(onSet) }) { Text("Set") }
    }
}

@Composable
private fun ModeRow(currentMode: GrinderMode, onSetMode: (GrinderMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrinderMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onSetMode(mode) },
                label = { Text(mode.name) }
            )
        }
    }
}

@Composable
private fun ControlButtonsRow(onStart: () -> Unit, onStop: () -> Unit, onEStop: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start") }
        OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
        Button(
            onClick = onEStop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f)
        ) { Text("E-Stop") }
    }
}

@Composable
private fun TareRow(onTare: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Empty the scale, then zero it.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onTare) { Text("Tare") }
    }
}

/**
 * Multi-point calibration: place a known weight, tell it what that weight is,
 * capture; repeat for a few different weights (e.g. 0g via Tare, 50g, 100g,
 * 200g); Save fits a line through all captured points. More points and a
 * wider weight spread = better accuracy across the whole working range.
 */
@Composable
private fun CalibrationSection(
    pointCount: Int,
    lastPointRaw: Float?,
    lastPointWeightG: Float?,
    lastSaveOk: Boolean?,
    onAddPoint: (Float) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    var knownWeight by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calibration", style = MaterialTheme.typography.titleMedium)
            Text(
                "Place a known weight on the scale, enter its actual weight below, and capture. Repeat with a few different weights (including empty = 0g), then Save.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = knownWeight,
                    onValueChange = { knownWeight = it },
                    label = { Text("Known weight on scale now (g)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { knownWeight.toFloatOrNull()?.let(onAddPoint) }) { Text("Capture") }
            }

            Text("Points captured: $pointCount", style = MaterialTheme.typography.bodyMedium)
            if (lastPointRaw != null && lastPointWeightG != null) {
                Text(
                    "Last: raw=%.0f at %.1fg".format(lastPointRaw, lastPointWeightG),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (lastSaveOk != null) {
                Text(
                    if (lastSaveOk) "Calibration saved" else "Save failed - need at least 2 points at different weights",
                    color = if (lastSaveOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear) { Text("Clear points") }
                Button(onClick = onSave, enabled = pointCount >= 2) { Text("Save calibration") }
            }
        }
    }
}

/**
 * Diagnostic only, not calibration: capture the same known weight at the
 * center and at each corner of the platform. Large differences between
 * positions point at a mechanical mounting problem (uneven feet, platform
 * not resting squarely on the load cell) rather than something a software
 * calibration number can fix.
 */
@Composable
private fun CornerCheckSection(currentWeightG: Float) {
    val positions = listOf("Center", "Corner 1", "Corner 2", "Corner 3", "Corner 4")
    val captured = remember { mutableStateMapOf<String, Float>() }
    val centerValue = captured["Center"]

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Corner consistency check", style = MaterialTheme.typography.titleMedium)
            Text(
                "Diagnostic only - doesn't change calibration. Put the same weight at the center, capture, then move it to each corner and capture again. Corners should read close to the center value.",
                style = MaterialTheme.typography.bodySmall
            )

            positions.forEach { pos ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(pos, modifier = Modifier.weight(1f))
                    val value = captured[pos]
                    val deltaText = if (value != null && centerValue != null && pos != "Center") {
                        " (%+.1fg)".format(value - centerValue)
                    } else ""
                    Text(
                        text = (value?.let { "%.1fg".format(it) } ?: "-") + deltaText,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { captured[pos] = currentWeightG }) { Text("Capture") }
                }
            }
        }
    }
}

@Composable
private fun CupProfileEditor(
    id: Int,
    profile: CupProfile,
    isActive: Boolean,
    currentWeightG: Float,
    onSelect: () -> Unit,
    onSave: (name: String, weightG: Float, toleranceG: Float) -> Unit
) {
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var weight by remember(profile.cupWeightG) {
        mutableStateOf(if (profile.isConfigured) profile.cupWeightG.toString() else "")
    }
    var tolerance by remember(profile.toleranceG) { mutableStateOf(profile.toleranceG.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = isActive, onClick = onSelect)
                Text("Cup ${id + 1}", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Cup weight (g)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { weight = currentWeightG.toString() }) { Text("Use current\n(%.1fg)".format(currentWeightG)) }
            }
            OutlinedTextField(
                value = tolerance,
                onValueChange = { tolerance = it },
                label = { Text("Tolerance (±g)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: return@Button
                    val t = tolerance.toFloatOrNull() ?: return@Button
                    onSave(name, w, t)
                },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Save") }
        }
    }
}
