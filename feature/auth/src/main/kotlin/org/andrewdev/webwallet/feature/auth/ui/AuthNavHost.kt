package org.andrewdev.webwallet.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.andrewdev.webwallet.core.navigation.CheckMailKey
import org.andrewdev.webwallet.core.navigation.CheckMailPurpose
import org.andrewdev.webwallet.core.navigation.ForgotPasswordKey
import org.andrewdev.webwallet.core.navigation.PendingDeletionKey
import org.andrewdev.webwallet.core.navigation.RegisterKey
import org.andrewdev.webwallet.core.navigation.ResetPasswordKey
import org.andrewdev.webwallet.core.navigation.RestoreAccountKey
import org.andrewdev.webwallet.core.navigation.SignInKey
import org.andrewdev.webwallet.core.navigation.TwoFactorKey
import org.andrewdev.webwallet.core.navigation.UnsubscribeKey
import org.andrewdev.webwallet.core.navigation.VerifyEmailKey

/**
 * The signed-out graph (docs/04 §1). [start] is a public deep-link key (verify, reset, restore, unsubscribe) or
 * null for the sign-in; signing in is observed by the root through the session state, not here.
 */
@Composable
fun AuthNavHost(
    start: NavKey?,
    initialNotice: SignInNotice?,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val backStack = rememberNavBackStack(SignInKey)
    var notice by rememberSaveable { mutableStateOf(initialNotice) }
    var prefillEmail by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(start) {
        if (start != null && backStack.lastOrNull() != start) backStack.add(start)
    }

    fun backToSignIn(withNotice: SignInNotice? = null) {
        withNotice?.let { notice = it }
        backStack.clear()
        backStack.add(SignInKey)
    }

    val actions = remember(onToggleLanguage, onToggleTheme) {
        AuthActions(
            toggleLanguage = onToggleLanguage,
            toggleTheme = onToggleTheme,
            openRegister = { backStack.add(RegisterKey) },
            openForgotPassword = { backStack.add(ForgotPasswordKey) },
            openTwoFactor = { backStack.add(TwoFactorKey(it)) },
            openPendingDeletion = { date, token -> backStack.add(PendingDeletionKey(date, token)) },
            openCheckMail = { backStack.add(CheckMailKey(it, CheckMailPurpose.REGISTERED)) },
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SignInKey> {
                SignInRoute(actions = actions, notice = notice, prefillEmail = prefillEmail)
            }
            entry<TwoFactorKey> { key ->
                TwoFactorRoute(
                    challenge = key.challenge,
                    actions = actions,
                    onBack = { backStack.removeLastOrNull() },
                    onExpired = { backToSignIn(SignInNotice.CODE_EXPIRED) },
                )
            }
            entry<PendingDeletionKey> { key ->
                PendingDeletionRoute(
                    deletionScheduledAt = key.deletionScheduledAt,
                    restoreToken = key.restoreToken,
                    actions = actions,
                    onLeave = { backToSignIn() },
                    onTwoFactor = { backStack.add(TwoFactorKey(it)) },
                )
            }
            entry<RegisterKey> {
                RegisterRoute(
                    actions = actions,
                    onBack = { backStack.removeLastOrNull() },
                    onRegistered = { email ->
                        prefillEmail = email
                        backStack.add(CheckMailKey(email, CheckMailPurpose.REGISTERED))
                    },
                )
            }
            entry<CheckMailKey> { key ->
                CheckMailRoute(email = key.email, purpose = key.purpose, actions = actions, onBack = { backToSignIn() })
            }
            entry<ForgotPasswordKey> {
                ForgotPasswordRoute(
                    actions = actions,
                    onBack = { backStack.removeLastOrNull() },
                    onSent = { email ->
                        prefillEmail = email
                        backStack.add(CheckMailKey(email, CheckMailPurpose.RESET_PASSWORD))
                    },
                )
            }
            entry<ResetPasswordKey> { key ->
                ResetPasswordRoute(
                    token = key.token,
                    actions = actions,
                    onDone = { backToSignIn(SignInNotice.PASSWORD_CHANGED) },
                    onRequestNew = {
                        backToSignIn()
                        backStack.add(ForgotPasswordKey)
                    },
                )
            }
            entry<VerifyEmailKey> { key ->
                VerifyEmailRoute(
                    token = key.token,
                    actions = actions,
                    onSignIn = { backToSignIn(SignInNotice.VERIFIED) },
                )
            }
            entry<RestoreAccountKey> { key ->
                RestoreAccountRoute(
                    token = key.token,
                    actions = actions,
                    onSignIn = { backToSignIn(SignInNotice.RESTORED) },
                    onTwoFactor = { backStack.add(TwoFactorKey(it)) },
                )
            }
            entry<UnsubscribeKey> { key ->
                UnsubscribeRoute(user = key.user, token = key.token, actions = actions, onSignIn = { backToSignIn() })
            }
        },
    )
}
