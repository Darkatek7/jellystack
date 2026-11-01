# Android TV, Google TV, and Fire TV

Jellystack TV is a remote-first companion build in the same Kotlin Multiplatform repository. It shares server, authentication, metadata, settings, and playback code with Jellystack mobile while using a dedicated Compose for TV interface.

It uses the package ID `app.jellystack.mobile`; its `versionName` and `versionCode` live in `app-tv/build.gradle.kts`.

## Supported devices

- Android TV and Google TV on Android 7.0 or newer
- Fire TV devices on Fire OS 6 or newer
- 1080p and 4K landscape displays

The TV app does not include downloads, Cast, App Lock, biometrics, the admin dashboard, picture-in-picture, or touch navigation.

## Build

Run all commands from the repository root with JDK 17:

```powershell
.\gradlew.bat :app-android:assembleDebug
.\gradlew.bat :app-tv:assembleDebug
.\gradlew.bat :app-tv:assembleRelease :app-tv:bundleRelease
.\gradlew.bat verifyUniqueAndroidVersionCodes :app-tv:verifyTvReleaseManifestPermissions
```

Outputs:

- Debug APK: `app-tv/build/outputs/apk/debug/app-tv-debug.apk`
- Release APK: `app-tv/build/outputs/apk/release/app-tv-release.apk`
- Release AAB: `app-tv/build/outputs/bundle/release/app-tv-release.aab`

The TV AAB is a local store artifact. It must not be committed or attached to a GitHub release.

## Emulator and Fire TV installation

List connected devices and install the debug build:

```powershell
adb devices
adb -s <serial> install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
adb -s <serial> shell monkey -p app.jellystack.mobile -c android.intent.category.LEANBACK_LAUNCHER 1
```

For a physical Fire TV, enable Developer Options, ADB Debugging, and Apps from Unknown Sources. Connect with `adb connect <fire-tv-ip>:5555`, confirm the prompt on the TV, then use the same install command.

Test at minimum:

- Cold start, Home, Sleep/Wake, app switching, and Back behavior
- D-pad, Play/Pause, Rewind, Fast Forward, and Fire TV Menu
- Quick Connect and password fallback
- Direct Play and HLS with AAC, AC3, EAC3, Opus, and a forced-transcode case
- Audio and subtitle switching, quality, playback speed, Stats for Nerds, and SyncPlay
- Wi-Fi or Ethernet interruption and recovery
- A playback session longer than 30 minutes

## Audio playback

Playback requests Direct Play first. Jellyfin approves it only when container, video codec and the selected audio track fit the device profile; otherwise the server returns its own stream and transcodes unsupported audio to the best codec the TV reported (EAC3, then AC3, AAC, MP3), copying video and any supported audio bit-identically. Switching to a track the TV cannot decode asks the server for a new stream. If audio output does not start, playback falls back once to server-selected stereo AAC HLS. Check Stats for Nerds and the debug log line `Playback decision` when testing DTS or TrueHD sources.

DTS and TrueHD are never encoder targets (the server cannot encode them), so they only appear as Direct Play inputs in Matroska, and only when the audio output route takes them as a bitstream or the FFmpeg software decoder can play them (next paragraph). The profile is built from a fresh probe for every playback: Media3's `AudioCapabilities` (the logic ExoPlayer's audio sink uses, including the system surround-sound setting, HDMI/ARC and the AudioManager direct profiles on API 29+) decides which of AC3, EAC3, DTS and TrueHD pass through and with how many channels, and that is merged with the decoder list. A TV on stereo PCM or Bluetooth gets none of them, so those tracks are transcoded; a soundbar plugged in later changes the next playback, not the one running. DTS has one ceiling for all flavours (6 for core-only sinks, 8 with DTS-HD), because Jellyfin reports every DTS variant as `dts`.

FFmpeg software decoding (`org.jellyfin.media3:media3-ffmpeg-decoder` 1.9.0+1, GPL-3.0, linked by `app-tv` only, so the phone APK is unchanged) plays DTS and TrueHD that the TV has neither a decoder nor a passthrough route for, instead of forcing a server transcode. The renderer is appended after Media3's platform audio renderers, so a platform decoder or passthrough always wins and FFmpeg only gets what is left. When `FfmpegLibrary` loads and supports the codec, the profile declares DTS and TrueHD for Direct Play in Matroska with up to 8 channels (also on core-only passthrough sinks), and AC3/EAC3 with up to 6 channels only on devices that have neither a decoder nor passthrough for them. FFmpeg codecs never become transcode targets, so the transcode lists are unchanged. The extension is a precompiled build against Media3 1.9.0 running on 1.10.1; re-check it on a device whenever Media3 is upgraded. The decoder runs on the CPU: verify on a Fire TV Stick and a Google TV Streamer that 5.1 and 7.1 DTS-HD MA and TrueHD play without stutter and that the multichannel PCM is downmixed correctly on a stereo TV.

## Video range and Dolby Vision

Direct Play of HEVC and AV1 is limited with a `VideoRangeType` condition on the codec profile (not required, so a stream the server cannot classify is not blocked). Hardware decoder profile levels give HDR10 and HLG (a `Main10` decoder counts as both; the display is not consulted, the device tone-maps). Dolby Vision streams with an HDR10, HLG or SDR base layer (`DOVIWithHDR10`, `DOVIWithHLG`, `DOVIWithSDR`, `DOVIWithHDR10Plus`) play as that base layer on any such decoder, because Media3 falls back to the plain HEVC/AV1 decoder for Dolby Vision profile 8 and AV1 profile 10. Pure profile 5 (`DOVI`) and profile 7 (`DOVIWithEL`) have no such fallback, so they are only direct-played with a hardware Dolby Vision decoder for that profile on a display that lists Dolby Vision; otherwise the server transcodes them. A decoder that reports no profile levels leaves the codec unrestricted. Stats for Nerds shows `Dolby Vision P<profile>` for Dolby Vision tracks.

Bitmap subtitles: PGS is rendered by the player. DVD (VobSub), DVB and XSUB subtitles are never fetched as `.sup`; selecting one, also during Direct Play, renegotiates with the server, which burns it into a transcoded stream.

## Video playback

- Decoder fallback is enabled: if the preferred (usually hardware) decoder fails to start, Media3 tries the next decoder before the error reaches the server-side fallback chain.
- Picture size (Fit, Zoom, Stretch) is in the player's More panel and is remembered across titles and app restarts.
- Settings > Playback > Match refresh rate (off by default) switches the display, while the player is open, to a mode with the same resolution and a refresh rate that is an integer multiple of the video frame rate (for example 23.976 fps on 23.976, 47.95 or 119.88 Hz). The frame rate is the one of the Media3 video format. The mode is requested through the window's `preferredDisplayModeId`, because Fire TV runs Android 11 and Media3's frame-rate strategy needs Android 12. The previous preference is restored when the player closes. Switching blanks the screen briefly, and Home trailers never switch. Verify on each Fire TV and Google TV model that the display actually changes mode. Fire OS may offer apps only some modes: a Fire TV 4K Max at 4K lists 30, 50 and 60 Hz, so 25 fps switches to 50 Hz while 23.976 fps leaves the display alone.
- Audio and subtitle delay (More > Audio and subtitle sync) shift one title by up to ±5 s in 50 ms steps with Left/Right (Center resets a row) and are forgotten when the next title starts. Audio delay offsets the position the audio sink reports, which is Media3's media clock, so a positive value makes the picture run earlier relative to the sound and works for PCM and passthrough; lowering it holds the picture until the sound has caught up. Subtitle delay renders the text renderer at the playback position minus the delay. Negative subtitle delay on subtitles inside the file only works as far as the player has buffered ahead. Verify on each Fire TV and Google TV model that the sync is audible and the picture does not stutter.

## Remote controls

- Navigation rail: hidden while the content has focus; Left at the content's left edge or Back slides it in
- Home spotlight: Left/Right pages through it (Left on the first page opens the navigation rail), Center opens the details, Play/Pause plays the title, Down moves to the rows
- Home trailer: after 3 s without input the home UI fades out and the trailer fills the screen; any key brings the UI back and still acts as usual
- Library and Discover cards: Center opens the details, Play/Pause plays the focused title; a library's last row opens All titles (sorting and filters), and Back returns to it; trailers take the screen as on home
- Center with hidden controls: pause and show controls, resume when paused, or activate a visible Skip/Next prompt
- Left/Right with hidden controls: show the seek bar (with a trickplay thumbnail when the server has one) and seek on release; hold for accelerated scrubbing
- Left/Right on the focused timeline: seek live, with the same thumbnail
- Up/Down: show controls and move between player information
- Back: close dialog, close panel, close the seek bar (dropping a seek that is still held), hide controls, leave player, then focus the navigation rail
- Hardware Play/Pause, Rewind, Fast Forward, Stop, and Menu are handled explicitly

## Permissions and platform services

The TV manifest is limited to `INTERNET`, `ACCESS_NETWORK_STATE` and `com.android.providers.tv.permission.WRITE_EPG_DATA` (Watch Next, see below), plus the application-scoped receiver permission generated by Android. The screensaver service's `BIND_DREAM_SERVICE` is a service permission and not part of this list. TV does not include Google Play Services, Cast, location, storage, notification, or biometric permissions. Active playback keeps the display awake through the Activity window lifecycle rather than a wake-lock permission.

Run `:app-tv:verifyTvReleaseManifestPermissions` before every store build. Also inspect the final release manifest because transitive dependencies can change.

## Watch Next row (Android TV and Google TV)

Continue Watching and Next Up are published to the launcher's Watch Next row; choosing a card opens the item's detail page in the app. Settings > Appearance > "Show on TV home screen" turns it off (on by default; the tile only exists where the feature works).

How it works:

- `app-tv/.../watchnext/` holds the publisher. `design-tv` feeds it plain domain items (`TvWatchNextItem`) from the home feeds through `TvPlatformIntegrations`; the lists are published 2 s after they settle, never before the home feeds have loaded, and only the changes are written. There is no background worker, so cards update while the app runs.
- Cards are matched by internal provider id (`<server hash>:<item id>`). Cards no longer in the lists are deleted. A card the user removes in the launcher (the system sets `browsable = 0` and keeps the row, or deletes it) is remembered as dismissed and not added again until the item has left the lists.
- `watch_next_type` is CONTINUE (with position and duration) for Continue Watching and NEXT for Next Up.
- Poster art never carries the access token. The publisher downloads the image itself (token in the `Authorization` header, no redirects, 2 MB cap) into the cache and gives the launcher a `content://<applicationId>.watchnext.art/art/<sha256>` URI served by `WatchNextArtProvider` (read-only; the name is a hash with a per-install salt). The Watch Next table is readable by other apps, so a Jellyfin image URL (which needs `ApiKey` on Jellyfin 12) is never written to it.
- Everything published is deleted on profile switch (including cold start into the profile picker), sign-out or expiry, and when the setting is turned off. A change of account clears the previous account's cards before publishing.
- Where the row does not exist nothing happens and no setting is shown: Fire TV (`amazon.hardware.fire_tv`), Android 7.x, no `android.media.tv` provider, non-leanback devices. Provider failures are logged by exception type only and never thrown.
- Deep link: `jellystack://item/{32 hex id}?s={server hash}`, handled by `MainActivity` in `onCreate` (fresh start only) and `onNewIntent`. Anything not matching exactly is ignored. The link waits in `TvDeepLinkInbox` for at most two minutes, is used once, only if the hash matches the active server, and is dropped while the connect/reconnect screen shows.
- There is no parental-restriction concept in the app (profiles have a PIN only), so nothing is filtered by rating.

Check on a Google TV emulator (`Jellystack_Android_TV_4K`):

```powershell
adb -s <serial> shell content query --uri content://android.media.tv/watch_next_program
adb -s <serial> shell "run-as app.jellystack.mobile ls cache/watchnext-art"
adb -s <serial> shell am start -a android.intent.action.VIEW -d "jellystack://item/<id>?s=<hash>" app.jellystack.mobile
adb -s <serial> logcat -d | findstr "Watch Next"
```

`content query` on the TV provider may be refused for the shell user; the launcher's Watch Next row is then the check. The hash of a server is logged nowhere: take the `intent_uri` column of a published row.

## Screensaver (Android TV and Google TV)

`JellystackDreamService` (`app-tv/.../dream/`) is a system screensaver (`DreamService`) with backdrops from the active profile's library. It appears in the TV's Settings > Device Preferences > Screen saver list as "Jellystack library artwork" (Jellystack Mediathek-Bilder). Choosing it there is the opt-in; the app has no setting of its own, and nothing runs until the user picks it. Fire TV: see below.

- Backdrops cross-fade every 20 s, with title and year small in a lower corner. The picture shifts a little and the caption changes corner with every slide (burn-in protection); nothing animates between fades. The caption scrim is baked (`TvScrim.SCREENSAVER_CAPTION`), not a per-frame gradient.
- Data: `TvScreensaverSource` (design-tv) reads cached recently added movies and shows of the active profile (no continue-watching or watch history). It never refreshes, signs in or contacts the server for data; only the images load, through the shared Coil loader, using the same URLs as the home screen so the disk cache is reused. Items without a backdrop are skipped (no untagged-backdrop fallback). Slide objects keep the image URL (which carries the token) out of `toString`.
- Dark screen with the app name, moving once a minute, instead of titles when: no profile is active (the profile picker clears the active profile), the active profile has a PIN (cold starts lock those again), the cache is empty, or reading fails. A dead app process is fine: `Application.onCreate` starts Koin before the service is created and everything is read from disk.
- `DreamLifecycleOwner` supplies the `ViewTreeLifecycleOwner` and `ViewTreeSavedStateRegistryOwner` Compose needs on the decor view; the loading coroutine and the composition are cancelled in `onDreamingStopped` and `onDetachedFromWindow`.
- Manifest: `BIND_DREAM_SERVICE` is a service permission, not in the permission allowlist (see `docs/permissions.md`).
- Fire TV: Fire OS has its own Screensaver setting that lists only Amazon's screensavers, and recent Fire OS versions ignore a third-party one set over adb (reports for Fire OS 8.1 and later). The service is inert there; no Fire TV support is claimed or tested.

Check on a TV emulator or device (the `settings` commands change the device's screensaver setting; the `cmd dreams` ones only start or stop it):

```powershell
adb -s <serial> shell settings get secure screensaver_components
adb -s <serial> shell settings put secure screensaver_components app.jellystack.mobile/app.jellystack.tv.dream.JellystackDreamService
adb -s <serial> shell settings put secure screensaver_enabled 1
adb -s <serial> shell cmd dreams start-dreaming     # start the selected screensaver now (no setting changed)
adb -s <serial> shell cmd dreams stop-dreaming
adb -s <serial> shell settings put secure screensaver_components <previous value>   # restore
```

Test with a profile that has loaded the home screen (so the cache is filled), after force-stopping the app (`am force-stop app.jellystack.mobile`) to cover a dead process, at the profile picker (expect the dark screen), and with a PIN profile.

## Google Play closed TV test

1. Enable the Android TV form factor for the existing Play Console app.
2. Create a dedicated closed Android TV test track.
3. Upload `app-tv-release.aab` with the `versionCode` from `app-tv/build.gradle.kts`.
4. Upload `store-assets/tv/google-play-banner-320x180.png` as the localized TV banner.
5. Upload at least two real 1920x1080 TV screenshots captured from a current build.
6. Provide reusable English reviewer credentials and Quick Connect instructions under App Access.
7. Confirm the Data safety and permission declarations match the TV artifact.
8. Promote only after the emulator and physical Fire TV checklist passes.

Google guidance: <https://developer.android.com/training/tv/publishing/distribute>

## Amazon Live App Testing

1. Create a Fire TV app in the Amazon Developer Console using package ID `app.jellystack.mobile`.
2. Select Fire TV as the supported device class.
3. Upload the same GMS-free TV APK or AAB used for Google TV testing.
4. Upload:
   - `store-assets/amazon/0.16.0-tv-beta.3/fire-tv/app-icon-1280x720.png`
   - `store-assets/amazon/0.16.0-tv-beta.3/fire-tv/background-1920x1080.png`
   - the five real 1920x1080 screenshots in the same `fire-tv` directory
5. Add the privacy URL, support URL, English reviewer credentials, and Quick Connect steps.
6. Start Live App Testing, install Amazon's re-signed build on a physical Fire TV, and rerun playback, audio focus, remote, network, and lifecycle checks.
7. Submit for normal review only after Live App Testing passes.

Amazon guidance:

- <https://developer.amazon.com/docs/app-submission/upload-app-file.html>
- <https://developer.amazon.com/docs/fire-tv/differences-from-android-tv-development.html>
- <https://developer.amazon.com/docs/fire-tv/multimedia-app-requirements.html>

## Store and binary validation

Before upload:

- Confirm APK and AAB signatures use the expected upload certificate.
- Record SHA-256 hashes for both release files.
- Inspect every packaged ABI and native library.
- Confirm packaged native libraries are compatible with 16 KB page sizes.
- Confirm the TV dependency graph contains no Google Play Services or mobile-only modules.
- Keep AAB files local; screenshots and public store artwork may be committed when they contain no private server or account data.
