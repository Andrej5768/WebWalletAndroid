package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.andrewdev.webwallet.core.common.Arithmetic
import java.math.BigDecimal

/**
 * The amount field (docs/05 §5, docs/02 §8): accepts `,` and `.`, ignores spaces, evaluates `+ - * / %` expressions
 * when the field loses focus or Enter is pressed, and shows the result under the field while an expression is being
 * typed. The caller keeps the text; [onValueChange] gets the parsed value, null while the text is not a number.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
fun MoneyField(
    text: String,
    onTextChange: (String) -> Unit,
    onValueChange: (BigDecimal?) -> Unit,
    currency: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    error: String? = null,
    allowNegative: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    autoFocus: Boolean = false,
) {
    val preview = remember(text) { if (Arithmetic.isExpression(text)) Arithmetic.evaluate(text) else null }
    var hadFocus by remember { mutableStateOf(false) }
    val money = formatters.money
    val settle = {
        val value = Arithmetic.evaluate(text)
        if (value != null && Arithmetic.isExpression(text)) {
            onTextChange(
                money.formatQuantity(value, 2).replace('\u00a0', ' '),
            )
        }
        onValueChange(value?.takeIf { allowNegative || it.signum() >= 0 })
    }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val filtered = typed.filter { it.isDigit() || it in ",. +-*/%()×x÷" }
            onTextChange(filtered)
            onValueChange(
                Arithmetic.evaluate(
                    filtered,
                )?.takeIf { (allowNegative || it.signum() >= 0) && !Arithmetic.isExpression(filtered) },
            )
        },
        modifier = modifier
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused) settle()
                hadFocus = state.isFocused
            }
            .let { if (autoFocus) it.autoFocusOnce() else it },
        label = label?.let { { Text(it) } },
        suffix = { Text(money.symbol(currency)) },
        supportingText = when {
            error != null -> ({ Text(error) })
            preview != null -> ({ Text("= " + money.format(preview, currency)) })
            else -> null
        },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { settle() },
            onDone = {
                settle()
                onDone()
            },
        ),
    )
}
