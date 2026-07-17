#!/usr/bin/env bash
# Stamp MARKETING_VERSION / CURRENT_PROJECT_VERSION into iosApp.xcodeproj/project.pbxproj before an
# archive build. Run from CI (github-release.yml / release.yml ios job) — never commit the result,
# the pbxproj's checked-in values (1.0 / 1) are just a local-build fallback.
#
# Uses the same three-tier model as gradle/versioning.gradle.kts (see docs/RELEASE.md):
#   MARKETING (CFBundleShortVersionString) = YYYY.M.<MILESTONE>
#   BUILDCODE (CFBundleVersion)            = 1 + commitCount
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PBXPROJ="$ROOT/iosApp/iosApp.xcodeproj/project.pbxproj"
MILESTONE_FILE="$ROOT/MILESTONE"

[ -f "$PBXPROJ" ] || { echo "no iosApp/iosApp.xcodeproj — nothing to stamp" >&2; exit 0; }

commit_count="$(git -C "$ROOT" rev-list --count HEAD)"
year="$(date -u +%Y)"
month="$(date -u +%-m 2>/dev/null || date -u +%m | sed 's/^0//')"
milestone="$(tr -d '[:space:]' < "$MILESTONE_FILE" 2>/dev/null || echo 1)"
marketing="${year}.${month}.${milestone}"
buildcode=$((1 + commit_count))

sed -i.bak -E \
  -e "s/MARKETING_VERSION = [^;]+;/MARKETING_VERSION = ${marketing};/" \
  -e "s/CURRENT_PROJECT_VERSION = [^;]+;/CURRENT_PROJECT_VERSION = ${buildcode};/" \
  "$PBXPROJ"
rm -f "${PBXPROJ}.bak"

echo "Stamped iosApp: MARKETING_VERSION=$marketing CURRENT_PROJECT_VERSION=$buildcode"
