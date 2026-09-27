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
release_sha=$(git rev-parse HEAD)

# Keep the F-Droid draft in sync so a later release is just "copy this file into the
# fork". Its `commit:` field must be a real 40-char hash, not a tag or branch name
# (F-Droid requires this) - point it at the release commit above, in its OWN commit:
# embedding a commit's hash inside itself is impossible (the hash covers the content).
fdroid_meta="fdroid/io.github.ds17f.andropolis.yml"
if [[ -f "$fdroid_meta" ]]; then
    sed -i -E "s/versionName: [0-9.]+/versionName: $ver/; \
        s/versionCode: [0-9]+/versionCode: $code/; \
        s/commit: [0-9a-f]{40}/commit: $release_sha/; \
        s/CurrentVersion: [0-9.]+/CurrentVersion: $ver/; \
        s/CurrentVersionCode: [0-9]+/CurrentVersionCode: $code/" "$fdroid_meta"
    git add "$fdroid_meta"
    git commit -m "fdroid: bump metadata to v$ver (versionCode $code)"
fi

# Tag whichever commit is now HEAD (with or without the fdroid-sync commit above) -
# CI only checks versionName in build.gradle.kts, unchanged by either commit.
git tag -a "v$ver" -m "Micropolis $ver"
echo "Tagged v$ver (versionCode $code) at $(git rev-parse HEAD). Push with: git push origin main --tags"
