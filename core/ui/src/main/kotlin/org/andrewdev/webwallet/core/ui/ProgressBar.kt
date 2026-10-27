package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The progress of an envelope or a goal (docs/05 §5): the filled share of a track, with an optional marker at the
 * share of the period that has passed ("today"), so that the eye compares pace with spending.
 */
@Composable
fun ProgressBar(
    share: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    marker: Float? = null,
    height: Dp = 6.dp,
    description: String? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .let { if (description != null) it.semantics { contentDescription = description } else it },
    ) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val filled = size.width * share.coerceIn(0f, 1f)
        if (filled > 0f) drawRoundRect(color, size = Size(filled, size.height), cornerRadius = radius)
        if (marker != null) {
            val x = size.width * marker.coerceIn(0f, 1f)
            drawLine(markerColor, Offset(x, -MARKER_OVERHANG), Offset(x, size.height + MARKER_OVERHANG), MARKER_WIDTH)
        }
    }
}

private const val MARKER_OVERHANG = 4f
private const val MARKER_WIDTH = 2f
