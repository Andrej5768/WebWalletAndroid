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

## Flavors

Two product flavors share one code base. The `dev` flavor (`org.andrewdev.webwallet.dev`, "WebWallet Dev") talks to
a backend on the developer's machine, and the `prod` flavor talks to `https://wallet.andrew-dev.org`. Each combines
with the `debug` and `release` build types; `release` is shrunk with R8 and, for publishing, signed with the Play
upload key.

Firebase Crashlytics and Analytics are wired but off: the Google Services plugins apply only when the Firebase
configuration is present, it is never committed, and telemetry collects nothing until the user consents in the app.
Cloud Messaging arrives with push notifications in M5.

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
Before generation the build drops the cookie parameters of the contract (the web client's refresh-token and
trusted-device cookies): the app gets those tokens in the body and the Kotlin generator cannot express cookies.
CI compares the committed copy with the backend's `master` branch (`api-drift` job): informational on branches
and pull requests, blocking on `main`. Decisions that depart from the specification are recorded in
`docs/decisions`.

## Checks

CI runs detekt (ktlint-style formatting and the Compose rules), Android Lint, the unit tests of every module and the
`devDebug` and `prodRelease` builds on every push to `dev` and `main` and on every pull request, with compiler warnings treated as errors.
Screenshot tests (Roborazzi) and instrumented tests arrive with the first screens in M1.

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
