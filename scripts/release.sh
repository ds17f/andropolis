#!/usr/bin/env bash
# Cut a release: bump versionName/versionCode, add the changelog, commit, tag.
#   scripts/release.sh 0.2.0 "What changed, in one or two lines."
# Then: git push origin main --tags   (CI builds, signs and publishes the tag).
set -euo pipefail
cd "$(dirname "$0")/.."
ver="${1:?usage: release.sh X.Y.Z \"changelog\"}"
notes="${2:?give a changelog line}"
[[ "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must be X.Y.Z"; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "commit your work first"; exit 3; }
f=android/app/build.gradle.kts
code=$(( $(grep -oP 'versionCode = \K[0-9]+' "$f") + 1 ))
sed -i -E "s/versionCode = [0-9]+/versionCode = $code/; s/versionName = \"[^\"]+\"/versionName = \"$ver\"/" "$f"
printf '%s\n' "$notes" > "fastlane/metadata/android/en-US/changelogs/$code.txt"
git add "$f" "fastlane/metadata/android/en-US/changelogs/$code.txt"
git commit -m "release v$ver (versionCode $code)"
git tag -a "v$ver" -m "Micropolis $ver"
echo "Tagged v$ver (versionCode $code). Push with: git push origin main --tags"
