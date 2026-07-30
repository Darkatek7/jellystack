# Jellystack 0.14.3 - Stability design

## Goal

Resolve the first production feedback batch without expanding the product surface. The release must make requests permission-aware, accept valid self-hosted server addresses, keep Discover search input stable, and make Android audio behavior match the selected language.

## Request capabilities

Seerr's permission bitmask is mapped to typed capabilities for standard movie requests, standard series requests, 4K movie requests, 4K series requests, advanced requests, and request management. Admin bypasses all checks. Request management enables management and advanced controls but does not by itself grant permission to create a movie or series request.

The request sheet exposes only supported variants. Profile, server, language, and quality controls exist only for users with advanced-request capability. The coordinator performs the same capability check before creating the API payload.

## Server addresses

All connection paths share one Ktor-based parser. Users must enter an explicit HTTP or HTTPS scheme. IPv4, bracketed IPv6, custom ports, and nested reverse-proxy paths remain intact after normalization. Query parameters, fragments, and embedded credentials are not retained.

## Playback

The Android device profile advertises only audio codecs backed by an installed decoder. Media3 uses media/movie audio attributes and manages audio focus. Direct sources use deterministic Media3 track matching. HLS audio changes request a new Jellyfin source with the chosen AudioStreamIndex while preserving position, pause state, subtitles, and quality.

## Discover search

Text entry is local UI state and no longer passes through navigation dispatch. The raw text, including spaces, remains visible. Only the server query is trimmed. Network searches use a 300 ms debounce and cancel stale work.

## Privacy

Jellystack does not create developer-operated accounts or collect developer data. Local app data can be removed with Android Clear storage or uninstall. Jellyfin and Seerr account data is controlled by the selected server administrator.
