# 04 — Screens

Every screen lists: nav key, milestone, endpoints, layout, actions, states. "States" always
implies the standard set from [02 §3](02-architecture.md): loading skeleton, content, empty,
error (with offline variant), stale banner. Only screen-specific states are called out.
Web references point at `WebWalletClient/src/...` for wording and edge cases.

Common rules:

- Top app bar: `TopAppBar` with title; large variant only on tab roots. Back arrow on pushed
  screens; close (X) on full-screen editors with a "Save" text button on the right.
- Forms: unsaved-changes guard on close ("Discard changes?").
- Lists: pull-to-refresh; infinite scroll where the API is paged.
- Amount colouring: expense red, income green, transfer neutral; always with sign.
- Destructive actions: confirm dialog; irreversible ones (delete account, start over) require
  typed confirmation, like the web.

---

## 1. Auth (M1; Google M2)

Keys: `SignIn`, `TwoFactor(challenge)`, `PendingDeletion(date, restoreToken)`, `Register`,
`CheckMail(email, purpose)`, `ForgotPassword`, `ResetPassword(token)`, `VerifyEmail(token?)`,
`RestoreAccount(token)`, `Unsubscribe(user, token)`, `LockOffer`, `Consent`.

Behaviour is specified in [03](03-auth-and-security.md). Layout: centred logo, title, card
with the form, language (УКР/ENG) and theme toggles in the top-right as small icon buttons,
footer links Privacy · Terms (Custom Tabs). `Consent` is a one-time screen after first sign-in
on a device: "Help improve WebWallet" with Crashlytics/Analytics explanation, buttons Allow /
Not now (see [08 §6](08-release-and-ops.md)); shown before `LockOffer`.

Web refs: `src/auth/pages/*`.

## 2. Lock (M1)

Keys: `Lock`, `PinSetup(mode: Create|Change)`. See [03 §6](03-auth-and-security.md).

## 3. Main scaffold (M1)

Bottom `NavigationBar`: Overview · Transactions · Budget · Accounts · More. Centre FAB "+"
(`TransactionEditor(type=EXPENSE)`) on Overview, Transactions, Accounts; hidden on Budget and
More and while a sheet/editor is open. Badge on More when an update banner (B8) or Monobank
error exists.

---

## 4. Overview (M1 figures + recent; M2 charts)

Key: `Overview`. Web ref: `src/dashboard/DashboardPage.tsx`, `ForecastCard.tsx`.

Endpoints: `GET /accounts/summary`, `GET /accounts`, `GET /transactions?from&to&size=6`
(current month, recent), `GET /reports/spending?from&to` (month, top 6), `GET /reports/income-expense`
(last 6 months), `GET /budget/{month}` (envelopes pace), `GET /forecast?to`, `GET /banks/monobank`
(difference alert), `GET /recurring/occurrences?from&to` (overdue count).

Layout (vertical scroll, cards):

1. **Hero**: balance mode selector (`Net worth` / `Available (with credit)` / `Free outside
   envelopes`) as a tappable label with chevron → menu; amount `amountL`; `unconvertedCurrencies`
   note when non-empty. Persisted `balanceMode` (DataStore, shared with widgets).
2. **This month**: Income, Expenses, Savings rate (3 `Figure`s); Ready to assign figure → Budget tab.
3. **Alerts** (only when present): Monobank balance difference (→ Accounts), overdue planned
   payments (→ Transactions planned group), update available (B8).
4. **Money to the end of the month** (M2): `LineChart` of forecast balance with actual/forecast
   split, planned events as markers, minimum-balance line (long-press the chart or the
   "Minimum" chip to set; stored in DataStore `forecastMinimum`), lowest point callout, dip
   warning `WarnCard` when below minimum/zero.
5. **Top categories** (M2): 6 rows, `CategoryIcon`, amount, share bar → Transactions filtered.
6. **6 months** (M2): `ColumnChart` income vs expenses, tap month → Reports income/expense.
7. **Envelopes** (M2): top 4 envelopes by spent with `ProgressBar` and today marker → Budget.
8. **Recent transactions**: 6 rows (same row component as feed) → editor; "All" → Transactions.

Empty state (new user, no accounts): illustration-free card "Add your first account" →
`AccountEditor`, and "Connect Monobank" → Imports.

## 5. Accounts (M1)

### 5.1 Accounts list

Key: `Accounts`. Web ref: `src/accounts/AccountsPage.tsx`, `groups.ts`.
Endpoints: `GET /accounts`, `GET /accounts/summary`, `GET /banks/monobank`.

- Summary strip: Available · Loans & credit cards · Debts owed to you (3 figures).
- Sections by group, collapsible, with group subtotal in base currency: Cards (CARD), FOP,
  Cash, Credit (CREDIT_CARD), Savings (SAVINGS, DEPOSIT), Debts (LOAN, DEBT), Investments,
  Archive (collapsed by default; toggle "Show archived" persisted).
- Row: type icon tile, name, status line (Synced · Manual · "Differs from bank by X"), balance
  in account currency + base currency when different; credit cards show available credit.
- Tap → `AccountTransactions(accountId)` (Transactions feed prefiltered). Long-press or
  trailing `⋮` → `BottomSheetMenu`: Edit, New transaction, Positions (INVESTMENT only →
  Investments hub with chip), Archive/Unarchive.
- Top bar action: `+` → `AccountEditor()`. Reorder: not in M1 (web has `sortOrder`; add
  drag-handle reorder mode in M2 via `PUT /accounts/{id}` with `sortOrder`).

### 5.2 Account editor

Key: `AccountEditor(id?)`. Endpoints: `POST /accounts`, `PUT /accounts/{id}`, `DELETE /accounts/{id}`.

Fields: Name*, Type (9 types as `RadioTiles` grid with icons; editable after creation, as on
the web), Currency (searchable sheet, UAH + ~20 common first, full ISO list below; **locked
after creation**, with the web's red "currency is locked" hint), Opening balance (`MoneyField`), Credit limit
(CREDIT_CARD, LOAN), Interest rate % (SAVINGS, DEPOSIT, LOAN), Include in budget (switch,
default per type), Archived (edit only). Delete in overflow; `RESOURCE_IN_USE` → "Account has
transactions; archive it instead".

## 6. Transactions (M1; recurring/planned M2)

### 6.1 Feed

Key: `Transactions(filter: FeedFilter = default)`. `AccountTransactions(accountId)` is the same
screen with `accountId` preset and the account name as title.
Web ref: `src/transactions/*`.
Endpoints: `GET /transactions` (paged 50), `GET /transactions/transfer-pairs?from&to`,
`GET /recurring/occurrences?from&to` (M2), `PUT /transactions/{id}` (quick categorise),
`POST /transactions/transfer-pairs/{merge,dismiss}`.

`FeedFilter { period: PeriodPreset|Custom, accountId?, categoryId?, payeeId?, tagId?, type?, q? }`
is saved in `SavedStateHandle` and the last used one in DataStore.

Layout:

- Top bar: title "Transactions", search icon (expands `SearchField`, debounced `q`), filter
  icon with active-count badge → `FilterSheet`, overflow → Recurring (M2), Transfer pairs.
- Period chip row: Month · Prev month · 3 months · Year · 12 months · All · Custom (`PeriodPicker`).
- **Planned** group (M2) pinned above today when occurrences exist in range: rows with dashed
  left border, "Due 24 Oct" label, overdue in warn colour. Swipe right → Confirm (saves
  transaction via `POST /recurring/{id}/occurrences/{date}/confirm`), swipe left → Skip; tap →
  sheet: Save as transaction (opens editor prefilled, on save calls confirm with amount),
  Link to existing (`LinkSheet` lists `GET …/candidates`), Skip, Edit series.
- Day groups: sticky header "Today / Yesterday / Fri, 17 Oct" with day net total. Rows:
  `CategoryIcon`, payee or category name, secondary line (account · category · tags · comment
  excerpt), amount; transfers show `A → B` with both amounts when currencies differ; imported
  rows show a small bank glyph; `hold` shows a clock glyph.
- **Uncategorised quick action**: rows without category show a dashed icon; tap the icon →
  category picker sheet → optimistic `PUT`.
- **Transfer-pair hints**: when `transfer-pairs` returns a pair in range, the expense row shows
  a hint strip "Looks like a transfer to {account} · Merge · Not a transfer".
- Infinite scroll by `page`; end-of-list footer with total count.
- Tap row → `TransactionEditor(id)`. Swipe left on a row → Delete (confirm snackbar with Undo
  is **not** possible since the API has no restore; use a confirm dialog instead).

Empty: "No transactions for this period" + "Add transaction". Search empty: "Nothing found
for “…”".

### 6.2 Filter sheet

Pickers: Account (sheet), Category (tree sheet with search; parent selects subtree), Payee
(searchable), Tag, Type (Expense/Income/Transfer segmented). Buttons Reset · Apply.

### 6.3 Transaction editor

Key: `TransactionEditor(id?, type?, accountId?, categoryId?, payeeId?, occurrence?)`.
Web ref: `src/transactions/TransactionDrawer.tsx`, `SplitEditor.tsx`.
Endpoints: `GET /transactions/{id}`, `POST /transactions`, `PUT /transactions/{id}`,
`DELETE /transactions/{id}`, `POST /transactions/{id}/split-transfer`, `GET /categories`,
`GET /accounts`, `GET /payees`, `GET /tags`, `POST /tags`.

Full-screen. Top: segmented Expense · Income · Transfer. Body:

- **Amount** (`MoneyField`, large, auto-focused on create) with account currency suffix.
- **Account** (chip row of everyday accounts + "More" sheet). For Transfer: **From** and
  **To**; when currencies differ show **Received amount** (`toAmount`) with the implied rate.
- **Date**: chips Today · Yesterday · pick (`DatePicker`), range 1990–2100.
- **Category** (not for Transfer): `CategoryIcon` grid of recent 8 + "All" → tree sheet; or
  **Split** toggle → `SplitEditor`: rows (amount, category, comment), remainder indicator,
  "Distribute remainder" button; sum must equal amount.
- **Payee**: autocomplete over `GET /payees`; free text creates via `payeeName`. Hidden for Transfer.
- **Tags**: chip input with autocomplete; new names auto-create (`POST /tags`).
- **Comment**: multiline.
- **Foreign amount** (collapsed "Paid in another currency?"): `originalAmount` + currency.
- **FOP income tax** switch (Income on a FOP account; web `TransactionDrawer` "fopIncome"):
  marks the transaction for taxes via `PUT /taxes/transactions/{id} {included}` after save.
- Imported transactions (`source != MANUAL`): amount, account, date read-only with a note
  "Imported from Monobank"; everything else editable.
- Transfer that was merged (has `toExternalId`/two bank ids): overflow "Split into two
  transactions" → `split-transfer` (confirm).
- Overflow: Delete (confirm). Save → close; snackbar "Saved" with "Add another" action when
  created from FAB.
- Validation inline from `errors[]`; amount > 0; `toAccountId != accountId`.

### 6.4 Recurring (M2)

Keys: `RecurringList`, `RecurringEditor(id?)`. Web ref: `src/recurring/RecurringDrawer.tsx`.
Endpoints: `GET/POST /recurring`, `GET/PUT/DELETE /recurring/{id}`.

List: header with monthly In / Out totals; rows: name, schedule text ("Monthly on the 5th",
"Every 2 weeks on Fri"), amount (or "Variable ≈ X"), next due date, mode badge (Remind /
Auto). Tap → editor. `+` → editor.

Editor fields: Name*, Type (Expense/Income/Transfer), Account (+ To account, To amount),
Amount with "Variable amount" switch, Category, Payee, Comment, Frequency (Daily/Weekly/
Monthly/Yearly) + Every N, Weekday (weekly), Day of month (monthly; "last day in shorter
months" note), Start date, End (Never / On date / After N occurrences), Mode (Remind and match
with bank · Create automatically), Match tolerance % (0–50, when Remind). Actions: Stop (sets
`endDate` today), Delete.

### 6.5 Transfer pairs (M1)

Key: `TransferPairs(period)`. Endpoint: `GET /transactions/transfer-pairs`. List of suggested
pairs with both rows and difference; Merge / Not a transfer per pair. Accessible from feed
overflow and from the hint strip.

## 7. Budget (M2)

Key: `Budget(month: YearMonth = current)`. Web ref: `src/budget/BudgetPage.tsx`, `MoveMoneyModal.tsx`.
Endpoints: `GET /budget/{month}`, `PUT /budget/{month}/categories/{id} {assigned}`,
`POST /budget/{month}/move`, `POST /budget/{month}/copy-previous`.

- `MonthSwitcher` in the top bar. Overflow: "Same as last month" (confirm; `copy-previous`),
  "Move money".
- **Ready to assign** card: amount, state colour (positive primary, zero neutral, negative
  error) with text per web; negative shows "Cover" shortcut to Move money with `to = overspent`.
- Overspent `WarnCard` listing overspent envelopes, "Cover" per envelope opens Move money with
  `toCategoryId` preset.
- Groups (parent categories) with group totals; rows: `CategoryIcon`, name, **Assigned**
  (tap → inline `MoneyField` in a small sheet with numeric pad; optimistic; also "+ spent",
  "= last month" quick buttons), Spent, Available (coloured), `ProgressBar` with today marker.
- Tap row name → Transactions filtered by category and month.
- Move money sheet: From envelope (or Ready to assign), To envelope, Amount with "max" chips.
- Month without assignments: `EmptyState` "Nothing assigned yet" + "Same as last month" button
  when a previous month exists.

## 8. More hub (M1)

Key: `More`. List of entries with icons and one-line descriptions: Reports, Goals,
Investments, Taxes, Imports, Settings; divider; Lock now (when lock enabled); About; Sign out.
Header shows avatar initials, name, email, `Verified` badge. Update banner (B8) at the top.

## 9. Reports (M2)

Key: `Reports(tab, period)`. Web ref: `src/reports/ReportsPage.tsx`, `src/reports/analytics/*`.
Tabs (scrollable `PrimaryTabRow`): Spending · Income & expenses · Net worth · Payees ·
Analytics. `PeriodPicker` chip in the top bar (default 12 months; API max 20 years).

| Tab | Endpoint | Content |
|---|---|---|
| Spending | `GET /reports/spending` | `DonutChart` of parent categories (tap slice → expand), list tree with amounts and share bars, "Other" bucket after top 8; tap leaf → Transactions filtered |
| Income & expenses | `GET /reports/income-expense` | `ColumnChart` income vs expenses per month, table: month, income, expenses, net, savings rate |
| Net worth | `GET /reports/net-worth` | `LineChart` assets/debts/net, figures: assets, debts, change over period |
| Payees | `GET /reports/payees?limit=50` | Ranked list with count, total, average; tap → Transactions filtered |
| Analytics | sub-tabs below | |

Analytics sub-tabs (`SegmentedTabs` scrollable):

| Sub-tab | Endpoint | Content |
|---|---|---|
| Plan vs actual | `GET /analytics/plan/{month}` + `MonthSwitcher` | Per envelope: planned, actual, forecast to month end; over/under colouring |
| Cash flow | `GET /reports/income-expense` | Monthly net bars with cumulative line |
| Compare | `GET /analytics/compare?fromA&toA&fromB&toB` | Mode: MoM · YoY · Custom; "same date" toggle; cumulative spending lines A vs B; biggest changes list |
| Trends | `GET /analytics/trends?from&to` (≤ 36 months) | Per-category lines (select up to 5 categories via chips), pin a month, anomalies list (> 1.7× average) |
| Savings | `GET /analytics/savings?from&to&target`, `GET /analytics/needs-wants` | Savings rate vs target (slider 5–50 %), cumulative savings, 50/30/20 needs/wants ring, "where savings went" by account type |

Charts are tap-to-inspect; a legend row under each chart. All amounts in base currency with
`unconvertedCurrencies` note.

## 10. Goals (M2)

Key: `Goals(tab)`. Web ref: `src/goals/*`. Tabs: Goals · FIRE · FYM · Calculator.

### 10.1 Goals tab

Endpoints: `GET /goals`, `POST /goals`, `GET/PUT/DELETE /goals/{id}`, `POST /goals/{id}/contributions`.
Header: Saved / Target / Monthly needed. Cards: icon tile (palette colour), name, `saved /
target` with `RingProgress`, status chip (ACHIEVED, NO_DATE, ON_TRACK, BEHIND, OVERDUE), pace
line ("X / month needed", "Y months left"). Tap → menu: Edit, Add money, Withdraw, Mark
achieved / Mark active, Delete. FAB `+`.

Goal editor `GoalEditor(id?)`: Name*, Target amount*, Currency, Target date (optional),
Linked account (optional; progress follows balance; disables manual saved), Already saved
(manual), Icon (56-icon grid), Colour (12). Contribution sheet: amount, +/− toggle.

### 10.2 FIRE, FYM, Calculator

Client-side calculators, inputs persisted in DataStore (cleared on sign-out) exactly like web
localStorage `ww-fire`, `ww-fym`, `ww-compound`. Port the formulas from `src/goals/*` 1:1
(unit tests with fixtures copied from the web tests). FIRE: Lean/Regular/Fat/Coast levels with
capital projection `LineChart`; FYM: months of runway with milestones; Calculator: compound
interest with capitalisation frequency, contribution growth, inflation (real) toggle, result
table by year.

## 11. Investments (M3)

Key: `Investments(tab, accountId?)`. Web ref: `src/investments/*`.
Top bar: account chips (All + each INVESTMENT account), display currency toggle ₴/$ (stored),
`+` → `OperationEditor`. Tabs: Overview · Positions · Dividends · Operations.

| Tab | Endpoints | Content |
|---|---|---|
| Overview | `GET /investments/portfolio?accountId&currency`, `GET /investments/history?accountId&from&to&currency&benchmark` | Figures: value, day change, invested, unrealised, realised (year), dividends 12 m; range chips 3m/6m/1y/all; `LineChart` value vs benchmark (chips S&P 500 / MSCI World / Nasdaq 100); returns TWR, TWR annualised, XIRR vs benchmark; allocation rings by type / currency / sector (segmented); "Next payments" list |
| Positions | `GET /investments/portfolio` positions | Group by account / by asset class (toggle); rows: symbol, name, qty, value, P/L %; tap → `Instrument(id)` |
| Dividends | `GET /investments/dividends?accountId&from&to&currency` | Month calendar strip with dots, list grouped by month with status chips RECEIVED/EXPECTED/DECLARED/FORECAST/SCHEDULED, yield & yield-on-cost figures, 12-month `ColumnChart` |
| Operations | `GET /investments/operations?accountId&types&page` | Filter chips by type; paged list; tap → `OperationEditor(id)` |

### 11.1 Operation editor

Key: `OperationEditor(id?, accountId?, instrumentId?, type?)`. Endpoints: `POST/PUT/DELETE
/investments/operations…`, `GET /investments/instruments/search?q`, `POST /investments/instruments/market`,
`POST /investments/instruments`, `GET /investments/positions?accountId&instrumentId` (FIFO preview).

Type segmented (BUY, SELL, DIVIDEND, COUPON, FEE, SPLIT, DEPOSIT, WITHDRAWAL, EXCHANGE) — show
as a horizontal scrollable chip row. Fields by type: Account*, Instrument (search sheet by
ticker/name/ISIN; results from provider; "Add by hand" → `InstrumentEditor`; `providerEnabled=false`
→ note "Market search is disabled"; 429 → "Search limit reached, try later"), Date*, Quantity
(8 dp), Price, Amount, Fee, Tax, Currency, Split from/to (SPLIT), To amount/currency
(EXCHANGE), Comment. SELL shows **FIFO lot preview** (lots consumed, realised P/L) fetched
on quantity change (debounced).

### 11.2 Instrument page

Key: `Instrument(id)`. Endpoints: `GET /investments/instruments/{id}`, `GET …/{id}/prices?from&to`,
`GET /investments/positions?accountId&instrumentId`, dividends filtered by instrument.
Header: symbol, name, type/exchange/currency chips. Price `LineChart` 1Y/3Y/all with average
price line. Figures: quantity, average price, value, unrealised, realised. Tabs: Lots (FIFO) ·
Operations · Dividends. Actions: Buy, Sell (editor prefilled), Edit (own instruments only:
`InstrumentEditor` — nominal, coupon rate, coupon frequency, maturity, valuation, manual
price + date), Delete (`RESOURCE_IN_USE` → explain).

## 12. Taxes (M4)

Key: `Taxes(year, tab)`. Web ref: `src/taxes/*`, `model.ts` (CSV generation to port).
Top bar: year selector (`2000–2100`, default current), overflow: Taxpayer details, Rates {year}.
Tabs: FOP · Investments.

**Taxpayer details** (`TaxProfile`): `GET/PUT /taxes/profile` — TIN (mono, 10 digits), full
name, tax office, address, region/district codes, KVED. Stored encrypted server-side; FLAG_SECURE
on this screen.

**Rates {year}** (`TaxSettings(year)`): `GET/PUT /taxes/settings/{year}` — per-rate fields with
defaults and "Restore defaults"; FOP income categories multi-select (`PUT /taxes/income-categories`).

**FOP tab**: `GET /taxes/fop/{year}`, `PUT /taxes/fop/{year}/quarters/{q} {paid}`,
`GET/PUT /taxes/transactions/{id} {included}`. Cards per quarter: income, single tax, military
levy, ESV, deadlines, "Mark paid" switch. Income-limit `ProgressBar`. Income list per quarter
with include/exclude switch per transaction. Actions: Export CSV (generate client-side,
share/save via SAF), Download declaration (`GET /taxes/declarations/fop/{year}/{q}` → XML or
ZIP → SAF `CreateDocument` with the server filename; year ≥ 2025 only).

**Investments tab**: `GET /taxes/investments/{year}`, `PUT /taxes/investments/dividends/{opId}
{document}`. Dividends list with "Foreign tax document" switch; sales by FIFO in UAH; carried
loss; declaration summary card. Actions: Export CSV, Download property declaration
(`GET /taxes/declarations/property/{year}`).

## 13. Imports (M4)

Key: `Imports`. Web ref: `src/imports/*`.

### 13.1 Monobank card

Endpoints: `GET/POST/DELETE /banks/monobank`, `PUT /banks/monobank/accounts/{linkId}`,
`PUT /banks/monobank/schedule`.
States: Not connected → "Connect" (`ConnectSheet`: token (paste, link to api.monobank.ua in
Custom Tabs), since date ≤ 3 years back, sync mode); Connected → client name, last sync, next
sync, `lastError` warn, webhook status; schedule editor (INTERVAL every 15 min within
window / DAILY at time / WEBHOOK); card & jar → account link table (each row: bank title,
currency, bank balance, linked account picker or "Not synced", difference badge); Change token;
Disconnect (confirm).

### 13.2 Sources

List: PrivatBank PDF, Zen Money (token or CSV), Freedom Finance (2 XLSX). Each opens a small
form: file(s) via `OpenDocument` (MIME `application/pdf`, `text/csv`, XLSX), optional target
account, then **Preview** (`POST /imports/{privatbank,zenmoney,zenmoney/file,freedom}/preview`,
upload progress; 20 MB limit checked client-side; `INVALID_IMPORT_FILE` → message).

### 13.3 Preview

Key: `ImportPreview(id, source)`. Endpoints: `GET /imports/previews/{id}?page&size`,
`POST /imports/previews/{id}/confirm`, `DELETE /imports/previews/{id}`; Freedom:
`POST /imports/freedom/previews/{id}/confirm`.
Header: counts (all / uncategorised / duplicates) as filter chips; bulk actions (set category,
add tag) on selection mode. Rows: include checkbox, date, description, amount, type segmented,
category picker, "remember as rule" toggle, tags, transfer pairing selector (when candidate
exists). Freedom preview: instrument mapping rows (ticker choice, status) and excluded list.
Previews expire in 1 h: show countdown and handle 404 → "Preview expired".
Confirm → progress → `ImportResult` screen (imported, skipped, duplicates, rules learned) →
"Open transactions".

## 14. Settings (M1 partial, M2 full, M5 push)

Key: `Settings`, sub-keys below. Web ref: `src/settings/*`. Settings root is a list of
sections, each a pushed screen.

| Screen | Milestone | Endpoints | Content |
|---|---|---|---|
| Profile | M1 | `GET/PUT /me` | First/last name, email (read-only + Verified badge), base currency (sheet), language (System/UK/EN — note: web has only uk/en; app adds System), theme (System/Light/Dark) |
| Security | M1 | `POST /me/password`, 2FA endpoints, Google (M2) | Change password (or "Set a password" when `hasPassword=false`), 2FA card ([03 §5](03-auth-and-security.md)), App lock entry, Google card (M2, link/unlink via `ConfirmIdentitySheet`) |
| App lock | M1 | — | Enable/disable, change PIN, biometrics, timeout, "Show balances in widgets when locked" |
| Notifications | M2 (+M5) | `GET/PUT /notifications/settings`, telegram endpoints, devices (M5) | See [06 §1.4](06-notifications-widgets-shortcuts.md) |
| Categories | M2 | `GET/POST/PUT/DELETE /categories`, `PUT /categories/order` | Expense/Income segmented; two-level tree; reorder mode with drag handles (`sortOrder`, `parentId`); editor: name, parent, icon grid, colour, needs/wants/inherit, archive; delete (`RESOURCE_IN_USE` → "Has transactions; archive instead") |
| Rules | M2 | `/categorization-rules…`, `/apply` | List: condition (payee / payee contains / MCC / bank category) → category, "learned" badge; editor; "Apply to uncategorised" → result count |
| Payees | M2 | `GET/PUT/DELETE /payees` | Searchable list; rename inline; delete |
| Tags | M2 | `/tags…` | List with colour dots; editor name + colour |
| Data | M2 | `GET /me/export`, `POST /me/reset`, `POST /me/delete` | Export ZIP (stream to SAF `CreateDocument("application/zip")`, progress); Start over (type DELETE, keep categories switch); Delete account (type email, `ConfirmIdentity`; shows 14-day notice; on success sign out) |
| Privacy | M1 | — | Crashlytics / Analytics switches (consent), link to Privacy policy |
| About | M1 | — | Version/build, backend URL (dev only), Licenses (`oss-licenses-plugin` or `aboutlibraries`), Terms, Privacy, Contact support (mailto support@andrew-dev.org), "Rate on Google Play" |

## 15. Files: picking, saving, sharing

- Pick: `ActivityResultContracts.OpenDocument` / `OpenMultipleDocuments` with MIME filters;
  read through `ContentResolver.openInputStream` into a temp file in `cacheDir/imports`,
  deleted after upload.
- Save: `CreateDocument(mime)` with the server-provided filename
  (`Content-Disposition`), fallback `webwallet-<kind>-<date>.<ext>`. Downloads stream to
  the `Uri` with progress; never use `DownloadManager` (needs no notification clutter) and
  never write to public storage directly.
- Share: `FileProvider` from `cacheDir/share` with `ACTION_SEND`; used for CSV, recovery
  codes, declarations.
- Large exports (ZIP) show an indeterminate progress sheet with Cancel.
