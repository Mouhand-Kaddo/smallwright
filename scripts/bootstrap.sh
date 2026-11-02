#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
tools="$root/toolchains"
downloads="$tools/downloads"
aapt2_url="${AAPT2_URL:-https://github.com/Mouhand-Kaddo/smallwright/releases/download/aapt2-android-16.0.0_r4/libaapt2_exec.so}"

fetch() {
    local url=$1 sha256=$2 target=$3
    if [ -f "$target" ] && echo "$sha256  $target" | sha256sum --check --status; then
        return
    fi
    mkdir -p "$(dirname -- "$target")"
    curl --fail --location --proto '=https' --tlsv1.2 --output "$target.part" "$url"
    if ! echo "$sha256  $target.part" | sha256sum --check --status; then
        rm -f "$target.part"
        echo "Checksum mismatch for $url" >&2
        exit 1
    fi
    mv "$target.part" "$target"
}

jdk="$tools/jdk-21.0.12.1+1"
if [ ! -x "$jdk/bin/java" ]; then
    fetch "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz" \
        ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94 \
        "$downloads/temurin-21.0.12.1+1-linux-x64.tar.gz"
    tar -xzf "$downloads/temurin-21.0.12.1+1-linux-x64.tar.gz" -C "$tools"
fi

sdk="$tools/android-sdk"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$sdkmanager" ]; then
    fetch "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" \
        4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583 \
        "$downloads/commandlinetools-linux-15859902_latest.zip"
    unpacked="$(mktemp -d "$tools/cmdline-tools.XXXXXX")"
    unzip -q "$downloads/commandlinetools-linux-15859902_latest.zip" -d "$unpacked"
    mkdir -p "$sdk/cmdline-tools"
    mv "$unpacked/cmdline-tools" "$sdk/cmdline-tools/latest"
    rmdir "$unpacked"
fi

JAVA_HOME="$jdk" "$sdkmanager" --sdk_root="$sdk" \
    "platforms;android-36" "build-tools;35.0.0" "build-tools;36.0.0" "platform-tools" \
    "ndk;28.2.13676358"

fetch "https://archive.eclipse.org/eclipse/downloads/drops4/R-4.12-201906051800/ecj-4.12.jar" \
    69dad18a1fcacd342a7d44c5abf74f50e7529975553a24c64bce0b29b86af497 \
    "$root/toolchain/input/ecj-4.12.jar"
fetch "https://dl.google.com/android/maven2/com/android/tools/r8/8.13.23/r8-8.13.23.jar" \
    e3cdcb003d9beca956209ad6b9e9df31f26b732bfaed9c7c8674e903ca9f3b81 \
    "$root/toolchain/input/r8-8.13.23.jar"
fetch "$aapt2_url" \
    ed917a383f890b2cad3eb3be00657f20d4fc6f73ca5bbbabf062d357633374e7 \
    "$root/worker/src/main/jniLibs/arm64-v8a/libaapt2_exec.so"

python3 "$root/scripts/verify_compiler_inputs.py"
echo "Ready. Next: source scripts/env.sh && ./gradlew assembleDebug"
