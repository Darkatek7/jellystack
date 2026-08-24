# TV Profile Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the launch profile list with an avatar-first TV grid and move PIN/removal actions into a separate management flow.

**Architecture:** `TvProfileHost` resolves exact profile bindings and PIN state into immutable presentation models. Stateless selection and management Composables emit stable profile IDs and never read repositories. Existing activation, PIN, reconnection, and removal coordinators remain authoritative.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Coil 3, Compose UI tests, Android instrumentation tests.

## Global Constraints

- Use real Jellyfin display names and avatar URLs from each profile's exact connection.
- Keep all focused bounds inside 48dp horizontal and 27dp vertical safe insets.
- Selection contains only profile tiles, Add Profile, and Manage Profiles.
- PIN and removal actions exist only in management mode.
- Back order is dialog/PIN -> management detail -> management grid -> selection -> exit confirmation.
- Reduced Motion disables scale but keeps the dual-tone focus ring.
- Never delete a remote Jellyfin or Seerr account.

---

### Task 1: Immutable profile presentation

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileScreens.kt`
- Test: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvProfilePresentationTest.kt`

**Interfaces:**
- Produces: `TvProfilePresentation(id, displayName, avatarUrl, pinRequired, lastActiveAt)` and `selectInitialTvProfileId`.

- [ ] **Step 1: Write the failing initial-focus test**

```kotlin
assertEquals("bob", selectInitialTvProfileId(profiles, "bob"))
assertEquals("alice", selectInitialTvProfileId(profiles, "missing"))
```

- [ ] **Step 2: Run the focused unit test and verify RED**

Run: `.\gradlew.bat :design-tv:testDebugUnitTest --tests=dev.jellystack.design.tv.TvProfilePresentationTest --console=plain`

- [ ] **Step 3: Add the immutable model and stable fallback selection**

```kotlin
@Immutable
internal data class TvProfilePresentation(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val pinRequired: Boolean,
    val lastActiveAt: Instant?,
)

internal fun selectInitialTvProfileId(
    profiles: List<TvProfilePresentation>,
    rememberedProfileId: String?,
): String? = profiles.firstOrNull { it.id == rememberedProfileId }?.id ?: profiles.firstOrNull()?.id
```

- [ ] **Step 4: Run the focused unit test and verify GREEN**

- [ ] **Step 5: Commit only the model and its test**

### Task 2: Avatar-first selection grid

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvProfileScreensTest.kt`

**Interfaces:**
- Consumes: `TvProfilePresentation`.
- Produces: `TvProfilePickerScreen(profiles, rememberedProfileId, strings, onSelect, onAdd, onManage)`.

- [ ] **Step 1: Replace the existing test with a failing selection-contract test**

```kotlin
composeRule.onNodeWithTag("profile:alice:tile").assertIsFocused()
composeRule.onNodeWithContentDescription(strings.addProfile).assertHasClickAction()
composeRule.onNodeWithContentDescription(strings.manageProfiles).assertHasClickAction()
composeRule.onNodeWithText(strings.removeProfile).assertDoesNotExist()
```

- [ ] **Step 2: Run the one instrumentation class on the Fire TV and verify RED**

Run: `.\gradlew.bat :design-tv:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvProfileScreensTest" --console=plain`

- [ ] **Step 3: Implement a safe-inset `LazyVerticalGrid` of circular avatar tiles**

Each tile is one `tvFocusable` target, uses the initial fallback letter until Coil succeeds, ellipsizes the visible name, and announces the full name plus PIN state. Add Profile uses identical geometry; Manage Profiles is a separate action below the grid.

- [ ] **Step 4: Run the instrumentation class and verify GREEN**

- [ ] **Step 5: Commit the picker UI, strings, and tests**

### Task 3: Explicit management flow

**Files:**
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvProfileScreensTest.kt`
- Test: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvProfileNavigationTest.kt`

**Interfaces:**
- Produces: `TvProfileManagementScreen` with `onManagePin`, `onRemove`, and `onBack` actions.

- [ ] **Step 1: Add failing tests for management-only PIN/removal and Back hierarchy**

```kotlin
composeRule.onNodeWithTag("profile-management:alice:tile").performClick()
composeRule.onNodeWithContentDescription("${strings.profilePin}: Alice").assertExists()
composeRule.onNodeWithContentDescription("${strings.removeProfile}: Alice").assertExists()
```

- [ ] **Step 2: Run focused tests and verify RED**

- [ ] **Step 3: Add host-owned `managementVisible` and `managedProfileId` state**

Back dismisses modal flows first, then the selected management profile, then management mode. Existing PIN and removal repository calls are reused unchanged.

- [ ] **Step 4: Resolve PIN state for each exact profile into `TvProfilePresentation`**

Recompute the immutable list when profile IDs or PIN operations change. Never use another profile's binding or avatar URL as fallback.

- [ ] **Step 5: Run unit and instrumentation tests and verify GREEN**

- [ ] **Step 6: Commit host integration and tests**

### Task 4: Profile picker device gate

**Files:**
- Test: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvProfileScreensTest.kt`

- [ ] **Step 1: Build and install `:app-tv:assembleRelease` on the Fire TV**
- [ ] **Step 2: Verify cold-launch selection, avatar fallback, Add, Manage, PIN cancel, deletion cancel, and selection-screen Back exit confirmation**
- [ ] **Step 3: Capture one screenshot and UI-tree focus proof**
- [ ] **Step 4: Run `:app-android:assembleDebug` from the repository root**
