package org.andrewdev.webwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import org.andrewdev.webwallet.core.designsystem.gallery.GalleryScreen
import org.andrewdev.webwallet.core.designsystem.theme.WebWalletTheme

/**
 * The single activity of the app. M0 shows the design-system gallery; the root navigation that switches between
 * the auth flow, the lock screen and the main scaffold arrives with M1 (docs/02 §7).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebWalletTheme {
                GalleryScreen()
            }
        }
    }
}
