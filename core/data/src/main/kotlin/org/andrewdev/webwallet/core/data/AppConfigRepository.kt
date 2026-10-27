package org.andrewdev.webwallet.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.andrewdev.webwallet.core.common.KyivClock
import org.andrewdev.webwallet.core.datastore.AppConfigData
import org.andrewdev.webwallet.core.datastore.SettingsStore
import org.andrewdev.webwallet.core.model.AppConfig
import org.andrewdev.webwallet.core.network.ApiResult
import org.andrewdev.webwallet.core.network.apiCall
import org.andrewdev.webwallet.core.network.generated.api.AppApi
import org.andrewdev.webwallet.core.network.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** `GET /app/config` (B8), kept in the settings store and refreshed once a day at app start. */
@Singleton
class AppConfigRepository @Inject constructor(
    private val api: AppApi,
    private val settings: SettingsStore,
    private val clock: KyivClock,
) {
    val config: Flow<AppConfig?> = settings.data.map { it.appConfig?.toModel() }

    /** Fetches when the stored copy is older than a day; failures keep the old copy. */
    suspend fun refreshIfDue(): ApiResult<Unit>? {
        val stored = settings.current().appConfig
        val age = stored?.let { Duration.ofMillis(clock.now().toEpochMilli() - it.fetchedAtEpochMs) }
        if (age != null && age < REFRESH_EVERY) return null
        return apiCall { api.config() }.map { response ->
            val model = response.toModel()
            settings.update {
                it.copy(
                    appConfig = AppConfigData(
                        minSupportedVersionCode = model.minSupportedVersionCode,
                        latestVersionCode = model.latestVersionCode,
                        storeUrl = model.storeUrl,
                        googleSignIn = model.googleSignIn,
                        marketSearch = model.marketSearch,
                        telegram = model.telegram,
                        push = model.push,
                        fetchedAtEpochMs = clock.now().toEpochMilli(),
                        demo = model.demo,
                    ),
                )
            }
        }
    }

    private fun AppConfigData.toModel() =
        AppConfig(
            minSupportedVersionCode,
            latestVersionCode,
            storeUrl,
            googleSignIn,
            marketSearch,
            telegram,
            push,
            demo,
        )

    companion object {
        private val REFRESH_EVERY: Duration = Duration.ofHours(24)
    }
}
