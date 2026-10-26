package org.andrewdev.webwallet.core.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.andrewdev.webwallet.core.model.BalanceMode
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeSetting { SYSTEM, LIGHT, DARK }

/** The UI language: the system's, or a fixed one; the profile's `locale` wins once signed in (docs/01 §6). */
enum class LanguageSetting { SYSTEM, UK, EN }

/** `settings.json`: what survives a sign-out (theme, language, consent) and per-device conveniences. */
@Serializable
data class SettingsData(
    val theme: ThemeSetting = ThemeSetting.SYSTEM,
    val language: LanguageSetting = LanguageSetting.SYSTEM,
    /** null until the consent screen was answered. */
    val telemetryConsent: Boolean? = null,
    val balanceMode: BalanceMode = BalanceMode.NET_WORTH,
    val showArchivedAccounts: Boolean = false,
    val collapsedAccountGroups: Set<String> = emptySet(),
    /** The minimum balance of the forecast chart in the base currency, as the web stores it. */
    val forecastMinimum: String? = null,
    val appConfig: AppConfigData? = null,
)

/** The cached `GET /app/config` (B8) with when it was fetched. */
@Serializable
data class AppConfigData(
    val minSupportedVersionCode: Int,
    val latestVersionCode: Int,
    val storeUrl: String,
    val googleSignIn: Boolean,
    val marketSearch: Boolean,
    val telegram: Boolean,
    val push: Boolean,
    val fetchedAtEpochMs: Long,
)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<SettingsData>) {

    val data: Flow<SettingsData> = store.data

    suspend fun current(): SettingsData = store.data.first()

    suspend fun update(transform: (SettingsData) -> SettingsData) {
        store.updateData(transform)
    }
}
