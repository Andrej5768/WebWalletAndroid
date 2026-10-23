<h1>WebWallet Android</h1>

The native Android client of [WebWallet](https://github.com/Andrej5768/WebWallet), a personal finance service with
envelope budgeting, investments and Ukrainian taxes. The backend lives in that repository, the web client in
[WebWalletClient](https://github.com/Andrej5768/WebWalletClient). This app aims at full parity with the web, with the
day-to-day actions (balances, adding a transaction, the budget) faster on the phone than in a browser.

Kotlin, Jetpack Compose, Material 3 with the web brand, online with a read cache. Phones only, Android 12+.

## Status

Milestone **M0, project skeleton**: the build, the module layout, the design system with a debug gallery and the
generated API client. No user-visible features yet; the app opens on the gallery. The plan is in
`docs/01-scope-and-milestones.md`:

| Milestone | Contents |
|---|---|
| M0 | Skeleton: Gradle, convention plugins, design system, OpenAPI client, CI |
| M1 | Sign in and 2FA, app lock, accounts, transactions, security settings |
| M2 | Budget, overview charts, recurring, goals, reports, the rest of settings, Google sign-in |
| M3 | Investments |
| M4 | Taxes and imports |
| M5 | Push, widgets, shortcuts, Play release |

## Getting started

Requirements: JDK 21, the latest stable Android Studio (or the Android SDK with platform 37), Docker for the backend.

1. Run the backend locally from the backend repository: `docker compose --profile app up -d --build`
   (see its README). The API answers on `http://localhost:8080`, which the emulator reaches as `10.0.2.2`.
2. Open this project in Android Studio and run the `devDebug` variant on an emulator, or from the command line:

```bash
./gradlew :app:installDevDebug
```

The `dev` flavor (`org.andrewdev.webwallet.dev`, "WebWallet Dev") talks to `apiBaseUrl` from `gradle.properties`,
`http://10.0.2.2:8080` by default; override it with `-PapiBaseUrl=http://192.168.1.10:8080` for a real device.
The `prod` flavor talks to `https://wallet.andrew-dev.org`.

Firebase (Crashlytics, Analytics, Cloud Messaging) is wired but off: the Google Services plugins apply only once
`app/google-services.json` exists, and telemetry collects nothing until the user consents in the app. The file is
not committed: download it from the Firebase console (it must list both packages, `org.andrewdev.webwallet` and
`org.andrewdev.webwallet.dev`) as described in `docs/08-release-and-ops.md` §6. CI writes
it from the `GOOGLE_SERVICES_JSON` repository secret.

## Project layout

```
app/                 Application, MainActivity, root navigation (M1), shortcuts and the Quick Settings tile (M5)
build-logic/         Convention plugins: webwallet.android.{application,library,feature}, compose, hilt, room, jvm.library
core/common          Kyiv clock, dispatchers, formatting (plain Kotlin)
core/model           Domain models: Money, CurrencyCode, … (plain Kotlin, no Android)
core/network         The API client generated from openapi/openapi.json, OkHttp, interceptors (M1)
core/designsystem    Theme, colours, typography, shapes, icons, the debug gallery
core/testing         Test helpers shared by the modules
openapi/             openapi.json, a verbatim copy of the backend contract, and the generator options
docs/                The specification this app is built from; kept locally, not in Git (copy of the backend repository's docs/mobile-android)
```

Feature modules (`feature/*`) and the other `core/*` modules of `docs/02-architecture.md`
are added with the milestone that first needs them.

## The API client

The backend contract `openapi/openapi.json` is the source of truth; `core:network` generates the Kotlin models
and Retrofit interfaces from it at build time (nothing generated is committed), and DTOs are never written by hand.
After a backend change:

```bash
cp ../WebWallet/api/openapi.json openapi/openapi.json
./gradlew :core:network:openApiGenerate
```

Before generation the build drops the cookie parameters of the contract (the web client's refresh-token and
trusted-device cookies): the app gets those tokens in the body and the Kotlin generator cannot express cookies.
CI compares the committed copy with the backend's `master` branch (`api-drift` job): informational on branches
and pull requests, blocking on `main`. Decisions that depart from the specification are recorded in
`docs/decisions`.

## Checks

```bash
./gradlew detekt                         # ktlint-style formatting and the Compose rules; -PdetektAutoCorrect=true fixes
./gradlew lintDevDebug                   # Android Lint, warnings are errors
./gradlew testDevDebugUnitTest test      # unit tests of the Android and the JVM modules
./gradlew assembleDevDebug bundleProdRelease
```

CI (`.github/workflows/ci.yml`) runs the same on every push and pull request with `-PwarningsAsErrors=true`, so a
compiler warning that passes locally fails there. Screenshot tests (Roborazzi) and instrumented tests arrive with
the first screens in M1.

## Conventions

The rules for code written in this repository are in `docs/02-architecture.md` §10 and the
Definition of Done in `docs/09-testing-and-dod.md`: one milestone at a time, strings in
both `values/` and `values-uk/`, previews for every screen state, tests for every ViewModel, mapper and repository
path, `BigDecimal` for money, `KyivClock` for "today", dependencies only through `gradle/libs.versions.toml`.

Branches: `dev` is the default and `main` is stable, as in the backend. Versions come from Git tags `vX.Y.Z`
(`versionCode = MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + build`); the changelog is in `CHANGELOG.md`.

## License

Source available for reference only, see [LICENSE.md](LICENSE.md). The bundled fonts are under the SIL Open Font
License.
