@file:Suppress("MatchingDeclarationName")

package org.andrewdev.webwallet.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.TransactionType
import java.math.BigDecimal

/** How an amount is coloured: by its sign, by the transaction type, or not at all. */
sealed interface AmountTone {
    data object Neutral : AmountTone

    data object BySign : AmountTone

    data class ByType(val type: TransactionType) : AmountTone
}

/**
 * A formatted amount with tabular digits (docs/05 §5): expense red, income green, transfer neutral, always with
 * its sign when [signed]; TalkBack reads the formatted text, which says the currency in words enough.
 */
@Composable
fun Amount(
    amount: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier,
    style: TextStyle = WwTheme.type.amountM,
    tone: AmountTone = AmountTone.Neutral,
    signed: Boolean = false,
    decimals: Int = 2,
    compact: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val money = formatters.money
    val text = if (compact) money.formatCompact(amount, currency) else money.format(amount, currency, signed, decimals)
    val colors = WwTheme.colors
    val resolved = when {
        color != Color.Unspecified -> color
        tone is AmountTone.ByType -> when (tone.type) {
            TransactionType.EXPENSE -> colors.expense
            TransactionType.INCOME -> colors.income
            TransactionType.TRANSFER -> colors.transfer
        }
        tone is AmountTone.BySign && amount.signum() < 0 -> colors.expense
        tone is AmountTone.BySign && amount.signum() > 0 -> colors.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = style,
        color = resolved,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        modifier = modifier.semantics { contentDescription = text.replace('\u00a0', ' ') },
    )
}
