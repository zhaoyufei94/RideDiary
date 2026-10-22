# RideDiary / 途记

**Your everyday rides, a little better understood.** · [中文说明](README.zh-CN.md)

RideDiary is an offline, native Android commute journal. Record everyday rides with your phone, split elapsed time into moving, stationary and unknown intervals, and compare each commute with your own history.

For Android 8.0 and later, across Android phone brands. No extra hardware, account, Google Play services or map key is needed. Recording and analysis happen on your phone; the app has no Internet permission.

## What it does

| Screen | Features |
|---|---|
| Ride | Manual recording, elapsed time, average speed, stationary time and location status; motorcycle progress, historical comparison and arrival estimates after route recognition |
| Journal | Ride history and arrival summaries; correct route, direction and commute boundaries, exclude unusual rides, or delete a ride |
| Statistics | Filter by month, route and direction; view commute days, rides, distance, elapsed time, stationary share and per-ride time breakdowns |
| Routes | Create work/home routes from recorded rides; inspect local track sketches and historical samples; edit and delete routes |

The cream, forest-green, light-green and sand palette pairs with a motorcycle progress indicator. Screens use Jetpack Compose / Material 3. Chinese is the default language; the top-right control switches to English.

## Manage rides and routes

- Touch and hold a ride card in Journal or the summary card in details, choose **Delete ride** and confirm. The ride and raw fixes are removed and statistics refresh; routes created from it remain available.
- The detail back button, Android Back button and back gesture return to the Journal list.
- Touch and hold a route card in Routes to edit its name, work/home direction or endpoint radii, or delete it. Direction changes update linked rides. Deleting a route preserves rides and tracks but unlinks them. Finish recording before changing a route currently in use.
- The in-app replay feature has been removed. Synthetic tracks are test fixtures and are excluded from the application APK.

The [HTML prototype](docs/prototype/ui-prototype.html) remains a historical visual reference; its demo controls do not describe current app features.

## Permissions and operating conditions

| Permission or condition | When needed | Purpose and effect of denial |
|---|---|---|
| Precise location | When starting a real ride | Track recording, movement analysis and route matching. Approximate-only or denied location prevents real recording; saved history remains available |
| Notifications, Android 13+ | Requested as needed when starting | Ongoing recording notification and stop action. Recording can continue if denied; return to the app to finish |
| System location enabled | During real recording | Required for normal fixes. If disabled, the app explains the issue; timing continues and unclassifiable intervals remain unknown |
| Location foreground service | After permission and manual start | Continues recording in the background and with the screen off; managed by Android, without a separate permission dialog |

No “Allow all the time” location permission, boot startup, all-day listening or automatic ride start. Recording starts with your action and stops after arrival confirmation or manual finish.

## Using RideDiary

1. **Record the first ride.** While parked, tap **Start recording**, grant precise location and enable system location. With no saved route, view actual elapsed and stationary time, then finish manually.
2. **Save a commute route.** Open the ride in Journal, choose **Create commute route**, name it, select To work / To home and confirm endpoint radii. A route needs at least four valid fixes and 150 m; radii can be 20–500 m. Multiple routes per direction are supported.
3. **Record another commute.** Start manually again. Recognition uses the start area and observed track, so it needs some travel; nearly identical candidates remain uncertain.
4. **View progress and estimates.** Recognized rides show route progress. At least three usable histories for the same route and direction are needed for historical comparison and an arrival estimate.
5. **Arrive and review.** Arrival requires near-complete route progress, presence inside the endpoint area and at least 30 seconds of continuous fresh low-speed fixes. Manual finish remains available in the app or notification. Correct classification and boundaries or exclude unusual rides afterward; raw fixes are retained and statistics recalculate.

Operate the app while parked. Automatic recognition happens after you manually start recording.

## Timing, location and battery use

- **Total elapsed = moving + stationary + unknown.** Stationary time can include congestion, brief stops or parking; it does not identify red lights. Missing location is not treated as zero speed.
- Time to the first usable fix varies with surroundings and system behavior. Tunnels, garages, buildings and adjacent roads can affect location and recognition. Progress and arrival estimates are temporarily suppressed when off-route or using stale fixes.
- Arrival confirmation waits for sustained stopping, so the summary does not appear immediately when passing the endpoint. Estimates use your historical remaining time, without live traffic or second-level guarantees.
- Continuous location increases battery use. Fixes are requested about every two seconds, but Android controls delivery. Power use and long screen-off continuity still need measurement across devices, systems and real routes.
- Start with normal battery settings; inspect app-specific battery/background restrictions if recording is interrupted. The app currently holds no wake lock and requests no battery-optimization exemption.
- If permission is revoked or the process is terminated, reopening marks the unfinished ride interrupted and retains saved data. It does not resume automatically; interrupted rides are excluded from valid commute statistics.

## Build and install

Use **JDK 17** and Android Studio or the Android command-line SDK. Set `JAVA_HOME` and configure the SDK through `ANDROID_HOME` or `sdk.dir` in `local.properties`. Install these SDK packages and accept their licenses:

```text
platforms;android-35
build-tools;35.0.0
platform-tools
```

On this computer, project-local JDK 17 and Android SDK tools are configured in the Git-ignored `.tooling/` directory. Use `./scripts/gradle-local.sh :app:assembleDebug` to select them automatically. ADB is at `.tooling/android-sdk/platform-tools/adb`.

Before the first build on another computer, [import the shared signing key](docs/signing.en.md); this project will not generate a replacement identity.

The repository includes a pinned Gradle Wrapper; a separate Gradle installation is unnecessary. From the repository root:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`. The first build downloads tools and dependencies, so its duration depends on the network and cache. Using the app requires no network connection.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Connect a phone, enable USB debugging and authorize the computer, then install:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Add `--no-streaming` if streamed installation fails. Alternatively, transfer the APK to the phone and allow installation from that source when Android prompts. USB debugging is for development installation, not everyday use. This build uses a development signing key.

## Statistics and ride detail presentation (0.1.3)

Statistics uses a monthly distance overview, time-composition ring, selectable per-ride bars and departure/arrival rhythm cards. Month, route and direction filters continue to apply consistently across every metric and chart. Empty scopes show an honest empty state, and enlarged text switches metrics to vertical layouts.

Ride details share the forest-green overview and grouped cards for time, distance, speed, moving/stationary/unknown intervals, stops, the recorded track and same-route historical comparison. Holding a card opens a bottom action sheet with icons and explanations; edit forms and deletion confirmations share the palette and rounded shapes.

## Alternating builds between two Macs

The project pins `.signing/debug.keystore`. After syncing the code to the other Mac, privately transfer this key and import it once before building. Missing keys or wrong certificates fail explicitly; the build never silently generates a different signing identity. Git does not transfer the private key.

```sh
python3 scripts/import-debug-signing.py "$HOME/Downloads/debug.keystore"
./scripts/gradle-local.sh :app:assembleDebug
```

This retains the certificate used by version 0.1.1 currently installed on the phone, so no uninstall is needed. See [shared signing setup](docs/signing.en.md) for transfer, verification and troubleshooting.

## Data and current limits

Tracks remain in app-private storage, without uploads, telemetry or precise coordinates in logs. System cloud and device-transfer backups are disabled; no additional app-level encryption is provided. **Uninstalling or clearing app data loses local records. Export, import and cloud recovery are not yet available.**

The initial version has track sketches, without a map basemap, navigation, live traffic or red-light detection. Long screen-off recording, real-road accuracy and battery use require continued validation. See [testing](docs/testing.en.md) for actual automated and device results.

## Roadmap

- Improve location-loss recovery, arrival detection and long-session performance; expand device and Android-version coverage.
- Add departure-time/duration scatter plots, stationary hotspots and segment analysis; refine estimates for usual departure windows.
- Add user-controlled local track/statistics export, backup and import.
- Improve route/endpoint editing, large text, screen-reader and large-screen support; explore a dark theme.

These are planned directions, not completed features or release-date commitments.

## Project documentation

- [Product design](PRODUCT_DESIGN.md): product scope, visual style, Android interaction requirements and future development constraints.
- [Technical design](docs/design.en.md): architecture, data models, algorithms and metric definitions.
- [Testing](docs/testing.en.md): verification methods, recorded results and pending acceptance scenarios.
