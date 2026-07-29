# Building Jellystack

## Requirements

- JDK 17
- Android SDK platform 36 and current build tools
- Git
- macOS with Xcode for iOS targets

Create `local.properties` locally if Android Studio does not create it:

```properties
sdk.dir=/path/to/Android/sdk
```

Never commit `local.properties`, keystores, or signing passwords.

## Android

```bash
./gradlew :app-android:assembleDebug
```

Run the main verification gates:

```bash
./gradlew spotlessCheck detekt
./gradlew :design:verifyComposeResourceParity
./gradlew :app-android:check
./gradlew :shared-network:jvmTest
./gradlew :shared-core:testDebugUnitTest
```

Release signing is intentionally local and is not configured in public CI.

## iOS (experimental)

On macOS:

```bash
./gradlew :app-ios:linkDebugFrameworkIosSimulatorArm64
```

The iOS host is experimental and not covered by the Android release support promise.
