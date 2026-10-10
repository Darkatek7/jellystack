# Jellystack TV Profile Picker Design

## Summary

The cold-launch profile picker will adopt a calm, avatar-first layout inspired by modern TV profile selectors while retaining Jellystack's colors, focus glow, typography, and safe-area rules. The primary screen contains only profile selection, Add Profile, and Manage Profiles. PIN changes and profile deletion move to a separate management screen so destructive or administrative actions do not compete with the normal launch path.

This is a presentation and interaction redesign over the existing household-profile model. It does not change credential ownership, profile switching atomicity, PIN verification, connection binding, or deletion semantics.

## Selection screen

The screen uses a centered grid of large circular avatar tiles with each real Jellyfin display name directly below its image. At the 960 x 540dp reference size, the layout normally shows four or five profiles per row while respecting the 48dp horizontal and 27dp vertical safe insets.

Each profile tile is one focus target. It uses Jellystack's dual-tone focus ring, 1.05-1.06 scale when motion is enabled, elevation, and surface change. The avatar image, name, PIN state, and decorative background are not separate focus targets.

The picker also contains:

- An Add Profile tile using the same geometry as a profile tile
- A separate Manage Profiles action below the grid
- A concise heading such as "Who's watching?"

The selection screen never places PIN or Delete buttons beside profile tiles. It does not show server URLs, credential IDs, or connection implementation details.

The most recently active profile receives initial focus when it still exists. Otherwise, the first profile receives focus. Center activates the focused profile or begins its PIN flow. With multiple profiles, cold launch (including a restore after process death) and a return after more than one minute in the background reopen the picker; a shorter trip keeps the active profile.

## Avatar and naming behavior

The picker uses the real Jellyfin user name and profile image associated with the profile's exact Jellyfin connection. It never falls back to another profile's user or image.

If an image is missing or fails to load, the tile shows a designed circular fallback containing the profile's first valid initial. Long names are limited to one centered line with an ellipsis, while the full unique profile name remains available to accessibility services. Image loading never changes tile order or focus.

## Management screen

Manage Profiles opens an explicit management mode owned by the profile host, outside the authenticated app's five-destination navigation stack. It uses the same avatar grid. Selecting a profile there exposes only management operations valid for that profile:

- Add, change, or remove profile PIN
- Remove local profile

Removing a profile always requires confirmation. It deletes local preferences, saved media, PIN data, profile bindings, and unreferenced credentials, but never deletes Jellyfin or Seerr server accounts. Removing the active profile first stops playback and previews and completes through the existing profile-removal coordinator.

Back from a PIN or confirmation dialog dismisses that dialog. Back from an individual management view returns to the management grid. Back from the management grid returns to profile selection. Back from the selection screen follows the app-level exit-confirmation contract rather than exiting immediately.

## State and data flow

`TvProfilePickerScreen` renders immutable profile presentation models and emits Select, Add, and Manage actions. `TvProfileManagementScreen` renders the same stable profile identities and emits Manage PIN, Remove, and Back actions. Neither screen reads credentials or server state directly.

The existing profile host resolves exact Jellyfin bindings into presentation data containing profile ID, display name, avatar URL, and PIN status. Loading generations prevent an old profile image or name from appearing after profile removal, reconnection, or process restoration.

Profile activation retains the existing atomic sequence: unlock target, stop playback and trailer preview, cancel the previous generation, activate the target, clear navigation and focus state, bootstrap target caches, and refresh. The visual redesign cannot relax those isolation rules.

## Loading and failure behavior

- Profile metadata loading retains stable tile positions.
- Avatar failure shows the initial fallback without an error dialog.
- Expired authentication routes that exact profile to Reconnect.
- PIN failures and lockout use the existing secure verifier and lockout policy.
- Add Profile and Reconnect failures remain in their dedicated flows.
- Profile removal failure leaves the profile visible and reports a recoverable error.
- Loading and errors do not create focusable placeholders or move focus to another profile.

## Accessibility and motion

The heading is marked as a heading. Every tile announces the full profile name and whether a PIN is required. Add Profile and Manage Profiles use unique action labels. Decorative background art is hidden from accessibility services.

Reduced Motion disables avatar scaling and animated background transitions while retaining the immediate high-contrast focus ring. Normal text meets 4.5:1 contrast, and icons, large text, and focus indicators meet 3:1 against both light and dark avatar fixtures.

## Verification

Unit and Compose tests cover:

- Real Jellyfin names and avatar URLs from exact profile bindings
- Initial focus on the last active profile and first-profile fallback
- Center activation and PIN routing
- Add Profile and Manage Profiles reachability
- PIN and Delete controls absent from the selection screen
- Management Back hierarchy
- Deletion confirmation and local-only deletion semantics
- Missing, failed, delayed, and reordered avatar loads
- Multiple users on the same Jellyfin URL
- Long and duplicate-looking display names with stable profile IDs
- Process recreation at selection and management screens
- Expired authentication and PIN lockout
- Reduced Motion, large font scale, and accessibility semantics

Physical Fire TV verification includes cold launch, profile selection, management, PIN entry, deletion cancellation, Exit confirmation, sleep and wake, and 100 D-pad actions without focus loss or accidental destructive activation. Golden screenshots cover 720p, 1080p, and 4K; English and German; font scales 1.0 and 1.5; and light, dark, missing, and transparent avatar fixtures.

## Non-goals

- Editing Jellyfin or Seerr server accounts from the picker
- Deleting remote server users
- Local parental-control rules beyond the existing optional profile PIN
- Automatic profile selection based on device identity
- Manufacturer or model-specific layouts
