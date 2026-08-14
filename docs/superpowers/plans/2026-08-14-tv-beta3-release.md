# TV Beta 3 Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the TV detail entry scroll regression, prepare complete Amazon tablet and Fire TV artwork, and publish Jellystack `0.16.0-tv-beta.3` as a GitHub prerelease.

**Architecture:** A small reusable detail-focus scaffold owns the detail `LazyListState`, Hero focus target, and primary-action focus target so every Jellyfin movie, series, and episode starts at the top and can navigate Hero → Play → Hero. Store artwork is derived deterministically from existing Jellystack brand assets, screenshot-test fixtures, and real 1920×1080 TV captures. Release metadata advances only the TV artifact to version code 21 and attaches the signed TV APK to a prerelease tag.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Compose UI tests, Gradle Wrapper, ADB, Pillow, Git, GitHub CLI.

## Global Constraints

- Keep mobile behavior and `app-android` version metadata unchanged.
- Use `versionCode 21`, `versionName 0.16.0-tv-beta.3`, and tag `v0.16.0-tv-beta.3`.
- GitHub release is a prerelease; attach the release TV APK but never the TV AAB.
- Tablet icons must be transparent PNGs at exactly 512×512 and 114×114.
- Tablet screenshots must use an accepted Amazon size and include at least three images.
- Fire TV app icon must be opaque PNG at 1280×720.
- Fire TV background and screenshots must be opaque 1920×1080 landscape PNGs, with at least three screenshots.
- Preserve the existing untracked `.qa-firetv-*.png` files and never stage private media captures.
- Run every Gradle task from the repository root and finish with `:app-android:assembleDebug`.

---

### Task 1: Restore the TV detail Hero entry position

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvDetailFocusTest.kt`

**Interfaces:**
- Produces: an internal detail-focus scaffold keyed by the Jellyfin item ID, with a Hero focus target and primary-action focus target.
- Behavior: initial focus places the Hero at scroll offset zero; Down/Center enters Play; Up from Play scrolls to zero and restores Hero focus.

- [ ] **Step 1: Write the failing Compose test**

Render the production focus scaffold with an oversized 520 dp Hero and assert that the Hero is initially focused at root top zero. Send Down and Up and assert focus returns to the Hero and its top is again zero.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat --no-daemon --console=plain :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvDetailFocusTest'
```

Expected: the new production scaffold/API is missing or the initial Hero assertion fails.

- [ ] **Step 3: Implement the minimal focus scaffold**

Use one `LazyListState`, one Hero `FocusRequester`, and one primary-action `FocusRequester`. Key the entry effect by route/item ID, call `scrollToItem(0)` before requesting Hero focus, consume Hero Down/Center to focus Play, and consume Play Up to return to offset zero and Hero focus.

- [ ] **Step 4: Verify GREEN and the real emulator repro**

Run the focused Compose test, install `:app-tv:assembleDebug`, open a real Jellyfin card, and confirm the full backdrop starts at the top. Verify Hero → Play → Hero and a second detail route.

- [ ] **Step 5: Commit the bugfix**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvDetailFocusTest.kt
git commit -m "fix: restore TV detail hero position"
```

### Task 2: Build and validate Amazon store assets

**Files:**
- Create: `store-assets/amazon/0.16.0-tv-beta.3/tablet/tablet_large_icon.png`
- Create: `store-assets/amazon/0.16.0-tv-beta.3/tablet/tablet_small_icon.png`
- Create: three `store-assets/amazon/0.16.0-tv-beta.3/tablet/screenshots/*.png`
- Create: `store-assets/amazon/0.16.0-tv-beta.3/fire-tv/app_icon.png`
- Create: `store-assets/amazon/0.16.0-tv-beta.3/fire-tv/background.png`
- Create: at least three `store-assets/amazon/0.16.0-tv-beta.3/fire-tv/screenshots/*.png`
- Create: `store-assets/amazon/0.16.0-tv-beta.3/manifest.txt`

**Interfaces:**
- Consumes: Jellystack launcher foreground, current tablet screenshot fixtures, tracked Amazon TV artwork, and new real TV captures.
- Produces: upload-ready opaque/transparent PNGs with exact dimensions and a manifest containing dimensions, alpha state, and SHA-256.

- [ ] **Step 1: Produce transparent tablet icons**

Render the highest-density Jellystack launcher foreground onto transparent 512×512 and 114×114 canvases with high-quality resampling and safe padding.

- [ ] **Step 2: Copy compliant tablet screenshots**

Use the dark 1920×1200 Home, Detail, and Discover responsive screenshot references. Flatten their alpha channel onto the app background so the store files are opaque.

- [ ] **Step 3: Capture current Fire TV screens**

Capture the fixed Home Hero, a focused media row, and the fixed detail Hero/navigation flow from the installed Beta 3 build at 1920×1080. Do not copy or stage the existing private `.qa-firetv-*.png` inputs.

- [ ] **Step 4: Copy/flatten Fire TV artwork**

Copy the tracked 1280×720 Amazon app icon and 1920×1080 background into the versioned package, enforcing opaque RGB output.

- [ ] **Step 5: Validate and commit assets**

Use Pillow to reject incorrect sizes, wrong formats, transparent Fire TV assets, or nontransparent corners in the tablet icons. Write exact SHA-256 values to `manifest.txt`, inspect every output visually, then commit only the versioned asset package.

### Task 3: Prepare and verify TV Beta 3

**Files:**
- Modify: `app-tv/build.gradle.kts`
- Modify: `RELEASE_NOTES.md`
- Modify: `docs/android-tv.md`
- Create: `docs/releases/0.16.0-tv-beta.3-discord.md`

**Interfaces:**
- Produces: TV version code 21/name `0.16.0-tv-beta.3`, release notes, current upload instructions, and copy-ready Discord announcement.

- [ ] **Step 1: Update version and release copy**

Document the premium TV Home Hero carousel, deterministic D-pad row/detail navigation, local Jellyfin trailer previews, responsive detail UI, and playback/device compatibility retained from Beta 2.

- [ ] **Step 2: Run the complete verification matrix**

```powershell
.\gradlew.bat --no-daemon --console=plain :shared-network:testDebugUnitTest :shared-core:testDebugUnitTest :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest :app-tv:testDebugUnitTest :app-tv:assembleDebug :app-android:assembleDebug
.\gradlew.bat --no-daemon --console=plain verifyUniqueAndroidVersionCodes :app-tv:verifyTvReleaseManifestPermissions :app-tv:assembleRelease :app-tv:bundleRelease
```

Expected: both commands exit zero; all TV instrumentation tests pass; APK and AAB are generated.

- [ ] **Step 3: Install and smoke-test the release candidate**

Install the exact release APK on the TV AVD, launch it, repeat the Home → detail → Play → Hero flow, and inspect the crash buffer.

- [ ] **Step 4: Record artifact hashes and commit release metadata**

Record SHA-256 for the release APK/AAB locally. Commit the version, release notes, TV documentation, Discord copy, and updated asset manifest if artifact hashes are listed there.

### Task 4: Merge, push, and publish GitHub prerelease

**Files:**
- No new source files.

**Interfaces:**
- Consumes: verified commits and release APK.
- Produces: pushed `main`, tag `v0.16.0-tv-beta.3`, and GitHub prerelease with the release APK attached.

- [ ] **Step 1: Review scope and merge locally**

Ensure only intended tracked changes are committed, fast-forward local `main`, and preserve user-owned untracked QA images.

- [ ] **Step 2: Re-run required verification on merged main**

Run `:app-android:assembleDebug` plus the relevant TV tests/build on the exact merge result.

- [ ] **Step 3: Push main and publish prerelease**

Push `main`, create tag `v0.16.0-tv-beta.3`, and publish `Jellystack 0.16.0 TV Beta 3` with the release notes and only `app-tv-release.apk` attached.

- [ ] **Step 4: Verify the remote release**

Query GitHub for the release URL, prerelease flag, tag, target commit, asset name, size, and digest. Hand off the store-assets directory, APK/AAB paths, hashes, and Discord announcement.
