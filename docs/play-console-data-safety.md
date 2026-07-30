# Google Play Data Safety Review

This checklist documents the implementation that must be compared with the Play Console form for every release. It is not legal advice and does not replace Google's current definitions.

## Current implementation

- No data is sent to the Jellystack developer.
- Jellystack does not create or operate user accounts.
- No advertising, analytics, tracking, crash-reporting SDK, or automatic log upload is present.
- Authentication data, library requests, and playback traffic go directly to user-configured Jellyfin or Seerr servers.
- Credentials and tokens are stored locally in the platform-protected credential store.
- Offline media and metadata remain in app-specific storage.
- Google Play services are present only for optional Cast functionality.
- Users may explicitly choose an unencrypted HTTP server after a warning; HTTPS is recommended.

## Accounts and deletion

- Select **My app does not allow users to create an account** in the Play Console account-deletion section.
- Signing in to an existing Jellyfin or Seerr server is not account creation by Jellystack. Those accounts and their server-side data are controlled by the selected server and its administrator.
- Jellystack has no developer-operated account or remotely collected developer data to delete.
- Users can remove all locally stored Jellystack data through Android's **Clear storage** action or by uninstalling the app.
- Users who want Jellyfin or Seerr account data deleted must contact the administrator of that server.
- Do not claim that Jellystack provides a developer data-deletion request mechanism when no developer data is collected.

## Console review

Before submitting:

1. Re-run the release manifest permission allowlist.
2. Confirm that dependency changes did not add telemetry or data collection.
3. Review Google's current treatment of user-directed server traffic and Google Play services.
4. Keep the public privacy-policy URLs synchronized with the shipped app.
5. Verify the App access test credentials independently from production accounts.
6. Confirm that the account-deletion answer still states that Jellystack does not allow account creation.

Google Play's “all versions” permission view can retain permissions from older releases. The current bundle manifest determines permissions requested by the current app version.
