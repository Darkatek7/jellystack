# Jellystack TV: Three-Release Improvement Roadmap

## Summary

Harden the current beta before expansion, then deliver real household profiles, followed by faster cinematic discovery.

The immediate blocker is a reproducible movie-detail crash in `design-tv/src/main/kotlin/dev/jellystack/design/tv/TvDetailScreens.kt`. The current JVM and device tests pass despite this crash, proving that production-root and asynchronous-state coverage must be added.

These are gated beta releases, not a GA commitment. If a release fails its exit criteria, later features move right.

## Release 1 — Trustworthy core and ten-foot polish

- Replace positional detail focus lists with one immutable `TvDetailUiState` whose keyed sections drive both rendering and focus. Cover episodes, cast, and similar sections appearing, disappearing, and reordering asynchronously.
- Incrementally extract a lifecycle-aware `TvAppStateHolder`; keep platform integrations in `app-tv` and expose immutable state/actions to `design-tv`.
- Enforce the Back hierarchy: dismiss keyboard → dialog/panel → folder/path → detail/category → expanded rail → system exit. Back must never open the rail.
- Restore focus by stable section/item ID after refresh, paging, recreation, and wake; use the nearest horizontal position when no remembered item survives.
- Fix Search edit/browse modes, restored-query resubmission, clipped first results, Discover-to-Connections return behavior, partial-error focus stealing, and trailer/audio resuming on wake.
- Apply TV-safe insets, make the expanded rail an overlay instead of reflowing content, separate selection styling from focus, localize item counts, omit invalid metadata such as zero ratings, and provide a designed missing-art fallback.
- Correct accessibility semantics: headings, checked/selected state, unique labels, decorative artwork, live-region status messages, and no focusable no-op loading placeholders.
- Replace device-model/emulator playback branches with runtime `supported / unsupported / unknown` capability snapshots and conservative fallback.
- Add TV unit, app assembly, lint, manifest, and AVD tests to required CI.

Exit criteria: no reproducible crash, silent playback, top-level Back loop, or focus trap; complete TV suite passes three consecutive runs without retry or quarantine.

## Release 2 — Household profiles and My List

- Introduce real Jellyfin-user profiles. Each profile has its own Jellyfin credentials, libraries, progress, favorites, permissions, optional Seerr identity, preferences, and My List.
- When multiple profiles exist, show the picker on cold launch. Background resume retains the active profile. Add a focusable avatar above—not inside—the five navigation destinations.
- Support adding profiles with existing password or Quick Connect flows, including multiple users on the same Jellyfin URL.
- Add optional four-digit profile PINs. Store only a versioned salted verifier in secure storage; after five failures apply a 30-second lockout. Recovery requires Jellyfin reauthentication.
- Switch profiles atomically: unlock target → stop playback, trailers, and SyncPlay → cancel the previous state generation → activate target → clear navigation/focus/detail state → bootstrap target caches → refresh. Never expose previous-profile content during the transition.
- If target authentication is expired, show Reconnect for that profile. Do not silently fall back to the prior user or another profile’s Seerr credentials.
- Add a Home “My List” row combining the active user’s Jellyfin favorites with profile-local saved Seerr titles. Available items Play; unavailable items open the existing Seerr detail/request flow.
- Removing a profile deletes its local preferences, saves, PIN, and unreferenced credentials, but never deletes the Jellyfin/Seerr server account itself.

### Preference ownership

| Device-global | Profile-scoped |
|---|---|
| App language, streaming quality, seek steps, segment-skip policy, subtitle size/background, motion/focus appearance, trailer behavior, connections | Audio/subtitle language and mode, remembered tracks, autoplay/resume, default playback speed, Home shelf order, My List |

Exit criteria: switching between two users on the same URL never leaks cached libraries, artwork tokens, progress, favorites, preferences, Seerr privileges, playback state, navigation, or focus.

## Release 3 — Find media faster with cinematic rows

- Introduce a shared cinematic browse shell for Library, populated Search, and Discover while retaining the current Home hero and five-destination rail.
- Library gets explicit `Browse` and `All Titles` modes:
  - Browse uses focus-reactive horizontal rows: Continue/Next Up where supported, Recently Added, My List/Favorites, then server-provided sections.
  - All Titles uses an adaptive paged poster grid; libraries without reliable posters use landscape cards. Back restores the exact Browse row/card.
- Add `LibraryBrowseQuery` with:
  - Sort: title, date added, release year; ascending or descending.
  - Filters: played/unplayed, favorites, genre, year, and media type for mixed libraries.
  - Remember selections per profile and library.
  - Keep non-default query pages session-local so they never overwrite the canonical cached library.
- Add visible selected-item actions—Play/Resume, Details, My List/Favorite, Played/Unplayed—without depending on long-press or Menu keys.
- Add capability-gated voice search through an external system recognizer. Keyboard search remains universal; cancel/error preserves the previous query and unsupported Fire TV environments show no dead microphone control.
- Move query, source, edit mode, and results into one `TvSearchCoordinator`; restored queries are reissued automatically.
- Merge exact Jellyfin/Seerr duplicates using provider identity, showing one result with Play or Request actions. Never deduplicate by title text.
- Add user overrides for Reduced Motion and High-Contrast Focus, building on system animation-scale handling from Release 1.

## UI and interaction contract

- Design against a 960×540dp logical reference. Keep text, actions, focused bounds, scale, and glow inside 48dp horizontal and 27dp vertical safe insets.
- Expanded rail is a 224–232dp scrimmed overlay; content never shifts when it opens.
- Cinematic landscape cards use approximately 232×131dp artwork, a 56dp opaque metadata band, 16dp spacing, and room for the focus halo. Titles never sit directly over uncontrolled artwork.
- Focus uses three signals: 1.05–1.06 scale, a dual-tone ring/glow with at least 3:1 contrast against light and dark surroundings, and elevation/surface change. Selection uses a check or accent bar, not the focus treatment.
- Focused cards update the backdrop after a 120ms dwell. Cancel stale loads, retain the previous image until replacement, then crossfade over 220–260ms.
- Trailer preview waits at least 1.5 seconds, obeys the sound preference, and never starts or resumes on wake. Reduced Motion disables scaling/crossfades and uses an immediate high-contrast ring.
- All Titles uses adaptive portrait cards with a 136dp minimum width: normally five columns at the reference width and four at large font scale.
- Search opens the keyboard only on fresh entry or explicit Center. Back first closes the keyboard and enters result-browse mode.
- Loading retains a stable non-clickable status anchor; refresh and paging retain content/focus; partial errors remain inline; empty states expose only real recovery actions.
- Minimum action size is 48×48dp; normal text meets 4.5:1 contrast and large text/icons/focus meet 3:1. Voice and enhanced accessibility follow current Android TV quality guidance.

## Interfaces and migration

- Add `TvDetailUiState.sections: List<TvDetailSection>` and semantic `TvFocusAnchor(sectionId, itemId, destination)`; resolve current list indices only during focus materialization.
- Add `HouseholdProfile`, `ActiveProfileState`, `ProfileSwitchCoordinator`, and `ProfilePreferencesRepository`.
- Keep each authenticated `ManagedServer` record as the cache identity. Change duplicate detection from URL-only to normalized URL plus authenticated principal, allowing separate users on one endpoint without rewriting existing media caches.
- Profiles reference exact Jellyfin and optional Seerr connection IDs. Profile environment providers must never fall back across profiles.
- The next database migration creates:
  - A profile/binding table.
  - A profile-local saved-media table keyed by profile, media type, and provider ID.
  - Provider IDs on cached Jellyfin list items for efficient My List and Search reconciliation.
- Migration synthesizes one default profile from existing active connections, preserves connection IDs and caches, copies profile-scoped settings, and binds the legacy Seerr credential only to that default profile.
- Define `MediaIdentity` using media type plus TMDB, then TVDB, then source-local ID. No fuzzy title/year matching.
- Seerr saves remain local because Seerr’s published watchlist endpoint is Plex-specific and read-only for this use case.

## Verification and release gates

Required root gate for every release:

```text
spotlessCheck
detekt
:players:testDebugUnitTest
:design-tv:testDebugUnitTest
:app-tv:testDebugUnitTest
:app-tv:lintDebug
:app-tv:assembleDebug
verifyUniqueAndroidVersionCodes
:app-tv:verifyTvReleaseManifestPermissions
:app-android:assembleDebug
```

Required TV device gate:

```text
:design-tv:connectedDebugAndroidTest
:app-tv:connectedDebugAndroidTest
```

Add these scenarios:

- Release 1: actual Jellyfin/Seerr detail screens with asynchronous sections, full Home → Library → Detail → Playback → Back smoke flow, top-level exit, wake/recreation, keyboard behavior, missing art, partial errors, media keys, and playback capability combinations.
- Release 2: two users on one URL, concurrent loads during switching, expired credentials, PIN lockout/recovery, process death at the picker, profile deletion, preference isolation, exact My List reconciliation, and Seerr-unbound profiles.
- Release 3: query cancellation and paging boundaries, canonical-cache isolation, voice available/cancelled/unsupported/error states, Search deduplication, row/grid restoration, held-key traversal, and action-strip reachability.
- Golden screenshots at 720p, 1080p, and 4K; English and German; font scales 1.0 and 1.5; white/black artwork contrast fixtures.
- Manual passes on Android/Google TV and Fire TV, including minimum-supported and current OS profiles, 1080p/4K, network loss, sleep/wake, output switching, and 30-minute playback.
- Low-tier Fire TV browse target: p95 frame time no worse than 33.3ms, no queued backdrop animations, blank frames, focus loss, or accidental activation across 100 D-pad actions.
- Release candidates additionally run `:app-tv:bundleRelease :app-tv:assembleRelease`, certificate verification, local artifact testing, and a seven-day closed-test soak.

## Assumptions and deferrals

- Preserve the current cinematic Home hero, categorized Settings, and five top-level destinations. Settings remains a stable surface UI rather than becoming cinematic.
- Failed release gates shift later work; they are never waived to preserve the three-release schedule.
- This roadmap does not declare GA. GA follows a separate stabilization/closed-test release.
- Defer the full Seerr Pending → Processing → Ready-to-Watch lifecycle, Live TV/DVR, Engage/global search, downloads, Cast Connect, PiP, local parental controls, and broad Seerr administration.
- Defer frame-rate matching, advanced HDR/passthrough controls, and manual codec overrides until the capability foundation is proven.
- No manufacturer/model production branches, wholesale `design-tv` rewrite, navigation replacement, or new microphone permission.
