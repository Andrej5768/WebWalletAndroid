package org.andrewdev.webwallet.feature.budget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.budget.Envelope
import org.andrewdev.webwallet.feature.budget.R
import java.math.BigDecimal

/** The plan of one category: the amount, "as spent" and "as last month" shortcuts (docs/04 §7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssignSheet(
    envelope: Envelope,
    /** The shortcut that takes the actual value of the month: spent for an envelope, received for income. */
    asActualLabel: String,
    currency: String,
    onPreviousAssigned: (categoryId: Long, onResult: (BigDecimal?) -> Unit) -> Unit,
    onSave: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = formatters.money
    fun text(amount: BigDecimal) = if (amount.signum() == 0) "" else money.formatQuantity(amount, 2).replace(' ', ' ')
    var text by remember { mutableStateOf(text(envelope.assigned)) }
    var value by remember { mutableStateOf<BigDecimal?>(envelope.assigned) }
    val save = { value?.let(onSave) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.feature_budget_assign_title, envelope.name),
                style = MaterialTheme.typography.titleMedium,
            )
            MoneyField(
                text = text,
                onTextChange = { text = it },
                onValueChange = { value = it },
                currency = currency,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_budget_assigned),
                imeAction = ImeAction.Done,
                onDone = { save() },
                autoFocus = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = {
                        text = text(envelope.spent)
                        value = envelope.spent.max(BigDecimal.ZERO)
                    },
                    label = { Text(asActualLabel) },
                )
                AssistChip(
                    onClick = {
                        onPreviousAssigned(envelope.categoryId) { previous ->
                            if (previous != null) {
                                text = text(previous)
                                value = previous
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.feature_budget_assign_as_last_month)) },
                )
            }
            Button(onClick = { save() }, enabled = value != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_budget_assign_save))
            }
        }
    }
}

/** Moving part of the plan from one category to another; "max" takes everything the source has left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveSheet(
    envelopes: List<Envelope>,
    currency: String,
    initialFrom: Long?,
    initialTo: Long?,
    initialAmount: BigDecimal?,
    busy: Boolean,
    onMove: (from: Long, to: Long, amount: BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    val money = formatters.money
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var text by remember {
        mutableStateOf(initialAmount?.let { money.formatQuantity(it, 2).replace(' ', ' ') }.orEmpty())
    }
    var amount by remember { mutableStateOf(initialAmount) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    val same = from != null && from == to
    val source = envelopes.firstOrNull { it.categoryId == from }
    val canMove = from != null && to != null && !same && (amount?.signum() ?: 0) > 0 && !busy
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.feature_budget_move), style = MaterialTheme.typography.titleMedium)
            EnvelopePicker(
                label = stringResource(R.string.feature_budget_move_from),
                envelope = source,
                currency = currency,
                onClick = { picking = true },
            )
            EnvelopePicker(
                label = stringResource(R.string.feature_budget_move_to),
                envelope = envelopes.firstOrNull { it.categoryId == to },
                currency = currency,
                error = if (same) stringResource(R.string.feature_budget_move_different) else null,
                onClick = { picking = false },
            )
            MoneyField(
                text = text,
                onTextChange = { text = it },
                onValueChange = { amount = it },
                currency = currency,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_budget_amount),
                imeAction = ImeAction.Done,
            )
            if (source != null && source.available.signum() > 0) {
                AssistChip(
                    onClick = {
                        text = money.formatQuantity(source.available, 2).replace(' ', ' ')
                        amount = source.available
                    },
                    label = {
                        Text(
                            stringResource(R.string.feature_budget_max, money.format(source.available, currency)),
                        )
                    },
                )
            }
            Button(
                onClick = { onMove(from!!, to!!, amount!!) },
                enabled = canMove,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_budget_move_submit))
            }
        }
    }
    picking?.let { forSource ->
        BottomSheetMenu(
            title = stringResource(
                if (forSource) R.string.feature_budget_move_from else R.string.feature_budget_move_to,
            ),
            items = envelopes.map { envelope ->
                MenuItem("${envelope.name} · ${money.format(envelope.available, currency)}") {
                    if (forSource) from = envelope.categoryId else to = envelope.categoryId
                }
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun EnvelopePicker(
    label: String,
    envelope: Envelope?,
    currency: String,
    onClick: () -> Unit,
    error: String? = null,
) {
    val money = formatters.money
    OutlinedTextField(
        value = envelope?.let { "${it.name} · ${money.format(it.available, currency)}" }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
