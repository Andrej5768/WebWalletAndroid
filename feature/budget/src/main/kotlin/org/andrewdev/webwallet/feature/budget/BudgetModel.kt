package org.andrewdev.webwallet.feature.budget

import org.andrewdev.webwallet.core.model.BudgetRow
import org.andrewdev.webwallet.core.model.IncomeRow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** The own envelope of one category: what is planned for it and spent from it, without its subcategories. */
data class Envelope(
    val categoryId: Long,
    val name: String,
    val assigned: BigDecimal,
    /** Spending is negative. */
    val activity: BigDecimal,
    val available: BigDecimal,
    val carryOver: Boolean = false,
) {
    val spent: BigDecimal get() = activity.negate()
}

/** A top-level category with its envelopes; the server already sums the subcategories into the group. */
data class EnvelopeGroup(
    val categoryId: Long,
    val name: String,
    val assigned: BigDecimal,
    val spent: BigDecimal,
    val available: BigDecimal,
    /** The subcategories, and the group's own envelope when it has money or no subcategories. */
    val envelopes: List<Envelope>,
    /** A category without subcategories: one row, without a group heading repeating it. */
    val single: Boolean,
)

/** The income expected in one income category and the income received in it, without its subcategories. */
data class IncomeLine(
    val categoryId: Long,
    val name: String,
    val planned: BigDecimal,
    val received: BigDecimal,
    /** Under a parent income category. */
    val nested: Boolean,
) {
    /** What is still expected; zero once the plan is reached. */
    val remaining: BigDecimal get() = (planned - received).max(BigDecimal.ZERO)

    /** Received share of the plan, 0..1; 1 without a plan once something came in. */
    val share: Float
        get() = when {
            planned.signum() > 0 ->
                received.divide(planned, SHARE_SCALE, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            received.signum() > 0 -> 1f
            else -> 0f
        }

    private companion object {
        const val SHARE_SCALE = 4
    }
}

/** The web's `budget/model.ts`: grouping, pace and shares, with the plans being saved laid over the server's. */
object BudgetModel {

    /** Spending ahead of the month by more than this share is pointed out. */
    private const val AHEAD_THRESHOLD = 0.1
    private const val PERCENT = 100

    fun groupEnvelopes(
        rows: List<BudgetRow>,
        pending: Map<Long, BigDecimal> = emptyMap(),
        carryOver: Set<Long> = emptySet(),
    ): List<EnvelopeGroup> = rows.map { head ->
        val own = head.ownEnvelope(pending, carryOver)
        val children = head.children.map { it.ownEnvelope(pending, carryOver) }
        val delta = (listOf(own) + children).fold(BigDecimal.ZERO) { sum, envelope ->
            sum + (pending[envelope.categoryId]?.minus(rowAssigned(head, envelope.categoryId)) ?: BigDecimal.ZERO)
        }
        EnvelopeGroup(
            categoryId = head.categoryId,
            name = head.name,
            assigned = head.assigned + delta,
            spent = head.activity.negate(),
            available = head.available + delta,
            envelopes = if (children.isEmpty() || own.hasMoney) listOf(own) + children else children,
            single = children.isEmpty(),
        )
    }

    /** Every own envelope in display order, groups included. */
    fun flatEnvelopes(rows: List<BudgetRow>, carryOver: Set<Long> = emptySet()): List<Envelope> {
        val none = emptyMap<Long, BigDecimal>()
        return rows.flatMap { row -> (listOf(row) + row.children).map { it.ownEnvelope(none, carryOver) } }
    }

    /**
     * The income categories as lines, with the plans being saved laid over the server's. A category with
     * subcategories shows them, and itself only when it has its own plan or income.
     */
    fun incomeLines(rows: List<IncomeRow>, pending: Map<Long, BigDecimal> = emptyMap()): List<IncomeLine> =
        rows.flatMap { head ->
            fun line(row: IncomeRow, nested: Boolean) = IncomeLine(
                categoryId = row.categoryId,
                name = row.name,
                planned = pending[row.categoryId] ?: row.ownPlanned,
                received = row.ownReceived,
                nested = nested,
            )
            val own = line(head, nested = false)
            if (head.children.isEmpty()) {
                listOf(own)
            } else {
                val children = head.children.map { line(it, nested = true) }
                val ownHasMoney = own.planned.signum() != 0 || own.received.signum() != 0
                (if (ownHasMoney) listOf(own) else emptyList()) + children
            }
        }

    fun overspent(rows: List<BudgetRow>): List<Envelope> = flatEnvelopes(rows).filter { it.available.signum() < 0 }

    /** Share of the month that has passed, 0..1, for the "today" marker; null for other months. */
    fun monthProgress(month: YearMonth, today: LocalDate): Float? =
        if (YearMonth.from(today) == month) today.dayOfMonth.toFloat() / month.lengthOfMonth() else null

    /** Spent share of the money available at the start of the month (the plan plus what carried over). */
    fun spentShare(envelope: Envelope): Float {
        val spent = envelope.spent
        val budget = envelope.available + spent
        if (budget.signum() <= 0) return if (spent.signum() > 0) 1f else 0f
        return spent.divide(budget, SHARE_SCALE, RoundingMode.HALF_UP).toFloat()
    }

    /** By how many percent the spending runs ahead of the month, or null when it does not (or is overspent anyway). */
    fun aheadPercent(envelope: Envelope, progress: Float?): Int? {
        if (progress == null || envelope.available.signum() < 0) return null
        val share = spentShare(envelope)
        return if (share > progress + AHEAD_THRESHOLD) ((share - progress) * PERCENT).roundToInt() else null
    }

    private fun BudgetRow.ownEnvelope(pending: Map<Long, BigDecimal>, carryOver: Set<Long>): Envelope {
        val planned = pending[categoryId]
        val delta = planned?.minus(ownAssigned) ?: BigDecimal.ZERO
        return Envelope(
            categoryId = categoryId,
            name = name,
            assigned = planned ?: ownAssigned,
            activity = ownActivity,
            available = ownAvailable + delta,
            carryOver = categoryId in carryOver,
        )
    }

    private fun rowAssigned(head: BudgetRow, categoryId: Long): BigDecimal =
        head.flatten().first { it.categoryId == categoryId }.ownAssigned

    private val Envelope.hasMoney: Boolean
        get() = assigned.signum() != 0 || activity.signum() != 0 || available.signum() != 0

    private const val SHARE_SCALE = 4
}
