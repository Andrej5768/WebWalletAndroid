# 01 — Scope and Milestones

## 1. Product goal

Give WebWallet users a phone-native way to do everything they can do on the web, with the
day-to-day actions (checking balances, adding a transaction, glancing at the budget) being
faster on the phone than on the web. The phone is the primary device for *recording* money;
the web stays the primary device for *analysing* it, but nothing is withheld from the phone.

## 2. Users

Existing WebWallet users. There is one role (`USER`); the `ADMIN` role has no UI anywhere and
is ignored by the app. Users are Ukrainian-speaking or English-speaking, keep money in several
currencies with UAH as the usual base currency, and many are FOP (sole proprietors) and retail
investors.

## 3. In scope (full parity)

Everything the web client does as of commit `711112a`, grouped the way the app presents it:

| Area | Web section | App location |
|---|---|---|
| Auth: email/password, register, verify email, forgot/reset, 2FA, Google, scheduled deletion & restore | `/login`, `/register`, … | Auth flow |
| Overview (net worth, month in/out, ready-to-assign, forecast, top categories, 6-month chart, envelopes pace, recent) | `/` | Tab: Overview |
| Accounts (9 types, groups, archive, Monobank status) | `/accounts` | Tab: Accounts |
| Transactions (feed, filters, search, drawer, splits, tags, payees, transfer pairs, planned occurrences) | `/transactions` | Tab: Transactions |
| Recurring payments | drawer on `/transactions` | Transactions → Recurring |
| Budget envelopes (assign, move, cover, copy previous) | `/budget` | Tab: Budget |
| Reports (spending, income/expense, net worth, payees) + Analytics (plan, cash flow, compare, trends, savings) | `/reports` | More → Reports |
| Goals, FIRE, FYM, compound calculator | `/goals` | More → Goals |
| Investments (overview, positions, dividends, operations, instrument page) | `/investments` | More → Investments |
| Taxes (FOP, investments, profile, rates, CSV/XML) | `/taxes` | More → Taxes |
| Imports (Monobank connection, PrivatBank PDF, Zen Money, Freedom Finance, preview) | `/imports` | More → Imports |
| Settings (profile, security, notifications, data, categories, rules, payees, tags) | `/settings` | More → Settings |
| Legal pages (privacy, terms) | `/privacy`, `/terms` | Settings → About, and auth footer |

Plus mobile-only:

- App lock with PIN and biometrics.
- Push notifications for every existing notification kind.
- Home-screen widgets: balance, month overview, quick add.
- App shortcuts and a Quick Settings tile for quick entry.
- Android App Links for email links (verify, reset password, restore account, unsubscribe).

## 4. Out of scope

- iOS, tablets/foldables layouts, landscape, Wear OS, Android Auto.
- Offline writes / sync queue. Writes need network; the UI says so.
- Receipt OCR, SMS parsing, camera features.
- Landing/marketing page (web only). The app opens on sign-in.
- Admin functions.
- Sign in with Apple, CAPTCHA (also not on backend).
- In-app purchases, subscriptions, ads.
- Interactive Brokers / other broker imports not yet on the backend.
- Any backend feature not present in `api/openapi.json` at 1.0.1 except those listed in [07](07-backend-changes.md).

## 5. Milestones

Each milestone is a releasable app: it builds in CI, passes the Definition of Done, and can
be shipped to a Play track. Order is fixed; items inside a milestone may be reordered.

### M0 — Project skeleton (prerequisite, no user-visible features)

- Repository `WebWalletAndroid` with Gradle version catalog, convention plugins, module layout
  from [02](02-architecture.md), CI running lint/detekt/unit tests/assemble.
- Design system module with theme, typography, icons, and a debug "gallery" screen.
- OpenAPI client generation wired to a copy of `api/openapi.json`, with a drift check.
- Firebase project, `google-services.json` per flavor, Crashlytics and Analytics initialised
  (disabled until consent, see [08](08-release-and-ops.md)).
- Acceptance: `./gradlew build` green in CI; app installs and shows the gallery in debug.

### M1 — Sign in, accounts, transactions

Backend prerequisites: B1 (mobile token transport), B2 (trusted device in body), B6 (client
headers), B8 (app config endpoint). Recommended: B5 (Idempotency-Key).

- Auth: email/password sign-in, 2FA step with trust device, register + check-mail + resend,
  forgot/reset password via App Link, verify email via App Link, pending-deletion restore,
  sign-out. Google sign-in is **M2** (needs B3).
- Session: token storage, silent refresh, 401 replay, forced sign-out.
- App lock: PIN + biometrics, offer after first sign-in, lock on background.
- Tabs: Overview (figures + recent transactions only; charts come in M2), Accounts (full),
  Transactions (feed, filters, search, create/edit/delete, splits, tags, payees, transfer
  pairs merge/dismiss/split). Recurring and planned occurrences are **M2**.
- Settings: Profile, Security (password change, 2FA setup/disable/recovery codes), App lock,
  About/legal, Sign out. The rest of Settings is M2.
- Read cache for accounts, categories, tags, payees, current user, and the transaction feed.
- Acceptance: a user can sign in, see balances, add/edit/delete a transaction, filter the
  feed, and manage 2FA, all with Ukrainian and English UI, light and dark. Crash-free sessions
  ≥ 99.5 % on internal track over one week.

### M2 — Budget, goals, reports, recurring, Google

Backend prerequisites: B3 (Google native sign-in).

- Budget tab: month navigation, ready-to-assign, envelopes table, assign, move money, cover
  overspending, copy previous month.
- Overview completed: forecast chart with minimum balance, top categories, 6-month chart,
  envelope pace.
- Transactions: Recurring list and editor, planned occurrences group with confirm/link/skip.
- Goals: list, create/edit, contributions, FIRE, FYM, compound calculator.
- Reports: spending, income/expense, net worth, payees; Analytics: plan vs actual, cash flow,
  compare, trends, savings.
- Settings: Categories (tree, reorder, icons, colours, needs/wants), Rules, Payees, Tags,
  Notifications (Telegram + email digest settings), Data (export ZIP, start over, delete
  account), Google link/unlink.
- Google sign-in and link via Credential Manager.
- Acceptance: parity with web for all sections except Investments, Taxes, Imports.

### M3 — Investments

- Investments hub: account chips, display currency switch, Overview (value, changes, chart vs
  benchmark, TWR/XIRR, allocations), Positions, Dividends (calendar + list), Operations.
- Operation editor with instrument search, manual instrument creation, FIFO lot preview.
- Instrument page: price chart, figures, lots/operations/dividends, buy/sell/edit/delete.
- Acceptance: parity with web `/investments` and `/investments/instruments/:id`.

### M4 — Taxes and imports

- Taxes: year selector, taxpayer profile, rates, FOP tab (quarters, payments, income list with
  include/exclude, CSV, XML declaration download/share), Investments tab (dividends with foreign
  tax document toggle, FIFO sales, declaration summary, CSV, XML).
- Imports: Monobank connect/disconnect/schedule/links; PrivatBank PDF, Zen Money token/CSV,
  Freedom XLSX via the system file picker; preview with filters and per-row edits; result.
- Acceptance: parity with web `/taxes` and `/imports`. Files are picked with the Storage
  Access Framework; downloads are shared through the share sheet or saved via SAF.

### M5 — Push, widgets, shortcuts, polish

Backend prerequisites: B4 (device registration + FCM delivery), B7 (assetlinks already in M1).

- FCM registration, notification channels per kind, deep links from notifications into the
  relevant screen, per-kind push switches in Settings → Notifications.
- Widgets: Balance, Month overview, Quick add.
- App shortcuts (static + dynamic) and Quick Settings tile for quick entry.
- Baseline profile, startup time budget, release hardening, Play open release.
- Acceptance: notifications arrive within 1 minute of the server sending them; widgets update
  within 30 minutes and on app foreground; open release approved by Play.

## 6. Cross-cutting requirements

- **Languages:** uk and en, following the `locale` of the user profile once signed in, the
  system locale before that. All strings in `strings.xml`, both languages complete before a
  milestone ships. Reuse the web wording from the `locales/*.json` files when it fits.
- **Themes:** light, dark, follow-system. Default follow-system.
- **Formatting:** numbers, dates and currency follow the UI language (`uk-UA` / `en-GB`), not
  the device locale, to match the web. Dates are computed in `Europe/Kyiv` ("today" is Kyiv
  today), as the web does.
- **Accessibility:** every interactive element has a content description; touch targets
  ≥ 48 dp; supports font scale up to 200 % without clipping amounts; TalkBack reads amounts as
  "minus 1 234,50 hryvnias", not symbol by symbol.
- **Performance budgets:** cold start to first interactive frame ≤ 1.5 s on a Pixel 6a class
  device; transaction feed scroll without dropped frames at 60 Hz; every network call shows
  feedback within 100 ms (skeleton or progress).
- **Privacy:** no financial data in logs, analytics events or crash reports. Analytics events
  carry screen names and action names only, never amounts, names or account identifiers.
- **Security:** see [03](03-auth-and-security.md). FLAG_SECURE is on when the app lock is
  enabled; cleartext traffic is disallowed; certificate pinning is not used (Let's Encrypt
  rotation), but the network security config trusts only system CAs.
