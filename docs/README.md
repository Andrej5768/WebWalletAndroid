# WebWallet Android — Technical Specification

Specification for the native Android client of WebWallet, a personal finance service with
envelope budgeting, investments and Ukrainian taxes. The backend lives in this repository
(`D:\Dev\WebWallet`), the web client in `D:\Dev\WebWalletClient`. The Android app will live in a
separate repository, `WebWalletAndroid` (`D:\Dev\WebWalletAndroid`).

Status: v1.0, 2026-10-21. Written against backend 1.0.1 (OpenAPI `api/openapi.json`, 109 paths)
and web client commit `711112a`.

## Decisions in one table

| Topic | Decision |
|---|---|
| Platform | Android only, Kotlin, Jetpack Compose. iOS is out of scope; the architecture must not block a future KMP extraction of the domain layer. |
| Scope | Full feature parity with the web client, delivered in milestones M1–M5 (see [01](01-scope-and-milestones.md)). |
| Backend | May be changed. Required changes are listed in [07](07-backend-changes.md) and are prerequisites for specific milestones. |
| Code production | Code is generated with an AI coding agent. The spec is therefore explicit about structure, naming, libraries and acceptance criteria. |
| Min / target SDK | minSdk 31 (Android 12). targetSdk = latest stable required by Google Play (36 at time of writing). |
| Form factor | Phones only, portrait locked. Tablets get the phone layout. |
| Offline | Online with a read cache (Room). Reads work offline from cache; writes require network. No offline queue. |
| Device lock | PIN + biometrics, optional. Offered once after the first sign-in, configurable in Settings. |
| Mobile-only features | Push notifications (FCM), home-screen widgets (Glance), app shortcuts and a Quick Settings tile for quick entry. No receipt scanning. |
| Design | Material 3 components with the web brand: cobalt accent, Darcula-like dark theme, Fixel/Inter fonts, Lucide icons. No Dynamic Color. |
| Telemetry | Firebase Crashlytics + Firebase Analytics, with an in-app opt-out. |
| Distribution | Google Play, open production release. |
| Repository | Separate repo `Andrej5768/WebWalletAndroid`, GitHub Actions CI, Play publishing from CI. |
| Languages | UI in Ukrainian and English, same as web. This spec is in English. |

## Documents

| # | File | Contents |
|---|---|---|
| 01 | [01-scope-and-milestones.md](01-scope-and-milestones.md) | Goals, non-goals, milestones M1–M5 with acceptance criteria, dependencies on backend changes |
| 02 | [02-architecture.md](02-architecture.md) | Tech stack, module layout, layers, networking, caching, error handling, navigation, conventions for the coding agent |
| 03 | [03-auth-and-security.md](03-auth-and-security.md) | Sign-in flows, token storage, 2FA, Google, account deletion, PIN/biometric lock, deep links |
| 04 | [04-screens.md](04-screens.md) | Every screen: purpose, layout, data, actions, states, endpoints |
| 05 | [05-design-system.md](05-design-system.md) | M3 theme built from web tokens, typography, icons, shared components, formatting rules |
| 06 | [06-notifications-widgets-shortcuts.md](06-notifications-widgets-shortcuts.md) | FCM, notification channels, widgets, shortcuts, Quick Settings tile |
| 07 | [07-backend-changes.md](07-backend-changes.md) | API changes the backend must ship for the app |
| 08 | [08-release-and-ops.md](08-release-and-ops.md) | Build variants, signing, CI, Play listing, Data Safety, Firebase, versioning, monitoring |
| 09 | [09-testing-and-dod.md](09-testing-and-dod.md) | Test strategy, Definition of Done, review checklist |
| — | [backend-tasks/](backend-tasks/README.md) | Implementation-ready backend tickets (B1, B2, B6, B8 for M1), tied to the current code |

## How to use this spec with a coding agent

1. Work one milestone at a time, in order. Each milestone ends with a runnable, releasable app.
2. Before a milestone, read `README.md`, `01`, `02`, `05` and the screen sections of `04` for that
   milestone. Read `03` for M1, `06` for M5, `08` for M1 and M5.
3. The OpenAPI document `api/openapi.json` in the backend repo is the contract. Generate the
   Kotlin models and API interfaces from it; never hand-write DTOs. When this spec and the
   OpenAPI document disagree, the OpenAPI document wins for field names and types, and this spec
   wins for behaviour.
4. Backend prerequisites for a milestone (see `01` and `07`) must be merged to backend `dev`
   and the OpenAPI document regenerated before the client work that depends on them starts.
5. Do not invent features. Anything not in `04` is out of scope unless it is a direct
   consequence of a listed requirement (for example, a loading state).
6. Every PR must satisfy the Definition of Done in `09`.

## Source material

- Backend: `D:\Dev\WebWallet` — `README.md` (API conventions), `api/openapi.json`, controllers
  under `src/main/java/com/andrewcorp/webwallet/*`, Flyway migrations under
  `src/main/resources/db/migration`.
- Web client: `D:\Dev\WebWalletClient` — `src/app/router.tsx` (routes), `src/api/*` (session
  model), `src/theme/*` (tokens), `src/lib/categoryIcons.ts` (56 category icons),
  `src/lib/palette.ts` (12 colours), `src/i18n/*` and `*/locales/*` (all UI strings).
- Logos: `docs/logo/webwallet-logo-{rounded,square}-{120,512}.png`.
