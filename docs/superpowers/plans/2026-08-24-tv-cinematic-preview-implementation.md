# TV Cinematic Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Home, Library Browse, populated Search, and Discover one large non-focusable preview stage above static cinematic rows.

**Architecture:** `TvCinematicPreviewStage` renders immutable preview state and the one shared trailer player surface. Existing card focus and `TvTrailerPreviewController` feed route-scoped stage selection; cards only render artwork. `TvCinematicBrowse` owns row/action focus round trips while the app host cancels preview generations on route, profile, lifecycle, detail, and playback changes.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Coil 3, Media3-backed `AndroidPlayerEngine`, coroutines, Compose UI tests and JVM tests.

## Global Constraints

- Stage is approximately 310dp high at the 960x540dp reference and never focusable or clickable.
- Rows begin around 270dp and remain beyond `TvLayoutTokens.ContentStart`; the compact five-destination rail stays visible.
- Backdrop dwell is 120ms; trailer dwell is at least 1,500ms; crossfade is 220-260ms unless Reduced Motion is active.
- Cards never render video.
- D-pad Up enters valid selected-item actions and Down returns to the exact originating card.
- Focus changes, route/profile changes, detail/playback, sleep, and disposal stop pending or active preview playback.
- Missing artwork/trailers and network errors silently retain the current backdrop or fallback.
- No manufacturer/model production branches and no new microphone permission.

---

### Task 1: Route-scoped preview model and timing

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseModels.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBackdropController.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvTrailerPreviewController.kt`
- Test: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvBackdropControllerTest.kt`
- Test: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvTrailerPreviewControllerTest.kt`

**Interfaces:**
- Produces: `TvCinematicPreviewState(card, backdrop, trailerRequest, routeKey, generation)`.

- [ ] **Step 1: Add a failing stale-generation and 120ms-dwell test**
- [ ] **Step 2: Run the two focused JVM test classes and verify RED**
- [ ] **Step 3: Add route/profile generation to requests and cancel stale backdrop/trailer work**
- [ ] **Step 4: Keep the previous successful backdrop until the new image succeeds**
- [ ] **Step 5: Run focused tests and verify GREEN**
- [ ] **Step 6: Commit timing and generation behavior**

### Task 2: Non-focusable preview stage and action round trip

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCinematicPreviewStage.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCinematicBrowse.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicBrowseTest.kt`

**Interfaces:**
- Produces: `TvCinematicPreviewStage(state, actions, labels, previewState, previewEngine, soundEnabled, progress)`.

- [ ] **Step 1: Add failing tests that the stage has no click/focus action and cards contain no preview surface**
- [ ] **Step 2: Add a failing Up-to-actions and Down-to-originating-card test**
- [ ] **Step 3: Run the instrumentation class and verify RED**
- [ ] **Step 4: Implement the fixed stage and lower overlapping row viewport**

The stage renders backdrop/video, gradients, heading metadata, and valid actions. The row viewport uses top padding near 270dp and every first card retains left-rail escape.

- [ ] **Step 5: Implement explicit focus requesters for card/action round trips**
- [ ] **Step 6: Run the instrumentation class and verify GREEN**
- [ ] **Step 7: Commit the shared stage**

### Task 3: Library, Search, and Discover integration

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvCinematicDiscovery.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreen.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicSearchDiscoverTest.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvLibraryBrowseScreenTest.kt`

- [ ] **Step 1: Add a failing focused-card-to-stage test for each route family**
- [ ] **Step 2: Run focused instrumentation tests and verify RED**
- [ ] **Step 3: Pass exact Jellyfin items to local preview resolution and leave Seerr on backdrop when no supported trailer exists**
- [ ] **Step 4: Give Library Browse Play/Details/My List/Played actions without changing All Titles**
- [ ] **Step 5: Cancel previews on route/profile/detail/player transitions**
- [ ] **Step 6: Run focused tests and verify GREEN**
- [ ] **Step 7: Commit route integration**

### Task 4: Home migration and static cards

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvComponents.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvHomePresentation.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvHomeScreenTest.kt`
- Test: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvHomeCarouselSelectionTest.kt`

- [ ] **Step 1: Add failing tests for a non-focusable Home stage and first-card entry focus**
- [ ] **Step 2: Add a failing test proving focused card video surfaces never exist**
- [ ] **Step 3: Run focused tests and verify RED**
- [ ] **Step 4: Replace the focusable carousel with the shared stage and route row focus into it**
- [ ] **Step 5: Remove preview rendering parameters from `TvMediaCard`; retain artwork, progress, and selection UI**
- [ ] **Step 6: Run focused tests and verify GREEN**
- [ ] **Step 7: Commit Home migration**

### Task 5: Cinematic device and release gate

**Files:**
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvCinematicBrowseTest.kt`

- [ ] **Step 1: Run the focused JVM and Fire-TV instrumentation classes**
- [ ] **Step 2: Build and install `:app-tv:assembleRelease`**
- [ ] **Step 3: Verify Home, Library Browse, populated Search, and Discover with remote key events and UI-tree focus assertions**
- [ ] **Step 4: Run a 100-action D-pad traversal and inspect crash logs for focus loss or accidental activation**
- [ ] **Step 5: Run `:app-android:assembleDebug` from the repository root**
