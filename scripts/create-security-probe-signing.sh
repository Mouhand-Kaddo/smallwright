#!/usr/bin/env bash
set -euo pipefail

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
directory="$root/signing"
keystore="$directory/security-probe.jks"
properties="$directory/security-probe.properties"

if [ -e "$keystore" ] || [ -e "$properties" ]; then
    if [ -f "$keystore" ] && [ -f "$properties" ]; then exit 0; fi
    echo 'Incomplete security probe signing identity; resolve it without overwriting existing files.' >&2
    exit 1
fi

mkdir -p "$directory"
umask 077
password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
"${JAVA_HOME:?Set JAVA_HOME to the project JDK}/bin/keytool" -genkeypair \
    -keystore "$keystore" -storetype JKS -storepass "$password" \
    -alias smallwright-security-probe -keypass "$password" -keyalg RSA -keysize 3072 \
    -validity 365 -dname 'CN=Smallwright Security Probe, OU=Local Development, O=Smallwright, C=US' \
    -noprompt >/dev/null
printf 'storePassword=%s\nkeyAlias=smallwright-security-probe\nkeyPassword=%s\n' \
    "$password" "$password" > "$properties"
