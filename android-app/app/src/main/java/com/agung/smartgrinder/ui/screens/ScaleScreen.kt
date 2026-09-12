package com.agung.smartgrinder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.ToggleChip
import com.agung.smartgrinder.ui.theme.status

private val presetWeightsG = listOf(17f, 18f, 20f)

/**
 * Simple weighing, no timing. Firmware has no separate "Scale mode" - this
 * just reads the always-on weight stream and issues Tare, so it works no
 * matter what grinder mode is currently active.
 */
@Composable
fun ScaleScreen(status: GrinderStatus?, onTare: () -> Unit) {
    var selectedPreset by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Scale Mode", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Precision weighing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (status == null) {
            Text("Not connected - go to Settings to connect to the grinder.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        SectionCard(label = "Current weight") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "%.1fg".format(status.weightG),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                selectedPreset?.let { preset ->
                    val delta = status.weightG - preset
                    Text(
                        "%+.1fg vs %.0fg target".format(delta, preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (kotlin.math.abs(delta) <= 0.2f) MaterialTheme.status.success else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AppButton("↺ Tare (reset to 0)", onClick = onTare, modifier = Modifier.fillMaxWidth())

        Text("Quick portions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            presetWeightsG.forEach { preset ->
                val isSelected = selectedPreset == preset
                ToggleChip(
                    label = "%.0fg".format(preset),
                    selected = isSelected,
                    onClick = { selectedPreset = if (isSelected) null else preset },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
