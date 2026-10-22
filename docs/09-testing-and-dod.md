# 09 — Testing and Definition of Done

## 1. Test pyramid

| Level | Tooling | Scope | Where it runs |
|---|---|---|---|
| Unit | JUnit 5, MockK, Turbine, kotlinx-coroutines-test | Mappers, formatters (`MoneyFormatter`, periods, Kyiv dates), calculators (FIRE/FYM/compound, tax CSV builders), ViewModels (state transitions, effects), `DeepLinkParser`, `ApiErrorMapper`, PIN hashing | every PR |
| Repository | MockWebServer + Room in-memory (Robolectric) | Read path emits cache → fresh; stale on network error; write path upserts and invalidates; 401 → refresh → replay once; `Idempotency-Key` reuse on retry | every PR |
| Contract | Generated client + fixtures | Every key DTO deserialises from a fixture captured from the real backend (`src/test/resources/fixtures/*.json`); nullable vs required matches the spec | every PR |
| Screenshot | Roborazzi (Robolectric, Pixel 6, API 34) | Every design-system component and every screen `UiState` preview, light + dark, uk + en, font scale 1.0 and 1.3 | every PR (`verify`), record on demand |
| Compose UI | `androidx.compose.ui.test` on Robolectric where possible, else managed device | Form validation, filter sheet, transaction editor flows, PIN keypad, 2FA code field | every PR (Robolectric), nightly (device) |
| E2E | Maestro flows against `devDebug` + MockWebServer (`FakeBackend` module serving OpenAPI-shaped responses) | Sign in → add expense → appears in feed; 2FA sign-in; register → check mail; budget assign; lock/unlock; deep link verify-email | nightly, before release |
| Performance | Macrobenchmark | Cold start, feed scroll, editor open; baseline profile generation | milestone end |
| Manual | Checklist per milestone | Real backend `dev`, real Monobank token (owner only), real push, Play pre-launch report | before each Play promotion |

Coverage target: ≥ 70 % line coverage on `core:*` and ViewModels (Kover); UI composables
excluded from the metric. The number is reported, not gating, until M2; gating from M3.

## 2. Fixtures and fakes

- `core:testing` provides `FakeAccountsRepository` etc. with `MutableStateFlow`s, a
  `TestClock` for Kyiv dates, `MainDispatcherRule`, and `ProblemDetailFixtures` for each error
  code.
- `FakeBackend` (test module) is a MockWebServer `Dispatcher` with an in-memory model
  implementing the subset of endpoints each milestone needs; it validates requests against the
  OpenAPI schema (using `openapi4j` or `swagger-request-validator`) so a wrong client payload
  fails loudly.
- Fixture JSONs are captured from the backend running `local` profile with the seeded demo
  user; a script `scripts/capture-fixtures.sh` documents how.

## 3. Definition of Done (per PR)

- [ ] Scope matches a section of [04](04-screens.md) or a milestone item in [01](01-scope-and-milestones.md); no extra features.
- [ ] CI green: ktlint, detekt, Android Lint (no errors, no new warnings), unit tests, Roborazzi verify.
- [ ] New/changed screens have previews for every `UiState`, light and dark.
- [ ] All new strings in `values/strings.xml` and `values-uk/strings.xml`; no hardcoded text.
- [ ] Every new ViewModel, mapper and repository path has tests; fixtures added for new DTOs.
- [ ] Error, empty, offline and stale states implemented and visible in previews.
- [ ] Accessibility: content descriptions, 48 dp targets, TalkBack pass on the main path, font scale 2.0 does not clip amounts.
- [ ] No logging of tokens, amounts, names; no new permissions without spec change.
- [ ] Generated code untouched; if the API changed, backend PR link and regenerated spec included.
- [ ] Version catalog used for any new dependency, with a one-line justification.
- [ ] PR description: what, why, screenshots (light/dark) or screen recording for UI changes, test notes.

## 4. Definition of Done (per milestone)

- [ ] All screens for the milestone implemented per [04](04-screens.md); parity checklist against the web filled in (`docs/parity-Mx.md` in the app repo listing each web feature and its app status).
- [ ] Backend prerequisites merged to `dev` and deployed to the dev environment; `openapi.json` in sync.
- [ ] Maestro flows for the milestone pass nightly for 3 consecutive nights.
- [ ] Manual checklist executed on two real devices (one Pixel, one Samsung) on the oldest supported Android (12) and the newest.
- [ ] Macrobenchmark within budgets; baseline profile regenerated.
- [ ] Internal-track release published; crash-free ≥ 99.5 % over 7 days with ≥ 3 testers.
- [ ] `CHANGELOG.md` and Play release notes (uk/en) written.
- [ ] Spec deviations recorded in `docs/decisions/ADR-xxxx.md` in the app repo and mirrored
      back into this folder.

## 5. Review checklist for the coding agent's output

Reviewers (human or a second agent pass) check specifically:

1. State handling: no `null`-as-loading, no swallowed exceptions, every `Result` branch handled.
2. Coroutines: no `GlobalScope`, no blocking calls on Main, `viewModelScope` only, flows
   collected with `collectAsStateWithLifecycle`.
3. Compose: stable parameters, no heavy work in composition, `key`s in lazy lists,
   `remember` for derived objects, no `LaunchedEffect(Unit)` doing data loads (belongs in VM).
4. Security: tokens only via `SecretStore`; `FLAG_SECURE` where required; no secrets in
   `BuildConfig` beyond public ids.
5. Money: `BigDecimal` everywhere, no `Double`; currency always carried with the amount.
6. Dates: `KyivClock` for "today"; `LocalDate`/`YearMonth`, never `Date`/`Calendar`.
7. Parity: wording and edge cases compared against the referenced web file.
8. Localisation: Ukrainian text is natural, plurals present, no machine-translation artefacts.
