package com.aiyu.ridelink.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiyu.ridelink.ui.theme.Dimens
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/*
 * Single-series charts drawn on Canvas, following the dataviz rules: one hue (primary) for marks, the selected
 * mark in tertiary, text only in text colors, hairline solid gridlines, clean round ticks, no dual axes, no
 * legend (the card title names the series). Tap or drag to read a value in the line above the plot; every value
 * is also in the ride list, so nothing is tooltip-only.
 */

private val AXIS_WIDTH = 40.dp
private val X_LABEL_HEIGHT = 20.dp
private val TOP_PAD = 8.dp
private val BAR_MAX = 24.dp
private val BAR_RADIUS = 4.dp

/** Round step so the axis reads 0 / 5 / 10 / 15, not 0 / 4.7 / 9.4. */
internal fun niceStep(max: Double, ticks: Int = 3): Double {
    if (max <= 0) return 1.0
    val raw = max / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    return mag * when {
        n <= 1 -> 1.0
        n <= 2 -> 2.0
        n <= 2.5 -> 2.5
        n <= 5 -> 5.0
        else -> 10.0
    }
}

private class Axis(max: Double) {
    val step = niceStep(max)
    val top = if (max <= 0) step * 3 else ceil(max / step) * step
    val ticks = (0..(top / step).toInt()).map { it * step }
}

@Composable
private fun chartText(): TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun DrawScope.drawGrid(axis: Axis, measurer: TextMeasurer, style: TextStyle, grid: Color, format: (Double) -> String) {
    val plotH = size.height - X_LABEL_HEIGHT.toPx() - TOP_PAD.toPx()
    for (v in axis.ticks) {
        val y = TOP_PAD.toPx() + plotH * (1 - v / axis.top).toFloat()
        drawLine(grid, Offset(AXIS_WIDTH.toPx(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(format(v), style)
        drawText(label, topLeft = Offset(AXIS_WIDTH.toPx() - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
    }
}

/** X label centred under [cx], kept inside the canvas. */
private fun DrawScope.drawXLabel(text: String, cx: Float, measurer: TextMeasurer, style: TextStyle) {
    val label = measurer.measure(text, style)
    val x = (cx - label.size.width / 2f).coerceIn(AXIS_WIDTH.toPx(), size.width - label.size.width)
    drawText(label, topLeft = Offset(x, size.height - label.size.height))
}

/**
 * Columns from one baseline. [xLabels] may be sparse (empty strings are skipped). [describe] gives the readout
 * for a tapped column; [idle] shows when nothing is selected.
 */
@Composable
internal fun BarChart(
    values: List<Double>,
    xLabels: List<String>,
    describe: @Composable (Int) -> String,
    idle: String,
    axisFormat: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val style = chartText()
    val bar = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    fun indexAt(x: Float, width: Float, axis: Float): Int? {
        if (values.isEmpty()) return null
        val slot = (width - axis) / values.size
        return ((x - axis) / slot).toInt().takeIf { it in values.indices }
    }
    Column(modifier) {
        val pick = selected
        Text(
            if (pick != null) describe(pick) else idle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Dimens.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .pointerInput(values) { detectTapGestures { o -> selected = indexAt(o.x, size.width.toFloat(), AXIS_WIDTH.toPx()) } }
                .pointerInput(values) {
                    detectHorizontalDragGestures { change, _ -> selected = indexAt(change.position.x, size.width.toFloat(), AXIS_WIDTH.toPx()) }
                },
        ) {
            val axis = Axis(values.maxOrNull() ?: 0.0)
            drawGrid(axis, measurer, style, grid, axisFormat)
            if (values.isEmpty()) return@Canvas
            val plotH = size.height - X_LABEL_HEIGHT.toPx() - TOP_PAD.toPx()
            val base = TOP_PAD.toPx() + plotH
            val slot = (size.width - AXIS_WIDTH.toPx()) / values.size
            // Cap the bar and leave at least a 2 dp surface gap between neighbours.
            val w = min(BAR_MAX.toPx(), min(slot * 0.7f, slot - 2.dp.toPx())).coerceAtLeast(1f)
            val r = CornerRadius(min(BAR_RADIUS.toPx(), w / 2))
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (plotH * (v / axis.top)).toFloat().coerceAtLeast(2.dp.toPx())
                val left = AXIS_WIDTH.toPx() + slot * i + (slot - w) / 2
                val shape = RoundRect(Rect(left, base - h, left + w, base), topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)
                drawPath(Path().apply { addRoundRect(shape) }, if (i == selected) highlight else bar)
            }
            xLabels.forEachIndexed { i, l ->
                if (l.isNotEmpty()) drawXLabel(l, AXIS_WIDTH.toPx() + slot * (i + 0.5f), measurer, style)
            }
        }
    }
}

/**
 * Value over time: 2 dp line, 10% area wash, crosshair with a ringed dot on touch. [ring] is the card color the
 * dot's 2 dp ring is cut from.
 */
@Composable
internal fun LineChart(
    points: List<Pair<Long, Double>>,
    xLabel: (Long) -> String,
    describe: @Composable (Long, Double) -> String,
    idle: String,
    axisFormat: (Double) -> String,
    description: String,
    ring: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val style = chartText()
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val t0 = points.firstOrNull()?.first ?: 0L
    val span = ((points.lastOrNull()?.first ?: 0L) - t0).coerceAtLeast(1L)
    fun nearest(x: Float, width: Float, axis: Float): Int? {
        if (points.isEmpty()) return null
        val t = t0 + ((x - axis) / (width - axis)).coerceIn(0f, 1f) * span
        return points.indices.minByOrNull { kotlin.math.abs(points[it].first - t) }
    }
    Column(modifier) {
        val pick = selected?.takeIf { it in points.indices }
        Text(
            if (pick != null) describe(points[pick].first, points[pick].second) else idle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Dimens.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .pointerInput(points) { detectTapGestures { o -> selected = nearest(o.x, size.width.toFloat(), AXIS_WIDTH.toPx()) } }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ -> selected = nearest(change.position.x, size.width.toFloat(), AXIS_WIDTH.toPx()) }
                },
        ) {
            val axis = Axis(points.maxOfOrNull { it.second } ?: 0.0)
            drawGrid(axis, measurer, style, grid, axisFormat)
            if (points.size < 2) return@Canvas
            val plotW = size.width - AXIS_WIDTH.toPx()
            val plotH = size.height - X_LABEL_HEIGHT.toPx() - TOP_PAD.toPx()
            val base = TOP_PAD.toPx() + plotH
            fun x(t: Long) = AXIS_WIDTH.toPx() + plotW * ((t - t0).toFloat() / span)
            fun y(v: Double) = TOP_PAD.toPx() + plotH * (1 - v / axis.top).toFloat()
            val path = Path().apply {
                points.forEachIndexed { i, (t, v) -> if (i == 0) moveTo(x(t), y(v)) else lineTo(x(t), y(v)) }
            }
            val area = Path().apply {
                addPath(path)
                lineTo(x(points.last().first), base)
                lineTo(x(points.first().first), base)
                close()
            }
            drawPath(area, line.copy(alpha = 0.10f))
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            listOf(points.first().first, t0 + span / 2, points.last().first).forEach { t -> drawXLabel(xLabel(t), x(t), measurer, style) }
            selected?.let { i ->
                val (t, v) = points[i]
                drawLine(grid, Offset(x(t), TOP_PAD.toPx()), Offset(x(t), base), strokeWidth = 1.dp.toPx())
                drawCircle(ring, radius = 6.dp.toPx(), center = Offset(x(t), y(v)))
                drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(t), y(v)))
            }
        }
    }
}
