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

Google Play can show permissions from older published versions in its historical “all versions” view. The manifest of the current 0.14.2 bundle is authoritative for current installs.
