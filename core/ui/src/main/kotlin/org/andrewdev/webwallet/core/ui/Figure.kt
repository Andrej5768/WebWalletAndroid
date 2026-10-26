package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.andrewdev.webwallet.core.designsystem.theme.Spacing

/** A flat card with the 1 dp border of the web (docs/05 §3). */
@Composable
fun WwCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, border = border) { content() }
    } else {
        Card(modifier = modifier, colors = colors, border = border) { content() }
    }
}

/** A stat tile of the overview and reports: a label, a value, an optional note. */
@Composable
fun Figure(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    onClick: (() -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(Spacing.md),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
