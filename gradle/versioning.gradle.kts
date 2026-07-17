// gradle/versioning.gradle.kts — three-tier versioning shared by cmp-android, cmp-desktop, server.
// See docs/RELEASE.md for the model. Apply with `apply(from = "$rootDir/gradle/versioning.gradle.kts")`.
//
// Source files (repo root): VERSION (informational semver), MILESTONE (integer, bumped by
// scripts/bump_version.sh --milestone), BUILD_NUMBER (legacy, no longer drives versionCode).
//
// Derived (all computed, none hand-typed):
//   kursiFingerprint = YYYY.0M.0W.<MILESTONE>.<commitCount>  e.g. 2026.07.03.4.127
//   kursiMarketing   = YYYY.M.<MILESTONE>                    e.g. 2026.7.4  (<=3 int components,
//                      required by iOS CFBundleShortVersionString)
//   kursiBuildCode   = VERSION_CODE_BASE + commitCount        monotonic int, < 2.1e9

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

fun kursiCommitCount(): Int =
    providers
        .exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }.standardOutput.asText
        .get()
        .trim()
        .toIntOrNull() ?: 0

fun kursiMilestone(): Int =
    rootProject
        .file("MILESTONE")
        .takeIf { it.exists() }
        ?.readText()
        ?.trim()
        ?.toIntOrNull() ?: 1

val kursiUtc = TimeZone.getTimeZone("UTC")
val kursiNow = Date()
fun kursiFmt(pattern: String): String =
    SimpleDateFormat(pattern).apply { timeZone = kursiUtc }.format(kursiNow)

val kursiCommitCountVal = kursiCommitCount()
val kursiMilestoneVal = kursiMilestone()
val kursiVersionCodeBase = 1

extra["kursiFingerprint"] =
    "${kursiFmt("yyyy")}.${kursiFmt("MM")}.${kursiFmt("ww")}.$kursiMilestoneVal.$kursiCommitCountVal"
extra["kursiMarketing"] = "${kursiFmt("yyyy")}.${kursiFmt("M")}.$kursiMilestoneVal"
extra["kursiBuildCode"] = kursiVersionCodeBase + kursiCommitCountVal

// Compose Desktop native packages (Dmg/Msi/Deb) reject the date-based MARKETING string — they need
// strict MAJOR.MINOR.BUILD with MAJOR/MINOR <= 255, BUILD <= 65535, validated at configure time.
// ponytail: MILESTONE.0.commitCount fits until MILESTONE>255 or commitCount>65535 (years away).
extra["kursiDesktopPackageVersion"] = "$kursiMilestoneVal.0.$kursiCommitCountVal"
