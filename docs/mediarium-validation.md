# Mediarium manual validation

This is a test procedure and report template, not a record of completed testing.
Keep the Mediarium PR in draft until both live-server and physical TV/remote
checks have been performed. A TV emulator and MockEngine tests are useful
prechecks, but do not replace either requirement.

## Prepare

1. Use an Android TV, Google TV, or Fire TV device and its physical directional
   remote (Up, Down, Left, Right, OK, Back). A Tizen/webOS TV needs a compatible
   external device to run this APK.
2. Build the exact feature commit using the [build instructions](building.md):
   `./gradlew :app-tv:assembleDebug`. The output is
   `app-tv/build/outputs/apk/debug/app-tv-debug.apk`. Use the phone APK from
   `app-android/build/outputs/apk/debug/app-android-debug.apk` for the phone check.
   Fork Actions validation artifacts may also contain both APKs; record the
   workflow commit and source commit from that run, not just the version label.
3. Install the TV APK using your device's supported developer installation flow.
   With an authorized ADB connection, `adb -s DEVICE_SERIAL install -r APK_PATH`
   installs it. If installation reports a signature mismatch with a store build,
   use a spare test device or back up your settings before replacing the
   installed app. Uninstalling an existing build removes its local app data.
   Phone and TV builds share the application ID and should be tested on separate
   devices. Do not install an APK from another commit between test cases.
4. Have reachable Mediarium and Jellyfin servers, a Jellyfin test account and a
   Mediarium API key. Configure their URLs in the app, preserving a URL_BASE
   subpath if used. Do not expose either server publicly just for this test.
5. Choose test titles you are authorized to acquire. Use a small series because
   Request monitors the whole series and can start searches and downloads.
   Record existing library/request state first. Use an account requiring approval
   for the pending-request case if your installation supports it.
6. Use only the physical remote for TV navigation. A keyboard may assist entering
   a long API key during setup, but test reachability, field entry/exit, selection
   and Back with the remote. Record any keyboard assistance in the report.

## Cases

For each case, record PASS, FAIL, or NOT RUN, the observed result, and evidence.
Do not mark a case passed merely because the app did not crash.

| ID | Steps | Expected result |
| --- | --- | --- |
| TV-01 | Open request-provider settings; select Mediarium; move between URL, key, Connect and Cancel with the remote. | Every action is reachable, focus is visible, no trap; credential fields can be entered and exited. |
| TV-02 | Connect with a valid URL/key, close and reopen the app. | Mediarium remains selected and authenticates; no Jellyfin/Seerr login is requested for it. |
| TV-03 | Try an invalid key and an unreachable test URL; then correct them. | A recoverable error appears; no title is added; navigation still works. |
| TV-04 | Browse movie and TV rails; move between rows, open details and return. | Cards/details load; Back returns to a usable focus; no inaccessible actions. |
| TV-05 | Search for a movie and a series; open and close the on-screen keyboard using OK/Back; clear or change the query. | Results match the query; focus remains usable while results change. |
| API-01 | Request a movie absent from Mediarium with an account allowed to add directly. | Exactly one add is observed on the server; native search can begin; the client does not invent a pending-request ID. |
| API-02 | Request a small series with that account. | One series add, whole-series monitoring, and native search behavior agree with the documented options. |
| API-03 | Submit using an account requiring approval; inspect native pending requests, then approve in Mediarium and refresh Jellystack. | Pending then approved status is represented; only an exact known ID/type association is retained; approval is done in Mediarium. |
| API-04 | Open an existing title or attempt a duplicate request. | Existing/native duplicate state is handled; no repeated automatic POST or duplicate acquisition. |
| JF-01 | Inspect a title added in Mediarium but not imported into Jellyfin. | The app does not claim it is playable merely because Mediarium has added/downloaded it. |
| JF-02 | After normal download/import and Jellyfin library scan, find the title in Jellyfin through Jellystack and play it. | Jellyfin library identity and playback work independently of request history. |
| REG-01 | Switch to an existing Seerr connection; browse/search and inspect requests. | Existing Seerr behavior remains functional; switching back restores Mediarium. |
| PHONE-01 | Connect Mediarium on the phone, search/open movie and TV details, submit and inspect status. | The shared provider works through the phone UI; server state agrees with the TV checks. |

For pending-request history, restart the app as an additional check: native
history may have no TMDB identity, so previously submitted title associations are
not promised across restarts. Status-only rows are expected where identity is
unavailable. Do not infer identity from a matching title.

## Report template

Copy this section into the PR only after testing. Leave unexecuted cases NOT RUN.

- Source commit: <full SHA>
- APK artifact/workflow commit and SHA-256: <values>
- Test date and tester: <values>
- Device model, OS version and physical remote: <values>
- Mediarium and Jellyfin versions: <values>
- Connection: <LAN or VPN; URL_BASE used yes/no; no actual URLs>
- Accounts: <direct-add / approval-required; no names or credentials>
- Input assistance: <none or describe>

| Case | Result | Observed behavior / evidence |
| --- | --- | --- |
| TV-01 through PHONE-01 | NOT RUN | Fill one row per executed case. |

List failures with the exact navigation sequence and expected/observed result.
A short video showing the TV and remote can demonstrate focus and Back behavior;
it is supplementary evidence, not a substitute for the case results. Sanitized
logs can help diagnose a failure. Never publish API keys, passwords, cookies,
private server addresses, personal library contents, or raw network captures.
