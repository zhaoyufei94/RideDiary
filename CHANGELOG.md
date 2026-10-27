# Changelog

[中文](CHANGELOG.zh-CN.md)

## 0.3.4

- Added: Merge multiple recordings from the same trip, with gaps, endpoint distances and checks for overlapping times, distant or missing locations and conflicting directions.
- Added: Keep original recordings and restore them with Undo merge in trip actions.
- Improved: Unrecorded gaps remain unknown time; track and distance use recorded parts without inventing missing sections.

## 0.3.3

- Added: route recommendations show current traffic, driving reference times and update times from two hours before until one hour after departure, refreshed with weather using the existing Baidu Maps AK.
- Improved: Settings, track maps and recommendation cards share one Baidu Maps and traffic entry. Existing AKs are retained, with separate switches for basemaps and recommendation traffic and no additional server key.
- Improved: removed wind from recommended routes to make room for traffic below the weather preview.
- Improved: traffic details start collapsed, showing current conditions and driving reference time; expand to see update time, congestion changes and route notes.
- Improved: traffic is shown as a planned-route reference, allowing detours and moderate endpoint offsets while displaying route similarity, distance difference and endpoint offsets.
- Fixed: missing usable route data was incorrectly reported as a route mismatch.

## 0.3.2

- Fixed: linked trips in route details now follow the To work / To home selection and show only that direction.
- Added: view and copy the data path in Settings, and browse the trip database and its companion files with read-only access in the system file manager.
- Improved: route recommendations remain available until one hour after planned departure, including late starts across midnight.

## 0.3.1

- Added: A Monday-first calendar above the trip list. Swipe or use the arrows to change months; the summary and list follow the selected month. Empty months also show the calendar.
- Added: Daily distance circles showing total kilometers to one decimal place. Longer distances use darker green within the month; days without trips have no circle.
- Added: Calendar dates follow each trip's adjusted departure and recorded time zone; overnight trips belong to their departure day. The calendar includes ordinary, excluded, interrupted and recording trips, while Statistics still summarizes valid commutes only.
- Added: Chinese/English calendar text, accessible date and distance descriptions, and large-text support. Returning to the list preserves the selected month. Swipe within the calendar to change months, or on the header and list to change pages.
- Fixed: The recording card showing an earlier route after deviation, insufficient overlap, an ambiguous match or stale location. It now shows Unknown route and hides route progress and arrival estimates in these cases.
- Fixed: Trips without a valid linked route showing a work, home or other direction. The trip list, recent trips and details still show departure time, duration and status.
- Improved: Route recommendations explicitly label feels-like temperatures and use °C at both ends of the range.
- Fixed: Duplicate degree symbols in temperatures.
- Improved: A more compact trip calendar that fits the actual four to six weeks while retaining daily distance and accessibility descriptions.

## 0.3.0

- Added: Full-screen trip and route tracks with pinch zoom, pan and fit-to-track controls. Record shows live tracks and the current position; expanded maps can resume following your location.
- Added: A fixed red-to-green 0–60+ km/h trip speed scale, with gray for unknown speed. Reference routes remain monochrome.
- Added: Labels for stops of at least 30 seconds on expanded maps. Bubbles avoid tracks, other markers and screen edges; when space is unavailable, only the stop dot remains. Previews hide stops.
- Added: Optional Baidu basemaps using your own Android SDK AK, with copyable package and SHA1 details. Keys are encrypted on your phone. See the [setup guide](docs/baidu-map-setup.en.md).
- Added: Saved Display choices for basemap styles, speed colors, stops, endpoints, place names and live traffic. Custom styles use an ID you publish; live traffic does not change historical speeds or arrival estimates.
- Improved: Track colors and widths in previews and expanded maps for easier viewing when zoomed out.
- Fixed: Missing track lines on Baidu basemaps.
- Added: Baidu maps are off by default. Without a key, offline or when maps fail to load, local expanded tracks, speed colors, stop labels, recording, weather and statistics remain available.
- Improved: Invalid positions, location jumps and long gaps have no connecting lines, avoiding misleading tracks.

## 0.2.3

- Improved: Headers show dates, monthly trip counts and distance, or linked-trip counts instead of repeated branding and large page titles.
- Improved: Smaller card padding and section spacing, with compact two-line trip and route rows showing more content at once.
- Improved: A more prominent start action on Record, expandable weather details for wind, rain and update time, and compact Today/Tomorrow routes showing departure times and forecasts.
- Improved: Month, route and direction filters are grouped in Statistics to leave more room for charts; the overview retains distance, commute days and trip count.
- Added: A persistent More actions entry in trip and route details alongside long-press management.
- Improved: Header Back icons, consistent bottom navigation icons and smoother route progress animations.
- Improved: Vertical statistics filters, live metrics and trip rows with large text to avoid crowding and clipping.
- Fixed: Lost list scroll position after returning from trip or route details.
- Added: Left/right swipes between the four main pages, with animations following the gesture.
- Improved: Smoother opening of details and viewing of statistics and route information.
- Improved: Settings appears only on idle Record, the Trips list and the Routes list, with a clearer gear icon.
- Fixed: Flickering content while leaving details.

## 0.2.2

- Improved: Route recognition recovers more easily after loops near the start or brief deviations, avoiding stuck progress. Similar routes or poor location may still prevent recognition.
- Added: Up to two candidate routes and their overlap with the current track below the recording card.
- Improved: Fewer repeated route-matching messages, with concise location status and green/red dots beside Recording.
- Improved: Route matching normally updates about every 5 seconds, with up to 15 seconds between updates when slower; routes are checked again before saving.
- Fixed: Delayed saving and inflated duration when finishing long trips.
- Improved: The finish time stays fixed after recording ends, including when retrying a failed save.
- Fixed: The location indicator still showing Searching after location recovers.
- Improved: Smoother operation during recording.
- Added: Saving status and retry prompts.

## 0.2.1

- Fixed: Startup crashes after upgrading certain earlier builds while retaining trips, tracks and route plans.

## 0.2.0

- Added: Workdays, custom weekdays and departure times for each route direction.
- Improved: Today/Tomorrow replace recent routes and show only upcoming scheduled departures.
- Improved: Pull to refresh on Record replaces the separate weather refresh button.
- Improved: Weather sources appear once in the footer, with results shown as they become available.
- Added: Bundled 2026 holidays and make-up workdays for offline schedule checks.
- Improved: Current weather shows next-hour precipitation chance and route forecasts use feels-like temperatures.
- Improved: Only current weather shows an update time, with brief messages when refresh fails.
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
- Added: Settings with version, logo, brand slogan and language options.

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
