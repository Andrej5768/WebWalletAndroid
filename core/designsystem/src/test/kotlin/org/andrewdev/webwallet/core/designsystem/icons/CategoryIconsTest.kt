package org.andrewdev.webwallet.core.designsystem.icons

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryIconsTest {

    /** The keys of `CATEGORY_ICONS` in the web client's `src/lib/categoryIcons.ts`, copied verbatim. */
    private val webKeys = listOf(
        "shopping-cart", "shopping-basket", "utensils", "coffee", "package", "wine", "popcorn", "house",
        "key-round", "zap", "droplet", "wifi", "wrench", "sofa", "car", "car-taxi-front", "bus", "tram-front",
        "train-front", "fuel", "square-parking", "plane", "bed", "heart-pulse", "heart", "heart-handshake", "pill",
        "stethoscope", "dumbbell", "scissors", "clapperboard", "gamepad-2", "tv", "repeat", "music", "book-open",
        "shirt", "shopping-bag", "laptop", "smartphone", "gift", "baby", "paw-print", "graduation-cap", "receipt",
        "percent", "shield", "calendar-check", "briefcase", "coins", "trending-up", "tag", "undo-2", "piggy-bank",
        "landmark", "wallet",
    )

    @Test
    fun `every icon name of the web resolves to a glyph`() {
        val missing = webKeys.filterNot { it in CategoryIcons.byName }

        assertTrue(missing.isEmpty()) { "Icons missing on Android: $missing" }
        assertEquals(webKeys.size, CategoryIcons.byName.size, "The app has icons the web does not")
    }

    @Test
    fun `an unknown or absent name falls back to the tag`() {
        assertSame(CategoryIcons.fallback, CategoryIcons["no-such-icon"])
        assertSame(CategoryIcons.fallback, CategoryIcons[null])
        assertSame(CategoryIcons.byName.getValue("coffee"), CategoryIcons["coffee"])
    }
}
