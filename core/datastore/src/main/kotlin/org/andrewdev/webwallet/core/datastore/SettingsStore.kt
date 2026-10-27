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
    /** The inputs of the goal calculators (docs/04 §10.2), like the web's local storage; reset on sign-out. */
    val calculators: CalculatorInputs = CalculatorInputs(),
)

/** What the viewer typed into FIRE, FYM and the compound calculator; a null field follows the data. */
@Serializable
data class CalculatorInputs(
    val fire: FireInputs = FireInputs(),
    val fym: FymInputs = FymInputs(),
    val compound: CompoundInputs = CompoundInputs(),
)

@Serializable
data class FireInputs(
    val monthlyExpenses: String? = null,
    val withdrawalRate: String? = null,
    val realReturn: String? = null,
    val monthlySavings: String? = null,
)

@Serializable
data class FymInputs(
    /** Choices by account id; other accounts follow their type. */
    val accounts: Map<Long, Boolean> = emptyMap(),
    val target: Int = 12,
    val expenses: String? = null,
)

@Serializable
data class CompoundInputs(
    val initial: String? = "100000",
    val monthly: String? = "10000",
    val rate: String? = "12",
    val years: String? = "10",
    val capitalization: String = "MONTHLY",
    val growth: String? = "5",
    val inflation: String? = "8",
    val real: Boolean = false,
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
    val demo: Boolean = false,
)

@Singleton
class SettingsStore @Inject constructor(private val store: DataStore<SettingsData>) {

    val data: Flow<SettingsData> = store.data

    suspend fun current(): SettingsData = store.data.first()

    suspend fun update(transform: (SettingsData) -> SettingsData) {
        store.updateData(transform)
    }
}
