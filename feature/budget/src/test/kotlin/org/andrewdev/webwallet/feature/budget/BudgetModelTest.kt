package org.andrewdev.webwallet.feature.budget

import org.andrewdev.webwallet.core.model.BudgetRow
import org.andrewdev.webwallet.core.model.IncomeRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class BudgetModelTest {

    private fun row(
        id: Long,
        name: String,
        assigned: String,
        activity: String,
        available: String,
        parentId: Long? = null,
        children: List<BudgetRow> = emptyList(),
    ): BudgetRow {
        val own = Triple(BigDecimal(assigned), BigDecimal(activity), BigDecimal(available))
        val total = children.fold(own) { sum, child ->
            Triple(sum.first + child.assigned, sum.second + child.activity, sum.third + child.available)
        }
        return BudgetRow(
            id, name, parentId, false,
            total.first, total.second, total.third,
            own.first, own.second, own.third,
            children,
        )
    }

    @Test
    fun `a parent with subcategories is a group, its own envelope shows only when it has money`() {
        val food = row(
            1,
            "Їжа",
            "0",
            "0",
            "0",
            children = listOf(
                row(2, "Продукти", "4000", "-3200", "800", 1),
                row(3, "Кава", "2000", "-1000", "1000", 1),
            ),
        )
        val transport = row(4, "Транспорт", "1500", "-1800", "-300")

        val groups = BudgetModel.groupEnvelopes(listOf(food, transport))

        assertEquals(listOf("Продукти", "Кава"), groups[0].envelopes.map { it.name })
        assertEquals(BigDecimal("6000"), groups[0].assigned)
        assertEquals(BigDecimal("4200"), groups[0].spent)
        assertTrue(groups[1].single)
        assertEquals(listOf("Транспорт"), groups[1].envelopes.map { it.name })
    }

    @Test
    fun `a plan being saved is laid over the server's values, in the row and in the group`() {
        val food = row(1, "Їжа", "0", "0", "0", children = listOf(row(2, "Продукти", "4000", "-3200", "800", 1)))

        val groups = BudgetModel.groupEnvelopes(listOf(food), pending = mapOf(2L to BigDecimal("5000")))

        val envelope = groups[0].envelopes.single()
        assertEquals(BigDecimal("5000"), envelope.assigned)
        assertEquals(BigDecimal("1800"), envelope.available)
        assertEquals(BigDecimal("5000"), groups[0].assigned)
        assertEquals(BigDecimal("1800"), groups[0].available)
    }

    @Test
    fun `overspent envelopes are those below zero`() {
        val rows = listOf(row(1, "A", "100", "-150", "-50"), row(2, "B", "100", "-50", "50"))

        assertEquals(listOf("A"), BudgetModel.overspent(rows).map { it.name })
    }

    @Test
    fun `the today marker exists only in the current month`() {
        val today = LocalDate.of(2026, 10, 15)

        assertEquals(15f / 31f, BudgetModel.monthProgress(YearMonth.of(2026, 10), today))
        assertNull(BudgetModel.monthProgress(YearMonth.of(2026, 9), today))
    }

    @Test
    fun `the spent share is against what the month started with, and pace is flagged past the threshold`() {
        val onPace = Envelope(1, "A", BigDecimal("1000"), BigDecimal("-500"), BigDecimal("500"))
        val ahead = Envelope(2, "B", BigDecimal("1000"), BigDecimal("-900"), BigDecimal("100"))
        val overspent = Envelope(3, "C", BigDecimal("0"), BigDecimal("-100"), BigDecimal("-100"))

        assertEquals(0.5f, BudgetModel.spentShare(onPace))
        assertEquals(1f, BudgetModel.spentShare(overspent))
        assertNull(BudgetModel.aheadPercent(onPace, 0.5f))
        assertEquals(40, BudgetModel.aheadPercent(ahead, 0.5f))
        assertNull(BudgetModel.aheadPercent(overspent, 0.5f))
        assertNull(BudgetModel.aheadPercent(ahead, null))
    }

    private fun income(
        id: Long,
        name: String,
        planned: String,
        received: String,
        children: List<IncomeRow> = emptyList(),
    ) = IncomeRow(
        categoryId = id,
        name = name,
        parentId = null,
        archived = false,
        planned = children.fold(BigDecimal(planned)) { sum, child -> sum + child.planned },
        received = children.fold(BigDecimal(received)) { sum, child -> sum + child.received },
        ownPlanned = BigDecimal(planned),
        ownReceived = BigDecimal(received),
        children = children,
    )

    @Test
    fun `income lines list subcategories and a parent only when it has its own plan or income`() {
        val children = listOf(income(2, "Оклад", "50000", "50000"), income(3, "Бонус", "5000", "0"))
        val salary = income(1, "Зарплата", "0", "0", children)
        val gifts = income(4, "Подарунки", "0", "0")

        val lines = BudgetModel.incomeLines(listOf(salary, gifts))

        assertEquals(
            listOf("Оклад" to true, "Бонус" to true, "Подарунки" to false),
            lines.map { it.name to it.nested },
        )
        val withOwn = income(1, "Зарплата", "1000", "0", salary.children)
        assertEquals(listOf("Зарплата", "Оклад", "Бонус"), BudgetModel.incomeLines(listOf(withOwn)).map { it.name })
    }

    @Test
    fun `a plan being saved shows at once in the income line`() {
        val lines = BudgetModel.incomeLines(listOf(income(4, "Подарунки", "0", "0")), mapOf(4L to BigDecimal("700")))

        assertEquals(BigDecimal("700"), lines.single().planned)
    }

    @Test
    fun `an income line tells what is still expected and how much of the plan came in`() {
        val line = BudgetModel.incomeLines(listOf(income(1, "ФОП", "60000", "45000"))).single()
        assertEquals(BigDecimal("15000"), line.remaining)
        assertEquals(0.75f, line.share)

        val over = BudgetModel.incomeLines(listOf(income(1, "ФОП", "60000", "70000"))).single()
        assertEquals(BigDecimal.ZERO, over.remaining)
        assertEquals(1f, over.share)

        val unplanned = BudgetModel.incomeLines(listOf(income(1, "Кешбек", "0", "120"))).single()
        assertEquals(1f, unplanned.share)
        assertEquals(0f, BudgetModel.incomeLines(listOf(income(1, "Кешбек", "0", "0"))).single().share)
    }
}
