# RideDiary / 途记

Your everyday rides, a little better understood.

Native, offline Android commute journal for a phone alone. Manual start, system location foreground service, local Room storage, route recognition, stationary-time analysis, arrival summaries, history and monthly statistics. No GMS, map key, account, server, telemetry or track upload. No Internet permission. [完整中文说明](README.zh-CN.md).

The cream / forest-green / sand palette and vector motorcycle progress indicator follow the [original prototype](docs/prototype/ui-prototype.html). The four screens are Jetpack Compose, not a WebView. Default language is Chinese; the top-right control switches to English. Settings use private SharedPreferences for this one preference.

## Build and install

Pinned toolchain: JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin / Compose compiler 2.1.20, KSP 2.1.20-1.0.32, Android SDK / target 35, build tools 35.0.0, min SDK 26. Room 2.7.1; Compose BOM 2025.04.01. These are fixed compatible versions, not a claim to use the newest SDK.

Install Android Studio or the Android command-line SDK. Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to your SDK, or put `sdk.dir=/absolute/path/to/sdk` in untracked `local.properties`. Install `platforms;android-35`, `build-tools;35.0.0` and `platform-tools`. Accept the SDK licenses after reviewing them. On macOS, the system `/usr/bin/java` stub alone is insufficient.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/check-resources.py
adb install -r app/build/outputs/apk/debug/app-debug.apk
# With a connected Android 8+ device/emulator:
./gradlew :app:connectedDebugAndroidTest
```

This checkout also includes an ignored local JDK/SDK/cache in `.tooling/`. Run `./scripts/gradle-local.sh` to use those tools without changing your system installation, or pass specific Gradle tasks as arguments. On another machine, configure JDK/SDK as above; the helper does not download or accept licenses automatically.

If `adb` is not on PATH, use `.tooling/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk` on this checkout after connecting and authorizing your device.

The debug APK uses a development signing key. No release keystore or license has been chosen. The machine-specific `.tooling/` directory and `local.properties` are excluded from Git.

## Try it

Open **Try replay** on the Ride screen. Six synthetic historical rides appear, with a local route sketch. Play the deterministic ~11-second replay, pause/resume, watch the vector motorcycle follow the ordered route, then inspect the arrival summary, Journal, route and monthly filters. Replay is in memory, never inserted into the real database. Exit demo to return to your own data; demo cannot be entered during real recording.

For real rides, park safely, tap **Start recording**, read the permission explanation and grant precise location. Notification permission is requested on Android 13+; denial does not fabricate a location failure, but removes the normal notification stop action, so return to the app to finish. Approximate-only / denied location blocks starting; first launch and replay need no permissions. Enable system location if necessary. Continue with the screen off using a `location` foreground service, and finish from the persistent notification or app. No background location, boot receiver or automatic start is used.

After the first completed ride, open it in Journal → **Create commute route**, name it, choose To work / To home, and confirm endpoint radii (20–500 m). At least 150 m and four valid fixes are required. Subsequent manually started rides can match it. Several routes in the same direction are supported; near-equal parallel candidates stay uncertain. Arrival requires near-complete ordered progress, endpoint proximity and 30 seconds of fresh low-speed fixes. Manual finish remains available.

In ride details, correct the route/direction, exclude an unusual stop or detour without deleting raw fixes, or adjust commute start/end seconds. All statistics recalculate from selected boundaries and classifications. Interrupted rides remain visible with unknown gaps and do not enter valid commute statistics. They are not automatically restarted or silently converted into completed rides.

## What is implemented

- Four scrollable native screens with empty states; local trace Canvas; accessible labels and resource-based Chinese/English strings.
- Serialized recording service, duplicate-start guard, system GNSS/network updates, 2-second persistence checkpoints, UTC + timezone + monotonic intervals, raw point quality and algorithm version.
- Moving / stationary / unknown segments, low-speed dwell confirmation with backfill, hysteresis, accuracy / stale / jump / gap filtering.
- Ordered local-window route projection, conservative ambiguous classification, deviation/turnback indication, endpoint dwell arrival.
- Previous valid same-route/same-direction baselines (last 20), separate total/component medians, same-position comparison, remaining-time median and interquartile ETA (at least 3 valid samples).
- Local departure-month / route / direction filtering, unique commute dates, stacked per-ride time chart, distance/time/wait totals, mean/min/max clocks, durations and waits, total median; midnight-aware clock aggregation.
- Room transactions, raw-point retention, interrupted-record recovery, boundary edits and isolated synthetic replay.

## Verification and OPPO acceptance

See [testing and recorded results](docs/testing.en.md) and [design / algorithm definitions](docs/design.en.md). Build success, simulated tests and real-road accuracy are separate evidence. **OPPO lock-screen survival, GNSS behavior on adjacent Beijing main/service roads, arrival thresholds and battery usage require the user's phone and repeated commutes.** No road or rain certification is claimed.

For OPPO acceptance: run several repeat commutes in each direction; compare manual elapsed/stop notes; lock the screen for at least 30 minutes; test a brief endpoint stop, a tunnel/garage, permission withdrawal, notification stop, process termination and reopening. Record device model, ColorOS/Android versions and battery percentage. Begin with normal battery settings; change an app-specific battery setting only if measured interruption warrants it. Never operate while riding.

## Privacy and limits

Tracks remain in the app-private Room database. Cloud backup and device-transfer backup are excluded, `allowBackup=false`, no precise coordinates are logged. This is a platform backup policy, not encryption beyond Android's device storage protection; uninstalling clears local data, and some OEM transfer tools may need separate device verification. No export, cloud recovery or database migration beyond version 1 is shipped. Do not uninstall or clear storage if you need to retain rides.

GPS cannot reliably distinguish all adjacent lanes or roads. Heuristic thresholds are centralized in `core/Models.kt` and need field calibration. No navigation, live traffic, red-light inference, fastest-record coaching, paid APIs, hardware, maps, account or sync. Time-window route preference, stop hotspots, departure scatter plots and export are future enhancements. Recording does not hold a wake lock or request exemption from battery optimization: screen-off delivery/power behavior is specifically pending OPPO validation. Matching currently replays the recorded track and Room replaces derived segments per checkpoint; appropriate for commute-size tracks, with long-session performance still to be measured.

Source requirements are preserved in [Chinese](docs/requirements.zh-CN.md) and [English](docs/requirements.en.md). Repository: `git@github.com:zhaoyufei94/RideDiary.git`. No open-source license is asserted on the owner's behalf.

## Local Git handoff

`main` is initialized with the provided `origin`. Read-only SSH verification found no remote refs; no push or remote history change was made. Code and bilingual documentation are committed separately. This machine had no explicit Git author identity, so the local commits use Git's automatic OS identity; review/configure the repository identity before publishing. No global Git or SSH settings were changed.
