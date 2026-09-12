package com.agung.smartgrinder.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.GrinderViewModel
import com.agung.smartgrinder.ShotSample
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.*
import com.agung.smartgrinder.ui.theme.FlowLineColor
import com.agung.smartgrinder.ui.theme.WeightLineColor
import com.agung.smartgrinder.ui.theme.status

@Composable
fun EspressoScreen(
    status: GrinderStatus?,
    shotSamples: List<ShotSample>,
    onSetTargetWeight: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEStop: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Espresso Mode", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Track & pull shots", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (status == null) {
            item { Text("Not connected - go to Settings to connect to the grinder.", style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }

        item {
            SectionCard(label = "Current weight") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("%.1fg".format(status.weightG), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(status.state.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        item { YieldTargetRow(status.targetWeightG, onSetTargetWeight) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                AppButton("Pull shot", onClick = onStart, modifier = Modifier.weight(1f))
                NeutralOutlinedButton("Stop", onClick = onStop, modifier = Modifier.weight(1f))
                AppButton(
                    "E-Stop",
                    onClick = onEStop,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.status.danger)
                )
            }
        }

        item { EspressoShotSection(shotSamples) }
    }
}

@Composable
private fun YieldTargetRow(currentTarget: Float, onSet: (Float) -> Unit) {
    var text by remember(currentTarget) { mutableStateOf(currentTarget.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Target yield (g)") },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        AppButton("Set", onClick = { text.toFloatOrNull()?.let(onSet) })
    }
}

/** Combined weight + flow-rate shot graph, one time axis, two independent y-scales - matches the shot-timer layout common in espresso/pour-over apps. */
@Composable
private fun EspressoShotSection(samples: List<ShotSample>) {
    val weightPoints = remember(samples) { samples.map { it.tSeconds to it.weightG } }
    val flowPoints = remember(samples) { computeRate(weightPoints) }

    SectionCard {
        Text("Shot graph", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChartLegendItem(WeightLineColor, "Weight")
            ChartLegendItem(FlowLineColor, "Flow rate")
        }

        if (weightPoints.size < 2) {
            Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Text("Waiting for shot to start...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val maxT = weightPoints.last().first.coerceAtLeast(1f)
            val maxWeight = weightPoints.maxOf { it.second }.coerceAtLeast(1f)
            val maxFlow = (flowPoints.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
            val gridColor = MaterialTheme.colorScheme.outlineVariant

            Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                AxisLabels(maxFlow, alignEnd = false, modifier = Modifier.width(32.dp).fillMaxHeight())

                Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val steps = 4
                    for (i in 0..steps) {
                        val y = size.height * (1f - i.toFloat() / steps)
                        drawLine(gridColor.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    fun mapXt(t: Float) = (t / maxT) * size.width
                    fun mapYWeight(w: Float) = size.height - (w / maxWeight) * size.height
                    fun mapYFlow(f: Float) = size.height - (f / maxFlow) * size.height

                    val weightOffsets = weightPoints.map { (t, w) -> Offset(mapXt(t), mapYWeight(w)) }
                    drawPath(
                        buildSmoothPath(weightOffsets),
                        color = WeightLineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    val flowOffsets = flowPoints.map { (t, f) -> Offset(mapXt(t), mapYFlow(f)) }
                    drawPath(
                        buildSmoothPath(flowOffsets),
                        color = FlowLineColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                AxisLabels(maxWeight, alignEnd = true, modifier = Modifier.width(32.dp).fillMaxHeight())
            }

            Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp)) {
                val steps = 4
                for (i in 0..steps) {
                    Text(
                        formatShotTime(maxT * i / steps),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = if (i == 0) TextAlign.Start else if (i == steps) TextAlign.End else TextAlign.Center
                    )
                }
            }

            val lastW = weightPoints.last().second
            val lastF = flowPoints.lastOrNull()?.second ?: 0f
            Text(
                "%.1fg  ·  %.1fg/s  ·  %s".format(lastW, lastF, formatShotTime(weightPoints.last().first)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
