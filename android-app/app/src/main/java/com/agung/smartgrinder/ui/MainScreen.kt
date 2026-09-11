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
