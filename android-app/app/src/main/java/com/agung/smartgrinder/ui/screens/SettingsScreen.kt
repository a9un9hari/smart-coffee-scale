package com.agung.smartgrinder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agung.smartgrinder.GrinderViewModel
import com.agung.smartgrinder.ble.BleOtaState
import com.agung.smartgrinder.ble.BleOtaStatus
import com.agung.smartgrinder.ble.ConnectionState
import com.agung.smartgrinder.ble.bleOtaErrorText
import com.agung.smartgrinder.ble.WeightSource
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.ToggleChip
import com.agung.smartgrinder.ui.theme.status

@Composable
fun SettingsScreen(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    darkTheme: Boolean,
    onSetDarkTheme: (Boolean) -> Unit,
    smoothingAlpha: Float,
    onSetSmoothingAlpha: (Float) -> Unit,
    timemoreAutoConnect: Boolean,
    onSetTimemoreAutoConnect: (Boolean) -> Unit,
    timemoreConnected: Boolean,
    weightSource: WeightSource,
    onSetWeightSource: (WeightSource) -> Unit,
    hx711Detected: Boolean,
    currentWeightG: Float?,
    onTare: () -> Unit,
    calPointCount: Int,
    calLastPointRaw: Float?,
    calLastPointWeightG: Float?,
    calLastSaveOk: Boolean?,
    onCalAddPoint: (Float) -> Unit,
    onCalClear: () -> Unit,
    onCalSave: () -> Unit,
    firmwareFileInfo: GrinderViewModel.FirmwareFileInfo?,
    bleOtaStatus: BleOtaStatus?,
    bleOtaSentBytes: Long,
    bleOtaSending: Boolean,
    bleOtaAppError: String?,
    bleOtaAppNotice: String?,
    onRefreshFirmwareFile: () -> Unit,
    onBleOtaStart: () -> Unit,
    onBleOtaCancel: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Configuration & preferences", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionCard(label = "Connected device") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when (connectionState) {
                            ConnectionState.DISCONNECTED -> "Disconnected"
                            ConnectionState.SCANNING -> "Scanning..."
                            ConnectionState.CONNECTING -> "Connecting..."
                            ConnectionState.CONNECTED -> "Connected"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (connectionState == ConnectionState.CONNECTED) {
                        NeutralOutlinedButton("Disconnect", onClick = onDisconnect)
                    } else {
                        AppButton("Connect", onClick = onConnect, enabled = connectionState == ConnectionState.DISCONNECTED)
                    }
                }
            }
        }

        item {
            SectionCard(label = "Appearance") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Dark mode", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = darkTheme, onCheckedChange = onSetDarkTheme)
                }
            }
        }

        item {
            SectionCard(label = "Weight smoothing") {
                var sliderValue by remember(smoothingAlpha) { mutableStateOf(smoothingAlpha) }
                Text(
                    "Lower = smoother reading, less jitter, slightly slower to react. Higher = more responsive, but shows more raw noise. Not saved on the grinder - re-applied automatically every time the app connects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Smooth", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f".format(sliderValue), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Responsive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSetSmoothingAlpha(sliderValue) },
                    valueRange = 0.05f..0.9f
                )
            }
        }

        item {
            SectionCard(label = "Timemore Dot") {
                Text("Weight source", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Which sensor the grinder actually weighs from - switches everywhere in the app (Grind, Timer, Brew, Scale), not just here. Saved on the grinder, so it survives a power cycle even with no phone around.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ToggleChip(
                        label = "Load cell",
                        selected = weightSource == WeightSource.HX711,
                        onClick = { onSetWeightSource(WeightSource.HX711) },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleChip(
                        label = "Timemore Dot",
                        selected = weightSource == WeightSource.TIMEMORE,
                        onClick = { onSetWeightSource(WeightSource.TIMEMORE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-connect", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = timemoreAutoConnect, onCheckedChange = onSetTimemoreAutoConnect)
                }
                Text(
                    "On by default: the grinder scans for a Timemore Dot on its own as soon as it boots, no action needed here. Turn off if you don't have one, or don't want it woken up right now - not saved on the grinder, re-applied automatically every time the app connects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (timemoreAutoConnect) {
                    Text(
                        if (timemoreConnected) "Connected" else "Not connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (timemoreConnected) MaterialTheme.status.success else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            BleOtaSection(
                connected = connectionState == ConnectionState.CONNECTED,
                fileInfo = firmwareFileInfo,
                status = bleOtaStatus,
                sentBytes = bleOtaSentBytes,
                sending = bleOtaSending,
                appError = bleOtaAppError,
                appNotice = bleOtaAppNotice,
                onRefresh = onRefreshFirmwareFile,
                onStart = onBleOtaStart,
                onCancel = onBleOtaCancel
            )
        }

        if (currentWeightG == null) return@LazyColumn

        item {
            SectionCard {
                Text("Empty the scale, then zero it.", style = MaterialTheme.typography.bodyMedium)
                AppButton("Tare", onClick = onTare)
            }
        }

        // Both sections below work directly against the HX711 (raw ADC reads,
        // mechanical mounting checks) regardless of which weightSource is
        // currently selected - meaningless, and potentially destructive to
        // the load cell's saved calibration, when it isn't actually wired up
        // (e.g. running Timemore-Dot-only for a while).
        if (hx711Detected) {
            item {
                CalibrationSection(
                    pointCount = calPointCount,
                    lastPointRaw = calLastPointRaw,
                    lastPointWeightG = calLastPointWeightG,
                    lastSaveOk = calLastSaveOk,
                    onAddPoint = onCalAddPoint,
                    onClear = onCalClear,
                    onSave = onCalSave
                )
            }

            item { CornerCheckSection(currentWeightG = currentWeightG) }
        } else {
            item {
                SectionCard {
                    Text(
                        "Load cell not detected - calibration and corner-check are hidden until it's wired up again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Firmware update over Bluetooth (include/ble_ota.h) - the normal update
 * path once the board lives inside the grinder: no WiFi, no HTTP server.
 * The image is pushed to the phone first (adb push to the app's files dir),
 * then streamed from here. The grinder keeps its current firmware unless
 * the whole image arrives and its MD5 checks out.
 */
@Composable
private fun BleOtaSection(
    connected: Boolean,
    fileInfo: GrinderViewModel.FirmwareFileInfo?,
    status: BleOtaStatus?,
    sentBytes: Long,
    sending: Boolean,
    appError: String?,
    appNotice: String?,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    SectionCard(label = "Firmware update (Bluetooth)") {
        if (fileInfo == null) {
            Text(
                "No firmware.bin on the phone yet. Copy it to Android/data/com.agung.smartgrinder/files/firmware.bin, then tap Refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val modified = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(fileInfo.modifiedMs))
            Text(
                String.format(java.util.Locale.US, "firmware.bin · %.1f KB · copied %s", fileInfo.sizeBytes / 1024f, modified),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (sending) {
            val total = fileInfo?.sizeBytes ?: status?.total ?: 0L
            val fraction = if (total > 0) (sentBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            Text(
                String.format(java.util.Locale.US, "Sending… %d%% (%.0f / %.0f KB)", (fraction * 100).toInt(), sentBytes / 1024f, total / 1024f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                "Keep the phone close and the app open until it finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            status?.state == BleOtaState.SUCCESS -> Text(
                "Update verified - the grinder is restarting. Its screen shows the new build date on the splash.",
                color = MaterialTheme.status.success,
                style = MaterialTheme.typography.bodyMedium
            )
            status?.state == BleOtaState.ERROR -> Text(
                "Update failed: ${bleOtaErrorText(status.errorCode)}",
                color = MaterialTheme.status.danger,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        appError?.let {
            Text(it, color = MaterialTheme.status.danger, style = MaterialTheme.typography.bodyMedium)
        }
        appNotice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sending) {
                NeutralOutlinedButton("Cancel", onClick = onCancel)
            } else {
                AppButton("Update via Bluetooth", onClick = onStart, enabled = connected && fileInfo != null)
                NeutralOutlinedButton("Refresh", onClick = onRefresh)
            }
        }
        if (!connected && !sending) {
            Text(
                "Connect to the grinder first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

    SectionCard(label = "Calibration") {
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
            AppButton("Capture", onClick = { knownWeight.toFloatOrNull()?.let(onAddPoint) })
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
                color = if (lastSaveOk) MaterialTheme.status.success else MaterialTheme.status.danger,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeutralOutlinedButton("Clear points", onClick = onClear)
            AppButton("Save calibration", onClick = onSave, enabled = pointCount >= 2)
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

    SectionCard(label = "Corner consistency check") {
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
