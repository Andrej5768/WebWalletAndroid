# 06 — Notifications, Widgets, Shortcuts (M5)

## 1. Push notifications (FCM)

Backend: B4. Kinds (backend `NotificationKind`): RECURRING, BUDGETS, TAXES, BANKS, FORECAST,
GOALS, WEEKLY, MONTHLY (EMAIL_WEEKLY / EMAIL_MONTHLY are email-only and never pushed).

### 1.1 Registration

- `POST_NOTIFICATIONS` runtime permission (API 33+) requested in context: after the first
  transaction is saved, or when the user opens Settings → Notifications, with a short
  rationale sheet first. Never on app start.
- On token available (`FirebaseMessagingService.onNewToken`) and on every app start when
  signed in: enqueue `UploadFcmTokenWorker` (`PUT /devices {token, platform:ANDROID,
  appVersion, locale}`), network-constrained, exponential backoff. Re-upload when the UI
  language changes (`locale` field).
- On sign-out: `DELETE /devices/{token}` best effort, then `FirebaseMessaging.deleteToken()`.

### 1.2 Channels

| Channel id | Name (en / uk) | Importance | Kinds |
|---|---|---|---|
| `payments` | Planned payments / Заплановані платежі | DEFAULT | RECURRING |
| `budget` | Budget / Бюджет | DEFAULT | BUDGETS, FORECAST |
| `taxes` | Taxes / Податки | HIGH | TAXES |
| `banks` | Bank connections / Банки | HIGH | BANKS |
| `goals` | Goals / Цілі | LOW | GOALS |
| `digests` | Weekly and monthly summary / Підсумки | LOW | WEEKLY, MONTHLY |
| `sync` | Background sync / Фонова синхронізація | MIN | internal, silent |

Channel group "WebWallet". Users can mute kinds in Android settings; the in-app per-kind
switches map to the backend flags, and a note explains that system channel settings also
apply.

### 1.3 Payload and handling

```json
{"data":{"title":"…","body":"…","kind":"BUDGETS","route":"/budget?month=2026-10","refId":"42","sentAt":"2026-10-21T07:00:00Z"}}
```

- Messages are **data-only** (B4), so `onMessageReceived` always runs, in foreground and
  background, and the app builds the notification itself with `NotificationCompat`.
- Tap → `MainActivity` with `route` → `DeepLinkParser` → destination, after unlock if the app
  lock is on.
- When the app lock is enabled and "Show details in notifications" (Settings → Notifications,
  default on) is off, the body is replaced by a generic "You have a new notification" and the
  title by the channel name.
- Group by channel; summary notification when > 3.
- `WEEKLY`/`MONTHLY` include a `BigTextStyle` body (the server text is long); no images.
- Analytics: `notification_received{kind}`, `notification_opened{kind}` (no content).

### 1.4 Settings → Notifications (M2 base, M5 push)

Mirrors the web tab: Telegram card (link via one-time `t.me` URL opened in Telegram app, poll
`GET /telegram` every 3 s for 2 min while waiting, quick-entry account picker, disconnect),
reminder hour picker, per-kind switches, email switches for weekly/monthly. M5 adds the master
"Push notifications" switch (`push` field, B4) and the device permission status row with a
link to system settings when denied.

## 2. Widgets (Jetpack Glance)

All widgets read from Room/DataStore only; a `WidgetRefreshWorker` (periodic 30 min, network
constrained, plus one-shot on app foreground and after any transaction write) refreshes the
underlying aggregates and calls `updateAll()`. Widgets respect the app lock rule from
[03 §6.2](03-auth-and-security.md): when locked and "Show balances in widgets" is off, show
"••••" instead of amounts; tapping opens the app (lock screen first).

| Widget | Sizes | Content | Taps |
|---|---|---|---|
| **Balance** | 2×1, 2×2, 4×1 | Balance mode label (Net worth / Available / Free), amount in base currency, "updated 14:02"; 2×2 adds up to 3 everyday accounts | Body → Accounts tab; account row → that account's transactions |
| **Month** | 4×2 | Income / expenses this month, ready-to-assign, envelope pace bar (spent vs. month progress), top 3 categories | Body → Overview; categories → Transactions filtered by category |
| **Quick add** | 1×1, 2×1, 4×1 | Buttons: "+ Expense", "+ Income", "+ Transfer" (1×1 shows only "+") | Opens transaction editor with the type preset |

Widget balance mode follows the app's `balanceMode` setting (Overview). Signed-out state
shows "Sign in to WebWallet". Error state shows the last data with a stale marker; no
spinners. Glance theme uses the design-system colours and supports light/dark.

## 3. App shortcuts

- Static (`shortcuts.xml`): "Add expense" (`webwallet://quick-add?type=EXPENSE`), "Add
  income", "Add transfer", "Budget".
- Dynamic (`ShortcutManagerCompat`, max 4 total with static, so publish 2): the 2 most used
  account+category pairs for expenses from the last 30 days, label "☕ Coffee · Mono Black",
  preset in the editor. Recomputed after each transaction write. Cleared on sign-out.
- Pinned shortcuts: allow pinning the dynamic ones via long-press (default system behaviour).

## 4. Quick Settings tile

`TileService` "Add expense": tapping unlocks the device if needed
(`unlockAndRun`) and starts `MainActivity` with `webwallet://quick-add?type=EXPENSE`. Tile
state is always `ACTIVE` when signed in, `UNAVAILABLE` when signed out. Icon: Lucide `Plus`
in a wallet.

## 5. Quick-add editor behaviour

`webwallet://quick-add` opens the transaction editor as the only screen on the back stack
(after unlock). Saving returns to the Transactions tab with the new row highlighted for 2 s;
back/cancel exits to the launcher if the app was not already open (`isTaskRoot`).
