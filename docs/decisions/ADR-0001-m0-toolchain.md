# ADR-0001 — M0 toolchain and deviations from the specification

Date: 2026-10-22. Status: accepted.

## Context

The specification ([02](../02-architecture.md) §1) asks for the latest stable version of every library at the
start of the milestone and names AGP 8.x. At the start of M0 the latest stable versions were Gradle 9.8.2,
AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1 and Compose BOM 2026.09.00. Hilt 2.60 requires AGP 9, the
AGP 9 line comes with built-in Kotlin support, and OkHttp 5.5 requires compiling against API 37.

## Decisions

1. **AGP 9.4.1 with built-in Kotlin, Gradle 9.8.2.** The `org.jetbrains.kotlin.android` plugin is not applied;
   Kotlin options are set on the compile tasks in `build-logic`. The spec's "AGP 8.x" is read as "the current
   AGP line", the rest of the stack (Hilt, KSP, JUnit 5 plugin) only supports AGP 9 anyway.
2. **compileSdk = targetSdk = 37.** Android 17 is stable and OkHttp 5.5 needs API 37; the spec's own rule
   ("bump targetSdk within a month of a new stable Android release") gives 37. minSdk stays 31.
3. **JUnit 6.1 for unit tests** (the JUnit 5 Android plugin 2.0 ships it). The `MainDispatcherExtension`
   replaces the usual rule; JUnit 6 makes `ExtensionContext` non-null.
4. **Cookie parameters are stripped from the contract before generation.** `openapi/openapi.json` stays a
   verbatim copy for the drift check; `core:network` generates from `build/openapi/openapi-without-cookies.json`,
   because the Kotlin generator cannot express `in: cookie` parameters (it emits a syntax error) and the app
   never sends cookies (B1/B2 give it the tokens in the body).
5. **Launcher icon foreground is the Lucide wallet glyph as a vector**, white on the cobalt adaptive background,
   instead of the square PNG of the web logo: the PNG fills its whole square, which Android Lint rejects for an
   adaptive foreground, and the result is the same picture as the web `Logo.tsx`.
6. **Firebase plugins apply only when `google-services.json` exists.** The Firebase project is created outside
   the repository (docs/08 §6); until the two config files are committed the build must stay green.
7. **Module subset.** M0 creates `app`, `build-logic`, `core:common`, `core:model`, `core:network`,
   `core:designsystem` and `core:testing`; the other `core:*`, `feature:*` and `widget` modules of
   [02](../02-architecture.md) §2 are added with the milestone that first needs them, using the same convention
   plugins (`webwallet.android.feature` already exists).
8. **Lint checks that need the network are off** (`NewerVersionAvailable`, `GradleDependency`,
   `AndroidGradlePluginVersion`): with warnings as errors they would break CI whenever a dependency publishes a
   release. Dependabot opens the update pull requests instead.

## Consequences

- Anyone editing the build must know the AGP 9 DSL (`CommonExtension` without type parameters, variant API for
  generated sources, no `kotlin-android`).
- The spec folder of the backend repository carries a copy of this ADR; the spec text itself is left as written
  and this file records where the implementation differs.
