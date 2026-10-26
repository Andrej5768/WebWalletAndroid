package org.andrewdev.webwallet.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.andrewdev.webwallet.core.network.ApiError

/**
 * A callback that shows an [ApiError] as a snackbar with the message of [message]; mutation errors are red toasts
 * on the web (docs/05 §6), snackbars here.
 */
@Composable
fun rememberErrorReporter(host: SnackbarHostState): (ApiError) -> Unit {
    var pending by remember { mutableStateOf<ApiError?>(null) }
    val text = pending?.message()
    LaunchedEffect(pending) {
        if (pending != null && text != null) {
            host.showSnackbar(text)
            pending = null
        }
    }
    return { pending = it }
}
