# Jellystack

[![Google Play](https://img.shields.io/badge/Google_Play-Install-414141?logo=googleplay)](https://play.google.com/store/apps/details?id=app.jellystack.mobile)
[![CI](https://github.com/Darkatek7/jellystack/actions/workflows/ci.yml/badge.svg)](https://github.com/Darkatek7/jellystack/actions/workflows/ci.yml)
[![Security](https://github.com/Darkatek7/jellystack/actions/workflows/security.yml/badge.svg)](https://github.com/Darkatek7/jellystack/actions/workflows/security.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-7c4dff.svg)](LICENSE)

Jellystack is a privacy-focused Jellyfin and Seerr client with a native Android experience. Browse and play your own media, manage requests, download for offline use, and keep playback state in sync—without a Jellystack cloud, ads, analytics, or tracking.

## Highlights

- Jellyfin libraries, favourites, watched state, progress, search, and Quick Connect
- Direct Play and HLS playback with audio, subtitle, and quality selection
- Offline downloads stored in app-specific storage
- Seerr discovery, requests, seasons, request status, trailers, and recommendations
- Cinematic details with cast, ratings, streams, collections, and similar titles
- Phone and tablet layouts, English and German, light and dark themes
- Contextual Cast permissions and an optional biometric app lock

Android is the stable, published platform. The shared Kotlin Multiplatform code and iOS host remain experimental; iOS is not currently distributed as a supported release.

## Privacy

Jellystack connects directly to servers configured by the user. Server credentials and tokens are stored in the platform-protected credential store. The app has no analytics SDK, automatic log upload, advertising, or developer-operated media proxy.

See [Privacy](docs/privacy.md), [Permissions](docs/permissions.md), and the [German privacy policy](privacy-policy-de).

## Build

Requirements:

- JDK 17
- Android SDK API 36
- Xcode on macOS for iOS targets

All Gradle commands run from the repository root:

```bash
./gradlew :app-android:assembleDebug
```

Useful verification:

```bash
./gradlew spotlessCheck detekt
./gradlew :app-android:check
./gradlew :shared-network:jvmTest :shared-core:testDebugUnitTest
```

See [Building](docs/building.md) for local setup and [Architecture](docs/architecture.md) for module boundaries.

## Contributing

Issues, discussions, and pull requests are welcome. Every change—including maintainer changes—lands through a pull request with required CI checks.

Read [CONTRIBUTING.md](CONTRIBUTING.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md), and [SECURITY.md](SECURITY.md) before contributing.

## License and independence

Jellystack is licensed under [GPL-3.0](LICENSE). See [Third-Party Notices](THIRD_PARTY_NOTICES.md) for dependencies.

Jellystack is an independent community project and is not affiliated with or endorsed by Jellyfin, the Jellyfin project, Seerr, or their maintainers.
