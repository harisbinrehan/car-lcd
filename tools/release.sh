#!/bin/bash
# Publishes a new version that the head unit offers as an in-app update.
#
#   tools/release.sh <version-name> "<what's new, shown on the car screen>"
#   e.g. tools/release.sh 2.1 "Bigger music player. Qibla on the compass."
#
# It bumps versionCode by one and sets versionName, builds a signed release APK, commits and tags
# the version, pushes, and creates a GitHub release with the APK attached as car-lcd-<code>.apk plus
# latest.json, the small file the app reads to learn about the release.
# Needs: gh logged in, the signing key and the report key in ~/.carlcd (see CLAUDE.md), JAVA_HOME /
# ANDROID_SDK_ROOT.
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="${1:?version name, e.g. 2.1}"
NOTES="${2:?release notes}"
GRADLE="${GRADLE:-gradle}"
[ -f "$HOME/.carlcd/signing.properties" ] || { echo "signing key missing: ~/.carlcd/signing.properties"; exit 1; }
# NO_REPORT_KEY=1 publishes without the report key on purpose (Send report then says it is not set up)
[ -n "${NO_REPORT_KEY:-}" ] || grep -q '^token=.' "$HOME/.carlcd/reports.properties" 2>/dev/null || { echo "report key missing: ~/.carlcd/reports.properties (Send report would not work in this release)"; exit 1; }
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
# latest.json is what the app reads to find this version (a plain download, no GitHub API allowance used)
JSON="app/build/outputs/apk/release/latest.json"
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
python3 - "$CODE" "$NAME" "$NOTES" "https://github.com/$REPO/releases/download/v$NAME/car-lcd-$CODE.apk" "$OUT" > "$JSON" <<'PY'
import json, os, sys
code, name, notes, url, apk = sys.argv[1:6]
print(json.dumps({"code": int(code), "name": name, "notes": notes, "url": url, "size": os.path.getsize(apk)}))
PY

git add app/build.gradle
git commit -q -m "Release version $NAME"
git tag "v$NAME"
git push -q origin HEAD "v$NAME"
gh release create "v$NAME" "$OUT" "$JSON" --title "Car LCD $NAME" --notes "$NOTES"
echo "published: https://github.com/$REPO/releases/tag/v$NAME"
