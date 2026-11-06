package org.andrewdev.webwallet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthState
import org.andrewdev.webwallet.core.data.SharedFileInbox
import org.andrewdev.webwallet.core.navigation.DeepLink
import org.andrewdev.webwallet.core.navigation.DeepLinkParser
import org.andrewdev.webwallet.core.navigation.ImportsKey
import org.andrewdev.webwallet.core.navigation.MoreKey
import org.andrewdev.webwallet.feature.imports.ui.readUpload
import org.andrewdev.webwallet.ui.RootScreen
import org.andrewdev.webwallet.ui.RootViewModel
import javax.inject.Inject

/**
 * The single activity. A [FragmentActivity] because the biometric prompt needs one; the splash stays until the
 * session is restored (docs/02 §7). App Links and the internal scheme arrive here and go to the root as a [DeepLink];
 * a file shared from another app is read into the [SharedFileInbox] and opens the imports.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: RootViewModel by viewModels()

    @Inject lateinit var inbox: SharedFileInbox

    private var pendingLink by mutableStateOf<DeepLink?>(null)

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = viewModel.onForeground()

        override fun onStop(owner: LifecycleOwner) = viewModel.onBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value.auth is AuthState.Unknown }
        enableEdgeToEdge()
        // A recreated activity has the file in the inbox already; a content:// address is no link, so parse is null
        if (savedInstanceState != null || !receiveFile(intent)) pendingLink = DeepLinkParser.parse(intent?.dataString)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        lifecycleScope.launch {
            // The recents thumbnail is blanked whenever the lock is set up (docs/03 §6.2)
            viewModel.uiState.collect { state ->
                if (state.lockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            RootScreen(deepLink = pendingLink, onDeepLinkConsumed = { pendingLink = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!receiveFile(intent)) DeepLinkParser.parse(intent.dataString)?.let { pendingLink = it }
    }

    /**
     * A statement shared from another app ("Share") or opened with this one ("Open with"): read at once, since the
     * permission to read it ends with this intent, then handed to the imports screen.
     *
     * @return whether the intent carried a file
     */
    private fun receiveFile(intent: Intent?): Boolean {
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" }
            else -> null
        } ?: return false
        val type = intent?.type ?: "application/pdf"
        lifecycleScope.launch {
            readUpload(this@MainActivity, uri, type)
                .onSuccess { file ->
                    inbox.put(file)
                    pendingLink = DeepLink.Main(MoreKey, ImportsKey)
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, R.string.app_shared_file_unreadable, Toast.LENGTH_LONG).show()
                }
        }
        return true
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }
}
