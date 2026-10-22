# Design and implementation / v0.1

This document describes implementation. See [product design](../PRODUCT_DESIGN.md) for product scope, visual style and Android interaction requirements.

## Structure and lifecycle

One Android application module, Kotlin, Compose and Material 3. `core/` contains pure deterministic geometry, segmentation, route recognition, historical statistics. `data/` owns Room entities, transactions and a mutex-protected repository. `recording/` owns replaceable `LocationSource`, Android `LocationManager` and a foreground service. `ui/` owns the ViewModel, four screens, trace/motorcycle Canvas and correction dialogs. The ViewModel observes Room with Flow; the service owns recording and does not depend on a visible UI or Activity instance.

Start is a user action in the visible Activity, after precise location permission. The service immediately posts its foreground notification, waits for database recovery, then allocates one ride and subscribes to GNSS/network providers at a requested 2-second interval. A selector prefers good GNSS fixes for 10 seconds, falls back to network, suppresses redundant sub-second callbacks, and lets a good source replace a poor one. This prevents concurrent providers from creating artificial jump/unknown intervals. Requests are serialized on a service-owned channel; repeated starts return without allocating another session. Location and timer checkpoints transact raw fixes, ride summary state and derived segments. The timer continues when fixes disappear. Notification and UI stop use the same service action. `START_NOT_STICKY` prevents accidental recording resurrection. There is no boot receiver or background-location permission. Android may vary delivery intervals; 2 seconds is a request, not a reliability promise.

On a new process, existing RECORDING rows become INTERRUPTED, with the wall-clock gap since the last checkpoint added as unknown. Old monotonic points are not connected to a new clock origin. This gap is explicitly uncertain (and can be distorted by clock changes); the record stays out of valid history and can be inspected/trimmed without deleting raw data. The app does not resume a killed ride. A normal live ride uses `elapsedRealtime`, so wall-clock changes cannot change its elapsed total.

## Data and privacy

`rides`: original UTC start/end, recording timezone, persisted elapsed time and last save wall time, state, route/direction, exclusion flag/reason, algorithm version, optional corrected commute boundaries, confirmed-arrival flag. `points`: ordered raw fixes with UTC, ride-relative elapsed time, WGS84 coordinate system, accuracy, nullable speed/speed accuracy and quality. `segments`: derived moving/stationary/unknown intervals with distance; replaceable from raw points. `routes`: source ride ID, ordered reference points, independent direction, endpoint radii and version.

All writes are local. No Internet permission, trackers, coordinate logs, map API or GMS. Backup/cloud transfer exclusions are in the manifest and extraction rules. The app uses Android app-private storage; no additional application encryption is provided. Language is the only SharedPreferences setting. There is no destructive database migration fallback. A future schema change must include a migration and retention test. Raw fixes and original timestamps remain after edits; UI metrics are recalculated on selected boundaries rather than accumulated monthly counters.

## Analysis version 1.0

Thresholds are centralized in `Policy`. Accuracy must be ≤35 m; a source fix older than 10 seconds is tagged stale. Intervals >15 seconds, invalid endpoints or implied speed >55 m/s are unknown and contribute no distance. Speed with reported uncertainty >2 m/s is ignored. If speed is absent, infer it only when displacement exceeds mean endpoint accuracy; otherwise classify the interval unknown. Never convert missing speed to zero. Invalid raw points are retained, not joined across for distance.

Low speed below 0.7 m/s is a stationary candidate. After 12 seconds, backfill that candidate interval as stationary and erase its drift distance. Exit stationary at 1.4 m/s. Merge contiguous classified intervals; stop counts/longest/mean derive from merged stationary intervals. Slow movement beginning above the entry threshold remains moving. Hysteresis can still classify some very slow movement after a stop as stationary; field calibration is necessary. Unconfirmed short candidates remain moving. Total = moving + stationary + unknown. Stationary share uses total elapsed. Overall speed = valid distance / total elapsed; moving speed uses moving elapsed. Unknown >20% or no elapsed hides speed and excludes valid history.

Route creation uses the selected valid recorded trace, minimum 150 m and four fixes, endpoint radius 20–500 m (defaults 100/70). Recognition needs departure within the start radius, at least four valid observations, ≥120 m ordered progress and ≥75% corridor inliers. Each point projects onto reference line segments inside a local window: at most 60 m behind and a displacement- and time/speed-limited 60–250 m ahead (with a start-radius allowance for the first fix). A small advance penalty discourages distant crossing branches. End residual >25 m or backtracking >30 m indicates deviation/turnback; route attribution persists for manual correction. Near-equal candidates (residual margin <10 m) remain matching. Clock departure time is not a rejection criterion; time-window optimization is deferred.

Progress is projected along the ordered reference distance, smoothed against small backward drift and capped at 99% until arrival. Deviation or stale location suppresses live progress and ETA. Actual arrival requires ≥92% progress, all recent fixes within the end radius, ≥3 fresh fixes spanning 30 seconds with no >15-second gap, and speed <0.7 m/s. Arrival sets the completion flag and 100% summary indicator. Passing an endpoint or a short stop does not suffice. This is a heuristic, not map matching that can guarantee adjacent-road identification. One route containing large missing sections can be a poor reference; inspect its sketch before using it. Manual finish and correction are available. A manually finished matched ride with deviation or less than 92% progress is automatically excluded from baselines; review/correction can restore it.

## History and aggregation

Only completed, non-excluded, classified rides with ≤20% unknown and at least 150 m valid distance enter valid history/statistics. Baselines require same route and direction, strict UTC start before the current ride, a different ID, newest 20 samples maximum. At least three samples are required for conclusions/ETA. Total, moving and stationary medians are independent. The handoff example remains two distinct comparisons: today 29:36 vs total median 32:10 gives −02:34; moving +01:00 plus stationary −03:12 gives −02:12 against component medians.

ETA projects each historical trace to the current along-route position, takes its observed remaining elapsed time, and adds median/25th–75th percentile remaining time to now. The UI also compares current elapsed to historical elapsed at that position and reports actual sample count. Crossing times use the first sample reaching the position (2-second sampling approximation). No live traffic or paid API. No fake ETA before classification or with too few usable historical projections.

Monthly statistics select by departure local date in the stored zone, route and direction. Chart and table use the same filtered set. Days are unique dates; two rides on one date are one day/two rides. Clocks use circular anchoring and unwrap across midnight before mean/min/max; a near-uniform whole-day distribution uses the first observed minute as anchor. This is intended for commute windows, not uniformly distributed 24-hour activity. Arrival clock uses stored end UTC; boundary correction derives corrected UTC from original start plus elapsed offset, so changing the system clock mid-ride can make corrected clock labels approximate. Zero wait and one sample are legal; one sample is labeled.

## Visuals and accessibility

Use exact prototype background `#efeee7`, ink `#24382e`, accent `#c9ed94`, line `#d2d8ca`, soft `#e0e7d7`, surface `#fafbf6`, waiting `#dbb98b`, live card `#233d30`. Small secondary text is darkened to `#53634f` for readability. The motorcycle is drawn from the prototype's 42×30 SVG geometry, with reserved 24 dp edge space so its wheels are not clipped at 0/100%. Large monospace elapsed numbers, 12–16 dp corners, scrolling content, system-inset-aware Scaffold and ≥48 dp primary controls. Canvas charts include text descriptions. Driving interaction is discouraged. Large-font rendering, TalkBack, narrow phones, landscape and system navigation bars remain device acceptance items.

## Sources

Reviewed 2026-10-22: [Android foreground location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions), [location foreground service type](https://developer.android.com/develop/background-work/services/fgs/service-types#location), [AGP 8.9 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes). These support the permission/service design and pinned toolchain; they do not establish lock-screen reliability across devices.

## Ride and route management (0.1.1)

Ride deletion is serialized and transactional. Room cascades to points and segments; routes retain independent shape data even if their source ride is deleted. Route deletion first clears linked ride routeIds, preserving raw tracks, direction and exclusion state. Route edits retain shape/sourceRideId, increment version and propagate direction to linked rides in one transaction. Active rides and routes in use are protected. The schema is unchanged; no migration is needed.

Flow refreshes Journal, Statistics and Routes. A detail BackHandler clears selection and returns to Journal. Replay UI/state is removed; Replay lives in testShared for JVM and Android tests only.

## Long-press management (0.1.2)

Tapping a ride card opens details; holding it reveals deletion. Holding a route card reveals edit/delete; holding the detail summary reveals classification, boundary correction and deletion. Always-visible management buttons are removed. combinedClickable supplies long-press semantics and haptic feedback. Material 3 bottom action sheets dismiss on Back or an outside tap, and deletion still requires separate confirmation. Card composition is keyed by database ID so deleting an item cannot move its menu to another item. UI and repository recording protections remain in force.

## 0.1.3 presentation refinement

ManagementSheets centralizes bottom actions, editor forms and local Canvas line icons. Dialog content inherits the caller's language, density and configuration. Shared DataCard, SectionHeading and TimeComposition components align statistics and details.

Statistics still uses Statistics.month/valid/clocks and Sample.metrics; bar selection is presentation state and does not change storage or aggregation. Filters apply to rings, values, bars and rhythm cards together. The chart viewport fits complete columns and enlarged text expands labels and stacks metrics vertically.

Details retain earlier valid same-route/direction baselines and the distinction between the independent total median and component medians. Presentation changes do not alter recorded tracks, raw data or the recording service.
