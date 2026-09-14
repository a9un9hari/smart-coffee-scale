package com.agung.smartgrinder.ui.screens

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.ToggleChip
import com.agung.smartgrinder.ui.components.formatShotTime
import com.agung.smartgrinder.ui.theme.status
import kotlinx.coroutines.delay

private enum class TimerType(val label: String) { MANUAL("Manual"), AUTO("Auto-detect") }

private const val AUTO_START_THRESHOLD_G = 0.5f
private const val AUTO_STOP_THRESHOLD_G = 0.2f

/**
 * Weight + timing for pour-overs / French Press / AeroPress etc. Purely
 * client-side (a stopwatch reacting to the always-on weight stream) - no
 * firmware timer command needed, so this works on the current protocol.
 * Start/Pause stay manually operable in both modes - Auto-detect only adds
 * automatic start/stop on top, it never takes control away.
 */
@Composable
fun TimerScreen(status: GrinderStatus?, onTare: () -> Unit) {
    var timerType by remember { mutableStateOf(TimerType.MANUAL) }
    var isRunning by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var runStartRealtime by remember { mutableStateOf(0L) }
    var baselineWeight by remember { mutableStateOf(status?.weightG ?: 0f) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            runStartRealtime = SystemClock.elapsedRealtime() - elapsedMs
            while (isRunning) {
                elapsedMs = SystemClock.elapsedRealtime() - runStartRealtime
                delay(100)
            }
        }
    }

    val currentWeight = status?.weightG
    val pouredWeight = (currentWeight ?: 0f) - baselineWeight

    // Auto-detect only decides start/stop - the buttons below still work
    // normally the whole time, so the person brewing can always override it.
    LaunchedEffect(timerType, isRunning, pouredWeight) {
        if (timerType == TimerType.AUTO && currentWeight != null) {
            if (!isRunning && pouredWeight > AUTO_START_THRESHOLD_G) {
                isRunning = true
            } else if (isRunning && pouredWeight < AUTO_STOP_THRESHOLD_G) {
                isRunning = false
            }
        }
    }

    fun tareAndReset() {
        onTare()
        isRunning = false
        elapsedMs = 0L
        baselineWeight = 0f
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        val readoutSize = if (isLandscape) 48.sp else 56.sp

        if (isLandscape) {
            // Tare sits in its own column, height-matched to the Weight card
            // + Pause button stacked beside it (same trick as ScaleScreen) -
            // reachable without scrolling instead of sitting in a row below
            // everything else.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(label = "Time") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            TimeReadout(elapsedMs, readoutSize)
                        }
                    }
                    AppButton(
                        "▶ Start",
                        onClick = { isRunning = true },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.success)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(label = "Weight") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            WeightReadout(status.weightG, readoutSize)
                        }
                    }
                    AppButton(
                        "⏸ Pause",
                        onClick = { isRunning = false },
                        enabled = isRunning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.warning)
                    )
                }
                AppButton(
                    "↺ TARE",
                    onClick = { tareAndReset() },
                    modifier = Modifier.weight(0.6f).fillMaxHeight()
                )
            }
        } else {
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        TimeReadout(elapsedMs, readoutSize)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("WEIGHT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        WeightReadout(status.weightG, readoutSize)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AppButton(
                    "▶ Start",
                    onClick = { isRunning = true },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.success)
                )
                AppButton(
                    "⏸ Pause",
                    onClick = { isRunning = false },
                    enabled = isRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.warning)
                )
            }

            AppButton("↺ TARE", onClick = { tareAndReset() }, modifier = Modifier.fillMaxWidth())
        }

        Text("Timer type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TimerType.entries.forEach { type ->
                ToggleChip(
                    label = type.label,
                    selected = timerType == type,
                    onClick = { timerType = type },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                "Manual: klik Start/Pause sendiri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Auto: mulai di atas ${AUTO_START_THRESHOLD_G}g • berhenti di bawah ${AUTO_STOP_THRESHOLD_G}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeReadout(elapsedMs: Long, fontSize: TextUnit) {
    Text(
        formatShotTime(elapsedMs / 1000f),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun WeightReadout(weightG: Float, fontSize: TextUnit) {
    Text(
        "%.1fg".format(weightG),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary
    )
}
