package org.andrewdev.webwallet.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import org.andrewdev.webwallet.core.designsystem.icons.CategoryIcons
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Category
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.CategoryNature
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.CategoryIcon
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.IconTile
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.settings.CategoriesActions
import org.andrewdev.webwallet.feature.settings.CategoriesUiState
import org.andrewdev.webwallet.feature.settings.CategoriesViewModel
import org.andrewdev.webwallet.feature.settings.CategoryEditorUiState
import org.andrewdev.webwallet.feature.settings.CategoryEditorViewModel
import org.andrewdev.webwallet.feature.settings.R

@Composable
fun CategoriesRoute(actions: CategoriesActions, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    CategoriesScreen(
        state = state,
        snackbar = snackbar,
        actions = actions,
        onKind = viewModel::setKind,
        onRefresh = viewModel::refresh,
        onArchive = { category, archived -> viewModel.setArchived(category, archived, showError) },
        onDelete = { viewModel.delete(it, showError) },
        onMove = { active, over -> viewModel.move(active, over, showError) },
    )
}

/**
 * The categories (docs/04 §14): expense or income, groups with their subcategories; a row's menu edits, adds a
 * subcategory, moves it up or down, archives or deletes it. Moving replaces the drag of the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    snackbar: SnackbarHostState,
    actions: CategoriesActions,
    onKind: (CategoryKind) -> Unit,
    onRefresh: () -> Unit,
    onArchive: (Category, Boolean) -> Unit,
    onDelete: (Category) -> Unit,
    onMove: (activeId: Long, overId: Long) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val flat = state.groups.flatMap { listOf(it) + it.children }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_categories)) },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(Lucide.ArrowLeft, contentDescription = null) }
                },
                actions = {
                    IconButton(onClick = { actions.openEditor(null, state.kind, null) }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.feature_settings_categories_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                CategoryKind.entries.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = state.kind == kind,
                        onClick = { onKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, CategoryKind.entries.size),
                        label = { Text(kindName(kind)) },
                    )
                }
            }
            Text(
                stringResource(R.string.feature_settings_categories_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            when {
                state.loading -> SkeletonList(rows = 6)
                state.error != null -> ErrorState(state.error, onRetry = onRefresh)
                flat.isEmpty() -> EmptyState(
                    title = stringResource(R.string.feature_settings_categories_empty),
                    action = stringResource(R.string.feature_settings_categories_add),
                    onAction = { actions.openEditor(null, state.kind, null) },
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        item(key = group.id) { CategoryRow(group, child = false, state.busy) { menuFor = group } }
                        group.children.forEach { child ->
                            item(key = child.id) { CategoryRow(child, child = true, state.busy) { menuFor = child } }
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { category ->
        val index = flat.indexOfFirst { it.id == category.id }
        val above = flat.getOrNull(index - 1)
        val below = flat.getOrNull(index + 1)
        BottomSheetMenu(
            title = category.name,
            items = buildList {
                add(
                    MenuItem(stringResource(R.string.feature_settings_categories_edit)) {
                        actions.openEditor(category.id, category.kind, null)
                    },
                )
                if (category.parentId == null) {
                    add(
                        MenuItem(stringResource(R.string.feature_settings_categories_add_child)) {
                            actions.openEditor(null, category.kind, category.id)
                        },
                    )
                }
                if (above != null) {
                    add(
                        MenuItem(stringResource(R.string.feature_settings_categories_move_up), Lucide.ArrowUp) {
                            onMove(category.id, above.id)
                        },
                    )
                }
                if (below != null) {
                    add(
                        MenuItem(stringResource(R.string.feature_settings_categories_move_down), Lucide.ArrowDown) {
                            onMove(category.id, below.id)
                        },
                    )
                }
                add(
                    MenuItem(
                        stringResource(
                            if (category.archived) {
                                R.string.feature_settings_categories_unarchive
                            } else {
                                R.string.feature_settings_categories_archive
                            },
                        ),
                    ) { onArchive(category, !category.archived) },
                )
                if (category.children.isEmpty()) {
                    add(
                        MenuItem(stringResource(R.string.feature_settings_categories_delete), destructive = true) {
                            deleting = category
                        },
                    )
                }
            },
            onDismiss = { menuFor = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = stringResource(R.string.feature_settings_categories_delete_title, category.name),
            body = stringResource(R.string.feature_settings_categories_delete_text),
            confirmText = stringResource(R.string.feature_settings_categories_delete),
            destructive = true,
            onConfirm = {
                deleting = null
                onDelete(category)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, child: Boolean, busy: Set<Long>, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                category.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (category.archived) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = {
            val notes = buildList {
                if (category.archived) add(stringResource(R.string.feature_settings_categories_archived))
                if (category.carryOver) add(stringResource(R.string.feature_settings_categories_carry_over))
                if (category.kind == CategoryKind.EXPENSE && category.nature != CategoryNature.INHERIT) {
                    add(natureName(category.nature))
                }
            }
            if (notes.isNotEmpty()) Text(notes.joinToString(" · "))
        },
        leadingContent = { CategoryIcon(category, size = if (child) 28.dp else 36.dp) },
        modifier = Modifier
            .padding(start = if (child) Spacing.xl else 0.dp)
            .clickable(enabled = category.id !in busy, onClick = onClick),
    )
}

@Composable
fun kindName(kind: CategoryKind): String = stringResource(
    when (kind) {
        CategoryKind.EXPENSE -> R.string.feature_settings_categories_expense
        CategoryKind.INCOME -> R.string.feature_settings_categories_income
    },
)

@Composable
fun natureName(nature: CategoryNature): String = stringResource(
    when (nature) {
        CategoryNature.NEEDS -> R.string.feature_settings_categories_needs
        CategoryNature.WANTS -> R.string.feature_settings_categories_wants
        CategoryNature.INHERIT -> R.string.feature_settings_categories_inherit
    },
)

// --- editor

@Composable
fun CategoryEditorRoute(
    id: Long?,
    kind: CategoryKind,
    parentId: Long?,
    onClose: () -> Unit,
    viewModel: CategoryEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id, kind, parentId) }
    CategoryEditorScreen(
        state = state,
        onNameChange = viewModel::onNameChange,
        onParentChange = viewModel::onParentChange,
        onIconChange = viewModel::onIconChange,
        onColorChange = viewModel::onColorChange,
        onNatureChange = viewModel::onNatureChange,
        onArchivedChange = viewModel::onArchivedChange,
        onCarryOverChange = viewModel::onCarryOverChange,
        onSave = { viewModel.save(onClose) },
        onClose = onClose,
    )
}

/** The category editor (docs/04 §14): name, group, icon, colour, needs/wants, carry-over, archive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
fun CategoryEditorScreen(
    state: CategoryEditorUiState,
    onNameChange: (String) -> Unit,
    onParentChange: (Long?) -> Unit,
    onIconChange: (String?) -> Unit,
    onColorChange: (String?) -> Unit,
    onNatureChange: (CategoryNature) -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onCarryOverChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var pickParent by remember { mutableStateOf(false) }
    val required = stringResource(R.string.feature_settings_required)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.feature_settings_categories_new_title
                            } else {
                                R.string.feature_settings_categories_edit_title
                            },
                        ),
                    )
                },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.feature_settings_categories_name)) },
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
            Text(
                stringResource(R.string.feature_settings_categories_kind) + ": " + kindName(state.kind),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.parent?.name ?: stringResource(R.string.feature_settings_categories_no_parent),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.feature_settings_categories_parent)) },
                supportingText = if (state.parentLocked) {
                    { Text(stringResource(R.string.feature_settings_categories_parent_locked)) }
                } else {
                    null
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.parentLocked) { pickParent = true },
            )
            Text(stringResource(R.string.feature_settings_categories_icon), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.feature_settings_categories_inherits_icon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconGrid(selected = state.icon, color = state.color, id = state.editing?.id ?: 0, onPick = onIconChange)
            Text(
                stringResource(R.string.feature_settings_categories_color),
                style = MaterialTheme.typography.titleSmall,
            )
            ColorRow(selected = state.color, onPick = onColorChange)
            if (state.kind == CategoryKind.EXPENSE) {
                Text(
                    stringResource(R.string.feature_settings_categories_necessity_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.feature_settings_categories_necessity_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CategoryNature.entries.forEachIndexed { index, nature ->
                        SegmentedButton(
                            selected = state.nature == nature,
                            onClick = { onNatureChange(nature) },
                            shape = SegmentedButtonDefaults.itemShape(index, CategoryNature.entries.size),
                            label = { Text(natureName(nature)) },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.feature_settings_categories_carry_over_switch),
                    hint = stringResource(R.string.feature_settings_categories_carry_over_help),
                    checked = state.carryOver,
                    onChange = onCarryOverChange,
                )
            }
            SwitchRow(
                title = stringResource(R.string.feature_settings_categories_archived_switch),
                hint = stringResource(R.string.feature_settings_categories_archived_help),
                checked = state.archived,
                onChange = onArchivedChange,
            )
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickParent) {
        ModalBottomSheet(onDismissRequest = { pickParent = false }) {
            Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.feature_settings_categories_no_parent)) },
                    modifier = Modifier.clickable {
                        onParentChange(null)
                        pickParent = false
                    },
                )
                state.groups.forEach { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = { CategoryIcon(group, size = 32.dp) },
                        modifier = Modifier.clickable {
                            onParentChange(group.id)
                            pickParent = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconGrid(selected: String?, color: String?, id: Long, onPick: (String?) -> Unit) {
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
internal fun ColorRow(selected: String?, onPick: (String?) -> Unit) {
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

private val SWATCH = 36.dp
