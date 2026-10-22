# 03 — Auth and Security

Backend reference: `AuthController`, `GoogleController`, `TokenService`, `SecurityConfig`.
Access token: HS256 JWT, 15 min, `sub=userId`, `ver=tokenVersion` (checked on every request, so
revocation is immediate). Refresh token: opaque, 30 days, rotated on every refresh, reuse of a
rotated token revokes the whole family. On the web the refresh token is an HttpOnly cookie; the
app uses the body transport added by backend change **B1** (see [07](07-backend-changes.md)).

## 1. Token storage

| Item | Where | Protection |
|---|---|---|
| Access token | Memory only (`SessionManager`) | Lost on process death; restored via refresh |
| Refresh token | Proto DataStore `session.pb`, field encrypted | AES-256-GCM with an Android Keystore key (`webwallet.session`), `setUserAuthenticationRequired(false)` so background work (FCM, widgets) can refresh without unlock; `setUnlockedDeviceRequired(true)`; StrongBox when available, fallback to TEE |
| Trusted-device token (2FA) | Same store, encrypted | 90-day server validity; sent in the login body (B2) |
| User id, email, name snapshot | Same store, plaintext | Needed to render the lock screen before unlock |
| PIN | Same store | PBKDF2-HMAC-SHA256, 310 000 iterations, 16-byte random salt, 32-byte output; compared in constant time |
| Biometric flag | Same store | Boolean + the Keystore alias of a biometric-bound key used only to prove enrolment did not change (see §6) |

Never write tokens to logs, Crashlytics keys, analytics, `Bundle`s, or `SavedStateHandle`.
`android:allowBackup="false"`; `dataExtractionRules` exclude DataStore and the Room DB.

## 2. Session lifecycle

```
App start → AuthState.Unknown
  refreshToken absent → SignedOut
  refreshToken present → POST /auth/refresh {refreshToken}
      200 → store new refresh + access → SignedIn(locked = lockEnabled)
      401 INVALID_REFRESH_TOKEN → wipe session → SignedOut (toast "Session expired, sign in again")
      network error → SignedIn(locked = lockEnabled) with no access token; first API call retries refresh
```

- `SessionManager.refresh()` is single-flight (`Mutex`); concurrent 401s wait for one result.
- Refresh is also triggered proactively when the access token has < 60 s left and the app is
  in the foreground.
- Forced sign-out (refresh fails, or any `401 UNAUTHORIZED` after a successful refresh,
  which means `tokenVersion` was bumped by a password change elsewhere) runs the full sign-out
  routine from [02 §5.4](02-architecture.md) and shows the sign-in screen with an explanation.
- Password change in the app returns a new `TokenResponse`; the app replaces both tokens and
  does **not** sign out.
- Multiple accounts on one device: not supported. Signing in with another account first signs
  out the current one (with a confirmation).

## 3. Sign-in flows

### 3.1 Email + password

`POST /auth/login {email, password, trustedDeviceToken?}` →

| Result | Handling |
|---|---|
| `accessToken != null` | Store tokens; go to lock setup offer (first sign-in on this device) or main |
| `twoFactorChallenge != null` | Push `TwoFactorStep(challenge)` |
| 401 `INVALID_CREDENTIALS` | Inline error under password; keep email |
| 403 `EMAIL_NOT_VERIFIED` | Error with action "Resend verification email" → `POST /auth/resend-verification {email}` → CheckMail screen |
| 403 `ACCOUNT_DELETION_SCHEDULED` (`restoreToken`, `deletionScheduledAt`) | `PendingDeletionScreen(date, restoreToken)` |
| 403 `ACCOUNT_DISABLED` | Error "Account disabled, contact support@andrew-dev.org" |
| 429 `TOO_MANY_ATTEMPTS` | Error with the backend `detail`; disable the button for 30 s |

Email field: `KeyboardType.Email`, trimmed, lowercased before send. Password: show/hide toggle,
`imeAction = Done` submits. Autofill hints `AUTOFILL_HINT_EMAIL_ADDRESS` / `PASSWORD`; the
app registers as a Credential Manager client so password managers work.

### 3.2 Two-factor step

`POST /auth/login/two-factor {challenge, code, trustDevice}` → `TokenResponse` (+ `trustedDeviceToken`
in the body when `trustDevice=true`, per B2).

- 6-digit code field (one `BasicTextField` styled as 6 boxes; paste supported; auto-submit on
  6th digit) with a toggle "Use a recovery code" that switches to a text field accepting
  `XXXX-XXXX`.
- Checkbox "Trust this device for 90 days", default off.
- `TWO_FACTOR_EXPIRED` (challenge is valid 5 min) → return to sign-in with "Code expired, sign in again".
- `INVALID_TWO_FACTOR_CODE` → shake + inline error, clear the code.
- Autofill from SMS is not applicable (TOTP); support `AUTOFILL_HINT_SMS_OTP` anyway for
  authenticator apps that offer autofill.

### 3.3 Registration

`POST /auth/register {email, password, firstName, lastName?}` → 202 always (also when the
email exists). Then `CheckMailScreen(email)` with "Resend" (`POST /auth/resend-verification`,
rate-limited: 5/address/hour → show 429 detail) and "Open email app" (`Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_EMAIL)`).

Validation mirrors the backend: first name required, password 8+ chars and ≤ 72 UTF-8 bytes
(show "Password is too long" when bytes > 72, which can happen with Cyrillic at ~36 chars).
Consent text links to Terms and Privacy (opened in Custom Tabs).

### 3.4 Email verification (App Link)

`https://wallet.andrew-dev.org/verify-email#token=…` is claimed by the app (B7 assetlinks).
`VerifyEmailScreen` reads `uri.fragment`, calls `POST /auth/verify-email {token}`:
204 → "Email confirmed" + "Sign in" button (prefill the email if known). `INVALID_TOKEN` →
"Link expired" with an email field to resend. If the app is opened via the link while signed
in as a different user, still verify (it is user-agnostic) and show the result.

### 3.5 Forgot / reset password

- `ForgotPasswordScreen`: email → `POST /auth/forgot-password` → "Check your mail" (always 202).
- App Link `/reset-password#token=…` → `ResetPasswordScreen(token)`: new password + repeat,
  `POST /auth/reset-password {token, newPassword}` → 204 → "Password changed" → sign-in with
  email prefilled. `INVALID_TOKEN` → expired screen with "Request a new link".
- A reset revokes all sessions server-side; if the app had a session it will be force
  signed-out on the next call, which is expected.

### 3.6 Account scheduled for deletion

`PendingDeletionScreen`: "Your account will be deleted on {date}". Buttons: "Keep my account"
→ `POST /auth/restore {token: restoreToken}` → `LoginResponse` (may again return a 2FA
challenge) → signed in; "Leave" → back to sign-in. App Link `/restore-account#token=…` (the
emailed token) → `POST /auth/restore {token}` → shows "Deletion cancelled, sign in".

### 3.7 Google sign-in (M2, requires B3)

Use Credential Manager with `GetGoogleIdOption` (`serverClientId` = the backend's **web**
OAuth client ID so the ID token audience matches what the backend verifies; the Android client
ID is registered in the same GCP project for SHA-1 binding). Flow:

1. `GET /auth/options` → if `googleSignIn=false`, hide the button.
2. Button "Continue with Google" → `CredentialManager.getCredential` → `GoogleIdTokenCredential.idToken`.
3. `POST /auth/google/native {idToken}` → `LoginResponse` (same branches as 3.1, including
   2FA and pending deletion).
4. Errors from the backend map the web's codes: `cancelled` (user dismissed; no message),
   `failed`, `unverified` ("Your Google email is not verified"), `email_taken`
   ("An account with this email exists; sign in with password and link Google in Settings"),
   `disabled`.

Linking in Settings → Security uses the same credential flow and `POST /me/google/link/native
{idToken, password?, code?}` after `ConfirmIdentitySheet`. Unlink: `POST /me/google/unlink`,
blocked in UI when `hasPassword=false` (explain "Set a password first").

### 3.8 Sign-out

`POST /auth/logout {refreshToken}` (B1) best-effort with a 5 s timeout, then local sign-out
regardless of the result. Also `DELETE /devices/{fcmToken}` best-effort (M5, B4).

## 4. Confirm identity

Sensitive actions (2FA setup, Google link, Telegram link, account deletion) take
`{password?, code?}`. `ConfirmIdentitySheet` shows a password field when `me.hasPassword`,
otherwise a 2FA code field (the backend requires one of them). It returns the pair to the
caller, which includes it in the request. `INVALID_CURRENT_PASSWORD` / `INVALID_TWO_FACTOR_CODE`
→ inline error, stay in the sheet.

## 5. Two-factor management (Settings → Security)

- Setup: `ConfirmIdentitySheet` → `POST /me/two-factor/setup` → `{secret, otpauthUri}`.
  Screen shows a QR code (render the `otpauthUri` with ZXing `QRCodeWriter` into a `Bitmap`,
  320 dp, high error correction), the manual key in groups of 4 with Copy, a button "Open in
  authenticator" (`Intent(ACTION_VIEW, otpauthUri)` when a handler exists), then a 6-digit
  code field → `POST /me/two-factor/enable {code}` → `{recoveryCodes[10]}`.
- Recovery codes screen: monospace list, buttons Copy all, Share (as text), Save as file
  (SAF `CreateDocument("text/plain")`, `webwallet-recovery-codes.txt`), and a checkbox
  "I have saved these codes" that enables Done. FLAG_SECURE on this screen regardless of lock
  setting.
- Disable: code (+ password when `hasPassword`) → `POST /me/two-factor/disable`.
- Regenerate codes: code → `POST /me/two-factor/recovery-codes`.
- Show `recoveryCodesLeft` with a warning when ≤ 3.

## 6. App lock (PIN + biometrics)

Optional. Defaults: disabled. Offered once right after the first successful sign-in on a
device (`LockOfferScreen`: "Protect the app with a PIN" → Set up / Not now). Also in Settings →
Security → App lock.

### 6.1 Setup

1. Enter a 4–8 digit PIN (numeric keypad composable, not the system keyboard); forbid
   sequences (`1234`, `4321`, `1111`) with a warning that can be overridden.
2. Repeat PIN.
3. If `BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == SUCCESS`, offer "Unlock with
   fingerprint/face" (toggle, default on). Store the flag and create a Keystore key with
   `setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)`; the
   key is used only via `BiometricPrompt.CryptoObject` to detect enrolment changes
   (`KeyPermanentlyInvalidatedException` → disable biometric unlock, require PIN, tell the user).
4. Auto-lock timeout picker: Immediately (default), 1 min, 5 min, 15 min.

### 6.2 Locking

- Lock when the app goes to background for longer than the timeout (`ProcessLifecycleOwner`
  `onStop` timestamp vs `onStart`), on process restart, and on the user tapping "Lock" in the
  More tab.
- While locked, the content is replaced by `LockScreen` (not drawn underneath); the
  `RecentsScreen` thumbnail is blanked by `FLAG_SECURE`, which is set whenever lock is enabled.
- `LockScreen`: app logo, "Enter PIN", keypad, biometric button (auto-prompts on appear when
  enabled), "Forgot PIN? Sign out" (confirm → full sign-out; server session is revoked by
  logout, so re-authentication is required).
- Wrong PIN: after 5 failures, 30 s cooldown; after 10, sign out. Counter persists in DataStore.
- Widgets show no amounts when lock is enabled unless the user enables "Show balances in
  widgets when locked" (Settings → App lock; default off). Notifications never include amounts
  when lock is enabled (the server does not know; the client-side push handler replaces the
  body with a generic text, see [06](06-notifications-widgets-shortcuts.md)).

### 6.3 Disable / change

Settings → Security → App lock: Change PIN (requires current PIN or biometric), toggle
biometrics, change timeout, Turn off (requires current PIN).

## 7. Deep links and App Links

Claimed `https://wallet.andrew-dev.org` paths (`android:autoVerify="true"`):

| Path | Handling |
|---|---|
| `/verify-email` (`#token`) | §3.4 |
| `/reset-password` (`#token`) | §3.5 |
| `/restore-account` (`#token`) | §3.6 |
| `/unsubscribe?user&token` | `UnsubscribeScreen`: `POST /notifications/unsubscribe` (public) → "Unsubscribed" + "Subscribe again" (`/resubscribe`); works signed-out |
| `/transactions`, `/budget`, `/goals`, `/accounts`, `/investments`, `/taxes`, `/reports`, `/settings` (+ web query params where meaningful, e.g. `?new=1`, `?tab=`) | Open the matching tab/screen when signed in; otherwise sign in first, then continue |
| anything else | Open Overview |

The URL fragment is delivered in `intent.data` on Android; read `Uri.fragment` and parse
`token=`. Custom scheme `webwallet://` is used only for internal shortcuts/widgets
(`quick-add`, `lock`), not advertised.

Backend side: serve `/.well-known/assetlinks.json` from Caddy (B7) with the Play App Signing
SHA-256 **and** the upload/debug certificates for `dev`.

## 8. Transport and platform security

- `network_security_config.xml`: cleartext off; `dev` flavor adds a debug-overrides block for
  `10.0.2.2`/`localhost`. No pinning.
- `WebView` is not used; legal pages and Google consent open in Chrome Custom Tabs.
- Clipboard: copying a recovery code or key uses `ClipData` with `EXTRA_IS_SENSITIVE`.
- Screenshots: `FLAG_SECURE` when lock is enabled, and always on recovery-codes and 2FA setup.
- Root/emulator detection: not implemented (false positives outweigh benefits for this app).
- Secrets in the APK: none. Firebase config is public by design; the Google web client ID is
  public. API keys for Twelve Data/Alpha Vantage stay on the server.
- Rate limits: honour `429` with `Retry-After` when present; the global 300 req/min/IP limit
  is a risk on carrier NAT — see B9.
