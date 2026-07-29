# Architecture

Jellystack is a Kotlin Multiplatform project with shared domain, networking, persistence, playback coordination, and Compose UI.

## Modules

- `app-android`: Android lifecycle, permissions, Cast integration, Media3 surface, and release packaging.
- `app-ios`: experimental iOS host and framework entry point.
- `design`: shared Compose navigation, screens, resources, accessibility, and adaptive layouts.
- `shared-core`: repositories, settings, authentication, domain models, downloads, and coordination.
- `shared-network`: Ktor clients and Jellyfin/Seerr transport models.
- `shared-database`: SQLDelight schemas and platform drivers.
- `players`: playback resolution, state machine, direct/HLS sources, and Cast abstraction.
- `testing`: shared fixtures.
- `tools`: development-only JVM utilities.

UI reads state from coordinators and repositories; transport DTOs do not leak into screen state. Platform-only capabilities are injected behind common interfaces or explicit platform capability models.

Jellyfin is authoritative for library identity, playback, progress, favourites, watched state, streams, and availability. Seerr enriches discovery and request workflows but must never block Jellyfin playback.
