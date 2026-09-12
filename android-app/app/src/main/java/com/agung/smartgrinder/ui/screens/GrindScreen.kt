package com.agung.smartgrinder.ui.screens

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
import com.agung.smartgrinder.ble.CupProfile
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.theme.status

@Composable
fun GrindScreen(
    status: GrinderStatus?,
    cupProfiles: List<CupProfile>,
    onSetTargetWeight: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEStop: () -> Unit,
    onSelectCupProfile: (Int) -> Unit,
    onSaveCupProfile: (id: Int, name: String, weightG: Float, toleranceG: Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Smart Grinder", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Weight-based dosing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (status == null) {
            item {
                Text(
                    "Not connected - go to Settings to connect to the grinder.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@LazyColumn
        }

        item {
            SectionCard(label = "Current weight") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("%.1fg".format(status.weightG), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(status.state.label, style = MaterialTheme.typography.titleMedium)
                    if (status.errorCode != 0) {
                        Text("error code: ${status.errorCode}", color = MaterialTheme.status.danger)
                    }
                }
            }
        }

        item { TargetWeightRow(status.targetWeightG, onSetTargetWeight) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AppButton("Start", onClick = onStart, modifier = Modifier.weight(1f))
                NeutralOutlinedButton("Stop", onClick = onStop, modifier = Modifier.weight(1f))
                AppButton(
                    "E-Stop",
                    onClick = onEStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.danger)
                )
            }
        }

        item { Text("Dosing cups", style = MaterialTheme.typography.titleMedium) }
        items(cupProfiles.size) { id ->
            CupProfileEditor(
                id = id,
                profile = cupProfiles[id],
                isActive = status.activeCupProfileId == id,
                currentWeightG = status.weightG,
                onSelect = { onSelectCupProfile(id) },
                onSave = { name, weight, tolerance -> onSaveCupProfile(id, name, weight, tolerance) }
            )
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
        AppButton("Set", onClick = { text.toFloatOrNull()?.let(onSet) })
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

    SectionCard {
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
        AppButton(
            "Save",
            onClick = {
                val w = weight.toFloatOrNull() ?: return@AppButton
                val t = tolerance.toFloatOrNull() ?: return@AppButton
                onSave(name, w, t)
            },
            modifier = Modifier.align(Alignment.End)
        )
    }
}
