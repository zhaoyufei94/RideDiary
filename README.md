# RideDiary / 途记

<img src="docs/brand/ridediary-logo.svg" width="96" height="96" alt="RideDiary logo" />

**Your everyday rides, a little better understood.** · [中文说明](README.zh-CN.md) · [Changelog](CHANGELOG.md)

Current version: **0.2.1**

RideDiary is an offline-first trip journal that helps you understand your commute and compare it with earlier trips on the same route. For Android 8.0 and later; no account, extra hardware or Google Play services needed.

## Features

- **Record trips:** see elapsed time, distance, average speed, stationary time and location status, with motorcycle progress after route recognition.
- **Check weather:** current conditions, next-hour precipitation chance, today’s overview and departure-window feels-like temperatures.
- **Travel plans:** set workdays or custom weekdays and departure times separately for each direction; Today/Tomorrow show upcoming plans, with a play icon to start on the selected route and direction.
- **Manage routes:** keep work/home tracks under one route name, with automatic matching and manual correction.
- **Explore statistics:** view monthly distance, commute days and trip count across both directions, then select a direction for durations and charts.
- **Review history:** inspect time breakdowns, stops, tracks and historical comparisons; correct classification, adjust boundaries, exclude or delete trips.

## Getting started

1. Open the APK and follow your phone's installation prompts; install updates over the existing app to retain your records.
2. While parked, tap **Start recording**, grant precise location and enable system location.
3. To finish, choose **Cancel** to continue, **Delete** to discard this trip, or **Save** to keep it; notification finish and detected arrival offer the same choices, and recording continues until you decide.
4. After saving, review the matched route, select an existing route or use **Create a new route** at the top; a missing work/home direction can use the current track.
5. In Statistics, choose a month and route, then To work / To home; open a trip in Trips for details.

Chinese is the default; change language and weather service in Settings at the top right. Open-Meteo is the default; QWeather requires your API Host and API Key from the [QWeather console](https://id.qweather.com/), followed by Save weather settings. Weather can be turned off. Operate the app while parked.

Weekday plans from earlier versions remain as Custom weekdays. To follow holidays and make-up workdays, switch the route's travel plan to Workdays.

## Permissions and conditions

| Permission or condition | Purpose and effect |
|---|---|
| Precise location | Records tracks and recognizes routes; denied or approximate-only access prevents recording, but history remains available |
| Approximate location, weather only | Shows local weather; granting weather location never starts recording, and existing route-origin weather works without it |
| Internet | Fetches weather; recording and history still work offline or with weather turned off |
| Notifications, Android 13+ | Shows recording status and a finish entry; if denied, return to the app to finish |
| System location enabled | Supplies fixes; when disabled or unavailable, timing continues and unclassifiable intervals are unknown |

No “Allow all the time” location access is required; trips never start automatically or at boot.

## Using RideDiary

- **Travel plans:** open a route, choose To work or To home and edit its plan. Workdays is the default, using the bundled China calendar to exclude holidays and include make-up workdays. Custom weekdays are also available; clear all to exclude that direction. The 2026 calendar is included; other years arrive with app updates, without guessing missing years or fetching calendars at runtime. Set a time or use usual departure history, with 08:00 to work / 18:00 to home when no history exists.
- **Today/Tomorrow:** only show routes scheduled for that day with a future departure, ordered by time. Reached times disappear without starting recording or shifting into another window. Plans follow the phone’s current timezone.
- **Departure weather:** previews combine endpoint feels-like temperature, rain probability and gusts from planned departure through median historical duration, with one hour when duration is unknown.
- **Weather refresh:** automatic checks run while Record is visible; pull down to refresh current weather and both days’ route forecasts together. Normal cache lasts 20 minutes; failures retain recent usable data and show a Toast. Only current weather displays an update time. Sources appear once at the bottom of Record. Requests can take several seconds and do not affect offline recording. Background weather reminders are not available yet.
- **Routes and history:** creating a route needs four valid fixes and 150 m of track; endpoint radii are 20–500 m, and historical comparison/arrival estimates need three usable histories for the same direction.
- **Arrival prompts:** near-complete progress and at least 30 seconds of sustained low speed may trigger review, but Save/Delete still requires your choice; similar routes, deviation or poor fixes can prevent recognition and estimates.
- **Time breakdown:** elapsed time equals moving, stationary and unknown time; estimates do not include live traffic.
- **Screen and battery:** the visible app stays awake during recording and restores normal timeout when you leave or finish; the power button can lock the screen while background recording continues, and location/screen use increases battery consumption.
- **Edits and deletion:** hold a trip or route card for management, or browse linked trips inside a route, open details or hold to unlink; deleting a route keeps trips, while deleting a trip removes its raw track.
- **Interruptions:** time to the first fix depends on surroundings; if recording is interrupted, check location and app background restrictions, then reopen to inspect saved data without automatic resumption.

## Data and limits

Trips and full tracks stay on your phone. Weather queries send current or route-endpoint coordinates rounded to two decimal places to the selected provider; QWeather credentials are encrypted locally. **Uninstalling or clearing app data loses records; export, import and cloud recovery are not available yet.**

[Open-Meteo’s free API](https://open-meteo.com/en/pricing) is for non-commercial use; QWeather access and quotas depend on your account. Weather is a grid forecast; endpoint previews do not cover every location along the route or describe road-surface conditions.

Track sketches are available; map basemaps, navigation, live traffic and red-light detection are not. Location accuracy and background continuity depend on surroundings and your phone's system.

## Planned features

- Configurable adverse-weather reminders.
- Local track/statistics export, backup and import.
- Stationary hotspots, segment analysis and estimates informed by usual departure times.
- Easier route editing, large text, screen readers, larger screens and dark mode.

These features are not all available yet; see the changelog for delivered updates.
