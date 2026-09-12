package com.agung.smartgrinder.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.StatBox
import com.agung.smartgrinder.ui.components.ToggleChip
import com.agung.smartgrinder.ui.components.formatShotTime
import com.agung.smartgrinder.ui.theme.status
import kotlinx.coroutines.delay

private enum class TimerType(val label: String) { MANUAL("Manual"), AUTO("Auto-detect"), HYBRID("Hybrid") }

private const val AUTO_START_THRESHOLD_G = 0.5f
private const val SUDDEN_DROP_THRESHOLD_G = 30f

/**
 * Weight + timing for pour-overs / French Press / AeroPress etc. Purely
 * client-side (a stopwatch reacting to the always-on weight stream) - no
 * firmware timer command needed, so this works on the current protocol.
 */
@Composable
fun TimerScreen(status: GrinderStatus?) {
    var timerType by remember { mutableStateOf(TimerType.MANUAL) }
    var isRunning by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var runStartRealtime by remember { mutableStateOf(0L) }
    var baselineWeight by remember { mutableStateOf(status?.weightG ?: 0f) }
    var brewFinished by remember { mutableStateOf(false) }

    // Last weight seen while running - only tracked while running, so an
    // idle-state cup swap doesn't look like a "sudden drop" the moment Start
    // is pressed. Reset to the current weight on every run-start below.
    var lastRunningWeight by remember { mutableStateOf<Float?>(null) }
    var showFinishPrompt by remember { mutableStateOf(false) }
    var dropElapsedMs by remember { mutableStateOf(0L) } // elapsed at the instant the drop was detected

    fun startRun() {
        isRunning = true
        brewFinished = false
        lastRunningWeight = status?.weightG
    }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            runStartRealtime = SystemClock.elapsedRealtime() - elapsedMs
            while (isRunning) {
                elapsedMs = SystemClock.elapsedRealtime() - runStartRealtime
                delay(100)
            }
        }
    }

    val autoDetectEnabled = timerType == TimerType.AUTO || timerType == TimerType.HYBRID
    val currentWeight = status?.weightG
    LaunchedEffect(autoDetectEnabled, isRunning, currentWeight) {
        if (autoDetectEnabled && !isRunning && !brewFinished && !showFinishPrompt && currentWeight != null) {
            if (currentWeight - baselineWeight > AUTO_START_THRESHOLD_G) {
                startRun()
            }
        }
    }

    // Cup lifted off the scale mid-brew - ask, but keep the clock running
    // underneath (isRunning stays true) so "Belum" means the popup's own
    // open time counts too, same as if it had never paused. Only a "Ya"
    // rolls the displayed time back to dropElapsedMs, the instant the drop
    // actually happened.
    LaunchedEffect(isRunning, currentWeight, showFinishPrompt) {
        if (isRunning && !showFinishPrompt && currentWeight != null) {
            val prev = lastRunningWeight
            if (prev != null && prev - currentWeight > SUDDEN_DROP_THRESHOLD_G) {
                dropElapsedMs = elapsedMs
                showFinishPrompt = true
            } else {
                lastRunningWeight = currentWeight
            }
        }
    }

    if (showFinishPrompt) {
        AlertDialog(
            onDismissRequest = {
                showFinishPrompt = false
                lastRunningWeight = currentWeight
            },
            title = { Text("Brewing selesai?") },
            text = { Text("Beban turun drastis di ${formatShotTime(dropElapsedMs / 1000f)}. Konfirmasi brewing sudah selesai?") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishPrompt = false
                    isRunning = false
                    elapsedMs = dropElapsedMs
                    brewFinished = true
                }) { Text("Ya, selesai") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFinishPrompt = false
                    lastRunningWeight = currentWeight
                }) { Text("Belum") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Timer Mode", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Brew timing & weight tracking", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (status == null) {
            Text("Not connected - go to Settings to connect to the grinder.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        SectionCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBox(
                    "Time",
                    formatShotTime(elapsedMs / 1000f),
                    valueFontSize = 44.sp,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    monospace = true
                )
                StatBox(
                    "Weight",
                    "%.1fg".format(status.weightG),
                    valueFontSize = 44.sp,
                    valueColor = MaterialTheme.colorScheme.primary,
                    monospace = true
                )
            }

            if (brewFinished) {
                Text(
                    "Brewing selesai - ${formatShotTime(elapsedMs / 1000f)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.status.success
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (timerType != TimerType.AUTO) {
                    AppButton(
                        if (isRunning) "⏸ Pause" else "▶ Start",
                        onClick = { if (isRunning) isRunning = false else startRun() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.status.warning else MaterialTheme.status.success
                        )
                    )
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isRunning) "Running..." else "Waiting for pour ≥ ${AUTO_START_THRESHOLD_G}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                NeutralOutlinedButton(
                    "Reset",
                    onClick = {
                        isRunning = false
                        elapsedMs = 0L
                        baselineWeight = status.weightG
                        brewFinished = false
                        lastRunningWeight = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Timer type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TimerType.entries.forEach { type ->
                ToggleChip(
                    label = type.label,
                    selected = timerType == type,
                    onClick = {
                        timerType = type
                        isRunning = false
                        elapsedMs = 0L
                        baselineWeight = status.weightG
                        brewFinished = false
                        lastRunningWeight = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
