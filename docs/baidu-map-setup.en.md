# Baidu Maps AK setup

Each user can enter an Android SDK AK from their own account in the APK. Recording, statistics, zoomable local tracks, speed colors and stop analysis work without a key or with maps disabled.

1. Sign in to the [Baidu Maps console](https://lbsyun.baidu.com/apiconsole/key) and complete the developer registration/verification requested by the console.
2. Open Application Management → My Applications → Create Application. Name it RideDiary and choose **Android SDK**.
3. In the APK, open Settings → Baidu Maps and traffic → Copy package and SHA-1, then enter those values in the console. The current shared signing certificate uses:

   - Package: `com.ridediary`
   - SHA1: `FD:FB:9B:E9:10:90:FD:1D:16:19:2B:24:64:B6:99:3B:5A:7F:E3:9F`

   Current local Debug and Release performance APKs share this certificate, so the development and release SHA1 fields can use the same value. If an APK uses another certificate, copy its actual SHA1 from Settings.
4. Enable the map SDK services needed by the application, submit, and copy the generated AK. Do not select browser or server application types.
5. Paste the AK in Settings → Baidu Maps, enable and save, then reopen an expanded track. Leave the optional style ID blank initially to confirm basic map access.
6. To use Tea Field, select that template and publish it in your account's [map style editor](https://lbsyun.baidu.com/index.php?title=open/custom), then enter and save its ID in the APK. In the expanded track, choose Display → Basemap style → Custom and apply. An existing style ID remains selected by default after upgrading.

Display also offers minimal, standard and satellite basemaps, with separate controls for speed colors, stop points/duration bubbles, endpoints, place names and live traffic. Preferences stay on the device; track contents also apply to offline local tracks. Live traffic shows current road conditions and does not explain historical trip speeds.

If maps fail, check AK type, package, full colon-separated SHA1, service permissions and connectivity. Local tracks remain available. Enter the AK directly in the APK; sharing it with others is unnecessary.

Official instructions: [Register and get a key](https://lbs.baidu.com/docs/guide?title=android-guide/create-project/ak). The SHA1 above was verified from a local APK using `apksigner verify --print-certs`.
