# Release Notes

## 0.16.0-tv-beta.4

- Keep Spotlight on the selected item so trailer previews can play without interruption.
- Show trailer previews only in the exact focused card, even when media appears in multiple rows.
- Open Home media and Movies or Shows libraries without endless loading.
- Keep Discover and Upcoming cards in one consistent poster format without focus bouncing.
- Restore remote focus predictably across rows, routes, and the sidebar.

## 0.16.0-tv-beta.3

- Explore Home through a larger, immersive carousel built from recently added Jellyfin media.
- Move through carousel slides and Home rows predictably with a TV remote while keeping focus stable.
- Preview local Jellyfin trailers from Home cards after a short pause, with separate preview and sound settings.
- Enjoy redesigned full-bleed details with clearer actions and reliable navigation back to the complete hero artwork.
- Keep the hero first with or without server Home Sections, including a latest-local-media fallback when nothing was added in the last 30 days.

## 0.16.0-tv-beta.2

- Negotiate playback codecs, HLS containers, and audio channel limits from the capabilities of each TV.
- Fix missing audio when playback starts and preserve working audio when changing languages.
- Restart HLS sessions cleanly when switching audio tracks instead of reusing stale transcodes.
- Keep Auto quality at the best reliable source resolution and bitrate while retaining manual quality controls.
- Improve compatibility for AV1, HEVC, H.264, AAC, AC3, and EAC3 across Android TV and Fire TV devices.

## 0.16.0-tv-beta.1

- Add a dedicated remote-first interface for Android TV, Google TV, and Fire TV.
- Browse Home Sections, libraries, Jellyfin and Seerr search, Discover, requests, and cinematic details from the couch.
- Use Quick Connect, playback controls, audio, subtitles, quality, speed, Stats for Nerds, and SyncPlay without touch.
- Restore row focus and scroll position after opening details, and stop playback cleanly when leaving the TV app.
- Ship one GMS-free TV build for closed Google TV testing and Amazon Live App Testing.

## 0.15.0

- Follow the Home Sections layout configured on compatible Jellyfin servers, with an Appearance setting to use Jellystack's default Home instead.
- Create and join Jellyfin SyncPlay groups, share current playback, and keep play, pause, and seeking synchronized.
- Select playback speed per session and open live Stats for Nerds with stream, codec, resolution, buffer, and dropped-frame details.
- Give Jellyfin administrators a dedicated Admin tab for library scans, server restart, library statistics, user management, and recent activity.
- Approve or decline pending Seerr requests from the Admin tab when the connected Seerr account has permission.

## 0.14.3

- Make Seerr request options follow the connected user's movie, series, 4K, advanced-request, and management permissions.
- Accept Jellyfin and Seerr addresses using IPv4, bracketed IPv6, custom ports, and reverse-proxy paths.
- Improve Android audio compatibility by advertising only codecs the device can decode and using explicit media audio focus.
- Apply the preferred or manually selected audio track to the actual stream, including HLS source re-resolution.
- Close Jellyfin playback sessions reliably and report the exact final watch position when leaving the player.
- Keep Discover search focused while typing, with debounced and cancellable network requests.
- Keep the Home spotlight visible and return to it after refreshing.
- Replace the crowded request-status chips with a compact status selector.

## 0.14.2

- Significantly reduce Android permissions and remove phone-state and broad storage access.
- Explain every remaining permission, its trigger, and current status in Settings.
- Keep Cast discovery and notification prompts contextual and optional.
- Harden credential semantics, release logging, and unencrypted HTTP sign-in.
- Publish the complete Kotlin Multiplatform project as open source under GPL-3.0.

## 0.14.1

- Connect Jellyfin with a secure six-digit Quick Connect code.
- Link Seerr automatically after Jellyfin Quick Connect when the server supports it.
- Switch to username and password whenever Quick Connect is unavailable.
- Use the same streamlined sign-in flow during onboarding and in Settings → Connections.
- Keep Quick Connect tokens and device identity stored securely without retaining a Jellyfin password.

## 0.14.0

- Explore movies, series, and episodes in a new immersive cinematic detail view.
- Play, favorite, download, and update seen status from the new Command Deck.
- Browse ratings, cast, episodes, extras, and similar titles without leaving Jellystack.
- Inspect richer production, video, audio, subtitle, HDR, and accessibility details.

## 0.13.0

- Control playback, language, subtitles, appearance, and downloads from the redesigned Settings hub.
- Choose separate Wi-Fi and mobile quality, resume behavior, seek intervals, and autoplay.
- Set preferred audio and subtitle languages, subtitle style, and remembered series choices.
- Manage download network behavior with consistently sized, better-spaced settings cards.

## 0.12.0

- Navigate Home, Library, Discover, and Requests with clearer back behavior.
- Enjoy a calmer Home spotlight and content that stays clear of navigation.
- Set up and manage Jellyfin and Seerr with cleaner, fully localized screens.
- Watch in immersive playback with stronger accessibility and App Lock recovery.
