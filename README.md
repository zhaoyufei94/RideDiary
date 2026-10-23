# RideDiary / 途记

<img src="docs/brand/ridediary-logo.svg" width="96" height="96" alt="RideDiary logo" />

**Your everyday rides, a little better understood.** · [中文说明](README.zh-CN.md)

Current version: **0.1.6**. The graph-paper logo is used across the launcher, app header, notification and project branding.

RideDiary is an offline, native Android commute journal. Record everyday rides with your phone, split elapsed time into moving, stationary and unknown intervals, and compare each commute with your own history.

For Android 8.0 and later, across Android phone brands. No extra hardware, account, Google Play services or map key is needed. Recording and analysis happen on your phone; the app has no Internet permission.

## What it does

| Screen | Features |
|---|---|
| Ride | Manual recording, elapsed time, average speed, stationary time and location status; motorcycle progress, historical comparison and arrival estimates after route recognition |
| Journal | Ride history and arrival summaries; correct route, direction and commute boundaries, exclude unusual rides, or delete a ride |
| Statistics | Choose a route from a list, then To work / To home; view monthly days, rides, distance, elapsed time and per-ride breakdowns |
| Routes | Group actual work/home tracks under one name; inspect directional samples, edit or delete the named route |

The cream, forest-green, light-green and sand palette pairs with a motorcycle progress indicator. Screens use Jetpack Compose / Material 3. Chinese is the default language; the top-right control switches to English.

## Keep the recording screen awake

After recording starts, RideDiary automatically keeps its visible screen awake across all four tabs. Saving or discarding the recording, or leaving the app, restores normal screen timeout; returning during recording keeps it awake again. You can still lock the phone with the power button while background recording continues. No additional permission is needed; keeping the screen on consumes more power.

## Manage rides and routes

- Touch and hold a ride card in Journal or the summary card in details, choose **Delete ride** and confirm. The ride and raw fixes are removed and statistics refresh; routes created from it remain available.
- The detail back button, Android Back button and back gesture return to the Journal list.
- Routes lists named routes with To work / To home tags and separate actual tracks and sample counts. Hold a card to edit: names apply to both directions, endpoint settings to the selected direction. Deleting a named route removes both references and unlinks rides while preserving history and raw fixes. Finish recording before changing a route in use.
- Saving automatically matches the recorded track and opens a correction list. Its first entry is **Create new route**; alternatively select an existing route and direction. A missing direction is created from the current actual track under the same name. Reopen this list later using **Select or correct route** in ride details.
- Upgrading groups legacy references with the same name (including trailing work/home labels) while preserving rides, directions, reference geometry and links. Statistics shows one route and direction at a time.
- The in-app replay feature has been removed. Synthetic tracks are test fixtures and are excluded from the application APK.

The [HTML prototype](docs/prototype/ui-prototype.html) remains a historical visual reference; its demo controls do not describe current app features.

## Permissions and operating conditions

| Permission or condition | When needed | Purpose and effect of denial |
|---|---|---|
| Precise location | When starting a real ride | Track recording, movement analysis and route matching. Approximate-only or denied location prevents real recording; saved history remains available |
| Notifications, Android 13+ | Requested as needed when starting | Ongoing recording notification and stop action. Recording can continue if denied; return to the app to finish |
| System location enabled | During real recording | Required for normal fixes. If disabled, the app explains the issue; timing continues and unclassifiable intervals remain unknown |
| Location foreground service | After permission and manual start | Continues recording in the background and with the screen off; managed by Android, without a separate permission dialog |

No “Allow all the time” location permission, boot startup, all-day listening or automatic ride start. Recording starts with your action. Manual finish, the notification finish entry and detected arrival all open **Cancel / Delete / Save**; recording continues while waiting for your choice.

## Using RideDiary

1. **Record the first ride.** While parked, tap Start recording, grant precise location and enable system location. Without a saved route, see actual elapsed and stationary time.
2. **Choose how to finish.** Manual stop, notification finish and arrival detection open the same dialog. **Cancel** continues; **Delete** stops and removes this ride and its raw fixes; **Save** finishes and retains it. Location and timing continue while the dialog is open.
3. **Select or correct the route.** Saving tries to match the complete track and opens a route list. The first entry creates a named route; existing routes offer To work / To home. A missing direction uses the current actual track. A matching reverse ride can automatically add the opposite direction to the same route; ambiguous candidates remain unassigned. Dismissing correction keeps the saved ride and automatic result for later review.
4. **Record subsequent rides.** Start manually. Recognition needs some travel before motorcycle progress appears. Historical comparison and ETA need three usable histories for the same directional reference.
5. **View statistics.** Select month, choose a route from the list, then select To work / To home. The route overview combines both directions for the selected month; charts and duration metrics below use the selected direction. Correct classification/boundaries or exclude unusual rides in details without losing raw fixes.

Creating a direction needs four valid fixes and 150 m of track; endpoint radii are 20–500 m. Work/home retain separate actual geometry; ETA history remains scoped to its directional reference.
Operate the app while parked. Automatic recognition happens after you manually start recording.

## Timing, location and battery use

- **Total elapsed = moving + stationary + unknown.** Stationary time can include congestion, brief stops or parking; it does not identify red lights. Missing location is not treated as zero speed.
- Time to the first usable fix varies with surroundings and system behavior. Tunnels, garages, buildings and adjacent roads can affect location and recognition. Progress and arrival estimates are temporarily suppressed when off-route or using stale fixes.
- Arrival detection requires near-complete progress and at least 30 seconds of sustained fresh low-speed fixes. It prompts for Save/Delete rather than ending recording immediately. Estimates use your historical remaining time, without live traffic or second-level guarantees.
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

## Statistics and ride detail presentation

Statistics uses a monthly distance overview, time-composition ring, selectable per-ride bars and departure/arrival rhythm cards. The route overview combines both directions within the selected month; direction tabs filter the charts and duration metrics below. Empty scopes show an honest empty state, and enlarged text switches metrics to vertical layouts.

Ride details share the forest-green overview and grouped cards for time, distance, speed, moving/stationary/unknown intervals, stops, the recorded track and same-route historical comparison. Holding a card opens a bottom action sheet with icons and explanations; edit forms and deletion confirmations share the palette and rounded shapes.

## Alternating builds between two Macs

The project pins `.signing/debug.keystore`. After syncing the code to the other Mac, privately transfer this key and import it once before building. Missing keys or wrong certificates fail explicitly; the build never silently generates a different signing identity. Git does not transfer the private key.

```sh
python3 scripts/import-debug-signing.py "$HOME/Downloads/debug.keystore"
./scripts/gradle-local.sh :app:assembleDebug
```

Both Macs use the same certificate, so signed updates can preserve installed data without uninstalling. See [shared signing setup](docs/signing.en.md) for transfer, verification and troubleshooting.

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
