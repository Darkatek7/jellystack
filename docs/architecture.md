# Architecture

Jellystack is a Kotlin Multiplatform project with shared domain, networking, persistence, playback coordination, and Compose UI. Android phone/tablet and Android TV / Fire TV are the released platforms, and the iOS host is experimental.

## Modules

| Module | Kind | Responsibility |
| --- | --- | --- |
| `app-android` | Android app (`app.jellystack.mobile`) | Phone/tablet host: Activity lifecycle, permissions, Cast discovery, Media3 surface, Koin platform module, release packaging. |
| `app-tv` | Android app (`app.jellystack.tv`, same `applicationId` as mobile) | GMS-free TV host for Android TV, Google TV, and Fire TV. |
| `app-ios` | KMP (iOS only) | Experimental iOS host and framework entry point. |
| `design` | KMP (Android, iOS) | Phone/tablet Compose UI: navigation, screens, Compose resources (EN/DE), accessibility, adaptive layouts. `JellystackRoot.kt` is the mobile composition root. |
| `design-tv` | Android library | Compose for TV UI: focus handling, remote controls, TV strings (`TvStrings.kt`). `TvJellystackRoot.kt` is the TV composition root. Does not depend on `design`. |
| `design-screenshots` | Android library | Compose Preview screenshot tests for `design` (reference images are checked in). |
| `players` | KMP (Android, iOS) | Playback source resolution (Direct Play/HLS/offline), `PlaybackController` state machine, segments, continuation, SyncPlay, Cast abstraction. |
| `players-cast-google` | Android library | Google Cast implementation of the `players` Cast abstraction (mobile only, never on TV). |
| `shared-core` | KMP (Android, iOS) | Repositories, coordinators, settings, authentication, secure storage, domain models, downloads, Koin core module. |
| `shared-network` | KMP (Android, iOS, JVM) | Ktor clients and Jellyfin/Seerr transport DTOs. The JVM target exists for fast `jvmTest` runs. |
| `shared-database` | KMP (Android, iOS) | SQLDelight schema, migrations, and SQL-backed store implementations. |
| `testing` | KMP (JVM only) | Placeholder for shared test fixtures; currently unused (see [tech debt](tech-debt.md)). |
| `tools` | KMP (JVM only) | Development-only API client generator scaffold; not part of any app build. |

## Dependency direction

```text
app-android ──► design ──────► players ──► shared-core ──► shared-network
     │             └─(android)► players-cast-google ──► players
     └────────────────────────► shared-database ──► shared-core
app-tv ──────► design-tv ────► players, shared-core, shared-network
app-ios ─────► design, players, shared-core, shared-database, shared-network
```

Library modules never depend on app modules. `design` and `design-tv` are siblings; code both need belongs in `players` or `shared-core`.

## Rules

- UI reads state from coordinators and repositories; transport DTOs do not leak into screen state.
- Platform-only capabilities are injected behind common interfaces or explicit platform capability models (`AppPlatformCapabilities`).
- Jellyfin is authoritative for library identity, playback, progress, favourites, watched state, streams, and availability.
- Seerr enriches discovery and request workflows but must never block Jellyfin playback.
- Dependency injection uses Koin. The core module lives in `shared-core/.../di/Modules.kt`; each app module adds its platform module (`AndroidAppModule`, `TvAppModule`, `IosAppModule`).
