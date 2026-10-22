# 02 — Architecture

## 1. Stack

Use the latest stable version of each library at the time the milestone starts, pinned in
`gradle/libs.versions.toml`. Do not pre-release.

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x, K2 compiler, Coroutines + Flow | `explicitApi()` off; `allWarningsAsErrors` on in CI |
| Build | AGP 8.x, Gradle with configuration cache, version catalog, convention plugins in `build-logic/` | Java 21 toolchain |
| UI | Jetpack Compose (BOM), Material 3, `material3-adaptive` not used | Compose compiler metrics on in CI |
| Navigation | Navigation 3 (`androidx.navigation3`) with type-safe `@Serializable` keys | One `NavBackStack` per top-level tab, see §7 |
| DI | Hilt | `@HiltViewModel`, `@Module` per layer |
| Networking | Retrofit + OkHttp + kotlinx.serialization (`retrofit2-kotlinx-serialization-converter`) | Generated API interfaces, see §4 |
| API models | OpenAPI Generator (`org.openapi.generator` Gradle plugin), generator `kotlin`, library `jvm-retrofit2`, `serializationLibrary=kotlinx_serialization`, `dateLibrary=java8`, `typeMappings: number=java.math.BigDecimal` with a custom `BigDecimalSerializer` | Never hand-write DTOs |
| Local DB | Room (KSP) with Flow queries | Read cache only, see §5 |
| Preferences | DataStore (Proto for session + settings, Preferences for simple flags) | |
| Secure storage | Android Keystore AES-GCM key + Tink `AeadConfig`, or a thin custom wrapper around `Cipher` | Jetpack Security `EncryptedSharedPreferences` is deprecated; do not use it |
| Background | WorkManager (widget refresh, FCM token upload, cache warm-up) | |
| Charts | Vico (line, column); donut/ring charts drawn with Compose `Canvas` | |
| Icons | Lucide via `com.composables:icons-lucide`; Material Symbols only for system-like actions (back, close, more) | Category icons must map 1:1 to the web's 56 names |
| Images | None needed (no avatars). Do not add Coil unless a need appears | |
| Biometrics | `androidx.biometric` | |
| Push | Firebase Cloud Messaging | |
| Telemetry | Firebase Crashlytics, Firebase Analytics | Consent-gated |
| Widgets | Jetpack Glance | |
| Credential | `androidx.credentials` + `com.google.android.libraries.identity.googleid` for Google sign-in | M2 |
| Testing | JUnit 5 (via `de.mannodermaus.android-junit5`), MockK, Turbine, kotlinx-coroutines-test, Room in-memory, OkHttp `MockWebServer`, Compose UI test, Roborazzi screenshot tests, Maestro for E2E | See [09](09-testing-and-dod.md) |
| Static analysis | Android Lint, detekt (with compose rules), ktlint via detekt formatting | |

## 2. Module layout

Multi-module by layer and feature. Feature modules depend only on `core:*`, never on each
other; cross-feature navigation goes through typed navigation keys defined in `core:navigation`.

```
WebWalletAndroid/
├─ app/                         # Application, MainActivity, root navigation, DI graph root, shortcuts, QS tile
├─ build-logic/                 # Convention plugins: android.application, android.library, android.feature, compose, hilt, room
├─ core/
│  ├─ common/                   # Result/Either, dispatchers, clock, Kyiv date utils, Money, formatting
│  ├─ model/                    # Domain models (Account, Transaction, Category, …) — plain Kotlin, no Android deps
│  ├─ network/                  # Generated OpenAPI client, OkHttp, interceptors, ProblemDetail parsing, ApiErrorMapper
│  ├─ database/                 # Room DB, entities, DAOs, converters
│  ├─ datastore/                # Proto DataStore: session, app settings, lock settings, consent
│  ├─ security/                 # Keystore crypto, SecretStore, PIN hashing, BiometricGate
│  ├─ auth/                     # SessionManager, TokenAuthenticator, AuthState flow, sign-out orchestration
│  ├─ data/                     # Repositories (one per aggregate), cache policy, mappers DTO↔entity↔model
│  ├─ designsystem/             # Theme, colours, typography, shapes, icons, atomic components
│  ├─ ui/                       # Composite components: Amount, CategoryIcon, MoneyField, PeriodPicker, EmptyState, ErrorState, Skeletons
│  ├─ navigation/               # @Serializable nav keys for every destination, deep-link parsing
│  ├─ analytics/                # AnalyticsTracker interface, Firebase impl, no-op impl, event catalog
│  ├─ notifications/            # Channels, NotificationBuilder, FCM payload parsing → nav key
│  └─ testing/                  # Test doubles, fixtures, MainDispatcherRule, fake repositories
├─ feature/
│  ├─ auth/                     # Sign in, 2FA, register, forgot/reset, verify, restore, Google
│  ├─ lock/                     # PIN setup, PIN entry, biometric prompt, lock screen
│  ├─ overview/
│  ├─ accounts/
│  ├─ transactions/             # Feed, filters, editor, transfer pairs, recurring, planned
│  ├─ budget/
│  ├─ reports/                  # Reports + analytics
│  ├─ goals/                    # Goals, FIRE, FYM, calculator
│  ├─ investments/
│  ├─ taxes/
│  ├─ imports/
│  ├─ settings/                 # All settings tabs incl. categories/rules/payees/tags, notifications, data, about
│  └─ more/                     # The "More" hub screen
├─ widget/                      # Glance widgets + WorkManager refresh
├─ openapi/
│  ├─ openapi.json              # Copy of backend api/openapi.json (source of truth; CI drift check)
│  └─ config.yaml               # Generator options
└─ docs/                        # Copy of this spec folder (kept in sync manually)
```

Package root: `org.andrewdev.webwallet`. Application ID: `org.andrewdev.webwallet`
(`.dev` suffix for the `dev` flavor).

## 3. Layers inside a feature

```
UI (Compose screen + previews)  →  ViewModel (UiState, events)  →  Repository (core:data)  →  Api (core:network) / Dao (core:database)
```

- **Screen**: a stateless `XxxScreen(state, onEvent)` composable plus a `XxxRoute()` that
  obtains the ViewModel. Every screen has `@Preview`s for light/dark and for each major state.
- **ViewModel**: exposes one `StateFlow<XxxUiState>` built with `combine` over repository
  flows and local state, and a `Channel<XxxEffect>` for one-off effects (snackbar, navigate).
  No Android framework types inside ViewModels except `SavedStateHandle`.
- **UiState**: a sealed interface or data class per screen with explicit `Loading`, `Content`,
  `Empty`, `Error(offline: Boolean)` representations. Never `null` to mean loading.
- **Repository**: the only place that knows about both the API and the DB. Returns
  `Flow<CacheState<T>>` for reads (see §5) and `suspend fun …: Result<T, ApiError>` for writes.
- **Mappers**: pure functions `Dto.toEntity()`, `Entity.toModel()`, `Model.toRequest()` in
  `core:data`, unit tested.

Domain models in `core:model` use `BigDecimal` for money, `java.time.LocalDate`/`YearMonth`
for dates, and `Currency` as a 3-letter `String` value class `CurrencyCode`.

## 4. Networking

### 4.1 Base URL and flavors

| Flavor | Base URL | Notes |
|---|---|---|
| `dev` | `BuildConfig.API_BASE_URL`, default `http://10.0.2.2:8080` (emulator → local backend) | Cleartext allowed for `10.0.2.2` and `localhost` only, via `network_security_config.xml` debug overrides |
| `prod` | `https://wallet.andrew-dev.org` | |

All paths are `/api/v1/...`; the generated client takes the base URL from DI.

### 4.2 Interceptors (OkHttp, in this order)

1. `ClientHeadersInterceptor`: adds `User-Agent: WebWallet-Android/<versionName> (<versionCode>; Android <sdk>)`,
   `X-WebWallet-Client: android`, `X-App-Version: <versionCode>`, `Accept-Language: uk|en` from the current UI language.
2. `AuthInterceptor`: adds `Authorization: Bearer <access>` for every path except `/api/v1/auth/**` and `/.well-known/**`.
3. `IdempotencyInterceptor`: adds `Idempotency-Key: <uuid>` to `POST` requests that create
   resources (transactions, operations, goals, contributions, recurring, categories, tags,
   payees, rules, imports confirm). The key is generated once per logical attempt and reused on
   automatic retries. Requires backend B5; harmless before it ships.
4. `LoggingInterceptor` (debug only): redacts `Authorization`, `refreshToken`, `password`,
   `token`, `code`, `secret` fields in bodies and headers.

`TokenAuthenticator` (OkHttp `Authenticator`) handles 401: single-flight refresh through
`SessionManager`, then replay once. If refresh fails with `INVALID_REFRESH_TOKEN`, it triggers
forced sign-out. Never refresh on `/api/v1/auth/**` responses.

Timeouts: connect 10 s, read 30 s, write 30 s; file uploads (imports) use a separate client with
read/write 120 s. Retries: GET requests retry once on IOException with 500 ms backoff; writes
do not auto-retry except through the `Idempotency-Key` path (one retry on IOException).

### 4.3 Errors

The backend returns RFC 9457 `application/problem+json` with `code`, `errors[{field,message}]`,
and for `ACCOUNT_DELETION_SCHEDULED` also `restoreToken` and `deletionScheduledAt`.

```kotlin
sealed interface ApiError {
  data class Problem(val status: Int, val code: ErrorCode, val title: String, val detail: String?,
                     val fieldErrors: Map<String, String>, val extensions: JsonObject) : ApiError
  data class Network(val cause: IOException) : ApiError     // offline / timeout / DNS
  data class Unexpected(val status: Int?, val cause: Throwable?) : ApiError
}
```

`ErrorCode` is an enum mirroring the backend list (VALIDATION_FAILED, MALFORMED_REQUEST,
INVALID_CREDENTIALS, EMAIL_NOT_VERIFIED, ACCOUNT_DISABLED, ACCOUNT_DELETION_SCHEDULED,
TOO_MANY_ATTEMPTS, TOO_MANY_REQUESTS, INVALID_TOKEN, INVALID_REFRESH_TOKEN,
INVALID_CURRENT_PASSWORD, INVALID_TWO_FACTOR_CODE, TWO_FACTOR_EXPIRED, UNAUTHORIZED, FORBIDDEN,
NOT_FOUND, RESOURCE_IN_USE, CONFLICT, INVALID_EXTERNAL_TOKEN, EXTERNAL_SERVICE_UNAVAILABLE,
INVALID_IMPORT_FILE, INTERNAL_ERROR) plus `UNKNOWN`. The backend's `detail` is already
localised via `Accept-Language`; show it as the message. Field errors map to form fields by
name; unmapped ones are shown under the form.

User-facing mapping: `Network` → "No connection" banner with Retry; 429 → "Too many attempts,
try again in a minute"; 5xx/502 → "Service unavailable" with Retry; validation → inline.

### 4.4 OpenAPI generation

- `openapi/openapi.json` is a verbatim copy of backend `api/openapi.json`. CI job `api-drift`
  downloads `https://raw.githubusercontent.com/Andrej5768/WebWallet/master/api/openapi.json`
  and fails if it differs (informational on PRs, blocking on `main`).
- Generation runs in `core:network` build; generated sources are not committed.
- Nullable fields follow the spec's `type: [X, "null"]`. The generator must emit `T?` for them
  and non-null for everything else; verify with a unit test deserialising a fixture per key DTO.
- Partial `PUT` semantics for accounts/categories/tags (omitted = keep, `null` = clear, `0` =
  clear for `creditLimit`/`interestRate`) are preserved by using `explicitNulls = false` and
  request builders that only set changed fields.

## 5. Caching ("online + read cache")

Goal: the app opens instantly with the last known data and never shows a blank screen when
offline; writes always go to the server.

### 5.1 What is cached (Room)

| Aggregate | Table(s) | Refresh trigger | TTL for "stale" badge |
|---|---|---|---|
| Current user | `user` | app start, after profile edits | 1 h |
| Accounts + summary | `accounts`, `account_summary` | app start, Accounts/Overview open, after any transaction write | 15 min |
| Categories tree, tags, payees, rules | `categories`, `tags`, `payees`, `rules` | app start (once per 6 h), Settings open, after edits | 6 h |
| Transactions | `transactions`, `transaction_splits`, `transaction_tags`, `feed_pages` | feed open per filter key, pull-to-refresh, after writes | 15 min |
| Budget month | `budget_months`, `budget_rows` | Budget open per month, after assign/move | 15 min |
| Goals | `goals` | Goals open, after edits | 1 h |
| Recurring + occurrences | `recurring`, `occurrences` | Transactions open, after edits | 1 h |
| Reports/analytics responses | `json_cache(key, body, fetchedAt)` | on open per (endpoint, params) | 1 h |
| Investments: portfolio, positions, dividends, operations, instruments | dedicated tables + `json_cache` for history/portfolio | Investments open | 15 min |
| Taxes, imports, Monobank status, notifications/telegram settings | `json_cache` | on open | 15 min |
| App config (B8) | DataStore | app start, every 24 h | — |

`feed_pages` stores `(filterKey, page, transactionIds, fetchedAt)` so the feed can be rebuilt
for the exact filter set that was last viewed. The feed cache keeps the last 10 filter keys.

### 5.2 Read model

```kotlin
sealed interface CacheState<out T> {
  data class Fresh<T>(val data: T) : CacheState<T>
  data class Stale<T>(val data: T, val fetchedAt: Instant, val refreshing: Boolean, val error: ApiError?) : CacheState<T>
  data class Missing(val loading: Boolean, val error: ApiError?) : CacheState<Nothing>
}
```

Repositories emit cached data immediately, start a network refresh, then emit `Fresh` or
`Stale(error=…)`. Screens show a thin "Showing data from 14:02 · Offline" strip on `Stale`
with a network error, and a skeleton only on `Missing(loading=true)`.

### 5.3 Writes

- Writes go to the API. On success the repository upserts the response into Room and
  invalidates dependent aggregates (a transaction write invalidates accounts, summary, the
  budget month of its date, goals when a linked account is involved, and the forecast cache).
- On `Network` error the UI shows "You're offline. Changes need a connection." and keeps the
  form open with its values. No local queue.
- Optimistic updates are **not** used except for the budget "assigned" inline field and the
  quick-categorize action, which revert on failure.

### 5.4 Invalidation on sign-out

`SessionManager.signOut()` clears Room (`clearAllTables()`), the session DataStore, the
`json_cache`, WorkManager jobs, FCM registration on the server (best effort), and widget state.
Lock settings (PIN hash, biometric flag) are also cleared; app settings (theme, language,
analytics consent) are kept.

## 6. Session and auth state

`core:auth` owns a `StateFlow<AuthState>`:

```kotlin
sealed interface AuthState {
  data object Unknown : AuthState            // before the first restore attempt
  data object SignedOut : AuthState
  data class SignedIn(val userId: Long, val locked: Boolean) : AuthState
}
```

Details (storage, refresh, lock) are in [03](03-auth-and-security.md). The root composable
switches between `AuthNavHost`, `LockScreen` and `MainNavHost` based on this state.

## 7. Navigation

- Root: `AuthState.Unknown` → splash (system splash screen API, no custom splash);
  `SignedOut` → auth graph; `SignedIn(locked=true)` → lock screen over a blank scaffold;
  `SignedIn(locked=false)` → main scaffold.
- Main scaffold: Material 3 `NavigationBar` with five tabs — **Overview, Transactions, Budget,
  Accounts, More**. Each tab has its own back stack (Navigation 3 `rememberNavBackStack` per
  tab, saved in `rememberSaveable`). Re-tapping the current tab pops to its root and scrolls to
  top.
- A centered FAB "+" is shown on Overview, Transactions and Accounts and opens the transaction
  editor as a full-screen dialog destination.
- Editors and detail screens are full-screen destinations (no bottom sheets for long forms).
  Bottom sheets are used for pickers (account, category, period, filters) and short action menus.
- Deep links: `core:navigation` has `DeepLinkParser.parse(uri): NavKey?` for App Links
  (`https://wallet.andrew-dev.org/{verify-email,reset-password,restore-account,unsubscribe,
  transactions,budget,goals,...}`), notification payloads (`route` field) and shortcuts
  (`webwallet://quick-add?type=EXPENSE`). Unknown links open the Overview tab.
- Back behaviour: predictive back enabled (`android:enableOnBackInvokedCallback="true"`); the
  system back from a tab root goes to Overview, then exits.

Nav keys live in `core:navigation` as `@Serializable` objects/data classes, e.g.
`TransactionEditorKey(id: Long? = null, type: TransactionType? = null, accountId: Long? = null)`.

## 8. Formatting, dates, money

- `Money(amount: BigDecimal, currency: CurrencyCode)`. Formatting via `MoneyFormatter` using
  `java.text.DecimalFormat` with the UI locale (`uk-UA` → `1 234,50 ₴`; `en-GB` → `₴1,234.50`),
  grouping on, 2 fraction digits (0 for whole amounts ≥ 1 000 000 in compact mode), negative
  with a leading minus, never parentheses. Currency symbol table mirrors the web
  (`₴ $ € £ zł …`), falling back to the ISO code.
- `KyivClock.today(): LocalDate` is the single source of "today". Period presets (month,
  previous month, 3 months, year, 12 months, all, custom) match `src/lib/periods.ts`.
- Dates display: `d MMM` within the current year, `d MMM yyyy` otherwise; feed day headers
  "Today", "Yesterday", then `EEEE, d MMMM`.
- Input: `MoneyField` accepts both `,` and `.` as decimal separators, strips spaces, supports a
  leading `-` only where allowed, and evaluates simple `+ - * /` expressions on blur (as a
  convenience; the web does not do this — keep it, it is cheap and useful on a phone).

## 9. Analytics and logging

- `AnalyticsTracker.screen(name)` and `.event(name, params)` with a fixed catalog in
  `core:analytics/EventCatalog.kt`. Params are enums/booleans/small ints only. No free text.
- Firebase Analytics and Crashlytics collection is **off** until the user accepts in the
  first-run consent screen or Settings → Privacy; setting is stored in DataStore and applied via
  `setAnalyticsCollectionEnabled` / `setCrashlyticsCollectionEnabled` at startup.
- Logging: Timber; release builds plant only a Crashlytics tree that records `WARN+` without
  message payloads containing user data. Never log request/response bodies in release.

## 10. Conventions for the coding agent

- Kotlin style: official; 120 columns; trailing commas; no wildcard imports.
- Naming: `XxxScreen`, `XxxRoute`, `XxxViewModel`, `XxxUiState`, `XxxEvent`, `XxxEffect`,
  `XxxRepository`, `XxxDao`, `XxxEntity`, `XxxDto` (generated), `XxxMapper.kt`.
- Strings: `strings.xml` keys `feature_screen_element`, e.g. `transactions_feed_empty_title`.
  Both `values/` (en) and `values-uk/` must have every key; a lint check (`MissingTranslation`)
  is an error.
- Previews: every screen file ends with `@PreviewLightDark` previews for each `UiState`.
- Tests: every ViewModel has a test; every mapper has a test; every repository has a
  MockWebServer + in-memory Room test for its read path and one write path.
- Dependencies: only through the version catalog; adding a library requires a one-line
  justification in the PR description.
- Generated code is never edited. If the API needs a change, change the backend and regenerate.
- Feature flags: `AppConfig` from B8 can disable Google sign-in, market search, Telegram,
  and set a minimum supported `versionCode`; the UI must handle each flag.
- One milestone per branch series; PRs ≤ ~800 changed lines where possible; each PR green in CI.
