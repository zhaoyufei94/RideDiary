# Testing and OPPO acceptance

Date: 2026-10-22. Executed on macOS arm64 with Temurin JDK 17.0.20.1 / Gradle 8.11.1 / Kotlin 2.1.20 / SDK 35. Results below distinguish execution from pending device cases. Machine-readable evidence: [verification.json](verification.json).

## Evidence levels

| Level | Scope | Current evidence |
|---|---|---|
| Resources / static checks | Bilingual parity, Kotlin references, Git whitespace | Passed: 125 translated resources; all references resolve |
| JVM unit tests | Segmentation, fix quality, routing, history, monthly statistics | Passed: 28 tests, 0 failures / 0 errors / 0 skipped (analysis 11, routing 9, statistics 8) |
| Android build / Lint | Compose, Room/KSP, manifest, resources, APK | Passed: app APK, test APK and Lint (0 errors; 9 GradleDependency update notices) |
| Android device tests | Room reopen/correction, duplicate starts, interrupted recovery, four-screen replay | 3 instrumented tests compiled, not executed; final ADB recheck still found no devices/running emulator |
| OPPO road tests | Screen-off behavior, adjacent roads, arrival, battery | Not executed; physical device required |

App APK: `app/build/outputs/apk/debug/app-debug.apk` (11,314,623 bytes). Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. `apksigner verify --verbose` verified v2 signing; AAPT confirmed `com.ridediary`, version 0.1.0, min API 26 / target 35, and no Internet or background-location permission. Artifact checksums are in the JSON record.

The 9 Lint notices only report newer dependency versions. The initial release keeps the pinned combination actually built here; no error suppression or Lint baseline was used to bypass validation.

## Automated commands

Final command executed on this checkout:

```sh
./scripts/gradle-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

General reproduction and pending device commands:

```sh
python3 scripts/check-resources.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
adb devices -l
./gradlew :app:connectedDebugAndroidTest
```

JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`. Lint: `app/build/reports/lint-results-debug.html`. Instrumentation: `app/build/reports/androidTests/connected/debug/index.html`. Reports exist only after the relevant command executes. No device means instrumentation has not passed.

Algorithm cases cover stationary drift/backfill, initial slow creeping, missing speed kept unknown or inferred from meaningful displacement, concurrent GNSS/network selection and fallback, large gaps without distance bridging, jumps, poor/stale fixes, no-location elapsed time, merged stops, deterministic replay, departure recognition and early degradation, parallel ambiguity, deviation, U-turns, self-intersection projection, brief/sustained/no-fresh-fix arrivals, insufficient history, exclusion of current/future/anomalous/interrupted/other-route/direction rides, local dates and same-day return trips, correction rebuilding aggregates, midnight clock means, zero wait/single samples, separate component medians and historical-remaining-time ETA.

`PersistenceTest` saves raw fixes, segments and routes in Android Room, closes/reopens the file and checks retention. Classification/exclusion and boundary edits update the valid set/duration while raw point count stays unchanged. Repeated repository starts return one ID; interrupted recovery adds unknown gaps. Repeated service intents, system process termination and notification actions still require device tests; repository tests do not prove service lifecycle behavior.

`UiSmokeTest` covers the initial entry point, isolated demo, all four tabs, statistics and replay arrival summary. Chart filters, English, large fonts, TalkBack and permission workflows need additional manual device checks.

## Physical OPPO matrix — all pending

Record model, Android/ColorOS versions, APK version, algorithm 1.0, date, weather/sky visibility and battery settings. Operate only while parked.

| Case | Action and expected behavior | Evidence to collect |
|---|---|---|
| Permissions | Browse without permissions; denied/approximate location blocks start; precise permits it; denied notifications explained; permanent denial has settings entry | Prompts, service start result, displayed state |
| Lifecycle | Repeated start taps, rotation and reopening create one session; notification stops only that session | IDs/counts, notification, raw continuity |
| Screen off | Normal battery settings, lock for 30–60 minutes and background app | Gaps, delivery interval, unknown share, process survival |
| Location recovery | Garage/tunnel and location toggle during recording; gaps unknown and no bridging distance | Trace, unknown intervals, elapsed and distance |
| Revocation / termination | Revoke location, force-stop, kill process or reboot, then reopen | No automatic restart; interrupted row and saved fixes retained |
| Adjacent roads / alternatives | Same-origin alternatives, parallel roads, interchanges, self-crossings, turnback/detours | Ambiguous/uncertain states; no jump to a later branch |
| Arrival | Brief stop outside/inside endpoint, pass through, then genuine ≥30-second stop | No premature finish, confirmation delay, manual fallback |
| Stationary accuracy | Several commutes each way with manual elapsed and stop notes | Moving/stationary/unknown error, creeping and initial wait classification |
| ETA | At least three valid prior rides, inspect multiple positions while parked | Actual count, interval coverage, off-route degradation |
| Corrections/statistics | Route/direction/exclusion/boundary edits, midnight and same-day return trip, restart | Raw retention, synchronized chart/table, one day/two rides |
| Battery | Compare 30/60-minute recording with similar normal usage | Drain, heat, ColorOS prompts; no blanket battery exemption |
| Accessibility/privacy | Both languages, maximum font, rotation, gesture/three-button navigation, TalkBack, OEM transfer | Clipping/touch/reading, lock-screen notification visibility, backup policy compliance |

## Result template

| Build/device | Case | Expected | Observed | Evidence | Outcome |
|---|---|---|---|---|---|
| Model / ColorOS / Android / APK | Scenario and route version | Expected behavior | Time/unknown share/drain | Redacted screenshot or notes; do not put coordinates in public logs | Pass / fix / inconclusive |

Road accuracy is an iteration metric, not a promise of second-level precision. Keep untested items pending; synthetic replay does not establish OPPO background reliability.
