#!/usr/bin/env bash
# Dot this file before running Gradle after local tooling is installed.
# It intentionally contains paths only, not credentials.
PLB_ROOT="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

export JAVA_HOME="$PLB_ROOT/toolchains/jdk-21.0.12.1+1"
export ANDROID_HOME="$PLB_ROOT/toolchains/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$PLB_ROOT/toolchains/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
