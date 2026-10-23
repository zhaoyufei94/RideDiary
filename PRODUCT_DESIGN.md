# RideDiary / 途记 Product Design

Version: v0.1.5 product baseline · Updated: 2026-10-23 · [中文](PRODUCT_DESIGN.zh-CN.md)

This document guides product decisions, UI design and future development. **“Current” describes existing behavior; “requirements” constrain future changes and do not imply completed implementation or acceptance; “planned” means not delivered.** The product targets Android phones across brands, cities, motorcycle models and commute routes.

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
| Experience | Four native screens, Chinese/English switching, system Back to Journal | Large-text and screen-reader improvements, large screens, dark theme |
| Data management | Private local database, retained raw fixes, system backups disabled | User-controlled local export, backup, import and migrations |

No map basemap, navigation, live traffic, red-light detection, account or cloud sync is currently provided. Vehicle displays, CAN/OBD, third-party navigation scraping, Accessibility/MediaProjection capture and racing features are outside current scope. Prototype decoration or sample numbers do not establish a shipped feature.

## 3. User journeys

### First experience

Allow browsing and empty states before authorization. Ride offers **Start recording**. The in-app replay entry and demo runtime have been removed at user request. Synthetic tracks are only automation fixtures and are excluded from the application APK.

### First real ride

While parked, tap Start → explain location and notifications → request permissions as needed → start recording and its system notification. Without a route, show an ordinary ride, elapsed/stationary time and location quality, without invented progress or ETA. Timing continues without fixes; missing intervals are not stationary. Finish manually and open details.

Choose **Create commute route**, name it, select To work / To home and confirm endpoint areas. Current minimums are four valid fixes and 150 m; endpoint radii are 20–500 m, defaulting to 100 m at the start and 70 m at the end. Work/home are independent directions; multiple routes per direction are allowed.

### Subsequent commutes

Manual start → recognition from start area and ordered observations → route name and motorcycle progress → historical comparison/ETA when enough samples exist → confirmed arrival after sustained low speed near the endpoint.

Recognition needs some travel; departure clock time is not a hard filter. Ambiguous candidates stay uncertain. Deviation, turnback or stale fixes suppress unreliable progress and ETA. Current arrival needs at least 92% ordered progress, endpoint proximity and at least three fresh low-speed fixes spanning 30 seconds without long gaps. Progress stays at or below 99% until confirmation, then becomes 100%. Passing the endpoint briefly must not finish a ride; manual finish always remains available.

### Review

Show actual elapsed, moving/stationary/unknown time, historical differences and real sample counts. Explain the learning state when history is insufficient. Allow route/direction correction, exclusion of unusual stops/detours and commute-boundary adjustment in seconds relative to the original start. Recalculate details/statistics and retain raw data.

Mark interrupted rides separately, retain saved points and never automatically resume them or use them as normal completed baselines. A recognized ride manually finished while deviating or substantially incomplete is currently auto-excluded, with later review/correction available.

## 4. Information architecture

| Screen | Hierarchy and actions | Empty/degraded states |
|---|---|---|
| Ride | Primary: recording state, elapsed, start/finish; secondary: location quality, route/motorcycle progress; supporting: breakdown, history, ETA | Clear start entry; ordinary ride without a route; no placeholders pretending to be history |
| Journal | History → details/arrival → create route, classify, exclude, adjust boundaries | Explain recording; visibly mark interrupted and excluded rides |
| Statistics | Month/route/direction filters → totals → per-ride stacked chart → clock/duration statistics | Explain no valid samples; label one sample; zero stationary time is valid |
| Routes | Name, direction, length, sample count, local track sketch and history entry | Explain creation from history; do not present sketches as navigation maps |

Requirements: give each context a clear primary action. Errors explain their impact and a useful next step. Separate durations from departure/arrival clock times and label distance, speed and percentage units. Use text or icons alongside color for important states.

Journal list cards and detail summary cards expose correction/deletion menus on long press, followed by a separate deletion confirmation, cascading to raw points and segments while retaining saved route geometry. Detail Back controls and Android Back return to the Journal list. Route cards expose editing names, direction and endpoint radii, and deletion through a long-press context menu. Direction changes propagate to linked rides. Route deletion unlinks rides while retaining their raw tracks and direction. A route in use by an active recording cannot be edited or deleted.

## 5. Recording states and failures

Lifecycle: idle → permission request → recording → completed, with interruption as an alternative outcome. Location quality, moving/stationary/unknown classification and route matching are independent states. “Matching” does not mean recording has stopped; “stationary” does not mean arrived.

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
- Apply month, direction and route filters consistently to charts and tables. Recalculate from raw data after corrections, rather than editing display totals.
- Use monotonic time for uninterrupted elapsed duration, UTC/timezone for clock labels and grouping. Mark interruption gaps uncertain; never join monotonic origins from separate processes.

Exact quality, hysteresis, jump and projection thresholds live in [technical design](docs/design.en.md) and `core/Models.kt`. Metric changes require matching copy, algorithm-version and meaningful test updates.

## 8. Visual style

The approved brand logo is **cream graph paper, one continuous forest-green trace, and lime/sand endpoints**, in a flat style without a notebook outline, folded corner, dimensional shadows or added motorcycle. Use it for the launcher, app header and public branding. Keep the in-app motorcycle progress indicator as a functional element. The editable master is [ridediary-logo.svg](docs/brand/ridediary-logo.svg); see [brand assets](docs/brand/README.md) for resources and Android adaptation rules.

Keywords: **quiet, natural, clear, journal-like**. Preserve the [prototype](docs/prototype/ui-prototype.html) hierarchy and character while adapting native components to real windows. Use whitespace, light surfaces, thin separators, 12–16 dp corners and prominent monospace elapsed numbers. Dense speed dashboards and neon styling do not fit the current identity.

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

Currently no Internet permission, uploads, accounts or telemetry. Tracks use app-private storage; system cloud/device-transfer backups are disabled, with no precise coordinate logging. Android storage protection applies; no extra application encryption or universal manufacturer-transfer verification is claimed.

Explain that uninstalling/clearing storage loses records, with no current export/cloud recovery. Future export/import/backup must be user-initiated and define formats, privacy and retention. Classification/exclusion retains raw fixes. Future deletion must explain consequences and require confirmation. Schema changes need migrations and retention tests rather than destructive rebuilding.

## 11. Acceptance and priorities

The [test record](docs/testing.en.md) documents 28 JVM tests and four device tests passed. One device does not establish all-device or real-road reliability. Brand names identify actual test environments, not product scope.

| Priority | Acceptance focus |
|---|---|
| P0: reliable records | Denied/approximate/revoked permissions, notification denial, no fixes, duplicate starts, recreation, storage failure, interruption; no duplicate ride or loss of saved raw points |
| P0: real roads | Repeated work/home rides, parallel roads, brief endpoint stop versus arrival, garages/tunnels, 30–60 minutes screen-off with normal battery settings; record error, unknown share, drain and heat |
| P1: understanding and interaction | Onboarding, learning/deviation/interruption copy, finish, corrections, linked filters, both languages, large fonts, TalkBack, Back and navigation bars |
| P1: analysis and management | Scatter plots, hotspots, departure windows, route/endpoint editing, local export/backup/import |
| P2: expanded experience | Large screens, dark theme, long-term performance; any future map basemap must preserve offline/key-independent core use |

Field results record Android/system version, model, app/algorithm version, route conditions and battery policy. Distinguish passed, needs work and untested; promise no unmeasured accuracy/endurance. Formal distribution, release signing and licensing remain undecided for release preparation.

## 12. Starting work in another conversation

Read this specification first, then [README](README.md) for usage/building, [technical design](docs/design.en.md) for implementation and [testing](docs/testing.en.md) for evidence and gaps.

| Location | Responsibility |
|---|---|
| `app/src/main/java/com/ridediary/core/` | Quality, segmentation, route projection and statistics |
| `app/src/main/java/com/ridediary/data/` | Models, Room, transactions, correction and recovery |
| `app/src/main/java/com/ridediary/recording/` | System location, foreground service, recording lifecycle |
| `app/src/main/java/com/ridediary/ui/` | Screens, components, charts, theme, dialogs and ViewModel |
| `app/src/main/res/values/strings.xml`, `values-en/strings.xml` | Chinese/English UI copy |
| `docs/prototype/ui-prototype.html` | Visual/demo reference, not Android functionality |
| `app/src/test/`, `app/src/androidTest/` | Algorithm, persistence, UI and service tests |

Original handoff requirements remain in `docs/requirements.*.md`; personal phone, city and early setup details are historical context. This document defines current product scope and design direction. New explicit user instructions take precedence. If code and specification differ, identify and resolve the gap rather than presenting plans as delivered.

Update both languages, feature status and affected validation with each iteration. Preserve metric and privacy contracts. Keep README focused on users and contributors; debugging narratives and device evidence belong in test records.

## 0.1.3 statistics, details and action sheets

Long-press management uses Material 3 bottom sheets showing the selected ride/route, its summary and explained actions with local line icons. Deletion uses a restrained brick-red treatment and still requires confirmation. Edit sheets provide scrolling content with a persistent save/cancel footer.

Statistics and details use light cards with 24 dp corners, forest-green hero cards with 26 dp corners and sheets with 28 dp top corners. Distance, commute days/counts, time composition, per-ride duration and commute rhythm form distinct information levels. Details lead with actual elapsed time, distance and speed, followed by intervals, stops, the track and historical baselines; insufficient history never produces invented differences. Enlarged text uses vertical layouts and selection includes accessibility semantics.

## Keep screen awake during recording (0.1.4)

Keep the visible app awake automatically after the recording service starts, consistently across all four tabs. Restore normal timeout on finish, confirmed arrival, service stop or backgrounding. Foreground return, rotation and language recreation restore the current recording behavior. The Ride screen explains that the power button can still lock the phone. No setting toggle or permission prompt is added. Continuous screen use increases power consumption.
