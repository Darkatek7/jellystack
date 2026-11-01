# Android Permissions

The release manifest is checked against an allowlist by `:app-android:verifyReleaseManifestPermissions`.

| Permission | Purpose | When requested |
| --- | --- | --- |
| `INTERNET` | Direct Jellyfin/Seerr connections and media streaming | Install-time normal permission |
| `ACCESS_NETWORK_STATE` | Select Wi-Fi/mobile streaming and download behavior | Install-time normal permission |
| `NEARBY_WIFI_DEVICES` | Discover Cast devices on Android 13+ | Only after choosing Cast |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Legacy Cast discovery on Android 12 and earlier; both are capped at API 32 | Only after choosing Cast |
| `POST_NOTIFICATIONS` | Cast playback controls | Explained and requested only in an active Cast session |
| `FOREGROUND_SERVICE` | Cast session control required by Google Cast components | No runtime prompt |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | Optional App Lock | Only after enabling App Lock |

Jellystack does not request phone state, device identity, broad photo/media access, or read/write external-storage permissions. Downloads use app-specific storage.

Google Play can show permissions from older published versions in its historical “all versions” view. The manifest of the current 0.15.1 bundle is authoritative for current installs.

## Android TV (`app-tv`)

The TV release manifest is checked against its own allowlist by `:app-tv:verifyTvReleaseManifestPermissions`.

| Permission | Purpose | When requested |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Jellyfin/Seerr connections and streaming | Install-time normal permission |
| `com.android.providers.tv.permission.WRITE_EPG_DATA` | Write the app's own Continue Watching and Next Up cards into the system Watch Next row | Install-time normal permission, no prompt; skipped entirely on Fire TV |

`WRITE_EPG_DATA` is needed because the Android TV provider (authority `android.media.tv`) declares it as the provider's `writePermission`, with protection level `normal` (AOSP `packages/providers/TvProvider/AndroidManifest.xml`, unchanged from Android 8 to current). It is not dangerous and not shown to the user. `androidx.tvprovider` merges it into the manifest; the TV manifest also declares it explicitly. The library's second permission, `READ_EPG_DATA`, is deprecated and no longer enforced by the provider, so the TV manifest removes it. Reading back the app's own rows needs no permission, and `READ_TV_LISTINGS` (all apps' listings) is not requested.

The Watch Next feature also exports a read-only content provider (`<applicationId>.watchnext.art`) so the launcher can load poster art. It serves cached images only, under unguessable file names, and refuses every write; it is not a permission.

The TV manifest also declares `JellystackDreamService` (the optional screensaver, see `docs/android-tv.md`) with `android:permission="android.permission.BIND_DREAM_SERVICE"`. That is a permission the *caller* must hold to bind the service, so only the system can start it; the app does not request it. It is a service attribute, not a `<uses-permission>`, which is why `:app-tv:verifyTvReleaseManifestPermissions` (it scans `<uses-permission>` only) neither lists nor needs it. The screensaver adds no permission to the allowlist and reads the local library cache only.
