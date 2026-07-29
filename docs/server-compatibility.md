# Server Compatibility

## Jellyfin

Jellystack targets current supported Jellyfin releases and uses public Jellyfin APIs for authentication, libraries, playback information, progress, favourites, watched state, images, and metadata. Quick Connect is the default sign-in method when enabled by the server; username and password remain available.

Direct Play depends on Android codec/container support. Jellystack falls back to Jellyfin HLS/transcoding profiles when needed.

## Seerr

Jellystack supports Seerr-compatible discovery, requests, request status, seasons, profiles, trailers, and enriched details through public server endpoints. Jellyfin Quick Connect tokens cannot universally authenticate to every Seerr deployment; manual Jellyfin or Seerr credentials remain the supported fallback.

## Transport

HTTPS is recommended. HTTP remains available for local self-hosted setups after an explicit warning and confirmation.

When reporting compatibility problems, include sanitized server and app versions. Never attach real tokens, cookies, private hosts, or unredacted logs.
