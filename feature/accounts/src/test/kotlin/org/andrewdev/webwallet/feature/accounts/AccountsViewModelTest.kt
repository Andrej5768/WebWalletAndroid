package org.andrewdev.webwallet.feature.accounts

import org.andrewdev.webwallet.core.model.Account
import org.andrewdev.webwallet.core.model.AccountGroup
import org.andrewdev.webwallet.core.model.AccountType
import org.andrewdev.webwallet.core.model.CurrencyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountsViewModelTest {

    private fun account(
        id: Long,
        name: String,
        type: AccountType,
        inBase: String?,
        archived: Boolean = false,
        sortOrder: Int = 0,
    ) = Account(
        id, name, type, CurrencyCode.UAH, BigDecimal.ONE,
        inBase?.let(
            ::BigDecimal,
        ),
        BigDecimal.ZERO, null, null, null, true, archived, sortOrder, 0,
    )

    @Test
    fun `accounts fall into the web's groups in order with subtotals`() {
        val groups = AccountsViewModel.groupAccounts(
            listOf(
                account(1, "Готівка", AccountType.CASH, "100"),
                account(2, "Біла", AccountType.CARD, "200", sortOrder = 2),
                account(3, "Чорна", AccountType.CARD, "300", sortOrder = 1),
                account(4, "Депозит", AccountType.DEPOSIT, "1000"),
                account(5, "Старий", AccountType.CARD, "0", archived = true),
            ),
            showArchived = false,
            collapsed = setOf("CASH"),
        )

        assertEquals(listOf(AccountGroup.CARDS, AccountGroup.CASH, AccountGroup.SAVINGS), groups.map { it.group })
        assertEquals(listOf("Чорна", "Біла"), groups[0].accounts.map { it.name })
        assertEquals(BigDecimal("500"), groups[0].totalInBase)
        assertTrue(groups[1].collapsed)
    }

    @Test
    fun `archived accounts come last and only on request, a missing rate makes the total unknown`() {
        val all =
            listOf(account(1, "A", AccountType.CARD, null), account(2, "Z", AccountType.CARD, "1", archived = true))

        val shown = AccountsViewModel.groupAccounts(all, showArchived = true, collapsed = emptySet())
        assertEquals(listOf(AccountGroup.CARDS, AccountGroup.ARCHIVED), shown.map { it.group })
        assertNull(shown[0].totalInBase)
        assertNull(shown[1].totalInBase)
        assertEquals(
            listOf(AccountGroup.CARDS),
            AccountsViewModel.groupAccounts(all, showArchived = false, collapsed = emptySet()).map { it.group },
        )
    }
}
