#!/usr/bin/env bash
# Checks that a release can be reproduced, the way F-Droid does it: builds the full edition from a
# clean checkout of a tag (default: the newest v* tag) in a temporary folder, then compares it with
# the signed APK in release/ using apksigcopier (pip install apksigcopier).
#
# Usage: scripts/check-reproducible.sh [tag]
set -euo pipefail
cd "$(dirname "$0")/.."
tag=${1:-$(git tag --list 'v*' --sort=-v:refname | head -1)}
version=${tag#v}
signed="release/DayPurse-$version.apk"
[ -f "$signed" ] || { echo "$signed not found; run scripts/build-release.sh first." >&2; exit 1; }
command -v apksigcopier >/dev/null || { echo "apksigcopier not found: pip install apksigcopier" >&2; exit 1; }
# apksigcopier verifies with apksigner from the Android SDK.
if ! command -v apksigner >/dev/null; then
  sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
  tools=$(ls -d "${ANDROID_HOME:-${sdk:-$HOME/Android/Sdk}}"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
  [ -n "$tools" ] && export PATH="$tools:$PATH"
fi
command -v apksigner >/dev/null || { echo "apksigner not found: install Android SDK build-tools or set ANDROID_HOME" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
git clone --quiet --no-checkout . "$work/daypurse"
git -C "$work/daypurse" -c advice.detachedHead=false checkout --quiet "$tag"
cp local.properties "$work/daypurse/" 2>/dev/null || true
(cd "$work/daypurse" && ./gradlew --quiet --no-daemon --no-configuration-cache assembleFullRelease)
unsigned="$work/daypurse/app/build/outputs/apk/full/release/app-full-release-unsigned.apk"

if apksigcopier compare "$signed" --unsigned "$unsigned"; then
  echo "Reproducible: a clean build of $tag matches $signed."
else
  echo "NOT reproducible: a clean build of $tag differs from $signed." >&2
  exit 1
fi
