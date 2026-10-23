# Changelog

[中文](CHANGELOG.zh-CN.md)

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
