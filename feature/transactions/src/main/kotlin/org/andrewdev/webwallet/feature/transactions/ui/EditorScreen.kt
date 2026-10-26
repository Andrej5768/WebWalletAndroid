package org.andrewdev.webwallet.feature.transactions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme
import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionSource
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.transactions.DebtDirection
import org.andrewdev.webwallet.feature.transactions.EditorUiState
import org.andrewdev.webwallet.feature.transactions.EditorViewModel
import org.andrewdev.webwallet.feature.transactions.FormType
import org.andrewdev.webwallet.feature.transactions.R
import org.andrewdev.webwallet.feature.transactions.SplitValue
import java.math.BigDecimal
import java.time.LocalDate

/** The arguments of the editor key, passed through to the ViewModel on first composition. */
data class EditorArgs(
    val id: Long? = null,
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    val fromFab: Boolean = false,
)

@Composable
fun EditorRoute(
    args: EditorArgs,
    onClose: () -> Unit,
    onSaved: (Transaction, fromFab: Boolean) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(args) { viewModel.load(args.id, args.type, args.accountId, args.categoryId, args.payeeId) }
    val debtAccountName = stringResource(R.string.feature_transactions_form_debt_account_name)
    EditorScreen(
        state = state,
        callbacks = EditorCallbacks(
            onTypeChange = viewModel::onTypeChange,
            onDebtDirectionChange = viewModel::onDebtDirectionChange,
            onAmountChange = viewModel::onAmountChange,
            onDateChange = viewModel::onDateChange,
            onAccountChange = viewModel::onAccountChange,
            onToAccountChange = viewModel::onToAccountChange,
            onSwap = viewModel::swapAccounts,
            onToAmountChange = viewModel::onToAmountChange,
            onPayeeChange = viewModel::onPayeeChange,
            onCategoryChange = viewModel::onCategoryChange,
            onSplitChange = viewModel::onSplitChange,
            onSplitRowChange = viewModel::onSplitRowChange,
            onAddSplit = viewModel::addSplitRow,
            onRemoveSplit = viewModel::removeSplitRow,
            onDistribute = viewModel::distributeRemainder,
            onTagsChange = viewModel::onTagsChange,
            onCommentChange = viewModel::onCommentChange,
            onForeignChange = viewModel::onForeignChange,
            onOriginalAmountChange = viewModel::onOriginalAmountChange,
            onOriginalCurrencyChange = viewModel::onOriginalCurrencyChange,
            onSave = { viewModel.save(debtAccountName) { onSaved(it, args.fromFab) } },
            onDelete = { viewModel.delete(onClose) },
            onSplitTransfer = { viewModel.splitTransfer(onClose) },
            onClose = onClose,
        ),
    )
}

@Suppress("LongParameterList")
class EditorCallbacks(
    val onTypeChange: (FormType) -> Unit,
    val onDebtDirectionChange: (DebtDirection) -> Unit,
    val onAmountChange: (String, BigDecimal?) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onAccountChange: (Long) -> Unit,
    val onToAccountChange: (Long) -> Unit,
    val onSwap: () -> Unit,
    val onToAmountChange: (String, BigDecimal?) -> Unit,
    val onPayeeChange: (String) -> Unit,
    val onCategoryChange: (Long?) -> Unit,
    val onSplitChange: (Boolean) -> Unit,
    val onSplitRowChange: (Int, SplitValue) -> Unit,
    val onAddSplit: () -> Unit,
    val onRemoveSplit: (Int) -> Unit,
    val onDistribute: () -> Unit,
    val onTagsChange: (List<String>) -> Unit,
    val onCommentChange: (String) -> Unit,
    val onForeignChange: (Boolean) -> Unit,
    val onOriginalAmountChange: (String, BigDecimal?) -> Unit,
    val onOriginalCurrencyChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onSplitTransfer: () -> Unit,
    val onClose: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod")
fun EditorScreen(state: EditorUiState, callbacks: EditorCallbacks) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSplitTransfer by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else callbacks.onClose() }
    BackHandler(onBack = close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.feature_transactions_form_new
                            } else {
                                R.string.feature_transactions_form_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.feature_transactions_form_close))
                    }
                },
                actions = {
                    if (!state.isNew && !state.notFound) {
                        IconButton(onClick = {
                            menu = true
                        }) {
                            Icon(
                                Lucide.EllipsisVertical,
                                contentDescription = stringResource(R.string.feature_transactions_form_more),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (state.editing?.isTransfer == true && state.imported) {
                                DropdownMenuItem(text = {
                                    Text(stringResource(R.string.feature_transactions_form_split_transfer))
                                }, onClick = {
                                    menu = false
                                    confirmSplitTransfer = true
                                })
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.feature_transactions_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                    TextButton(onClick = callbacks.onSave, enabled = state.loaded && !state.saving && !state.notFound) {
                        Text(stringResource(R.string.feature_transactions_form_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.loaded -> SkeletonList(modifier = Modifier.padding(padding), rows = 4)
            state.notFound -> EmptyState(
                title = stringResource(R.string.feature_transactions_form_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> EditorForm(state, callbacks, Modifier.padding(padding))
        }
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.feature_transactions_form_discard_title),
            confirmText = stringResource(R.string.feature_transactions_form_discard),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                callbacks.onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_transactions_delete_title),
            body = stringResource(R.string.feature_transactions_delete_warning),
            confirmText = stringResource(R.string.feature_transactions_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                callbacks.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmSplitTransfer) {
        ConfirmDialog(
            title = stringResource(R.string.feature_transactions_form_split_transfer),
            body = stringResource(R.string.feature_transactions_form_split_transfer_hint),
            confirmText = stringResource(R.string.feature_transactions_form_split_transfer),
            onConfirm = {
                confirmSplitTransfer = false
                callbacks.onSplitTransfer()
            },
            onDismiss = { confirmSplitTransfer = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun EditorForm(state: EditorUiState, callbacks: EditorCallbacks, modifier: Modifier) {
    var pickAccount by remember { mutableStateOf<String?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val f = formatters
    val texts = FormTexts(
        amountRequired = stringResource(R.string.feature_transactions_form_amount_required),
        accountRequired = stringResource(R.string.feature_transactions_form_account_required),
        toAccountRequired = stringResource(R.string.feature_transactions_form_to_account_required),
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (!state.adjustment) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FormType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { callbacks.onTypeChange(type) },
                        enabled = !state.imported,
                        shape = SegmentedButtonDefaults.itemShape(index, FormType.entries.size),
                        label = { Text(formTypeName(type)) },
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.feature_transactions_form_adjustment_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.imported) {
            Text(
                stringResource(R.string.feature_transactions_form_imported, sourceName(state.editing?.source)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.type == FormType.DEBT) {
            Text(
                stringResource(R.string.feature_transactions_form_debt_direction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DebtDirection.entries.forEachIndexed { index, direction ->
                    SegmentedButton(
                        selected = state.debtDirection == direction,
                        onClick = { callbacks.onDebtDirectionChange(direction) },
                        shape = SegmentedButtonDefaults.itemShape(index, DebtDirection.entries.size),
                        label = {
                            Text(
                                stringResource(
                                    if (direction == DebtDirection.GAVE) {
                                        R.string.feature_transactions_form_debt_gave
                                    } else {
                                        R.string.feature_transactions_form_debt_received
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Text(
                stringResource(R.string.feature_transactions_form_debt_direction_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.imported) {
            MoneyField(
                text = state.amountText,
                onTextChange = { callbacks.onAmountChange(it, state.amount) },
                onValueChange = { callbacks.onAmountChange(state.amountText, it) },
                currency = state.currency,
                label = stringResource(R.string.feature_transactions_form_amount),
                error = state.fieldErrors["amount"] ?: texts.amountRequired.takeIf { state.amountMissing },
                autoFocus = state.isNew,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ReadOnlyField(
                stringResource(R.string.feature_transactions_form_amount),
                f.money.format(state.amount ?: BigDecimal.ZERO, state.currency),
            )
        }
        // Account(s)
        if (!state.imported) {
            AccountField(
                label = stringResource(
                    if (state.type == FormType.TRANSFER) {
                        R.string.feature_transactions_form_from_account
                    } else {
                        R.string.feature_transactions_form_account
                    },
                ),
                account = state.account,
                error = state.fieldErrors["accountId"] ?: texts.accountRequired.takeIf { state.accountMissing },
                onClick = { pickAccount = "from" },
            )
        } else {
            ReadOnlyField(stringResource(R.string.feature_transactions_form_account), state.account?.name.orEmpty())
        }
        if (state.type == FormType.TRANSFER) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AccountField(
                    label = stringResource(R.string.feature_transactions_form_to_account),
                    account = state.toAccount,
                    error = state.fieldErrors["toAccountId"]
                        ?: texts.toAccountRequired.takeIf { state.toAccountInvalid },
                    onClick = { pickAccount = "to" },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = callbacks.onSwap, enabled = !state.imported) {
                    Icon(
                        Lucide.ArrowLeftRight,
                        contentDescription = stringResource(R.string.feature_transactions_form_swap),
                    )
                }
            }
        }
        if (state.type == FormType.TRANSFER || (state.type == FormType.DEBT && state.currenciesDiffer)) {
            MoneyField(
                text = state.toAmountText,
                onTextChange = { callbacks.onToAmountChange(it, state.toAmount) },
                onValueChange = { callbacks.onToAmountChange(state.toAmountText, it) },
                currency = state.toCurrency ?: state.currency,
                label = if (state.type == FormType.DEBT) {
                    stringResource(
                        R.string.feature_transactions_form_debt_amount,
                        state.toCurrency ?: state.baseCurrency,
                    )
                } else {
                    stringResource(R.string.feature_transactions_form_to_amount)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.type == FormType.TRANSFER) {
                Text(
                    stringResource(
                        if (state.currenciesDiffer) {
                            R.string.feature_transactions_form_to_amount_hint
                        } else {
                            R.string.feature_transactions_form_to_amount_fee_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Date
        Text(
            stringResource(R.string.feature_transactions_form_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FilterChip(selected = state.date == f.today, onClick = {
                callbacks.onDateChange(f.today)
            }, enabled = !state.imported, label = {
                Text(
                    stringResource(R.string.feature_transactions_form_date_today),
                )
            })
            FilterChip(selected = state.date == f.today.minusDays(1), onClick = {
                callbacks.onDateChange(f.today.minusDays(1))
            }, enabled = !state.imported, label = {
                Text(
                    stringResource(R.string.feature_transactions_form_date_yesterday),
                )
            })
            FilterChip(
                selected = state.date != f.today && state.date != f.today.minusDays(1),
                onClick = { pickDate = true },
                enabled = !state.imported,
                label = {
                    Text(
                        if (state.date != f.today && state.date != f.today.minusDays(1)) {
                            f.date.short(
                                state.date,
                                f.today,
                            )
                        } else {
                            stringResource(R.string.feature_transactions_form_date_pick)
                        },
                    )
                },
            )
        }
        // Category or split
        if (state.categorized && !state.adjustment) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_transactions_form_category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.feature_transactions_form_split),
                    style = MaterialTheme.typography.bodySmall,
                )
                Switch(
                    checked = state.split,
                    onCheckedChange = callbacks.onSplitChange,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (!state.split) {
                val selected = state.categoriesOfKind.flatMap { it.flatten() }.firstOrNull { it.id == state.categoryId }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { pickCategory = true }.padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    CategoryIcon(selected, size = 32.dp)
                    Text(
                        selected?.name ?: stringResource(R.string.feature_transactions_uncategorized),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                SplitEditor(state, callbacks)
            }
        }
        // Payee
        if (state.type != FormType.TRANSFER && !state.adjustment) {
            PayeeField(state, callbacks.onPayeeChange)
        }
        // Tags
        TagsField(state.tagNames, state.tags.map { it.name }, callbacks.onTagsChange)
        OutlinedTextField(
            value = state.comment,
            onValueChange = callbacks.onCommentChange,
            label = { Text(stringResource(R.string.feature_transactions_form_comment)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.categorized && !state.imported) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_transactions_form_foreign),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = state.foreign, onCheckedChange = callbacks.onForeignChange)
            }
            if (state.foreign) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoneyField(
                        text = state.originalAmountText,
                        onTextChange = { callbacks.onOriginalAmountChange(it, state.originalAmount) },
                        onValueChange = { callbacks.onOriginalAmountChange(state.originalAmountText, it) },
                        currency = state.originalCurrency,
                        label = stringResource(R.string.feature_transactions_form_original_amount),
                        imeAction = ImeAction.Done,
                        modifier = Modifier.weight(1f),
                    )
                    CurrencyDropdown(state.originalCurrency, callbacks.onOriginalCurrencyChange)
                }
            }
        }
        state.error?.takeIf { state.fieldErrors.isEmpty() }?.let {
            Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.fieldErrors.filterKeys { it !in setOf("amount", "accountId", "toAccountId") }.values.forEach {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    pickAccount?.let { which ->
        AccountPickerSheet(
            accounts = state.selectableAccounts,
            selected = if (which == "from") state.accountId else state.toAccountId,
            allowNone = false,
            onPick = { id ->
                id?.let {
                    if (which == "from") callbacks.onAccountChange(it) else callbacks.onToAccountChange(it)
                }
                pickAccount = null
            },
            onDismiss = { pickAccount = null },
        )
    }
    if (pickCategory) {
        CategoryPickerSheet(
            title = stringResource(R.string.feature_transactions_form_category),
            categories = state.categoriesOfKind,
            onPick = {
                callbacks.onCategoryChange(it.id)
                pickCategory = false
            },
            onDismiss = { pickCategory = false },
            allowNone = {
                callbacks.onCategoryChange(null)
                pickCategory = false
            },
        )
    }
    if (pickDate) {
        DatePickerDialogOf(state.date, onPick = {
            callbacks.onDateChange(it)
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
}

/** The validation messages of the form, resolved once. */
private class FormTexts(val amountRequired: String, val accountRequired: String, val toAccountRequired: String)

@Composable
private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(value = value, onValueChange = {
    }, readOnly = true, enabled = false, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AccountField(
    label: String,
    account: Account?,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = account?.name.orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        leadingIcon = account?.let { { AccountTypeIcon(it.type, size = 24.dp) } },
        trailingIcon = account?.let { {
            Text(
                it.currency.code,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = Spacing.sm),
            )
        }
        },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outline
            },
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeField(state: EditorUiState, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(state.payee, state.payees) {
        if (state.payee.isBlank()) {
            emptyList()
        } else {
            state.payees.filter {
                it.name.contains(state.payee.trim(), ignoreCase = true) && it.name != state.payee
            }.take(MAX_SUGGESTIONS)
        }
    }
    val debt = state.type == FormType.DEBT
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.payee,
            onValueChange = {
                onChange(it)
                expanded = true
            },
            label = {
                Text(
                    stringResource(
                        if (debt) {
                            R.string.feature_transactions_form_debt_person
                        } else {
                            R.string.feature_transactions_form_payee
                        },
                    ),
                )
            },
            placeholder = { Text(stringResource(R.string.feature_transactions_form_payee_placeholder)) },
            isError = state.payeeMissing,
            supportingText = if (state.payeeMissing) {
                (
                    {
                        Text(stringResource(R.string.feature_transactions_form_debt_person_required))
                    }
                    )
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
            suggestions.forEach { payee ->
                DropdownMenuItem(text = {
                    Text(payee.name)
                }, onClick = {
                    onChange(payee.name)
                    expanded = false
                }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 6

/** Chips of the chosen tags and a field that adds one on Enter; unknown names are created on save. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagsField(selected: List<String>, known: List<String>, onChange: (List<String>) -> Unit) {
    var typing by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                selected.forEach { name ->
                    InputChip(selected = true, onClick = {
                        onChange(selected - name)
                    }, label = {
                        Text(name)
                    }, trailingIcon = { Icon(Lucide.X, contentDescription = null, modifier = Modifier.padding(0.dp)) })
                }
            }
        }
        val suggestions = known.filter {
            typing.isNotBlank() && it.contains(typing.trim(), ignoreCase = true) && it !in selected
        }.take(MAX_SUGGESTIONS)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = typing,
                onValueChange = {
                    typing = it
                    expanded = true
                },
                label = { Text(stringResource(R.string.feature_transactions_form_tags)) },
                placeholder = { Text(stringResource(R.string.feature_transactions_form_tags_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val name = typing.trim()
                    if (name.isNotEmpty() && selected.none { it.equals(name, ignoreCase = true) }) {
                        onChange(
                            selected + name,
                        )
                    }
                    typing = ""
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded && suggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        onChange(selected + name)
                        typing = ""
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun SplitEditor(state: EditorUiState, callbacks: EditorCallbacks) {
    val f = formatters
    var pickFor by remember { mutableStateOf<Int?>(null) }
    val byId = state.categoriesOfKind.flatMap { it.flatten() }.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.splits.forEachIndexed { index, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.weight(1f).clickable { pickFor = index },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CategoryIcon(byId[row.categoryId], size = 28.dp)
                    Text(
                        byId[row.categoryId]?.name ?: stringResource(R.string.feature_transactions_form_split_category),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                MoneyField(
                    text = row.amountText,
                    onTextChange = { callbacks.onSplitRowChange(index, row.copy(amountText = it)) },
                    onValueChange = { callbacks.onSplitRowChange(index, row.copy(amount = it)) },
                    currency = state.currency,
                    label = stringResource(R.string.feature_transactions_form_split_amount),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { callbacks.onRemoveSplit(index) }, enabled = state.splits.size > 2) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.feature_transactions_form_split_remove))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.feature_transactions_form_split_remainder,
                    f.money.format(state.splitRemainder, state.currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.splitMismatch) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = callbacks.onDistribute, enabled = state.splitRemainder.signum() != 0) {
                Text(stringResource(R.string.feature_transactions_form_split_distribute))
            }
        }
        if (state.splitMismatch) {
            Text(
                stringResource(R.string.feature_transactions_form_split_mismatch),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(
            onClick = callbacks.onAddSplit,
        ) { Text(stringResource(R.string.feature_transactions_form_split_add)) }
    }
    pickFor?.let { index ->
        CategoryPickerSheet(
            title = stringResource(R.string.feature_transactions_form_split_category),
            categories = state.categoriesOfKind,
            onPick = {
                callbacks.onSplitRowChange(index, state.splits[index].copy(categoryId = it.id))
                pickFor = null
            },
            onDismiss = { pickFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(selected: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val choices = listOf("USD", "EUR", "GBP", "PLN", "CZK", "TRY", "GEL", "UAH")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .width(110.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = {
                    onPick(code)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun formTypeName(type: FormType): String = stringResource(
    when (type) {
        FormType.EXPENSE -> R.string.feature_transactions_form_type_expense
        FormType.INCOME -> R.string.feature_transactions_form_type_income
        FormType.TRANSFER -> R.string.feature_transactions_form_type_transfer
        FormType.DEBT -> R.string.feature_transactions_form_type_debt
    },
)

@Composable
private fun sourceName(source: TransactionSource?): String = stringResource(
    when (source) {
        TransactionSource.PRIVATBANK -> R.string.feature_transactions_form_source_privatbank
        TransactionSource.ZENMONEY -> R.string.feature_transactions_form_source_zenmoney
        else -> R.string.feature_transactions_form_source_monobank
    },
)

@Suppress("MagicNumber")
private fun previewAccount(id: Long, name: String, type: AccountType) = Account(
    id, name, type, CurrencyCode.UAH,
    BigDecimal(
        "100",
    ),
    BigDecimal("100"), BigDecimal.ZERO, null, null, null, true, false, 0, 5,
)

@PreviewLightDark
@Composable
@Suppress("MagicNumber")
private fun EditorPreview() {
    WebWalletTheme {
        EditorScreen(
            EditorUiState(
                loaded = true,
                amountText = "1200",
                amount = BigDecimal("1200"),
                date = LocalDate.of(2026, 10, 26),
                accountId = 1,
                accounts = listOf(
                    previewAccount(1, "Чорна картка", AccountType.CARD),
                    previewAccount(2, "Готівка", AccountType.CASH),
                ),
                tagNames = listOf("відпустка"),
            ),
            EditorCallbacks(
                {
                }, {
                }, { _, _ -> }, {
                }, {}, {}, {}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {}, {}, {},
                {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {},
            ),
        )
    }
}
