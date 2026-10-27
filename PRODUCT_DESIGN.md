# RideDiary / 途记 Product Design

Version: v0.3.3 product baseline · Updated: 2026-10-26 · [中文](PRODUCT_DESIGN.zh-CN.md)

This document guides product decisions, UI design and future development. **“Current” describes existing behavior; “requirements” constrain future changes and do not imply completed implementation or acceptance; “planned” means not delivered.** The product targets Android phones across brands, cities, motorcycle models and commute routes.

## 0.3.3 recommended route traffic

- Traffic starts collapsed, using the same arrow interaction as current weather. Keep conditions and driving reference time visible; reveal update time, trends and route differences only when expanded. Each card remembers its own state across data refreshes; omit empty toggles when no details exist.
- Remove route-card wind and show traffic below weather from two hours before until one hour after each planned directional departure. Query only while Record is visible, the app is foreground and recording is idle. Share weather entry, 15-minute checks and pull refresh, with a 20-minute cache and fresh data on manual refresh; no background traffic timer.
- Settings, track maps and recommendation cards open one Baidu Maps and traffic panel with a single Android SDK AK field retaining the existing key. Basemaps and recommendation traffic have independent switches, with recommendation traffic off by default. No additional server key, future forecasts or optimal departure windows.
- Request traffic-inclusive routes with the Android SDK, converting and verifying saved geometry locally. Send endpoints and at most three key waypoints, rather than the full raw trip. Create, submit and destroy search instances on Main, including cleanup after 15-second timeout or page cancellation. Label times as driving references, not motorcycle predictions.
- Under 80% known traffic coverage remains insufficient. Missing or misaligned traffic arrays are unknown; current conclusions expire after 20 minutes. Compare congestion share between sufficiently covered updates 5–45 minutes apart, labeled as change since the previous update.
- Traffic is a nearby-origin/destination planning reference: allow up to 1 km per endpoint and a length ratio of 0.3–3.5, rejecting reversed direction without requiring 90% overlap. Estimate directional proximity within 100 m both ways, choose the most similar usable candidate and display similarity, length difference and endpoint offsets. Missing routes and substantial differences have distinct messages; missing geometry is not 0% overlap.
- If waypoint-constrained search returns no route, retry once with endpoints only. Both attempts support a 15-second timeout and cancellation cleanup. Logs contain SDK status, candidate counts, lengths, offsets and coverage only, excluding keys, coordinates, route names and raw responses. Substantial changes in the planning route suppress congestion trends.

## 0.3.2 route direction, data location and recommendation grace period

- The selected direction in route details controls reference geometry, plans and linked trips together. Label the list with its direction and retain selection after returning from a trip or recreating the page.
- Settings displays the actual trip database path with touch-and-hold copying. A system-document-permission-protected, read-only entry exposes only the main database, WAL and SHM, excluding weather/map credentials and other private files. Do not migrate data or add storage permissions. Prefer a file manager and fall back to the system file picker.
- Replace the earlier removal-at-departure rule: fixed, learned and default departures remain for one hour afterward, including the deadline. Carry late-night plans into Today after midnight with a Yesterday label, then remove them after expiry. Evaluate schedules against the original departure date; keep the planned forecast window and manual recording start.

## 0.2.1 upgrade compatibility

Fix startup crashes after updating certain 0.2.0 builds. Room v4 handles both v3 variants, with or without planMode, retaining trips, raw tracks, boundaries, exclusion states and original route fields. Earlier weekday-only plans retain Custom weekdays and can be changed to Workdays in route settings. New routes and direct v2 upgrades still default to Workdays. App/test builds and four targeted device migration cases passed; this phone's upgrade, startup and data retention are verified within the testing record's scope.

## 0.2.0 travel plans and two-day suggestions

- **Record:** pull down to refresh weather and Today/Tomorrow forecasts; remove recent routes and the separate refresh button. Provider/license links appear once in the footer, with deduplicated progressive queries.
- **Plans:** configure Workdays or Custom weekdays per direction. Workdays excludes holidays and includes make-up workdays from the bundled China calendar; custom weekdays ignore holiday rules, and clearing all excludes a direction. Bundle 2026. No formal 2027 schedule was found, so no estimates or empty calendar are shipped. Update calendars with APK releases, without runtime requests. Missing years show an explicit state and withhold workday suggestions.
- **Time and dates:** choose a fixed time or a circular median from up to 20 usable same-direction trips over 60 days, separating known work/rest categories when at least three samples exist. Defaults without history are 08:00 to work and 18:00 to home. Check today/tomorrow in the phone’s current timezone; retain strictly future departures and order by time. Reached times disappear without shifting or auto-recording. Update on foreground entry, minute boundaries and pull refresh.
- **Weather:** replace current precipitation amount with the maximum hourly probability covering the next 60 minutes. Only current weather shows retrieval time. Route cards label feels-like temperature ranges, precipitation chance and gusts from endpoints across planned departure through median duration, or one hour without history. Missing data stays “—”, incomplete coverage is explicit and actual temperature never substitutes for missing feels-like values.
- **Refresh feedback:** retain usable data up to six hours old and show one Toast per failed batch, without cache/failure captions. No data remains unavailable, and retained data keeps its original timestamp. Current and endpoint weather share queries; route cards repeat neither time nor source.
- **Quick start:** retain route, direction, departure and a small play icon with a 48 dp target. Selection stays bound through permissions and saving; missing reverse references are never invented.
- **Data:** Room v3 explicitly adds plan mode, weekday bits and optional fixed minute, defaulting existing routes to workdays/automatic time while retaining trips/tracks. Update all same-group/same-direction references together without affecting the opposite direction. Preserve the palette, graph-paper logo and motorcycle progress.

Version 0.2.0 / versionCode 10 retains shared debug.keystore signing. The user reports usable weather in 0.1.8; this version is build-only, with plans, migration, pull refresh and presentation pending acceptance. See [workday calendars](docs/work-calendar.en.md) for sources and annual updates.

## 0.1.8 weather and route recommendations

- **Record:** show current conditions, feels-like temperature, wind/gusts, current precipitation and today’s range/rain chance; label a recent route-origin fallback explicitly. Preserve cream/green branding and motorcycle progress.
- **Recommendations:** rank up to 20 usable same-direction trips from 60 days by frequency, recency, departure time and time of day, favoring To home in the afternoon without inventing reverse references. Use a circular median and separate weekday/weekend samples when at least three exist; fewer than three is a reference time.
- **Quick start:** small play icons retain 48 dp targets and route/direction labels. Selection survives permissions and stays bound through saving; incomplete/deviated trips retain the association but are excluded until corrected.
- **Forecast window:** combine origin/destination hourly temperatures, peak rain probability and gusts from departure through median historical duration, using one hour without duration history. Missing data remains explicit. A usual departure up to six hours ago previews now; otherwise preview tomorrow.
- **Settings:** default Open-Meteo, optional off switch or QWeather using the user’s dedicated API Host and API Key, applied on save. Retain the logo, approved slogan and language selection.
- **Data and fallback:** only weather uses networking, with two-decimal coordinates and no full tracks. Approximate location suffices for weather; recording still needs precise access. Cache for 20 minutes; marked fallback may be up to six hours old. Missing values use “—”, never invented clear skies or zero rain. Weather failure cannot block recording; no background weather polling or reminders.

That version was 0.1.8 / versionCode 9, database v2. Only the app build is complete; live weather, permission/UI behavior and bound-route workflows remain unverified.

## 0.1.7 terminology, route navigation and settings

- **Terminology:** a trip is one recorded journey; a route is a fixed travel path. Main destinations are Record, Trips, Statistics and Routes.
- **Copy:** use direct labels and states, removing long-press instructions, decorative captions and stationary-time explanations while retaining permissions, errors, missing-data states and deletion consequences.
- **Route navigation:** the Routes list contains routes only. Open a route to see linked trips in both directions, then open trip details; Back returns to that route, while details opened from Trips return to the trip list.
- **Unlinking:** hold a trip inside its route to unlink it while retaining the trip, raw track, direction and exclusion state. Reference geometry remains; active recordings cannot be unlinked.
- **Settings:** the top-right gear opens the logo, installed version, slogan and Simplified Chinese/English selection. Language changes preserve the current page.
- **Brand copy:** retain “每天的骑行，多一点了解” and “Your everyday rides, a little better understood.” as the Chinese/English slogans, shown in Settings.

That version was 0.1.7 / versionCode 8; the database remains v2. See testing documentation for validation status.

## 0.1.6 route and finish contract

- **Route before direction:** Select a route first: the overview below the route selector and above direction tabs combines both directions for valid distance, commute days and ride count in the selected month. Charts and duration metrics below the tabs use To work / To home separately, without cross-route aggregation. Routes shows one card per name with separate directional samples and tracks.
- **Logical route versus actual reference:** groupId identifies the named route; each direction retains actual geometry, endpoint radii and historical links. Creating the same name reuses the group, while existing directions are selected rather than recreated. Migration normalizes trailing direction labels, whitespace and English case to group legacy references, preserving IDs and raw records.
- **Every finish requires review:** manual stop, notification finish and arrival detection open Cancel / Delete / Save. Cancel or Back continues; Delete stops and removes the current ride and raw points; only Save completes and retains it. Timing and location continue during review. Arrival prompts once; after cancelling, manual finish remains available.
- **Classification after saving:** match the complete track again. When a group has only one direction and the complete reverse trace clearly matches it, create the missing reference from this ride's actual points. A reversed reference is inference only, never stored as fabricated travel. Ambiguous groups/directions remain unassigned.
- **Correction:** saving opens the route list, also reachable via Select or correct route in details. Create new route is the first item; existing routes expose both directions. Selecting a missing direction opens a form locked to that group name and creates it from the current ride. Dismissing correction does not undo the saved ride.
- **Management:** rename all directions together; edit endpoint radii for the selected reference. Deleting a group unlinks rides and retains history/raw fixes. A group in active use is protected. Short tracks can be saved or classified, but cannot create a reference.
- **Android interaction:** use Material 3 dialogs, scrolling lists and bottom forms. Direction has text and selected semantics as well as color. Back closes overlays and keeps recording. Preserve the palette, graph-paper logo and motorcycle progress. Large text, TalkBack and landscape still require broader coverage.

Room v2 explicitly migrates v1 with groupId and arrivalSuggested. ETA continues using strictly earlier histories of the concrete directional reference, avoiding position comparisons between different geometries. The user has verified screen-awake behavior; it is not retested in this iteration. Recording remains active during finish review.

## 1. Product identity

**Your everyday rides, a little better understood.**

Use **RideDiary** as the English product name and **途记** as the Chinese name consistently across app headers, demo prototypes, document titles and footers. “Commute” describes a feature or use case, never a separate product name. The combined brand label is “RideDiary / 途记”.

RideDiary is a phone-only, offline-first commute journal. It serves people repeatedly riding a set of commute routes who want to understand their time. Why did a ride feel congested yet finish sooner? Was movement slower, stationary time longer, or departure different?

Priorities are reliable recording, an honest moving/stationary/unknown breakdown, comparison with personal history on the same route and direction, and understanding long-term patterns. Instantaneous speed, speed records and racing rankings are not product goals.

Principles:

- **User control:** manual start and an always-available finish; recognition happens only within an explicitly started ride.
- **Honest data:** explain missing fixes, routes or history; do not invent progress, zero waiting or ETA.
- **Independent and offline:** core features require no GMS, map key, extra hardware or server.
- **Traceability:** keep raw data separate from derived metrics; corrections retain raw fixes.
- **Minimal interaction while riding:** emphasize elapsed time and status; review and operate while parked.
- **Native Android behavior:** preserve the brand while following platform navigation, permissions, layout and accessibility conventions.

## 2. Scope and status

| Capability | Current baseline | Planned direction |
|---|---|---|
| Recording | Manual start, location foreground service, manual/confirmed-arrival finish, persistence, interruption marking | Long screen-off reliability, recovery, power and long-session performance |
| Analysis | Moving/stationary/unknown segmentation, time, distance and average speed | Field calibration, stationary hotspots and segment analysis |
| Routes | Creation from recorded tracks, editing names/direction/endpoint radii, deletion, multiple-route recognition, local sketches | More intuitive track-shape and endpoint editing |
| Comparison | Same-route/direction history, same-position elapsed differences, historical remaining-time ETA ranges | Departure-window-aware sampling |
| History and statistics | Details, classification/exclusion/boundary correction, linked month/route/direction filters | Departure-time/duration scatter plots and trends |
| Experience | Four native screens, Chinese/English switching, system Back to Trips | Large-text and screen-reader improvements, large screens, dark theme |
| Weather and departure | Current/daily weather, plan-window forecasts, Today/Tomorrow routes and directional quick start | Configurable adverse-weather reminders |
| Data management | Private local database, retained raw fixes, system backups disabled | User-controlled local export, backup, import and migrations |

No map basemap, navigation, live traffic, red-light detection, account or cloud sync is currently provided. Vehicle displays, CAN/OBD, third-party navigation scraping, Accessibility/MediaProjection capture and racing features are outside current scope. Prototype decoration or sample numbers do not establish a shipped feature.

## 3. User journeys

### First experience

Allow browsing and empty states before authorization. Record offers **Start recording**. The in-app replay entry and demo runtime have been removed at user request. Synthetic tracks are only automation fixtures and are excluded from the application APK.

### First real ride

While parked, tap Start → explain location and notifications → request permissions as needed → start recording and its system notification. Without a route, show an ordinary ride, elapsed/stationary time and location quality, without invented progress or ETA. Timing continues without fixes; missing intervals are not stationary. Choose Save to finish and open details/correction; Cancel continues and Delete discards.

Choose **Select or correct route** from the post-save list or details, name it, select To work / To home and confirm endpoint areas. Current minimums are four valid fixes and 150 m; endpoint radii are 20–500 m, defaulting to 100 m at the start and 70 m at the end. Work/home are independent directions; multiple routes per direction are allowed.

### Subsequent commutes

Manual start → recognition from start area and ordered observations → route name and motorcycle progress → historical comparison/ETA when enough samples exist → confirmed arrival after sustained low speed near the endpoint.

Recognition needs some travel; departure clock time is not a hard filter. Ambiguous candidates stay uncertain. Deviation, turnback or stale fixes suppress unreliable progress and ETA. Current arrival needs at least 92% ordered progress, endpoint proximity and at least three fresh low-speed fixes spanning 30 seconds without long gaps. Progress stays at or below 99% until confirmation, then becomes 100% after saving. Passing the endpoint briefly must not finish a ride; manual finish always remains available.

### Review

Show actual elapsed, moving/stationary/unknown time, historical differences and real sample counts. Explain the learning state when history is insufficient. Allow route/direction correction, exclusion of unusual stops/detours and commute-boundary adjustment in seconds relative to the original start. Recalculate details/statistics and retain raw data.

Mark interrupted rides separately, retain saved points and never automatically resume them or use them as normal completed baselines. A saved ride still matching a reference but substantially incomplete is currently auto-excluded, with later review/correction available.

## 4. Information architecture

| Screen | Hierarchy and actions | Empty/degraded states |
|---|---|---|
| Record | Primary: recording state, elapsed, start/finish; secondary: location quality, route/motorcycle progress; supporting: breakdown, history, ETA; before recording: weather, Today/Tomorrow routes | Clear start entry; ordinary ride without a route; no placeholders pretending to be history |
| Trips | History → details/arrival → create route, classify, exclude, adjust boundaries | Explain recording; visibly mark interrupted and excluded rides |
| Statistics | Month/route/direction filters → totals → per-ride stacked chart → clock/duration statistics | Explain no valid samples; label one sample; zero stationary time is valid |
| Routes | Name, direction, length, sample count, local track sketch and history entry | Explain creation from history; do not present sketches as navigation maps |

Requirements: give each context a clear primary action. Errors explain their impact and a useful next step. Separate durations from departure/arrival clock times and label distance, speed and percentage units. Use text or icons alongside color for important states.

Top-level pages no longer use a fixed brand bar, and their main heading does not repeat the bottom tab name. The header carries useful context only: Record shows the date with “Ready to ride/Recording” and location status; Trips shows this month’s trip count and distance plus the all-time total; Statistics puts month switching directly in the header; Routes shows route and linked-trip counts. Settings remains a secondary header icon and does not get a row of its own.

Trips list cards and detail summary cards expose correction/deletion menus on long press, followed by a separate deletion confirmation, cascading to raw points and segments while retaining saved route geometry. Detail Back controls and Android Back return to the source list: Trips or the owning route. Route cards expose editing names, direction and endpoint radii, and deletion through a long-press context menu. Direction changes propagate to linked rides. Route deletion unlinks rides while retaining their raw tracks and direction. A route in use by an active recording cannot be edited or deleted.

## 5. Recording states and failures

Lifecycle: idle → permission request → recording → finish review (still recording) → saved or discarded, with interruption as an alternative outcome. Location quality, moving/stationary/unknown classification and route matching are independent states. “Matching” does not mean recording has stopped; “stationary” does not mean arrived.

| Situation | Expected experience |
|---|---|
| Denied/approximate location | Explain precise location; block real start while allowing browsing saved rides |
| Notification denial | Explain the missing normal notification stop entry; allow recording and in-app finish |
| Location disabled, first fix, poor signal or gap | Honest quality state, continuous timing, unknown intervals rather than fabricated distance/stationary time |
| Ambiguous routes, deviation or turnback | Preserve uncertainty; suppress unreliable progress/ETA; permit later correction |
| Service start/storage failure | Explain failure; never claim successful start or saving |
| Background, Activity recreation, duplicate start | One ongoing ride; UI returns to authoritative recording state |
| Permission withdrawal/process death | Retain points and mark interrupted on reopening; no silent reconstruction or restart |
| Early manual finish | Retain the ride and indicate whether baseline eligibility needs review |

Future recovery changes must keep the service authoritative over recording; the UI observes it. Back, tab changes and rotation must not stop a ride.

## 6. Permissions, timing and resources

Request location and notifications only when starting real recording. Precise location supports tracks/recognition; Android 13+ notifications provide ongoing status and finish. Declare a location foreground service and start it through a user action in a visible Activity. Do not request all-the-time background location, boot recording or automatic starts. See [location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions) and [location foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#location).

Requirements: explain purpose, denial effects and retry paths. Do not block first-launch browsing, repeatedly prompt or demand unrelated permissions. Contextualize system-settings links; do not require blanket removal of battery restrictions.

Current fixes/checkpoints are requested about every two seconds; delivery is system-controlled. First fixes and recognition have no fixed latency promise. ETA needs at least three usable same-route/direction histories. Arrival requires sustained fresh low-speed observations, never a timer alone without fixes.

Continuous location consumes power and processing. No wake lock or battery-optimization exemption is currently requested. Long screen-off continuity, battery drain, heat and very long rides need measurement. Do not invent battery percentages, endurance or road-accuracy guarantees.

## 7. Metric and data contracts

- Total elapsed = moving + stationary + unknown. Unknown is not zero speed; stationary time is not “red-light time.”
- Overall average speed = valid distance / total elapsed. Hide unreliable speed with excessive unknown data; do not bridge gaps for distance.
- Current valid commute baselines require completed, route-associated, non-excluded rides, at least 150 m valid distance and at most 20% unknown time.
- Baselines use strictly earlier rides of the same route and direction, newest 20 maximum. At least three usable samples are required for conclusions/ETA. Total and component medians are independent; component differences need not sum to the total difference.
- ETA uses historical remaining-time median and quartiles at the current position, without live traffic. Identify sample count and estimation.
- Group months by local departure date in the recorded timezone. Count unique commute dates; two rides on one date mean one day/two rides. Unwrap clocks across midnight before aggregation.
- Scope the overview to month and route across directions; apply the direction filter to charts and duration metrics below the tabs. Recalculate from raw data after corrections, rather than editing display totals.
- Use monotonic time for uninterrupted elapsed duration, UTC/timezone for clock labels and grouping. Mark interruption gaps uncertain; never join monotonic origins from separate processes.

Exact quality, hysteresis, jump and projection thresholds live in [technical design](docs/design.en.md) and `core/Models.kt`. Metric changes require matching copy, algorithm-version and meaningful test updates.

## 8. Visual style

The approved brand logo is **cream graph paper, one continuous forest-green trace, and lime/sand endpoints**, in a flat style without a notebook outline, folded corner, dimensional shadows or added motorcycle. Use it for the launcher, Settings and public branding; do not keep a permanent brand bar on the main screens. Keep the in-app motorcycle progress indicator as a functional element. The editable master is [ridediary-logo.svg](docs/brand/ridediary-logo.svg); see [brand assets](docs/brand/README.md) for resources and Android adaptation rules.

Keywords: **quiet, natural, clear, journal-like**. Preserve the [prototype](docs/prototype/ui-prototype.html) hierarchy and character while adapting native components to real windows. Use whitespace, light surfaces, thin separators, 14–20 dp corners and prominent monospace elapsed numbers. Dense speed dashboards and neon styling do not fit the current identity.

| Role | Value | Use |
|---|---|---|
| Background | `#efeee7` | Cream page |
| Ink/primary | `#24382e` | Headings, primary text and controls |
| Secondary text | `#53634f` | Labels/explanations; darker than prototype `#758174` for small-text readability |
| Separator | `#d2d8ca` | Borders/dividers, never sole critical-state indication |
| Accent | `#c9ed94` | Progress and emphasis |
| Soft | `#e0e7d7` | Secondary containers |
| Surface | `#fafbf6` | Cards/content |
| Stationary | `#dbb98b` | Stationary portion of time charts |
| Live card | `#233d30` | Deep-green recording card with light text |

The motorcycle is a core identity element: retain the prototype's 42×30 vector geometry, drive it from actual ordered route progress, and reserve edge space so wheels remain visible at 0/100%. Never reach 100% before confirmed arrival. Explain missing location/deviation rather than animating imaginary movement. Provide readable route/progress/state text alongside graphics.

Current theme is fixed brand light mode. Dark mode is not implemented. Dynamic color must not replace this palette without design validation. Check contrast and hierarchy when adding colors, motion or components.

## 9. Android design requirements

These are design/acceptance requirements. Using Compose / Material 3 does not establish complete compliance.

### Native components, navigation and Back

Prefer Material 3 navigation, buttons, cards, dialogs and inputs with a consistent brand theme. Keep four top-level phone destinations; details belong to their list. Dialogs must be dismissible and saves provide clear feedback. System Back dismisses overlays, leaves details, then delegates to Android; it must not stop recording. Support platform Back gestures and verify predictive Back instead of faking system animations. References: [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [predictive Back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back).

### System bars and adaptive layout

Use edge-to-edge layout with correct WindowInsets for status/navigation bars, cutouts, gesture areas and the IME. Backgrounds may extend behind bars; important content and touch targets remain unobscured. Do not copy the HTML phone frame or decorative status bar. Adapt to available window size, not brand or a fixed prototype width. Reflow/scroll for narrow windows, landscape, split screen and larger fonts. Navigation rail/two-pane expanded layouts are a future direction. References: [system bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars), [adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps).

### Accessibility and localization

Use at least 48×48 dp interaction targets and sp text; prefer 14–16 sp body text instead of shrinking overflowing copy. Target at least 4.5:1 body-text contrast and 3:1 for important non-text elements. Keep actions and metric meaning at large font scales. Give charts, sketches and progress meaningful semantics; avoid redundant decorative-icon announcements. Check TalkBack order, focus and labels. Never rely on color alone; keep motion restrained and honor system animation settings. Resource all Chinese/English copy, accommodate longer translations and local date/number formats, and avoid sentence concatenation. References: [touch and default semantics](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [accessibility design](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility).

Scrolling, insets, primary touch sizes and some semantics exist today. Full Back flows, predictive Back, large-text/TalkBack journeys and large screens still require inspection and improvements. This specification is not an acceptance result. Official references reviewed 2026-10-22.

## 10. Privacy and ownership

Trips and full tracks stay in app-private storage without uploads, accounts or telemetry. Optional weather uses Internet access to send two-decimal current/route-endpoint coordinates to the selected service and can be disabled. Android Keystore encrypts the QWeather key; host and language preferences remain local. Tracks have no additional application encryption. System cloud/device-transfer backups are disabled, with no precise coordinate logging or universal manufacturer-transfer verification claim.

Explain that uninstalling/clearing storage loses records, with no current export/cloud recovery. Future export/import/backup must be user-initiated and define formats, privacy and retention. Classification/exclusion retains raw fixes. Future deletion must explain consequences and require confirmation. Schema changes need migrations and retention tests rather than destructive rebuilding.

## 11. Acceptance and priorities

The [test record](docs/testing.en.md) documents actual automated and device results. One device does not establish all-device or real-road reliability. Brand names identify actual test environments, not product scope.

| Priority | Acceptance focus |
|---|---|
| P0: reliable records | Denied/approximate/revoked permissions, notification denial, no fixes, duplicate starts, recreation, storage failure, interruption; no duplicate ride or loss of saved raw points |
| P0: real roads | Repeated work/home rides, parallel roads, brief endpoint stop versus arrival, garages/tunnels, 30–60 minutes screen-off with normal battery settings; record error, unknown share, drain and heat |
| P1: understanding and interaction | Onboarding, learning/deviation/interruption copy, finish, corrections, linked filters, both languages, large fonts, TalkBack, Back and navigation bars |
| P1: analysis and management | Scatter plots, hotspots, departure windows, route/endpoint editing, local export/backup/import |
| P2: expanded experience | Large screens, dark theme, long-term performance; any future map basemap must preserve offline/key-independent core use |

Field results record Android/system version, model, app/algorithm version, route conditions and battery policy. Distinguish passed, needs work and untested; promise no unmeasured accuracy/endurance. Formal distribution, release signing and licensing remain undecided for release preparation.

## 12. Starting work in another conversation

Read this specification first, then [README](README.md) for usage, [technical design](docs/design.en.md) for implementation and [testing](docs/testing.en.md) for evidence and gaps.

| Location | Responsibility |
|---|---|
| `app/src/main/java/com/ridediary/core/` | Quality, segmentation, route projection and statistics |
| `app/src/main/java/com/ridediary/data/` | Models, Room, transactions, correction and recovery |
| `app/src/main/java/com/ridediary/recording/` | System location, foreground service, recording lifecycle |
| `app/src/main/java/com/ridediary/weather/` | Weather providers, cache, credentials, location queries and recommendations |
| `app/src/main/java/com/ridediary/ui/` | Screens, components, charts, theme, dialogs and ViewModel |
| `app/src/main/res/values/strings.xml`, `values-en/strings.xml` | Chinese/English UI copy |
| `docs/prototype/ui-prototype.html` | Visual/demo reference, not Android functionality |
| `app/src/test/`, `app/src/androidTest/` | Algorithm, persistence, UI and service tests |

Original handoff requirements remain in `docs/requirements.*.md`; personal phone, city and early setup details are historical context. This document defines current product scope and design direction. New explicit user instructions take precedence. If code and specification differ, identify and resolve the gap rather than presenting plans as delivered.

Update both languages, feature status and affected validation with each iteration. Preserve metric and privacy contracts. Keep README focused on users; debugging narratives and device evidence belong in test records.

## 0.1.3 statistics, details and action sheets

Long-press management uses Material 3 bottom sheets showing the selected ride/route, its summary and explained actions with local line icons. Deletion uses a restrained brick-red treatment and still requires confirmation. Edit sheets provide scrolling content with a persistent save/cancel footer.

Statistics and details use light cards with 24 dp corners, forest-green hero cards with 26 dp corners and sheets with 28 dp top corners. Distance, commute days/counts, time composition, per-ride duration and commute rhythm form distinct information levels. Details lead with actual elapsed time, distance and speed, followed by intervals, stops, the track and historical baselines; insufficient history never produces invented differences. Enlarged text uses vertical layouts and selection includes accessibility semantics.

## Keep screen awake during recording (0.1.4)

Keep the visible app awake automatically after the recording service starts, consistently across all four tabs. Restore normal timeout on save, discard, service stop or backgrounding. Foreground return, rotation and language recreation restore the current recording behavior. The power button can still lock the phone. No setting toggle or permission prompt is added. Continuous screen use increases power consumption.

## 0.2.3: interface density and hierarchy (2026-10-24)

This pass reorders all four top-level screens around “information first, decoration second.” The fixed brand bar and large page titles that repeated the bottom tabs are gone. Headers now show the date, this month’s trip count/distance, linked-route counts or the month selector; Settings becomes a secondary icon. Page padding is 16 dp with roughly 10 dp between sections. Light cards use 18 dp corners and 16 dp padding, secondary panels 14/14, the record and statistics hero cards 20/18, and trip rows 14/12.

Record puts “Start recording” immediately after the header. Weather collapses into one line with temperature, condition, feels-like and rain, expanding to wind, next-hour rain, update time and the location entry. Today/Tomorrow routes become one-line cards with departure time, direction and feels-like/rain, while retaining a separate 48 dp play target. Statistics combines month navigation and filters: the month lives in the header, and route selection sits beside the work/home switch. The overview keeps valid distance, commute days and trip count for both directions, leaving space for direction-specific time composition, the chart and the rhythm. Trips shows month and all-time summaries; rows use two lines for date, route/direction and duration/distance. Routes shows route and linked-trip counts; route details merge the reference trace, endpoints and travel plan into one card.

Trip and route details replace the standalone “Back” text button with a header icon and add a persistent “More actions” entry; long-press management remains. Bottom navigation uses custom line icons consistent with the brand. The motorcycle progress bar animates over roughly 650 ms. With enlarged text, statistics filters, live metrics and trip rows switch to vertical layouts to avoid horizontal squeeze and clipped numbers.

Returning from a trip or route detail preserves the list’s previous scroll position; a newly opened detail still starts at the top. Each top-level page keeps its own scroll state. The four tabs use a horizontal pager and follow left/right swipes, with page motion tracking the gesture. Pages are precomposed; the bottom navigation and Back handler read the page index in isolated scopes so a swipe does not recompose the whole screen. The list stays composed while a detail is open; the detail is an independent opaque overlay that slides in and out over roughly 300 ms. On return the list does not move, and the detail translates out as one page, avoiding a two-page transition. Tab swipes are paused while a detail is open to avoid conflicting with the detail hierarchy. Detail content is composed on demand, statistics and route sample counts move to background work, and historical comparison is calculated only near its card, reducing main-thread work when a detail opens.

Settings appears only on the idle Record, Trips list and Routes list; it is hidden while recording, on Statistics and on every detail page so focused recording or reading is not interrupted by a secondary action. The gear icon now has solid tooth blocks with clear root gaps instead of radial single strokes.

Verification status: Debug and AndroidTest APKs compile, JVM unit tests and Lint pass. The four pages, details and action sheet were visually checked on a PKJ110, and slowed animations confirmed transition direction and clean edges. The `tripListScrollPositionSurvivesDetailReturn`, `statisticsFiltersAndSelectableChartKeepTheSameDataScope`, `fourTabsAndSystemBackReturnToJournalAfterRecreation`, `longPressShowsMenuWithoutOpeningDetailsAndBackDismissesIt` and `settingsOnlyOnTopLevelPages` device tests pass. The full `UiSmokeTest` was not treated as passing: its capture-heavy `englishLargeTextStatisticsAndActionSheetRemainUsable` case did not finish within five minutes. TalkBack, landscape and the full large-text flow still need separate acceptance.
