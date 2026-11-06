package org.andrewdev.webwallet.feature.imports.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.designsystem.theme.WwTheme
import org.andrewdev.webwallet.core.model.UploadFile
import org.andrewdev.webwallet.core.ui.formatters
import org.andrewdev.webwallet.feature.imports.R
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The backend refuses larger uploads; checked here to say so before sending. */
private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
private const val TICK_MS = 1_000L

private val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Why a picked file was not taken. */
enum class FileProblem { TOO_BIG, UNREADABLE }

/** Reads a document the user picked into memory, with its display name and type. */
suspend fun readUpload(context: Context, uri: Uri, fallbackMime: String): Result<UploadFile> =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { name = it }
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
            if (size > MAX_UPLOAD_BYTES) throw FileTooBig()
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No stream")
            if (bytes.size > MAX_UPLOAD_BYTES) throw FileTooBig()
            UploadFile(name, resolver.getType(uri) ?: fallbackMime, bytes)
        }
    }

class FileTooBig : RuntimeException()

/** A file field: the chosen name or a "choose" button, opening the system picker for [mimeTypes]. */
@Composable
fun FileField(
    label: String,
    file: UploadFile?,
    mimeTypes: Array<String>,
    onFile: (UploadFile?) -> Unit,
    onProblem: (FileProblem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            readUpload(context, uri, mimeTypes.first())
                .onSuccess(onFile)
                .onFailure { onProblem(if (it is FileTooBig) FileProblem.TOO_BIG else FileProblem.UNREADABLE) }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (file == null) {
            OutlinedButton(onClick = { launcher.launch(mimeTypes) }) {
                Icon(Lucide.FileText, contentDescription = null)
                Text(stringResource(R.string.feature_imports_choose_file), Modifier.padding(start = Spacing.sm))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.FileText, contentDescription = null)
                Text(
                    file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm).clickable { launcher.launch(mimeTypes) },
                )
                IconButton(onClick = { onFile(null) }) { Icon(Lucide.X, contentDescription = null) }
            }
        }
    }
}

/** A read-only field that opens a choice; used instead of dropdowns, which are awkward on a phone. */
@Composable
fun ChoiceField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    warn: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                error != null -> MaterialTheme.colorScheme.error
                warn -> WwTheme.colors.warnText
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** One option of [PickerSheet]; [indent] shows a subcategory under its parent. */
data class PickerOption<T>(val value: T, val label: String, val supporting: String? = null, val indent: Boolean = false)

/** A searchable list in a bottom sheet; the search appears only when the list is long. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerSheet(
    title: String,
    options: List<PickerOption<T>>,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        if (options.size > SEARCH_FROM) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            )
        }
        val shown = if (query.isBlank()) options else options.filter { it.label.contains(query.trim(), true) }
        LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            items(shown) { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    supportingContent = option.supporting?.let { { Text(it) } },
                    trailingContent = if (option.value == selected) ({ Text("✓") }) else null,
                    modifier = Modifier
                        .clickable {
                            onPick(option.value)
                            onDismiss()
                        }
                        .padding(start = if (option.indent) Spacing.xl else 0.dp),
                )
            }
        }
    }
}

private const val SEARCH_FROM = 8

/** A button with the time that opens the Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(label: @Composable (String) -> String, time: LocalTime, onTime: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(label(time.format(HHMM))) }
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text(stringResource(org.andrewdev.webwallet.core.ui.R.string.core_ui_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.feature_imports_cancel)) }
            },
            text = { TimePicker(state = state) },
        )
    }
}

/** "5 лист. 14:30" in Kyiv time, like the web's date with time. */
@Composable
fun dateTime(value: OffsetDateTime?): String {
    if (value == null) return stringResource(R.string.feature_imports_none)
    val local = value.atZoneSameInstant(KYIV)
    return formatters.date.dayMonth(local.toLocalDate()) + " " + local.format(HHMM)
}

/** The current moment, ticking every second while shown; for the countdowns of the bank's limits. */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    return Instant.ofEpochMilli(now)
}

/** The message of a file that could not be taken. */
@Composable
fun FileProblem.message(): String = stringResource(
    when (this) {
        FileProblem.TOO_BIG -> R.string.feature_imports_file_too_big
        FileProblem.UNREADABLE -> R.string.feature_imports_file_unreadable
    },
)
