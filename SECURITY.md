# Security Policy

## Supported versions

Security fixes target the latest Google Play release and the current `main` branch.

## Reporting a vulnerability

Do not open a public issue and do not paste credentials, tokens, cookies, private server URLs, or exploit details into Discussions.

Use GitHub's **Report a vulnerability** form in the Security tab of `Darkatek7/jellystack`. Include:

- affected version and platform;
- minimal reproduction steps using sanitized data;
- impact and suggested mitigation;
- whether the issue may expose stored credentials or server access.

You should receive an acknowledgement within seven days. Valid reports are coordinated privately until a fix and disclosure plan are ready.

## Scope

In scope are Jellystack source code, official Android releases, credential handling, authentication flows, networking, local downloads, and GitHub automation. Vulnerabilities in Jellyfin, Seerr, Android, or third-party servers should be reported to those projects.

Never send live credentials. Rotate any credential that may have been exposed before reporting it.
