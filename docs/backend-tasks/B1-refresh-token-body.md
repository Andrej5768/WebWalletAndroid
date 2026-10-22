# B1 — Refresh token in the body for native clients

**Goal:** the Android app can keep a session. Today the refresh token exists only as the
`refresh_token` HttpOnly cookie (`Path=/api/v1/auth`, `SameSite=Strict`), which a native app
cannot use sensibly. For requests marked as coming from a native app (B6 `ClientInfo`), the
server returns the refresh token in the JSON body and accepts it from the JSON body. Browser
clients see no change at all.

Size: M. Depends on B6 (`ClientInfo`). Ship together with B2.

## 1. Behaviour

Trigger: `ClientInfo.current(request).nativeApp()` (header `X-WebWallet-Client: android|ios`).

| Endpoint | Web (unchanged) | Native |
|---|---|---|
| `POST /auth/login` | `Set-Cookie: refresh_token`, body `refreshToken: null` | no `Set-Cookie`, body `refreshToken: "<raw>"` (when a session is issued; `null` with a 2FA challenge) |
| `POST /auth/login/two-factor` | cookie; body `refreshToken: null` | body `refreshToken` |
| `POST /auth/refresh` | reads cookie, rotates, sets new cookie | reads `{refreshToken}` body, rotates, returns new one in body, no cookie |
| `POST /auth/logout` | reads cookie, revokes, clears cookie | reads `{refreshToken}` body, revokes, **no** `Set-Cookie` |
| `POST /auth/restore` | cookie when a session is issued | body |
| `POST /auth/google/complete` | cookie | body |
| `POST /me/password` | cookie | body |

Token resolution on `/refresh` and `/logout`: the body field when present and non-blank,
otherwise the cookie. This holds for both client kinds — a web request that happens to send a
body is served; what the **response** does (cookie vs body) is decided by `ClientInfo` alone.
A native request with neither body nor cookie → `401 INVALID_REFRESH_TOKEN` (as today for a
missing cookie).

Everything else is unchanged: rotation on every refresh, reuse detection revokes the family,
30-day TTL, `tokenVersion` revocation, rate limits, `bearerTokenResolver` ignoring the
`Authorization` header on `/api/v1/auth/**`.

## 2. API changes (additive)

### DTOs (`auth/dto`)

- `TokenResponse(String accessToken, String tokenType, long expiresIn, @Schema(nullable = true) String refreshToken)`
  - keep `bearer(accessToken, expiresIn)` → `refreshToken = null`;
  - add `bearer(IssuedTokens tokens, boolean includeRefresh)`.
  - Javadoc: "`refreshToken` is set for the native apps only; the web client gets it in an HttpOnly cookie."
  - B2 adds a fifth field `trustedDeviceToken`; design the factory so both land cleanly.
- `LoginResponse(…, @Schema(nullable = true) String refreshToken)` as the **last** component.
  `session(accessToken, expiresIn)` → `session(IssuedTokens tokens, boolean includeRefresh)`;
  `challenge(...)` unchanged (`refreshToken = null`).
- New `RefreshTokenRequest(@Size(max = 128) String refreshToken)` — `refreshToken` nullable
  (no `@NotBlank`), used by `/refresh` and `/logout` as `@RequestBody(required = false)`.
  Javadoc: "Native apps send the token here; browsers send the cookie and no body."

### Controllers

Replace `RefreshTokenCookie.respond(...)` with a new component **`SessionResponse`**
(`com.andrewcorp.webwallet.auth.SessionResponse`), leaving `RefreshTokenCookie` with
`of(IssuedTokens)` and `cleared()` only (and `PATH`). Signatures:

```java
ResponseEntity<TokenResponse> tokens(IssuedTokens tokens, ClientInfo client);
ResponseEntity<LoginResponse> login(AuthService.LoginResult result, ClientInfo client);
```

- web → `header(SET_COOKIE, refreshTokenCookie.of(tokens))` + body without the token;
- native → no header + body with the token.

Call sites to update (each controller method gains a `ClientInfo client` parameter, hidden from
springdoc as per B6 §2):

| File | Method |
|---|---|
| `auth/AuthController.java` | `login`, `loginTwoFactor`, `refresh`, `logout`, `restore` |
| `auth/google/GoogleController.java` | `complete` |
| `user/UserController.java` | `changePassword` |

`refresh`:

```java
@PostMapping("/refresh")
public ResponseEntity<TokenResponse> refresh(
        @Valid @RequestBody(required = false) RefreshTokenRequest body,
        @Parameter(description = "Refresh token set by login or a previous refresh")
                @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
        ClientInfo client) {
    String presented = presentedToken(body, cookie);   // body first, then cookie, else throw INVALID_REFRESH_TOKEN
    return sessionResponse.tokens(tokenService.refresh(presented, client.kind()), client);
}
```

`logout`: same resolution; `tokenService.revokeSession(token)` when a token was presented; add
the `cleared()` cookie header **only** for web (`!client.nativeApp()`). Status stays 204.

Update the `@Operation` descriptions of `login`, `loginTwoFactor`, `refresh`, `logout`,
`changePassword` to mention: "Native apps (header `X-WebWallet-Client`) get the refresh token in
`refreshToken` instead of the cookie and send it in the body."

### Token storage — which client owns a session

Migration `V33__refresh_token_client.sql`:

```sql
-- Which client the session belongs to: null for the web client (cookie), android / ios for the apps (body)
alter table refresh_tokens add column client varchar(16);
```

`RefreshToken` entity: `@Column(length = 16) @Enumerated(EnumType.STRING) private ClientInfo.Kind client;`
(nullable; `null` means WEB; expose `getClient()` returning `Kind.WEB` for null). Constructor
gains the parameter.

`TokenService`:

- `issueTokens(User user)` → `issueTokens(User user, ClientInfo.Kind client)`;
- `refresh(String raw)` → `refresh(String raw, ClientInfo.Kind client)`: the rotated token
  inherits the presented token's `client` (ignore the parameter if they differ? **No**: if the
  presented token's client differs from the requesting client — a web cookie replayed by a
  native header or vice versa — treat it like reuse: log a warning, revoke the family, throw
  `INVALID_REFRESH_TOKEN`. Cheap extra protection, and tested.)
- private `issueTokens(user, familyId, client)` stores the client.

Thread the `ClientInfo.Kind` through the services that issue sessions (add a parameter; no
other logic change):

| Service | Method |
|---|---|
| `auth/AuthService.java` | `login(request, trustedDeviceToken, clientIp, client)`, `startSession(user, trustedDeviceToken, client)`, `changePassword(userId, current, new, client)` |
| `auth/twofactor/TwoFactorService.java` | `completeLogin(challenge, code, trustDevice, client)` (B2 also changes this) |
| `auth/google/GoogleSignInService.java` | `complete(ticket, trustedDeviceToken, client)` |
| `user/AccountDeletionService.java` | `restore(rawToken, client)` |

Other callers of `issueTokens`/`startSession` (search for them: Telegram? tests?) pass
`ClientInfo.Kind.WEB`.

## 3. Security notes (put the gist into Javadoc of `SessionResponse`)

- For native clients there is no cookie, so the CSRF reasoning of the cookie (`SameSite=Strict`)
  is not needed; the token travels only in a JSON body over HTTPS and is stored by the app
  encrypted (client spec 03 §1).
- Never log the raw token. `RefreshTokenRequest.toString()` must not include it — records print
  all components, so override `toString()` to return `"RefreshTokenRequest[***]"`.
- The response body with `refreshToken` must not be cached: `/auth/**` responses already go
  with `Cache-Control: no-store`? **Verify**; if Spring Security's cache-control headers are not
  applied to these responses (they are, by default, for the security filter chain), add
  `.cacheControl(CacheControl.noStore())` to `SessionResponse` builders explicitly anyway.

## 4. Tests — `AuthControllerTest` and friends

Add a helper in `AbstractIntegrationTest`:

```java
protected static MockHttpServletRequestBuilder asAndroid(MockHttpServletRequestBuilder request) {
    return request.header(ClientInfo.CLIENT_HEADER, "android").header(ClientInfo.VERSION_HEADER, "1");
}
```

New tests:

1. `nativeLoginReturnsTheRefreshTokenInTheBodyAndSetsNoCookie` — `refreshToken` non-blank,
   `Set-Cookie` header absent, `refresh_tokens.client = 'ANDROID'`.
2. `webLoginKeepsTheCookieAndHasNoRefreshTokenInTheBody` — existing behaviour; assert
   `jsonPath("$.refreshToken").value(nullValue())`.
3. `nativeRefreshRotatesTheTokenInTheBody` — refresh with body → 200, new token ≠ old, no
   `Set-Cookie`; old token → 401 and the family is revoked (`refreshTokens` repository check,
   as `refreshRotatesTheRefreshTokenCookie` does).
4. `nativeRefreshWithoutATokenIsRejected` — native header, no body, no cookie → 401 `INVALID_REFRESH_TOKEN`.
5. **Replace** `refreshTokenInTheBodyIsNotAccepted` with `webRefreshAcceptsTheTokenFromTheBodyToo`
   (no native header, body token → 200 and a new cookie is set). The old test encoded the old
   rule and must go.
6. `refreshTokenOfAnotherClientKindRevokesTheSession` — login as web (cookie), present that
   token in the body with the native header → 401, family revoked.
7. `nativeLogoutRevokesTheSessionWithoutSetCookie` — 204, no `Set-Cookie`, refresh with the
   token afterwards → 401.
8. `nativeTwoFactorLoginReturnsTheRefreshTokenInTheBody` — in `twofactor/*Test` (combine with B2).
9. `nativeRestoreReturnsTheRefreshTokenInTheBody` — in the account deletion test class.
10. `nativeGoogleCompleteReturnsTheRefreshTokenInTheBody` — in `auth/google/*Test` (the ticket
    flow already has a test; add the header variant).
11. `nativeChangePasswordReturnsTheRefreshTokenInTheBody` — in `user/*Test` where
    `changePassword` is tested.
12. `RefreshTokenRequestTest` (unit): `toString()` hides the token.

Regenerate `api/openapi.json`; `typesAreExactForGeneratedClients` passes (new fields nullable,
`RefreshTokenRequest.refreshToken` nullable).

## 5. Web client check

From `D:\Dev\WebWalletClient`: `npm run api` (point `OPENAPI_SOURCE` at `../WebWallet/api/openapi.json`
or the running backend), then `npm run typecheck && npm test && npx playwright test`. Expected:
only `schema.d.ts` changes (new nullable fields); no source edits needed. Commit the regenerated
types in the web repo after the backend PR merges.

## 6. Manual check

```bash
# native login
curl -s -H 'Content-Type: application/json' -H 'X-WebWallet-Client: android' -d '{"email":"a@b.c","password":"..."}' http://localhost:8080/api/v1/auth/login
```

```bash
# native refresh
curl -si -H 'Content-Type: application/json' -H 'X-WebWallet-Client: android' -d '{"refreshToken":"<from login>"}' http://localhost:8080/api/v1/auth/refresh
```

No `Set-Cookie` in either response; the second returns a new `refreshToken`; repeating it with
the first token returns 401.

## 7. Docs

README "API conventions" (~lines 98–114): replace the last bullet ("The native mobile apps will
need a separate way…") with a short "Native apps" paragraph: the header, body transport for
`login`, `login/two-factor`, `refresh`, `logout`, `restore`, `google/complete`, `me/password`;
body-first resolution; same rotation and reuse rules; `client` column.

## 8. Done when

- [ ] All call sites compile with `ClientInfo`; web behaviour byte-for-byte unchanged in the
      existing tests (no existing assertion weakened except the one replaced test).
- [ ] New tests above green; coverage gate passes.
- [ ] `V33` migration, `api/openapi.json`, README updated.
- [ ] Web client regenerated types verified locally.
