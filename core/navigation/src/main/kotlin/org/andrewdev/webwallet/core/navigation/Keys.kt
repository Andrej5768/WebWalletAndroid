package org.andrewdev.webwallet.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.model.TransactionType

// Auth flow (docs/04 §1)

@Serializable data object SignInKey : NavKey

@Serializable data class TwoFactorKey(val challenge: String) : NavKey

@Serializable data class PendingDeletionKey(val deletionScheduledAt: String, val restoreToken: String) : NavKey

@Serializable data object RegisterKey : NavKey

/** Why the user is told to look at their mail: after a registration or a password reset request. */
enum class CheckMailPurpose { REGISTERED, RESET_PASSWORD }

@Serializable data class CheckMailKey(val email: String, val purpose: CheckMailPurpose) : NavKey

@Serializable data object ForgotPasswordKey : NavKey

@Serializable data class ResetPasswordKey(val token: String) : NavKey

@Serializable data class VerifyEmailKey(val token: String?) : NavKey

@Serializable data class RestoreAccountKey(val token: String) : NavKey

@Serializable data class UnsubscribeKey(val user: String, val token: String) : NavKey

// First-run screens after sign-in

@Serializable data object ConsentKey : NavKey

@Serializable data object LockOfferKey : NavKey

// Lock (docs/04 §2)

enum class PinSetupMode { CREATE, CHANGE }

@Serializable data class PinSetupKey(val mode: PinSetupMode) : NavKey

// Main tabs (docs/02 §7)

@Serializable data object OverviewKey : NavKey

@Serializable data object TransactionsKey : NavKey

@Serializable data object BudgetKey : NavKey

@Serializable data object AccountsKey : NavKey

@Serializable data object MoreKey : NavKey

// Accounts

@Serializable data class AccountEditorKey(val id: Long? = null) : NavKey

@Serializable data class AccountTransactionsKey(val accountId: Long) : NavKey

@Serializable data class PayeeTransactionsKey(val payeeId: Long, val name: String) : NavKey

// Transactions

@Serializable
data class TransactionEditorKey(
    val id: Long? = null,
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val payeeId: Long? = null,
    /** From the FAB: "Add another" after saving. */
    val fromFab: Boolean = false,
) : NavKey

@Serializable data object TransferPairsKey : NavKey

// More and settings (docs/04 §8, §14)

@Serializable data object SettingsKey : NavKey

@Serializable data object ProfileSettingsKey : NavKey

@Serializable data object SecuritySettingsKey : NavKey

@Serializable data object AppLockSettingsKey : NavKey

@Serializable data object PrivacySettingsKey : NavKey

@Serializable data object AboutKey : NavKey

@Serializable data object TwoFactorSetupKey : NavKey

@Serializable data class RecoveryCodesKey(val codes: List<String>) : NavKey

@Serializable data object ChangePasswordKey : NavKey

/** Sections that arrive with later milestones: the hub shows them as "coming soon". */
@Serializable data class ComingSoonKey(val section: String) : NavKey
