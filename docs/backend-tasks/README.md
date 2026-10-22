# Backend tasks for the Android client — batch 1 (M1 prerequisites)

Implementation-ready tickets for the WebWallet backend (`D:\Dev\WebWallet`, branch `dev`).
They unblock milestone M1 of the Android app (see [../01-scope-and-milestones.md](../01-scope-and-milestones.md)
and [../07-backend-changes.md](../07-backend-changes.md)). Written against commit `1ff6f42`
(backend 1.0.1, migrations up to `V32`).

| Ticket | Title | Depends on | PR |
|---|---|---|---|
| [B6](B6-client-headers.md) | Client identification headers (`ClientInfo`, MDC, error alerts) | — | PR 1 |
| [B8](B8-app-config.md) | `GET /api/v1/app/config`: minimum version and feature flags | — | PR 1 |
| [B1](B1-refresh-token-body.md) | Refresh token in the body for native clients | B6 (`ClientInfo`) | PR 2 |
| [B2](B2-trusted-device-body.md) | Trusted-device token in the body for native clients | B1 | PR 2 |

Do B6 + B8 in one PR, then B1 + B2 in one PR (B2 touches the same controllers and DTOs as B1
and is small; separate PRs would conflict).

## Rules that apply to every ticket

Repository conventions, all enforced by CI (`mvn spotless:check`, `mvn clean verify`, JaCoCo ≥ 75 %):

1. **Formatting:** palantir-java-format via Spotless. Run `mvn spotless:apply` before committing.
2. **Errors:** throw `ApiException(ErrorCode, …)`; the handler renders RFC 9457 ProblemDetail with
   `code`. New codes go into `ErrorCode` with a status and messages in `messages.properties`
   and `messages_uk.properties`.
3. **OpenAPI:** `api/openapi.json` is committed and checked by `OpenApiSpecTest`. After any API
   change run `mvn test -Dtest=OpenApiSpecTest -Dopenapi.update=true` and commit the file.
   `typesAreExactForGeneratedClients` requires every nullable response field to carry
   `@Schema(nullable = true)` and every field to be present (`required`). Document endpoints
   with `@Operation(summary, description)` like the neighbours.
4. **Web client compatibility:** the React client generates types from the same spec. Only add
   fields to responses (nullable) and only add optional fields to requests. Never rename a
   schema or change a type. After merging, the web client repo needs `npm run api` to refresh
   `src/api/schema.d.ts` (its CI has a drift check).
5. **Tests:** integration tests extend `AbstractIntegrationTest` (MockMvc + Testcontainers
   Postgres). Helpers: `postJson`, `putJson`, `json(request, status)`, `createUser`,
   `createVerifiedUser`, `login`, `loginResponse`, `tokens`, `refreshCookie`, `refresh(cookie)`,
   `bearer`, `lastMail`. Follow `AuthControllerTest` for style; one behaviour per test method,
   method names are sentences.
6. **Javadoc:** every new class and non-trivial public method gets a short Javadoc in the
   repo's voice (what and why, no restating the signature).
7. **Properties:** new settings go to `AppProperties` (a validated `@ConfigurationProperties`
   record) and `application.properties` with `${ENV_VAR:default}`; document env vars in
   `.env.example` and README "Getting started".
8. **README:** the "API conventions" section (README.md ~line 116) describes the token model;
   update it when B1/B2 change it.
9. **Commits:** one logical change per commit; message in the imperative, as in `git log`.

## Shared piece: `ClientInfo`

B6 introduces `com.andrewcorp.webwallet.common.web.ClientInfo` and B1/B2/B8 use it. If B1 is
started before B6 is merged, implement `ClientInfo` exactly as B6 §2 describes it and let the
rest of B6 follow.

## Verification checklist for the whole batch

- [ ] `mvn clean verify` green locally and in CI; coverage gate passes.
- [ ] `api/openapi.json` regenerated and committed in the same PR as the code change.
- [ ] Web client: `npm run api && npm run typecheck && npm test` pass against the new spec
      (run from `D:\Dev\WebWalletClient` with `OPENAPI_SOURCE` pointing at the local backend
      or the raw file); no behaviour change for the web.
- [ ] Manual smoke with curl (examples in each ticket) against `docker compose --profile app up`.
- [ ] `docs/deployment.md` and `.env.example` updated for new env vars (B8).
