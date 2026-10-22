# Shared development signing across two Macs

Android requires compatible signing certificates when updating an existing package. Each Mac normally generates its own `~/.android/debug.keystore`, so their default Debug APKs cannot update one another. See [Android signing documentation](https://developer.android.com/studio/publish/app-signing#debug-mode).

## Project configuration

- Package name remains `com.ridediary`.
- Both Macs use `.signing/debug.keystore` at the project root, without falling back to a machine-generated key.
- This is the same key used by version 0.1.1 currently installed on the phone, so adopting this configuration needs no uninstall or data migration.
- The public certificate pin is stored in `signing/debug-certificate.sha256`. Application and instrumentation APKs share this development key.
- `verifyDebugSigning` runs before Debug signing validation. A missing key, unavailable private key or wrong fingerprint fails the build instead of generating another key.
- `.signing/` is Git-ignored. The public fingerprint, importer and Gradle configuration may be committed.

Pinned certificate SHA-256:

```text
24107b0f1a354d165eb7bae7be2b235752428dae1d4622754e0ebdb84fe110d3
```

## One-time setup on the other Mac

1. Sync these code changes, including the Gradle configuration, public certificate pin and importer.
2. Transfer this project's `.signing/debug.keystore` using AirDrop or controlled encrypted storage. Only this key file is needed; no APK, ride database or complete `.tooling/` directory is required. Keep the private key out of Git, do not regenerate it, and retain a private backup.
3. Configure JDK 17 and Android SDK on the other Mac, then run from its project root:

```sh
python3 scripts/import-debug-signing.py "$HOME/Downloads/debug.keystore"
```

The script checks the certificate before importing to `.signing/debug.keystore`, with directory mode 700 and file mode 600. Supply the actual received filename. A wrong file does not replace the existing project key. Leave the other Mac's global `~/.android/debug.keystore` in place.

4. Build and inspect signing:

```sh
./scripts/gradle-local.sh :app:assembleDebug :app:signingReport
```

Android Studio Run and `./gradlew :app:assembleDebug` use the same Gradle configuration. Debug and DebugAndroidTest should report the same certificate SHA-256 on both Macs.

5. Update the phone:

```sh
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
```

On this Mac, use `.tooling/android-sdk/platform-tools/adb` if ADB is not on PATH. Both Macs can now produce mutually compatible updates while retaining application data. Increment `versionCode` for new versions; a matching certificate does not bypass Android's version-downgrade restrictions.

## Scope and troubleshooting

This remains a development Debug key with the standard `androiddebugkey` alias and Debug password. Release distribution signing needs separate configuration; Release is not automatically signed with this development key.

- **Shared Debug key missing**: import the existing shared key; do not generate another.
- **Wrong Debug signing certificate**: obtain the key matching the pinned certificate rather than a different Mac's automatically generated key.
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE**: verify the APK fingerprint and the installed version's signing identity. Back up application data before any necessary migration; do not directly uninstall a daily-use app.

Git deliberately does not transfer the private key. Import the same file again when cloning into a fresh checkout or replacing a Mac.
