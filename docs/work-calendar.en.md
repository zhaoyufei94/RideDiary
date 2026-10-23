# Bundled workday calendars

Version 0.2.0 includes mainland China’s **2026** holidays and make-up workdays from the [State Council notice](https://www.gov.cn/zhengce/content/202511/content_7047090.htm). Structured data comes from [holiday-cn](https://github.com/NateScarlet/holiday-cn); the JSON and MIT license ship under `app/src/main/assets/work-calendar/`.

Reviewed 2026-10-23. Make-up workdays are January 4, February 14/28, May 9, September 20 and October 10; holiday intervals are retained day by day from the notice.

No formal 2027 schedule was found in this review, and the [data file](https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2027.json) still has empty papers/days. Neither an empty placeholder nor estimated holidays are bundled as an available calendar.

At runtime, read only the APK: no calendar queries, downloads, updates or location transmission. Apply published holiday/workday overrides before the normal weekday rule within covered years, with any bundled following-year year-end overrides taking priority. Missing years return unknown, display a notice and withhold workday suggestions; custom weekdays continue working.

After each annual notice is published, verify/update its JSON and ship it with sources/license in an app release. Never substitute empty files, prior-year arrangements or Monday–Friday for missing annual data. Recommendations for make-up weekends, weekday holidays, normal dates, year boundaries and missing years still need validation. Version 0.2.1 passed four device database-migration cases and verified the actual in-place upgrade, startup and data retention; these results do not establish calendar-recommendation acceptance. See the [test record](testing.en.md).
