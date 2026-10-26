package org.andrewdev.webwallet.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.andrewdev.webwallet.core.common.DateFormatter
import org.andrewdev.webwallet.core.common.MoneyFormatter
import org.andrewdev.webwallet.core.common.UiLanguage
import java.time.LocalDate

/** The formatters of the current UI language and today's Kyiv date, available to every screen (docs/02 §8). */
@Immutable
data class Formatters(
    val language: UiLanguage,
    val money: MoneyFormatter,
    val date: DateFormatter,
    val today: LocalDate,
) {
    companion object {
        fun of(language: UiLanguage, today: LocalDate): Formatters =
            Formatters(language, MoneyFormatter(language), DateFormatter(language), today)
    }
}

val LocalFormatters = staticCompositionLocalOf { Formatters.of(UiLanguage.UK, LocalDate.now()) }

@Composable
fun ProvideFormatters(language: UiLanguage, today: LocalDate, content: @Composable () -> Unit) {
    val formatters = remember(language, today) { Formatters.of(language, today) }
    CompositionLocalProvider(LocalFormatters provides formatters, content = content)
}

val formatters: Formatters
    @Composable @ReadOnlyComposable
    get() = LocalFormatters.current
