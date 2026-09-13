package com.agung.smartgrinder.ui.screens

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.computeRate
import com.agung.smartgrinder.ui.theme.status

private const val FLOW_RATE_WINDOW_SEC = 3f // how far back the flow-rate average looks
private const val FLOW_RATE_MAX_G_PER_S = 6f // bar's full-scale reference, not a hard cap

/**
 * Simple weighing, no timing. Firmware has no separate "Scale mode" - this
 * just reads the always-on weight stream (plus a short client-side rolling
 * window for flow rate) and issues Tare, so it works no matter what grinder
 * mode is currently active.
 */
@Composable
fun ScaleScreen(status: GrinderStatus?, onTare: () -> Unit) {
    var samples by remember { mutableStateOf(listOf<Pair<Float, Float>>()) } // (elapsedSec, weightG)
    val startRealtime = remember { SystemClock.elapsedRealtime() }

    val currentWeight = status?.weightG
    LaunchedEffect(currentWeight) {
        if (currentWeight != null) {
            val t = (SystemClock.elapsedRealtime() - startRealtime) / 1000f
            samples = (samples + (t to currentWeight)).filter { t - it.first <= FLOW_RATE_WINDOW_SEC }
        }
    }
    val flowRate = computeRate(samples).lastOrNull()?.second ?: 0f

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        if (isLandscape) {
            // Tare sits in its own column on the right, not a full-width row
            // below the cards - on real (short) landscape height that row
            // was getting clipped by the bottom nav.
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)
            ) {
                SectionCard(label = "Weight", modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        WeightReadout(status.weightG, fontSize = 48.sp)
                        Text("±0.05g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionCard(label = "Flow rate", modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        FlowRateReadout(flowRate, fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        FlowRateBar(flowRate)
                    }
                }
                AppButton("↺ TARE", onClick = onTare, modifier = Modifier.weight(0.7f).fillMaxHeight())
            }
        } else {
            SectionCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "CURRENT WEIGHT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    WeightReadout(status.weightG, fontSize = 64.sp)
                    Text(
                        "±0.05g precision",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    AppButton("↺ TARE", onClick = onTare, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "FLOW RATE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRateReadout(flowRate, fontSize = 36.sp)
                    Spacer(Modifier.height(12.dp))
                    FlowRateBar(flowRate)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                "💡 Auto-detects pour start • Real-time flow rate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightReadout(weightG: Float, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        "%.1fg".format(weightG),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun FlowRateReadout(flowRateGPerS: Float, fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "%.1f".format(flowRateGPerS.coerceAtLeast(0f)),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Text("g/s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Track stays dark; only the filled portion shows the slow->ideal->fast gradient, same as the mockup. */
@Composable
private fun FlowRateBar(flowRateGPerS: Float) {
    val fraction = (flowRateGPerS.coerceAtLeast(0f) / FLOW_RATE_MAX_G_PER_S).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(MaterialTheme.status.success, MaterialTheme.status.warning, MaterialTheme.status.danger)))
        )
    }
}
