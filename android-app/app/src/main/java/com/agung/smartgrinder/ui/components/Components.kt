package com.agung.smartgrinder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 8dp corners everywhere - the mockup's buttons are rounded rectangles, not Material3's default fully-pill shape. */
val AppButtonShape = RoundedCornerShape(8.dp)

/** Filled primary/CTA button (Start, Set, Tare, Save, ...) at the mockup's corner radius. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, shape = AppButtonShape) {
        Text(text)
    }
}

/**
 * Card styled per the La Mardjono design system: bordered, rounded, section
 * label on top. [accent] gives it the mockup's "this one matters right now"
 * 2dp brand-color border, used for the active brew phase.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    label: String? = null,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            if (accent) 2.dp else 1.dp,
            if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label != null) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

/**
 * A 2-up label/value stat, used for maintenance stats, phase targets, shot
 * summaries, etc. [valueFontSize]/[valueColor]/[monospace] let call sites
 * match the mockup's oversized monospace readouts (Timer's Time/Weight)
 * without every StatBox paying for that by default.
 */
@Composable
fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = TextUnit.Unspecified,
    valueColor: Color = Color.Unspecified,
    monospace: Boolean = false
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontSize = valueFontSize,
            color = valueColor,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Nested darker panel the mockup uses to set a row of StatBoxes apart from the card around it. */
@Composable
fun StatGrid(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) { content() }
}

/**
 * Segmented pill toggle (Timer type, Scale quick portions, etc.) - selected
 * is a solid brand-color fill, unselected is a muted dark chip. Matches the
 * mockup's convention of keeping the brand accent reserved for the one
 * active choice instead of tinting every outline button with it.
 */
@Composable
fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = AppButtonShape,
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
}

/** A neutral secondary action (Stop, Disconnect, Reset, etc.) - outlined without the brand-color tint OutlinedButton uses by default. */
@Composable
fun NeutralOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = AppButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) { Text(text) }
}

@Composable
fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun AxisLabels(maxValue: Float, alignEnd: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        val steps = 4
        for (i in steps downTo 0) {
            Text("%.0f".format(maxValue * i / steps), style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun formatShotTime(tSeconds: Float): String {
    val total = tSeconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Builds a rounded curve through points via quadratic-bezier-to-midpoint segments, instead of jagged straight lines between every raw (noisy) sample. */
fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val mid = Offset((prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
        path.quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/** Derives a smoothed rate-of-change (unit/s) series from raw cumulative samples via finite differences. */
fun computeRate(samples: List<Pair<Float, Float>>, smoothWindow: Int = 6): List<Pair<Float, Float>> {
    if (samples.size < 2) return emptyList()
    val raw = (1 until samples.size).map { i ->
        val (t0, v0) = samples[i - 1]
        val (t1, v1) = samples[i]
        val dt = t1 - t0
        t1 to (if (dt > 0.01f) (v1 - v0) / dt else 0f)
    }
    return raw.mapIndexed { i, (t, _) ->
        val start = (i - smoothWindow + 1).coerceAtLeast(0)
        t to raw.subList(start, i + 1).map { it.second }.average().toFloat()
    }
}

/** Single-series mini line chart with a light grid - used for pour-rate visualization. */
@Composable
fun MiniLineChart(
    points: List<Pair<Float, Float>>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Waiting for data..."
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    if (points.size < 2) {
        Box(modifier = modifier.height(100.dp), contentAlignment = Alignment.Center) {
            Text(emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxT = points.last().first.coerceAtLeast(1f)
    val maxV = points.maxOf { it.second }.coerceAtLeast(1f)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val steps = 3
            for (i in 0..steps) {
                val y = size.height * (1f - i.toFloat() / steps)
                drawLine(gridColor.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val offsets = points.map { (t, v) ->
                Offset((t / maxT) * size.width, size.height - (v / maxV) * size.height)
            }
            drawPath(
                buildSmoothPath(offsets),
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatShotTime(0f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                formatShotTime(maxT),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}
