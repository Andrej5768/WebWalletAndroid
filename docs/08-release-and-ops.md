# 08 — Release and Operations

## 1. Identity

| | prod | dev |
|---|---|---|
| applicationId | `org.andrewdev.webwallet` | `org.andrewdev.webwallet.dev` |
| App name | WebWallet | WebWallet Dev |
| Backend | `https://wallet.andrew-dev.org` | `BuildConfig.API_BASE_URL` (default `http://10.0.2.2:8080`), overridable in a debug "Developer" screen |
| Firebase app | prod Android app in project `webwallet-prod` | separate Android app in the same project |
| Launcher icon | cobalt | cobalt with "DEV" ribbon |

Build types: `debug` (debuggable, no shrinking, `LeakCanary`, logging), `release` (R8 full
mode, resource shrinking, no logging). Flavors × types = 4 variants; CI builds `devDebug`,
`prodRelease`.

## 2. Versioning

- `versionName` = SemVer `MAJOR.MINOR.PATCH` from the Git tag `vX.Y.Z`.
- `versionCode` = `MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + buildNumber(0–99)`; CI computes
  it from the tag and run attempt. Monotonic by construction.
- Changelog in `CHANGELOG.md` (Keep a Changelog); Play release notes (uk/en) are taken from
  `fastlane/metadata/android/{uk,en-GB}/changelogs/<versionCode>.txt`.

## 3. Signing

- Play App Signing. The upload key lives only in GitHub Actions secrets (`UPLOAD_KEYSTORE_B64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`); never in the repo.
- Debug builds use a committed, shared `debug.keystore` so every machine and CI have the same
  SHA-1/SHA-256 for App Links and the Google Android OAuth client.
- SHA-256 fingerprints needed in: `assetlinks.json` (B7), Firebase console, Google Cloud OAuth
  Android client.

## 4. CI (GitHub Actions, repo `WebWalletAndroid`)

Workflows:

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | push to `main`/`dev`, PRs | `check`: ktlint/detekt, Android Lint (fatal on error), unit tests (all modules), Roborazzi verify, Compose compiler stability report; `assemble`: `devDebug` APK + `prodRelease` AAB (unsigned), upload artifacts; `api-drift`: compare `openapi/openapi.json` with backend `master` (informational on PR, blocking on `main`) |
| `instrumented.yml` | nightly, manual | Gradle Managed Devices (Pixel 6 API 34, ATD) running `androidTest` + Maestro flows against a MockWebServer-backed `dev` build |
| `release.yml` | tag `v*` | Build signed `prodRelease` AAB, upload mapping to Crashlytics, publish to Play **internal** track via Gradle Play Publisher (`com.github.triplet.play`) with service-account JSON secret, attach APK to GitHub Release |
| `promote.yml` | manual | Promote internal → closed → production with staged rollout (10 % → 50 % → 100 %) |

Caching: Gradle build cache (remote via GitHub cache), configuration cache on. Target CI
time for `ci.yml` ≤ 12 min.

Branching: `dev` default, `main` stable; releases are PRs `dev → main` then tag; same model as
the backend. Dependabot/Renovate weekly for the version catalog.

## 5. Google Play

- Developer account under the same identity as the web domain; app category Finance.
- Store listing in uk (default) and en-GB; screenshots from the Gallery/Maestro flows for 6.7"
  phone (8 per language); feature graphic from the logo. Short/long descriptions reuse landing
  copy from `src/landing/locales/*`.
- **Data Safety** declaration (must match reality):
  - Collected: email, name (account), financial info (transactions, balances, investments —
    user-provided), app interactions and crash logs (Firebase; optional, user can opt out),
    device/other IDs (FCM token, Firebase installation id).
  - Shared: none with third parties for their own purposes (Firebase is a processor).
  - Encrypted in transit: yes. Deletion: in-app (Settings → Data → Delete account) and on web.
  - Account deletion URL: `https://wallet.andrew-dev.org/settings?tab=data` (Play requires a
    web link); also an in-app path.
- Privacy policy URL `https://wallet.andrew-dev.org/privacy` — **update the web legal text**
  (`src/legal/content.ts`) to mention the Android app, Firebase Crashlytics/Analytics, FCM,
  and the App lock data stored on device.
- Permissions requested: `INTERNET`, `POST_NOTIFICATIONS`, `USE_BIOMETRIC`,
  `RECEIVE_BOOT_COMPLETED` (widget refresh), `VIBRATE`. No storage, camera, SMS, contacts.
- Target API: keep within Play's current requirement each August; bump `targetSdk` within a
  month of a new stable Android release.
- Pre-launch report must be clean of crashes; fix accessibility warnings flagged there.
- Release cadence: internal on every tag, production weekly at most, staged rollout, halt on
  crash-free sessions < 99.5 %.

## 6. Firebase and telemetry

- Services: Crashlytics, Analytics, Cloud Messaging. **Not** used: Remote Config (use B8),
  Performance Monitoring (revisit after M5), Auth, Firestore.
- Collection disabled in `AndroidManifest` meta-data
  (`firebase_crashlytics_collection_enabled=false`, `firebase_analytics_collection_enabled=false`)
  and enabled at runtime after consent (`Consent` screen, Settings → Privacy). Analytics
  `setUserId` is never called; `setUserProperty` only for `theme`, `language`, `lock_enabled`.
- Crashlytics custom keys: `screen`, `milestone_build`, `flavor`; never user ids or amounts.
  Non-fatal logging for `ApiError.Unexpected` with status and path (no body).
- Mapping files uploaded by the Crashlytics Gradle plugin in `release.yml`.
- Firebase config `app/google-services.json` (one file with clients for both packages,
  `org.andrewdev.webwallet` and `org.andrewdev.webwallet.dev`) is ignored by Git: downloaded from
  the console locally, written from the `GOOGLE_SERVICES_JSON` repository secret in CI. Firebase
  API key restricted to the Android packages + SHA in the console.

## 7. Backend / infrastructure touchpoints

- Caddy: serve `/.well-known/assetlinks.json` (B7); allow the new headers; no CORS change.
- Backend env additions: `GOOGLE_ANDROID_CLIENT_IDS` (B3), `FCM_CREDENTIALS_JSON`,
  `FCM_PROJECT_ID` (B4), `APP_ANDROID_MIN_VERSION_CODE`, `APP_ANDROID_LATEST_VERSION_CODE`,
  `APP_ANDROID_STORE_URL` (B8). Document them in backend `.env.example` and `docs/deployment.md`.
- Uptime: add `GET /api/v1/app/config` to the uptime checks.
- Error alerts (`ALERT_EMAIL`) now include the `X-WebWallet-Client` MDC field (B6).

## 8. Monitoring after release

| Signal | Source | Threshold / action |
|---|---|---|
| Crash-free sessions | Crashlytics, Play Vitals | < 99.5 % → halt rollout |
| ANR rate | Play Vitals | > 0.47 % (Play bad-behaviour threshold) → hotfix |
| Cold start p90 | Play Vitals / Macrobenchmark in CI | > 1.5 s → investigate |
| API error rate by client | backend logs (B6) | spikes after a release → compare versions |
| 429s from mobile | backend logs | any → confirm B9 is live |
| Push delivery failures | B4 `failed_at`, FCM console | > 5 % → inspect token hygiene |
| Forced sign-outs | analytics event `session_forced_signout{reason}` | sudden rise → refresh-token bug |

## 9. Developer experience

- `README.md` in the app repo: prerequisites (JDK 21, Android Studio latest stable), how to
  run against the local backend (`docker compose --profile app up` in the backend repo, then
  `devDebug` on an emulator), how to regenerate the API (`./gradlew :core:network:openApiGenerate`
  after copying the spec), how to run tests and screenshot record/verify.
- Debug "Developer" screen (dev flavor only): base URL override, clear cache, force token
  expiry, simulate offline, show FCM token, trigger each notification kind locally, reset
  consent/lock offer flags, open Gallery.
- Baseline profile generated with Macrobenchmark for startup + feed scroll; checked into
  `app/src/main/baseline-prof.txt` and refreshed each milestone.
