# Jellystack TV Cinematic Preview Design

## Summary

Jellystack TV will use one shared Netflix-style browse presentation for Home, Library Browse, populated Search, and Discover. A large, non-focusable preview stage occupies the upper portion of the screen while horizontal media rows scroll below it. Focusing a card updates the stage backdrop and metadata, then optionally starts its trailer in that same stage after a stable dwell. Cards never play video themselves. The compact five-destination sidebar remains visible.

This design replaces the current focusable Home trailer container and extends the existing focus-reactive cinematic backdrop infrastructure without changing detail navigation, the All Titles grid, profiles, or Settings.

## Scope

The shared presentation applies to:

- Home
- Library Browse mode
- Populated Search results
- Discover

The following retain specialized layouts:

- Library All Titles
- Jellyfin and Seerr detail screens
- Profile selection and management
- Settings
- Empty, loading, authentication, and connection screens

## Layout and components

`TvCinematicPreviewStage` is a presentation component responsible only for the current backdrop or video, title, metadata, description, and selected-item actions. Its image and video surfaces are never focusable or clickable.

At the 960 x 540dp reference size, the fixed stage is approximately 310dp tall and the row viewport begins around 270dp, overlapping only the stage's lower gradient. Its metadata remains inside the 48dp horizontal and 27dp vertical safe insets. The compact sidebar remains visible to the left, and browse content starts at `TvLayoutTokens.ContentStart`, so neither the stage nor rows can hide behind the rail. The same proportions scale to 720p, 1080p, and 4K logical layouts.

Horizontal rows occupy the lower viewport and may visually overlap the lower gradient of the stage. Cards retain the cinematic landscape treatment: approximately 232 x 131dp artwork, a 56dp opaque metadata band, 16dp spacing, and room for the focus halo. Video is never rendered inside a card.

`TvCinematicPreviewActions` exposes Play or Resume, Details, and My List or Favorite when those operations are valid. D-pad Up from a focused row card enters the action strip. D-pad Down returns to the exact originating card. Screens with no valid actions show metadata without focusable placeholders.

## State and data flow

A route-scoped `TvBrowsePreviewCoordinator` owns an immutable preview state containing:

- The focused card identity and source route
- The last successfully displayed backdrop
- Backdrop loading generation
- Trailer loading and playback generation
- Preview metadata and available actions
- Whether focus is currently in the rows or action strip

Cards report stable provider identity and `TvFocusAnchor` values to the coordinator. The coordinator does not deduplicate or reconcile media by title. Jellyfin cards use their Jellyfin source identity and provider IDs. Seerr cards use media type plus TMDB, then TVDB, then source-local ID.

After a card remains focused for 120ms, the coordinator requests its backdrop. A stale request is cancelled when focus, route, profile generation, or lifecycle changes. The previous valid backdrop stays visible until the replacement is ready, then crossfades over 220-260ms unless Reduced Motion is active.

After at least 1.5 seconds of uninterrupted focus, the coordinator may resolve and start a trailer. Jellyfin media prefers a playable local trailer. Seerr media uses an available provider trailer. Missing or failed trailers leave the backdrop and metadata visible without an error dialog.

There is one shared preview player instance, but every playback request carries the active route and profile generation. Output from an older generation is discarded and can never appear in the new route or profile.

## Focus and navigation

- Fresh entry focuses the first card in the first actionable row.
- Moving vertically to another row focuses that row's first card.
- Horizontal movement remains within the active row.
- Returning from a detail screen restores the exact originating row and card.
- D-pad Up from a row enters the selected-item actions when present.
- D-pad Down from the actions restores the originating card.
- Back follows the existing hierarchy and never opens the sidebar.
- The stage itself never participates in focus traversal.
- Loading and error surfaces never steal card focus.

Each route owns separate row, focus, backdrop, and preview state. Switching among Home, Library, Search, and Discover cannot expose another route's selected item or action strip.

## Playback and lifecycle behavior

Trailer playback obeys the existing trailer enablement and sound preferences. It never starts before the 1.5-second dwell, never resumes after wake, and stops immediately when any of the following occurs:

- Focus changes to another card
- The active route or profile changes
- A detail screen or full playback opens
- The app stops or sleeps
- The preview leaves composition

Rapid focus movement cancels stale resolution and playback work rather than queueing previews. A new card first displays its backdrop and only later replaces that image with video. Stopping a trailer restores the current card's backdrop without a blank frame.

Reduced Motion removes stage scaling and crossfades and uses immediate backdrop replacement with the high-contrast focus ring. It does not override the user's explicit trailer enablement preference.

## Failure handling

- Missing artwork uses the designed media fallback.
- Backdrop failure retains the previous valid image or fallback.
- Trailer absence or failure remains silent and preserves the backdrop.
- Network loss does not remove rows or move focus.
- Partial source errors remain inline with real recovery actions.
- Unsupported playback capability suppresses preview playback and uses the backdrop.
- No error state creates a focusable no-op element.

## Accessibility and performance

The preview title is a heading. Backdrop and video surfaces are decorative when the same media name is already announced by the focused card. Actions expose unique labels and correct selected or checked state. Status changes use concise live-region announcements without interrupting D-pad traversal.

Only the visible stage and row viewport render expensive artwork. The preview player has at most one active request, backdrop loads are generation-cancelled, and no animation queue is retained. On the low-tier Fire TV target, p95 frame time must be no worse than 33.3ms with no blank frames, focus loss, or accidental activation across 100 D-pad actions.

## Verification

Unit and Compose tests cover:

- Correct focused card identity reaching the stage
- The 120ms backdrop dwell
- The 1.5-second trailer dwell
- Cancellation of stale backdrop and trailer requests
- One active preview player generation
- Static, non-video cards and a non-focusable stage
- First-card focus on vertical row changes
- Exact focus restoration after detail navigation
- Action-strip Up and Down round trips
- Route and profile state isolation
- Sleep, wake, route change, profile switch, and disposal stopping previews
- Missing artwork, missing trailers, network errors, and unsupported capability
- Reduced Motion and trailer preference behavior

Device verification covers Home, Library Browse, populated Search, and Discover on the physical Fire TV using remote key events, UI-tree focus assertions, screenshots, crash logs, and a 100-action D-pad traversal. Golden coverage includes 720p, 1080p, and 4K; English and German; font scales 1.0 and 1.5; and light and dark artwork fixtures.

## Non-goals

- Video inside individual media cards
- Trailer autoplay in All Titles or detail screens
- A new player engine or navigation framework
- Manufacturer or model-specific production behavior
- Frame-rate matching, manual codec controls, or new microphone permissions
