# B2 — Trusted-device token in the body for native clients

**Goal:** a user with two-factor authentication who ticks "trust this device" in the Android
app is not asked for a code for 90 days, like in the browser. Today the trusted-device marker is
the `trusted_device` cookie (`Path=/api/v1/auth`); native clients need it in the body, same
pattern as B1.

Size: S. Depends on B1 (`ClientInfo` in the controllers, `TokenResponse` factory). Ship in the
same PR as B1.

## 1. Behaviour

| Endpoint | Web (unchanged) | Native |
|---|---|---|
| `POST /auth/login/two-factor` with `trustDevice=true` | `Set-Cookie: trusted_device` | no cookie; body `trustedDeviceToken: "<raw>"` |
| `POST /auth/login` | reads `trusted_device` cookie | reads optional body field `trustedDeviceToken`; cookie as fallback |
| `POST /auth/google/complete` | reads cookie | reads optional body field `trustedDeviceToken`; cookie as fallback |
| `POST /auth/google/native` (B3, later) | — | will take the same body field |

Server-side validation, TTL (`app.auth.trusted-device-ttl=90d`), hashing (`TokenHasher`),
"one trusted device per user" (`trustedDevices.deleteByUserId` before save in
`TwoFactorService` ~line 210) and invalidation on password reset / 2FA disable are unchanged.

## 2. API changes (additive)

- `LoginRequest(email, password, @Size(max = 128) String trustedDeviceToken)` — optional
  (nullable, no `@NotBlank`). Javadoc: "Returned to native apps by a two-factor sign-in with
  trustDevice; browsers keep it in a cookie instead."
- `GoogleTicketRequest(ticket, @Size(max = 128) String trustedDeviceToken)` — same.
- `TokenResponse` gains `@Schema(nullable = true) String trustedDeviceToken` (after
  `refreshToken` from B1). It is non-null only in the `/login/two-factor` response for native
  clients with `trustDevice=true`; everywhere else `null`. Factory:
  `TokenResponse.bearer(IssuedTokens tokens, boolean includeRefresh, String trustedDeviceToken)`;
  the two-argument `bearer(...)` keeps passing `null`.
  (A separate response type for `/login/two-factor` would rename the schema the web client
  already uses for that path; adding a nullable field is the compatible choice.)
- `TwoFactorLoginRequest` unchanged (`trustDevice` boolean already exists).

### Controllers

`AuthController.login`:

```java
String trusted = request.trustedDeviceToken() != null && !request.trustedDeviceToken().isBlank()
        ? request.trustedDeviceToken() : trustedDeviceCookie;   // body first, then cookie
return sessionResponse.login(authService.login(request, trusted, httpRequest.getRemoteAddr(), client.kind()), client);
```

`AuthController.loginTwoFactor`:

```java
TwoFactorService.Login login = twoFactor.completeLogin(request.challenge(), request.code(), request.trustDevice(), client.kind());
if (client.nativeApp()) {
    return sessionResponse.tokens(login.tokens(), login.trustedDeviceToken(), client);   // overload: token goes into the body
}
ResponseEntity.BodyBuilder response = ResponseEntity.ok().header(SET_COOKIE, refreshTokenCookie.of(login.tokens()));
if (login.trustedDeviceToken() != null) response.header(SET_COOKIE, trustedDeviceCookie.of(login.trustedDeviceToken()));
return response.body(TokenResponse.bearer(login.tokens(), false, null));
```

Cleaner: give `SessionResponse` a single method
`tokens(IssuedTokens tokens, String trustedDeviceToken /* nullable */, ClientInfo client)` that
does the web/native split for both tokens, and have `AuthController.loginTwoFactor` call only
that. `GoogleController.complete`: resolve the token the same way as `login` (body first, cookie
fallback).

`@Operation` descriptions of `login`, `loginTwoFactor`, `complete`: add "Native apps send and
receive `trustedDeviceToken` in the body."

## 3. Tests

In the two-factor test class (`src/test/java/com/andrewcorp/webwallet/auth/twofactor/`) — the
web cases exist; add native variants using `asAndroid(...)` from B1:

1. `nativeTwoFactorLoginWithTrustDeviceReturnsTheTokenInTheBody` — body has
   `trustedDeviceToken` and `refreshToken`; no `Set-Cookie` at all.
2. `nativeTwoFactorLoginWithoutTrustDeviceHasNullTrustedDeviceToken`.
3. `nativeLoginWithTheTrustedDeviceTokenInTheBodySkipsTheCode` — second login with the body
   field returns tokens, no `twoFactorChallenge`.
4. `nativeLoginWithAnExpiredOrUnknownTrustedDeviceTokenAsksForTheCode` — garbage token →
   `twoFactorChallenge` set (no error; same as an unknown cookie today).
5. `webTwoFactorLoginStillUsesTheCookieAndHasNullTrustedDeviceTokenInTheBody`.
6. `passwordResetInvalidatesTrustedDevicesForNativeToo` — only if not already covered
   generically (it is server-side and client-agnostic; one assertion with the body token is enough).
7. Google: `nativeGoogleCompleteHonoursTheTrustedDeviceTokenFromTheBody` in `auth/google/*Test`,
   mirroring the existing cookie-based case if there is one; otherwise skip and note it in the PR.

Regenerate `api/openapi.json`.

## 4. Manual check

```bash
curl -s -H 'Content-Type: application/json' -H 'X-WebWallet-Client: android' -d '{"challenge":"<from login>","code":"123456","trustDevice":true}' http://localhost:8080/api/v1/auth/login/two-factor
```

Body contains `trustedDeviceToken`; then

```bash
curl -s -H 'Content-Type: application/json' -H 'X-WebWallet-Client: android' -d '{"email":"a@b.c","password":"...","trustedDeviceToken":"<from above>"}' http://localhost:8080/api/v1/auth/login
```

returns `accessToken` + `refreshToken` directly, `twoFactorChallenge: null`.

## 5. Docs

README "API conventions", two-factor bullet: mention the body field for native apps (one
sentence, next to the B1 paragraph).

## 6. Done when

- [ ] DTO fields, controller resolution, `SessionResponse` overload, tests, OpenAPI, README.
- [ ] Web two-factor tests unchanged and green.
