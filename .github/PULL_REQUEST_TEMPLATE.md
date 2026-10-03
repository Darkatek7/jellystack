## Summary

<!-- What changes and why? -->

## Verification

- [ ] Added or updated tests for changed behavior
- [ ] `./gradlew spotlessCheck detekt` and the unit tests of every touched module (full gate: `docs/building.md`)
- [ ] `./gradlew :app-android:assembleDebug` (and `:app-tv:assembleDebug` for shared or TV changes)
- [ ] English and German strings remain in sync (Compose resources, app `res/values`, `TvStrings.kt`)
- [ ] Mobile and TV release manifest permission allowlists still pass

## Privacy and security

- [ ] No credentials, private hosts, personal media, logs, UI dumps, keystores, or signing configuration
- [ ] New network/logging behavior does not expose secrets
- [ ] Any new Android permission is documented and requested only at the feature trigger

## UI changes

<!-- Attach only sanitized demo screenshots. Remove this section when not applicable. -->
