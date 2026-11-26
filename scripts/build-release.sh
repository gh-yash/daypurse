#!/usr/bin/env bash
# Builds all release files into release/ (git-ignored):
#   DayPurse-<version>.apk       full edition, reproducible  -> GitHub release (F-Droid checks against it)
#   DayPurse-Lite-<version>.apk  lite edition, reproducible  -> GitHub release
#   DayPurse-<version>.aab       full edition, optimized     -> Google Play
#
# Needs keystore.properties (see keystore.properties.example). Use JDK 21, like F-Droid's build servers.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f keystore.properties ] || { echo "keystore.properties is missing: release builds must be signed." >&2; exit 1; }
version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
[ -n "$version" ] || { echo "Can't read versionName from app/build.gradle.kts" >&2; exit 1; }
# The APK records the git commit it was built from, so it only matches F-Droid's build of the tag
# when built from that exact, clean commit.
if [ -n "$(git status --porcelain)" ] && [ "${ALLOW_DIRTY:-}" != 1 ]; then
  echo "Uncommitted changes: commit (and tag) first, or set ALLOW_DIRTY=1 for a test build." >&2
  exit 1
fi

./gradlew --no-configuration-cache clean assembleFullRelease assembleLiteRelease
mkdir -p release
cp app/build/outputs/apk/full/release/app-full-release.apk "release/DayPurse-$version.apk"
cp app/build/outputs/apk/lite/release/app-lite-release.apk "release/DayPurse-Lite-$version.apk"

./gradlew --no-configuration-cache -Pdaypurse.play=true bundleFullRelease
cp app/build/outputs/bundle/fullRelease/app-full-release.aab "release/DayPurse-$version.aab"

sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
apksigner=$(ls -d "${ANDROID_HOME:-${sdk:-$HOME/Android/Sdk}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
for apk in "release/DayPurse-$version.apk" "release/DayPurse-Lite-$version.apk"; do
  if [ -n "$apksigner" ]; then
    "$apksigner" verify "$apk" 2>/dev/null && echo "$apk: signature OK"
  fi
done
if [ -n "$apksigner" ]; then
  echo "Signing certificate SHA-256 (F-Droid's AllowedAPKSigningKeys):"
  "$apksigner" verify --print-certs "release/DayPurse-$version.apk" 2>/dev/null | sed -n 's/.*SHA-256 digest: //p' | head -1
fi
ls -lh release/*"$version"*
