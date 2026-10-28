package org.andrewdev.webwallet.core.model

import java.math.BigDecimal
import java.time.YearMonth

/**
 * The plan of a month in the base currency (docs/04 §7): what came in, what was planned, spent and is left, per
 * expense category, and the income expected and received per income category. A parent row already includes its
 * subcategories; the `own` values are the parent's alone.
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
    /** Income expected in the income categories; the free money counts only the income received. */
    val plannedIncome: BigDecimal = BigDecimal.ZERO,
    /** Income received, with and without a category. */
    val income: BigDecimal = BigDecimal.ZERO,
    val uncategorizedIncome: BigDecimal = BigDecimal.ZERO,
    val incomeRows: List<IncomeRow> = emptyList(),
) {
    /** Every row with its subcategories, depth first. */
    fun flatten(): List<BudgetRow> = rows.flatMap { it.flatten() }

    /** Every income row with its subcategories, depth first. */
    fun flattenIncome(): List<IncomeRow> = incomeRows.flatMap { it.flatten() }

    /** Whether anything was planned, spent or received this month. */
    val hasMoney: Boolean
        get() = assigned.signum() != 0 || activity.signum() != 0 || available.signum() != 0 ||
            plannedIncome.signum() != 0
}

/** The income expected in an income category this month and the income received in it. */
data class IncomeRow(
    val categoryId: Long,
    val name: String,
    val parentId: Long?,
    val archived: Boolean,
    val planned: BigDecimal,
    val received: BigDecimal,
    val ownPlanned: BigDecimal,
    val ownReceived: BigDecimal,
    val children: List<IncomeRow> = emptyList(),
) {
    fun flatten(): List<IncomeRow> = listOf(this) + children.flatMap { it.flatten() }
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
