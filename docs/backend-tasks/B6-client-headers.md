# B6 — Client identification headers

**Goal:** the server knows which client (web, Android, later iOS) and which app version made a
request, so that (a) B1/B2 can switch the token transport per client, (b) logs and error-alert
emails show the client, and (c) support can tell web and app problems apart.

Size: S. No API model changes (OpenAPI unchanged except the optional header parameters if you
choose to document them — do **not**; keep the spec unchanged).

## 1. Headers

Sent by native clients on every request; absent from the web client.

| Header | Values | Notes |
|---|---|---|
| `X-WebWallet-Client` | `android`, `ios` (case-insensitive) | Anything else or absent → `WEB` |
| `X-App-Version` | integer `versionCode`, e.g. `1020300` | Non-integer, negative or > 20 chars → ignored (`null`) |
| `User-Agent` | `WebWallet-Android/1.2.3 (1020300; Android 14)` | Only logged, truncated to 200 chars |

CORS: these headers come from native apps, not browsers, so `CORS_ALLOWED_ORIGINS`/allowed
headers need no change. Caddy passes unknown request headers through by default; verify in
`deploy/Caddyfile` that nothing strips `X-*` headers (nothing does today).

## 2. `ClientInfo` — `com.andrewcorp.webwallet.common.web.ClientInfo`

```java
/** Which client made the request, from the X-WebWallet-Client and X-App-Version headers; the web client sends none. */
public record ClientInfo(Kind kind, Integer appVersion) {

    public static final String CLIENT_HEADER = "X-WebWallet-Client";
    public static final String VERSION_HEADER = "X-App-Version";
    public static final String REQUEST_ATTRIBUTE = ClientInfo.class.getName();
    public static final ClientInfo WEB = new ClientInfo(Kind.WEB, null);

    public enum Kind { WEB, ANDROID, IOS }

    /** True for the mobile apps, which keep their tokens themselves instead of in cookies (see B1). */
    public boolean nativeApp() { return kind != Kind.WEB; }

    public static ClientInfo of(HttpServletRequest request) { … }   // parses the two headers, never throws
    public static ClientInfo current(HttpServletRequest request) { … } // request attribute set by the filter, else of(request)
}
```

Parsing rules: trim; `kind` by `equalsIgnoreCase`; `appVersion` via `Integer.parseInt` inside
try/catch, `null` on failure or when ≤ 0.

### Argument resolver

`ClientInfoResolver implements HandlerMethodArgumentResolver` (next to `CurrentUserIdResolver`):
supports parameter type `ClientInfo`, returns `ClientInfo.current(request)`. Register in
`WebConfig.addArgumentResolvers`. Controllers then declare `ClientInfo client` as a parameter.
springdoc must not render it as a request parameter: annotate the parameter with
`@io.swagger.v3.oas.annotations.Parameter(hidden = true)` at each use, **or** (preferred)
register the type as ignored once: `SpringDocUtils.getConfig().addRequestWrapperToIgnore(ClientInfo.class)`
in a static block of `WebConfig`. Verify with `OpenApiSpecTest` that the spec does not change.

## 3. `ClientInfoFilter` — `com.andrewcorp.webwallet.monitoring.ClientInfoFilter`

`OncePerRequestFilter`, registered via `FilterRegistrationBean` for URL pattern `/api/*`,
order `Ordered.HIGHEST_PRECEDENCE + 10` (before the security chain so that 401/429 responses are
logged with the client too).

```java
ClientInfo client = ClientInfo.of(request);
request.setAttribute(ClientInfo.REQUEST_ATTRIBUTE, client);
MDC.put("client", client.kind().name().toLowerCase(Locale.ROOT));
if (client.appVersion() != null) MDC.put("appVersion", client.appVersion().toString());
try { chain.doFilter(request, response); }
finally { MDC.remove("client"); MDC.remove("appVersion"); }
```

Also put the truncated `User-Agent` into MDC key `ua` **only** when `client.nativeApp()` (browser
UAs are noise).

## 4. Logging

`application.properties`:

```properties
# Which client made the request (web / android / ios) and its version, from ClientInfoFilter
logging.pattern.level=%5p [%X{client:-web}%replace( %X{appVersion}){' $', ''}]
```

Keep it simple if the `%replace` trick is fiddly: `logging.pattern.level=%5p [%X{client:-web} %X{appVersion:-}]`.
Check that `mvn spring-boot:run` output shows `INFO [web ]` for web requests and
`INFO [android 1020300]` for a curl with the headers.

## 5. Error alerts

`ErrorAlerts` emails logged errors (`report(source, message, trace)`). In the Logback appender
(`append(ILoggingEvent event)`, ErrorAlerts.java ~line 79) prepend the client to the message when
the MDC has it:

```java
Map<String, String> mdc = event.getMDCPropertyMap();
String message = event.getFormattedMessage();
if (mdc.containsKey("client") && !"web".equals(mdc.get("client"))) {
    message = "[" + mdc.get("client") + " " + mdc.getOrDefault("appVersion", "?") + "] " + message;
}
```

(Web requests stay as today so existing alert subjects do not change.)

## 6. Tests

- `ClientInfoTest` (unit, `common/web`): parses `android`/`ANDROID`/`ios`; unknown → WEB; version
  parsing: `"1020300"` → 1020300, `"abc"`/`"-1"`/`""`/absent → null.
- `ClientInfoFilterTest` (integration): a request with the headers to any endpoint (e.g.
  `GET /api/v1/auth/options`) ends with the request attribute set and MDC cleared afterwards
  (assert `MDC.get("client") == null` after `mockMvc.perform`). Use a tiny test controller or
  a `@SpyBean`-free approach: register a test `OncePerRequestFilter` after it that captures
  `MDC.getCopyOfContextMap()` into a static field.
- `ErrorAlertsTest` (exists? check `src/test/java/com/andrewcorp/webwallet/monitoring`): add a
  case where an error logged with MDC `client=android`, `appVersion=5` produces a mail body
  containing `[android 5]`.
- `OpenApiSpecTest` must pass **without** regenerating the spec (no API change).

## 7. Manual check

```bash
curl -s -H 'X-WebWallet-Client: android' -H 'X-App-Version: 1020300' -H 'User-Agent: WebWallet-Android/1.2.3 (1020300; Android 14)' http://localhost:8080/api/v1/auth/options
```

Server log line for this request shows `[android 1020300]`.

## 8. Done when

- [ ] `ClientInfo`, resolver, filter, logging pattern, alert prefix implemented and covered.
- [ ] `api/openapi.json` unchanged (`git diff --stat api/` empty).
- [ ] README "API conventions": one bullet — "Native apps send `X-WebWallet-Client` and
      `X-App-Version` on every request; see B1 for what changes for them."
