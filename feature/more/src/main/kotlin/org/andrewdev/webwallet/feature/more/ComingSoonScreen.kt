package org.andrewdev.webwallet.feature.more

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.andrewdev.webwallet.core.ui.EmptyState

/** A section of a later milestone (docs/01 §5): named, with a way to the web page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(section: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = when (section) {
        "reports", "analytics" -> stringResource(R.string.feature_more_reports)
        "goals" -> stringResource(R.string.feature_more_goals)
        "investments" -> stringResource(R.string.feature_more_investments)
        "taxes" -> stringResource(R.string.feature_more_taxes)
        else -> stringResource(R.string.feature_more_imports)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, contentDescription = null) } },
            )
        },
    ) { padding ->
        EmptyState(
            title = stringResource(R.string.feature_more_coming_soon_title),
            body = stringResource(R.string.feature_more_coming_soon_text),
            action = stringResource(R.string.feature_more_open_web),
            onAction = {
                CustomTabsIntent.Builder().build().launchUrl(
                    context,
                    Uri.parse("https://wallet.andrew-dev.org/$section"),
                )
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}
