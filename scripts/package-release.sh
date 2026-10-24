#!/bin/sh
# Build a local performance-test Release APK with the shared debug certificate.
# Formal distribution signing is not configured by this repository.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
version=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$root/app/build.gradle.kts" | head -1)
test -n "$version"
"$root/scripts/gradle-local.sh" :app:assembleRelease -Pkotlin.compiler.execution.strategy=in-process

android_home=${ANDROID_HOME:-$root/.tooling/android-sdk}
build_tools=$(find "$android_home/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
test -d "$build_tools"
unsigned="$root/app/build/outputs/apk/release/app-release-unsigned.apk"
output="$root/app/build/outputs/apk/release/RideDiary-release-v${version}.apk"
test -f "$unsigned"
rm -f "$output"
JAVA_HOME=${JAVA_HOME:-$root/.tooling/jdk} "$build_tools/apksigner" sign \
    --ks "$root/.signing/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$output" "$unsigned"
echo "$output"
