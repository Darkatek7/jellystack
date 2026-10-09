# Tech debt and cleanup backlog

Result of a full repository audit (October 2026). Line numbers refer to commit `b414b17` unless stated otherwise.

- **Priority:** P0 = bug, data loss, privacy or release risk · P1 = blocks maintainability · P2 = cleanup · P3 = cosmetic
- **Effort:** S = under a day · M = a few days · L = a week or more
- ✅ = done on the branch that introduced this file

## Summary

| Area | Verdict |
| --- | --- |
| Correctness | Several real bugs: versionCode reuse, plaintext tokens in auto-backup, download cancellation deletes files, duplicate Cast managers, lost image tags. |
| Tooling | detekt never analyses KMP source sets (718 hidden findings); TV code was not built or tested in CI. |
| Structure | Module split is sound, but UI modules hold business logic in two god files and phone/TV code is copy-pasted. |
| Tests | Not too many: ~85% test real behaviour. Problems are tests that never run, duplicated fakes, the same rule tested in 2–4 layers, and ~34 trivial tests. |
| Repo hygiene | Agent artefacts, stale docs, duplicated build configuration, unused catalog entries. |

---

## 1. Bugs and risks (P0)

| # | Problem | Where | Fix | Effort |
| --- | --- | --- | --- | --- |
| 1.1 | **versionCode 19 used twice under the same applicationId**: TV `0.16.0-tv-beta.1` (commit 4595211) and mobile `0.15.1` (current). Play Console rejects a reused code. `verifyUniqueAndroidVersionCodes` only compares the two *current* values (read via regex), and its rule `tvCode > mobileCode` forces TV to stay ahead forever. | `app-android/build.gradle.kts:131`, `app-tv/build.gradle.kts:53`, `build.gradle.kts` | Check Play Console. Keep codes in one place (e.g. `gradle.properties`) and check against a committed list of published codes, or use disjoint ranges (TV = 1_000_000 + n). | S |
| 1.2 | **Access tokens, API keys and session cookies are stored in plaintext SQLite**, only the Jellyfin password goes to the secure store. The mobile manifest does not disable Auto Backup (TV sets `allowBackup="false"`), so tokens are included in Android cloud/device backups. Contradicts README, `docs/privacy.md` and `docs/play-console-data-safety.md` ("tokens are stored in the platform-protected credential store"). | `shared-database/.../SqlDelightServerStore.kt:21-34`, `app-android/src/main/AndroidManifest.xml:28` | Move secrets into `ServerCredentialVault`/`SecureStore` (with a migration), set `allowBackup="false"` or backup rules excluding the DB, then re-check the data-safety form. | M |
| 1.3 | **detekt does not analyse KMP source sets.** The plain `detekt` task only scans `src/{main,test}/{java,kotlin}`. Running detekt CLI with the repo config on `commonMain/androidMain/iosMain` reports **718 findings** (design 387, shared-core 198, players 91, …: MaxLineLength 185, FunctionNaming 100, LongParameterList 93, LongMethod 70, …), while `src/main` modules report 0. | `build.gradle.kts` (`subprojects { detekt }`) | Set `source.setFrom(...)` to all `src/*/kotlin` folders (or use the per-source-set tasks), generate a baseline, burn it down. | S + L |
| 1.4 | **Leaving the activity breaks downloads.** The download manager lives in composition and is released on dispose (`scope.cancel()`); the `catch (Throwable)` treats cancellation as failure and deletes the partial file and media entry. | `app-android/.../MainActivity.kt:849-857, 661-670`, `shared-core/src/androidMain/.../AndroidOfflineDownloadManager.kt:192-199` | App-scoped Koin single (ideally WorkManager/foreground service); rethrow `CancellationException`. | M |
| 1.5 | **Two `GoogleCastSessionManager` instances on Android**, each with its own session listener and notification manager; snapshot provider bound twice. | `design/.../JellystackRoot.kt:564`, `design/src/androidMain/.../PlatformCastSessionManager.android.kt:27-35`, `MainActivity.kt:836-839, 891-896` | Inject one manager; only create the platform one when none is supplied. | S–M |
| 1.6 | `CastContext.getSharedInstance` unguarded (crash without Play Services); guarded everywhere else. | `MainActivity.kt:835` | Wrap in `runCatching` like the other call sites. | S |
| 1.7 | **Cached items lose logo/art/banner tags**: `JellyfinItemRecord` has the fields, `JellyfinItems.sq` has no columns, `mapItemRecord` never maps them. | `shared-core/.../jellyfin/JellyfinStores.kt:51-56`, `shared-database/.../JellyfinStores.kt:649-720` | Migration `8.sqm` + mapping. | M |
| 1.8 | Database work on the main thread: `ServerRepository` loads servers with `runBlocking` in `init` (first resolved inside a composable); `JellyfinBrowseCoordinator` queries SQLite in its constructor; stores don't switch dispatcher; `PlaybackController.release()` uses `runBlocking`. | `ServerRepository.kt:27-29`, `JellyfinBrowseCoordinator.kt:120`, `PlaybackController.kt:1281` | Async load with `StateFlow`, `withContext(Dispatchers.IO/Default)` in stores. | M |
| 1.9 | `CancellationException` swallowed by broad catches (and ~110 `runCatching`), e.g. the bulk download loop keeps going after cancellation. | `JellystackRoot.kt:1557, 2206, 3302`, `JellyfinQuickConnectCoordinator.kt:100/119/131/185/211`, `ServerConnectivityChecker.kt:76/103/130/158`, `JellyseerrRepository.kt:896/907/924/992/1063`, `JellyfinOfflineProgressSyncer.kt:61` | Shared `catchNonCancellation {}` helper. | M |
| 1.10 | Signing keystore path resolved differently: mobile `file(it)` (module-relative), TV `rootProject.file(it)`; two hand-written properties parsers with different edge cases. | `app-android/build.gradle.kts:104-139`, `app-tv/build.gradle.kts:7-19, 60` | One `java.util.Properties` loader, same path resolution. | S |
| 1.11 | "Generated" API clients were edited by hand; running `:tools:generateApis` deletes the folder and would break `ServerConnectivityChecker`. `JellyfinAuthApi` hardcodes client `Version="0.1"`. | `shared-network/.../generated/**`, `tools/.../GeneratorScaffold.kt:231-346` | Delete `:tools`, move files to `network/jellyfin|jellyseerr` as normal code. | S |
| 1.12 | Playback graph wired 3× with different dependencies: phone omits `clientVersion` (reports `"0.1.0"`), iOS omits the preferences provider. | `MainActivity.kt:858-890`, `app-tv/.../MainActivity.kt:58-74`, `app-ios/.../AppEntry.kt:100-110` | One Koin `playbackModule`. | M |
| 1.13 | Raw Seerr base URL logged (everywhere else uses `sanitizeUrl`). | `JellyseerrRequestsCoordinator.kt:622-624` | `sanitizeUrl(environment.baseUrl)`. | S |
| 1.14 | TV `Application` lacks the uncaught-exception handler the mobile app installs. | `app-tv/.../JellystackTvApplication.kt` vs `app-android/.../JellystackApplication.kt:21-35` | Share the handler. | S |
| 1.15 | `observeActiveServer()` returns a new `combine` per call and is collected without `remember`; `activeServer()` getter writes preferences as a side effect on every recomposition. | `ServerRepository.kt:33-47`, `MainActivity.kt:529-532`, `TvJellystackRoot.kt:286-288`, `JellystackRoot.kt:888-891` | Expose one `StateFlow`; make the getter pure. | S |
| 1.16 | Phone and TV wiring already diverge: TV "play next" re-applies speed/stats, phone doesn't; SyncPlay `playItem` returns on TV but fabricates a 30-field placeholder `JellyfinItem` on phone. | `TvJellystackRoot.kt:264, 350-352`, `MainActivity.kt:546-587, 645-654` | Shared playback wiring (see 3.6). | M |
| 1.17 | iOS player engine, back handler, foreground state and cast are silent no-ops but wired as real implementations. | `players/src/iosMain/.../IosPlayerEngine.kt:7-43`, `design/src/iosMain/...` | Fail loudly or gate the iOS target. | M |

## 2. Build, CI and tooling

| # | Problem | Where | Fix | Prio | Effort |
| --- | --- | --- | --- | --- | --- |
| 2.1 | CI never built or tested TV code (no `:app-tv:assembleDebug`, `:design-tv:testDebugUnitTest`, TV manifest allowlist, version-code check). ✅ added to the build job. Still missing: TV instrumentation shard, `players-cast-google` and `players` androidTest. | `.github/workflows/ci.yml` | Add a TV emulator shard or reduce `design-tv/src/androidTest` to a smoke set. | P1 | S |
| 2.2 | Same config repeated in every module (`compileSdk` ×10, Java 17 ×20, `jvmToolchain` ×9, `minSdk` ×8, iOS triple ×6). | `*/build.gradle.kts` | `build-logic/` convention plugins (android-app, android-lib, kmp-lib). | P1 | M |
| 2.3 | Plugin versions declared twice (settings + catalog); Dependabot only updates the catalog. ✅ removed from `settings.gradle.kts`. Modules still mix `id("…")` and `alias(libs.plugins.…)`. | `settings.gradle.kts`, `app-android`, `shared-database` | Use `alias` everywhere. | P2 | S |
| 2.4 | `static-analysis.yml` duplicates `spotlessCheck detekt` from `ci.yml`. | `.github/workflows/static-analysis.yml` | Delete after removing it from required checks in branch protection. | P2 | S |
| 2.5 | Release builds not shrunk (`isMinifyEnabled = false`), placeholder `proguard-rules.pro`, dead R8 flags in `gradle.properties`, `material-icons-extended` shipped unshrunk. | `app-android:148`, `app-tv:69` | Enable R8 with keep rules, or delete the dead config. | P1 | M |
| 2.6 | Windows workarounds applied to everyone: `org.gradle.workers.max=1` (contradicts `parallel=true`, slows CI), 50-line jar-lock retry, migration verification disabled twice on Windows. | `gradle.properties:2-3`, `shared-core/build.gradle.kts:70-120`, `shared-database:82-93` | Move to the maintainer's `~/.gradle/gradle.properties`; keep one switch. | P2 | S |
| 2.7 | AGP 9 opt-out flags (`builtInKotlin=false`, `newDsl=false`) keep KMP on `com.android.library`; several other flags have no effect; obsolete Kotlin flags. | `gradle.properties` | Plan migration to `com.android.kotlin.multiplatform.library`; delete no-op flags. | P2 | M |
| 2.8 | Dependencies without imports (verify with a build before removing): `koin-compose` (4 modules), `koin-android` in design-tv, app-android `serialization-json`, `androidx-media`, `media3-ui`, `biometric`, `concurrent-futures(-ktx)`, `tracing` (+ duplicated androidTest declarations), app-tv `media3-ui/session`, shared-core ktor logging/content-negotiation, design-screenshots `players`/`sharedCore`. ✅ Voyager removed. | module build files | Remove one by one with a green build. | P2 | S |
| 2.9 | Unused catalog entries. ✅ removed (Voyager ×3, compose-ui-tooling/util, ktor-client-auth, koin-annotations/test, media-kit). Remaining: duplicate Koin version keys, `compose-material-icons` = `compose`, inline `lifecycle-runtime-compose 2.10.0` next to `lifecycle-runtime 2.8.7`, deprecated alpha `security-crypto`. | `gradle/libs.versions.toml` | Consolidate version keys. | P3 | S |
| 2.10 | Copy-pasted resource-parity and manifest-allowlist tasks reading AGP-internal paths. | `app-android:10-97`, `design:10-38`, `app-tv:21-43` | One typed task in build-logic using `SingleArtifact.MERGED_MANIFEST`. | P2 | M |
| 2.11 | Android Lint never runs in CI; `design` sets `abortOnError=false`. | `design/build.gradle.kts:108-111` | Add `lintDebug` with a baseline. | P2 | S |
| 2.12 | iOS targets declared in 6 modules but only built by a manual workflow; `app-ios` has no Xcode project and no `ComposeUIViewController` factory; `iosX64` never used. | `.github/workflows/ios-experimental.yml`, `app-ios` | Weekly schedule, drop `iosX64`, or drop iOS until it is real. | P2 | S |
| 2.13 | `multiDexEnabled` is a no-op at minSdk 24 (and in a library). | `app-android:134`, `design:96` | Delete. | P3 | S |
| 2.14 | Spotless formats Gradle scripts twice (`kotlin` target includes `**/*.kts`); `.editorconfig` lacks `root = true`. | `build.gradle.kts`, `.editorconfig` | Restrict targets; add `root = true`. | P3 | S |
| 2.15 | CI: four hand-written shard entries instead of a matrix, `chmod +x gradlew` and `CI: true` redundant, no AVD caching, CodeQL job without Gradle cache and without TV code. | `.github/workflows/*.yml` | Composite setup action, `matrix.shard`, AVD cache. | P3 | S |
| 2.16 | Dependabot: deprecated `reviewers`, no `groups`. | `.github/dependabot.yml` | Group Kotlin/Compose/AGP. | P3 | S |
| 2.18 | The CI TV emulator (API 35, `google_apis`, `tv_1080p`) segfaults at random while rendering `TvCinematicBrowse`, also on code that predates the TV instrumentation job. Reproduced locally with 1080p and 720p profiles and with `swiftshader_indirect` and `guest` GPU modes; isolated theme, stage, card, and image-loader renders do not crash. TV instrumentation is therefore non-blocking in CI. | `.github/workflows/ci.yml` (`tv_instrumentation`) | Try an `android-tv` system image or a newer emulator, then make the TV shards required again. Until then run `:design-tv:connectedDebugAndroidTest` and `:app-tv:connectedDebugAndroidTest` locally before TV releases. | P1 | M |
| 2.17 | Root build cruft. ✅ removed unused `group`/`version` and `printProjectStructure` (= `./gradlew projects`); ✅ `iInclude` helper and discontinued JetBrains Space repos removed. | `build.gradle.kts`, `settings.gradle.kts` | – | P3 | S |

## 3. Architecture and structure

| # | Problem | Where | Fix | Prio | Effort |
| --- | --- | --- | --- | --- | --- |
| 3.1 | **`JellystackRoot()` is a 3,004-line composable** with 49 `mutableStateOf`, 37 `LaunchedEffect`, 17 `koin.get`, its own `CoroutineScope`, coordinators built in `remember`, network calls in effects, and ~20 hoisted `stringResource`s just for error text. No state-holder/ViewModel layer exists. | `design/.../JellystackRoot.kt:539-3543` | Split per the table below; state holders expose `StateFlow<UiState>` and message codes. | P1 | L |
| 3.2 | **`JellyfinBrowseScreen.kt` is 5,126 lines**, ~1,350 of them dead (see 5.1/5.2). | `design/.../jellyfin/JellyfinBrowseScreen.kt` | Delete dead code, then split per the table below. | P1 | M |
| 3.3 | `PlaybackController` god class: 1,930 lines, 33 mutable fields, three copy-pasted HLS switch pipelines (subtitle/audio/quality), four boolean fallback flags, all 13 constructor params default to no-ops, no interface (tests reflect into `_state`). | `players/.../PlaybackController.kt` | Extract `CastHandoffController`, generic `TrackSwitcher`, `PlaybackFallbackPolicy`, `ProgressPersister`; add an interface. | P1 | L |
| 3.4 | Koin used as service locator: 47 `koin.get` in composables/activities; only repositories registered; coordinators, `PlaybackController`, HttpClients built by hand. | `shared-core/.../di/Modules.kt`, `JellystackRoot`, `TvJellystackRoot`, both `MainActivity` | `factory`/`viewModel` definitions + `koinInject`. | P1 | M–L |
| 3.5 | 13 `NetworkClientFactory.create` call sites (up to three clients per authenticator call; UI creates segment clients). | `JellyseerrAuthenticator.kt:19/35/186`, `JellyfinPlaybackInfoService.kt:26`, `SyncPlayCoordinator.kt:70`, `MainActivity.kt:603`, `TvJellystackRoot.kt:315` | Koin `single<HttpClient>` + cookie variant. | P1 | M |
| 3.6 | **Phone/TV copy-paste**: coordinator lifecycle file identical apart from names; segment prompt coordinators near-identical (tested 4×); `selectNextEpisode` = `selectNextTvEpisode`; autoplay prompt models; spotlight/carousel selection; track-label formatting ×3; image-URL builders ×3. | `app-android/.../playback/*`, `design-tv/.../TvPlayback*.kt`, `TvAutoplayCoordinator.kt`, `TvComponents.kt:610-627`, `PlaybackOptionsSheet.kt:335` | Move to `players` (androidMain/commonMain) or `shared-core`, test once. | P1 | M |
| 3.7 | Three string systems: Compose resources (507), app `strings.xml` (87), hand-rolled `TvStrings` data class (~250 fields, `Locale.getDefault()`; its constructor is at the JVM limit of 255 parameter slots, so new TV strings go into nested groups such as `TvDurationStrings`); 85 TV strings already exist verbatim elsewhere; TV strings have no parity check. | `design-tv/.../TvStrings.kt` | TV → Android resources or a shared strings module, with parity check. | P2 | M |
| 3.8 | Four parallel item models (`JellyfinItemDto`, `JellyfinItemRecord`, `JellyfinItem`, `OfflineMediaMetadata`) with mappers living in UI files; ~63 string comparisons of item types. | `JellystackRoot.kt:4062, 4605`, `JellyfinBrowseScreen.kt:2097` | Mappers into `shared-core`; `JellyfinItemKind` enum. | P2 | M |
| 3.9 | Download orchestration in UI code; series/season download paths copy-pasted. | `JellystackRoot.kt:1482-1684, 4554-4667` | `DownloadRequestFactory` + orchestrator in `shared-core`. | P2 | M |
| 3.10 | `MainActivity` (965 lines) builds SyncPlay, coordinators, an HttpClient and the whole playback graph during composition; biometric hooks in five lifecycle callbacks; `initCastContext` called 3×; 7 `isInitialized` checks. | `app-android/.../MainActivity.kt` | Lifecycle observers, `ProcessLifecycleOwner`, Koin playback module. | P2 | M |
| 3.11 | `JellyseerrRepository` (1,488 lines): ~700 lines of DTO mappers, 16 copy-pasted log-and-rethrow blocks, unguarded caches. `JellyseerrApi.kt` holds the API plus 48 DTOs. | `shared-core/.../JellyseerrRepository.kt`, `shared-network/.../JellyseerrApi.kt` | `JellyseerrMappers.kt`, `JellyseerrDtos.kt`, `logFailure {}`. | P2 | M |
| 3.12 | Composables with 23–41 parameters. | `ImmersiveMediaDetailContent.kt:183`, `JellyfinBrowseScreen` | State object + actions interface. | P2 | M |
| 3.13 | Phone and TV construct the same coordinators with different lifecycles (`autoStart=false` + manual flags vs side effects in `remember`). | `JellystackRoot.kt:837-1005`, `TvJellystackRoot.kt:176-221` | One construction path via DI. | P2 | M |
| 3.14 | Database Koin module copied 3×; `DriverFactory`/`DatabaseProvider` expect/actual is dead (Android actual throws). | `AndroidAppModule.kt`, `TvAppModule.kt`, `IosAppModule.kt`, `shared-database/.../DatabaseProvider.kt` | `databaseModule(driver)` in shared-database. | P2 | S |
| 3.15 | Six copy-pasted 34-column projection lambdas. | `shared-database/.../JellyfinStores.kt:78-580` | Select `jellyfin_items.*` and reuse `toRecord()`. | P2 | M |
| 3.16 | Two logging facades (`JellystackLog` 72 calls, Napier directly 41); `JellystackLog` is an unnecessary expect/actual over Napier. | `shared-core/.../logging/` | One facade. | P3 | S |
| 3.17 | Android and iOS download managers duplicate queue/restore/status logic. | `AndroidOfflineDownloadManager.kt:343-432`, `IosOfflineDownloadManager.kt` | Common queue + platform transfer engine. | P3 | M |

Proposed split of `JellystackRoot.kt`:

| Lines | Content | New home |
| --- | --- | --- |
| 366-535 | UI state types, `ServerFormState` + validation, `shellTitle` | `detail/DetailUiState.kt`, `servers/ServerFormState.kt`, `shell/ShellTitle.kt` |
| 700-830, 2246-2420 | Detail route stacks, back dispatch, library pop | `shell/ShellNavigator.kt` (pure reducer) |
| 826-1006 | Coordinator construction and start flags | Koin factories / state holders |
| 1007-1221 | Tutorial and What's New | `onboarding/OnboardingController.kt` |
| 1125-1300, 1758-2000, 3826-4092 | Trailer, enrichment, `loadDetail`, `DetailContent` | `detail/DetailController.kt`, `detail/DetailContent.kt` |
| 1300-1470 | Track preferences, resume dialog, `launchPlayback` | `playback/PlaybackLauncher.kt` |
| 1470-1700, 4554-4667 | Download orchestration | `shared-core` `DownloadRequestFactory` |
| 1700-2245, 4177-4535 | Server dialog, connect/Quick Connect, `AddServerDialog` | `servers/ServerSetupController.kt`, `servers/AddServerDialog.kt` |
| 2422-3543 | 1,100-line UI tree | `RootScaffold.kt` + per-destination hosts |
| 3578-3825 | Library/Home/Welcome content, `WhatsNewDialog` | `home/HomeHost.kt` |
| 4093-4175, 4668-4872 | Biometric overlay, Cast banner | `security/`, `cast/CastStatusBanner.kt` |

Proposed split of `JellyfinBrowseScreen.kt` (after deleting dead code):

| Lines | Content | New home |
| --- | --- | --- |
| 277-1440 | Screen, search, landing header, rails | `jellyfin/library/LibraryScreen.kt`, `LibraryLanding.kt` |
| 1444-1811, 2337-2455 | Poster/landscape cards | `jellyfin/cards/` |
| 1812-2662 | Continue Watching / Next Up / Recently Added | `jellyfin/home/HomeSections.kt` |
| 2663-3002 | Badges, `SectionHeader`, `HomeSpotlightCard` | `jellyfin/home/HomeSpotlightCard.kt` |
| 3003-3349 | Skeletons | `jellyfin/JellyfinSkeletons.kt` |
| 3350-3571, 4911-4977 | Series grouping, season selection (pure logic) | `shared-core` `SeriesGrouping.kt` |
| 3479-3533, 5097-5126 | Track labels, `formatBytes`, ticks | shared `MediaFormatters.kt` |
| 3750-3930 | `PosterImage`, image URL builder | `jellyfin/image/` + `shared-core` `JellyfinImageUrls` |
| 4673-5096 | `SeasonEpisodeSelector`, `EpisodeCard` | `jellyfin/detail/` |

## 4. Tests

**Do we need this many tests?** Mostly yes. 145 files, ~33k lines, 880 tests against ~62k lines of production code (ratio 0.53) is normal for an app with this much concurrency, focus and playback logic, and roughly 85% of the tests check real behaviour. The suite is inefficient, not oversized.

| # | Problem | Fix | Prio | Effort |
| --- | --- | --- | --- | --- |
| 4.1 | ~5.5k test lines never ran in CI: `design-tv/src/test` (2.9k), `design-tv/src/androidTest` (2.5k), `app-tv`, `players` and `players-cast-google` androidTest (the last two probably don't even compile: missing runner / `kotlin("test")`). ✅ `design-tv` unit tests added to CI. | TV emulator shard or smoke set; move `AndroidOfflinePlaybackSourceResolverTest` and `CastPlayerTest` to unit tests. | P1 | S |
| 4.2 | Trivial tests (constants, copy text, fakes, one-line expressions, compile-only). ✅ deleted 14 files + 3 test cases: `PlatformTest`, `AppPlatformCapabilitiesTest`, `DatabaseProviderTest`, `ResourceBackedCopyTest`, `JellystackPreviewFixtureTest`, `HomeLoadingStateTest`, `PlayedStatusSupportTest`, `HomeSectionCardLayoutTest`, `CastSessionManagerFakeTest`, `TvStringsTest`, `TvPremiumLayoutTest` (+ its test-only production helpers), `TvConnectionLayoutTest`, `TvConnectionContentModeTest`, `ComposeEntryBridgeTest`, the constants case in `JellystackVisualTokensTest`, `televisionCapabilitiesExcludeMobileOnlyFeatures`, `whatsNewShowsCanonical0151Highlights` (broke on every release). | – | P2 | ✅ |
| 4.3 | **The `:testing` module is dead** (JVM-only, no dependants), so fakes are copy-pasted: in-memory `Settings` ×4 (~400 lines), `JellyfinItem(...)` builders in 22 files (~700), stores/providers/clocks/`respondJson` (~350), Android/TV review helpers (~100). The two browse-store fakes have already diverged. | Make `:testing` a KMP (Android/iOS/JVM) fixtures module used as `commonTest` dependency; use `MapSettings` from `multiplatform-settings-test`; move `CastSessionManagerFake` there. Saves ~1.7k lines. | P1 | M |
| 4.4 | Same rule tested in 2–5 layers: segment skip button (4×), "all five segment settings dispatch" (5×), retry kind after page error (4×), Seerr request permissions/status (unit + instrumented), spotlight selection (unit + 7 instrumented cases), plain-HTTP warning (3×), layout geometry asserted in `SettingsOnboardingUiTest` although screenshots cover it. | Keep the unit test, delete the copies; fix production duplication first (3.6). ~1.1–1.3k lines. | P2 | M |
| 4.5 | Boilerplate: `PlaybackControllerTest` (3,518 lines) has 65 copies of the controller setup and 59 try/finally blocks; `JellyfinBrowseCoordinatorTest` carries 604 lines of fakes. | `withController {}` harness, table tests, shared fakes. ~1.1k lines. | P2 | M |
| 4.6 | Flaky/brittle patterns: coordinator on real `Dispatchers.Default` inside `runTest` with virtual `delay(100)` (stale-result tests can pass with the bug present); log-line assertions via a global singleton (`JellystackLogBuffer`); `runBlocking` + endless `yield()` loop in `SpotlightAutoAdvanceTest`; `SystemClock.sleep` and pixel-brightness checks in `MainActivityTest`/`TvComponentsTest`. | Inject test dispatchers, drop log assertions, use screenshots for visuals. | P2 | S–M |
| 4.7 | Large instrumented suites: `LibraryAndMediaUiTest` 2,545 lines, `TvHomeScreenTest` 1,209, `AndroidPlaybackSegmentsReviewTest` 715 (mirror of the TV one). Five emulator jobs per PR. | Split, then shrink to device-only behaviour (~2.5k lines design, ~0.8k app). | P2 | M |
| 4.8 | Test-only code in production source sets: `CastSessionManagerFake` (players commonMain), `setLibraryItemsForTest`, `MainActivity.playbackEnvironment`, `JellystackLogMonitor`, preview fixtures (~610 lines), default store methods that exist only for fakes. | Move into the fixtures module (4.3). | P2 | S–M |
| 4.9 | No TV screenshot tests. | Small TV screenshot set; replaces geometry assertions. | P3 | S |
| 4.10 | Style drift: 12 backtick test names vs 868 camelCase (mixed within `CastPermissionPolicyTest`). | camelCase everywhere. | P3 | S |

Estimated total: ~4.7k test lines (−14%) removable without losing meaningful coverage, another ~1.9k by reducing the TV instrumented suite to a smoke set.

Recommended strategy: unit tests are the main layer; JSON parsing is tested once at the `shared-network` contract level; instrumented tests only for device-only behaviour; visuals via screenshot tests; one shared fixtures module. The testing policy in `AGENTS.md` encodes this.

## 5. Dead code

| # | What | Where | Prio | Effort |
| --- | --- | --- | --- | --- |
| 5.1 | Legacy `JellyfinDetailContent` (~620 lines, 37 params) – only tests and screenshots render it; production uses `ImmersiveMediaDetailContent`. Tests therefore exercise a screen users never see. | `JellyfinBrowseScreen.kt:4046-4672`, `preview/JellystackPreviewFixture.kt:187`, `LibraryAndMediaUiTest.kt:1838-1938` | P1 | M |
| 5.2 | ~750 lines of unused composables (`ContinueWatchingCard`, `NextUpCard`, `MediaCardMetadata`, `SeriesPosterCard`, `MoviePosterCard`, `TvSeriesCard`, `LibraryItemRow`, `AudioTrackDropdown`, `SubtitleTrackDropdown`). | `JellyfinBrowseScreen.kt` | P2 | S |
| 5.3 | ~560 lines unused in `JellyseerrMediaScreen.kt` (`MediaTabsSkeleton`, `StatusChip`, `RecommendationDetailSheet` and its helpers, a private `PlaceholderLine` shadowing the public one). | `design/.../jellyseerr/JellyseerrMediaScreen.kt` | P2 | S |
| 5.4 | Unused in `JellystackRoot.kt`: `CastToolbarAction`, `JellystackTags`, `itemOrNull`, `ServerManagementUiState` + `serverUiState`, unused `jellyseerrAuthenticator` lookup, `remoteProgress` (hidden via `@Suppress`), parameters no caller passes. | `JellystackRoot.kt` | P2 | S |
| 5.5 | Sonarr/Radarr support: enum values, generated clients, connectivity branches, UI branches – but nothing can create such a server. Remove it, with an `8.sqm` that deletes stored rows first (enum parsing would throw otherwise). | `ServerType.kt:5-6`, `shared-network/.../generated/{sonarr,radarr}`, `ServerConnectivityChecker.kt`, `ServerRepository.kt:317-324`, `SettingsScreen.kt:1479`, `JellystackRoot.kt:3207, 3295` | P2 | M |
| 5.6 | Telemetry pipeline whose only binding is `NoOp`, yet impressions build payload maps and four callbacks are threaded through screens. | `Modules.kt:56`, `JellystackRoot.kt:1234-1289` | P2 | S |
| 5.7 | `ServerConfigRepository` (registered, never injected), `ThemePreferenceRepository.currentTheme/setDarkTheme`, deprecated `submitRequest` overload, `JellystackLogMonitor` mutators, 43 unused Compose strings per language, 6 unused app strings, unused `StdoutLogger` and 17 redundant `installLogging = false`. | various | P3 | S |
| 5.8 | `:tools` generator (361 lines generating 259 lines) and the `:testing` module. | `tools/`, `testing/` | P2 | S |

## 6. Naming and consistency

| # | Problem | Fix | Prio | Effort |
| --- | --- | --- | --- | --- |
| 6.1 | "Jellyseerr" (~1,780 occurrences) vs "Seerr" (~400), mixed even in one function (`ServerType.JELLYSEERR` vs `ServerFormType.SEERR`). | Mechanical rename to Seerr; keep wire-level identifiers. | P3 | L |
| 6.2 | Packages/source roots: `app.jellystack.*` in `src/main/java` vs `dev.jellystack.*`; `players-cast-google` namespace `…cast.google` but package `dev.jellystack.players.cast`; deprecated `players/src/androidTest` folder name; `ic_launcher-playstore.png` outside `res/`. | Align when touching the modules. | P3 | S |
| 6.3 | Misleading prefixes: `Tv*` in the mobile module means "TV series"; `TvPlaybackDecoderPolicy` in players is used by the phone; 18 `Android*` types exist only to differ from TV copies; `TutorialNavigation.kt` holds onboarding form logic; `TvAutoplayCoordinator.kt` contains no coordinator. | Rename with the dedup in 3.6. | P3 | S |
| 6.4 | 73 `@Suppress`, 41 of them `FunctionName` (detekt's rule is `FunctionNaming`; ktlint already ignores `@Composable`), plus stale ones (`UNUSED_PARAMETER`, `MaxLineLength`). Eight files carry blanket file-level complexity suppressions. | `naming.FunctionNaming.ignoreAnnotated: ['Composable']` in `detekt.yml`, then delete suppressions. | P2 | S |
| 6.5 | Magic numbers: `MagicNumber` disabled globally; 103 `RoundedCornerShape(N.dp)` with 12 radii despite token objects; ~110 inline `fontSize` (24 sizes) in design-tv, now migrating file by file to `TvTextSize`/`TvShapes` (done: `TvComponents.kt`); stale user agent `JellystackMobile/0.1` and three User-Agent formats; `TICKS_PER_MILLISECOND` declared 3×. | Tokens + one constants file. | P3 | S–M |
| 6.6 | Hardcoded English in shipped UI (resume dialog, welcome screen, server form labels, Cast content descriptions, TV settings); settings search keywords mix EN/DE; seek button labels claim fixed 10 s/30 s although the interval is a setting. | Move to resources. | P2 | S |
| 6.7 | Duplicate formatters with different output (`formatBytes` "1 GB" vs "1.4 GB", `formatScore`/`formatPercent` ×2). | One formatter. | P3 | S |
| 6.8 | 12 avoidable `!!`. | `JellyfinBrowseScreen.kt:2090-2092, 3716, 4366`, `TvDetailScreens.kt:914`, … | P3 | S |

## 7. Repository hygiene and docs

| # | Problem | Fix | Prio | Effort |
| --- | --- | --- | --- | --- |
| 7.1 | Agent artefacts committed: `design-qa.md` (contained absolute personal paths), `docs/superpowers/plans/…`. ✅ deleted. | – | P2 | ✅ |
| 7.2 | `local.integration.properties.example` read by nothing. ✅ deleted. | – | P3 | ✅ |
| 7.3 | No agent guide; docs disagreed with each other and with CI (four different command lists); `architecture.md` omitted four modules. ✅ `AGENTS.md`/`CLAUDE.md` added, `building.md` is the single command reference, `architecture.md` rewritten, README/CONTRIBUTING/PR template point to them. | – | P2 | ✅ |
| 7.4 | `.gitignore` duplicates and missing TV AAB rule. ✅ cleaned (`*.aab`, `*.apk`). Personal entries (`/Handle/`, `/adb.exe`, `castlog*.txt`, …) still belong in `.git/info/exclude`. | Move locally, then drop from `.gitignore`. | P3 | S |
| 7.5 | `privacy-policy-de` is only a stub pointing to `docs/privacy-de.md`, but the app and README link to the stub. | Link `docs/privacy-de.md` directly in the next release; keep the stub for shipped builds. | P3 | S |
| 7.6 | `RELEASE_NOTES.md` mixes mobile and TV entries out of order and duplicates `docs/releases/*`. | One source, generate store/Discord texts from it. | P3 | S |
| 7.7 | store-assets: byte-identical duplicates (`store-assets/tv/amazon-*` vs `amazon/0.16.0-tv-beta.3/fire-tv/`), a 35 KB `LICENSE` copy, per-version folders; `scripts/generate_amazon_store_assets.py` hardcodes beta 3 and reads untracked `build/` files. | Unversioned `store-assets/{google-play,amazon}/`, parameterised script. | P3 | S |
| 7.8 | `docs/android-tv.md`: PowerShell-only commands, hardcoded beta/versionCode, beta-3 asset paths, outdated "first beta" note, manual GMS-free check. `permissions.md` hardcodes "0.15.1"; issue template placeholder "0.14.2" and no TV device field; `FUNDING.yml` is the full GitHub template. | Remove version numbers from docs; automate the GMS check. | P3 | S |
| 7.9 | Third-party notices maintained by hand in two places (`THIRD_PARTY_NOTICES.md`, `generateThirdPartyReport`). ✅ Voyager removed from both. | `app.cash.licensee` plugin. | P3 | S |
| 7.10 | `rootProject.name = "jellystack-mobile"` although the repo contains TV and iOS. Renaming changes the generated Compose `Res` package (`jellystack_mobile.*`) and every import. | Only together with an explicit `compose.resources { packageOfResClass = … }`. | P3 | S |

## Suggested order

1. P0 items 1.1–1.7 (release, privacy, data loss, crash).
2. Enable detekt for KMP with a baseline (1.3) and get TV into CI fully (2.1, 4.1).
3. Shared fixtures module and test dedup (4.3–4.5) – makes the following refactors cheaper.
4. Delete dead code (section 5) – shrinks the god files by ~2k lines for free.
5. State holders + Koin wiring, then split `JellystackRoot.kt` / `JellyfinBrowseScreen.kt` (3.1–3.5).
6. Phone/TV dedup (3.6, 3.7), convention plugins (2.2), the rest opportunistically.
