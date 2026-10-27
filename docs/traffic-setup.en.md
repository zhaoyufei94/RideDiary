# Traffic on recommended routes

[中文](traffic-setup.zh-CN.md)

1. Open Settings → Baidu Maps and traffic. Track-map settings and recommendation setup links open the same panel.
2. Your existing Android SDK AK is retained automatically. No repeat entry or additional server AK is needed. For first-time setup, follow the [Baidu Maps guide](baidu-map-setup.en.md).
3. Enable Live traffic on recommended routes and choose Save Baidu settings. Basemaps and recommendation traffic have independent switches sharing one AK; recommendation traffic is off by default.
4. From two hours before until one hour after departure, open Record or pull to refresh. Traffic follows weather refresh with separate directional queries. Background, other pages and active recording do not request recommendation traffic.

This version provides **current traffic and driving reference time** only, without future forecasts or optimal departure windows. It uses Android SDK routing with traffic: [official interface](https://lbsyun.baidu.com/cms/androidsdk/doc/v4_0_0/com/baidu/mapapi/search/route/DrivingRoutePlanOption.DrivingTrafficPolicy.html). Availability and quota depend on your Baidu account.

Times are **driving references**, not actual motorcycle times or navigation guidance. Missing or expired data never means clear traffic, and personal history never substitutes for live traffic. Cards show update time and congestion changes when a usable prior observation exists.

Traffic starts collapsed, showing current conditions and driving reference time. Use the arrow to expand update time, congestion changes, route similarity and offsets; tap again to collapse. Each recommendation has its own toggle.

Queries send route endpoints and waypoints to Baidu; comparison stays on device. Traffic is a planned-route reference allowing detours and moderate endpoint offsets, rather than disappearing after minor differences. Cards show similarity, distance difference and endpoint offsets. Similarity estimates nearby, similarly directed travel within about 100 m, rather than exact road overlap. Opposite direction, distant endpoints or missing geometry still withhold the reference, without changing routes or raw trips.

Current traffic is shown for at most 20 minutes. If access or key validation fails, check the existing Android AK package, signature and service access; do not copy it into a second key field. Offline recording and statistics remain available when traffic is disabled or unavailable.
