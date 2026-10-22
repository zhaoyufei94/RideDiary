#!/bin/sh
# Use this checkout's downloaded tools when present, or the caller's configured JDK/SDK.
set -eu
ridediary_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ -z "${JAVA_HOME:-}" ] && [ -d "$ridediary_root/.tooling/jdk" ]; then
    JAVA_HOME="$ridediary_root/.tooling/jdk"
    export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$ridediary_root/.tooling/android-sdk" ]; then
    ANDROID_HOME="$ridediary_root/.tooling/android-sdk"
    export ANDROID_HOME
fi
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ridediary_root/.tooling/gradle-home}"
export GRADLE_USER_HOME
if [ "$#" -eq 0 ]; then
    set -- :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
fi
exec "$ridediary_root/gradlew" -p "$ridediary_root" "$@"
