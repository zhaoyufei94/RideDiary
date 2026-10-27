# RideDiary / 途记

<img src="docs/brand/ridediary-logo.svg" width="96" height="96" alt="RideDiary logo" />

**Your everyday rides, a little better understood.** · [中文说明](README.zh-CN.md) · [Changelog](CHANGELOG.md)

Current version: **0.3.4**

RideDiary is an offline-first trip journal that helps you understand your commute and compare it with earlier trips on the same route. For Android 8.0 and later; no account, extra hardware or Google Play services needed.

## Features

- **Trip calendar:** swipe between months and browse all trips from the displayed month. Days with trips show a circle containing their total kilometers; longer daily distances have darker circles within that month.
- **Record trips:** see elapsed time, distance, average speed, stationary time and location status; view live candidate routes and overlap, with motorcycle progress after recognition.
- **Check weather:** current conditions, next-hour precipitation chance, today’s overview and departure-window feels-like temperatures.
- **Traffic before departure:** view current route traffic from two hours before until one hour after departure, refreshed with weather using the existing Baidu Maps AK. No additional key is needed. Times are driving references; future forecasts are not provided.
- **Travel plans:** set workdays or custom weekdays and departure times separately for each direction; Today/Tomorrow show plans until one hour after departure, with a play icon to start on the selected route and direction.
- **Manage routes:** keep work/home tracks under one route name, with automatic matching and manual correction.
- **Explore statistics:** view monthly distance, commute days and trip count across both directions, then select a direction for durations and charts.
- **Merge split recordings:** select recordings from the same trip, check their time and location joins, then merge them. Originals are kept for undo.
- **Review history:** inspect time breakdowns, stops, tracks and historical comparisons; correct classification, adjust boundaries, exclude or delete trips.
- **Expand tracks:** tap a trip or route preview to pinch, pan and fit the full track. Trip speed runs from red (slow) to green (fast), with gray for unknown speed and labels for stops of at least 30 seconds.
- **Optional Baidu basemap:** enter your own Android SDK AK in the APK. Recording can show your current position and live track; recording, statistics and local tracks work without a key, with maps disabled or offline.
- **Map display choices:** expanded maps offer minimal, standard, satellite or custom styles, with speed colors, stops, endpoints, place names and live traffic. Stop bubbles avoid the track while previews stay uncluttered.

## Getting started

1. Open the APK and follow your phone's installation prompts; install updates over the existing app to retain your records.
2. While parked, tap **Start recording**, grant precise location and enable system location.
3. To finish, choose **Cancel** to continue, **Delete** to discard this trip, or **Save** to keep it; notification finish and detected arrival offer the same choices, and recording continues until you decide.
4. After saving, review the matched route, select an existing route or use **Create a new route** at the top; a missing work/home direction can use the current track.
5. In Statistics, choose a month and route, then To work / To home; open a trip in Trips for details.

Chinese is the default; change language and weather service in Settings at the top right. Open-Meteo is the default; QWeather requires your API Host and API Key from the [QWeather console](https://id.qweather.com/), followed by Save weather settings. Weather can be turned off. Operate the app while parked.

Baidu maps are disabled by default. In Settings → Baidu Maps, copy this APK’s package and SHA1, create an **Android SDK** AK in your own [Baidu console](https://lbsyun.baidu.com/apiconsole/key), bind those values, then enter the AK, enable and save. Map settings are also available at the top right of the expanded track. The default is minimal. To use Tea Field or another style, publish it in your account's [style editor](https://lbsyun.baidu.com/index.php?title=open/custom), enter and save its ID in map settings, then choose Custom under Display in the expanded map. Style failures do not affect tracks. APKs signed with different certificates require matching SHA1 entries.

See the [Baidu Maps setup guide](docs/baidu-map-setup.en.md) for steps and copyable signing details.

Weekday plans from earlier versions remain as Custom weekdays. To follow holidays and make-up workdays, switch the route's travel plan to Workdays.

## Permissions and conditions

| Permission or condition | Purpose and effect |
|---|---|
| Precise location | Records tracks and recognizes routes; denied or approximate-only access prevents recording, but history remains available |
| Approximate location, weather only | Shows local weather; granting weather location never starts recording, and existing route-origin weather works without it |
| Internet and network state | Fetch weather and the optional Baidu basemap; local tracks, recording and history remain available offline |
| Notifications, Android 13+ | Shows recording status and a finish entry; if denied, return to the app to finish |
| System location enabled | Supplies fixes; when disabled or unavailable, timing continues and unclassifiable intervals are unknown |

No “Allow all the time” location access is required; trips never start automatically or at boot.

## Using RideDiary

- **Travel plans:** open a route, choose To work or To home and edit its plan. Workdays is the default, using the bundled China calendar to exclude holidays and include make-up workdays. Custom weekdays are also available; clear all to exclude that direction. The 2026 calendar is included; other years arrive with app updates, and the calendar works offline. If a year is unavailable, use Custom weekdays instead of Workdays. Set a time or use usual departure history, with 08:00 to work / 18:00 to home when no history exists.
- **Today/Tomorrow:** show scheduled routes until one hour after planned departure, ordered by time. Recommendations remain at exactly one hour late and disappear afterward. Late-night plans remain in Today after midnight until that hour ends, showing their original planned time. Recording never starts automatically. Plans follow the phone’s current timezone.
- **Departure weather:** route cards combine endpoint feels-like temperature and rain probability from planned departure through median historical duration, with one hour when duration is unknown. Wind is no longer shown on route cards.
- **Route traffic:** enable Live traffic on recommended routes in Settings → Baidu Maps and traffic; see [setup](docs/traffic-setup.en.md). Settings, track maps and recommendation cards open the same configuration. The saved Android SDK AK is retained, without a separate server key; basemaps and recommendation traffic have independent switches. From two hours before until one hour after departure, traffic follows weather refresh while Record is visible and idle. Traffic is a planned-route reference allowing detours and moderate offsets, with similarity, distance difference and endpoint offsets shown. Opposite direction, distant endpoints or missing geometry withhold the reference; insufficient or expired data never appears clear. Driving references are not motorcycle times and do not change historical arrival estimates during recording. Future forecasts and optimal departure windows are not provided.
- **Weather refresh:** automatic checks run while Record is visible; pull down to refresh current weather and both days’ route forecasts together. Weather data is normally retained for 20 minutes; refresh failures retain recent usable data and show a brief message. Only current weather displays an update time. Sources appear once at the bottom of Record. Requests can take several seconds and do not affect offline recording. Background weather reminders are not available yet.
- **Routes and history:** creating a route needs four valid fixes and 150 m of track; endpoint radii are 20–500 m, and historical comparison/arrival estimates need three usable histories for the same direction.
- **Arrival prompts:** near-complete progress and at least 30 seconds of sustained low speed may trigger review, but Save/Delete still requires your choice; similar routes, deviation or poor fixes can prevent recognition and estimates.
- **Time breakdown:** elapsed time equals moving, stationary and unknown time; estimates do not include live traffic.
- **Maps and stops:** Display at the top right of expanded maps changes styles and contents, with choices saved locally. The stop toggle applies to expanded maps; previews hide stop dots and bubbles. Bubbles hide when no free space fits; zoom in to reveal them. Live traffic shows current road conditions, not historical trip speeds.
- **Screen and battery:** the visible app stays awake during recording and restores normal timeout when you leave or finish; the power button can lock the screen while background recording continues, and location/screen use increases battery consumption.
- **Merge trips:** use the merge icon at the top of Trips, long-press a trip, or open its actions. Select 2–10 recordings across months; they are sorted by time with gaps and endpoint distances shown. Adjacent recordings must not overlap and must be within 30 minutes and 300 m. Missing locations, conflicting routes/directions and omitted intervening trips show a reason. Finish active recording first; total duration is limited to 12 hours. Adjusted boundaries are respected; interrupted recordings end at the last saved point. Unrecorded gaps stay unknown without invented track or distance. Manual exclusions remain, and excessive unknown time still prevents valid-commute inclusion. Only the merged result is listed; undo restores the state before that merge. Further merges can include up to 10 originals. Deleting a merged trip also deletes its retained originals; saved routes remain.
- **Edits and deletion:** hold a trip or route card for management. Linked trips inside a route follow the To work / To home selection; open details or hold to unlink. Deleting a route keeps trips, while deleting a trip removes its raw track.
- **Data location:** Settings shows the actual trip database path. Touch and hold to copy it, or choose Open in file manager to browse the database and its companion files with read-only access. A file manager supporting system document access is needed; a system file picker is tried as a fallback. This entry does not edit, delete or restore data.
- **Interruptions:** time to the first fix depends on surroundings; if recording is interrupted, check location and app background restrictions, then reopen to inspect saved data without automatic resumption.

## Data and limits

Enabling recommendation traffic queries Baidu Maps with route endpoints and waypoints; track verification stays on your device and the full raw trip is not uploaded. These locations are more precise than weather queries. Traffic is off by default and reuses the encrypted map AK, subject to your Baidu account access and quota.

Trips and full tracks stay on your phone without uploading to a track cloud; speed and stop analysis also stays on your phone. Weather queries send current or route-endpoint coordinates rounded to two decimal places to the selected provider. QWeather and Baidu keys are encrypted on your phone and excluded from backups. When Baidu maps are enabled, a key is configured and you view a map, the service processes location, viewed map areas, IP and device information under its [privacy policy](https://lbsyun.baidu.com/index.php?title=openprivacy). Online maps therefore require network access. Viewing maps does not change historical tracks. **Uninstalling or clearing app data loses records; in-app export, import and cloud recovery are not available yet.**

[Open-Meteo’s free API](https://open-meteo.com/en/pricing) is for non-commercial use; QWeather access and quotas depend on your account. Weather is a grid forecast; endpoint previews do not cover every location along the route or describe road-surface conditions.

Expanded tracks, speed colors and stop labels work offline. Baidu basemaps and live traffic require your own valid AK, network access and account entitlement. Stale locations hide the live marker; gaps and jumps never gain invented connecting lines, and unknown speed is not zero. Reference routes do not borrow trip-specific speed or stops. Navigation and signal-light detection are not available. Location accuracy and background continuity depend on surroundings and your phone’s system.

The default APK supports **64-bit ARM (arm64-v8a)** phones.
