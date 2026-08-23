# Jellystack TV Release 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the reproducible detail crash and make the current TV beta dependable under asynchronous data, Back, focus restoration, sleep/wake, partial failure, and diverse runtime playback capabilities.

**Architecture:** Rendering and focus consume the same immutable keyed UI models. A lifecycle-aware `TvAppStateHolder` owns navigation, rail, and semantic focus anchors while screen coordinators expose immutable state and actions. Android-only codec inspection, activity lifecycle, recognizer hooks, and CI emulator setup remain outside reusable TV design state.

**Tech Stack:** Kotlin Multiplatform, Kotlin coroutines and StateFlow, Jetpack Compose for TV, Navigation 3, Media3, SQLDelight, Compose UI tests, Android Preview Screenshot testing, Gradle Wrapper, GitHub Actions.

## Global Constraints

- Design against a 960×540dp logical reference and keep text, actions, focused bounds, scale, and glow inside 48dp horizontal and 27dp vertical safe insets.
- Expanded rail is a 224–232dp scrimmed overlay; content never shifts when it opens.
- Focus uses 1.05–1.06 scale, a dual-tone ring/glow with at least 3:1 contrast, and elevation/surface change; selection uses a check or accent bar.
- Focused backdrop dwell is 120ms and replacement crossfade is 220–260ms; stale loads are cancelled and the previous successful image remains visible.
- Trailer preview dwell is at least 1.5 seconds and preview audio never starts or resumes after wake before fresh user interaction.
- Back order is keyboard → dialog/panel → folder/path → detail/category → expanded rail → system exit; Back never opens the rail.
- Production playback behavior uses runtime capability detection with `supported / unsupported / unknown` states and conservative fallback; no manufacturer, model, fingerprint, product, or emulator branches.
- Minimum action size is 48×48dp; normal text contrast is at least 4.5:1 and large text, icons, and focus indicators are at least 3:1.
- Keep platform integrations in `app-tv`; `design-tv` consumes immutable state and action interfaces.
- Run every Gradle command from the repository root and finish each task with its focused tests before committing.

---

### Task 1: Keyed immutable detail sections and semantic focus anchors

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailUiState.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvDetailUiStateTest.kt`
- Modify: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvDetailFocusTest.kt`

**Interfaces:**
- Produces `@Immutable data class TvDetailUiState(val routeKey: String, val sections: List<TvDetailSection>)`.
- Produces keyed `TvDetailSection` variants for facts, overview, seasons, episodes, cast, ratings, and similar payloads; every variant has a stable `id` and the focusable variants expose stable item IDs.
- Produces `data class TvFocusAnchor(val sectionId: String?, val itemId: String?, val destination: TvFocusDestination)` and `enum class TvFocusDestination { HERO, PRIMARY_ACTION, BODY, SECTION_ITEM }`.
- Changes `TvDetailFocusLayout` to accept keyed sections and return `Map<String, TvDetailSectionFocusModifiers>` to its content instead of a positional list.

- [ ] **Step 1: Write failing immutable-state tests**

Add tests that build Jellyfin and Seerr detail states with episodes, cast, and similar in different orders. Assert section IDs are unique, order matches the rendered contract, absent sections disappear, and a remembered `TvFocusAnchor("cast", "person-2", SECTION_ITEM)` resolves by IDs rather than the old index.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvDetailUiStateTest" --console=plain
```

Expected: compilation fails because `TvDetailUiState`, `TvDetailSection`, and `TvFocusAnchor` do not exist.

- [ ] **Step 3: Implement immutable keyed section construction**

Define the types above, use stable keyed section builders for Jellyfin and Seerr payloads, and make both the `LazyColumn` rendering loop and focus requester map iterate the same `sections` instance. Resolve a section’s current lazy index only when scrolling/materializing focus. Remove `lowerContentFocusIndex`, unchecked `lowerContentFocusModifiers[index]`, and `tvSeerrDetailLowerContentTargets`.

- [ ] **Step 4: Add the asynchronous production-screen regression**

Extend `TvDetailFocusTest` to render the real Jellyfin and Seerr detail content, mutate episodes/cast/similar after the initial frame, and cover appear, disappear, and reorder transitions while focus is on a surviving and a removed item. Assert no uncaught exception, the stable surviving item retains focus, and removed focus falls back to the nearest section/item.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvDetailFocusTest' --console=plain
```

Expected: the keyed-state unit tests and production detail instrumentation tests pass with no `IndexOutOfBoundsException`.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailUiState.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvDetailUiStateTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvDetailFocusTest.kt
git commit -m "fix(tv): key asynchronous detail sections"
```

### Task 2: Lifecycle-aware app state, Back hierarchy, and stable focus restoration

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAppStateHolder.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigationRailState.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvAppStateHolderTest.kt`
- Modify: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvBackNavigationTest.kt`
- Modify: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvFocusMemoryTest.kt`

**Interfaces:**
- Produces immutable `TvAppUiState(backStack, currentRoute, railExpanded, activeProfileGeneration, isForeground)` and a `TvAppStateHolder` action API for `push`, `selectTopLevel`, `popRoute`, `openRail`, `closeRail`, `onForegrounded`, `onBackgrounded`, and generation reset.
- Replaces `TvBackAction.OPEN_RAIL` with `TvBackAction.SYSTEM_EXIT`.
- `TvFocusMemory` stores `TvFocusAnchor` plus horizontal center/index and resolves exact section/item first, nearest surviving horizontal item second, and first actionable item last.

- [ ] **Step 1: Write failing state-machine tests**

Cover every Back layer: library folder pops before route, deep route pops before rail, expanded rail closes, and top-level collapsed rail yields `SYSTEM_EXIT`. Assert no Back path yields an open-rail action. Add recreation/wake tests that serialize routes, preserve a semantic focus anchor, and never let asynchronous restoration steal focus after the user moves to the rail.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvAppStateHolderTest" --tests "dev.jellystack.design.tv.TvBackNavigationTest" --tests "dev.jellystack.design.tv.TvFocusMemoryTest" --console=plain
```

Expected: tests fail because `TvAppStateHolder` and `SYSTEM_EXIT` are missing and top-level Back currently returns `OPEN_RAIL`.

- [ ] **Step 3: Implement the holder and move navigation state out of the root**

Move back-stack mutation, rail visibility, focus memory, detail-source identity, and lifecycle generation into `TvAppStateHolder`. Keep Compose `Saver` adaptation at the root boundary. Expose state read-only and actions explicitly; do not move player engines, Activity contracts, Koin lookup, or Android lifecycle classes into the holder.

- [ ] **Step 4: Enforce layered Back ownership**

Let screen-local keyboard/dialog handlers consume Back first. Enable the app `BackHandler` only for folder, route, or expanded-rail actions; when the holder reports `SYSTEM_EXIT`, leave Back unconsumed for the Activity/system. Keep Left-on-first-control as the only normal rail opener.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvAppStateHolderTest" --tests "dev.jellystack.design.tv.TvBackNavigationTest" --tests "dev.jellystack.design.tv.TvFocusMemoryTest" --console=plain
```

Expected: all state, Back, and stable-ID restoration cases pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAppStateHolder.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigationRailState.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvAppStateHolderTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvBackNavigationTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvFocusMemoryTest.kt
git commit -m "refactor(tv): extract lifecycle app state"
```

### Task 3: Search modes, Discover return behavior, and wake-safe trailers

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvSearchState.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvTrailerPreviewController.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Modify: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchStateTest.kt`
- Modify: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvTrailerPreviewControllerTest.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvSearchInteractionTest.kt`

**Interfaces:**
- Produces `enum class TvSearchMode { EDIT, BROWSE }` and immutable search session state containing query, selected source, mode, and query generation.
- Adds `restoreQuery`, `enterEditMode`, `enterBrowseMode`, and retry actions to the search coordinator; a restored nonblank query is submitted once automatically.
- Adds trailer controller lifecycle actions `onBackgrounded()` and `onUserInteraction()`; backgrounding cancels focus/player state and foregrounding alone cannot re-arm preview.

- [ ] **Step 1: Write failing coordinator and trailer tests**

Assert fresh Search starts in edit mode, Back changes edit to browse without clearing the query, Center re-enters edit mode, restored query is reissued once, a newer query cancels stale results, and retry preserves the query. Assert backgrounding cancels preview and a subsequent focus cannot start playback until `onUserInteraction()` followed by a fresh 1.5-second dwell.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvSearchStateTest" --tests "dev.jellystack.design.tv.TvTrailerPreviewControllerTest" --console=plain
```

Expected: failures show the missing edit/browse and lifecycle-gating APIs.

- [ ] **Step 3: Implement search and wake state**

Hoist query/source/mode from `TvSearchScreen` into its coordinator state, preserve content while refresh is in flight, and resubmit restored queries through one generation. Open the keyboard only on fresh route entry or explicit Center. Route any Activity/Compose background signal to `onBackgrounded`; route the next real key-down to `onUserInteraction` without replaying the old focus request.

- [ ] **Step 4: Fix Discover return and partial-error focus**

Change Connect Seerr navigation to push Connections on top of Discover rather than clearing the top-level stack. Render rail failures as inline non-focusable status content after successful rails; only full failure owns Retry focus. Ensure the first result is padded inside the 48dp safe region and its halo is not clipped.

- [ ] **Step 5: Add UI interaction coverage and verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvSearchInteractionTest' --console=plain
```

Expected: keyboard Back, restored query, source traversal, clipped-bound assertions, Discover return, partial error, and wake preview tests pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvSearchState.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvTrailerPreviewController.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvSearchStateTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvTrailerPreviewControllerTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvSearchInteractionTest.kt
git commit -m "fix(tv): stabilize search and wake behavior"
```

### Task 4: TV-safe visual system and accessibility semantics

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLayout.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAccessibility.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvComponents.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCategorizedSettingsScreen.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLayoutTest.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvMetadataPresentationTest.kt`
- Modify: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvComponentsTest.kt`

**Interfaces:**
- Produces `TvSafeInsets(horizontal = 48.dp, vertical = 27.dp)`, rail width `228.dp`, and reusable card/focus tokens.
- `TvActionButton` distinguishes visual `primary` from semantic `selected`; toggle controls expose checked state.
- `TvStatusAnchor` exposes heading/live-region/status semantics and D-pad Left/Back handling without a click action.
- `TvMediaCard` owns one merged semantic label, marks artwork decorative, and renders a deterministic gradient/icon missing-art fallback.

- [ ] **Step 1: Write failing geometry, metadata, and semantics tests**

Assert 960×540 safe bounds, 228dp overlay rail without content offset, minimum 48dp actions, focus scale in 1.05–1.06, two-ring contrast against white and black, adaptive settings columns at font scales 1.0 and 1.5, localized English/German counts, and omission of zero/negative ratings. Compose tests must assert headings, checked/selected state, one media-card label, decorative artwork, polite status announcements, and absence of a click action on loading anchors.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvLayoutTest" --tests "dev.jellystack.design.tv.TvMetadataPresentationTest" --console=plain
```

Expected: token and presentation APIs are missing and current semantics/geometry assertions fail.

- [ ] **Step 3: Implement reusable layout and focus tokens**

Remove root content reflow animation. Draw a 228dp scrimmed rail above unchanged content, place the rail and all screen content inside safe insets, and use a 1.055 scale with outer light and inner dark/accent rings plus raised surface/elevation. Keep selection as a check/accent bar. Use stable keys and remembered calculations in lazy rows.

- [ ] **Step 4: Correct metadata, artwork, loading, and accessibility**

Localize item counts through `TvStrings`, filter invalid numeric metadata, put titles on controlled opaque surfaces, and show designed fallback art. Mark section titles as headings, direct toggles as checked, chips as selected, artwork/logos/backdrops as decorative, and loading/results/errors as one-shot polite live regions. Remove clickable no-op placeholders and duplicate icon/title descriptions.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvComponentsTest' --console=plain
```

Expected: geometry, contrast, metadata, semantics, and action-size tests pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLayout.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAccessibility.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvComponents.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCategorizedSettingsScreen.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvLayoutTest.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvMetadataPresentationTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvComponentsTest.kt
git commit -m "feat(tv): add safe accessible focus system"
```

### Task 5: Tri-state runtime playback capability snapshots

**Files:**
- Modify: `players/src/commonMain/kotlin/dev/jellystack/players/TvPlaybackDecoderPolicy.kt`
- Modify: `players/src/commonMain/kotlin/dev/jellystack/players/PlaybackDeviceProfileProvider.kt`
- Modify: `players/src/androidMain/kotlin/dev/jellystack/players/AndroidPlaybackDeviceProfileProvider.kt`
- Modify: `players/src/commonTest/kotlin/dev/jellystack/players/TvPlaybackDecoderPolicyTest.kt`
- Modify: `players/src/commonTest/kotlin/dev/jellystack/players/PlaybackDeviceProfileProviderTest.kt`
- Create: `players/src/androidUnitTest/kotlin/dev/jellystack/players/AndroidPlaybackCapabilitySnapshotTest.kt`

**Interfaces:**
- Produces `enum class CapabilitySupport { SUPPORTED, UNSUPPORTED, UNKNOWN }`.
- Produces immutable `PlaybackCapabilitySnapshot` with per-codec video/audio support, hardware support, channel limits, and inspection completeness.
- `AndroidTvPlaybackDeviceProfileProvider` depends only on a runtime snapshot source; it contains no Build fingerprint/model/product or emulator policy branch.

- [ ] **Step 1: Write failing capability-combination tests**

Cover supported, unsupported, partially reported, contradictory, and inspection-failure/unknown snapshots. Assert advanced video is advertised only when runtime evidence is supported and hardware-capable; unsupported is omitted; unknown degrades to interoperable H.264/AAC transcode capabilities; encoders and aliases never count; channel limits remain conservative.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :players:testDebugUnitTest --tests "dev.jellystack.players.TvPlaybackDecoderPolicyTest" --tests "dev.jellystack.players.PlaybackDeviceProfileProviderTest" --console=plain
```

Expected: compilation fails for the tri-state snapshot and current emulator-specific expectations conflict with the new invariant.

- [ ] **Step 3: Implement snapshot selection and Android inspection**

Convert codec enumeration into an immutable snapshot. On API levels where hardware acceleration cannot be established reliably, represent it as unknown instead of guessing from codec or device names. Catch registry/capability failures per codec and retain partial evidence. Map unknown or contradictory data to a conservative interoperable profile rather than empty or optimistic direct play.

- [ ] **Step 4: Remove device identity branches and verify GREEN**

Delete all uses of `Build.FINGERPRINT`, `Build.MODEL`, `Build.PRODUCT`, and software-emulator allowances from production playback policy.

```powershell
.\gradlew.bat :players:testDebugUnitTest --console=plain
rg -n "Build\.(FINGERPRINT|MODEL|PRODUCT)|google_sdk|emulator" players/src/*Main
```

Expected: player tests pass and the search returns no production matches.

- [ ] **Step 5: Commit**

```powershell
git add players/src/commonMain/kotlin/dev/jellystack/players/TvPlaybackDecoderPolicy.kt players/src/commonMain/kotlin/dev/jellystack/players/PlaybackDeviceProfileProvider.kt players/src/androidMain/kotlin/dev/jellystack/players/AndroidPlaybackDeviceProfileProvider.kt players/src/commonTest/kotlin/dev/jellystack/players/TvPlaybackDecoderPolicyTest.kt players/src/commonTest/kotlin/dev/jellystack/players/PlaybackDeviceProfileProviderTest.kt players/src/androidUnitTest/kotlin/dev/jellystack/players/AndroidPlaybackCapabilitySnapshotTest.kt
git commit -m "fix(players): use runtime capability snapshots"
```

### Task 6: Production-root, device, screenshot, and required CI gates

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvGoldenFixtures.kt`
- Create: `design-screenshots/src/screenshotTest/kotlin/dev/jellystack/design/screenshots/JellystackTvScreenshotTest.kt`
- Modify: `design-screenshots/build.gradle.kts`
- Create: `app-tv/src/test/java/app/jellystack/tv/TvPlatformActionsTest.kt`
- Create: `app-tv/src/androidTest/java/app/jellystack/tv/TvProductionRootSmokeTest.kt`
- Modify: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvDetailFocusTest.kt`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces deterministic TV fixtures for loading, missing art, partial error, detail, Search, Discover, and focused-card contrast.
- CI build gate includes `spotlessCheck`, `detekt`, `:players:testDebugUnitTest`, `:design-tv:testDebugUnitTest`, `:app-tv:testDebugUnitTest`, `:app-tv:lintDebug`, `:app-tv:assembleDebug`, `verifyUniqueAndroidVersionCodes`, `:app-tv:verifyTvReleaseManifestPermissions`, and `:app-android:assembleDebug`.
- CI device gate runs both `:design-tv:connectedDebugAndroidTest` and `:app-tv:connectedDebugAndroidTest` on an Android TV AVD without retry or quarantine.

- [ ] **Step 1: Add failing production-root and fixture tests**

Launch the production root with injectable fake platform actions and asynchronous fake Jellyfin/Seerr sources. Exercise Home → Library → Detail → Playback → Back, top-level system Back delegation, recreation/wake, keyboard dismissal, partial error, missing art, and media keys. Assert the exact detail crash transition from Task 1 remains covered at the root.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :app-tv:testDebugUnitTest :app-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.jellystack.tv.TvProductionRootSmokeTest' --console=plain
```

Expected: tests fail until the production root/platform action seam exists.

- [ ] **Step 3: Add TV golden screenshot matrix**

Add short-named previews at one 960×540dp logical viewport rendered as 720p (213dpi), 1080p (320dpi), and 4K (640dpi). Cover English and German, font scales 1.0 and 1.5, and white/black artwork contrast fixtures without embedding private server data. Generate and visually inspect tracked references.

- [ ] **Step 4: Wire required CI gates**

Extend the build command with every required root gate and add an Android TV emulator matrix entry running both TV connected test tasks. Keep the existing mobile/tablet shards unchanged.

- [ ] **Step 5: Run the complete Release 1 gate three consecutive times**

Run the root gate followed by the two device tasks three times with no retry, quarantine, or test filtering. Each run must exit zero.

```powershell
.\gradlew.bat spotlessCheck detekt :players:testDebugUnitTest :design-tv:testDebugUnitTest :app-tv:testDebugUnitTest :app-tv:lintDebug :app-tv:assembleDebug verifyUniqueAndroidVersionCodes :app-tv:verifyTvReleaseManifestPermissions :app-android:assembleDebug --console=plain
.\gradlew.bat :design-tv:connectedDebugAndroidTest :app-tv:connectedDebugAndroidTest --console=plain
```

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvGoldenFixtures.kt design-screenshots/src/screenshotTest/kotlin/dev/jellystack/design/screenshots/JellystackTvScreenshotTest.kt design-screenshots/src/screenshotTestDebug/reference design-screenshots/build.gradle.kts app-tv/src/test app-tv/src/androidTest design-tv/src/androidTest .github/workflows/ci.yml
git commit -m "test(tv): enforce release one gates"
```
