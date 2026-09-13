package com.agung.smartgrinder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agung.smartgrinder.ble.ConnectionState
import com.agung.smartgrinder.ble.OtaState
import com.agung.smartgrinder.ble.OtaStatus
import com.agung.smartgrinder.ble.otaErrorHint
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
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
    currentWeightG: Float?,
    onTare: () -> Unit,
    calPointCount: Int,
    calLastPointRaw: Float?,
    calLastPointWeightG: Float?,
    calLastSaveOk: Boolean?,
    onCalAddPoint: (Float) -> Unit,
    onCalClear: () -> Unit,
    onCalSave: () -> Unit,
    otaStatus: OtaStatus?,
    onOtaStart: (ssid: String, password: String, url: String) -> Unit,
    onOtaCancel: () -> Unit
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

        if (connectionState == ConnectionState.CONNECTED) {
            item {
                OtaSection(
                    otaStatus = otaStatus,
                    onOtaStart = onOtaStart,
                    onOtaCancel = onOtaCancel
                )
            }
        }

        if (currentWeightG == null) return@LazyColumn

        item {
            SectionCard {
                Text("Empty the scale, then zero it.", style = MaterialTheme.typography.bodyMedium)
                AppButton("Tare", onClick = onTare)
            }
        }

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
    }
}

/**
 * WiFi OTA: the grinder is BLE-only day-to-day, but can bring up WiFi on
 * demand to pull a new firmware binary over HTTP(S) - see include/ota.h /
 * src/ota.cpp. Credentials and the firmware URL are sent fresh every time,
 * never persisted on the app side, and only held in RAM on the grinder.
 */
@Composable
private fun OtaSection(
    otaStatus: OtaStatus?,
    onOtaStart: (ssid: String, password: String, url: String) -> Unit,
    onOtaCancel: () -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    val state = otaStatus?.state ?: OtaState.IDLE
    val inProgress = state == OtaState.CONNECTING_WIFI || state == OtaState.UPDATING

    SectionCard(label = "Firmware update (WiFi)") {
        Text(
            "WiFi is only turned on for this - the grinder stays BLE-only otherwise. Enter your WiFi network and the firmware file URL, then start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("WiFi SSID") },
            singleLine = true,
            enabled = !inProgress,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("WiFi password") },
            singleLine = true,
            enabled = !inProgress,
            // Without this, the default keyboard can autocapitalize the
            // first character or autocorrect the string, silently changing
            // the password before it's ever sent - looks identical to a
            // wrong-password WiFi auth failure on the firmware side.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Firmware .bin URL") },
            singleLine = true,
            enabled = !inProgress,
            modifier = Modifier.fillMaxWidth()
        )

        if (otaStatus != null && state != OtaState.IDLE) {
            val isError = state == OtaState.ERROR_WIFI || state == OtaState.ERROR_UPDATE || state == OtaState.ERROR_NO_CONFIG
            val color = when {
                state == OtaState.SUCCESS -> MaterialTheme.status.success
                isError -> MaterialTheme.status.danger
                else -> MaterialTheme.colorScheme.primary
            }
            val label = if (state == OtaState.UPDATING) "${state.label} ${otaStatus.progressPercent}%" else state.label
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)

            if (state == OtaState.UPDATING) {
                LinearProgressIndicator(
                    progress = { otaStatus.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state == OtaState.ERROR_UPDATE) {
                val hint = otaErrorHint(otaStatus.lastErrorCode)
                Text(
                    "Error code ${otaStatus.lastErrorCode}" + (hint?.let { " - $it" } ?: ""),
                    color = MaterialTheme.status.danger,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                "Start update",
                onClick = { onOtaStart(ssid, password, url) },
                enabled = !inProgress && ssid.isNotBlank() && url.isNotBlank()
            )
            if (state == OtaState.CONNECTING_WIFI) {
                NeutralOutlinedButton("Cancel", onClick = onOtaCancel)
            }
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
