# 05 — Design System

Material 3 components and patterns, skinned with the web brand. Source tokens:
`WebWalletClient/src/theme/theme.ts`, `src/theme/global.css`, `src/lib/palette.ts`,
`src/lib/accounts.ts`, `src/lib/categoryIcons.ts`. No Dynamic Color. Everything in this file
lives in `core:designsystem` and is shown in the debug Gallery screen.

## 1. Colour

### 1.1 Material colour schemes

| Role | Light | Dark | Source |
|---|---|---|---|
| primary | `#0D52A8` | `#7AAEF2` | cobalt.6 / accent-text dark |
| onPrimary | `#FFFFFF` | `#072D57` | |
| primaryContainer | `#EAF1FB` | `#1E3A5F` | cobalt.0 / derived |
| onPrimaryContainer | `#072D57` | `#C5DBF7` | |
| secondary | `#52525B` | `#ABABAB` | muted |
| secondaryContainer | `#F4F4F5` | `#3C3F41` | subtle |
| onSecondaryContainer | `#18181B` | `#E6E6E6` | |
| tertiary | `#15803D` | `#6CC48A` | income (used for positive accents) |
| error | `#C62828` | `#F0716B` | expense |
| errorContainer | `#FDECEC` | `#4A2A28` | derived |
| background | `#FAFAFA` | `#2B2B2B` | bg |
| onBackground | `#18181B` | `#E6E6E6` | text |
| surface | `#FFFFFF` | `#313335` | surface |
| onSurface | `#18181B` | `#E6E6E6` | |
| surfaceVariant | `#F4F4F5` | `#3C3F41` | subtle |
| onSurfaceVariant | `#52525B` | `#ABABAB` | muted |
| surfaceContainerLowest / Low / (default) / High / Highest | `#FFFFFF` / `#FAFAFA` / `#F4F4F5` / `#EFEFF1` / `#E9E9EC` | `#2B2B2B` / `#2E2F31` / `#313335` / `#3C3F41` / `#45484A` | |
| outline | `#D4D4D8` | `#5A5D60` | border-strong |
| outlineVariant | `#E4E4E7` | `#45484A` | border |
| inverseSurface | `#2B2B2B` | `#FAFAFA` | |
| scrim | `#000000` | `#000000` | |

Dark theme is the web's Darcula-like palette, so `background` is `#2B2B2B`, not M3's near-black.
Elevation tint is disabled (`surfaceTint = surface`) to keep flat surfaces like the web.

### 1.2 Semantic extension colours (`LocalWwColors`)

| Token | Light | Dark | Use |
|---|---|---|---|
| income | `#15803D` | `#6CC48A` | positive amounts, income bars |
| expense | `#C62828` | `#F0716B` | negative amounts, expense bars |
| transfer | `#52525B` | `#ABABAB` | transfer amounts (neutral) |
| warnBg / warnBorder / warnText | `#FFF9EC` / `#EEA94A` / `#7A4E0E` | `#3D3324` / `#EEA94A` / `#F2C27D` | warning cards (overspent, Monobank difference, forecast dip) |
| faint | `#71717A` | `#959595` | tertiary text |
| accentText | `#0D52A8` | `#7AAEF2` | links |
| chartGrid | `#E4E4E7` | `#45484A` | |

### 1.3 Palette (categories, goals, tags) — 12 colours, same names as web

| Name | Light | Dark | Name | Light | Dark |
|---|---|---|---|---|---|
| blue | `#2F6FC9` | `#7AAEF2` | amber | `#D97706` | `#FBBF24` |
| green | `#16A34A` | `#6CC48A` | slate | `#64748B` | `#94A3B8` |
| orange | `#EA580C` | `#FB923C` | red | `#DC2626` | `#F0716B` |
| violet | `#7C3AED` | `#A78BFA` | indigo | `#4F46E5` | `#818CF8` |
| teal | `#0D9488` | `#2DD4BF` | cyan | `#0891B2` | `#22D3EE` |
| pink | `#DB2777` | `#F472B6` | lime | `#65A30D` | `#A3E635` |

Container variant for icon tiles: colour at 14 % alpha (light) / 22 % (dark). A category
without a colour gets a stable automatic colour by `id % 12`, same as the web.

### 1.4 Account type colours

CARD blue, CREDIT_CARD red, CASH green, SAVINGS pink, DEPOSIT violet, LOAN orange, DEBT amber,
INVESTMENT teal, FOP indigo.

## 2. Typography

Fonts bundled as `font` resources (OFL): **Fixel Text** 400/500/600 and **Fixel Display** 600
(copy from `WebWalletClient/src/assets/fonts/fixel/`), **Inter** variable (amounts, with
`fontFeatureSettings = "tnum"` for tabular digits), **IBM Plex Mono** 400/500 (codes, keys).

| M3 style | Font | Size/line | Use |
|---|---|---|---|
| displaySmall | Fixel Display 600 | 32/40 | Net worth hero figure (Inter for the number itself) |
| headlineSmall | Fixel Display 600 | 24/32 | Screen titles in large top bars |
| titleLarge | Fixel Text 600 | 20/28 | Card titles |
| titleMedium | Fixel Text 600 | 16/24 | List section headers, sheet titles |
| titleSmall | Fixel Text 500 | 14/20 | Row titles |
| bodyLarge | Fixel Text 400 | 16/24 | Form fields |
| bodyMedium | Fixel Text 400 | 14/20 | Default text |
| bodySmall | Fixel Text 400 | 12/16 | Secondary text, captions |
| labelLarge | Fixel Text 500 | 14/20 | Buttons, tabs |
| labelMedium | Fixel Text 500 | 12/16 | Chips, badges |
| labelSmall | Fixel Text 500 | 11/16 | Overlines |
| `WwType.amountL / amountM / amountS` | Inter 600/500/500 tnum | 28/34, 16/22, 14/20 | Amounts |
| `WwType.mono` | IBM Plex Mono 400 | 14/20 | Recovery codes, 2FA key, TIN |

Font scaling: all sizes in `sp`; amounts clip-safe via `autoSize` down to 70 % before
truncating with ellipsis.

## 3. Shape, spacing, elevation

- Shapes: extraSmall 4, small 6, medium 8, large 10, extraLarge 12 (web radii). Cards use
  `medium`; sheets `extraLarge` top corners; FAB `large`; chips `small`.
- Spacing scale: 4, 8, 12, 16, 20, 24, 32. Screen horizontal padding 16 dp. List rows 56 dp
  (single line) / 72 dp (two lines).
- Elevation: cards 0 dp with a 1 dp `outlineVariant` border (flat like the web); bottom bar
  and top bar use `surfaceContainer`; dialogs/sheets 3 dp.

## 4. Icons

- Lucide via `com.composables:icons-lucide`. Icon size 20 dp in rows, 24 dp in bars.
- Logo: `Wallet` Lucide glyph in a cobalt `#0D52A8` rounded tile (web `Logo.tsx`). Launcher:
  adaptive icon from `docs/logo/webwallet-logo-square-512.png` as foreground on a cobalt
  background, plus a monochrome layer (white wallet stroke). Splash uses the same icon.
- **Category icons:** every key of `CATEGORY_ICONS` in `src/lib/categoryIcons.ts` (~55 Lucide
  names such as `shopping-cart`, `utensils`, `coffee`) must be mapped to the same Lucide glyph
  in `CategoryIcons.kt` (`Map<String, ImageVector>`). Unknown names fall back to `Tag`. Goal
  icons use the same map. A unit test asserts every web key resolves; the key list is copied
  verbatim into the test so drift is caught.
- Account type icons: CARD `CreditCard`, CREDIT_CARD `CreditCard` (red), CASH `Banknote`,
  SAVINGS `PiggyBank`, DEPOSIT `Landmark`, LOAN `HandCoins`, DEBT `Handshake`, INVESTMENT
  `TrendingUp`, FOP `Briefcase`.
- Tabs: Overview `LayoutDashboard`, Transactions `ArrowLeftRight`, Budget `Wallet`, Accounts
  `Landmark`, More `Menu`.

## 5. Components (`core:ui`)

| Component | Behaviour |
|---|---|
| `Amount(money, style, signed, colorBySign, compact)` | Formats via `MoneyFormatter`; sign colouring income/expense/transfer; `compact` → `1,2 млн`/`1.2M`; `contentDescription` spells the amount and currency |
| `MoneyField(value, currency, onChange, allowNegative, label, error)` | Numeric keyboard with `,`/`.`; expression evaluation on blur; currency suffix; 2 decimals; selects all on focus |
| `CategoryIcon(category, size)` | Tile with palette container colour and Lucide glyph; uncategorised = dashed outline `HelpCircle` |
| `AccountChip(account)` | Type icon + name + optional balance |
| `PeriodPicker` | Bottom sheet with presets (month, previous month, 3 months, year, 12 months, all, custom) + `DateRangePicker` |
| `MonthSwitcher(month, onPrev, onNext, onPick)` | `‹ Жовтень 2026 ›`, tap the label for a month grid |
| `FilterBar(chips)` | Horizontal chips with counts; active state filled; "Clear" chip when any active |
| `SegmentedTabs` | M3 `PrimaryTabRow` or `SingleChoiceSegmentedButtonRow` for 2–4 options |
| `Figure(label, value, delta?, onClick?)` | Overview/report stat tile |
| `ProgressBar(value, max, marker?)` | Envelope/goal progress with an optional "today" marker |
| `WarnCard(text, action?)` | Warning surface using warn tokens |
| `StaleBanner(fetchedAt, offline)` | "Showing data from 14:02 · Offline" strip with Retry |
| `EmptyState(icon, title, body, action?)` | Centered, illustration-free |
| `ErrorState(error, onRetry)` | Maps `ApiError` to text |
| `Skeleton*` | Shimmer placeholders for rows, figures, charts |
| `ConfirmDialog(title, body, confirmText, destructive)` | Destructive uses `error` colour; typed confirmation variant for "DELETE"/email |
| `PinKeypad` | 3×4 keypad, haptics, dot indicators |
| `CodeField(length=6)` | 6 boxes, paste, auto-submit |
| `DonutChart(slices)` / `RingProgress` | Canvas-drawn |
| `LineChart` / `ColumnChart` | Vico wrappers with theme colours, Kyiv-date axis formatter, tap-to-inspect marker |
| `SearchField` | Top-bar search with clear and debounce 300 ms |
| `BottomSheetMenu(items)` | Replaces web row menus |

Charts: no animations longer than 300 ms; colour-blind safe by always pairing colour with a
label; income/expense never conveyed by colour alone (icons/signs too).

## 6. Motion and feedback

- Navigation transitions: M3 shared-axis X for push, fade-through for tab switches, 250 ms.
- Haptics: `LongPress` on destructive confirms, `Confirm` on save, `Reject` on wrong PIN.
- Snackbars at bottom above the bar; errors are snackbars with Retry unless they belong to a
  form field. Success toasts are short (2 s). Mirror the web's "red toast on mutation error".
- Pull-to-refresh on every list/overview screen (`PullToRefreshBox`).

## 7. Dark mode and theming

Theme setting: System / Light / Dark in Settings → Profile (same as web) stored in DataStore.
System bars are transparent with edge-to-edge; icons adapt to theme. Widgets follow the system
theme via Glance `GlanceTheme` with the same colour scheme.

## 8. Localisation and text rules

- Ukrainian first. Translate meaning, not words; reuse web strings from `*/locales/uk.json`
  and `en.json` where the UI element matches.
- Dates and numbers follow the UI language, not the system locale.
- Avoid abbreviations in Ukrainian where the web spells them out; keep "FOP"/"ФОП", "ESV"/"ЄСВ".
- Plurals via `<plurals>` for uk (one/few/many/other) and en.
- Text never truncates amounts; truncate names with ellipsis at the end.
