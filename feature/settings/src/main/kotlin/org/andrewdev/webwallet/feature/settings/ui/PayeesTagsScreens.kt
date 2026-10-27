package org.andrewdev.webwallet.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.andrewdev.webwallet.core.designsystem.theme.PaletteColor
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.model.Payee
import org.andrewdev.webwallet.core.model.Tag
import org.andrewdev.webwallet.core.network.ApiError
import org.andrewdev.webwallet.core.ui.BottomSheetMenu
import org.andrewdev.webwallet.core.ui.ConfirmDialog
import org.andrewdev.webwallet.core.ui.EmptyState
import org.andrewdev.webwallet.core.ui.ErrorState
import org.andrewdev.webwallet.core.ui.MenuItem
import org.andrewdev.webwallet.core.ui.SkeletonList
import org.andrewdev.webwallet.core.ui.message
import org.andrewdev.webwallet.core.ui.rememberErrorReporter
import org.andrewdev.webwallet.feature.settings.PayeesUiState
import org.andrewdev.webwallet.feature.settings.PayeesViewModel
import org.andrewdev.webwallet.feature.settings.R
import org.andrewdev.webwallet.feature.settings.TagsUiState
import org.andrewdev.webwallet.feature.settings.TagsViewModel
import java.util.UUID

// --- payees

@Composable
fun PayeesRoute(onBack: () -> Unit, viewModel: PayeesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    PayeesScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onRefresh = viewModel::refresh,
        onRename = { payee, name -> viewModel.rename(payee, name, showError) },
        onDelete = { viewModel.delete(it, showError) },
    )
}

/** The payees (docs/04 §14 Payees): searched, renamed in a sheet, deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayeesScreen(
    state: PayeesUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
    onRename: (Payee, String) -> Unit,
    onDelete: (Payee) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Payee?>(null) }
    var renaming by remember { mutableStateOf<Payee?>(null) }
    var deleting by remember { mutableStateOf<Payee?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_payees)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.feature_settings_payees_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.feature_settings_payees_filter)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )
            when {
                state.loading -> SkeletonList(rows = 6)
                state.error != null -> ErrorState(state.error, onRetry = onRefresh)
                state.shown.isEmpty() -> EmptyState(title = stringResource(R.string.feature_settings_payees_empty))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.shown, key = { it.id }) { payee ->
                        ListItem(
                            headlineContent = { Text(payee.name) },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.feature_settings_payees_operations,
                                        payee.operations.toInt(),
                                        payee.operations.toInt(),
                                    ),
                                )
                            },
                            modifier = Modifier.clickable { menuFor = payee },
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { payee ->
        BottomSheetMenu(
            title = payee.name,
            items = listOf(
                MenuItem(stringResource(R.string.feature_settings_payees_rename)) { renaming = payee },
                MenuItem(
                    stringResource(R.string.feature_settings_payees_delete),
                    destructive = true,
                ) { deleting = payee },
            ),
            onDismiss = { menuFor = null },
        )
    }
    renaming?.let { payee ->
        NameSheet(
            title = stringResource(R.string.feature_settings_payees_rename),
            initial = payee.name,
            onSave = { name ->
                renaming = null
                onRename(payee, name)
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { payee ->
        ConfirmDialog(
            title = stringResource(R.string.feature_settings_payees_delete_title, payee.name),
            body = stringResource(R.string.feature_settings_payees_delete_text),
            confirmText = stringResource(R.string.feature_settings_payees_delete),
            destructive = true,
            onConfirm = {
                deleting = null
                onDelete(payee)
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.feature_settings_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_settings_save))
            }
        }
    }
}

// --- tags

@Composable
fun TagsRoute(onBack: () -> Unit, viewModel: TagsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val showError = rememberErrorReporter(snackbar)
    TagsScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSave = { tag, name, color, key, onResult -> viewModel.save(tag, name, color, key, onResult) },
        onDelete = { viewModel.delete(it, showError) },
    )
}

/** The tags (docs/04 §14 Tags): colour dots, an editor sheet for the name and the colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    state: TagsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (Tag?, String, String?, UUID, (ApiError?) -> Unit) -> Unit,
    onDelete: (Tag) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Tag?>(null) }
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    val dark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_tags)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
                actions = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Lucide.Plus, contentDescription = stringResource(R.string.feature_settings_tags_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.feature_settings_tags_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            when {
                state.loading -> SkeletonList(rows = 4)
                state.error != null -> ErrorState(state.error, onRetry = onRefresh)
                state.tags.isEmpty() -> EmptyState(
                    title = stringResource(R.string.feature_settings_tags_empty),
                    action = stringResource(R.string.feature_settings_tags_add),
                    onAction = { creating = true },
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tags, key = { it.id }) { tag ->
                        ListItem(
                            headlineContent = { Text("#${tag.name}") },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(DOT)
                                        .background(PaletteColor.of(tag.color, tag.id).color(dark), CircleShape),
                                )
                            },
                            modifier = Modifier.clickable { menuFor = tag },
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { tag ->
        BottomSheetMenu(
            title = "#${tag.name}",
            items = listOf(
                MenuItem(stringResource(R.string.feature_settings_categories_edit)) { editing = tag },
                MenuItem(stringResource(R.string.feature_settings_tags_delete), destructive = true) { deleting = tag },
            ),
            onDismiss = { menuFor = null },
        )
    }
    if (creating || editing != null) {
        TagSheet(
            tag = editing,
            onSave = { name, color, key, onResult ->
                onSave(editing, name, color, key) { error ->
                    onResult(error)
                    if (error == null) {
                        creating = false
                        editing = null
                    }
                }
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.feature_settings_tags_delete_title, tag.name),
            body = stringResource(R.string.feature_settings_tags_delete_text),
            confirmText = stringResource(R.string.feature_settings_tags_delete),
            destructive = true,
            onConfirm = {
                deleting = null
                onDelete(tag)
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(tag: Tag?, onSave: (String, String?, UUID, (ApiError?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(tag?.name.orEmpty()) }
    var color by remember { mutableStateOf(tag?.color) }
    var error by remember { mutableStateOf<ApiError?>(null) }
    var saving by remember { mutableStateOf(false) }
    val key = remember { UUID.randomUUID() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(
                    if (tag == null) {
                        R.string.feature_settings_tags_new_title
                    } else {
                        R.string.feature_settings_tags_edit_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trimStart('#') },
                label = { Text(stringResource(R.string.feature_settings_name)) },
                prefix = { Text("#") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ColorRow(selected = color, onPick = { color = it })
            error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    saving = true
                    onSave(name, color, key) {
                        saving = false
                        error = it
                    }
                },
                enabled = name.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feature_settings_save)) }
        }
    }
}

private val DOT = 16.dp
