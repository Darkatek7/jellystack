# Release Notes

## 0.18.0 (TV)

- Listen to music: artist and album pages, track lists, shuffle, and a now-playing screen. The next track starts by itself.
- Watch Live TV: a channel list with what is on now and next. It appears when your Jellyfin server has channels.
- Open a page for a person or a collection, and start a whole collection with Play all.
- Scroll Home and Discover far more smoothly on Fire TV, with fewer image loads while moving through a row.
- See Continue Watching and Next Up in the Watch Next row of Android TV and Google TV, and turn it off in Settings if you prefer. Not on Fire TV.
- Choose Jellystack library artwork as the Android TV and Google TV screensaver. It is off until you pick it.
- Set the picture to Fit, Zoom, or Stretch, and adjust audio and subtitle delay while watching.
- Match the display's refresh rate to the video in Settings. Off by default. Fire TV offers apps fewer modes, so 24p titles stay as they are there.
- Play DTS and TrueHD without a server transcode when the device has no decoder for them. This makes the app about 6 MB larger.
- Get more titles in Direct Play, because Jellyfin now learns what the output really handles, and fall back to the next decoder when one fails to start.
- Return to the card you left when you move back into a row, and get clearer loading, empty, and error screens.
- Add another account with fewer presses: the sign-in form goes straight to Connect when the server address is known, and the keyboard only opens after you press OK on a field.

## 0.17.3 (TV)

- Start about a quarter faster on Fire TV, with less memory use and an app about a quarter of its old size.
- Press Play on a series or season to start the episode in progress or the next unwatched one, instead of getting a decoder error.
- Open Discover without a crash when Seerr lists several requests for one title, and keep Home working with duplicate sections, nameless items, or blank titles.
- See the player right away with a loading screen, cancel a start with Back, and get Retry when a start fails.
- Keep the same stream through a short Wi-Fi drop instead of switching to a transcode, and stop right away with a clear error when the server rejects the session.
- Return to the sign-in screen when a Jellyfin session has expired, instead of looping on the profile.
- Leave the player when playback stops from outside it, such as the Home button, a media stop, or a SyncPlay stop, instead of staying on a black screen.
- Let the screen saver start after ten minutes of pause.
- Send voice and remote media commands through the same playback logic as the on-screen buttons, and show a pause caused by another app as paused.
- Reconnect SyncPlay after a connection drop, and open the player when the group starts a queue from another device.
- Stop the server transcode when a trailer preview ends, and fix crashes from marking titles watched while offline.
- See the app version, the last crash, and a recent log under Settings > Diagnostics. Nothing leaves the device.

## 0.17.2 (TV)

- Play with real Direct Play and surround passthrough; when Jellyfin has to transcode, it picks the best audio the TV supports (E-AC3 or AC3 5.1) and switches to stereo only when no sound arrives.
- Send Jellyfin the complete device profile, so it decides Direct Play and transcoding correctly instead of the app building its own streams.
- Keep AV1 video untouched on TVs with an AV1 decoder when only the audio has to be transcoded, which saves the server a full video encode.
- See Jellyfin SRT, ASS, and PGS subtitles; PGS no longer makes the server burn subtitles into the video.
- Add a second profile with a password for an account that is already on the TV, and sign Quick Connect profiles in to the household's Seerr automatically.
- See titles that another profile already requested as requested instead of requesting them again.
- Let rows scroll to the screen edges instead of being cut off, and edit a search after submitting it.
- Fix a crash when the app is opened again right after exiting it.
- Open books, music, playlists, and collections straight on their titles, and step back out of folders to the card you came from.
- Open the Playlists tile on Home as a library instead of a detail page.
- Load Seerr details from My List and after the app restarts, with a loading line and Retry when a load fails.
- Start dialogs on their default button, close the keyboard in Search with Back, and open the menu from Search again.
- Pause and resume a SyncPlay group from the TV, and read audio and subtitle tracks by language without codec clutter.
- Play the audio track you pick during Direct Play; choosing English no longer plays the German track.
- Fix dark boxes inside focused buttons, focus rings, the profile avatar outline, clipped text fields, long track names, library images taking over the screen, black frames before trailers, stalled trailers, German wording, and a playback error dialog the remote could not reach.

## 0.17.1 (TV)

- Add another profile without typing the Jellyfin server address again.
- Sign each profile in to its own Seerr account.
- Choose the profile on every start and after more than a minute in the background when the household has several profiles.
- See progress and watched marks from other devices on Home and in details while the app stays open.
- Fix the year line under two-line titles on Home and Discover, and show audio and subtitle languages in the app language.

## 0.17.0 (TV)

- Leave the beta: Jellystack TV 0.17.0 is the first stable release for Android TV, Google TV, and Fire TV.
- Watch with a redesigned player: clock and end time, a seek bar with chapters, segments, and trickplay thumbnails, plus chapters and episodes panels.
- Scrub with Left/Right while the controls are hidden, and pause or resume with Center.
- Continue to the next episode from an up-next card with artwork and a countdown.
- Browse a full-screen home spotlight that pages on its own, with trailers that take the screen while the remote rests.
- Find the same full-screen look in Library and Discover, with an All titles card at the end of each library.
- See series, episode, time left, progress, and watched marks on cards, and 4K, HDR, Dolby Atmos, and surround badges in details.
- Get the resume question from every Play button, reach seasons with the remote, and choose how long cards wait before trailers start.
- Fix quality and subtitle settings, the search field losing the keyboard, cut-off German labels, the PIN pad, and Seerr needing a restart after connecting.

## 0.16.0

- Connect to Jellyfin 12.0+ with password login or Quick Connect; Jellyfin 10.x servers keep working.
- Watch with working artwork, subtitles, and SyncPlay on Jellyfin 12.0+.
- Continue downloads that were queued before a Jellyfin server upgrade.
- Let administrators set a new password for a user instead of clearing it.

## 0.16.0-tv-beta.6

- Use household profiles with real Jellyfin names and avatars, optional PINs, isolated preferences, and My List.
- Browse Home, Library, Search, and Discover with a cinematic backdrop and trailer stage above compact card rows; stage buttons fade out while a trailer plays.
- Switch Library between cinematic Browse rows and a paged, filterable All Titles grid.
- Search Jellyfin and Seerr together, restore queries, and use voice search where the device supports it.
- Connect to Jellyfin 12.0+ for sign-in, Quick Connect, browsing, artwork, playback, subtitles, and SyncPlay.
- Open details on Play or Resume and leave them with one Back press.
- Reach the sidebar from every top-level screen, start vertical row moves at the first card, and confirm before exiting.
- Fix overlapping screens, focus highlight shape, Seerr Discover data, missing artwork, safe insets, wake behavior, and subtitle placement.

## 0.16.0-tv-beta.4

- Keep Spotlight on the selected item so trailer previews can play without interruption.
- Show trailer previews only in the exact focused card, even when media appears in multiple rows.
- Open Home media and Movies or Shows libraries without endless loading.
- Keep Discover and Upcoming cards in one consistent poster format without focus bouncing.
- Start every vertical Home row move at its first card and restore remote focus predictably across routes and the sidebar.
- Show episode names in readable white text and make Overview, Cast, episodes, and similar media reachable by remote.
- Skip server-provided intros, recaps, previews, commercials, and credits with per-type Off, Button, or Auto settings, plus Play Next during credits.

## 0.15.1

- Skip server-provided intros, recaps, previews, commercials, and credits directly from the player.
- Choose Off, Button, or Auto independently for each segment type in Settings.
- Play the next episode while credits are on screen.

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
