package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wallet

/** The wallet glyph in a cobalt tile, as the web's `Logo.tsx` and the launcher icon (docs/05 §4). */
@Composable
fun Logo(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.large)
            .background(Color(COBALT)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Wallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}

private const val COBALT = 0xFF0D52A8
