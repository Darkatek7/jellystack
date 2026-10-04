# Jellystack TV Release 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real household profiles whose Jellyfin/Seerr identity, cached media, preferences, playback state, focus, and My List remain isolated during creation, switching, reconnect, deletion, process death, and concurrent loads.

**Architecture:** SQLDelight stores profile bindings and provider-keyed local saves while each existing authenticated `ManagedServer.id` remains the Jellyfin cache identity. A profile repository owns exact connection bindings, a secure PIN repository owns only versioned salted verifiers and lockout metadata, and `ProfileSwitchCoordinator` executes an ordered generation switch before exposing target content. TV profile UI consumes immutable profile state through the Release 1 app holder.

**Tech Stack:** Kotlin Multiplatform, SQLDelight migrations/stores, Multiplatform Settings, platform secure storage, Kotlin coroutines/StateFlow, Jetpack Compose for TV, Koin, Jellyfin password and Quick Connect coordinators.

## Global Constraints

- A profile references one exact Jellyfin connection ID and zero or one exact Seerr connection ID; environment providers never fall back to another connection or profile.
- Keep each authenticated `ManagedServer.id` as the cache identity and allow multiple Jellyfin principals on the same normalized URL.
- Profile switch order is unlock target → stop playback/trailers/SyncPlay → cancel previous generation → activate target → clear navigation/focus/detail state → bootstrap target caches → refresh.
- During a switch, no previous-profile library, artwork token, progress, favorite, preference, Seerr privilege, playback state, navigation, or focus may be rendered.
- A four-digit PIN stores only a versioned salted verifier in secure storage; five failed attempts cause a 30-second lockout and recovery requires Jellyfin reauthentication.
- Removing a profile deletes only local profile preferences, saves, PIN material, and unreferenced connection credentials; it never deletes server-side Jellyfin or Seerr accounts.
- Device-global preferences remain app language, streaming quality, seek steps, segment-skip policy, subtitle size/background, motion/focus appearance, trailer behavior, and connections.
- Profile-scoped preferences are audio/subtitle language and mode, remembered tracks, autoplay/resume, default playback speed, Home shelf order, and My List.
- `MediaIdentity` uses media type plus TMDB, then TVDB, then source-local ID; title/year or other fuzzy matching is forbidden.
- Seerr saves remain local and are not written to the Plex-specific Seerr watchlist endpoint.
- Run every Gradle command from the repository root and finish each task with its focused tests before committing.

---

### Task 1: Profile schema, provider IDs, saved-media store, and migration

**Files:**
- Create: `shared-database/src/commonMain/sqldelight/dev/jellystack/database/HouseholdProfiles.sq`
- Modify: `shared-database/src/commonMain/sqldelight/dev/jellystack/database/JellyfinItems.sq`
- Create: `shared-database/src/commonMain/sqldelight/dev/jellystack/database/migrations/8.sqm`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfileModels.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfileStore.kt`
- Create: `shared-database/src/commonMain/kotlin/dev/jellystack/database/SqlDelightProfileStore.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinModels.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinStores.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/JellyfinBrowseRepository.kt`
- Modify: `shared-database/src/commonMain/kotlin/dev/jellystack/database/JellyfinStores.kt`
- Modify: `shared-database/src/androidUnitTest/kotlin/dev/jellystack/database/DatabaseMigrationTest.kt`
- Create: `shared-database/src/commonTest/kotlin/dev/jellystack/database/SqlDelightProfileStoreTest.kt`

**Interfaces:**
- Produces `HouseholdProfile`, `ProfileConnectionBinding`, `SavedMediaRecord`, `MediaIdentity`, and `MediaProviderIds(tmdbId, tvdbId, sourceLocalId)`.
- `ProfileStore` supports observe/list/get/upsert/delete profile, exact bindings, and saved-media CRUD keyed by profile/media type/provider identity.
- Adds nullable TMDB and TVDB provider ID columns to cached Jellyfin list items and maps network `ProviderIds` into those columns.

- [ ] **Step 1: Write failing migration and identity tests**

Extend migration tests from schema versions 1, 5, and 7 to the latest version. Assert the profile, binding, saved-media, and provider-ID columns exist; legacy server/cache rows survive; and the saved-media composite key rejects duplicate identity within one profile while allowing the same media in another profile. Add `MediaIdentity` precedence tests for TMDB, TVDB, source-local, differing media types, and title collisions.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-database:testDebugUnitTest :shared-core:testDebugUnitTest --tests "*MediaIdentityTest" --console=plain
```

Expected: schema/store/types are missing.

- [ ] **Step 3: Implement schema version 8 and stores**

Create profile rows with stable ID, display name, avatar seed, created/updated timestamps, and optional last-active timestamp. Create exact binding rows for Jellyfin and optional Seerr connection IDs. Create profile-local saved rows containing media type, TMDB/TVDB/source-local identity, title, artwork paths, and timestamps. Add provider columns to `jellyfin_items`, update insert/select mapping, and keep existing primary cache keys unchanged.

- [ ] **Step 4: Persist provider IDs from real list responses**

Add `ProviderIds` to the list DTO if not already present, request it in every browse/search/list field set, normalize TMDB/TVDB values, and round-trip them through `JellyfinItemRecord` and `JellyfinItem`. Do not fetch detail solely to compute identity.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :shared-database:testDebugUnitTest :shared-core:testDebugUnitTest --console=plain
```

Expected: migration, store, identity precedence, and provider-ID round-trip tests pass.

- [ ] **Step 6: Commit**

```powershell
git add shared-database/src/commonMain/sqldelight shared-database/src/commonMain/kotlin/dev/jellystack/database shared-database/src/androidUnitTest shared-database/src/commonTest shared-core/src/commonMain/kotlin/dev/jellystack/core/profile shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin shared-core/src/commonTest
git commit -m "feat(profiles): add profile and saved-media storage"
```

### Task 2: Authenticated-principal connection identity and legacy default profile

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/server/ServerStore.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/server/ServerRepository.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/server/ServerModels.kt`
- Modify: `shared-database/src/commonMain/sqldelight/dev/jellystack/database/Servers.sq`
- Modify: `shared-database/src/commonMain/kotlin/dev/jellystack/database/SqlDelightServerStore.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/HouseholdProfileRepository.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/HouseholdProfileRepositoryTest.kt`
- Modify: `shared-core/src/commonTest/kotlin/dev/jellystack/core/server/ServerRepositoryTest.kt`
- Modify: `shared-core/src/commonTest/kotlin/dev/jellystack/core/server/JellyfinQuickConnectCoordinatorTest.kt`

**Interfaces:**
- Server duplicate identity becomes `(ServerType, normalizedBaseUrl, authenticatedPrincipal)` where Jellyfin principal is the authenticated Jellyfin user ID and Seerr principal is its authenticated API user ID when available.
- Produces `HouseholdProfileRepository.ensureLegacyDefaultProfile()` and exact create/update/remove/bind APIs.
- The legacy bootstrap reads current active connection IDs once, creates one default profile, copies profile-scoped legacy values later through Task 4, and binds the legacy Seerr connection only to that profile.

- [ ] **Step 1: Write failing same-URL and migration-bootstrap tests**

Assert two authenticated Jellyfin users with distinct user IDs can register on the same normalized URL and receive distinct connection IDs; the same principal remains a duplicate; updates by ID preserve creation time and caches. Assert a legacy installation creates exactly one default profile bound to existing active Jellyfin and Seerr IDs and repeated bootstrap is idempotent.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --tests "dev.jellystack.core.server.ServerRepositoryTest" --tests "dev.jellystack.core.profile.HouseholdProfileRepositoryTest" --console=plain
```

Expected: current URL-only duplicate detection rejects the second user and profile bootstrap APIs are missing.

- [ ] **Step 3: Move duplicate detection after authentication**

For password registration, authenticate first, derive the principal from returned credentials, then check exact identity. For Quick Connect/authenticated registration, use the supplied user ID. Replace URL-only store lookup with identity lookup/list comparison without exposing access tokens or API keys in equality, logs, or exceptions.

- [ ] **Step 4: Implement idempotent legacy profile synthesis**

Use the exact active connection IDs supplied by `ActiveServerPreferenceRepository`; never choose another server when the active ID is missing. Preserve those `ManagedServer.id` values and all existing caches. Mark bootstrap completion only after the profile and both bindings commit successfully.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest :shared-database:testDebugUnitTest --console=plain
```

Expected: same-URL multi-user, duplicate-principal, Quick Connect, update, and idempotent bootstrap cases pass.

- [ ] **Step 6: Commit**

```powershell
git add shared-core/src/commonMain/kotlin/dev/jellystack/core/server shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/HouseholdProfileRepository.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/server shared-core/src/commonTest/kotlin/dev/jellystack/core/profile shared-database/src/commonMain/sqldelight/dev/jellystack/database/Servers.sq shared-database/src/commonMain/kotlin/dev/jellystack/database/SqlDelightServerStore.kt
git commit -m "feat(profiles): support principals on one server"
```

### Task 3: Secure profile PIN, lockout, and reauthentication recovery

**Files:**
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePinRepository.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePinModels.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/ProfilePinRepositoryTest.kt`
- Modify: `shared-core/src/commonTest/kotlin/dev/jellystack/core/security/FakeSecureStore.kt`

**Interfaces:**
- Produces `ProfilePinState` (`NotConfigured`, `Ready`, `Locked(until)`), `ProfilePinResult` (`Unlocked`, `Rejected(remainingAttempts)`, `Locked(until)`), and repository actions `configure`, `verify`, `remove`, and `recoverAfterReauthentication`.
- Secure keys contain a versioned salted iterative verifier and lockout metadata; raw PIN text is never persisted or logged.
- Clock, salt generator, and hash work factor are injected for deterministic tests.

- [ ] **Step 1: Write failing verifier and lockout tests**

Assert only exactly four numeric digits are accepted; equal PINs use different salts; secure values do not contain the raw PIN; correct verification succeeds; failures report 4, 3, 2, 1 remaining; the fifth locks for 30 seconds; verification stays locked until the injected clock reaches the deadline; success resets failures; process recreation retains lockout; recovery without successful Jellyfin reauthentication cannot clear the PIN.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --tests "dev.jellystack.core.profile.ProfilePinRepositoryTest" --console=plain
```

Expected: PIN models and repository are missing.

- [ ] **Step 3: Implement versioned salted verification**

Store a format such as `v1:<work-factor>:<salt>:<digest>` in secure storage, compare complete digests without early exit, and keep all keys namespaced by profile ID. Persist failure count and lockout deadline securely so process death cannot reset brute-force protection. Never expose verifier, salt, or PIN through `toString`.

- [ ] **Step 4: Implement reauthentication recovery**

Accept a suspend reauthentication action bound to the profile’s exact Jellyfin connection. Clear PIN/lockout only when that action succeeds for the target principal; failure leaves all PIN material intact.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --tests "dev.jellystack.core.profile.ProfilePinRepositoryTest" --console=plain
```

Expected: validation, salted storage, five-attempt lockout, process recreation, reset, and recovery cases pass.

- [ ] **Step 6: Commit**

```powershell
git add shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePinRepository.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePinModels.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/ProfilePinRepositoryTest.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/security/FakeSecureStore.kt
git commit -m "feat(profiles): secure TV profile PINs"
```

### Task 4: Profile preferences, exact environments, and atomic switching

**Files:**
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfilePreferencesRepository.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ActiveProfileRepository.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfileEnvironmentProvider.kt`
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/ProfileSwitchCoordinator.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettings.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences/AppSettingsRepository.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/ServerRepositoryEnvironmentProvider.kt`
- Modify: `shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyseerr/JellyseerrEnvironmentProvider.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/ProfilePreferencesRepositoryTest.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/ProfileSwitchCoordinatorTest.kt`

**Interfaces:**
- Produces `ActiveProfileState` variants `Picker`, `Locked`, `Switching`, `Bootstrapping`, `Active`, and `Reconnect`.
- `ProfilePreferencesRepository` namespaces only profile-scoped fields by profile ID and exposes an immutable `ProfilePreferences` flow.
- `ProfileSwitchCoordinator.switchTo(profileId)` is generation-based and accepts ordered hooks for stopping playback/trailers/SyncPlay, cancelling old work, activating exact connections, clearing TV state, bootstrapping, and refreshing.

- [ ] **Step 1: Write failing preference and switch-order tests**

Create two profiles with opposite audio/subtitle/autoplay/resume/speed/shelf values and assert device-global settings stay shared while profile values remain isolated. Record every switch hook and assert exact order. Start concurrent old-profile loads, switch, then complete them; assert stale generations cannot publish. Cover expired Jellyfin auth yielding `Reconnect`, Seerr-unbound profile yielding no Seerr environment, and no fallback to another active server.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --tests "dev.jellystack.core.profile.ProfilePreferencesRepositoryTest" --tests "dev.jellystack.core.profile.ProfileSwitchCoordinatorTest" --console=plain
```

Expected: profile preference, active-state, environment, and switch APIs are missing.

- [ ] **Step 3: Split settings ownership and migrate legacy values**

Keep device-global keys in `AppSettingsRepository`. Move profile-scoped keys into `ProfilePreferencesRepository` with `profile.<id>.` namespaces. During legacy default profile synthesis, copy the old profile-scoped values once, then remove or ignore legacy keys without changing device-global values.

- [ ] **Step 4: Implement exact environment providers and atomic switch**

Resolve Jellyfin/Seerr environments only through the current profile bindings and exact connection IDs. While `Switching` or `Bootstrapping`, expose no content environment. Increment generation before cancellation and require every async publisher to match the active generation. If target authentication is expired, expose `Reconnect` for that profile and never reactivate the prior one silently.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --console=plain
```

Expected: ownership migration, two-profile isolation, ordered switching, stale-load rejection, expired auth, and Seerr-unbound cases pass.

- [ ] **Step 6: Commit**

```powershell
git add shared-core/src/commonMain/kotlin/dev/jellystack/core/profile shared-core/src/commonMain/kotlin/dev/jellystack/core/preferences shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyfin/ServerRepositoryEnvironmentProvider.kt shared-core/src/commonMain/kotlin/dev/jellystack/core/jellyseerr/JellyseerrEnvironmentProvider.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/profile
git commit -m "feat(profiles): isolate state and switching"
```

### Task 5: Cold-launch picker, rail avatar, add/reconnect/delete flows

**Files:**
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileScreens.kt`
- Create: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileState.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAppStateHolder.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt`
- Modify: `app-tv/src/main/java/app/jellystack/tv/di/TvAppModule.kt`
- Create: `design-tv/src/test/kotlin/dev/jellystack/design/tv/TvProfileStateTest.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvProfileScreensTest.kt`

**Interfaces:**
- Multiple profiles on cold launch render a picker; one migrated profile may activate directly. Background resume retains active profile.
- A focusable avatar control is placed above and outside the five destination entries.
- Add supports password and existing Quick Connect coordinators; reconnect is bound to the selected profile; delete confirms local-only impact.

- [ ] **Step 1: Write failing picker state and Compose tests**

Cover cold launch with zero/one/two profiles, process death at picker, background resume, PIN entry and lockout countdown, exact Reconnect profile, password/Quick Connect add on a repeated URL, avatar-to-destination focus order, dialog focus trapping/return, and delete confirmation wording. Assert the avatar is not counted among the five destinations.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest --tests "dev.jellystack.design.tv.TvProfileStateTest" --console=plain
```

Expected: profile UI state and screens are missing.

- [ ] **Step 3: Implement immutable profile presentation and flows**

Render picker/lock/reconnect/switching states before authenticated content. Reuse connection coordinators through action interfaces instead of moving them into Composables. Keep active-profile resume in the holder, and clear route/focus/detail state when the switch coordinator reaches its clear-state hook.

- [ ] **Step 4: Add avatar rail entry and deletion cleanup**

Place a separate avatar target above the destinations with a unique label and stable focus ID. On delete, remove profile settings/saves/PIN and remove a connection only if no remaining profile binding references it; never call a server-side delete endpoint.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat :design-tv:testDebugUnitTest :design-tv:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.jellystack.design.tv.TvProfileScreensTest' --console=plain
```

Expected: picker, PIN, reconnect, add, avatar focus, process-death, and delete tests pass.

- [ ] **Step 6: Commit**

```powershell
git add design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvProfileState.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvAppStateHolder.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvNavigation.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt design-tv/src/test/kotlin/dev/jellystack/design/tv/TvProfileStateTest.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvProfileScreensTest.kt app-tv/src/main/java/app/jellystack/tv/di/TvAppModule.kt
git commit -m "feat(tv): add household profile picker"
```

### Task 6: Exact My List reconciliation and Release 2 isolation gate

**Files:**
- Create: `shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/MyListRepository.kt`
- Create: `shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/MyListRepositoryTest.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt`
- Modify: `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt`
- Create: `design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvMyListTest.kt`
- Modify: `app-tv/src/androidTest/java/app/jellystack/tv/TvProductionRootSmokeTest.kt`

**Interfaces:**
- Produces `MyListItem.Available(jellyfinItem, identity)` and `MyListItem.Unavailable(savedSeerrItem, identity)`.
- Merge order is active user Jellyfin favorites plus profile-local Seerr saves, deduplicated only by `MediaIdentity` provider precedence.
- Available entries play/open Jellyfin detail; unavailable entries open the existing Seerr detail/request flow.

- [ ] **Step 1: Write failing reconciliation tests**

Cover exact TMDB merge, TVDB fallback, source-local fallback, same-title distinct providers, movie/series ID collisions, active-profile filtering, saved Seerr becoming available after refresh, unbound Seerr profiles, and stable shelf order. Assert no title/year deduplication.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat :shared-core:testDebugUnitTest --tests "dev.jellystack.core.profile.MyListRepositoryTest" --console=plain
```

Expected: My List repository and result types are missing.

- [ ] **Step 3: Implement repository and TV actions**

Observe active-profile favorites and local saves, reconcile by exact identity, and publish no list while profile state is switching/bootstrapping. Add Home My List row and visible save/remove actions on Jellyfin and Seerr detail. Route available/unavailable entries to Play/Details or Request respectively.

- [ ] **Step 4: Add end-to-end isolation scenarios**

Extend production-root tests for two users on one URL, concurrent loads during switching, expired credentials, PIN lockout/recovery, process death at picker, profile deletion, preference isolation, exact My List reconciliation, Seerr privileges, navigation/focus reset, and Seerr-unbound profiles.

- [ ] **Step 5: Run Release 2 gates**

```powershell
.\gradlew.bat spotlessCheck detekt :shared-core:testDebugUnitTest :shared-database:testDebugUnitTest :players:testDebugUnitTest :design-tv:testDebugUnitTest :app-tv:testDebugUnitTest :app-tv:lintDebug :app-tv:assembleDebug verifyUniqueAndroidVersionCodes :app-tv:verifyTvReleaseManifestPermissions :app-android:assembleDebug --console=plain
.\gradlew.bat :design-tv:connectedDebugAndroidTest :app-tv:connectedDebugAndroidTest --console=plain
```

Expected: every command exits zero and the two-user isolation suite reports no leaks.

- [ ] **Step 6: Commit**

```powershell
git add shared-core/src/commonMain/kotlin/dev/jellystack/core/profile/MyListRepository.kt shared-core/src/commonTest/kotlin/dev/jellystack/core/profile/MyListRepositoryTest.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvBrowseScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvJellystackRoot.kt design-tv/src/main/kotlin/dev/jellystack/design/tv/TvStrings.kt design-tv/src/androidTest/kotlin/dev/jellystack/design/tv/TvMyListTest.kt app-tv/src/androidTest/java/app/jellystack/tv/TvProductionRootSmokeTest.kt
git commit -m "feat(tv): add profile-scoped My List"
```
