# Changelog

[中文](CHANGELOG.zh-CN.md)

## 0.3.1 · Trip calendar and live routes (2026-10-25)

- Added a Monday-first month calendar above the trip list. Swipe or use the arrows to change months; the summary and list follow the displayed month. Empty months retain the calendar and show an empty state.
- Days with trips show a circle containing the sum of their kilometers to one decimal place. Longer daily totals use darker green within the displayed month; days without trips have no circle.
- Dates follow each trip's adjusted departure and recorded time zone, with overnight trips assigned to their departure date. Calendar totals and the list include ordinary, excluded, interrupted and recording trips. Statistics retains its existing valid-commute scope.
- Swipes inside the calendar change months; the header and list retain top-level tab swipes. The selected month survives details, Back and activity recreation. Includes Chinese/English text, accessible daily descriptions and large-font sizing.

- Validation: Debug/Release and instrumentation APK builds, all 74 JVM tests, Lint (0 errors / 35 warnings) and 291 bilingual resource checks pass. The 0.3.1 Release APK retains the existing certificate; four targeted device cases pass and the Release build is installed in place. All rows in the four rides, 5476 points, 87 segments and two routes match before/after tests. Full device regression and visual acceptance were not performed. See the [test record](docs/testing.en.md).

- Fixed the recording card retaining an earlier route name after the live match failed. It now shows Unknown route when deviating, below the overlap threshold, ambiguous or without fresh location; route progress and ETA require a current confirmed match. Candidate overlap, final classification and historical data keep their existing behavior.

- Fixed unlinked trips showing a commute direction. The journal, recent-trip row and detail header now omit direction when no valid route is associated, while retaining time, duration and status. Linked trips keep their direction; historical fields are unchanged.

- Improved route recommendations with an explicit Feels like label and one °C unit per temperature, fixing duplicated degree symbols. The journal calendar uses smaller cells, dots and spacing, fits the actual four to six weeks and removes the explanatory footer while retaining daily kilometers and accessibility descriptions.

## 0.3.0 · Expanded tracks and Baidu maps (2026-10-24)

- Trip and reference-route previews open full screen with pinch zoom, pan and fit-to-track controls. Recording shows live tracks and fresh positions; expanded maps can resume following.
- Trip speeds use a fixed red-to-green 0–60+ km/h scale, with gray for unknown speed. Reference routes remain monochrome and do not borrow trip speed or stop data.
- Expanded maps label stops of at least 30 seconds with small rounded bubbles and leaders to their true locations. Bubbles avoid tracks, other markers and viewport edges; unavailable bubbles retain their dots. Previews hide stop markers.
- Optional Baidu basemaps accept each user's Android SDK AK in the APK, with copyable package/SHA1 details and Android Keystore encryption. See the [setup guide](docs/baidu-map-setup.en.md).
- The expanded Display panel persists minimal, standard, satellite and custom styles, plus speed colors, expanded-map stops, endpoints, place names and live traffic. Tea Field and other custom styles use an ID published in your account. Live traffic shows current road conditions and does not change historical speeds or arrival estimates.
- Improved track palette and widths in previews/expanded maps. Local casings draw in a separate pass; native maps use per-segment colored polylines, improving overview readability and fixing missing native track lines.
- Baidu maps default off. Missing keys, offline access or map failures retain local expanded tracks, speed colors and stop labels. Recording, weather and statistics do not depend on the map SDK.
- Original fixes and historical analysis remain unchanged; coordinate conversion is display-only. Invalid fixes, jumps and long gaps never gain connecting lines. Only map/coordinate tools and required dependencies are included, with compressed native libraries and arm64-v8a by default; `rideDiaryAbis` selects other architectures.
- Validation: Debug/Release builds, all 67 local JVM tests, Lint and 287 bilingual resources pass. In-place installation retains the existing certificate. No device/emulator tests or visual acceptance were performed, as requested; see the [test record](docs/testing.en.md).

## 0.2.3 · Interface and interaction refinement (2026-10-24)

- Removed the fixed brand bar on every page and the large page titles that repeated the bottom tabs (“Record / Trips / Statistics / Routes”). The header now carries useful context such as the date, this month’s trip count and distance, or linked route trips.
- Reduced card radii, inner padding and section spacing. Trips are now two-line, higher-density rows; routes show name, direction samples and linked-trip count in two lines.
- Record now leads with the start action. Weather collapses into a one-line summary with expandable wind, rain and update details; Today/Tomorrow routes use compact rows with departure time and forecast.
- Statistics combines month navigation, route selection and direction filtering into a compact header and one filter row. The overview keeps distance, commute days and trip count while leaving more first-screen room for charts.
- Trip and route details gain a persistent “More actions” entry while retaining long-press management. Back navigation moves into the header instead of a standalone text button.
- Bottom navigation uses custom line icons consistent with the brand; route progress animates smoothly.
- Large text switches statistics filters, live metrics and trip rows to vertical layouts to avoid clipping.
- Fixed: returning from a trip or route detail preserves the list scroll position instead of jumping to the top; each top-level page keeps its own scroll state, while a newly opened detail still starts at the top.
- Improved: the four top-level pages now use a horizontal pager and follow left/right swipes; pages are precomposed, while the bottom navigation and Back handler read the page index in isolated scopes to avoid whole-screen recomposition during a swipe.
- Improved: ride details use a LazyColumn, statistics and route sample counts move to background work, and historical comparison is computed only near its card, reducing main-thread work when a detail opens.
- Improved: the Settings entry appears only on the idle Record, Trips list and Routes list; it is hidden while recording, on Statistics and on every detail page. The gear icon now uses solid gear teeth instead of radial single lines.
- Fixed: the list now stays composed while a detail slides out as an independent overlay, so the list no longer takes part in the return transition. The current trip sample and callbacks also stay stable, removing the content shimmer during the detail exit.
- Build: local performance Release packages are named `RideDiary-release-v<version>.apk` and produced by `scripts/package-release.sh`.
- Verification: Debug APK, AndroidTest APK, JVM unit tests and Lint pass. Four pages, details and the action sheet were visually checked on a PKJ110. Six targeted device tests passed before the final detail-overlay change: `horizontalSwipeChangesTabsAtTopLevel`, `tripListScrollPositionSurvivesDetailReturn`, `statisticsFiltersAndSelectableChartKeepTheSameDataScope`, `fourTabsAndSystemBackReturnToJournalAfterRecreation`, `longPressShowsMenuWithoutOpeningDetailsAndBackDismissesIt` and `settingsOnlyOnTopLevelPages`. The final overlay was accepted by the user on-device; the device then locked, so the automated cases were not rerun on the final package. A release APK signed with the existing debug certificate measured 404 frames across six tab swipes: 90th 8 ms, 95th 9 ms, 99th 14 ms, 0.99% janky; detail entry/back measured 90th 12 ms and 99th 46 ms. Debug builds are noticeably slower, so the performance figures refer to release. The detail-exit shimmer fix is installed; per the user’s request no further animation review was performed and device acceptance is left to the user. The full `UiSmokeTest` was not treated as passing: its capture-heavy `englishLargeTextStatisticsAndActionSheetRemainUsable` case did not finish within five minutes.

## 0.2.2

- Recover from reference-start loops and short deviations using bounded, consecutive-fix reacquisition, while keeping parallel-route ambiguity and gap protections.
- Show up to two live candidate routes and collected-fix overlap in a compact section below the main recording card.
- Remove repeated route-matching labels and place location status beside the recording title, with green/red indicators and concise text.
- Match about every 5 seconds, adapting up to 15 seconds for expensive calculations. Raw-fix persistence stays independent; finishing always forces a final review.
- Fix delayed stop/save and inflated duration caused by queued full-trace updates on long rides.
- Coalesce update wakeups while retaining every received raw fix in batches. Stop acquisition immediately and keep the requested finish time through retries.
- Decouple live location status from database/rendering lag so a recovered fix clears the searching indicator.
- Cache route projection indexes and search nearby segments; run live analysis off the UI thread without repeating it on clock ticks.
- Add saving/retry states. Existing rides, routes, durations and raw points are not retrospectively modified.

## 0.2.1

- Fixed: Startup crashes after upgrading certain earlier builds while retaining trips, tracks and route plans.

## 0.2.0

- Added: Workdays, custom weekdays and departure times for each route direction.
- Improved: Today/Tomorrow replace recent routes and show only upcoming scheduled departures.
- Improved: Pull to refresh on Record replaces the separate weather refresh button.
- Improved: Weather attribution appears once in the footer, with deduplicated queries and progressive results.
- Added: Bundled 2026 holidays and make-up workdays for offline schedule checks.
- Improved: Current weather shows next-hour precipitation chance and route forecasts use feels-like temperatures.
- Improved: Only current weather shows an update time, with refresh failures reported through a Toast.
- Improved: Longer forecasts cover tomorrow’s late departures.

## 0.1.8

- Added: Current weather and today’s overview on Record.
- Added: Switch or disable Open-Meteo and QWeather with personal credentials in Settings.
- Added: Route recommendations based on recent habits and time of day, with departure-window weather.
- Added: Start recording with a selected route and direction using its play icon.
- Improved: Cached weather and clear failure states preserve offline recording.

## 0.1.7

- Improved: Consistent trip/route terminology with fewer redundant instructions and decorative captions.
- Improved: Linked trips appear inside route details instead of a recent-trip list on the Routes page.
- Added: Open linked trips directly from route details.
- Added: Unlink a trip from its route while retaining the trip and raw track.
- Added: Settings with version, logo, approved slogan and language options.

## 0.1.6

- Added: Cancel, Delete and Save at every finish entry, with recording continuing until confirmation.
- Added: Automatic route matching after saving, with a list for correcting classification or creating a route.
- Added: Actual return tracks can automatically fill a route's missing direction.
- Fixed: Same-name work/home references appearing as separate routes.
- Improved: Route-first statistics with a combined overview above separate work/home charts.
- Improved: Time breakdown and stop details now share one ride-detail card.

## 0.1.5

- Added: A graph-paper app icon with a trace connecting two endpoints.
- Improved: Consistent RideDiary / 途记 naming and branding across the app, notifications and presentation pages.

## 0.1.4

- Added: The visible app stays awake during recording and restores normal timeout when you leave or finish.

## 0.1.3

- Added: Selectable per-ride duration bars and departure/arrival rhythm statistics.
- Improved: Consistent overview, time-composition and historical-comparison cards in Statistics and ride details.
- Improved: Explained management actions and edit forms in bottom sheets.
- Fixed: Forms displaying a different language from the current screen.

## 0.1.2

- Added: Management menus when holding ride, detail or route cards.
- Improved: Management menus dismiss with Back or an outside tap.

## 0.1.1

- Added: Ride deletion removes its raw track while retaining routes created from it.
- Added: Edit route names, directions and endpoint areas, or delete routes while retaining ride history.
- Fixed: Returning from ride details did not lead back to the Trips list.
- Improved: Removed the replay entry to simplify everyday recording.

## 0.1.0

- Added: Offline ride recording with elapsed time, distance, average speed and location status.
- Added: Moving, stationary and unknown time analysis.
- Added: Route recognition, motorcycle progress, historical comparison and arrival estimates.
- Added: Ride history, monthly statistics and route-classification correction.
- Added: In-app Chinese/English switching.
