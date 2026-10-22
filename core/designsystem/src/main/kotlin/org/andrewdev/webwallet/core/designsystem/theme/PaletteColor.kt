package org.andrewdev.webwallet.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** The 12 colours of categories, goals and tags, named as the backend stores them (docs/05 §1.3). */
enum class PaletteColor(val key: String, private val light: Color, private val dark: Color) {
    BLUE("blue", Color(0xFF2F6FC9), Color(0xFF7AAEF2)),
    GREEN("green", Color(0xFF16A34A), Color(0xFF6CC48A)),
    ORANGE("orange", Color(0xFFEA580C), Color(0xFFFB923C)),
    VIOLET("violet", Color(0xFF7C3AED), Color(0xFFA78BFA)),
    TEAL("teal", Color(0xFF0D9488), Color(0xFF2DD4BF)),
    PINK("pink", Color(0xFFDB2777), Color(0xFFF472B6)),
    AMBER("amber", Color(0xFFD97706), Color(0xFFFBBF24)),
    SLATE("slate", Color(0xFF64748B), Color(0xFF94A3B8)),
    RED("red", Color(0xFFDC2626), Color(0xFFF0716B)),
    INDIGO("indigo", Color(0xFF4F46E5), Color(0xFF818CF8)),
    CYAN("cyan", Color(0xFF0891B2), Color(0xFF22D3EE)),
    LIME("lime", Color(0xFF65A30D), Color(0xFFA3E635)),
    ;

    fun color(dark: Boolean): Color = if (dark) this.dark else this.light

    /** The tile behind an icon: the colour at a low alpha. */
    fun container(
        dark: Boolean,
    ): Color = color(dark).copy(alpha = if (dark) CONTAINER_ALPHA_DARK else CONTAINER_ALPHA_LIGHT)

    companion object {
        private const val CONTAINER_ALPHA_LIGHT = 0.14f
        private const val CONTAINER_ALPHA_DARK = 0.22f

        /** Colours given to categories that have none of their own; slate is left out, it reads as "no category". */
        private val AUTO = entries.filter { it != SLATE }

        fun fromKey(key: String?): PaletteColor? = entries.firstOrNull { it.key == key }

        /** A stable colour for a category without one, the same the web picks (`src/lib/palette.ts`). */
        fun auto(id: Long): PaletteColor = AUTO[(abs(id) % AUTO.size).toInt()]

        /** The colour stored by the backend, or the automatic one for the id. */
        fun of(key: String?, id: Long): PaletteColor = fromKey(key) ?: auto(id)
    }
}
