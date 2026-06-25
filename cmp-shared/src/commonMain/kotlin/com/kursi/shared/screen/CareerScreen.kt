package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.PersonaRoster
import com.kursi.core.prefs.DecisionGrade
import com.kursi.core.prefs.DecisionLedger
import com.kursi.core.prefs.PersonaRecord
import com.kursi.core.prefs.StatsLedger
import com.kursi.designsystem.*
import com.kursi.shared.strings.KursiStrings
import com.kursi.shared.strings.LocalKursiStrings
import kotlin.math.roundToInt

/**
 * CareerScreen — Roznamcha career register (M3 §3). A persisted lifetime ledger surfaced as a
 * stamped office file: total cases (games), wins, win-rate, bluffs held/caught, and a per-persona
 * head-to-head table. Reachable from Home and Settings.
 */
@Composable
fun CareerScreen(
    ledger: StatsLedger,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** M6b — lifetime decision-quality dossier. Empty (default) hides the dossier section. */
    decisionLedger: DecisionLedger = DecisionLedger(),
    /** M6c — recorded finished matches (most-recent first) for the Review entry list. */
    recentMatches: List<com.kursi.feature.game.session.CompletedMatch> = emptyList(),
    /** M6c — open the Review screen on the recent-match at this index (most-recent = 0). */
    onReview: (index: Int) -> Unit = {},
    /** M6d — ranked ELO standing surfaced as a compact rank strip; taps through to the leaderboard. */
    ranked: com.kursi.core.prefs.RankedStanding = com.kursi.core.prefs.RankedStanding(),
    /** M6d — open the local leaderboard / standings screen. */
    onLeaderboard: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    Box(modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with back
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandTokens.TeakDark)
                    .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Back to home"
                        }
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("‹ DAFTAR", style = KursiType.title.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary)
                }
                Text(
                    "ROZNAMCHA — Career Register",
                    style = KursiType.title.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // M6d — ranked rank/rating strip at the top of the career file (always shown so the
                    // player's sarkari rank is visible even before the first ranked result lands).
                    RankedStrip(ranked = ranked, onOpen = onLeaderboard, modifier = Modifier.fillMaxWidth())
                    if (ledger.games == 0) {
                        CareerEmpty()
                    } else {
                        CareerHeadline(ledger)
                        CareerStatGrid(ledger)
                        if (!decisionLedger.isEmpty) DecisionDossier(decisionLedger)
                        CareerH2H(ledger)
                    }
                    // M6c — recent finished matches, each reviewable on the real table.
                    if (recentMatches.isNotEmpty()) {
                        RecentMatchesList(matches = recentMatches, onReview = onReview)
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.05f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(28.dp)
            .semantics { contentDescription = "No career record yet. Play a game to open your file." },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Khaata khaali hai", style = KursiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
            Text(
                "Abhi tak koi khel poora nahi hua. Ek baazi khelo — phir yahan record darj hoga.",
                style = KursiType.body.copy(fontSize = 12.sp),
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CareerHeadline(ledger: StatsLedger) {
    val winPct = (ledger.winRate * 100).roundToInt()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BrandTokens.BrassDark.copy(alpha = 0.5f), BrandTokens.TeakDark.copy(alpha = 0.7f)),
                ),
            )
            .border(1.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(20.dp)
            // Announced as a unit by a screen reader: the headline career line.
            .semantics {
                contentDescription =
                    "${ledger.wins} wins from ${ledger.games} games, $winPct percent win rate."
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .border(2.dp, BrandTokens.GoldAntique, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$winPct%", style = KursiType.display.copy(fontSize = 20.sp), color = BrandTokens.GoldAntique)
            }
            Column {
                Text("${ledger.wins} KURSI HAASIL", style = KursiType.title.copy(fontSize = 17.sp), color = KursiNeutrals.TextPrimary)
                Text(
                    "${ledger.games} khel · ${ledger.losses} haar",
                    style = KursiType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun CareerStatGrid(ledger: StatsLedger) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCell("Jhooth chala", "${ledger.bluffsHeld}", "bluffs held", Modifier.weight(1f))
        StatCell("Rangey haath", "${ledger.bluffsCaught}", "bluffs caught", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, a11y: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.05f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(16.dp)
            .semantics { contentDescription = "$value $a11y" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = KursiType.numeric.copy(fontSize = 24.sp), color = BrandTokens.GoldAntique)
            Text(label, style = KursiType.caption.copy(fontSize = 10.sp), color = KursiNeutrals.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ─────────────────────────── M6b Decision-Quality dossier ─────────────────────

/**
 * M6b — the decision-quality dossier: a stamped grade roundel (Sharp Babu / Steady / Reckless), a
 * sub-line naming the sample size, then a 2×2 grid of the four headline readouts — best-move match %,
 * avg win-probability bled, challenge discipline, and bluff survival. Bilingual via [LocalKursiStrings].
 */
@Composable
private fun DecisionDossier(dl: DecisionLedger) {
    val s = LocalKursiStrings.current
    val grade = dl.grade
    val (gradeName, gradeSub) = gradeStrings(grade, s)
    val gradeColor = when (grade) {
        DecisionGrade.SHARP -> KursiSemantics.Success
        DecisionGrade.STEADY -> BrandTokens.GoldAntique
        DecisionGrade.RECKLESS -> BrandTokens.StampRed
        DecisionGrade.UNRATED -> BrandTokens.BrassAged
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.04f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            s.dqHeader,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        // Grade stamp row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Grade $gradeName. ${dl.accuracyPct} percent of moves matched the best move, " +
                            "over ${dl.decisions} decisions."
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.5.dp, gradeColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .background(gradeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    gradeName,
                    style = KursiType.title.copy(fontSize = 15.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = gradeColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(gradeSub, style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic), color = KursiNeutrals.TextSecondary)
                Text(s.dqSampleSub(dl.decisions), style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
            }
        }
        // 2×2 readouts
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell(s.dqAccuracyLabel, "${dl.accuracyPct}%", "best-move match", Modifier.weight(1f))
            StatCell(s.dqEvLostLabel, "${dl.avgEvLostPct}%", "average win-prob bled per decision", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell(
                s.dqChallengeLabel,
                if (dl.challenges == 0) "—" else "${dl.challengeAccuracyPct}%",
                "challenge accuracy over ${dl.challenges} challenges",
                Modifier.weight(1f),
            )
            StatCell(
                s.dqBluffLabel,
                if (dl.bluffsTried == 0) "—" else "${dl.bluffSuccessPct}%",
                "bluff success over ${dl.bluffsTried} attempts",
                Modifier.weight(1f),
            )
        }
    }
}

/** Resolve the bilingual grade name + flavour sub-line for [grade]. */
private fun gradeStrings(grade: DecisionGrade, s: KursiStrings): Pair<String, String> = when (grade) {
    DecisionGrade.SHARP -> s.dqGradeSharp to s.dqGradeSharpSub
    DecisionGrade.STEADY -> s.dqGradeSteady to s.dqGradeSteadySub
    DecisionGrade.RECKLESS -> s.dqGradeReckless to s.dqGradeRecklessSub
    DecisionGrade.UNRATED -> s.dqGradeUnrated to s.dqGradeUnratedSub
}

@Composable
private fun CareerH2H(ledger: StatsLedger) {
    if (ledger.headToHead.isEmpty()) return
    // Order rivals by games faced (most-encountered first), resolving display names from the roster.
    val rows = ledger.headToHead.entries.sortedByDescending { it.value.played }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.04f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "AAMNE-SAAMNE — Head to Head",
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        rows.forEach { (id, rec) -> H2HRow(personaId = id, record = rec) }
    }
}

@Composable
private fun H2HRow(personaId: String, record: PersonaRecord) {
    val persona = PersonaRoster.ALL.firstOrNull { it.id == personaId }
    val name = persona?.name ?: personaId.replace("_", " ").replaceFirstChar { it.uppercase() }
    val color = persona?.seatColorArgb?.let { Color(it) } ?: BrandTokens.BrassAged
    val pct = if (record.played == 0) 0 else (record.wins * 100 / record.played)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$name: faced ${record.played} times, won ${record.wins}, $pct percent."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.4f))
                .border(1.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(persona?.monogram ?: "?", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.Cream)
        }
        Text(
            name,
            style = KursiType.body.copy(fontSize = 12.sp),
            color = KursiNeutrals.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${record.wins}/${record.played}  ($pct%)",
            style = KursiType.numeric.copy(fontSize = 12.sp),
            color = if (pct >= 50) KursiSemantics.Success else KursiNeutrals.TextSecondary,
        )
    }
}

/**
 * Compact career strip for the Home hub — a single-line lifetime summary that taps through to the
 * full [CareerScreen]. Hidden (returns nothing visible) when no games have been recorded yet.
 */
@Composable
fun CareerStrip(
    ledger: StatsLedger,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ledger.games == 0) return
    val winPct = (ledger.winRate * 100).roundToInt()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.TeakDark.copy(alpha = 0.7f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription =
                    "Career: ${ledger.wins} wins from ${ledger.games} games, $winPct percent. Tap to open the register."
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ROZNAMCHA", style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold), color = BrandTokens.BrassAged)
            Text(
                "${ledger.wins}W · ${ledger.losses}L · $winPct%",
                style = KursiType.numeric.copy(fontSize = 13.sp),
                color = BrandTokens.GoldAntique,
                modifier = Modifier.weight(1f),
            )
            Text("›", style = KursiType.title.copy(fontSize = 16.sp), color = KursiNeutrals.TextMuted)
        }
    }
}
