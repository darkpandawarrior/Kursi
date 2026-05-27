#!/usr/bin/env bash
# Single-source version bump for Kursi.
# VERSION holds the semantic versionName; BUILD_NUMBER is a monotonically increasing
# counter. versionCode = 1 + BUILD_NUMBER (mirrors cmp-android/build.gradle.kts).
#
# Usage: scripts/bump_version.sh --major|--minor|--patch
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$ROOT/VERSION"
BUILD_NUMBER_FILE="$ROOT/BUILD_NUMBER"

[ -f "$VERSION_FILE" ]      || echo "1.0.0" > "$VERSION_FILE"
[ -f "$BUILD_NUMBER_FILE" ] || echo "0"     > "$BUILD_NUMBER_FILE"

version="$(tr -d '[:space:]' < "$VERSION_FILE")"
build="$(tr -d '[:space:]' < "$BUILD_NUMBER_FILE")"
IFS='.' read -r major minor patch <<< "$version"

case "${1:-}" in
  --major) major=$((major + 1)); minor=0; patch=0 ;;
  --minor) minor=$((minor + 1)); patch=0 ;;
  --patch) patch=$((patch + 1)) ;;
  *) echo "usage: $(basename "$0") --major|--minor|--patch" >&2; exit 1 ;;
esac

new_version="${major}.${minor}.${patch}"
new_build=$((build + 1))
new_code=$((1 + new_build))

echo "$new_version" > "$VERSION_FILE"
echo "$new_build"   > "$BUILD_NUMBER_FILE"

echo "Version bumped: $version → $new_version  (build $build → $new_build, versionCode $new_code)"
echo "Commit with: git commit -am \"chore: bump version to $new_version (build $new_build)\""
