# 07 — Backend Changes Required for the App

All changes land in `D:\Dev\WebWallet` on `dev`, follow the repo conventions (ProblemDetail
errors, every response field `required`, nullable as `[X, "null"]`, partial PUT rules), update
`api/openapi.json` via `mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true`, keep JaCoCo
≥ 75 %, and must not break the web client (new response fields are additive; new request
fields are optional).

| Id | Change | Needed by | Size |
|---|---|---|---|
| B1 | Refresh token in the response/request body for native clients | M1 | M |
| B2 | Trusted-device token in the body for native clients | M1 | S |
| B3 | Google sign-in and link with a native ID token | M2 | M |
| B4 | Device registration and FCM push delivery | M5 | L |
| B5 | `Idempotency-Key` on create endpoints | M1 (recommended) | M |
| B6 | Client identification headers accepted and logged | M1 | S |
| B7 | `/.well-known/assetlinks.json` and email links kept on the web domain | M1 | S |
| B8 | `GET /api/v1/app/config` (min version, feature flags) | M1 | S |
| B9 | Rate limiting keyed per user for authenticated requests | M1 (recommended) | S |
| B10 | Transactions: `updatedAfter` filter and `includeDeleted` tombstones | later, optional | M |

## B1 — Mobile token transport

Trigger: request header `X-WebWallet-Client: android` (or `ios` later). When present:

- `POST /auth/login`, `POST /auth/login/two-factor`, `POST /auth/restore`,
  `POST /auth/google/complete`, `POST /auth/google/native`, `POST /me/password`, and
  `POST /auth/refresh` **do not set** the `refresh_token` cookie and instead include
  `refreshToken: string` in the JSON body (`LoginResponse`/`TokenResponse` get a nullable
  `refreshToken` field; it stays `null` for browser clients).
- `POST /auth/refresh` and `POST /auth/logout` accept an optional JSON body
  `{refreshToken}`. Resolution order: body field, then cookie. Reuse detection and family
  revocation behave exactly as today.
- Refresh tokens issued to native clients get `client` = `android` stored on
  `refresh_tokens` (new nullable column, migration V33) so the user can see device sessions
  later; no UI for that yet.
- Tests: refresh via body rotates and revokes on reuse; a browser request never sees
  `refreshToken` in the body; a native request never receives a `Set-Cookie`.

## B2 — Trusted device in the body

Same trigger. `POST /auth/login/two-factor` with `trustDevice=true` returns
`trustedDeviceToken: string | null` in the body (no cookie). `POST /auth/login` and
`POST /auth/google/native` accept optional `trustedDeviceToken` in the body, checked the same way
as the `trusted_device` cookie. Password reset and 2FA disable still invalidate all trusted
devices.

## B3 — Google native sign-in

- `POST /api/v1/auth/google/native {idToken}` → `LoginResponse`. Verify the ID token with
  Google's JWKS (`googleapis.com/oauth2/v3/certs`, already used), `iss` in
  `{accounts.google.com, https://accounts.google.com}`, `aud` ∈ configured client IDs
  (`GOOGLE_CLIENT_ID` plus new `GOOGLE_ANDROID_CLIENT_IDS`, comma-separated), `exp`, and
  `email_verified=true` (else error code `GOOGLE_EMAIL_UNVERIFIED`, 400). Then reuse the
  existing "find by `googleSubject` or link by verified email or create user" logic from the
  web callback, including 2FA challenge and `ACCOUNT_DELETION_SCHEDULED` handling.
- `POST /api/v1/me/google/link/native {idToken, password?, code?}` → 204, requiring
  `ConfirmIdentityRequest` like the web link. Error `GOOGLE_ACCOUNT_TAKEN` (409) when the
  subject is linked to another user.
- Add the new codes to `ErrorCode` and the OpenAPI enum. Rate limit: count under the login
  attempt counters.
- `GET /auth/options` additionally returns `googleWebClientId: string | null` so the app does
  not hardcode it.

## B4 — Push notifications

- Table `user_devices` (V34): `id`, `user_id`, `platform` (`ANDROID`/`IOS`), `token` (unique),
  `app_version`, `locale`, `created_at`, `last_seen_at`, `failed_at`.
- `PUT /api/v1/devices {token, platform, appVersion, locale}` → 204 (upsert by token; moving a
  token between users reassigns it). `DELETE /api/v1/devices/{token}` → 204.
- `NotificationSettingsDto` gets `push: boolean` (default `true`). Push follows the existing
  per-kind flags (`recurring`, `budgets`, …) and `hour`; when `push=false` nothing is sent.
- Delivery: `PushSender` using FCM HTTP v1 (service account JSON via `FCM_CREDENTIALS_JSON`
  env, project id via `FCM_PROJECT_ID`). Messages are **data-only** (no `notification` block,
  so the app's `onMessageReceived` always runs and can apply the lock-screen privacy rule):
  `data{title, body, kind, route, refId, sentAt}`, `title`/`body` localised by the user's
  `locale`, `route` one of the deep link paths in [06](06-notifications-widgets-shortcuts.md).
  Android `priority: high` for TAXES and BANKS, `normal` otherwise. Collapse key per `kind`. Remove
  tokens on `UNREGISTERED`/`INVALID_ARGUMENT`; mark `failed_at` on other errors and drop after
  7 days of failures.
- Reuse `NotificationLog` dedup: one log row per `(user, kind, ref)` regardless of channel, so
  a user with Telegram + push gets both once. Sending order: push, Telegram, email.
- `AccountDeletionTask` and `/me/reset` delete `user_devices`.

## B5 — Idempotency-Key

For `POST` endpoints that create resources (`/transactions`, `/investments/operations`,
`/goals`, `/goals/{id}/contributions`, `/recurring`, `/categories`, `/tags`, `/payees`,
`/categorization-rules`, `/imports/previews/{id}/confirm`, `/imports/freedom/previews/{id}/confirm`,
`/budget/{month}/move`): if `Idempotency-Key` (UUID) is present, store `(user_id, key,
request_hash, status, response_body)` in `idempotency_keys` (V35) for 24 h. Same key + same
hash → replay the stored response; same key + different hash → `409 CONFLICT` with code
`IDEMPOTENCY_MISMATCH`. Implemented as a `HandlerInterceptor` + response wrapper, or a filter
scoped to those paths. Cleanup in `TokenCleanupTask`.

## B6 — Client headers

Accept and log `X-WebWallet-Client`, `X-App-Version`, `User-Agent` in the request MDC so
server logs and error alerts show which client produced an error. Add
`X-WebWallet-Client, X-App-Version, Idempotency-Key` to `CORS_ALLOWED_HEADERS` is **not**
needed (CORS is a browser concern), but Caddy must not strip them.

## B7 — App Links and email links

- Serve `GET /.well-known/assetlinks.json` from Caddy as a static file (not through Spring):
  ```json
  [{"relation":["delegate_permission/common.handle_all_urls"],
    "target":{"namespace":"android_app","package_name":"org.andrewdev.webwallet",
              "sha256_cert_fingerprints":["<PLAY_APP_SIGNING_SHA256>","<UPLOAD_SHA256>"]}},
   {"relation":["delegate_permission/common.handle_all_urls"],
    "target":{"namespace":"android_app","package_name":"org.andrewdev.webwallet.dev",
              "sha256_cert_fingerprints":["<DEBUG_SHA256>"]}}]
  ```
  `Content-Type: application/json`, no redirect, HTTP 200. Add to `deploy/Caddyfile` and
  `deploy.sh`.
- Email links keep pointing at `APP_FRONTEND_URL` routes; no change. Android intercepts them
  via App Links when the app is installed. The web pages continue to work as fallback.
- Optional: the unsubscribe page is public and works in the app; nothing to change.

## B8 — App config

`GET /api/v1/app/config` (public, cached 5 min, no rate-limit cost beyond the global one):

```json
{"android":{"minSupportedVersionCode":1,"latestVersionCode":1,"storeUrl":"https://play.google.com/store/apps/details?id=org.andrewdev.webwallet"},
 "features":{"googleSignIn":true,"marketSearch":true,"telegram":true,"push":true}}
```

Values from `application.properties`/env (`APP_ANDROID_MIN_VERSION_CODE`, …). The app shows a
blocking "Update required" screen when its `versionCode < minSupportedVersionCode` and a
dismissible banner when `< latestVersionCode`. `features` lets the app hide Google/market
search/Telegram/push without a release; it supersedes `GET /auth/options.googleSignIn` for the
app (keep `/auth/options` for the web).

## B9 — Rate limiting per user

The global limiter is 300 requests/minute per client IP. Mobile users behind carrier-grade
NAT share IPs, so a handful of active users could trip it. Change: authenticated requests count
against the user id (same 300/min), unauthenticated requests keep the IP key. Webhooks stay
exempt. Return `Retry-After` on 429.

## B10 — Incremental sync (optional, after M5)

Only if the read cache proves insufficient: `GET /transactions?updatedAfter=<instant>` plus
soft-delete tombstones (`deleted_at` column, `includeDeleted=true`) so the app can refresh the
feed cache incrementally instead of per page. Not required for any milestone in this spec.

## Implementation tickets

B1, B2, B6 and B8 are written up as code-level tickets in [backend-tasks/](backend-tasks/README.md);
the tickets take precedence over the summaries above where they differ.

## Rollout order

1. B6, B8, B9, B7 (no API model changes beyond a new public endpoint) — one PR.
2. B1 + B2 — one PR, with web client regression run (the web must be unaffected).
3. B5 — one PR.
4. B3 — one PR, needs the Android OAuth client created in GCP first (SHA-1 of upload key and
   Play signing key).
5. B4 — one PR, needs the Firebase project and service account.

After each PR: regenerate `api/openapi.json`, run the web client `npm run api` drift check,
and copy the spec into `WebWalletAndroid/openapi/openapi.json`.
