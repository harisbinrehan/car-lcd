#!/bin/bash
# Publishes a new version that the head unit offers as an in-app update.
#
#   tools/release.sh <version-name> "<what's new, shown on the car screen>"
#   e.g. tools/release.sh 2.1 "Bigger music player. Qibla on the compass."
#
# It bumps versionCode by one and sets versionName, builds a signed release APK, commits and tags
# the version, pushes, and creates a GitHub release with the APK attached as car-lcd-<code>.apk.
# Needs: gh logged in, the signing key in ~/.carlcd (see CLAUDE.md), JAVA_HOME / ANDROID_SDK_ROOT.
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:?version name, e.g. 2.1}"
NOTES="${2:?release notes}"
GRADLE="${GRADLE:-gradle}"
[ -f "$HOME/.carlcd/signing.properties" ] || { echo "signing key missing: ~/.carlcd/signing.properties"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit or stash your changes first"; exit 1; }
git rev-parse "v$NAME" >/dev/null 2>&1 && { echo "tag v$NAME already exists"; exit 1; }

OLD=$(sed -nE 's/^ *versionCode ([0-9]+).*/\1/p' app/build.gradle)
CODE=$((OLD + 1))
sed -i '' -E "s/^( *versionCode )[0-9]+/\1$CODE/; s/^( *versionName )\"[^\"]*\"/\1\"$NAME\"/" app/build.gradle
echo "version $NAME (code $CODE)"

"$GRADLE" :app:assembleRelease --no-daemon --console=plain -q
APK="app/build/outputs/apk/release/app-release.apk"
OUT="app/build/outputs/apk/release/car-lcd-$CODE.apk"
cp "$APK" "$OUT"

git add app/build.gradle
git commit -q -m "Release version $NAME"
git tag "v$NAME"
git push -q origin HEAD "v$NAME"
gh release create "v$NAME" "$OUT" --title "Car LCD $NAME" --notes "$NOTES"
echo "published: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/v$NAME"
