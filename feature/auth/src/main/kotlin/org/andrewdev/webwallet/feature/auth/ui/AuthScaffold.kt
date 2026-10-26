package org.andrewdev.webwallet.feature.auth.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.SunMoon
import org.andrewdev.webwallet.core.designsystem.theme.Spacing
import org.andrewdev.webwallet.core.ui.Logo
import org.andrewdev.webwallet.core.ui.WwCard
import org.andrewdev.webwallet.feature.auth.R

const val PRIVACY_URL = "https://wallet.andrew-dev.org/privacy"
const val TERMS_URL = "https://wallet.andrew-dev.org/terms"

/** Opens a legal page in Chrome Custom Tabs (docs/03 §8: no WebView). */
fun openInBrowser(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

/**
 * The layout of every auth screen (docs/04 §1): logo, title, a card with the form, the language and theme toggles
 * at the top right, the legal links at the bottom.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String?,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onToggleLanguage) {
                Icon(Lucide.Languages, contentDescription = stringResource(R.string.feature_auth_language))
            }
            IconButton(onClick = onToggleTheme) {
                Icon(Lucide.SunMoon, contentDescription = stringResource(R.string.feature_auth_theme))
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        Logo()
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
        WwCard(modifier = Modifier.widthIn(max = 420.dp)) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                content()
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(Spacing.md))
            footer()
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.xxl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { openInBrowser(context, PRIVACY_URL) },
            ) { Text(stringResource(R.string.feature_auth_privacy)) }
            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick = { openInBrowser(context, TERMS_URL) },
            ) { Text(stringResource(R.string.feature_auth_terms)) }
        }
    }
}

@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.feature_auth_email)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.EmailAddress },
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    newPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Lucide.EyeOff else Lucide.Eye,
                    contentDescription = stringResource(
                        if (visible) R.string.feature_auth_hide_password else R.string.feature_auth_show_password,
                    ),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
    )
}
