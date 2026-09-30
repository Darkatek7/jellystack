# Jellystack TV Release 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Library, populated Search, and Discover faster to browse with stable cinematic rows, an adaptive All Titles grid, exact provider deduplication, visible item actions, and capability-gated external voice search.

**Architecture:** `LibraryBrowseQuery` is a pure profile/library-scoped value translated at the network edge; only the canonical default page may update the persistent library cache. A shared cinematic browse shell consumes immutable rows/cards and a cancellable dwell-based backdrop controller. `TvSearchCoordinator` owns query/source/mode/results/voice state and reconciles Jellyfin/Seerr items by `MediaIdentity`; `app-tv` supplies the external recognizer capability without microphone permission.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines/StateFlow, SQLDelight, Multiplatform Settings, Ktor Jellyfin APIs, Jetpack Compose for TV, Android Activity Result API, Compose UI tests, Preview Screenshot tests, Macrobenchmark/FrameMetrics validation.

## Global Constraints

- Preserve the current Home hero, categorized Settings, and five top-level destinations; Settings remains a stable surface UI.
- Shared cinematic shell is used by Library, populated Search, and Discover.
- Landscape cards use approximately 232×131dp artwork, a 56dp opaque metadata band, 16dp spacing, and space for the focus halo.
- Focused cards update the backdrop after 120ms, cancel stale loads, retain the previous image, and crossfade over 220–260ms.
- Reduced Motion disables scaling/crossfades and uses an immediate high-contrast ring; High-Contrast Focus remains independently selectable.
- All Titles poster cards have a 136dp minimum width, normally five columns at 960×540dp and four at font scale 1.5.
- Non-default library query pages are session-local and never replace or append to the canonical cached library.
- Search restoration automatically reissues a nonblank query; cancellation/error keeps the previous query and prior successful content.
- Voice search uses an external system recognizer, adds no microphone permission, and renders no microphone action when unavailable.
- Duplicate Jellyfin/Seerr results merge only by `MediaIdentity`; title text and release year are never deduplication keys.
- Visible item actions are Play/Resume, Details, My List/Favorite, and Played/Unplayed; no action depends on long-press or Menu.
- Low-tier Fire TV browse target is p95 frame time no worse than 33.3ms with no queued backdrop animations, blank frames, focus loss, or accidental activation across 100 D-pad actions.
- Run every Gradle command from the repository root and finish each task with its focused tests before committing.

---

### Task 1: Profile/library browse queries and canonical-cache isolation

**Files:**
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/LibraryBrowseQuery.kt`
- Modify: `shared-network/src/commonMain/kotlin/dev/jellystack/network/jellyfin/JellyfinBrowseApi.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseRepository.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseCoordinator.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePreferencesRepository.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/jellyfin/LibraryBrowseQueryTest.kt`
- Modify: `shared-core/src/commonTest/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseRepositoryTest.kt`
- Modify: `shared-core/src/commonTest/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseCoordinatorTest.kt`
- Modify: `shared-network/src/commonTest/kotlin/dev/jellystack/network/jellyfin/JellyfinBrowseApiTest.kt`

**Interfaces:**
- Produces `LibraryBrowseQuery(sort, direction, played, favoritesOnly, genres, years, mediaTypes)` with defaults equivalent to title ascending and no filters.
- Sort values are title, date added, and release year; direction is ascending or descending; played is any, played, or unplayed.
- Repository exposes `loadLibraryPage(libraryId, page, pageSize, query, cachePolicy)` where only `CanonicalDefault` writes persistent list pages and `SessionOnly` returns network results without store mutation.
- Profile preferences key browse query by `(profileId, libraryId)`.

- [ ] **Step 1: Write failing query mapping and isolation tests**

Assert every sort/direction parameter, played/unplayed/favorite filter, genre, year, and mixed-library media type is encoded correctly and omitted at defaults. Load a canonical page, then load and page a non-default query; assert cached canonical records and counts are byte-for-byte unchanged. Cover query cancellation, page zero replacement, paging boundaries, end detection, and per-profile/library remembered query isolation.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-network:testDebugUnitTest :shared-core:testDebugUnitTest --tests "*LibraryBrowseQueryTest" --console=plain
```

Expected: query and cache-policy APIs are missing.

- [ ] **Step 3: Implement query value and API translation**

Keep the query independent of Ktor and UI types. Translate it at `JellyfinBrowseApi` to Jellyfin sort/filter parameters, normalize/sort set values for stable requests, and preserve collection-type defaults when media type is not overridden. Reject impossible year/media values before the request.

- [ ] **Step 4: Separate canonical and session page paths**

Default query uses existing SQLDelight replace/append paths. Any non-default query uses coordinator-owned session pages keyed by library and complete query, cancels the previous generation on change, and never calls item-store replace/upsert for those pages. Persist only the selected query value per profile/library.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :shared-network:testDebugUnitTest :shared-core:testDebugUnitTest --console=plain
```

Expected: request mapping, cancellation, paging, canonical isolation, and preference tests pass.

- [ ] **Step 6: Commit**

```powershell
git add shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/LibraryBrowseQuery.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseRepository.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseCoordinator.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePreferencesRepository.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/jellyfin shared-network/src/commonMain/kotlin/dev/jellystack/network/jellyfin/JellyfinBrowseApi.kt shared-network/src/commonTest/kotlin/dev/jellystack/network/jellyfin/JellyfinBrowseApiTest.kt
git commit -m "feat(library): add isolated browse queries"
```

### Task 2: Shared cinematic shell, dwell backdrop, focus tokens, and item actions

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCinematicBrowse.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBackdropController.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseModels.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvComponents.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvTheme.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettings.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettingsRepository.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvBackdropControllerTest.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvCinematicLayoutTest.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicBrowseTest.kt`

**Interfaces:**
- Produces immutable `TvCinematicBrowseState(hero, rows, focusedAnchor, backdrop, inlineStatus)` and keyed `TvCinematicRow`/`TvCinematicCard` models.
- `TvBackdropController.focus(card)` waits 120ms, cancels stale jobs, retains previous successful artwork until replacement success, then signals a 240ms crossfade; reduced motion swaps immediately after dwell.
- Produces visible `TvSelectedItemActions` callbacks for play/resume, details, save/favorite, and played state.
- Adds device-global `MotionPreference { SYSTEM, REDUCED, FULL }` and `highContrastFocus: Boolean`.

- [ ] **Step 1: Write failing controller and geometry tests**

With a test scheduler, focus A then B before 120ms and assert only B loads; fail B and assert A remains; focus C and assert one 240ms replacement; reduced motion emits no scale/crossfade. Assert 232×131 art, 56dp metadata, 16dp gaps, safe halo, opaque title surface, minimum 48dp actions, and independent selection/focus signals.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvBackdropControllerTest" --tests "dev.jellystack.design.tv.TvCinematicLayoutTest" --console=plain
```

Expected: cinematic models/controller/tokens are missing.

- [ ] **Step 3: Implement stable shell and backdrop pipeline**

Use stable item/row keys, immutable presentation lists, remembered expensive formatting, bounded image requests, and one cancellable backdrop job. Retain successful backdrop state across refresh and partial error. Do not queue animations or allocate/sort rows in card composition.

- [ ] **Step 4: Implement focus-reactive metadata and action strip**

The currently focused card updates upper metadata and exposes the four visible actions. Keep text inside the opaque 56dp band and full semantic title on the card. Respect system animation scale plus explicit Reduced Motion and High-Contrast Focus overrides.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvCinematicBrowseTest' --console=plain
```

Expected: dwell cancellation, fallback retention, motion, geometry, focus, semantics, and action reachability tests pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCinematicBrowse.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBackdropController.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseModels.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvComponents.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvTheme.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvBackdropControllerTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvCinematicLayoutTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicBrowseTest.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettings.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettingsRepository.kt
git commit -m "feat(tv): add cinematic browse shell"
```

### Task 3: Library Browse rows, adaptive All Titles, filters, and restoration

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreen.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLibraryQueryControls.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLibraryBrowsePresentationTest.kt`
- Modify: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLibraryPagingTest.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreenTest.kt`

**Interfaces:**
- Produces `enum class TvLibraryMode { BROWSE, ALL_TITLES }` saved per route session.
- Browse row order is Continue/Next Up where supported, Recently Added, My List/Favorites, then server-provided nonempty sections.
- All Titles uses adaptive 136dp-min portrait columns or consistent landscape cards when poster reliability is false.
- Back from All Titles returns to exact Browse `TvFocusAnchor`; Back from Browse follows the Release 1 hierarchy.

- [ ] **Step 1: Write failing presentation and traversal tests**

Assert explicit Browse/All Titles controls, nonempty row order, omission of unsupported/empty rows, five columns at 960×540 font scale 1.0, four at 1.5, landscape fallback for poster-poor libraries, exact Browse anchor restoration, remembered item per row, nearest-x vertical movement, no row wrap, paging-at-end, held-key traversal, and all item actions reachable.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvLibraryBrowsePresentationTest" --tests "dev.jellystack.design.tv.TvLibraryPagingTest" --console=plain
```

Expected: mode/presentation/adaptive grid APIs are missing.

- [ ] **Step 3: Implement Browse and All Titles modes**

Keep the library landing as one Libraries row. Inside a selected library, render header mode controls and the shared shell. Browse consumes cached/capability-backed rows; All Titles consumes the paged query state. Derive column count from usable safe width and font scale rather than device model or resolution.

- [ ] **Step 4: Add sort/filter controls and exact restoration**

Expose title/date-added/year, direction, played, favorites, genre, year, and mixed media type through visible focusable controls/dialogs. Restore selected values per profile/library and exact row/card when returning from All Titles or detail; use nearest x only when the exact item no longer exists.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvLibraryBrowseScreenTest' --console=plain
```

Expected: row order, adaptive grid, query controls, paging, Back, focus restoration, held-key, and action-strip tests pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreen.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLibraryQueryControls.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLibraryBrowsePresentationTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLibraryPagingTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreenTest.kt
git commit -m "feat(tv): add cinematic library modes"
```

### Task 4: Unified Search coordinator, exact deduplication, and external voice port

**Files:**
- Replace: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvSearchState.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvVoiceSearch.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Create: `app-tv/src/main/java/app/jellystack/tv/AndroidTvVoiceSearch.kt`
- Modify: `app-tv/src/main/java/app/jellystack/tv/MainActivity.kt`
- Modify: `app-tv/src/main/AndroidManifest.xml`
- Replace: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchStateTest.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchDeduplicationTest.kt`
- Create: `app-tv/src/test/java/app/jellystack/tv/AndroidTvVoiceSearchTest.kt`
- Modify: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvSearchInteractionTest.kt`

**Interfaces:**
- Produces one `TvSearchCoordinator` owning query, source, edit mode, Jellyfin/Seerr generations, prior successful results, merged presentation, and voice state.
- Produces `TvVoiceSearchPort(availability, launch)` with results `Success(text)`, `Cancelled`, and `Error`; unsupported has no rendered control.
- Exact duplicate produces one card with `Play` when Jellyfin is available and `Request` when only Seerr is available; source-local-only items from different sources do not merge.

- [ ] **Step 1: Write failing unified state, dedupe, and voice tests**

Assert restored query reissues both sources once, newer query cancels old results, partial error keeps successful source, cancel/error voice preserves previous query/results/mode, unsupported exposes no voice action, exact TMDB/TVDB duplicates merge with correct action, title-only matches remain separate, and provider collisions across media types remain separate.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :app-tv:testDebugUnitTest --tests "*TvSearch*" --tests "*AndroidTvVoiceSearchTest" --console=plain
```

Expected: unified coordinator/voice APIs are missing and current split coordinator cannot satisfy the assertions.

- [ ] **Step 3: Implement unified coordinator and exact reconciliation**

Use one generation per submitted query and combine source completions without discarding prior successful content during refresh. Reconcile only identities with shared TMDB or TVDB values; source-local fallback is namespaced by provider. Preserve the current query and edit/browse state through voice cancellation and failure.

- [ ] **Step 4: Implement capability-gated external recognizer**

Use the Activity Result API with a system speech-recognition intent, probe intent resolution at runtime, and pass results through `TvVoiceSearchPort`. Do not request or declare `RECORD_AUDIO`. Hide the microphone action entirely when the intent is unsupported, including Fire TV environments without a recognizer.

- [ ] **Step 5: Verify GREEN and manifest invariants**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :app-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvSearchInteractionTest' :app-tv:verifyTvReleaseManifestPermissions --console=plain
```

Expected: search/voice/dedupe tests pass and merged manifest contains no microphone permission.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvSearchState.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvVoiceSearch.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchStateTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchDeduplicationTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvSearchInteractionTest.kt app-tv/src/main/java/app/jellystack/tv/AndroidTvVoiceSearch.kt app-tv/src/main/java/app/jellystack/tv/MainActivity.kt app-tv/src/main/AndroidManifest.xml app-tv/src/test/java/app/jellystack/tv/AndroidTvVoiceSearchTest.kt
git commit -m "feat(tv): unify search and external voice"
```

### Task 5: Cinematic Search/Discover, performance/golden matrix, and Release 3 gates

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvGoldenFixtures.kt`
- Modify: `design-screenshots/src/screenshotTest/kotlin/dev/jellystack/design/screenshots/JellystackTvScreenshotTest.kt`
- Create: `tv-benchmark/build.gradle.kts`
- Create: `tv-benchmark/src/main/AndroidManifest.xml`
- Create: `tv-benchmark/src/main/java/dev/jellystack/tv/benchmark/TvBrowseBenchmark.kt`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicSearchDiscoverTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Populated Search and Discover use the shared cinematic shell; disconnected/full-error/empty states remain focused recovery surfaces and partial errors stay inline.
- Benchmark performs 100 deterministic D-pad actions and reports frame timing, focus continuity, blank backdrop frames, queued replacements, and accidental activation count.
- Golden matrix covers 720p, 1080p, and 4K; English/German; font scales 1.0/1.5; white/black art; Browse/All Titles/Search/Discover.

- [ ] **Step 1: Write failing Search/Discover shell tests**

Assert populated results use the shared row/card geometry, focused cards own backdrop, exact actions are reachable, partial failures do not steal focus, full errors focus Retry, disconnected Discover focuses Connect, row/grid return is exact, and a 100-key held-repeat traversal keeps exactly one focus target without activation.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvCinematicSearchDiscoverTest' --console=plain
```

Expected: current Search/Discover do not use the shared shell and geometry/focus assertions fail.

- [ ] **Step 3: Migrate populated Search and Discover to the shell**

Map merged Search and available Discover rails to stable cinematic rows. Keep query/source controls above Search rows, preserve Discover recovery states, and reuse dwell/backdrop/action behavior without duplicating card composition.

- [ ] **Step 4: Add release benchmark and complete golden matrix**

Create a release-targeted Macrobenchmark or equivalent frame metrics module for the TV app. Execute 100 D-pad actions against deterministic fixtures and fail if p95 exceeds 33.3ms, focus disappears, an activation occurs, backdrop replacement queues, or a blank frame is observed. Generate and visually inspect all golden references using short preview names to remain Windows-path safe.

- [ ] **Step 5: Run complete Release 3 and release-candidate gates**

```powershell
.\gradlew.bat spotlessCheck detekt :players:testDebugUnitTest :design-tv:testDebugUnitTest :app-tv:testDebugUnitTest :app-tv:lintDebug :app-tv:assembleDebug verifyUniqueAndroidVersionCodes :app-tv:verifyTvReleaseManifestPermissions :app-android:assembleDebug --console=plain
.\gradlew.bat :design-tv:connectedDebugAndroidTest :app-tv:connectedDebugAndroidTest :tv-benchmark:connectedCheck --console=plain
.\gradlew.bat :app-tv:bundleRelease :app-tv:assembleRelease --console=plain
```

Expected: root/device/benchmark/release builds exit zero; certificate fingerprint and artifact hashes are recorded locally; no release is pushed.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvGoldenFixtures.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicSearchDiscoverTest.kt design-screenshots/src/screenshotTest design-screenshots/src/screenshotTestDebug/reference tv-benchmark settings.gradle.kts gradle/libs.versions.toml .github/workflows/ci.yml
git commit -m "test(tv): gate cinematic discovery"
```
