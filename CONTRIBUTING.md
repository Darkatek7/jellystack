# Contributing to Jellystack

Thanks for helping improve Jellystack.

## Before you start

- Search existing issues and discussions.
- Use an issue for large features, security-neutral architecture changes, or behavior changes.
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
- Never include real server URLs, access tokens, cookies, passwords, media libraries, logs, or UI dumps in an issue or pull request.

## Development setup

Use JDK 17 and Android SDK API 36. Run Gradle only from the repository root:

```bash
./gradlew :app-android:assembleDebug
```

macOS and Xcode are required for iOS compilation. iOS is experimental.

## Workflow

1. Fork the repository and branch from `main`.
2. Keep changes focused and add tests for changed behavior.
3. Run:

   ```bash
   ./gradlew spotlessCheck detekt
   ./gradlew :app-android:check
   ./gradlew :app-android:assembleDebug
   ```

4. Open a pull request using the template.

`main` is protected. All changes use pull requests, linear history, and squash merges. Force pushes to `main` are disabled.

## Release artifacts

GitHub releases contain source code and release notes only. Android App Bundles are built and retained locally for direct upload to Google Play Console; do not attach `.aab` files to GitHub releases or commits.

## Test data and screenshots

Use only clearly fictional credentials and reserved domains such as `example.com`, `example.org`, or `example.invalid`. Screenshots must come from a sanitized demo setup and must not contain account names, private hosts, personal media, or notification data.

## Style

- Follow existing Kotlin and Compose conventions.
- Prefer shared common code unless behavior is platform-specific.
- Keep strings localized in English and German.
- Explain new Android permissions and request them only at the feature trigger.
- Do not add analytics, remote logging, or new external services without prior design discussion.

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).
