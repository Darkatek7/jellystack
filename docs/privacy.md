# Jellystack Privacy Policy

Last updated: 27 July 2026

Jellystack is a self-hosted media client. It does not operate a Jellystack cloud service.

## Data processing

Jellystack connects directly from the user's device to Jellyfin and Seerr servers configured by the user. The developer does not receive media, account credentials, library contents, viewing history, requests, or server addresses.

Server configuration, access tokens, session credentials, preferences, playback progress pending synchronization, and offline-download metadata are stored locally. Authentication secrets use the platform-protected credential store where supported.

Jellystack contains no advertising, analytics SDK, tracking, or automatic log upload. Debug information stays on the device unless the user deliberately shares sanitized information.

## Optional Google Cast

Google Play services are used only for the optional Cast feature. Choosing Cast may ask for nearby-device access (Android 13+) or location permission (Android 12 and earlier) to discover devices on the local network. During an active Cast session, Android may ask for notification permission to show playback controls. Cast continues if notifications are declined.

Google's handling of Google Play services data is governed by Google's policies.

## Downloads

Offline media is stored in app-specific storage. Jellystack does not request broad photo, media, USB-storage, phone-state, or device-identity access.

## Unencrypted servers

Jellystack supports HTTP for local self-hosted environments, but warns before the first sign-in. HTTPS is recommended because HTTP can expose credentials and media traffic to others on the network.

## Contact and source

Source code and public issue tracking: <https://github.com/Darkatek7/jellystack>

Report vulnerabilities privately through the repository Security tab.
