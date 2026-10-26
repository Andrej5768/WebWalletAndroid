package org.andrewdev.webwallet.core.navigation

import org.andrewdev.webwallet.core.model.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeepLinkParserTest {

    @Test
    fun `email links carry their token in the fragment`() {
        assertEquals(
            DeepLink.Public(VerifyEmailKey("abc")),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/verify-email#token=abc"),
        )
        assertEquals(
            DeepLink.Public(ResetPasswordKey("r1")),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/reset-password#token=r1"),
        )
        assertEquals(
            DeepLink.Public(RestoreAccountKey("x")),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/restore-account/#token=x"),
        )
    }

    @Test
    fun `a link without its token falls back to the screen that asks for a new one`() {
        assertEquals(
            DeepLink.Public(VerifyEmailKey(null)),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/verify-email"),
        )
        assertEquals(
            DeepLink.Public(ForgotPasswordKey),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/reset-password#token="),
        )
    }

    @Test
    fun `unsubscribe reads the query`() {
        assertEquals(
            DeepLink.Public(UnsubscribeKey("7", "t%2F1".let { "t/1" })),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/unsubscribe?user=7&token=t%2F1"),
        )
        assertNull(DeepLinkParser.parse("https://wallet.andrew-dev.org/unsubscribe?user=7"))
    }

    @Test
    fun `web routes open their tab and unknown paths open the overview`() {
        assertEquals(
            DeepLink.Main(TransactionsKey, TransactionEditorKey(type = TransactionType.EXPENSE)),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/transactions?new=1"),
        )
        assertEquals(DeepLink.Main(AccountsKey), DeepLinkParser.parse("https://wallet.andrew-dev.org/accounts"))
        assertEquals(
            DeepLink.Main(MoreKey, SettingsKey),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/settings"),
        )
        assertEquals(
            DeepLink.Main(MoreKey, ComingSoonKey("goals")),
            DeepLinkParser.parse("https://wallet.andrew-dev.org/goals"),
        )
        assertEquals(DeepLink.Main(OverviewKey), DeepLinkParser.parse("https://wallet.andrew-dev.org/whatever"))
    }

    @Test
    fun `other hosts and empty links are not ours`() {
        assertNull(DeepLinkParser.parse("https://example.com/verify-email#token=abc"))
        assertNull(DeepLinkParser.parse(null))
        assertNull(DeepLinkParser.parse("not a uri at all ::"))
    }

    @Test
    fun `the internal scheme opens the quick add`() {
        assertEquals(
            DeepLink.Main(TransactionsKey, TransactionEditorKey(type = TransactionType.INCOME)),
            DeepLinkParser.parse("webwallet://quick-add?type=INCOME"),
        )
        assertNull(DeepLinkParser.parse("webwallet://lock"))
    }
}
