# Testing and Android device acceptance

This checklist applies across Android brands. Specific models below identify historical test environments, not product restrictions. See [product design](../PRODUCT_DESIGN.md) for product and interaction requirements.

## 0.1.4 keep the recording screen awake (2026-10-22)

- Application/test APK builds pass; all 156 bilingual resources pass. Lint has 0 errors and 7 existing dependency-update warnings. Shared signatures verify, and the app was installed in place without uninstalling the main package.
- The initial 13-case device run passed 7 UI and 5 persistence tests. The service fixture was blocked while re-granting existing permissions and now skips already-granted permissions.
- The service rerun timed out on a screen-on assertion. Its foreground return now resumes the same ActivityScenario with phase-specific diagnostics. ADB stopped responding during subsequent UI cases. This latest fixture is not yet rebuilt/retested; the screen-on feature has not passed device acceptance.
- Automatic escalation review repeatedly timed out even after explicit user approval to continue. Sandbox Gradle/ADB fallback also failed because local sockets are prohibited. Rebuild and rerun all 13 cases after tool recovery. Algorithms are unchanged; JVM tests were not rerun.
- Acceptance must also cover tabs/recreation/foreground return, finish/arrival/service destruction, normal background timeout, and manual power-button locking with continued recording.

Evidence: [initial build](validation/local-build-0.1.4.txt), [permission-fixture build](validation/local-build-0.1.4-final.txt), [initial device run](validation/oppo-screen-awake-preliminary-0.1.4.txt), [incomplete rerun](validation/oppo-screen-awake-incomplete-0.1.4.txt) and [verification.json](verification.json).

## 0.1.3 statistics and detail refinement (2026-10-22)

- Application/test APK builds, all 155 bilingual resources and Git whitespace checks pass. Lint has 0 errors and 7 dependency-update warnings. Both APKs use the shared development certificate.
- OPPO PKJ110 is updated in place to 0.1.3 / versionCode 4 without uninstalling the main app. Normal application launch succeeds.
- All 12 instrumented tests pass in 21.272 seconds: 7 UI cases cover action sheets, editing/deletion, system Back, month/route/direction filters, chart selection and English at 1.65 font scale; 5 persistence cases cover retention, cascades, associations and recording protection.
- Synthetic-data previews of ride overview, time composition, stop metrics, trace, historical comparison and Chinese/English action sheets were inspected. Capture copies only this application's windows. Disposable databases isolate test data, with original daily rides equality asserted after every UI case.
- The preliminary dialog locale issue is fixed. Test windows stay awake to avoid automatic lock-screen interruptions. Algorithms are unchanged; the 28 JVM results from 0.1.1 are retained.

Evidence: [application build](validation/local-build-0.1.3.txt), [final test build](validation/test-build-0.1.3.txt), [device output](validation/oppo-refined-ui-0.1.3.txt) and [verification.json](verification.json).

## 0.1.2 long-press menu validation (2026-10-22)

- Application/test APK builds, 133 bilingual resource references and Git whitespace checks pass. Lint has 0 errors and 7 dependency-update warnings.
- OPPO PKJ110 was updated in place to 0.1.2 using `adb install --no-streaming -r` and the shared Debug key, without uninstalling the application.
- All 10 instrumented tests pass in 11.282 seconds: 5 UI cases cover hidden-by-default management actions, long-press menus, dismissal, normal detail navigation, deletion confirmation, route editing and Back after recreation; 5 Room cases cover cascades, route links, reopen persistence and active-recording protection.
- UI cases use disposable in-memory databases and assert the original daily rides are unchanged after each case. Algorithms are unchanged; the 28 JVM results from 0.1.1 are retained rather than rerun for this UI change.

Evidence: [verification.json](verification.json), [device output](validation/oppo-long-press-0.1.2.txt) and [build log](validation/local-build-0.1.2.txt).

## 0.1.1 validation for this change (2026-10-22)

Project-local Temurin JDK 17.0.20.1, Gradle 8.11.1 and Android SDK 35 are configured on this computer in the Git-ignored `.tooling/` directory. `local.properties` points to the SDK and `./scripts/gradle-local.sh` selects these tools. ADB is at `.tooling/android-sdk/platform-tools/adb`.

- All 128 bilingual resource references and `git diff --check` pass.
- Application and Android test APKs build; all 28 JVM algorithm tests pass.
- Lint: 0 errors and 7 existing dependency-update warnings.
- Version 0.1.1 / versionCode 2; database schema unchanged. The application APK excludes the Replay synthetic track class and demo runtime state.
- Three new persistence cases cover ride deletion cascades and retained routes, route edits/deletion/reopening, and active-recording protection/invalid input. Four isolated-database UI cases cover tabs and Back after recreation, list deletion/cancellation, detail deletion returning to Journal, and route editing/deletion/cancellation.
- **Android instrumentation tests for this change have not run; they await the user's phone connection and installation.** The OPPO results below belong to 0.1.0 and do not validate the new management features on a device.

Current evidence: [verification.json](verification.json). Previous evidence: [verification-0.1.0.json](validation/verification-0.1.0.json). Current build log: [local-build-0.1.1.txt](validation/local-build-0.1.1.txt). After connecting the phone:

```sh
.tooling/android-sdk/platform-tools/adb devices -l
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
# Tests use disposable databases. Finish any active ride first.
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
.tooling/android-sdk/platform-tools/adb shell am instrument -w -r com.ridediary.test/androidx.test.runner.AndroidJUnitRunner
```

## Shared signing validation across Macs (2026-10-22)

Pinned `.signing/debug.keystore` to the certificate already installed with 0.1.1. Application and instrumentation APK builds, signingReport and `apksigner verify --print-certs` pass; both APKs match the public certificate pin. An isolated temporary project rejects both missing and valid-but-wrong keys. The importer rejects a wrong certificate while retaining the correct key, supports repeat imports, sets mode 600 and leaves the key Git-ignored. The other Mac was not accessed; the user still needs to transfer and import this key once, following [shared signing setup](signing.en.md). No phone uninstall is needed for this configuration change.

## 0.1.0 historical validation record

Date: 2026-10-22. Executed on macOS arm64 with Temurin JDK 17.0.20.1 / Gradle 8.11.1 / Kotlin 2.1.20 / SDK 35. Results below distinguish execution from pending device cases. Machine-readable evidence: [verification.json](verification.json).

## Evidence levels

| Level | Scope | Current evidence |
|---|---|---|
| Resources / static checks | Bilingual parity, Kotlin references, Git whitespace | Passed: 125 translated resources; all references resolve |
| JVM unit tests | Segmentation, fix quality, routing, history, monthly statistics | Passed: 28 tests, 0 failures / 0 errors / 0 skipped (analysis 11, routing 9, statistics 8) |
| Android build / Lint | Compose, Room/KSP, manifest, resources, APK | Passed: app APK, test APK and Lint (0 errors; 7 GradleDependency update notices) |
| Android device tests | Room reopen/correction, interrupted recovery, four-screen replay, real service lifecycle | 4 passed on OPPO PKJ110: the original 3 and an added service test; daily ride data retained |
| Real-road tests | Screen-off behavior, adjacent roads, arrival, battery | Not executed; physical device required |

App APK: `app/build/outputs/apk/debug/app-debug.apk` (11,314,623 bytes). Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. `apksigner verify --verbose` verified v2 signing; AAPT confirmed `com.ridediary`, version 0.1.0, min API 26 / target 35, and no Internet or background-location permission. Artifact checksums are in the JSON record.

The 7 Lint notices only report newer dependency versions. The initial release keeps the pinned combination actually built here; no error suppression or Lint baseline was used to bypass validation.

## Historical device execution record

2026-10-22, OPPO PKJ110, reported Android 17 / API 37 and Oplus ROM V17.0.0. The app installed and launched. The original two database tests and four-tab/replay test passed in 13.647 seconds; the additional `ServiceLifecycleTest` passed in 7.151 seconds. Raw evidence: [original 3 tests](validation/oppo-instrumentation.txt) and [real service test](validation/oppo-service-test.txt). These used direct ADB instrumentation; no Gradle connected HTML report was generated.

The initial UI run failed because old Espresso reflected the removed `InputManager.getInstance` method. Pinning Espresso 3.7.0, Runner 1.7.0 and JUnit extension 1.3.0 resolved it, consistent with the [official AndroidX fix](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0). Production runtime dependencies were unchanged. Streamed install failed; `adb install --no-streaming -r` succeeded. USB disconnected several times before connection/authorization was restored.

The service test used standard app-scoped instrumentation grants for location/notifications. Samples went to a disposable database; cleanup stopped the service, deleted that database and asserted the daily database was unchanged. It checked duplicate starts, Activity recreation, brief background persistence and notification-based completion/removal. Battery settings were untouched. This does not establish road-level GPS accuracy or 30–60-minute screen-off survival.

## Automated commands

Final command executed on this checkout:

```sh
./scripts/gradle-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

After updating device-test compatibility and adding the service test, `./scripts/gradle-local.sh :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain` also succeeded. Production code and JVM algorithm tests were unchanged, so the algorithm suite was not redundantly rerun for test-only dependencies.

General reproduction and pending device commands:

```sh
python3 scripts/check-resources.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
adb devices -l
# Direct installation/execution avoids Gradle connected cleanup/uninstall:
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.ridediary.test/androidx.test.runner.AndroidJUnitRunner
# On a dedicated test device (can uninstall afterward):
./gradlew :app:connectedDebugAndroidTest
```

JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`. Lint: `app/build/reports/lint-results-debug.html`. This device run is evidenced by the two raw ADB outputs above; `app/build/reports/androidTests/connected/debug/index.html` is only generated by Gradle connected runs.

Algorithm cases cover stationary drift/backfill, initial slow creeping, missing speed kept unknown or inferred from meaningful displacement, concurrent GNSS/network selection and fallback, large gaps without distance bridging, jumps, poor/stale fixes, no-location elapsed time, merged stops, deterministic replay, departure recognition and early degradation, parallel ambiguity, deviation, U-turns, self-intersection projection, brief/sustained/no-fresh-fix arrivals, insufficient history, exclusion of current/future/anomalous/interrupted/other-route/direction rides, local dates and same-day return trips, correction rebuilding aggregates, midnight clock means, zero wait/single samples, separate component medians and historical-remaining-time ETA.

`PersistenceTest` saves raw fixes, segments and routes in Android Room, closes/reopens the file and checks retention. Classification/exclusion and boundary edits update the valid set/duration while raw point count stays unchanged. Repeated repository starts return one ID; interrupted recovery adds unknown gaps. These passed on this phone. `ServiceLifecycleTest` also verified repeated service intents, Activity recreation, brief background persistence and notification actions. Process termination, long screen-off sessions and notification denial still need device acceptance.

`UiSmokeTest` covers the initial entry point, isolated demo, all four tabs, statistics and replay arrival summary. Chart filters, English, large fonts, TalkBack and permission workflows need additional manual device checks.

An additional ADB screenshot attempt did not capture usable App pixels and its hierarchy contained only system UI. Pixel-level visual acceptance is not claimed; the UI result relies on Compose visibility and interaction assertions.

## General device and real-road acceptance

Record model, Android/manufacturer-system versions, APK version, algorithm 1.0, date, weather/sky visibility and battery settings. Operate only while parked.

| Case | Action and expected behavior | Evidence to collect |
|---|---|---|
| Permissions | Browse without permissions; denied/approximate location blocks start; precise permits it; denied notifications explained; permanent denial has settings entry | Prompts, service start result, displayed state |
| Lifecycle (partly passed) | Duplicate starts, Activity recreation, brief backgrounding and notification stop verified; manual rotation and long-session reopening pending | IDs/counts, notification, raw continuity |
| Screen off | Normal battery settings, lock for 30–60 minutes and background app | Gaps, delivery interval, unknown share, process survival |
| Location recovery | Garage/tunnel and location toggle during recording; gaps unknown and no bridging distance | Trace, unknown intervals, elapsed and distance |
| Revocation / termination | Revoke location, force-stop, kill process or reboot, then reopen | No automatic restart; interrupted row and saved fixes retained |
| Adjacent roads / alternatives | Same-origin alternatives, parallel roads, interchanges, self-crossings, turnback/detours | Ambiguous/uncertain states; no jump to a later branch |
| Arrival | Brief stop outside/inside endpoint, pass through, then genuine ≥30-second stop | No premature finish, confirmation delay, manual fallback |
| Stationary accuracy | Several commutes each way with manual elapsed and stop notes | Moving/stationary/unknown error, creeping and initial wait classification |
| ETA | At least three valid prior rides, inspect multiple positions while parked | Actual count, interval coverage, off-route degradation |
| Corrections/statistics | Route/direction/exclusion/boundary edits, midnight and same-day return trip, restart | Raw retention, synchronized chart/table, one day/two rides |
| Battery | Compare 30/60-minute recording with similar normal usage | Drain, heat, system prompts; no blanket battery exemption |
| Accessibility/privacy | Both languages, maximum font, rotation, gesture/three-button navigation, TalkBack, OEM transfer | Clipping/touch/reading, lock-screen notification visibility, backup policy compliance |

## Result template

| Build/device | Case | Expected | Observed | Evidence | Outcome |
|---|---|---|---|---|---|
| Model / manufacturer system / Android / APK | Scenario and route version | Expected behavior | Time/unknown share/drain | Redacted screenshot or notes; do not put coordinates in public logs | Pass / fix / inconclusive |

Road accuracy is an iteration metric, not a promise of second-level precision. Keep untested items pending; synthetic replay does not establish background reliability across devices.
