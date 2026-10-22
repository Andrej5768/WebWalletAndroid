# B8 — `GET /api/v1/app/config`

**Goal:** a public endpoint the Android app calls on start to learn (a) whether its version is
still supported, (b) which optional features the server has enabled. Lets the app force an
update and hide Google sign-in / market search / Telegram / push without an app release.

Size: S.

## 1. Endpoint

`GET /api/v1/app/config` — public (no bearer), cacheable.

Response `200 application/json`, schema `AppConfigResponse`:

```json
{
  "android": { "minSupportedVersionCode": 1, "latestVersionCode": 1,
               "storeUrl": "https://play.google.com/store/apps/details?id=org.andrewdev.webwallet" },
  "features": { "googleSignIn": true, "marketSearch": true, "telegram": true, "push": false }
}
```

Headers: `Cache-Control: public, max-age=300`.

Every field is required and non-null (no `@Schema(nullable = true)` anywhere): `storeUrl` falls
back to the default Play URL, version codes default to `1`, `push` is a constant `false` until
B4 ships (then it becomes "FCM configured").

## 2. Code

Package `com.andrewcorp.webwallet.config` already holds infrastructure; put the feature in a
new package `com.andrewcorp.webwallet.app`:

- `AppConfigController` — `@Tag(name = "App")`, `@RestController`, `@RequestMapping("/api/v1/app")`,
  one `@GetMapping("/config")` returning `ResponseEntity<AppConfigResponse>` with the
  `CacheControl.maxAge(5, MINUTES).cachePublic()`.
  `@Operation(summary = "Versions and features for the native apps", description = "Public. The app refuses to run below minSupportedVersionCode and hides features that are off on this server.")`
- `AppConfigResponse(Android android, Features features)` with nested records
  `Android(int minSupportedVersionCode, int latestVersionCode, String storeUrl)` and
  `Features(boolean googleSignIn, boolean marketSearch, boolean telegram, boolean push)`.
  Javadoc each `@param`.
- `AppConfigService` builds the response from:
  - `AppProperties.android()` (new, see §3);
  - `googleSignIn` = `GoogleSignInService.enabled()` (same source as `/auth/options`);
  - `marketSearch` = Twelve Data key non-blank. Today the key is read with
    `@Value("${app.market-data.twelve-data.api-key:}")` in `TwelveDataClient`; expose a
    `boolean enabled()` on `TwelveDataClient` (or on whatever `SearchResponse.providerEnabled`
    is computed from — follow that code path so both answers always agree) and use it;
  - `telegram` = `AppProperties.telegram().enabled()`;
  - `push` = `false` (constant, with a `// B4` comment).

## 3. Properties

`AppProperties`: add `@NotNull Android android` component and

```java
/**
 * Versions of the Android app this server supports.
 *
 * @param minSupportedVersionCode the app refuses to run below it (forced update)
 * @param latestVersionCode       the app offers an update below it
 * @param storeUrl                where to send the user for the update
 */
public record Android(
        @DefaultValue("1") @Positive int minSupportedVersionCode,
        @DefaultValue("1") @Positive int latestVersionCode,
        @DefaultValue("https://play.google.com/store/apps/details?id=org.andrewdev.webwallet") @NotBlank String storeUrl) {}
```

Add a validation that `latestVersionCode >= minSupportedVersionCode` (compact constructor
throwing `IllegalArgumentException`, surfaced at startup).

`application.properties`:

```properties
# The Android app: the server refuses versions below the minimum and offers an update below the latest
app.android.min-supported-version-code=${APP_ANDROID_MIN_VERSION_CODE:1}
app.android.latest-version-code=${APP_ANDROID_LATEST_VERSION_CODE:1}
app.android.store-url=${APP_ANDROID_STORE_URL:https://play.google.com/store/apps/details?id=org.andrewdev.webwallet}
```

`.env.example`: add the three variables with a comment. `docs/deployment.md`: mention them in
the env section. README "Getting started" env list: add them.

## 4. Security and rate limit

- `SecurityConfig.securityFilterChain`: add `.requestMatchers("/api/v1/app/config").permitAll()`
  next to the other public endpoints, with a comment ("Read by the native apps before sign-in").
- `RequestRateLimitFilter`: no change (the endpoint is cheap and the app calls it once per start
  and then at most every 24 h).
- `BearerTokenResolver`: no change — the app may send an expired token; the endpoint is
  `permitAll`, and an expired bearer on a `permitAll` path would still produce 401 through the
  resource server filter. **Check this:** if `GET /app/config` with an expired/garbage
  `Authorization: Bearer x` returns 401, extend `SecurityConfig.bearerTokenResolver()` to also
  ignore the token for `/api/v1/app/**` (same reason as for `/api/v1/auth/**`). Add a test
  either way (§5).

## 5. Tests — `AppConfigControllerTest extends AbstractIntegrationTest`

- Returns 200 without a token with defaults `1/1/<play url>` and `push=false`;
  `Cache-Control` header is `max-age=300, public` (order as Spring renders it).
- Returns 200 with a garbage bearer token (see §4).
- `features.googleSignIn` is `false` in the test profile (no Google client configured) and
  equals `/auth/options.googleSignIn`.
- `features.telegram` matches `AppProperties.telegram().enabled()` in the test profile.
- Property override test (`@TestPropertySource` or `@DynamicPropertySource` in a separate
  test class): `app.android.min-supported-version-code=5`, `latest=7`, custom store URL are
  returned verbatim.
- `AppPropertiesTest` (unit, if a binder test exists; otherwise skip): `latest < min` fails
  startup validation.
- Regenerate `api/openapi.json`; the new schema `AppConfigResponse` has all fields `required`
  and none nullable (`typesAreExactForGeneratedClients` passes).

## 6. Manual check

```bash
curl -si http://localhost:8080/api/v1/app/config
```

Expect `200`, `Cache-Control: max-age=300, public`, JSON as in §1.

## 7. Done when

- [ ] Endpoint, properties, security rule, tests, OpenAPI regenerated.
- [ ] `.env.example`, `docs/deployment.md`, README env list updated.
- [ ] `docs/deployment.md` uptime checks: add `/api/v1/app/config` to the list the VM monitors.
