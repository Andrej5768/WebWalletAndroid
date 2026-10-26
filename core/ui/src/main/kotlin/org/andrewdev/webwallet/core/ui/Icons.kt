package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.designsystem.icons.AccountTypeIcons
import org.andrewdev.webwallet.core.designsystem.icons.CategoryIcons
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.Category

/** The tile of a category: its palette colour behind its Lucide glyph; no category shows a dashed question mark. */
@Composable
fun CategoryIcon(category: Category?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = isSystemInDarkTheme()
    if (category == null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.CircleHelp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
        return
    }
    val palette = PaletteColor.of(category.color, category.id)
    IconTile(CategoryIcons[category.icon], palette.color(dark), palette.container(dark), modifier, size)
}

/** The tile of an account type, coloured as docs/05 §1.4 says. */
@Composable
fun AccountTypeIcon(type: AccountType, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = isSystemInDarkTheme()
    val palette = type.palette
    IconTile(type.icon, palette.color(dark), palette.container(dark), modifier, size)
}

val AccountType.icon: ImageVector
    get() = when (this) {
        AccountType.CARD -> AccountTypeIcons.card
        AccountType.CREDIT_CARD -> AccountTypeIcons.creditCard
        AccountType.CASH -> AccountTypeIcons.cash
        AccountType.SAVINGS -> AccountTypeIcons.savings
        AccountType.DEPOSIT -> AccountTypeIcons.deposit
        AccountType.LOAN -> AccountTypeIcons.loan
        AccountType.DEBT -> AccountTypeIcons.debt
        AccountType.INVESTMENT -> AccountTypeIcons.investment
        AccountType.FOP -> AccountTypeIcons.fop
    }

val AccountType.palette: PaletteColor
    get() = when (this) {
        AccountType.CARD -> PaletteColor.BLUE
        AccountType.CREDIT_CARD -> PaletteColor.RED
        AccountType.CASH -> PaletteColor.GREEN
        AccountType.SAVINGS -> PaletteColor.PINK
        AccountType.DEPOSIT -> PaletteColor.VIOLET
        AccountType.LOAN -> PaletteColor.ORANGE
        AccountType.DEBT -> PaletteColor.AMBER
        AccountType.INVESTMENT -> PaletteColor.TEAL
        AccountType.FOP -> PaletteColor.INDIGO
    }

@Composable
fun IconTile(icon: ImageVector, tint: Color, container: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}
