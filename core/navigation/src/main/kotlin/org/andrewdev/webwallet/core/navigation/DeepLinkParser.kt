package org.andrewdev.webwallet.core.navigation

import androidx.navigation3.runtime.NavKey
import org.andrewdev.webwallet.core.model.TransactionType
import java.net.URI
import java.net.URLDecoder

/** Where a link wants to go: a public screen that works signed out, or a place inside the signed-in app. */
sealed interface DeepLink {
    /** Email links: verify, reset, restore, unsubscribe. */
    data class Public(val key: NavKey) : DeepLink

    /** A tab or screen of the main scaffold; shown after sign-in when needed. */
    data class Main(val tab: NavKey, val key: NavKey? = null) : DeepLink
}

/**
 * Parses App Links of `wallet.andrew-dev.org` and the internal `webwallet://` scheme (docs/03 §7). The one-time
 * token of email links travels in the fragment (`#token=…`), as the web client reads it. Unknown paths of the
 * domain open the overview; anything else is not a link of ours.
 */
object DeepLinkParser {

    const val HOST = "wallet.andrew-dev.org"
    private const val SCHEME = "webwallet"

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun parse(link: String?): DeepLink? {
        val uri = link?.let { runCatching { URI(it) }.getOrNull() } ?: return null
        if (uri.scheme == SCHEME) return parseInternal(uri)
        if (uri.host != HOST) return null
        val token = params(uri.rawFragment)["token"]
        val query = params(uri.rawQuery)
        return when (uri.path?.trimEnd('/')) {
            "/verify-email" -> DeepLink.Public(VerifyEmailKey(token))
            "/reset-password" -> DeepLink.Public(token?.let { ResetPasswordKey(it) } ?: ForgotPasswordKey)
            "/restore-account" -> DeepLink.Public(token?.let { RestoreAccountKey(it) } ?: SignInKey)
            "/unsubscribe" -> {
                val user = query["user"]
                val unsubscribeToken = query["token"]
                if (user != null && unsubscribeToken != null) {
                    DeepLink.Public(
                        UnsubscribeKey(user, unsubscribeToken),
                    )
                } else {
                    null
                }
            }
            "/transactions" -> DeepLink.Main(
                TransactionsKey,
                if (query["new"] == "1") TransactionEditorKey(type = TransactionType.EXPENSE) else null,
            )
            "/accounts" -> DeepLink.Main(AccountsKey)
            "/budget" -> DeepLink.Main(BudgetKey)
            "/settings" -> DeepLink.Main(MoreKey, SettingsKey)
            "/goals", "/investments", "/taxes", "/reports", "/analytics", "/imports" ->
                DeepLink.Main(MoreKey, ComingSoonKey(uri.path.trim('/')))
            else -> DeepLink.Main(OverviewKey)
        }
    }

    private fun parseInternal(uri: URI): DeepLink? = when (uri.host ?: uri.authority) {
        "quick-add" -> {
            val type = params(uri.rawQuery)["type"]?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
            DeepLink.Main(TransactionsKey, TransactionEditorKey(type = type ?: TransactionType.EXPENSE))
        }
        else -> null
    }

    /** `a=1&b=2` into a map, decoded; empty values are dropped. */
    private fun params(raw: String?): Map<String, String> = raw.orEmpty()
        .split('&')
        .filter { it.isNotBlank() }
        .associate { part ->
            val name = part.substringBefore('=')
            val value = URLDecoder.decode(part.substringAfter('=', ""), Charsets.UTF_8.name()).trim()
            name to value
        }
        .filterValues { it.isNotEmpty() }
}
