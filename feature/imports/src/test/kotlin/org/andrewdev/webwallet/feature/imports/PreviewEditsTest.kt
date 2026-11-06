package org.andrewdev.webwallet.feature.imports

import org.andrewdev.webwallet.core.model.ImportAccount
import org.andrewdev.webwallet.core.model.ImportOperation
import org.andrewdev.webwallet.core.model.TransactionType
import org.andrewdev.webwallet.core.model.TransferSuggestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class PreviewEditsTest {

    private val card = ImportAccount(1, "Card", "UAH", created = false)

    private fun suggestion(operationKey: String? = null, transactionId: Long? = null, exact: Boolean = true) =
        TransferSuggestion(
            accountName = "Other",
            amount = BigDecimal("100"),
            currency = "UAH",
            date = LocalDate.of(2026, 11, 1),
            difference = BigDecimal.ZERO,
            exact = exact,
            operationKey = operationKey,
            transactionId = transactionId,
        )

    private fun operation(
        key: String,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = null,
        categoryName: String? = null,
        duplicate: Boolean = false,
        suggestions: List<TransferSuggestion> = emptyList(),
        tagIds: List<Long> = emptyList(),
    ) = ImportOperation(
        key = key,
        date = LocalDate.of(2026, 11, 1),
        type = type,
        amount = BigDecimal("100"),
        currency = "UAH",
        account = card,
        toAccount = null,
        payee = "Shop",
        comment = null,
        categoryId = categoryId,
        categoryName = categoryName,
        tagIds = tagIds,
        duplicate = duplicate,
        transferSuggestions = suggestions,
    )

    @Test
    fun `pairs two halves of the file that match only each other`() {
        val out = operation("a", suggestions = listOf(suggestion(operationKey = "b")))
        val into = operation("b", type = TransactionType.INCOME, suggestions = listOf(suggestion(operationKey = "a")))
        val pairs = PreviewEdits.exactPairs(listOf(out, into))
        assertEquals(TransferHalf("b", null), pairs["a"])
        assertEquals(TransferHalf("a", null), pairs["b"])
    }

    @Test
    fun `leaves a ledger transaction claimed by two rows unpaired`() {
        val first = operation("a", suggestions = listOf(suggestion(transactionId = 7)))
        val second = operation("b", suggestions = listOf(suggestion(transactionId = 7)))
        assertTrue(PreviewEdits.exactPairs(listOf(first, second)).isEmpty())
    }

    @Test
    fun `does not pair on an inexact match or a half that points elsewhere`() {
        val inexact = operation("a", suggestions = listOf(suggestion(transactionId = 7, exact = false)))
        val oneSided = operation("b", suggestions = listOf(suggestion(operationKey = "c")))
        val other = operation("c", type = TransactionType.INCOME)
        assertTrue(PreviewEdits.exactPairs(listOf(inexact, oneSided, other)).isEmpty())
    }

    @Test
    fun `counts uncategorized rows unless the import creates the category`() {
        val none = operation("a")
        val created = operation("b", categoryName = "Groceries")
        val state = PreviewEditState().withOperations(listOf(none, created))
        assertEquals(1, state.uncategorizedCount)
        val categorized = state.update(none) { it.copy(categoryId = 5, categoryChanged = true) }
        assertEquals(0, categorized.uncategorizedCount)
    }

    @Test
    fun `an expense turned into a transfer needs the other account`() {
        val expense = operation("a")
        val state = PreviewEditState().withOperations(listOf(expense))
            .update(expense) { it.copy(type = TransactionType.TRANSFER) }
        assertEquals(1, state.withoutTransferAccount)
        assertEquals(0, state.uncategorizedCount)
        val done = state.update(expense) { it.copy(transferAccountId = 2) }
        assertEquals(0, done.withoutTransferAccount)
        val request = done.request(emptyMap()).single()
        assertEquals(2L, request.transferAccountId)
        assertNull(request.categoryId)
    }

    @Test
    fun `choosing a half of the file makes it point back and releases the old partner`() {
        val a = operation("a", suggestions = listOf(suggestion(operationKey = "c"), suggestion(operationKey = "b")))
        val b = operation(
            "b",
            type = TransactionType.INCOME,
            suggestions = listOf(suggestion(operationKey = "a", exact = false)),
        )
        val c = operation(
            "c",
            type = TransactionType.INCOME,
            suggestions = listOf(suggestion(operationKey = "a", exact = false)),
        )
        var state = PreviewEditState().withOperations(listOf(a, b, c))
        state = state.update(a) { it.copy(pair = TransferHalf("c", null)) }
        assertEquals(TransferHalf("a", null), state.editOf(c).pair)
        state = state.update(a) { it.copy(pair = TransferHalf("b", null)) }
        assertEquals(TransferHalf("a", null), state.editOf(b).pair)
        assertNull(state.editOf(c).pair)
        assertEquals(1, state.chosenPairs)
    }

    @Test
    fun `sends only touched or paired rows, never duplicates, and resolves tag names`() {
        val plain = operation("a")
        val tagged = operation("b")
        val duplicate = operation("c", duplicate = true)
        val state = PreviewEditState().withOperations(listOf(plain, tagged, duplicate))
            .update(tagged) { it.copy(tags = listOf("Trip ")) }
        assertEquals(listOf("Trip "), state.tagNamesToSend())
        val request = state.request(mapOf("trip" to 9L))
        assertEquals(listOf("b"), request.map { it.key })
        assertEquals(listOf(9L), request.single().tagIds)
        assertTrue(request.single().keepSuggestedCategory)
        assertFalse(request.single().rememberRule)
    }

    @Test
    fun `bulk sets the category except on transfers and adds a tag once`() {
        val expense = operation("a", tagIds = listOf(1))
        val transfer = operation("b", type = TransactionType.TRANSFER)
        val state = PreviewEditState(tagNames = mapOf(1L to "trip")).withOperations(listOf(expense, transfer))
            .applyBulk(setOf("a", "b"), categoryId = 5, tag = "Trip")
        assertEquals(5L, state.editOf(expense).categoryId)
        assertEquals(listOf("trip"), state.editOf(expense).tags)
        assertNull(state.editOf(transfer).categoryId)
        assertEquals(listOf("Trip"), state.editOf(transfer).tags)
    }

    @Test
    fun `allows the web's type changes`() {
        assertEquals(
            listOf(TransactionType.EXPENSE, TransactionType.TRANSFER),
            PreviewEdits.allowedTypes(TransactionType.EXPENSE),
        )
        assertEquals(3, PreviewEdits.allowedTypes(TransactionType.TRANSFER).size)
    }
}
