# Google Play Data Safety Review

This checklist documents the implementation that must be compared with the Play Console form for every release. It is not legal advice and does not replace Google's current definitions.

## Current implementation

- No data is sent to the Jellystack developer.
- No advertising, analytics, tracking, crash-reporting SDK, or automatic log upload is present.
- Authentication data, library requests, and playback traffic go directly to user-configured Jellyfin or Seerr servers.
- Credentials and tokens are stored locally in the platform-protected credential store.
- Offline media and metadata remain in app-specific storage.
- Google Play services are present only for optional Cast functionality.
- Users may explicitly choose an unencrypted HTTP server after a warning; HTTPS is recommended.

## Console review

Before submitting:

1. Re-run the release manifest permission allowlist.
2. Confirm that dependency changes did not add telemetry or data collection.
3. Review Google's current treatment of user-directed server traffic and Google Play services.
4. Keep the public privacy-policy URLs synchronized with the shipped app.
5. Verify the App access test credentials independently from production accounts.

Google Play's “all versions” permission view can retain permissions from older releases. The current bundle manifest determines permissions requested by the current app version.
