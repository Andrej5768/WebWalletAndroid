package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.YearMonth

/**
 * The plan of a month in the base currency (docs/04 §7): what came in, what was planned, spent and is left, per
 * expense category. A parent row already includes its subcategories; the `own` values are the parent's alone.
 */
data class BudgetMonth(
    val month: YearMonth,
    val currency: CurrencyCode,
    /** The inflow of the month minus its plan; negative when the plan is bigger than the income. */
    val readyToAssign: BigDecimal,
    val inflow: BigDecimal,
    val assigned: BigDecimal,
    /** Spending is negative, refunds positive. */
    val activity: BigDecimal,
    val available: BigDecimal,
    val rows: List<BudgetRow>,
    val unconvertedCurrencies: List<String>,
) {
    /** Every row with its subcategories, depth first. */
    fun flatten(): List<BudgetRow> = rows.flatMap { it.flatten() }

    /** Whether anything was planned or spent this month. */
    val hasMoney: Boolean
        get() = assigned.signum() != 0 || activity.signum() != 0 || available.signum() != 0
}

data class BudgetRow(
    val categoryId: Long,
    val name: String,
    val parentId: Long?,
    val archived: Boolean,
    val assigned: BigDecimal,
    val activity: BigDecimal,
    val available: BigDecimal,
    val ownAssigned: BigDecimal,
    val ownActivity: BigDecimal,
    val ownAvailable: BigDecimal,
    val children: List<BudgetRow> = emptyList(),
) {
    fun flatten(): List<BudgetRow> = listOf(this) + children.flatMap { it.flatten() }
}
