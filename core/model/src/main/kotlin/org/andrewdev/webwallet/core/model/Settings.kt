package org.andrewdev.webwallet.core.model

import java.time.OffsetDateTime

/** A categorization rule (docs/04 §14): every given condition must match; learned ones came from manual changes. */
data class Rule(
    val id: Long,
    val categoryId: Long,
    val payee: Named?,
    /** A case-insensitive part of the payee name. */
    val payeePattern: String?,
    val mcc: Int?,
    val bankCategory: String?,
    val learned: Boolean,
)

data class RuleDraft(
    val categoryId: Long,
    val payeeId: Long? = null,
    val payeePattern: String? = null,
    val mcc: Int? = null,
    val bankCategory: String? = null,
)

data class CategoryDraft(
    val name: String,
    val kind: CategoryKind,
    val parentId: Long?,
    val icon: String?,
    val color: String?,
    val nature: CategoryNature,
    val archived: Boolean,
    val carryOver: Boolean,
)

/** One row of `PUT /categories/order`. */
data class CategoryOrder(val id: Long, val parentId: Long?, val sortOrder: Int)

/** What the user is notified about (docs/06 §1.4), per kind, and the hour the reminders and reports come at. */
data class NotificationSettings(
    val recurring: Boolean,
    val budgets: Boolean,
    val taxes: Boolean,
    val banks: Boolean,
    val forecast: Boolean,
    val goals: Boolean,
    val weekly: Boolean,
    val monthly: Boolean,
    val emailWeekly: Boolean,
    val emailMonthly: Boolean,
    val hour: Int,
)

/** Whether the Telegram bot is available and the chat of the user, if connected. */
data class TelegramStatus(
    val enabled: Boolean,
    val botUsername: String?,
    val linked: Boolean,
    val firstName: String?,
    val username: String?,
    val linkedAt: OffsetDateTime?,
    val quickEntryAccountId: Long?,
)
