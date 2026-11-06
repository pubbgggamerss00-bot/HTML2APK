#!/usr/bin/env bash
# Builds the template APK and embeds it into the app assets.
# The template APK is the base APK that the HTML2APK engine patches at runtime.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -x ./gradlew ]; then
  echo "gradlew not found — generating wrapper (requires a local Gradle installation)"
  gradle wrapper --gradle-version 8.7 --distribution-type bin
fi

./gradlew :template:assembleRelease --no-daemon

SRC="template/build/outputs/apk/release/template-release-unsigned.apk"
if [ ! -f "$SRC" ]; then
  echo "ERROR: $SRC not found" >&2
  exit 1
fi

mkdir -p app/src/main/assets
cp "$SRC" app/src/main/assets/template.apk
echo "template.apk embedded: $(du -h app/src/main/assets/template.apk | cut -f1)"
