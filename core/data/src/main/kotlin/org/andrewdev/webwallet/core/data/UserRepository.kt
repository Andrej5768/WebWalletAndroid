package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.andrewdev.webwallet.core.auth.SessionManager
import org.andrewdev.webwallet.core.model.User
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.CurrentUserApi
import org.andrewdev.webwallet.core.network.generated.model.ChangePasswordRequest
import org.andrewdev.webwallet.core.network.generated.model.ConfirmIdentityRequest
import org.andrewdev.webwallet.core.network.generated.model.DisableTwoFactorRequest
import org.andrewdev.webwallet.core.network.generated.model.TwoFactorCodeRequest
import org.andrewdev.webwallet.core.network.generated.model.UpdateProfileRequest
import org.andrewdev.webwallet.core.network.map
import org.andrewdev.webwallet.core.network.onOk
import javax.inject.Inject
import javax.inject.Singleton

/** The 2FA secret and the URI the authenticator app takes. */
data class TwoFactorSetup(val secret: String, val otpauthUri: String)

@Singleton
class UserRepository @Inject constructor(
    private val api: CurrentUserApi,
    private val jsonCache: JsonCache,
    private val cache: Cache,
    private val session: SessionManager,
) {
    val user: Flow<CacheState<User>> = cache.observe(
        key = KEY,
        ttl = Cache.HOUR,
        local = jsonCache.observe(KEY, UserJson.serializer()).map { it?.toModel() },
        fetch = ::fetch,
    )

    suspend fun refresh(): ApiResult<Unit> = fetch()

    suspend fun updateProfile(
        firstName: String,
        lastName: String?,
        baseCurrency: String,
        locale: String,
    ): ApiResult<User> =
        apiCall {
            api.updateProfile(
                UpdateProfileRequest(
                    baseCurrency = baseCurrency,
                    firstName = firstName.trim(),
                    locale = locale,
                    lastName = lastName?.trim()?.ifEmpty { null },
                ),
            )
        }.map { it.toModel() }.onOk { store(it) }

    /** The backend answers with new tokens; the session goes on, the other devices are signed out. */
    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        apiCall { api.changePassword(ChangePasswordRequest(current, new)) }.map { session.replaceTokens(it) }

    suspend fun setupTwoFactor(password: String?, code: String?): ApiResult<TwoFactorSetup> =
        apiCall { api.setup(ConfirmIdentityRequest(code = code, password = password)) }
            .map { TwoFactorSetup(it.secret, it.otpauthUri) }

    suspend fun enableTwoFactor(code: String): ApiResult<List<String>> =
        apiCall { api.enable(TwoFactorCodeRequest(code.trim())) }.map { it.recoveryCodes }.onOk { fetch() }

    suspend fun disableTwoFactor(code: String, password: String?): ApiResult<Unit> =
        apiCall { api.disable(DisableTwoFactorRequest(code.trim(), password)) }.onOk { fetch() }

    suspend fun regenerateRecoveryCodes(code: String): ApiResult<List<String>> =
        apiCall {
            api.regenerateRecoveryCodes(
                TwoFactorCodeRequest(code.trim()),
            )
        }.map { it.recoveryCodes }.onOk { fetch() }

    private suspend fun fetch(): ApiResult<Unit> = apiCall { api.me() }.map { store(it.toModel()) }

    private suspend fun store(user: User) = jsonCache.put(KEY, UserJson.serializer(), UserJson.of(user))

    companion object {
        const val KEY = "me"
    }
}
