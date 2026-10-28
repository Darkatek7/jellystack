# Building Jellystack

This is the single reference for build and verification commands. CI (`.github/workflows/ci.yml`) runs the same gates.

## Requirements

- JDK 17
- Android SDK platform 36 and current build tools
- Git
- macOS with Xcode for iOS targets (experimental)

Create `local.properties` locally if Android Studio does not create it:

```properties
sdk.dir=/path/to/Android/sdk
```

Never commit `local.properties`, `keystore.properties`, keystores, signing passwords, APKs, or AABs.

Run every Gradle command from the repository root. On Windows use `.\gradlew.bat` instead of `./gradlew`.

## Build

```bash
./gradlew :app-android:assembleDebug   # phone/tablet
./gradlew :app-tv:assembleDebug        # Android TV / Fire TV
```

Release signing is intentionally local (`keystore.properties`) and is not configured in public CI. TV release builds are described in [Android TV and Fire TV](android-tv.md).

## Verification

Formatting and static analysis:

```bash
./gradlew spotlessApply                # auto-format Kotlin and Gradle scripts
./gradlew spotlessCheck detekt
```

The full CI gate (static checks, resource parity, manifest allowlists, unit tests, screenshot tests, assembly):

```bash
./gradlew spotlessCheck detekt \
  verifyUniqueAndroidVersionCodes \
  :design:verifyComposeResourceParity \
  :app-android:verifyAndroidResourceParity \
  :app-android:verifyReleaseManifestPermissions \
  :app-tv:verifyTvReleaseManifestPermissions \
  :shared-network:jvmTest \
  :shared-core:testDebugUnitTest \
  :shared-database:testDebugUnitTest \
  :players:testDebugUnitTest \
  :design:testDebugUnitTest \
  :design-tv:testDebugUnitTest \
  :app-android:testDebugUnitTest \
  :design-screenshots:validateDebugScreenshotTest \
  :design-tv-screenshots:validateDebugScreenshotTest \
  :app-android:assembleDebug \
  :app-tv:assembleDebug
```

For a focused change, run only the unit tests of the touched module, for example `./gradlew :players:testDebugUnitTest`.

### Instrumented tests

These need a running emulator or device. CI runs the `design` and `app-android` suites on an API 35 tablet emulator and the `design-tv`, `app-tv`, and `tv-benchmark` suites on an API 35 TV emulator; the `design` and `design-tv` suites are sharded across separate emulators. The TV jobs are non-blocking because the CI TV emulator crashes at random (see `docs/tech-debt.md` 2.18), so run them locally before a TV release.

```bash
./gradlew :design:connectedDebugAndroidTest
./gradlew :app-android:connectedDebugAndroidTest
./gradlew :design-tv:connectedDebugAndroidTest   # Android TV emulator
```

### Screenshot tests

Reference images live in `design-screenshots/src/screenshotTestDebug/reference/` (phone and tablet UI) and `design-tv-screenshots/src/screenshotTestDebug/reference/` (TV UI). After an intended visual change, regenerate them and review the diff:

```bash
./gradlew :design-screenshots:updateDebugScreenshotTest
./gradlew :design-screenshots:validateDebugScreenshotTest
./gradlew :design-tv-screenshots:updateDebugScreenshotTest
./gradlew :design-tv-screenshots:validateDebugScreenshotTest
```

The TV tests render production `design-tv` composables through `JellystackTvGoldenFixture` (`design-tv/.../TvGoldenFixtures.kt`) with fictional data and no network artwork.

## TV release shrinking and baseline profile

`app-tv` release and `benchmark` builds run R8 (code and resource shrinking); keep rules live in `app-tv/proguard-rules.pro` and should only grow after a runtime failure of the shrunk build proves a rule is needed. Check a shrunk build with `./gradlew :app-tv:assembleBenchmark` on a TV emulator or device (`assembleRelease` needs the release keystore).

The TV baseline profile and startup profile are committed under `app-tv/src/main/generated/baselineProfiles/`. Fire TV gets no Play cloud profiles, so this is what makes the first runs fast. Regenerate them after larger UI or startup changes, with a TV emulator or device running that is already signed in to a Jellyfin server with a few libraries (a local Jellyfin test server works):

```bash
ANDROID_SERIAL=<tv device serial> ./gradlew :app-tv:generateBaselineProfile
```

Set `ANDROID_SERIAL` so the phone emulator is not used too. Gradle uninstalls the app after the run, so sign in again afterwards. The generator and the real-app macrobenchmarks live in `tv-baseline-profile/`; the synthetic-fixture benchmark stays in `tv-benchmark/`.

## iOS (experimental)

On macOS:

```bash
./gradlew :app-ios:linkDebugFrameworkIosSimulatorArm64
```

The iOS host is experimental, only built by the manually triggered `ios-experimental.yml` workflow, and not covered by the Android release support promise.
