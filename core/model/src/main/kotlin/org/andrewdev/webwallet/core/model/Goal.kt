package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.LocalDate

enum class GoalStatus { ACHIEVED, NO_DATE, ON_TRACK, BEHIND, OVERDUE }

/** A savings goal (docs/04 §10.1): a target, the progress and the pace to the deadline. */
data class Goal(
    val id: Long,
    val name: String,
    val targetAmount: BigDecimal,
    val currency: CurrencyCode,
    val targetDate: LocalDate?,
    /** The account that holds the money; its balance is the progress. Null for a goal tracked by hand. */
    val accountId: Long?,
    val saved: BigDecimal,
    val remaining: BigDecimal,
    /** saved / target, 0..1. */
    val progress: BigDecimal,
    val startDate: LocalDate,
    val startAmount: BigDecimal,
    /** What should be saved by today to keep the pace; null without a deadline. */
    val expected: BigDecimal?,
    val monthsLeft: Int?,
    val monthlyNeeded: BigDecimal?,
    val status: GoalStatus,
    val achievedOn: LocalDate?,
    /** A Lucide icon name in kebab case, or null for the default. */
    val icon: String?,
    /** A palette colour name, or null for the automatic one. */
    val color: String?,
    val sortOrder: Int,
) {
    val achieved: Boolean get() = status == GoalStatus.ACHIEVED
}

/** `GET /goals`: the goals with totals in the base currency. */
data class GoalsSummary(
    val currency: CurrencyCode,
    val saved: BigDecimal,
    val target: BigDecimal,
    /** What has to be saved per month to reach every goal with a deadline in time. */
    val monthlyNeeded: BigDecimal,
    val goals: List<Goal>,
    val unconvertedCurrencies: List<String>,
)

/** What the editor sends; `PUT` replaces every field. */
data class GoalDraft(
    val name: String,
    val targetAmount: BigDecimal,
    val currency: String?,
    val targetDate: LocalDate?,
    val accountId: Long?,
    /** The progress of a goal without an account; ignored with one. */
    val saved: BigDecimal?,
    val icon: String?,
    val color: String?,
    val achieved: Boolean? = null,
    val sortOrder: Int? = null,
)
