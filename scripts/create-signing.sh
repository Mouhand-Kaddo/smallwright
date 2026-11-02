#!/usr/bin/env bash
set -euo pipefail

name="${1:-harness-alpha}"
case "$name" in
    harness-alpha | release) ;;
    *) echo "Usage: $0 [harness-alpha|release]" >&2; exit 2 ;;
esac

root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
directory="$root/signing"
keystore="$directory/$name.jks"
properties="$directory/$name.properties"

if [ -e "$keystore" ] || [ -e "$properties" ]; then
    exit 0
fi

mkdir -p "$directory"
umask 077
password="$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
"${JAVA_HOME:?Set JAVA_HOME to the project JDK}/bin/keytool" -genkeypair \
    -keystore "$keystore" -storetype JKS -storepass "$password" \
    -alias "smallwright-$name" -keypass "$password" -keyalg RSA -keysize 3072 \
    -validity 10000 -dname "CN=Smallwright, O=Smallwright" \
    -noprompt >/dev/null
printf 'storePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' "$password" "smallwright-$name" "$password" > "$properties"
