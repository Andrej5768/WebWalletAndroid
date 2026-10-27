package org.andrewdev.webwallet.core.model

/** Expense or income side of the category tree. */
enum class CategoryKind { EXPENSE, INCOME }

/** Needs or wants for the budget pace; INHERIT takes the parent's. */
enum class CategoryNature { NEEDS, WANTS, INHERIT }

data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long?,
    /** A Lucide icon name of the web's `CATEGORY_ICONS`, or null for the default glyph. */
    val icon: String?,
    /** A palette colour name (blue, green, …), or null for the automatic one by id. */
    val color: String?,
    val nature: CategoryNature,
    val archived: Boolean,
    val sortOrder: Int,
    val children: List<Category> = emptyList(),
    /** An envelope: the rest of its plan moves to the next month, overspending is taken from the next month. */
    val carryOver: Boolean = false,
) {
    /** The category and its subcategories, depth first. */
    fun flatten(): List<Category> = listOf(this) + children.flatMap { it.flatten() }
}

data class Tag(val id: Long, val name: String, val color: String?)

data class Payee(val id: Long, val name: String, val operations: Long)

data class User(
    val id: Long,
    val email: String,
    val emailVerified: Boolean,
    val firstName: String,
    val lastName: String?,
    val baseCurrency: CurrencyCode,
    val locale: String,
    val hasPassword: Boolean,
    val twoFactorEnabled: Boolean,
    val recoveryCodesLeft: Long,
    val googleEmail: String?,
    /** A demo sandbox opened without registration; [demoExpiresAt] says when it is deleted. */
    val demo: Boolean = false,
    val demoExpiresAt: java.time.Instant? = null,
) {
    val fullName: String get() = listOfNotNull(firstName, lastName).joinToString(" ")

    /** Up to two letters for the avatar tile. */
    val initials: String
        get() = listOfNotNull(firstName.firstOrNull(), lastName?.firstOrNull()).joinToString("").uppercase()
}

/** `GET /app/config`: the versions the backend still accepts and the features it offers (B8). */
data class AppConfig(
    val minSupportedVersionCode: Int,
    val latestVersionCode: Int,
    val storeUrl: String,
    val googleSignIn: Boolean,
    val marketSearch: Boolean,
    val telegram: Boolean,
    val push: Boolean,
    /** Whether a demo sandbox can be opened without registration. */
    val demo: Boolean = false,
)
