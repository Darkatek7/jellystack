# Roadmap

What gets built next, in order. There are no dates: a release ships when its exit criteria pass, and work that misses them moves to the next release. Mobile and TV are versioned separately. The public version of this list is the roadmap on jellystack.org.

## TV 0.18.0: finished, waiting for release

Music, Live TV, person and collection pages, Watch Next, a library screensaver, picture size, audio and subtitle delay, refresh-rate matching, DTS and TrueHD without a transcode, and a much smoother Home and Discover on Fire TV.

Left to do: merge #50, upload the signed files, tag, and sync the website. The crash on Android 7 (#42) is fixed in #50 but has not been run on an Android 7 device.

Fire TV 4K Max, the numbers later releases must not fall behind:

| | Median frame | p90 | Janky frames |
| --- | --- | --- | --- |
| Home | 8 ms | 21 ms | 29% |
| Discover | 9 ms | 21 ms | 38-40% |

## TV 0.19: Home and navigation

The screens used most, so every press gets better.

- The hero shows the focused title, and the spotlight stops changing by itself while you move.
- Back on Home goes to the first row or the rail first. Only then does it ask whether to exit.
- Left from the first card of any row always opens the rail.
- Switching profile takes fewer presses than today's Left, Up, Up, OK.

Exit criteria:

- Instrumented tests cover the Back order and opening the rail.
- Walked through on a Fire TV with a real remote.
- Home is no slower than in the table above.

## TV 0.20: sign-in and cards

- Quick Connect asks only for what it needs: the display name moves out of the way, and a new server starts on the URL.
- The code stands alone in large type, with a QR code to the server's Quick Connect page. The QR code is generated on the device, with no external service.
- Hints look different from errors, and a long message no longer cuts off the heading.
- Library cards on Home share one style, with or without artwork.
- Seerr cast cards open a person page, or are no longer focusable.
- Library and Search stop starting a trailer lookup for every focused card.

Exit criteria:

- Adding a second account with a real remote takes at most three presses plus the approval.
- Screenshot references cover the sign-in states and the cards.

## TV 0.21: Live TV guide and Discover

- A simple guide grid for the next few hours. It ships only if it stays smooth on a Fire TV; otherwise the channel list stays.
- Discover reaches 30% janky frames or fewer on a Fire TV, without a slower median or p90.
- Live TV checked on a real tuner, including recovery after a long pause.
- A round on real hardware: Watch Next on a Google TV device, Dolby Vision displays, and AV receivers.

Exit criteria:

- The numbers above are measured with the same scripted run as before.
- Everything found on real hardware is fixed or listed in the release notes.

## Alongside the TV releases

These land with whichever release touches the code. Details are in [tech-debt.md](tech-debt.md).

- Move routing and wiring out of `TvJellystackRoot.kt`.
- Replace inline dp values with shared gutter tokens.
- Make `detekt` analyse KMP source sets.
- Upgrade Kotlin and Compose as one tested change.

## Later, in no fixed order

- TV accessibility review.
- More languages, with community translations.
- Smart offline downloads and admin tools on the phone.
- A supported iPhone and iPad release.

## Not planned

- A Jellystack backend. The app talks only to servers you configure.
- Analytics, telemetry, or remote logging.
