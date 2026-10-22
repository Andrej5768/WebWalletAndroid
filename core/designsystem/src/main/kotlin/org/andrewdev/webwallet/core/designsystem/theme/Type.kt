package org.andrewdev.webwallet.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.andrewdev.webwallet.core.designsystem.R

/** Fixel Text for text, Fixel Display for headings, Inter with tabular digits for amounts, IBM Plex Mono for codes. */
val FixelText: FontFamily = FontFamily(
    Font(R.font.core_designsystem_fixel_text_regular, FontWeight.Normal),
    Font(R.font.core_designsystem_fixel_text_medium, FontWeight.Medium),
    Font(R.font.core_designsystem_fixel_text_semibold, FontWeight.SemiBold),
)

val FixelDisplay: FontFamily = FontFamily(
    Font(R.font.core_designsystem_fixel_display_semibold, FontWeight.SemiBold),
)

val Inter: FontFamily = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
)

val PlexMono: FontFamily = FontFamily(
    Font(R.font.core_designsystem_ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.core_designsystem_ibm_plex_mono_medium, FontWeight.Medium),
)

private fun interFont(weight: FontWeight): Font = Font(
    resId = R.font.core_designsystem_inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val defaultLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineHeightStyle = defaultLineHeightStyle,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The Material 3 scale on the web fonts (docs/05 §2). */
internal val WebWalletTypography = Typography(
    displaySmall = style(FixelDisplay, FontWeight.SemiBold, size = 32, lineHeight = 40),
    headlineSmall = style(FixelDisplay, FontWeight.SemiBold, size = 24, lineHeight = 32),
    titleLarge = style(FixelText, FontWeight.SemiBold, size = 20, lineHeight = 28),
    titleMedium = style(FixelText, FontWeight.SemiBold, size = 16, lineHeight = 24),
    titleSmall = style(FixelText, FontWeight.Medium, size = 14, lineHeight = 20),
    bodyLarge = style(FixelText, FontWeight.Normal, size = 16, lineHeight = 24),
    bodyMedium = style(FixelText, FontWeight.Normal, size = 14, lineHeight = 20),
    bodySmall = style(FixelText, FontWeight.Normal, size = 12, lineHeight = 16),
    labelLarge = style(FixelText, FontWeight.Medium, size = 14, lineHeight = 20),
    labelMedium = style(FixelText, FontWeight.Medium, size = 12, lineHeight = 16),
    labelSmall = style(FixelText, FontWeight.Medium, size = 11, lineHeight = 16),
)

/** Styles outside the Material scale: amounts in Inter with tabular digits, codes in a monospace. */
@Immutable
data class WwType(
    val amountL: TextStyle,
    val amountM: TextStyle,
    val amountS: TextStyle,
    val mono: TextStyle,
)

private const val TABULAR_DIGITS = "tnum"

internal val DefaultWwType = WwType(
    amountL = style(Inter, FontWeight.SemiBold, size = 28, lineHeight = 34).copy(fontFeatureSettings = TABULAR_DIGITS),
    amountM = style(Inter, FontWeight.Medium, size = 16, lineHeight = 22).copy(fontFeatureSettings = TABULAR_DIGITS),
    amountS = style(Inter, FontWeight.Medium, size = 14, lineHeight = 20).copy(fontFeatureSettings = TABULAR_DIGITS),
    mono = style(PlexMono, FontWeight.Normal, size = 14, lineHeight = 20),
)

val LocalWwType = staticCompositionLocalOf { DefaultWwType }
