package org.andrewdev.webwallet.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.Rule
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.settings.R
import org.andrewdev.webwallet.feature.settings.RuleConditionType
import org.andrewdev.webwallet.feature.settings.RuleEditorUiState
import org.andrewdev.webwallet.feature.settings.RuleEditorViewModel
import org.andrewdev.webwallet.feature.settings.RulesUiState
import org.andrewdev.webwallet.feature.settings.RulesViewModel

@Composable
fun RulesRoute(openEditor: (id: Long?) -> Unit, onBack: () -> Unit, viewModel: RulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    val scope = rememberCoroutineScope()
    RulesScreen(
        state = state,
        snackbar = snackbar,
        openEditor = openEditor,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onDelete = { viewModel.delete(it, showError) },
        onApply = { message ->
            viewModel.apply { result ->
                when (result) {
                    is ApiResult.Ok -> scope.launch { snackbar.showSnackbar(message(result.value)) }
                    is ApiResult.Err -> showError(result.error)
                }
            }
        },
    )
}

/** The categorization rules (docs/04 §14 Rules): condition → category, learned ones marked, applied on demand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: RulesUiState,
    snackbar: SnackbarHostState,
    openEditor: (id: Long?) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (Rule) -> Unit,
    onApply: (message: (Int) -> String) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Rule?>(null) }
    var deleting by remember { mutableStateOf<Rule?>(null) }
    val applied = AppliedText()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_rules)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
                actions = {
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.feature_settings_rules_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.feature_settings_rules_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            Button(
                onClick = { onApply(applied) },
                enabled = !state.applying,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) { Text(stringResource(R.string.feature_settings_rules_apply)) }
            when {
                state.loading -> SkeletonList(rows = 5)
                state.error != null -> ErrorState(state.error, onRetry = onRefresh)
                state.rules.isEmpty() -> EmptyState(
                    title = stringResource(R.string.feature_settings_rules_empty),
                    action = stringResource(R.string.feature_settings_rules_add),
                    onAction = { openEditor(null) },
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rules, key = { it.id }) { rule ->
                        val category = state.categoriesById[rule.categoryId]
                        ListItem(
                            headlineContent = { Text(conditionText(rule)) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        category?.name,
                                        stringResource(R.string.feature_settings_rules_learned).takeIf { rule.learned },
                                    ).joinToString(" · "),
                                )
                            },
                            leadingContent = { CategoryIcon(category, size = 32.dp) },
                            modifier = Modifier.clickable { menuFor = rule },
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { rule ->
        BottomSheetMenu(
            title = conditionText(rule),
            items = listOf(
                MenuItem(stringResource(R.string.feature_settings_categories_edit)) { openEditor(rule.id) },
                MenuItem(
                    stringResource(R.string.feature_settings_rules_delete),
                    destructive = true,
                ) { deleting = rule },
            ),
            onDismiss = { menuFor = null },
        )
    }
    deleting?.let { rule ->
        ConfirmDialog(
            title = stringResource(R.string.feature_settings_rules_delete_title),
            confirmText = stringResource(R.string.feature_settings_rules_delete),
            destructive = true,
            onConfirm = {
                deleting = null
                onDelete(rule)
            },
            onDismiss = { deleting = null },
        )
    }
}

/** The "categorized N transactions" text as a function of the count, resolved while composing. */
@Composable
@Suppress("MagicNumber")
private fun AppliedText(): (Int) -> String {
    val one = pluralStringResource(R.plurals.feature_settings_rules_applied, 1, 1)
    val few = pluralStringResource(R.plurals.feature_settings_rules_applied, 2, 2)
    val many = pluralStringResource(R.plurals.feature_settings_rules_applied, 5, 5)
    val zero = pluralStringResource(R.plurals.feature_settings_rules_applied, 0, 0)
    return { count ->
        when {
            count == 0 -> zero
            count % 10 == 1 && count % 100 != 11 -> one.replace("1", count.toString())
            count % 10 in 2..4 && count % 100 !in 12..14 -> few.replace("2", count.toString())
            else -> many.replace("5", count.toString())
        }
    }
}

@Composable
private fun conditionText(rule: Rule): String {
    val bank = rule.bankCategory
    val pattern = rule.payeePattern
    val payee = rule.payee
    return when {
        bank != null -> stringResource(R.string.feature_settings_rules_bank_category, bank)
        pattern != null -> stringResource(R.string.feature_settings_rules_payee_contains, pattern)
        payee != null -> stringResource(R.string.feature_settings_rules_payee_is, payee.name)
        rule.mcc != null -> "MCC ${rule.mcc}"
        else -> ""
    }
}

// --- editor

@Composable
fun RuleEditorRoute(id: Long?, onClose: () -> Unit, viewModel: RuleEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    RuleEditorScreen(
        state = state,
        onType = viewModel::onTypeChange,
        onValue = viewModel::onValueChange,
        onCategory = viewModel::onCategoryChange,
        onSave = { viewModel.save(onClose) },
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    state: RuleEditorUiState,
    onType: (RuleConditionType) -> Unit,
    onValue: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var pickCategory by remember { mutableStateOf(false) }
    val required = stringResource(R.string.feature_settings_required)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_rules_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Lucide.X, contentDescription = stringResource(R.string.feature_settings_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.saving && state.loaded) {
                        Text(stringResource(R.string.feature_settings_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.feature_settings_rules_condition), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RuleConditionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { onType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, RuleConditionType.entries.size),
                        label = {
                            Text(
                                stringResource(
                                    when (type) {
                                        RuleConditionType.PAYEE -> R.string.feature_settings_rules_type_payee
                                        RuleConditionType.BANK -> R.string.feature_settings_rules_type_bank
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            OutlinedTextField(
                value = state.value,
                onValueChange = onValue,
                label = {
                    Text(
                        stringResource(
                            when (state.type) {
                                RuleConditionType.PAYEE -> R.string.feature_settings_rules_payee_label
                                RuleConditionType.BANK -> R.string.feature_settings_rules_bank_label
                            },
                        ),
                    )
                },
                isError = state.valueMissing,
                supportingText = required.takeIf { state.valueMissing }?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.category?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.feature_settings_rules_category)) },
                isError = state.categoryMissing,
                supportingText = required.takeIf { state.categoryMissing }?.let { { Text(it) } },
                leadingIcon = state.category?.let { { CategoryIcon(it, size = 24.dp) } },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = if (state.categoryMissing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().clickable { pickCategory = true },
            )
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickCategory) {
        CategoryTreeSheet(
            categories = state.categories,
            onPick = {
                onCategory(it.id)
                pickCategory = false
            },
            onDismiss = { pickCategory = false },
        )
    }
}

/** Groups and their subcategories, any of which can be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryTreeSheet(categories: List<Category>, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = Spacing.xxl)) {
            categories.forEach { group ->
                item(key = group.id) {
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = { CategoryIcon(group, size = 32.dp) },
                        trailingContent = {
                            SuggestionChip(onClick = { onPick(group) }, label = { Text(kindName(group.kind)) })
                        },
                        modifier = Modifier.clickable { onPick(group) },
                    )
                }
                items(group.children, key = { it.id }) { child ->
                    ListItem(
                        headlineContent = { Text(child.name) },
                        leadingContent = { CategoryIcon(child, size = 28.dp) },
                        modifier = Modifier.padding(start = Spacing.xl).clickable { onPick(child) },
                    )
                }
            }
        }
    }
}
