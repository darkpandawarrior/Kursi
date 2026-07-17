package com.kursi.feature.game

/**
 * Progressive-disclosure spine (spec §3). The same game revealed at three densities:
 *  - FOCUS   — whose turn · one plain-language line · your hand · your actions. Nothing else.
 *  - GUIDED  — FOCUS + one suggestion at a time.
 *  - ANALYST — the full instrument panel (today's screen). Default; preserves existing behavior.
 *
 * Persisted as a String in AppPrefs (core/prefs stays enum-free); the app layer maps String ↔ enum.
 */
enum class DensityLayer {
    FOCUS,
    GUIDED,
    ANALYST,
    ;

    companion object {
        /** Unknown / null → ANALYST (safe default = today's full behavior). */
        fun fromName(name: String?): DensityLayer = entries.firstOrNull { it.name == name } ?: ANALYST
    }
}
