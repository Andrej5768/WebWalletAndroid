package org.andrewdev.webwallet.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Material 3 with the web brand; no Dynamic Color (docs/05). Theme choice (system/light/dark) comes with M1. */
@Composable
fun WebWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWwColors provides if (darkTheme) DarkWwColors else LightWwColors,
        LocalWwType provides DefaultWwType,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = WebWalletTypography,
            shapes = WebWalletShapes,
            content = content,
        )
    }
}

/** Access to the extension tokens next to [MaterialTheme]. */
object WwTheme {
    val colors: WwColors
        @Composable @ReadOnlyComposable
        get() = LocalWwColors.current

    val type: WwType
        @Composable @ReadOnlyComposable
        get() = LocalWwType.current
}
