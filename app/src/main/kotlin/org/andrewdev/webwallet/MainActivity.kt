package org.andrewdev.webwallet

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.andrewdev.webwallet.core.auth.AuthState
import org.andrewdev.webwallet.core.navigation.DeepLink
import org.andrewdev.webwallet.core.navigation.DeepLinkParser
import org.andrewdev.webwallet.ui.RootScreen
import org.andrewdev.webwallet.ui.RootViewModel

/**
 * The single activity. A [FragmentActivity] because the biometric prompt needs one; the splash stays until the
 * session is restored (docs/02 §7). App Links and the internal scheme arrive here and go to the root as a [DeepLink].
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: RootViewModel by viewModels()

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
        pendingLink = DeepLinkParser.parse(intent?.dataString)
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
        DeepLinkParser.parse(intent.dataString)?.let { pendingLink = it }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        super.onDestroy()
    }
}
