package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme

/** One line of a [LineChart]. */
data class ChartLine(
    val values: List<Float>,
    val color: Color,
    val dashed: Boolean = false,
    /** Fill under the line, at a low alpha. */
    val filled: Boolean = false,
    /** The x position of the first value, when the line covers only part of the axis. */
    val offset: Int = 0,
)

/** A horizontal reference line of a [LineChart], e.g. a minimum or a target. */
data class ChartMarker(val value: Float, val color: Color, val dashed: Boolean = true)

/**
 * A plain line chart (docs/05 §5): lines over an evenly spaced x axis, optional horizontal markers, a grid of four
 * lines. Tap-to-inspect comes from the caller, which knows what the points mean.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun LineChart(
    lines: List<ChartLine>,
    modifier: Modifier = Modifier,
    markers: List<ChartMarker> = emptyList(),
    height: Dp = 160.dp,
    /** Values the y axis must include besides the data, e.g. zero. */
    include: List<Float> = emptyList(),
    /** How many x positions the axis has; by default the longest line. */
    points: Int? = null,
    description: String? = null,
) {
    val grid = WwTheme.colors.chartGrid
    val count = points ?: (lines.maxOfOrNull { it.offset + it.values.size } ?: 0)
    val all = lines.flatMap { it.values } + markers.map { it.value } + include
    val min = all.minOrNull() ?: 0f
    val max = all.maxOrNull() ?: 1f
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .let { if (description != null) it.semantics { contentDescription = description } else it },
    ) {
        fun y(value: Float) = size.height - (value - min) / span * size.height
        repeat(GRID_LINES) { i ->
            val gy = size.height * i / (GRID_LINES - 1)
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), GRID_WIDTH)
        }
        markers.forEach { marker ->
            drawLine(
                marker.color,
                Offset(0f, y(marker.value)),
                Offset(size.width, y(marker.value)),
                MARKER_WIDTH,
                pathEffect = if (marker.dashed) PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)) else null,
            )
        }
        val step = size.width / (count - 1).coerceAtLeast(1)
        lines.forEach { line ->
            if (line.values.size < 2) return@forEach
            val path = Path()
            line.values.forEachIndexed { index, value ->
                val point = Offset(step * (line.offset + index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            if (line.filled) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(step * (line.offset + line.values.size - 1), size.height)
                    lineTo(step * line.offset, size.height)
                    close()
                }
                drawPath(fill, line.color.copy(alpha = FILL_ALPHA))
            }
            drawPath(
                path,
                line.color,
                style = Stroke(
                    width = LINE_WIDTH,
                    cap = StrokeCap.Round,
                    pathEffect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)) else null,
                ),
            )
        }
    }
}

/** One group of a [ColumnChart]: the values side by side at one x position. */
data class ChartColumn(val values: List<Float>, val label: String)

/** Columns per period (docs/05 §5), the series coloured in order; negative values hang below the zero line. */
@Composable
fun ColumnChart(
    columns: List<ChartColumn>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    selected: Int? = null,
    description: String? = null,
) {
    val grid = WwTheme.colors.chartGrid
    val highlight = MaterialTheme.colorScheme.onSurface
    val all = columns.flatMap { it.values } + 0f
    val min = all.min()
    val max = all.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .let { if (description != null) it.semantics { contentDescription = description } else it },
    ) {
        fun y(value: Float) = size.height - (value - min) / span * size.height
        val zero = y(0f)
        drawLine(grid, Offset(0f, zero), Offset(size.width, zero), GRID_WIDTH)
        if (columns.isEmpty()) return@Canvas
        val slot = size.width / columns.size
        val series = colors.size.coerceAtLeast(1)
        val bar = slot * BAR_SHARE / series
        columns.forEachIndexed { index, column ->
            val left = slot * index + slot * (1 - BAR_SHARE) / 2
            column.values.forEachIndexed { s, value ->
                val top = minOf(y(value), zero)
                val h = kotlin.math.abs(y(value) - zero)
                drawRect(colors[s % colors.size], Offset(left + bar * s, top), Size(bar * BAR_GAP, h))
            }
            if (index == selected) {
                drawLine(highlight, Offset(left, size.height), Offset(left + bar * series, size.height), MARKER_WIDTH)
            }
        }
    }
}

/** A ring with the share filled (docs/05 §5), the text in the middle. */
@Composable
fun RingProgress(
    share: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = 6.dp,
    label: String? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Round)
            val inset = thickness.toPx() / 2
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, FULL_SWEEP, false, Offset(inset, inset), arc, style = stroke)
            drawArc(
                color,
                START_ANGLE,
                FULL_SWEEP * share.coerceIn(0f, 1f),
                false,
                Offset(inset, inset),
                arc,
                style = stroke,
            )
        }
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private const val GRID_LINES = 4
private const val GRID_WIDTH = 1f
private const val MARKER_WIDTH = 2f
private const val LINE_WIDTH = 4f
private const val DASH = 10f
private const val FILL_ALPHA = 0.12f
private const val BAR_SHARE = 0.7f
private const val BAR_GAP = 0.9f
private const val FULL_SWEEP = 360f
private const val START_ANGLE = -90f

/** One slice of a [DonutChart]. */
data class DonutSlice(val value: Float, val color: Color)

/** A ring split into shares (docs/05 §5), the selected slice drawn a little thicker; the text sits in the middle. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    thickness: Dp = 22.dp,
    selected: Int? = null,
    label: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2 + SELECTED_EXTRA
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, FULL_SWEEP, false, Offset(inset, inset), arc, style = Stroke(width = stroke))
            var start = START_ANGLE
            slices.forEachIndexed { index, slice ->
                val sweep = FULL_SWEEP * slice.value / total
                val width = if (index == selected) stroke + SELECTED_EXTRA * 2 else stroke
                drawArc(
                    slice.color,
                    start,
                    sweep - SLICE_GAP,
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(width = width),
                )
                start += sweep
            }
        }
        label()
    }
}

private const val SELECTED_EXTRA = 6f
private const val SLICE_GAP = 1.5f
