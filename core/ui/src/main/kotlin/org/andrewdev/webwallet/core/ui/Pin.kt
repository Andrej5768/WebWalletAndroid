package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme

/** The dots above the keypad: one per entered digit, up to [max]. */
@Composable
fun PinDots(entered: Int, max: Int, modifier: Modifier = Modifier, error: Boolean = false) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(max) { index ->
            val filled = index < entered
            val color = when {
                error -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
            )
        }
    }
}

/** A 3×4 numeric keypad with haptics (docs/05 §5); the system keyboard is not used for PINs (docs/03 §6.1). */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    extraIcon: ImageVector? = null,
    extraDescription: String? = null,
    onExtra: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf("123", "456", "789")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                row.forEach { digit ->
                    Key(digit.toString(), enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
            if (extraIcon != null) {
                FilledTonalIconButton(onClick = onExtra, enabled = enabled, modifier = Modifier.size(KEY_SIZE.dp)) {
                    Icon(extraIcon, contentDescription = extraDescription)
                }
            } else {
                Box(Modifier.size(KEY_SIZE.dp))
            }
            Key("0", enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onDigit('0')
            }
            FilledTonalIconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(KEY_SIZE.dp)) {
                Icon(Lucide.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun Key(text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KEY_SIZE.dp), shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}

private const val KEY_SIZE = 72

/**
 * Six boxes for a TOTP code (docs/03 §3.2): one text field styled as boxes, so paste and autofill work; calls
 * [onComplete] when the last digit lands.
 */
@Composable
fun CodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: Boolean = false,
    enabled: Boolean = true,
    onComplete: (String) -> Unit = {},
) {
    LaunchedEffect(code) { if (code.length == length) onComplete(code) }
    BasicTextField(
        value = code,
        onValueChange = { typed -> onCodeChange(typed.filter { it.isDigit() }.take(length)) },
        modifier = modifier
            .autoFocusOnce()
            .semantics { contentDescription = code },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(length) { index ->
                    val digit = code.getOrNull(index)?.toString().orEmpty()
                    val active = index == code.length
                    val border = when {
                        error -> MaterialTheme.colorScheme.error
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .size(44.dp, 52.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(if (active) 2.dp else 1.dp, border, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(digit, style = WwTheme.type.amountL, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}
