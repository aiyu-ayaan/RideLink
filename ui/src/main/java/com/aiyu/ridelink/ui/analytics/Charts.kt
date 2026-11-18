package com.aiyu.ridelink.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.aiyu.ridelink.core.analytics.MotionSpan
import com.aiyu.ridelink.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/*
 * Single-series charts drawn on Canvas, following the dataviz rules: one hue (primary) for marks, the selected
 * mark in tertiary, text only in text colors, hairline solid gridlines, clean round ticks, no dual axes, no
 * legend (the card title names the series). Press or drag along a chart to read a value, shown at the mark and in the
 * line above the plot; every value is also in the ride list, so nothing is tooltip-only.
 */

private val AXIS_WIDTH = 40.dp
private val X_LABEL_HEIGHT = 20.dp
/** Leaves room for the value label over the tallest mark. */
private val TOP_PAD = 28.dp
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
 * Selects on press and follows a horizontal drag. A vertical drag is left to the page, so charts never block scrolling.
 */
private fun Modifier.scrub(key: Any?, onX: (x: Float, width: Float, axis: Float) -> Unit) = pointerInput(key) {
    val axis = AXIS_WIDTH.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onX(down.position.x, size.width.toFloat(), axis)
        var moved = Offset.Zero
        var scrubbing = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || change.isConsumed) break
            moved += change.positionChange()
            if (!scrubbing) {
                if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) > abs(moved.x)) break
                scrubbing = abs(moved.x) > viewConfiguration.touchSlop
            }
            if (scrubbing) {
                change.consume()
                onX(change.position.x, size.width.toFloat(), axis)
            }
        }
    }
}

/** The selected value in a pill just above [top], kept inside the canvas. */
private fun DrawScope.drawValueLabel(text: String, cx: Float, top: Float, measurer: TextMeasurer, style: TextStyle, background: Color) {
    val label = measurer.measure(text, style)
    val padX = 8.dp.toPx()
    val padY = 3.dp.toPx()
    val w = label.size.width + padX * 2
    val h = label.size.height + padY * 2
    val x = (cx - w / 2).coerceIn(AXIS_WIDTH.toPx(), (size.width - w).coerceAtLeast(AXIS_WIDTH.toPx()))
    val y = (top - h - 4.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(background, Offset(x, y), Size(w, h), CornerRadius(h / 2))
    drawText(label, topLeft = Offset(x + padX, y + padY))
}

/**
 * Columns from one baseline. [xLabels] may be sparse (empty strings are skipped). [describe] gives the readout
 * for the selected column, [valueLabel] the text at its mark, and [footer] an action for it; [idle] shows when
 * nothing is selected.
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
    valueLabel: (Int) -> String = { axisFormat(values[it]) },
    footer: @Composable (Int) -> Unit = {},
) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val style = chartText()
    val c = MaterialTheme.colorScheme
    val labelStyle = style.copy(color = c.inverseOnSurface)
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        val pick = selected?.takeIf { it in values.indices }
        Text(
            if (pick != null) describe(pick) else idle,
            style = MaterialTheme.typography.titleSmall,
            color = c.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Dimens.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .scrub(values) { x, width, axis ->
                    if (values.isEmpty()) return@scrub
                    val i = ((x - axis) / ((width - axis) / values.size)).toInt().coerceIn(values.indices)
                    if (i != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selected = i
                },
        ) {
            val axis = Axis(values.maxOrNull() ?: 0.0)
            drawGrid(axis, measurer, style, c.outlineVariant, axisFormat)
            if (values.isEmpty()) return@Canvas
            val plotH = size.height - X_LABEL_HEIGHT.toPx() - TOP_PAD.toPx()
            val base = TOP_PAD.toPx() + plotH
            val slot = (size.width - AXIS_WIDTH.toPx()) / values.size
            // Cap the bar and leave at least a 2 dp surface gap between neighbours.
            val w = min(BAR_MAX.toPx(), min(slot * 0.7f, slot - 2.dp.toPx())).coerceAtLeast(1f)
            val r = CornerRadius(min(BAR_RADIUS.toPx(), w / 2))
            fun barHeight(v: Double) = if (v <= 0) 0f else (plotH * (v / axis.top)).toFloat().coerceAtLeast(2.dp.toPx())
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = barHeight(v)
                val left = AXIS_WIDTH.toPx() + slot * i + (slot - w) / 2
                val shape = RoundRect(Rect(left, base - h, left + w, base), topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)
                drawPath(Path().apply { addRoundRect(shape) }, if (i == pick) c.tertiary else c.primary)
            }
            xLabels.forEachIndexed { i, l ->
                if (l.isNotEmpty()) drawXLabel(l, AXIS_WIDTH.toPx() + slot * (i + 0.5f), measurer, style)
            }
            pick?.let { i ->
                drawValueLabel(valueLabel(i), AXIS_WIDTH.toPx() + slot * (i + 0.5f), base - barHeight(values[i]), measurer, labelStyle, c.inverseSurface)
            }
        }
        pick?.let { footer(it) }
    }
}

/**
 * Value over time: 2 dp line, 10% area wash, crosshair with a ringed dot on touch. [ring] is the card color the
 * dot's 2 dp ring is cut from. Pass [selectedTime] and [onSelectTime] to move the crosshair of several charts together.
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
    selectedTime: Long? = null,
    onSelectTime: ((Long) -> Unit)? = null,
    valueLabel: (Double) -> String = axisFormat,
    footer: @Composable (Long) -> Unit = {},
) {
    var own by remember(points) { mutableStateOf<Long?>(null) }
    val time = if (onSelectTime != null) selectedTime else own
    val select by rememberUpdatedState(onSelectTime ?: { t: Long -> own = t })
    val measurer = rememberTextMeasurer()
    val style = chartText()
    val c = MaterialTheme.colorScheme
    val labelStyle = style.copy(color = c.inverseOnSurface)
    val haptics = LocalHapticFeedback.current
    val line = c.primary
    val t0 = points.firstOrNull()?.first ?: 0L
    val span = ((points.lastOrNull()?.first ?: 0L) - t0).coerceAtLeast(1L)
    fun nearest(t: Long) = points.indices.minByOrNull { abs(points[it].first - t) }
    Column(modifier) {
        val pick = time?.let(::nearest)
        Text(
            if (pick != null) describe(points[pick].first, points[pick].second) else idle,
            style = MaterialTheme.typography.titleSmall,
            color = c.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Dimens.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .scrub(points) { x, width, axis ->
                    val i = nearest(t0 + (((x - axis) / (width - axis)).coerceIn(0f, 1f) * span).toLong()) ?: return@scrub
                    if (points[i].first != time) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    select(points[i].first)
                },
        ) {
            val axis = Axis(points.maxOfOrNull { it.second } ?: 0.0)
            drawGrid(axis, measurer, style, c.outlineVariant, axisFormat)
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
            pick?.let { i ->
                val (t, v) = points[i]
                drawLine(c.outline, Offset(x(t), TOP_PAD.toPx()), Offset(x(t), base), strokeWidth = 1.dp.toPx())
                drawCircle(ring, radius = 6.dp.toPx(), center = Offset(x(t), y(v)))
                drawCircle(c.tertiary, radius = 4.dp.toPx(), center = Offset(x(t), y(v)))
                drawValueLabel(valueLabel(v), x(t), y(v) - 6.dp.toPx(), measurer, labelStyle, c.inverseSurface)
            }
        }
        pick?.let { footer(points[it].first) }
    }
}

/** The ride as one strip of moving and stopped stretches; shares its selection with the charts below it. */
@Composable
internal fun MotionTimeline(
    spans: List<MotionSpan>,
    selectedTime: Long?,
    onSelectTime: (Long) -> Unit,
    describe: @Composable (MotionSpan) -> String,
    idle: String,
    description: String,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
) {
    if (spans.isEmpty()) return
    val c = MaterialTheme.colorScheme
    val select by rememberUpdatedState(onSelectTime)
    val t0 = spans.first().from
    val total = (spans.last().to - t0).coerceAtLeast(1L)
    val pick = selectedTime?.let { t -> spans.firstOrNull { t in it.from..it.to } ?: spans.last() }
    Column(modifier) {
        Text(
            if (pick != null) describe(pick) else idle,
            style = MaterialTheme.typography.titleSmall,
            color = c.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Dimens.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(MaterialTheme.shapes.small)
                .semantics { contentDescription = description }
                .scrub(spans) { x, width, _ -> select(t0 + ((x / width).coerceIn(0f, 1f) * total).toLong()) },
        ) {
            spans.forEach { s ->
                val left = size.width * ((s.from - t0).toFloat() / total)
                val right = size.width * ((s.to - t0).toFloat() / total)
                val color = when {
                    s == pick -> c.tertiary
                    s.moving -> c.primary
                    else -> c.surfaceContainerHighest
                }
                drawRect(color, Offset(left, 0f), Size(right - left, size.height))
            }
        }
        Spacer(Modifier.height(Dimens.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
    }
}
