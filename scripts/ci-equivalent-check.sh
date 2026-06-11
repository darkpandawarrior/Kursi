#!/usr/bin/env bash
# CI equivalent check — run locally to catch everything CI will catch.
#
# Usage:  ./scripts/ci-equivalent-check.sh
# Exit code 0 = all checks passed; non-zero = one or more checks failed.
#
# Mirrors the checks in:
#   .github/workflows/quality.yml  — ktlint, detekt, jvmTest
#   .github/workflows/ci.yml       — jvmTest, assembleDebug, desktop compile
#
# Run before pushing to avoid the usual "oops, CI red" commit.

set -euo pipefail

PASS=0
FAIL=0

run_check() {
    local name="$1"
    shift
    printf '\n▶  %s\n' "$name"
    if "$@"; then
        printf '✔  %s passed\n' "$name"
        PASS=$((PASS + 1))
    else
        printf '✘  %s FAILED\n' "$name" >&2
        FAIL=$((FAIL + 1))
    fi
}

GRADLEW="./gradlew"
if [[ ! -x "$GRADLEW" ]]; then
    echo "ERROR: ./gradlew not found or not executable. Run from project root."
    exit 1
fi

# ── Quality ────────────────────────────────────────────────────────────────────
run_check "ktlint"   "$GRADLEW" ktlintCheck
run_check "detekt"   "$GRADLEW" detekt
run_check "jvmTest"  "$GRADLEW" jvmTest --continue

# ── Build ──────────────────────────────────────────────────────────────────────
run_check "Android assembleDebug"  "$GRADLEW" :cmp-android:assembleDebug
run_check "Desktop compile"        "$GRADLEW" :cmp-desktop:compileKotlinJvm

# ── iOS (compile-only — no Xcode needed for KMP compile) ──────────────────────
if command -v xcodebuild &>/dev/null; then
    run_check "iOS arm64 compile"       "$GRADLEW" :engine:compileKotlinIosArm64
    run_check "iOS simulator compile"   "$GRADLEW" :engine:compileKotlinIosSimulatorArm64
else
    printf '⚠   Skipping iOS compile — xcodebuild not found (macOS only)\n'
fi

# ── Summary ────────────────────────────────────────────────────────────────────
printf '\n══════════════════════════════════════════════\n'
printf '  %d passed  |  %d failed\n' "$PASS" "$FAIL"
printf '══════════════════════════════════════════════\n\n'

[[ "$FAIL" -eq 0 ]]
