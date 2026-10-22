# RideDiary / 途记: MVP Requirements and Development Handoff

Updated: 2026-10-22. This document corresponds to `REQUIREMENTS.zh-CN.md`. It supplies the product requirements, design reference and implementation brief for a new Codex task.

## 1. Task and project identity

- Product: **RideDiary**; Chinese name: **途记**.
- Local project: `/Users/zhaoyf5/Applications/RideDiary`, already created by the user.
- GitHub: `git@github.com:zhaoyufei94/RideDiary.git`, already created. The user reports that the local SSH key is registered with GitHub.
- This handoff contains requirements and an HTML prototype only. **No Android project, code commit or SSH verification has been completed.** Inspect existing files, Git status, remotes and applicable `AGENTS.md` instructions first. Preserve existing work; do not reset or overwrite blindly.
- Next task: implement a runnable initial Android app and complete Chinese/English documentation in that directory. Carry the approved HTML visual style into the real app rather than producing another website or an empty shell.
- Suggested tagline: *Your everyday rides, a little better understood.*

## 2. User and motivation

The user rides a Haojue TVL350 and uses an OPPO Android phone, mainly commuting along Beijing's East Fourth Ring service roads. Adjacent main/service roads make location drift relevant. Screen-off location collection and process survival on OPPO require device testing.

Core question: a ride may feel congested yet take less time. Explain whether a trip was slower while moving, involved more waiting, or simply started at a different time.

The MVP is a **standalone phone app for commute recording and analysis**, with real collection, local persistence and offline recording. No additional hardware, vehicle integration or emphasis on instantaneous speed/fastest-record chasing.

## 3. Agreed scope and exclusions

Include: manual recording start; continuing location collection while backgrounded; ride history; creating commute routes from recorded rides; route recognition and arrival detection during recording; live progress and ETA; arrival summaries; route and monthly statistics.

Exclude: external display, ESP32, Bluetooth hardware, CAN/OBD, vehicle power/waterproof hardware, altitude/mountain/compass/return-home screens, traffic-light countdowns, turn-by-turn navigation, Accessibility, MediaProjection, scraping navigation apps, accounts, cloud sync and paid APIs.

Do not block implementation on a mapping provider's approval. A local Canvas route preview is sufficient initially. A map key or Google Play Services must not be a prerequisite for core functionality. Maps, export and other enhancements may follow later.

Automatic commute recognition means classification **after a user manually starts recording**, not all-day tracking or automatic recording startup.

## 4. End-to-end flow

1. On first launch, allow UI browsing and empty states. Request precise location and relevant notification permission contextually when recording starts, explaining their purpose.
2. Start recording timestamps and locations immediately. Continue when the UI is backgrounded or the screen turns off. Without a learned route, show an ordinary ride with actual elapsed/waiting time, not fabricated progress or ETA.
3. Finish the first ride manually. From ride details, select “Set as commute route,” choose inbound/outbound (work/home), name it and confirm endpoint areas. Simple radius controls and endpoint summaries are acceptable initially.
4. Match subsequent recordings using origin, direction and track overlap. Show “Matching” before confidence is sufficient. Classify the whole recording, retaining samples collected before recognition. Departure time is supporting evidence, not a strict gate.
5. Once matched, display progress, historical comparison and ETA. Show uncertainty when deviating or lacking history.
6. Confirm arrival using destination proximity plus sustained dwell; finish the commute segment and show a summary. Keep manual finish available. Passing near the destination must not immediately terminate recording.
7. Persist the summary in history. Allow direction/route corrections and marking unusual stops or detours as excluded from historical baselines without deleting raw data.
8. Provide route, direction and monthly aggregation that survives app restart.

## 5. Screens and visual design

`ui-prototype.html` is the approved single-file offline design reference. It does not implement real location, persistence or Android background execution. The surrounding website narrative, phone frame, fake status bar, progress slider and scenario buttons are presentation tools, not production features.

### 5.1 Visual language

Quiet, minimal and nature-inspired: warm off-white surfaces, forest-green primary cards, pale green accents and sand-colored waiting time. Avoid neon, technology-blue styling or a dense automotive dashboard. Use restrained borders/shadows, generous space, large monospaced numerals and approximately 12–16 dp corner radii. Chinese body copy may use small English overlines.

| Token | Value | Purpose |
| --- | --- | --- |
| Background | `#efeee7` | Page/background |
| Ink | `#24382e` | Main text and buttons |
| Muted | `#758174` | Secondary text; verify production contrast |
| Line | `#d2d8ca` | Dividers |
| Accent | `#c9ed94` | Progress and highlights |
| Soft | `#e0e7d7` | Tinted surfaces |
| Surface | `#fafbf6` | Cards |
| Wait | `#dbb98b` | Waiting segments |
| Ride card | `#233d30` | Active ride primary card |

The progress bar has a **small vector motorcycle marker** moving from 0 to 100%. Reserve endpoint padding to avoid clipping. Adapt the inline SVG in the prototype to Compose Canvas or VectorDrawable; no generated images or remote assets are needed.

Adapt to real system insets, font scaling and touch targets; do not reproduce the fixed 336px HTML phone frame. Increase tiny prototype labels for legibility. Do not encourage interaction while riding.

### 5.2 Main screens

**Commute / active recording**
- Before departure: recent/common route summary and Start Recording.
- During recording: route/direction, location quality and matching/recognized/deviation/no-fix states.
- Large elapsed time, overall average speed and cumulative stationary time; optionally current stop duration.
- Motorcycle progress bar, percentage and remaining along-route distance.
- Arrival estimate/range. Use “Learning” for insufficient history and unavailable for unmatched routes.
- Elapsed-time difference at comparable route progress using prior rides; never display fabricated history.
- Moving/waiting breakdown and finish control; no performance-chasing prompts.

**Arrival summary / ride details**
- Departure/arrival dates and times, duration, distance and overall average speed.
- Moving, stationary and unknown durations; stop count, longest/mean stop and stationary share.
- Whole-ride historical comparison and explanations for component differences.
- Route/direction, create-route entry, reclassification and exclusion controls.
- The prototype's Summary tab is a showcase shortcut. Production may show the latest summary plus history here; open details from history or arrival. Do not ship only one fixed example.

**Statistics**
- Selectable month: commute days, one-way ride count, distance, total time, stationary duration/share.
- Direction and route filters; charts and tables must both respond to changes.
- Per-ride stacked moving/waiting chart, with unknown time separate where necessary.
- Mean/earliest/latest departure and arrival; mean/fastest/slowest duration plus median; mean/shortest/longest cumulative stationary time per ride.
- Departure-time scatter plots and segment waiting analysis are later enhancements, not MVP blockers.

**Routes / history**
- Route outline, name, direction, length and valid sample count.
- Recorded rides and details; create a route from a selected historical ride.
- Separate work/home directions; support alternative routes for one direction without combining their baselines.
- The prototype route illustration is fictional, not an actual Beijing map.

## 6. Definitions and algorithms

### 6.1 Time, speed and stationary detection

- Store UTC timestamps plus recording timezone/local-date context. Assign months and commute days by the ride's local departure date.
- Prefer monotonic elapsed intervals to avoid wall-clock corrections. Recovery after process/device restart must flag uncertain gaps instead of silently counting them as stationary time.
- Total duration = moving + stationary + unknown. Unclassifiable low-quality or missing intervals are unknown. State explicitly that stationary share uses total duration as its denominator.
- Overall average speed = filtered valid distance / total duration. Moving average = moving distance / moving duration. Mark incomplete data and suppress speed where gaps make it misleading.
- Do not test `speed == 0`. Combine accuracy filtering, sustained low speed and entry/exit hysteresis. Centralize tunable thresholds, record algorithm versions and tune with real rides. Backdate a confirmed stationary segment to the candidate interval start.
- Missing speed is not zero. Slow rolling traffic should not automatically become stationary time. Count stops from merged, qualifying stationary segments.
- Stationary time is **not red-light time**: it may include congestion or personal stops. Maintain this terminology throughout UI and documentation.

### 6.2 Routes and progress

- Derive routes from recorded ordered geometry. Match origin/destination areas, direction, overlap and ordered forward movement.
- Progress is distance projected along the ordered reference route divided by route length, not straight-line distance or simply accumulated ride distance.
- Handle self-intersections, adjacent main/service roads, alternatives sharing an origin, U-turns and detours. Nearest-point matching must not jump to a later route segment.
- Filter minor jitter without masking real deviations. Mark uncertainty instead of forcing misleading monotonic progress; confirm arrival before declaring 100%.
- Arrival radius and dwell duration are initial tuning parameters. Preserve raw samples and editable commute boundaries, with manual finish as a fallback.

### 6.3 Baselines and ETA

- Use prior valid rides on the same route and direction; a recent-20 window is a suggested initial parameter, not a user mandate.
- Exclude the current ride from its own comparison, as well as unfinished, excluded or unsuitable low-quality rides. Display actual sample counts and avoid confident statements with insufficient samples.
- ETA = current time + distribution of remaining historical duration from comparable route progress. Start with median/range; refine by departure window later. No real-time traffic API is required.
- Median total duration need not equal median moving duration + median stationary duration. Clearly distinguish component baselines from the whole-ride baseline.
- For average departure/arrival clock times, unwrap relative to the associated commute window across midnight. Averaging 23:50 and 00:10 must not yield noon.
- Monthly days = distinct local departure dates of valid commutes; ride count = valid one-way rides. A round trip is one day, two rides.
- Provide empty states; label a single-sample baseline appropriately. Zero stationary duration is valid.

### 6.4 Prototype data caveats

The HTML uses fixed illustrative values. Slider progress, ETA and totals are not a coherent location simulation; charts are not complete direction-specific aggregation. Reference the layout, but **do not copy hardcoded demo numbers into production analytics**.

A useful verification example: today's moving `23:18` + stationary `06:18` = total `29:36`. Historical component baselines: moving `22:18`, stationary `09:30`, summing to `31:48`. Component deltas `+01:00` and `−03:12` sum to `−02:12`. An independent whole-ride median of `32:10` produces `−02:34`. Label these as different baselines.

## 7. Suggested Android implementation (versions not selected)

Prefer native Kotlin and Jetpack Compose, Room persistence, Coroutines/Flow, ViewModels and DataStore settings. Start with one app module and clean internal boundaries rather than a server or elaborate multi-module architecture. Select and pin SDK, Gradle, JDK and dependency versions after inspecting the new workspace and host environment.

- Abstract location collection; provide a real Android provider and deterministic track replay. Do not assume Google Play Services on a Chinese OPPO handset. Evaluate system `LocationManager` or another non-GMS implementation first.
- Start a `location` foreground service from the user-visible recording action. Provide an ongoing notification with Stop. Handle location, service and notification requirements according to the chosen target SDK; do not request unrelated permissions for all-day automatic startup.
- Screen-off recording does not automatically require background-location permission. Follow Android's location/foreground-service startup rules. Handle denial, approximate-only access, disabled location and revoked permission explicitly.
- Test OPPO lifecycle behavior on hardware. Emulator success is not proof of reliable screen-off recording. Do not require disabling all battery protections by default.
- The recording service owns session state; rotation or UI recreation must not start another session. Persist incrementally and expose interrupted rides after restart without inventing missing samples.
- Continue elapsed timing without a fix, flag unknown intervals, and do not bridge large gaps into accumulated distance. Reject stale samples and obvious jumps.
- Local storage by default; no accounts, remote backend, analytics or track uploads. Avoid precise coordinates in logs and explicitly define backup behavior so tracks are not accidentally uploaded by automatic system backups.
- Initial UI is Chinese, with all strings resource-based; English UI resources are recommended. Full English/Chinese developer documentation is explicitly required.
- Provide an isolated Demo/Replay mode for UI and analytics without real history. Demo rides must not pollute real statistics.

References:
- [Android location permissions and foreground tracking](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Runtime location access](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Room persistence](https://developer.android.com/training/data-storage/room)

## 8. Suggested data model

- `Ride`: ID, start/end wall clock and timezone, elapsed-time basis, state, valid distance, route ID, direction, exclusion flag/reason, quality summary and algorithm version.
- `TrackPoint`: ride ID, ordered sequence, timestamps/monotonic clock, coordinates and coordinate system, location accuracy, nullable speed/speed accuracy and quality state.
- `RideSegment`: interval boundaries, moving/stationary/unknown classification, distance and classification evidence; retain raw points for recalculation.
- `CommuteRoute`: ID, name, direction, ordered reference geometry, endpoint areas, source ride ID, optional usual time window and version.
- Derive statistics from persisted records. Cached aggregates must be rebuildable after reclassification or exclusion; do not store only irrecoverable monthly totals.

## 9. Delivery stages and acceptance

### A. Project and real UI

Create a buildable Android project, four main screens with navigation/empty states and isolated Demo mode. Translate the HTML design into native Compose. Provide Chinese/English README setup, build and installation instructions. Do not wrap the HTML in a WebView and call it a native recorder.

### B. Real recording loop

Permissions → start → service collection and incremental storage → screen off → manual finish → summary/history → persistence after restart. Surface permission, no-fix and interruption states. Notification Stop works; do not start unsolicited tracking.

### C. Commute classification and analytics

Historical ride → create route → recognize a later recording → progress/arrival/ETA → edit classification/exclusion → linked monthly/direction statistics. New routes with no history degrade gracefully.

### Required validation

- Meaningful algorithm tests: stationary drift, slow movement, missing speed/location, long time gaps, jumps, route deviations, U-turns, parallel/self-intersecting progress, insufficient history, exclusion, midnight boundaries, day deduplication and excluding the current ride from its baseline.
- Database reopen, aggregation after classification edits, duplicate-service-start handling and deterministic replay.
- Run available build, unit and key UI smoke checks. Do not repeatedly run redundant tests.
- Clearly distinguish implemented, simulation-verified and pending OPPO field tests. Missing JDK/SDK/device is an explicit validation limit, not a successful build or field verification.
- User field validation: repeat the commute several times; compare manual timing, stops near the destination, screen-off recording, tunnel/garage recovery and battery use. Accuracy is an iteration target, not a seconds-level guarantee.

## 10. Suggested repository documentation/layout

```text
README.md                    # Full English intro, setup, build, limits; link Chinese
README.zh-CN.md               # Full Chinese counterpart
app/                         # Native Android app
build.gradle.kts, settings.gradle.kts, gradle/ ...
docs/requirements.zh-CN.md
docs/requirements.en.md
docs/design.zh-CN.md
docs/design.en.md
docs/testing.zh-CN.md
docs/testing.en.md
docs/prototype/ui-prototype.html
```

README should cover actual features, architecture, privacy, environment, build/install, Demo versus real recording, OPPO field testing and known limitations. Do not claim provider partnerships or road/weather certification. Let the user choose an open-source license; do not invent unconfirmed licensing or ownership claims.

Git: inspect existing remotes before adding origin. Use the supplied address. Do not rewrite remote history or force-push. Commit code/docs in reviewable stages and report network/SSH/authentication failures accurately. Do not create another Codex chat unless requested; this handoff is for the directory task the user intends to create.

## 11. Suggested first message for the new task

> Implement the native Android MVP of RideDiary / 途记 in `/Users/zhaoyf5/Applications/RideDiary`. The repository is `git@github.com:zhaoyufei94/RideDiary.git`. Inspect existing files and project instructions, then read this handoff's `REQUIREMENTS.en.md` and `ui-prototype.html`. Build standalone commute recording, route recognition, a live commute screen, arrival summaries, history and monthly analytics. Preserve the prototype palette and motorcycle progress marker. Exclude external displays, hardware and traffic lights. Choose an appropriate native Android stack, provide complete English and Chinese README/design/testing docs, run available build/tests and distinguish remaining OPPO device validation. Deliver a runnable initial implementation, not only a plan or static UI.
