package org.andrewdev.webwallet.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.andrewdev.webwallet.core.designsystem.theme.Spacing

/**
 * A confirmation; destructive ones use the error colour, and [typedConfirmation] asks the user to type a word
 * (DELETE, their email) before the button enables, like the web (docs/04 common rules).
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    destructive: Boolean = false,
    typedConfirmation: String? = null,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val enabled = typedConfirmation == null || typed.trim().equals(typedConfirmation, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (body != null || typedConfirmation != null) {
            {
                Column {
                    if (body != null) Text(body)
                    if (typedConfirmation != null) {
                        OutlinedTextField(
                            value = typed,
                            onValueChange = { typed = it },
                            label = { Text(stringResource(R.string.core_ui_type_to_confirm, typedConfirmation)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.md),
                        )
                    }
                }
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = enabled,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_cancel)) } },
    )
}

/** One entry of a [BottomSheetMenu]. */
data class MenuItem(
    val text: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Replaces the row menus of the web (docs/05 §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(title: String?, items: List<MenuItem>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
        items.forEach { item ->
            val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            ListItem(
                headlineContent = { Text(item.text, color = color) },
                leadingContent = item.icon?.let { { Icon(it, contentDescription = null, tint = color) } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable {
                    onDismiss()
                    item.onClick()
                },
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Spacing.xxl))
    }
}
