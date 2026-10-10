# AGENTS.md

Guidance for coding agents (and humans) working in this repository. Keep it short and current; details live in `docs/`.

## Project

Jellystack is a privacy-focused Jellyfin + Seerr client written in Kotlin Multiplatform with Compose.

- **Android phone/tablet** (`app-android`) – stable, published on Google Play.
- **Android TV / Google TV / Fire TV** (`app-tv`) – GMS-free, separate Compose for TV UI.
- **iOS** (`app-ios`) – experimental, not distributed, only built by a manual workflow.

No Jellystack backend exists: the app talks only to servers the user configures.

## Module map

| Module | What goes here |
| --- | --- |
| `shared-network` | Ktor clients and transport DTOs for Jellyfin and Seerr. No UI or persistence. |
| `shared-core` | Repositories, coordinators, settings, auth, secure storage, downloads, domain models, Koin core module (`di/Modules.kt`). |
| `shared-database` | SQLDelight schema (`src/commonMain/sqldelight`), numbered migrations (`migrations/N.sqm`), SQL store implementations. |
| `players` | Playback resolution, `PlaybackController`, segments, continuation, SyncPlay, Cast abstraction. Shared by phone and TV. |
| `players-cast-google` | Google Cast implementation. Mobile only; must never reach the TV app. |
| `design` | Phone/tablet Compose UI and Compose resources. Composition root: `JellystackRoot.kt`. |
| `design-tv` | Compose for TV UI. Composition root: `TvJellystackRoot.kt`. Does not depend on `design`. |
| `design-screenshots` | Screenshot tests for `design` with checked-in reference PNGs. |
| `design-tv-screenshots` | Screenshot tests for the production `design-tv` components (fixtures in `TvGoldenFixtures.kt`). |
| `app-android`, `app-tv`, `app-ios` | Thin platform hosts: Activity/lifecycle, permissions, platform Koin module, packaging. |
| `testing`, `tools` | JVM-only leftovers (unused fixtures module, API generator scaffold). Do not build on them without reading `docs/tech-debt.md`. |

Dependency direction: `app-* → design / design-tv → players → shared-core → shared-network`; `shared-database → shared-core`. Code needed by both phone and TV UI belongs in `players` or `shared-core`, not in a UI module. See `docs/architecture.md`.

Naming quirks to respect (not to "fix" in passing): library packages are `dev.jellystack.*`, app packages are `app.jellystack.mobile` / `app.jellystack.tv` with Kotlin under `src/main/java`. KMP modules use `src/commonMain|androidMain|iosMain`; Android-only modules use `src/main/kotlin`.

## Commands

JDK 17, Android SDK 36. Always run Gradle from the repository root.

```bash
./gradlew :app-android:assembleDebug          # phone build
./gradlew :app-tv:assembleDebug               # TV build
./gradlew spotlessApply                       # format before committing
./gradlew spotlessCheck detekt                # static checks
./gradlew :<module>:testDebugUnitTest         # unit tests of one module
./gradlew :shared-network:jvmTest             # shared-network uses its JVM target for tests
./gradlew :design-screenshots:updateDebugScreenshotTest   # after intended visual changes
./gradlew :design-tv-screenshots:updateDebugScreenshotTest   # same for TV UI
```

The full CI gate is listed in `docs/building.md` and mirrors `.github/workflows/ci.yml`. Before finishing a change, run `spotlessCheck detekt` plus the unit tests of every module you touched; for UI changes also validate screenshots.

## Architecture rules

- Jellyfin is authoritative for library identity, playback, progress, favourites, watched state, streams, and availability. Seerr only enriches discovery and requests and must never block Jellyfin playback.
- Transport DTOs from `shared-network` do not reach UI state; map them in `shared-core`.
- Prefer common code. Platform behaviour goes behind a common interface or `AppPlatformCapabilities`.
- Resolve dependencies through Koin. Do not hand-construct repositories, coordinators, or `HttpClient`s inside composables or activities.
- **Do not grow the god files** `design/.../JellystackRoot.kt`, `design/.../jellyfin/JellyfinBrowseScreen.kt`, and `players/.../PlaybackController.kt`. Put new state logic in a separate state holder/coordinator and new composables in their own files; extracting code out of these files is welcome.
- Do not duplicate phone logic into `design-tv` (or the reverse). If both need it, move it to `players` or `shared-core` first.
- Schema changes need a new `shared-database/.../migrations/N.sqm`; never edit an existing migration.

## Code conventions

- Follow ktlint (`.editorconfig`) and detekt (`config/detekt/detekt.yml`). Fix findings instead of adding `@Suppress`; never add file-level suppressions for complexity rules.
- Coroutines: never swallow `CancellationException` – rethrow it before catching `Throwable`/`Exception`, and avoid `runCatching` around suspending calls. No `runBlocking` or `GlobalScope` in production code.
- Logging: use `JellystackLog` and pass URLs/identifiers through `sanitizeUrl` / `sanitizeIdentifier`. Never log tokens, passwords, cookies, or full server URLs.
- Strings: every user-visible string exists in English **and** German – `design/src/commonMain/composeResources/values{,-de}/strings.xml` for phone UI, `app-*/src/main/res/values{,-de}/strings.xml` for app hosts, `design-tv/.../TvStrings.kt` for TV. No hardcoded UI text.
- Use "Seerr" in new names and UI copy. Existing `Jellyseerr*` types stay until a dedicated rename; wire-level identifiers keep their server names.
- No new analytics, telemetry, remote logging, or external services without prior design discussion.

## Testing policy

Write tests for behaviour that can break: coordinators, reducers, resolvers, request/permission rules, focus logic, migrations, HTTP contracts (MockEngine).

Do **not** add tests that:

- assert constants, dimensions, colours, enum names, or copy text,
- test a fake or a one-line expression,
- recompute layout arithmetic (use screenshot tests for visuals),
- duplicate a unit-tested rule again in an instrumented test.

Instrumented tests (`androidTest` / `androidInstrumentedTest`) are only for device-only behaviour: D-pad focus, accessibility, system bars, rotation/recreation, permission flows, and a few end-to-end wiring smoke tests.

Style: camelCase test names, `runTest` for coroutines, no real dispatchers or `delay()` for synchronisation, reuse existing fakes in the module (`shared-core/.../testing/InMemorySettings.kt`, `security/FakeSecureStore.kt`) instead of writing new copies.

## Privacy, security, test data

- Use only fictional data and reserved domains (`example.com`, `example.org`, `example.invalid`). Never commit real server URLs, tokens, cookies, logs, UI dumps, or personal media.
- Request Android permissions only at the feature trigger and document them in `docs/permissions.md`. The TV manifest allowlist is enforced by `:app-tv:verifyTvReleaseManifestPermissions`.
- Never commit `local.properties`, `keystore.properties`, keystores, APKs, or AABs.
- Do not commit agent artefacts (plans, QA logs, scratch captures) or absolute local paths.

## Releases

- Mobile and TV share `applicationId = app.jellystack.mobile`, so **a `versionCode` may never be reused across either app** – check git history of both `app-*/build.gradle.kts` before bumping.
- Release notes: `RELEASE_NOTES.md` plus store/Discord texts in `docs/releases/`.
- GitHub releases contain source and notes only; store bundles stay local.

## Known pitfalls

- The plain `detekt` task currently analyses only `src/main|test` folders, not KMP source sets (`commonMain`, …). A clean `detekt` run does not mean KMP code is clean.
- `gradle.properties` sets `org.gradle.workers.max=1` for Windows file-locking; builds are slower than necessary on Linux/macOS.
- `rootProject.name` determines the generated Compose resources package (`jellystack_mobile.design.generated.resources`); renaming it breaks every resource import.
- `design-tv` and `app-tv` instrumented tests run in CI on a TV emulator but are non-blocking, because that emulator crashes at random (`docs/tech-debt.md` 2.18). Run them on a TV emulator or device locally when changing TV focus behaviour and before every TV release.

## More

- `docs/architecture.md` – modules and rules
- `docs/building.md` – all commands
- `docs/android-tv.md` – TV builds, devices, store submission
- `docs/tech-debt.md` – known problems and the cleanup backlog; check it before large refactors
