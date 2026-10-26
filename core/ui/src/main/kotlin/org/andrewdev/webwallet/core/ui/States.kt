package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WifiOff
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.network.ErrorCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The message of an error for people (docs/02 §4.3): the backend's own text when it has one. */
@Composable
fun ApiError.message(): String = when (this) {
    is ApiError.Network -> stringResource(R.string.core_ui_error_network)
    is ApiError.Problem -> when {
        code == ErrorCode.TOO_MANY_ATTEMPTS || code == ErrorCode.TOO_MANY_REQUESTS ->
            detail ?: stringResource(R.string.core_ui_error_too_many)
        status >= HTTP_SERVER_ERROR -> stringResource(R.string.core_ui_error_unavailable)
        else -> detail ?: stringResource(R.string.core_ui_error_unexpected)
    }
    is ApiError.Unexpected -> if ((status ?: 0) >= HTTP_SERVER_ERROR) {
        stringResource(R.string.core_ui_error_unavailable)
    } else {
        stringResource(R.string.core_ui_error_unexpected)
    }
}

private const val HTTP_SERVER_ERROR = 500

/** Centered, illustration-free (docs/05 §5). */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) Button(onClick = onAction, modifier = Modifier.padding(top = Spacing.sm)) { Text(action) }
    }
}

/** An error with nothing cached behind it: the message and a retry. */
@Composable
fun ErrorState(error: ApiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = error.message(),
        modifier = modifier,
        icon = if (error is ApiError.Network) Lucide.WifiOff else null,
        action = stringResource(R.string.core_ui_retry),
        onAction = onRetry,
    )
}

/** "Showing data from 14:02 · Offline" over stale content (docs/02 §5.2). */
@Composable
fun StaleBanner(fetchedAt: Instant?, offline: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val time = fetchedAt?.let { DateTimeFormatter.ofPattern("HH:mm").format(it.atZone(ZoneId.systemDefault())) }
    val text = when {
        time != null && offline -> stringResource(R.string.core_ui_stale_offline, time)
        time != null -> stringResource(R.string.core_ui_stale_from, time)
        offline -> stringResource(R.string.core_ui_offline)
        else -> stringResource(R.string.core_ui_stale)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WwTheme.colors.warnBg)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = WwTheme.colors.warnText,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.core_ui_retry)) }
    }
}

/** A warning surface with the warn tokens (overspent, bank difference, forecast dip). */
@Composable
fun WarnCard(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(WwTheme.colors.warnBg)
            .border(1.dp, WwTheme.colors.warnBorder, MaterialTheme.shapes.medium)
            .padding(Spacing.md),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = WwTheme.colors.warnText)
        if (action != null) {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) { Text(action) }
        }
    }
}

/** A shimmer-free placeholder block while something loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 16.dp, width: Dp? = null) {
    Box(
        modifier = modifier
            .let { if (width != null) it.width(width) else it.fillMaxWidth() }
            .height(height)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Three rows of skeleton lines, the usual list placeholder. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 3) {
    Column(modifier = modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(height = 40.dp, width = 40.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Skeleton(width = 160.dp)
                    Skeleton(width = 100.dp, height = 12.dp)
                }
                Skeleton(width = 72.dp)
            }
        }
    }
}
