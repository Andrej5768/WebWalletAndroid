package org.andrewdev.webwallet.core.data

import org.andrewdev.webwallet.core.database.CategoryEntity
import org.andrewdev.webwallet.core.model.CategoryKind
import org.andrewdev.webwallet.core.model.CategoryNature
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.andrewdev.webwallet.core.model.Named
import org.andrewdev.webwallet.core.model.Split
import org.andrewdev.webwallet.core.model.Transaction
import org.andrewdev.webwallet.core.model.TransactionSource
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.network.generated.model.NamedResponse
import org.andrewdev.webwallet.core.network.generated.model.SplitResponse
import org.andrewdev.webwallet.core.network.generated.model.TransactionResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

class MappersTest {

    private fun response(splits: List<SplitResponse>) = TransactionResponse(
        accountId = 1,
        adjustment = false,
        amount = BigDecimal("120.50"),
        comment = "кава",
        createdAt = OffsetDateTime.parse("2026-10-26T10:00:00Z"),
        currency = "UAH",
        date = LocalDate.of(2026, 10, 26),
        id = 7,
        originalAmount = null,
        originalCurrency = null,
        payee = NamedResponse(3, "Сільпо"),
        recurring = null,
        source = TransactionResponse.Source.MONOBANK,
        splits = splits,
        tagIds = listOf(1, 2),
        toAccountId = null,
        toAmount = null,
        toCurrency = null,
        type = TransactionResponse.Type.EXPENSE,
    )

    @Test
    fun `one split is the plain category, more are a split`() {
        val plain = response(listOf(SplitResponse(BigDecimal("120.50"), 5, null, 11))).toEntity().toModel()
        assertEquals(5, plain.categoryId)
        assertEquals(false, plain.isSplit)
        assertEquals(listOf(1L, 2L), plain.tagIds)
        assertEquals(Named(3, "Сільпо"), plain.payee)

        val split = response(
            listOf(SplitResponse(BigDecimal("100"), 5, "a", 11), SplitResponse(BigDecimal("20.50"), 6, null, 12)),
        ).toEntity().toModel()
        assertNull(split.categoryId)
        assertEquals(true, split.isSplit)
        assertEquals(
            listOf(Split(11, BigDecimal("100"), 5, "a"), Split(12, BigDecimal("20.50"), 6, null)),
            split.splits,
        )
    }

    @Test
    fun `a model survives the entity round trip`() {
        val transaction = Transaction(
            id = 9, type = TransactionType.TRANSFER,
            date = LocalDate.of(
                2026,
                10,
                1,
            ),
            accountId = 1, amount = BigDecimal("50"),
            currency = CurrencyCode.UAH, toAccountId = 2,
            toAmount = BigDecimal(
                "1.2",
            ),
            toCurrency = CurrencyCode.USD, categoryId = null,
            payee = null, comment = null, tagIds = emptyList(), splits = emptyList(),
            originalAmount = null, originalCurrency = null,
            source = TransactionSource.MANUAL, adjustment = false,
            recurring = Named(
                4,
                "Оренда",
            ),
            createdAt = OffsetDateTime.parse("2026-10-01T08:00:00+03:00"),
        )
        assertEquals(transaction, transaction.toEntity().toModel())
    }

    @Test
    fun `the category tree is rebuilt from flat rows`() {
        val rows = listOf(
            CategoryEntity(1, "Їжа", "EXPENSE", null, "utensils", "green", null, false, 0),
            CategoryEntity(2, "Кава", "EXPENSE", 1, null, null, "WANTS", false, 1),
            CategoryEntity(3, "Зарплата", "INCOME", null, null, null, null, false, 0),
        )
        val tree = rows.toTree()
        assertEquals(listOf("Їжа", "Зарплата"), tree.map { it.name })
        assertEquals(CategoryKind.EXPENSE, tree[0].kind)
        assertEquals(CategoryNature.INHERIT, tree[0].nature)
        assertEquals(listOf("Кава"), tree[0].children.map { it.name })
        assertEquals(CategoryNature.WANTS, tree[0].children[0].nature)
        assertEquals(3, tree.flatMap { it.flatten() }.size)
    }
}
