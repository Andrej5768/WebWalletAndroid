package org.andrewdev.webwallet.feature.goals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.icons.CategoryIcons
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.ui.AccountTypeIcon
import org.andrewdev.webwallet.core.ui.DatePickerDialogOf
import org.andrewdev.webwallet.core.ui.IconTile
import org.andrewdev.webwallet.core.ui.MoneyField
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.feature.goals.GoalEditorCallbacks
import org.andrewdev.webwallet.feature.goals.GoalEditorUiState
import org.andrewdev.webwallet.feature.goals.GoalEditorViewModel
import org.andrewdev.webwallet.feature.goals.R

@Composable
fun GoalEditorRoute(id: Long?, onClose: () -> Unit, viewModel: GoalEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    GoalEditorScreen(
        state = state,
        callbacks = GoalEditorCallbacks(
            onNameChange = viewModel::onNameChange,
            onTargetChange = viewModel::onTargetChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onDateChange = viewModel::onDateChange,
            onAccountChange = viewModel::onAccountChange,
            onSavedChange = viewModel::onSavedChange,
            onIconChange = viewModel::onIconChange,
            onColorChange = viewModel::onColorChange,
            onSave = { viewModel.save(onClose) },
            onClose = onClose,
        ),
    )
}

/** The goal editor (docs/04 §10.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun GoalEditorScreen(state: GoalEditorUiState, callbacks: GoalEditorCallbacks) {
    var pickAccount by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    val f = formatters
    val required = stringResource(R.string.feature_goals_form_required)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.feature_goals_form_new_title
                            } else {
                                R.string.feature_goals_form_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onClose) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.feature_goals_form_close))
                    }
                },
                actions = {
                    TextButton(onClick = callbacks.onSave, enabled = !state.saving && state.loaded) {
                        Text(stringResource(R.string.feature_goals_form_save))
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
            OutlinedTextField(
                value = state.name,
                onValueChange = callbacks.onNameChange,
                label = { Text(stringResource(R.string.feature_goals_form_name)) },
                isError = state.nameMissing || "name" in state.fieldErrors,
                supportingText = (state.fieldErrors["name"] ?: required.takeIf { state.nameMissing })?.let { {
                    Text(
                        it,
                    )
                }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                text = state.targetText,
                onTextChange = { callbacks.onTargetChange(it, state.target) },
                onValueChange = { callbacks.onTargetChange(state.targetText, it) },
                currency = state.effectiveCurrency,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.feature_goals_form_target),
                error = state.fieldErrors["targetAmount"] ?: required.takeIf { state.targetMissing },
            )
            if (state.account == null) {
                PickerField(
                    label = stringResource(R.string.feature_goals_form_currency),
                    value = state.currency,
                    onClick = { pickCurrency = true },
                )
            }
            PickerField(
                label = stringResource(R.string.feature_goals_form_date),
                value = state.targetDate?.let { f.date.long(it) }.orEmpty(),
                hint = stringResource(R.string.feature_goals_form_date_hint),
                onClick = { pickDate = true },
                onClear = state.targetDate?.let { { callbacks.onDateChange(null) } },
            )
            PickerField(
                label = stringResource(R.string.feature_goals_form_account),
                value = state.account?.name ?: stringResource(R.string.feature_goals_form_no_account),
                hint = stringResource(
                    if (state.account != null) {
                        R.string.feature_goals_form_account_hint
                    } else {
                        R.string.feature_goals_form_manual_hint
                    },
                ),
                leading = state.account?.let { { AccountTypeIcon(it.type, size = 24.dp) } },
                onClick = { pickAccount = true },
            )
            if (state.account == null) {
                MoneyField(
                    text = state.savedText,
                    onTextChange = { callbacks.onSavedChange(it, state.saved) },
                    onValueChange = { callbacks.onSavedChange(state.savedText, it) },
                    currency = state.effectiveCurrency,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.feature_goals_form_saved),
                )
            }
            Text(stringResource(R.string.feature_goals_form_icon), style = MaterialTheme.typography.titleSmall)
            IconGrid(
                selected = state.icon,
                color = state.color,
                id = state.editing?.id ?: 0,
                onPick = callbacks.onIconChange,
            )
            Text(stringResource(R.string.feature_goals_form_color), style = MaterialTheme.typography.titleSmall)
            ColorRow(selected = state.color, onPick = callbacks.onColorChange)
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickAccount) {
        AccountSheet(
            accounts = state.accounts,
            onPick = {
                callbacks.onAccountChange(it)
                pickAccount = false
            },
            onDismiss = { pickAccount = false },
        )
    }
    if (pickCurrency) {
        CurrencySheet(
            onPick = {
                callbacks.onCurrencyChange(it)
                pickCurrency = false
            },
            onDismiss = { pickCurrency = false },
        )
    }
    if (pickDate) {
        DatePickerDialogOf(state.targetDate ?: f.today, onPick = {
            callbacks.onDateChange(it)
            pickDate = false
        }, onDismiss = { pickDate = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconGrid(selected: String?, color: String?, id: Long, onPick: (String?) -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = PaletteColor.of(color, id)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CategoryIcons.byName.forEach { (name, icon) ->
            val chosen = name == selected
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        if (chosen) {
                            Modifier.border(
                                2.dp,
                                palette.color(dark),
                                MaterialTheme.shapes.medium,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(if (chosen) null else name) },
            ) {
                IconTile(icon, tint = palette.color(dark), container = palette.container(dark))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(selected: String?, onPick: (String?) -> Unit) {
    val dark = isSystemInDarkTheme()
    val outline = MaterialTheme.colorScheme.onSurface
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PaletteColor.entries.forEach { palette ->
            val chosen = palette.key == selected
            Box(
                modifier = Modifier
                    .size(SWATCH)
                    .clip(CircleShape)
                    .then(if (chosen) Modifier.border(3.dp, outline, CircleShape) else Modifier)
                    .padding(if (chosen) 4.dp else 0.dp)
                    .clip(CircleShape)
                    .background(palette.color(dark))
                    .clickable { onPick(if (chosen) null else palette.key) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    accounts: List<org.andrewdev.webwallet.core.model.Account>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.feature_goals_form_no_account)) },
                modifier = Modifier.clickable { onPick(null) },
            )
            accounts.forEach { account ->
                ListItem(
                    headlineContent = { Text(account.name) },
                    supportingContent = { Text(account.currency.code) },
                    leadingContent = { AccountTypeIcon(account.type, size = 32.dp) },
                    modifier = Modifier.clickable { onPick(account.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
            CURRENCIES.forEach { code ->
                ListItem(headlineContent = { Text(code) }, modifier = Modifier.clickable { onPick(code) })
            }
        }
    }
}

/** A read-only field that opens a picker; [onClear] adds a clear action. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    hint: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        leadingIcon = leading,
        trailingIcon = onClear?.let {
            {
                IconButton(onClick = it) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.feature_goals_form_clear))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

private val SWATCH = 36.dp
private val CURRENCIES = listOf("UAH", "USD", "EUR", "PLN", "GBP", "CZK", "CHF")
