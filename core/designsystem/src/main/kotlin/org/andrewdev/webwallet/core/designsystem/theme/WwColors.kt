package org.andrewdev.webwallet.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colours Material 3 has no role for (docs/05 §1.2): amounts by sign, warnings, faint text, charts. */
@Immutable
data class WwColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warnBg: Color,
    val warnBorder: Color,
    val warnText: Color,
    val faint: Color,
    val accentText: Color,
    val chartGrid: Color,
)

internal val LightWwColors = WwColors(
    income = Color(0xFF15803D),
    expense = Color(0xFFC62828),
    transfer = Color(0xFF52525B),
    warnBg = Color(0xFFFFF9EC),
    warnBorder = Color(0xFFEEA94A),
    warnText = Color(0xFF7A4E0E),
    faint = Color(0xFF71717A),
    accentText = Color(0xFF0D52A8),
    chartGrid = Color(0xFFE4E4E7),
)

internal val DarkWwColors = WwColors(
    income = Color(0xFF6CC48A),
    expense = Color(0xFFF0716B),
    transfer = Color(0xFFABABAB),
    warnBg = Color(0xFF3D3324),
    warnBorder = Color(0xFFEEA94A),
    warnText = Color(0xFFF2C27D),
    faint = Color(0xFF959595),
    accentText = Color(0xFF7AAEF2),
    chartGrid = Color(0xFF45484A),
)

val LocalWwColors = staticCompositionLocalOf { LightWwColors }
