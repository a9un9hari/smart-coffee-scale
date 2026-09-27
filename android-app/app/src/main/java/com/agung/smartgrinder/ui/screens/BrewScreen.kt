package com.agung.smartgrinder.ui.screens

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agung.smartgrinder.BrewLogData
import com.agung.smartgrinder.BrewLogSample
import com.agung.smartgrinder.BrewLogSegment
import com.agung.smartgrinder.ble.GrinderStatus
import com.agung.smartgrinder.ui.components.AppButton
import com.agung.smartgrinder.ui.components.NeutralOutlinedButton
import com.agung.smartgrinder.ui.components.SectionCard
import com.agung.smartgrinder.ui.components.buildSmoothPath
import com.agung.smartgrinder.ui.components.computeRate
import com.agung.smartgrinder.ui.components.formatShotTime
import com.agung.smartgrinder.ui.theme.status
import kotlinx.coroutines.delay

private val BrewMethodNames = listOf("V60", "French Press", "AeroPress", "Chemex", "Turkish", "Moka Pot")

private const val RATIO_MIN = 5f
private const val RATIO_MAX = 25f
private const val RATIO_STEP = 0.5f
private const val DEFAULT_RATIO = 16f
private const val DEFAULT_COFFEE_G = 30f
private const val POUR_FLOW_THRESHOLD_G_PER_S = 0.3f // below this counts as "paused" for segment tagging
private const val MIN_SEGMENT_WEIGHT_G = 1f // a "pour" under this is sensor noise on a flat stretch, not a real pour
private const val FIRST_POUR_THRESHOLD_G = 0.5f // timer/graph don't start until the pour actually begins
private const val SUDDEN_DROP_WARNING_G = 10f // a same-tick drop this big smells like a Tare from another tab, not a real pour
private const val FLOW_MAX_G_PER_S = 6f // chart's flow-line full-scale reference, not a hard cap

private data class BrewSegment(val label: String, val startSec: Float, val endSec: Float, val weightG: Float)

/**
 * Ratio-driven manual brew: no fixed per-method phase durations - the person
 * brewing sets a ratio, pours however they like, and the app segments the
 * pour into "Bloom"/"Pour N" after the fact from the flow-rate behavior
 * (flow drops to ~0, then picks back up = a new segment), rather than
 * enforcing a schedule. Matches docs/UI/mode/manual_brew.html.
 */
@Composable
fun BrewScreen(status: GrinderStatus?, onTare: () -> Unit, onBrewComplete: (BrewLogData) -> Unit) {
    var methodLabel by remember { mutableStateOf(BrewMethodNames.first()) }
    var coffeeWeight by remember { mutableStateOf(DEFAULT_COFFEE_G) }
    var ratio by remember { mutableStateOf(DEFAULT_RATIO) }
    val waterTargetG = coffeeWeight * ratio

    var isBrewing by remember { mutableStateOf(false) }
    // Start Brewing arms the session (baseline captured, ready to detect a
    // pour) but the clock/graph/segments don't begin until the first real
    // pour crosses FIRST_POUR_THRESHOLD_G - matches how a barista actually
    // starts a timer (water hits the grounds), not when the button is
    // tapped a few seconds earlier while still getting the kettle ready.
    var timerStarted by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var brewStartRealtime by remember { mutableStateOf(0L) }
    var baselineWeight by remember { mutableStateOf(0f) }
    var samples by remember { mutableStateOf(listOf<Pair<Float, Float>>()) } // (elapsedSec, pouredWaterG)
    var segments by remember { mutableStateOf(listOf<BrewSegment>()) }
    var currentSegmentStart by remember { mutableStateOf<Pair<Float, Float>?>(null) } // (startSec, startWeight) while actively pouring
    var showTareWarning by remember { mutableStateOf(false) }
    var lastPouredForDropCheck by remember { mutableStateOf(0f) }

    val currentWeight = status?.weightG
    val pouredWaterG = ((currentWeight ?: 0f) - baselineWeight).coerceAtLeast(0f)

    // Ground coffee tracks the live scale reading while still on Setup -
    // place the grounds on the scale and the number just follows. It stops
    // following (freezing at whatever it last read) the moment brewing
    // starts, since the same weight channel then means water poured, not
    // coffee.
    LaunchedEffect(currentWeight, isBrewing) {
        if (!isBrewing && currentWeight != null) {
            coffeeWeight = currentWeight
        }
    }

    // Arms as soon as Start Brewing is tapped, but doesn't flip timerStarted
    // until the scale actually shows a pour - see the field's own comment.
    LaunchedEffect(isBrewing, timerStarted, pouredWaterG) {
        if (isBrewing && !timerStarted && pouredWaterG > FIRST_POUR_THRESHOLD_G) {
            timerStarted = true
            elapsedMs = 0L
            currentSegmentStart = 0f to 0f // Bloom starts counting from true zero, not from the threshold crossing
        }
    }

    LaunchedEffect(isBrewing, timerStarted) {
        if (isBrewing && timerStarted) {
            brewStartRealtime = SystemClock.elapsedRealtime()
            while (isBrewing && timerStarted) {
                elapsedMs = SystemClock.elapsedRealtime() - brewStartRealtime
                delay(100)
            }
        }
    }

    LaunchedEffect(isBrewing, timerStarted, currentWeight) {
        if (isBrewing && timerStarted && currentWeight != null) {
            samples = samples + (elapsedMs / 1000f to pouredWaterG)
        }
    }

    // A same-tick drop this large mid-brew is not a real pour dynamic - almost
    // certainly the scale got tared from another tab (Scale/Settings/Timer),
    // which this screen's baselineWeight has no way to know about on its own.
    // Warns instead of auto-stopping - the brew itself may still be fine.
    LaunchedEffect(isBrewing, timerStarted, pouredWaterG) {
        if (isBrewing && timerStarted) {
            if (lastPouredForDropCheck - pouredWaterG > SUDDEN_DROP_WARNING_G) {
                showTareWarning = true
            }
            lastPouredForDropCheck = pouredWaterG
        }
    }

    val flowRate = computeRate(samples).lastOrNull()?.second ?: 0f

    // Edge-detects pour segments from the flow-rate history: a segment opens
    // when flow rises past the threshold and closes (gets its final label,
    // "Bloom" for the first one, "Pour N" after) when it drops back down. A
    // segment under MIN_SEGMENT_WEIGHT_G is sensor noise on an otherwise flat
    // stretch, not a real pour - dropped instead of logged.
    LaunchedEffect(isBrewing, timerStarted, flowRate) {
        if (!isBrewing || !timerStarted) return@LaunchedEffect
        val t = elapsedMs / 1000f
        val pouring = flowRate > POUR_FLOW_THRESHOLD_G_PER_S
        val started = currentSegmentStart
        if (pouring && started == null) {
            currentSegmentStart = t to pouredWaterG
        } else if (!pouring && started != null) {
            val (startT, startW) = started
            val weightG = pouredWaterG - startW
            if (weightG > MIN_SEGMENT_WEIGHT_G) {
                val label = if (segments.isEmpty()) "Bloom" else "Pour ${segments.size}"
                segments = segments + BrewSegment(label, startT, t, weightG)
            }
            currentSegmentStart = null
        }
    }

    fun startBrewing() {
        baselineWeight = currentWeight ?: 0f
        elapsedMs = 0L
        samples = emptyList()
        segments = emptyList()
        currentSegmentStart = null
        showTareWarning = false
        lastPouredForDropCheck = 0f
        timerStarted = false
        isBrewing = true
    }

    fun stopBrewing() {
        // Only worth a log entry if some actual pouring happened - an
        // accidental Start-then-immediately-Stop shouldn't clutter the file.
        if (samples.size >= 2) {
            onBrewComplete(
                BrewLogData(
                    methodLabel = methodLabel,
                    coffeeWeightG = coffeeWeight,
                    ratio = ratio,
                    waterTargetG = waterTargetG,
                    finalWaterG = pouredWaterG,
                    durationSec = elapsedMs / 1000f,
                    samples = samples.map { (t, w) -> BrewLogSample(t, w) },
                    segments = segments.map { BrewLogSegment(it.label, it.startSec, it.endSec, it.weightG) }
                )
            )
        }
        isBrewing = false
        currentSegmentStart = null
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bigReadout = if (isLandscape) 20.sp else 28.sp
    val ratioReadout = if (isLandscape) 22.sp else 32.sp

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isBrewing) "Brewing..." else "Manual Brew",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (!isBrewing) {
                Text("Setup & brew", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (status == null) {
            Text("Not connected - go to Settings to connect to the grinder.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        if (!isBrewing) {
            BrewSetup(
                methodLabel = methodLabel,
                onSelectMethod = { methodLabel = it },
                coffeeWeight = coffeeWeight,
                onTare = onTare,
                ratio = ratio,
                onRatioChange = { ratio = it },
                waterTargetG = waterTargetG,
                bigReadout = bigReadout,
                ratioReadout = ratioReadout,
                onStart = { startBrewing() }
            )
        } else {
            BrewLive(
                timerStarted = timerStarted,
                elapsedMs = elapsedMs,
                liveRatio = if (coffeeWeight > 0f) pouredWaterG / coffeeWeight else 0f,
                pouredWaterG = pouredWaterG,
                waterTargetG = waterTargetG,
                showTareWarning = showTareWarning,
                onDismissTareWarning = { showTareWarning = false },
                samples = samples,
                segments = segments,
                bigReadout = bigReadout,
                ratioReadout = ratioReadout,
                onStop = { stopBrewing() }
            )
        }
    }
}

@Composable
private fun BrewSetup(
    methodLabel: String,
    onSelectMethod: (String) -> Unit,
    coffeeWeight: Float,
    onTare: () -> Unit,
    ratio: Float,
    onRatioChange: (Float) -> Unit,
    waterTargetG: Float,
    bigReadout: TextUnit,
    ratioReadout: TextUnit,
    onStart: () -> Unit
) {
    var methodExpanded by remember { mutableStateOf(false) }
    Column {
        Text("Brew method", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Box {
            NeutralOutlinedButton(methodLabel, onClick = { methodExpanded = true }, modifier = Modifier.fillMaxWidth())
            DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                BrewMethodNames.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onSelectMethod(name); methodExpanded = false })
                }
            }
        }
    }

    // Empty vessel on the scale, Tare, then add coffee - without this step
    // "ground coffee" ends up including the dripper/cup's own weight.
    NeutralOutlinedButton("↺ Tare (empty vessel first)", onClick = onTare, modifier = Modifier.fillMaxWidth())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "💡 Insert your ground coffee - the reading below follows the scale live",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledReadout("COFFEE", "%.0fg".format(coffeeWeight), bigReadout, MaterialTheme.colorScheme.onSurface)
            LabeledReadout("RATIO", "1:%.1f".format(ratio), ratioReadout, MaterialTheme.colorScheme.primary)
            LabeledReadout("WATER", "%.0fg".format(waterTargetG), bigReadout, MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Slider(value = ratio, onValueChange = onRatioChange, valueRange = RATIO_MIN..RATIO_MAX, steps = ((RATIO_MAX - RATIO_MIN) / RATIO_STEP).toInt() - 1)
        Text(
            "↑ Adjust ratio ↓",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }

    AppButton("▶ Start Brewing", onClick = onStart, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun BrewLive(
    timerStarted: Boolean,
    elapsedMs: Long,
    liveRatio: Float,
    pouredWaterG: Float,
    waterTargetG: Float,
    showTareWarning: Boolean,
    onDismissTareWarning: () -> Unit,
    samples: List<Pair<Float, Float>>,
    segments: List<BrewSegment>,
    bigReadout: TextUnit,
    ratioReadout: TextUnit,
    onStop: () -> Unit
) {
    if (!timerStarted) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                "⏳ Armed - timer starts the moment you pour",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showTareWarning) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.status.warning.copy(alpha = 0.15f))
                .clickable(onClick = onDismissTareWarning)
                .padding(12.dp)
        ) {
            Text(
                "⚠️ Weight dropped suddenly - was the scale tared from another tab? Tap to dismiss.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.status.warning
            )
        }
    }

    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LabeledReadout("TIME", if (timerStarted) formatShotTime(elapsedMs / 1000f) else "--:--", bigReadout, MaterialTheme.colorScheme.onSurface)
            LabeledReadout("RATIO", "1:%.1f".format(liveRatio), ratioReadout, MaterialTheme.colorScheme.primary)
            LabeledReadout("WATER", "%.0fg".format(pouredWaterG), bigReadout, MaterialTheme.colorScheme.primary)
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "%.0f/%.0fg".format(pouredWaterG, waterTargetG),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (waterTargetG > 0f) (pouredWaterG / waterTargetG).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp))
        )
    }

    SectionCard(label = "Weight & flow") {
        WeightFlowChart(samples = samples, waterTargetG = waterTargetG)
    }

    SectionCard(label = "Segments") {
        if (segments.isEmpty()) {
            Text("No segment detected yet - pour, then pause briefly to close one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            segments.forEachIndexed { i, seg ->
                val color = if (i == 0) MaterialTheme.status.success else MaterialTheme.status.warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(color))
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(seg.label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${formatShotTime(seg.startSec)}-${formatShotTime(seg.endSec)} • %.0fg".format(seg.weightG),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    NeutralOutlinedButton("⏹ Stop", onClick = onStop, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LabeledReadout(label: String, value: String, fontSize: TextUnit, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = fontSize, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = color)
    }
}

/** Two independently-normalized lines (cumulative water in amber, flow rate in cyan) sharing one canvas - visual pattern matters more than a shared absolute scale. */
@Composable
private fun WeightFlowChart(samples: List<Pair<Float, Float>>, waterTargetG: Float) {
    if (samples.size < 2) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("Start pouring to see weight & flow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val weightColor = MaterialTheme.colorScheme.primary
    val flowColor = MaterialTheme.status.success
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val maxT = samples.last().first.coerceAtLeast(1f)
    val maxWeight = waterTargetG.coerceAtLeast(samples.maxOf { it.second }).coerceAtLeast(1f)
    val flowSamples = computeRate(samples)

    Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        val steps = 3
        for (i in 0..steps) {
            val y = size.height * (1f - i.toFloat() / steps)
            drawLine(gridColor.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        val weightOffsets = samples.map { (t, w) -> Offset((t / maxT) * size.width, size.height - (w / maxWeight).coerceIn(0f, 1f) * size.height) }
        drawPath(buildSmoothPath(weightOffsets), color = weightColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (flowSamples.size >= 2) {
            val flowOffsets = flowSamples.map { (t, r) -> Offset((t / maxT) * size.width, size.height - (r.coerceAtLeast(0f) / FLOW_MAX_G_PER_S).coerceIn(0f, 1f) * size.height) }
            drawPath(buildSmoothPath(flowOffsets), color = flowColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
