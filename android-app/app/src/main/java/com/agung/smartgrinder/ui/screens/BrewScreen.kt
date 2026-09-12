package com.agung.smartgrinder.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.MiniLineChart
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.StatBox
import com.agung.smartgrinder.ui.components.StatGrid
import com.agung.smartgrinder.ui.components.computeRate
import com.agung.smartgrinder.ui.components.formatShotTime
import com.agung.smartgrinder.ui.theme.CoffeeBrown
import com.agung.smartgrinder.ui.theme.status
import kotlinx.coroutines.delay

/**
 * One step of a brew method. [targetWeightG] is the cumulative scale reading
 * expected by the end of this phase (null when the phase doesn't involve
 * pouring, e.g. AeroPress's press or a Moka pot brewing on the stove).
 *
 * Numbers sourced from docs/expanded/EXPANDED-MODES-AND-BREW-ASSIST.md's
 * "Multi-Phase Guidance System" section - don't re-derive them from scratch.
 */
data class BrewPhase(val name: String, val durationSec: Int, val targetWeightG: Float?, val guidance: String)
data class BrewMethod(val label: String, val phases: List<BrewPhase>, val flowZoneMlPerS: ClosedFloatingPointRange<Float>?)

val BrewMethods = listOf(
    BrewMethod(
        "V60", listOf(
            BrewPhase("Bloom", 45, 60f, "Pour slowly to saturate the grounds evenly"),
            BrewPhase("Main Pour", 165, 300f, "Pour steadily, keep flow in the green zone"),
            BrewPhase("Finish", 30, 500f, "Slow, gentle final pour")
        ), 3.0f..5.0f
    ),
    BrewMethod(
        "French Press", listOf(
            BrewPhase("Bloom", 30, 100f, "Initial pour, let it bloom"),
            BrewPhase("Full", 240, 500f, "Steep and complete the pour")
        ), 0.5f..2.0f
    ),
    BrewMethod(
        "AeroPress", listOf(
            BrewPhase("Pour", 60, 200f, "Fill the chamber steadily"),
            BrewPhase("Press", 30, null, "Plunge slowly and steadily")
        ), 2.0f..4.0f
    ),
    BrewMethod(
        "Chemex", listOf(
            BrewPhase("Bloom", 45, 100f, "Pour slowly to saturate the grounds evenly"),
            BrewPhase("Main Pour", 180, 400f, "Pour steadily in circles"),
            BrewPhase("Finish", 30, 650f, "Slow, gentle final pour")
        ), 2.5f..4.0f
    ),
    BrewMethod("Turkish", listOf(BrewPhase("Brew", 120, 200f, "Continuous medium heat")), null),
    BrewMethod("Moka Pot", listOf(BrewPhase("Brew", 600, null, "Watch until coffee comes through")), null),
)

@Composable
fun BrewScreen(status: GrinderStatus?) {
    var method by remember { mutableStateOf<BrewMethod?>(null) }
    var phaseIndex by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var phaseElapsedMs by remember { mutableStateOf(0L) }
    var phaseRunStartRealtime by remember { mutableStateOf(0L) }
    var priorPhasesElapsedSec by remember { mutableStateOf(0f) }
    var samples by remember { mutableStateOf(listOf<Pair<Float, Float>>()) } // (elapsedSec since brew start, weightG)

    val currentPhase = method?.phases?.getOrNull(phaseIndex)
    val brewDone = method != null && phaseIndex >= method!!.phases.size

    fun reset() {
        phaseIndex = 0
        isRunning = false
        phaseElapsedMs = 0L
        priorPhasesElapsedSec = 0f
        samples = emptyList()
    }

    // Phase timer + auto-advance
    LaunchedEffect(isRunning, phaseIndex, method) {
        val phase = currentPhase ?: return@LaunchedEffect
        if (isRunning) {
            phaseRunStartRealtime = SystemClock.elapsedRealtime() - phaseElapsedMs
            while (isRunning) {
                phaseElapsedMs = SystemClock.elapsedRealtime() - phaseRunStartRealtime
                if (phaseElapsedMs / 1000f >= phase.durationSec) {
                    priorPhasesElapsedSec += phase.durationSec
                    phaseIndex += 1
                    phaseElapsedMs = 0L
                    break
                }
                delay(100)
            }
        }
    }

    // Sample the weight stream while a brew is running, for the pour-rate chart.
    val currentWeight = status?.weightG
    LaunchedEffect(isRunning, currentWeight) {
        if (isRunning && currentWeight != null) {
            val t = priorPhasesElapsedSec + phaseElapsedMs / 1000f
            samples = samples + (t to currentWeight)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Manual Brew", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("Professional brew guidance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (status == null) {
            Text("Not connected - go to Settings to connect to the grinder.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        SectionCard {
            var expanded by remember { mutableStateOf(false) }
            Box {
                NeutralOutlinedButton(method?.label ?: "Select brew method...", onClick = { expanded = true }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BrewMethods.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = {
                            method = m
                            reset()
                            expanded = false
                        })
                    }
                }
            }
        }

        if (method == null) return@Column

        if (brewDone) {
            SectionCard(label = "Brew complete") {
                Text("${method!!.label} finished - total ${formatShotTime(priorPhasesElapsedSec)}, ${"%.0f".format(currentWeight ?: 0f)}g in the cup.")
                AppButton("Start another", onClick = { reset() })
            }
            return@Column
        }

        val phase = currentPhase!!
        val prevTarget = if (phaseIndex == 0) 0f else method!!.phases[phaseIndex - 1].targetWeightG ?: 0f

        SectionCard(label = "Phase ${phaseIndex + 1} of ${method!!.phases.size}", accent = true) {
            Text(phase.name, style = MaterialTheme.typography.titleLarge)
            Text(phase.guidance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            StatGrid {
                StatBox("Target Weight", phase.targetWeightG?.let { "%.0fg".format(it) } ?: "—")
                StatBox("Time", "${formatShotTime(phaseElapsedMs / 1000f)} / ${formatShotTime(phase.durationSec.toFloat())}")
            }

            val guidanceBanner = paceGuidance(
                phase = phase,
                prevTargetG = prevTarget,
                elapsedSec = phaseElapsedMs / 1000f,
                currentWeightG = currentWeight
            )
            if (guidanceBanner != null) {
                val (text, color) = guidanceBanner
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(8.dp)
                ) {
                    Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AppButton(
                    if (isRunning) "⏸ Pause" else "▶ Start",
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.status.warning else MaterialTheme.status.success
                    )
                )
                NeutralOutlinedButton(
                    "Skip phase",
                    onClick = {
                        priorPhasesElapsedSec += phase.durationSec
                        phaseIndex += 1
                        phaseElapsedMs = 0L
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCard(label = "Pour rate") {
            MiniLineChart(points = computeRate(samples), lineColor = CoffeeBrown, emptyLabel = "Start the brew to see pour rate")
        }
    }
}

/** Compares actual cumulative weight against the phase's expected pace and returns a (message, color) banner, or null when there's nothing to say. */
@Composable
private fun paceGuidance(phase: BrewPhase, prevTargetG: Float, elapsedSec: Float, currentWeightG: Float?): Pair<String, androidx.compose.ui.graphics.Color>? {
    val target = phase.targetWeightG ?: return null
    val weight = currentWeightG ?: return null
    val timeRatio = (elapsedSec / phase.durationSec).coerceIn(0f, 1f)
    val expected = prevTargetG + (target - prevTargetG) * timeRatio
    val tolerance = (target - prevTargetG) * 0.15f
    val delta = weight - expected

    return when {
        delta > tolerance -> "Pouring too fast" to MaterialTheme.status.warning
        delta < -tolerance -> "Pouring too slow" to MaterialTheme.status.warning
        else -> "On pace" to MaterialTheme.status.success
    }
}
