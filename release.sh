#!/usr/bin/env bash
# Build the signed release APK as `portal-widgets.apk` — the stable asset name the
# Immortal App Store serves from releases/latest/download/portal-widgets.apk.
#
# Usage:
#   ./release.sh             # build portal-widgets.apk (signed with the release key)
#   ./release.sh upload      # build, then upload to the latest git tag's GH release
#   TAG=v1.0 ./release.sh upload
#
# Requires keystore.properties (local, gitignored) pointing at the release keystore
# (backed up in 1Password: "Portal Widgets — Android release keystore").
set -euo pipefail
cd "$(dirname "$0")"

[ -f keystore.properties ] || { echo "keystore.properties missing — can't sign a release" >&2; exit 1; }

./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk portal-widgets.apk
echo ">> built portal-widgets.apk ($(du -h portal-widgets.apk | cut -f1))"

APKSIGNER=$(ls "$HOME"/Android/Sdk/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
[ -n "$APKSIGNER" ] && "$APKSIGNER" verify --print-certs portal-widgets.apk | grep -iE "SHA-256" | head -1 || true

if [ "${1:-}" = "upload" ]; then
  TAG="${TAG:-$(git describe --tags --abbrev=0)}"
  gh release upload "$TAG" portal-widgets.apk --clobber
  echo ">> uploaded portal-widgets.apk to release $TAG"
fi
