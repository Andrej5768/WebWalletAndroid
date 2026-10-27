package org.andrewdev.webwallet.feature.transactions.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Frequency
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.transactions.EndMode
import org.andrewdev.webwallet.feature.transactions.R
import org.andrewdev.webwallet.feature.transactions.RecurringEditorCallbacks
import org.andrewdev.webwallet.feature.transactions.RecurringEditorUiState
import org.andrewdev.webwallet.feature.transactions.RecurringEditorViewModel
import org.andrewdev.webwallet.feature.transactions.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun RecurringEditorRoute(id: Long?, onClose: () -> Unit, viewModel: RecurringEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    RecurringEditorScreen(
        state = state,
        callbacks = RecurringEditorCallbacks(
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onAccountChange = viewModel::onAccountChange,
            onToAccountChange = viewModel::onToAccountChange,
            onAmountChange = viewModel::onAmountChange,
            onToAmountChange = viewModel::onToAmountChange,
            onVariableChange = viewModel::onVariableChange,
            onCategoryChange = viewModel::onCategoryChange,
            onPayeeChange = viewModel::onPayeeChange,
            onCommentChange = viewModel::onCommentChange,
            onFrequencyChange = viewModel::onFrequencyChange,
            onIntervalChange = viewModel::onIntervalChange,
            onWeekdayChange = viewModel::onWeekdayChange,
            onDayOfMonthChange = viewModel::onDayOfMonthChange,
            onYearDayChange = viewModel::onYearDayChange,
            onStartFromChange = viewModel::onStartFromChange,
            onEndModeChange = viewModel::onEndModeChange,
            onEndDateChange = viewModel::onEndDateChange,
            onCountChange = viewModel::onCountChange,
            onAutoCreateChange = viewModel::onAutoCreateChange,
            onToleranceChange = viewModel::onToleranceChange,
            onSave = { viewModel.save(onClose) },
            onStop = { viewModel.stop(onClose) },
            onDelete = { viewModel.delete(onClose) },
            onClose = onClose,
        ),
    )
}

/** The recurring payment editor (docs/04 §6.4): the transaction template, then the schedule, then the mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorScreen(state: RecurringEditorUiState, callbacks: RecurringEditorCallbacks) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.feature_transactions_recurring_new_title
                            } else {
                                R.string.feature_transactions_recurring_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onClose) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.feature_transactions_form_close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Lucide.Trash2,
                                contentDescription = stringResource(R.string.feature_transactions_form_delete),
                            )
                        }
                    }
                    TextButton(onClick = callbacks.onSave, enabled = !state.saving && state.loaded) {
                        Text(stringResource(R.string.feature_transactions_form_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TemplateFields(state, callbacks)
            ScheduleFields(state, callbacks)
            ModeFields(state, callbacks)
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            if (!state.isNew && state.editing?.stopped == false) {
                TextButton(onClick = callbacks.onStop, enabled = !state.saving) {
                    Text(stringResource(R.string.feature_transactions_recurring_stop))
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_transactions_recurring_delete_title, state.name),
            body = stringResource(R.string.feature_transactions_recurring_delete_text),
            confirmText = stringResource(R.string.feature_transactions_form_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                callbacks.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod")
private fun TemplateFields(state: RecurringEditorUiState, callbacks: RecurringEditorCallbacks) {
    var pickAccount by remember { mutableStateOf<Boolean?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    val required = stringResource(R.string.feature_transactions_form_required)
    OutlinedTextField(
        value = state.name,
        onValueChange = callbacks.onNameChange,
        label = { Text(stringResource(R.string.feature_transactions_recurring_name)) },
        isError = state.nameMissing || "name" in state.fieldErrors,
        supportingText = (state.fieldErrors["name"] ?: required.takeIf { state.nameMissing })?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TransactionType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = state.type == type,
                onClick = { callbacks.onTypeChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                label = { Text(typeName(type)) },
            )
        }
    }
    PickerField(
        label = stringResource(R.string.feature_transactions_form_account),
        value = state.account?.name.orEmpty(),
        error = required.takeIf { state.accountMissing },
        leading = state.account?.let { { AccountTypeIcon(it.type, size = 24.dp) } },
        onClick = { pickAccount = true },
    )
    if (state.isTransfer) {
        PickerField(
            label = stringResource(R.string.feature_transactions_form_to_account),
            value = state.toAccount?.name.orEmpty(),
            error = required.takeIf { state.toAccountMissing },
            leading = state.toAccount?.let { { AccountTypeIcon(it.type, size = 24.dp) } },
            onClick = { pickAccount = false },
        )
    }
    MoneyField(
        text = state.amountText,
        onTextChange = { callbacks.onAmountChange(it, state.amount) },
        onValueChange = { callbacks.onAmountChange(state.amountText, it) },
        currency = state.currency,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.feature_transactions_form_amount),
        error = state.fieldErrors["amount"] ?: required.takeIf { state.amountMissing },
    )
    if (state.needsToAmount) {
        MoneyField(
            text = state.toAmountText,
            onTextChange = { callbacks.onToAmountChange(it, state.toAmount) },
            onValueChange = { callbacks.onToAmountChange(state.toAmountText, it) },
            currency = state.toAccount?.currency?.code ?: state.currency,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.feature_transactions_form_to_amount),
        )
    }
    SwitchRow(
        text = stringResource(R.string.feature_transactions_recurring_variable),
        checked = state.variableAmount,
        onChange = callbacks.onVariableChange,
    )
    if (!state.isTransfer) {
        PickerField(
            label = stringResource(R.string.feature_transactions_form_category),
            value = state.categories.flatMap { it.flatten() }.firstOrNull { it.id == state.categoryId }?.name.orEmpty(),
            onClick = { pickCategory = true },
        )
        OutlinedTextField(
            value = state.payee,
            onValueChange = callbacks.onPayeeChange,
            label = { Text(stringResource(R.string.feature_transactions_form_payee)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = state.comment,
        onValueChange = callbacks.onCommentChange,
        label = { Text(stringResource(R.string.feature_transactions_form_comment)) },
        modifier = Modifier.fillMaxWidth(),
    )
    pickAccount?.let { forSource ->
        AccountPickerSheet(
            accounts = state.accounts,
            selected = if (forSource) state.accountId else state.toAccountId,
            allowNone = false,
            onPick = { id ->
                id?.let { if (forSource) callbacks.onAccountChange(it) else callbacks.onToAccountChange(it) }
                pickAccount = null
            },
            onDismiss = { pickAccount = null },
        )
    }
    if (pickCategory) {
        CategoryPickerSheet(
            title = stringResource(R.string.feature_transactions_form_category),
            categories = state.selectableCategories,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleFields(state: RecurringEditorUiState, callbacks: RecurringEditorCallbacks) {
    var pickStart by remember { mutableStateOf(false) }
    var pickYearDay by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val f = formatters
    val locale = f.language.locale
    SectionTitle(stringResource(R.string.feature_transactions_recurring_schedule))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Frequency.entries.forEachIndexed { index, frequency ->
            SegmentedButton(
                selected = state.frequency == frequency,
                onClick = { callbacks.onFrequencyChange(frequency) },
                shape = SegmentedButtonDefaults.itemShape(index, Frequency.entries.size),
                label = { Text(frequencyName(frequency)) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = state.intervalText,
            onValueChange = callbacks.onIntervalChange,
            label = { Text(stringResource(R.string.feature_transactions_recurring_every)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(INTERVAL_FIELD_WIDTH),
        )
        Text(unitName(state.frequency, state.interval), style = MaterialTheme.typography.bodyLarge)
    }
    when (state.frequency) {
        Frequency.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = state.weekday == day,
                    onClick = { callbacks.onWeekdayChange(day) },
                    label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
                )
            }
        }
        Frequency.MONTHLY -> {
            OutlinedTextField(
                value = state.dayOfMonth.toString(),
                onValueChange = { text -> text.toIntOrNull()?.let(callbacks.onDayOfMonthChange) },
                label = { Text(stringResource(R.string.feature_transactions_recurring_day_of_month)) },
                supportingText = { Text(stringResource(R.string.feature_transactions_recurring_short_months)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(INTERVAL_FIELD_WIDTH),
            )
        }
        Frequency.YEARLY -> PickerField(
            label = stringResource(R.string.feature_transactions_recurring_year_day),
            value = f.date.dayMonth(LocalDate.of(LEAP_YEAR, state.yearMonth, state.dayOfMonth)),
            onClick = { pickYearDay = true },
        )
        Frequency.DAILY -> Unit
    }
    PickerField(
        label = stringResource(R.string.feature_transactions_recurring_start_from),
        value = f.date.long(state.startFrom),
        onClick = { pickStart = true },
    )
    val preview = Schedule.nextDates(
        state.startDate,
        state.frequency,
        state.interval,
        state.resolvedEndDate,
        state.startDate,
        PREVIEW_DATES,
    )
    Text(
        stringResource(
            R.string.feature_transactions_recurring_preview,
            preview.joinToString(", ") { f.date.short(it, f.today) },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionTitle(stringResource(R.string.feature_transactions_recurring_end))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        EndMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.endMode == mode,
                onClick = { callbacks.onEndModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, EndMode.entries.size),
                label = { Text(endModeName(mode)) },
            )
        }
    }
    when (state.endMode) {
        EndMode.NEVER -> Unit
        EndMode.DATE -> PickerField(
            label = stringResource(R.string.feature_transactions_recurring_end_date_label),
            value = state.endDate?.let { f.date.long(it) }.orEmpty(),
            error = stringResource(
                R.string.feature_transactions_recurring_end_before_start,
            ).takeIf { state.endBeforeStart },
            onClick = { pickEnd = true },
        )
        EndMode.COUNT -> OutlinedTextField(
            value = state.countText,
            onValueChange = callbacks.onCountChange,
            label = { Text(stringResource(R.string.feature_transactions_recurring_count_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(INTERVAL_FIELD_WIDTH),
        )
    }
    if (pickStart) {
        DatePickerDialogOf(state.startFrom, onPick = {
            callbacks.onStartFromChange(it)
            pickStart = false
        }, onDismiss = { pickStart = false })
    }
    if (pickYearDay) {
        DatePickerDialogOf(LocalDate.of(LEAP_YEAR, state.yearMonth, state.dayOfMonth), onPick = {
            callbacks.onYearDayChange(it.monthValue, it.dayOfMonth)
            pickYearDay = false
        }, onDismiss = { pickYearDay = false })
    }
    if (pickEnd) {
        DatePickerDialogOf(state.endDate ?: state.startDate, onPick = {
            callbacks.onEndDateChange(it)
            pickEnd = false
        }, onDismiss = { pickEnd = false })
    }
}

@Composable
private fun ModeFields(state: RecurringEditorUiState, callbacks: RecurringEditorCallbacks) {
    SectionTitle(stringResource(R.string.feature_transactions_recurring_mode))
    ModeRow(
        selected = !state.autoCreate,
        title = stringResource(R.string.feature_transactions_recurring_remind),
        hint = stringResource(R.string.feature_transactions_recurring_remind_hint),
        onClick = { callbacks.onAutoCreateChange(false) },
    )
    ModeRow(
        selected = state.autoCreate,
        title = stringResource(R.string.feature_transactions_recurring_auto),
        hint = stringResource(R.string.feature_transactions_recurring_auto_hint),
        onClick = { callbacks.onAutoCreateChange(true) },
    )
    if (!state.autoCreate) {
        Text(
            stringResource(R.string.feature_transactions_recurring_tolerance) + ": " +
                if (state.tolerance == 0) {
                    stringResource(R.string.feature_transactions_recurring_exact)
                } else {
                    stringResource(R.string.feature_transactions_recurring_percent, state.tolerance)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = state.tolerance.toFloat(),
            onValueChange = { callbacks.onToleranceChange(it.toInt()) },
            valueRange = 0f..MAX_TOLERANCE,
            steps = TOLERANCE_STEPS,
        )
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/** A read-only field that opens a picker. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    error: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        leadingIcon = leading,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outline
            },
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun typeName(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.EXPENSE -> R.string.feature_transactions_form_type_expense
        TransactionType.INCOME -> R.string.feature_transactions_form_type_income
        TransactionType.TRANSFER -> R.string.feature_transactions_form_type_transfer
    },
)

@Composable
private fun frequencyName(frequency: Frequency): String = stringResource(
    when (frequency) {
        Frequency.DAILY -> R.string.feature_transactions_frequency_daily
        Frequency.WEEKLY -> R.string.feature_transactions_frequency_weekly
        Frequency.MONTHLY -> R.string.feature_transactions_frequency_monthly
        Frequency.YEARLY -> R.string.feature_transactions_frequency_yearly
    },
)

@Composable
private fun unitName(frequency: Frequency, count: Int): String = pluralStringResource(
    when (frequency) {
        Frequency.DAILY -> R.plurals.feature_transactions_unit_days
        Frequency.WEEKLY -> R.plurals.feature_transactions_unit_weeks
        Frequency.MONTHLY -> R.plurals.feature_transactions_unit_months
        Frequency.YEARLY -> R.plurals.feature_transactions_unit_years
    },
    count,
)

@Composable
private fun endModeName(mode: EndMode): String = stringResource(
    when (mode) {
        EndMode.NEVER -> R.string.feature_transactions_recurring_end_never
        EndMode.DATE -> R.string.feature_transactions_recurring_end_on_date
        EndMode.COUNT -> R.string.feature_transactions_recurring_end_after_count
    },
)

private val INTERVAL_FIELD_WIDTH = 120.dp
private const val PREVIEW_DATES = 3
private const val LEAP_YEAR = 2024
private const val MAX_TOLERANCE = 50f
private const val TOLERANCE_STEPS = 9
