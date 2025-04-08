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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.KursiMoment
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.designsystem.moment.rememberMomentHost
import com.kursi.feature.game.LocalKursiVoice
import com.kursi.shared.nav.MatchSummary
import com.kursi.shared.strings.LocalKursiStrings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ResultsScreen — Faisla (S5) from 17_app_plan.md §4.
 *
 * Stamped share-certificate of the outcome:
 * - Verdict roundel: winner monogram + "KURSI HAASIL" stamp
 * - ROZNAMCHA recap stats
 * - Three exits: REMATCH / NAYA KHEL / DAFTAR
 */
@Composable
fun ResultsScreen(
    summary: MatchSummary?,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * M6c — open the Review screen on this just-finished match (the most-recent recorded game). Null
     * (default) hides the Review CTA — e.g. the expired-record state where there is nothing to replay.
     */
    onReview: (() -> Unit)? = null,
    /**
     * Gauntlet progression — navigate to the next rung on the Tarakki ladder. Non-null only when this
     * was a gauntlet game, the human won, and a next rung exists. Null hides the PROMOTE CTA.
     */
    onNextGauntletRung: (() -> Unit)? = null,
) {
    // M3 §4 — honest empty state. On a MatchSummaryStore cache miss (process death cleared the
    // in-memory store) we do NOT fabricate a 0-turn certificate; we show a "record expired" notice.
    if (summary == null) {
        ResultsExpired(onNewGame = onNewGame, onHome = onHome, modifier = modifier)
        return
    }
    val s = LocalKursiStrings.current
    val voice = LocalKursiVoice.current
    val scrollState = rememberScrollState()
    val winnerColor = Color(summary.winnerColor)
    val stampColor = if (summary.humanWon) BrandTokens.GoldAntique else BrandTokens.StampRed

    // ── KURSI HAASIL win moment on entry ──────────────────────────────────────
    // The verdict certificate stamps itself in: a Win moment plays once when the
    // screen first composes, celebrating the result before the static card settles.
    val momentHost = rememberMomentHost()
    val tableAnchors = remember { resultsAnchors() }
    LaunchedEffect(summary.matchId) {
        momentHost.play(KursiMoment.Win(actorSeat = summary.winnerSeat ?: 0))
    }

    Box(modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk)) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                s.resultsTitle,
                style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                color = BrandTokens.GoldAntique,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Centre the certificate in the available height: when the report
            // and bark are shorter than the viewport (the common case), the
            // slack distributes evenly above and below instead of pooling into
            // a dead band between the card and the footer. When content grows
            // past the viewport, verticalScroll lets it scroll normally.
            verticalArrangement = Arrangement.Center,
        ) {
          // Centred certificate column so the verdict reads as a stamped share-card
          // rather than full-bleed bands on wide desktop windows.
          Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            // ── Verdict Roundel ────────────────────────────────────────────────
            VerdictRoundel(
                summary = summary,
                winnerColor = winnerColor,
                stampColor = stampColor,
            )

            // Winner bark
            val winBark = when {
                summary.humanWon -> voice.youWin
                summary.winnerName != null -> {
                    val personaId = summary.bestMomentPersonaId
                        ?: summary.winnerName.lowercase().replace(" ", "_")
                    voice.personaBark(personaId, "win").ifEmpty { voice.youWin }
                }
                else -> voice.youWin
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandTokens.PaperCream.copy(alpha = 0.06f))
                    .border(1.dp, stampColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "\"$winBark\"",
                        style = KursiType.title.copy(fontSize = 15.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.resultsVerdictSub,
                        style = KursiType.caption.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── ROZNAMCHA Recap ────────────────────────────────────────────────
            RoznamchaRecap(summary = summary)

            Spacer(Modifier.height(8.dp))
          }
        }

        // ── Footer Actions ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            // PROMOTE — shown only for gauntlet wins with a next rung available
            if (onNextGauntletRung != null) {
                StampChit(
                    label = "PROMOTE",
                    sublabel = "Agla rung — next challenge awaits",
                    isHero = true,
                    onClick = onNextGauntletRung,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // REMATCH hero CTA — downgraded to non-hero when PROMOTE is present
            StampChit(
                label = s.resultsRematch,
                sublabel = s.resultsRematchSub,
                isHero = onNextGauntletRung == null,
                onClick = onRematch,
                modifier = Modifier.fillMaxWidth(),
            )
            // M6c — REVIEW THIS GAME (teach-by-review). Only when a replayable record exists.
            if (onReview != null) {
                StampChit(
                    label = s.reviewCta,
                    sublabel = s.reviewCtaSub,
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // NAYA KHEL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onNewGame)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.resultsNewGame, style = KursiType.title.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary, textAlign = TextAlign.Center)
                        Text("Reconfigure", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted, textAlign = TextAlign.Center)
                    }
                }
                // DAFTAR (HOME)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onHome)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.resultsHome, style = KursiType.title.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary, textAlign = TextAlign.Center)
                        Text("Main office", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted, textAlign = TextAlign.Center)
                    }
                }
            }
          }
        }
    }

        // ── Win moment overlay — KURSI HAASIL stamp on entry ────────────────────
        ActionMomentOverlay(
            host = momentHost,
            anchors = tableAnchors,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Centre-anchored geometry: the Win confetti + stamp land mid-screen. */
private fun resultsAnchors(): TableAnchors = TableAnchors(
    seatCenters = mapOf(0 to Offset(720f, 360f)),
    treasuryCenter = Offset(720f, 360f),
)

// ─────────────────────────── Verdict Roundel ──────────────────────────────────

@Composable
private fun VerdictRoundel(
    summary: MatchSummary,
    winnerColor: Color,
    stampColor: Color,
) {
    Box(contentAlignment = Alignment.Center) {
        // Large brass roundel
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(winnerColor.copy(alpha = 0.3f), BrandTokens.TeakDark.copy(alpha = 0.9f)),
                    ),
                )
                .border(
                    3.dp,
                    Brush.sweepGradient(listOf(stampColor, BrandTokens.BrassAged, stampColor)),
                    CircleShape,
                )
                .drawBehind {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = size.width * 0.48f
                    val rays = 16
                    for (i in 0 until rays) {
                        val angle = (i * 2 * PI / rays).toFloat()
                        drawLine(
                            stampColor.copy(alpha = 0.2f),
                            Offset(cx + r * 0.72f * cos(angle), cy + r * 0.72f * sin(angle)),
                            Offset(cx + r * 0.95f * cos(angle), cy + r * 0.95f * sin(angle)),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    drawCircle(stampColor.copy(alpha = 0.15f), r * 0.68f, Offset(cx, cy), style = Stroke(0.8.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = summary.winnerMonogram ?: "?",
                    style = KursiType.display.copy(fontSize = 28.sp),
                    color = winnerColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.winnerName ?: "Unknown",
                    style = KursiType.name.copy(fontSize = 12.sp),
                    color = KursiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Win stamp overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(stampColor.copy(alpha = 0.9f))
                .border(1.dp, stampColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                LocalKursiStrings.current.resultsWinStamp,
                style = KursiType.title.copy(fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = KursiNeutrals.Cream,
            )
        }
    }
}

// ─────────────────────────── ROZNAMCHA Recap ──────────────────────────────────

@Composable
private fun RoznamchaRecap(summary: MatchSummary) {
    val s = LocalKursiStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.04f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            s.resultsRecapHeader,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.3f), Color.Transparent)),
            ),
        )
        RecapRow(s.resultsRecapSurvived, "${summary.turnsTotal} turns")
        RecapRow(s.resultsRecapBluffsLanded, "${summary.bluffsHeld} bluffs held")
        RecapRow(s.resultsRecapBluffsCaught, "${summary.bluffsCaught} bluffs caught")
        // M6b — per-game decision-quality line (omitted when no gradeable decisions were recorded).
        summary.decisionSummary?.let { dq ->
            RecapRow(s.resultsRecapDecision, s.resultsDecisionValue(dq.accuracyPct, dq.avgEvLostPct))
        }

        if (summary.finalStandings.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(s.resultsRecapStandings, style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic), color = KursiNeutrals.TextMuted)
            summary.finalStandings.forEachIndexed { i, name ->
                Text(
                    "${i + 1}. $name${if (i == 0) s.resultsRecapWinnerSuffix else ""}",
                    style = KursiType.body.copy(fontSize = 11.sp),
                    color = if (i == 0) BrandTokens.GoldAntique else KursiNeutrals.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.3f), Color.Transparent)),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            s.resultsRecapSeal,
            style = KursiType.label.copy(fontSize = 10.sp, letterSpacing = 2.sp),
            color = BrandTokens.BrassAged.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────── Expired (cache-miss) empty state ────────────────

/**
 * Honest "record expired" state (M3 §4). Shown when the MatchSummary is gone (process death
 * cleared the in-memory store) — instead of a fabricated 0-turn certificate, we own the gap with
 * an in-character "file mislaid" notice and route the player back to a real action.
 */
@Composable
private fun ResultsExpired(
    onNewGame: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(32.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandTokens.PaperCream.copy(alpha = 0.05f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(28.dp)
                .semantics { contentDescription = "Match record expired" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Faded "RAD" (cancelled) stamp roundel
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(2.dp, BrandTokens.StampRed.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "RAD",
                    style = KursiType.display.copy(fontSize = 22.sp, letterSpacing = 3.sp),
                    color = BrandTokens.StampRed.copy(alpha = 0.6f),
                )
            }
            Text(
                "File band ho gayi",
                style = KursiType.title.copy(fontSize = 18.sp),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
            Text(
                "Is khel ka record ab daftar mein nahi mila — purana faisla guzar gaya. " +
                    "Koi naya record banaya nahi gaya; jo hua so hua.",
                style = KursiType.body.copy(fontSize = 13.sp),
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            StampChit(
                label = "NAYA KHEL",
                sublabel = "Start a fresh match",
                isHero = true,
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandTokens.TeakDark)
                    .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onHome)
                    .semantics { contentDescription = "Return to Daftar home" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("DAFTAR", style = KursiType.title.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary)
            }
        }
    }
}

@Composable
private fun RecapRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = KursiType.body.copy(fontSize = 11.sp),
            color = KursiNeutrals.TextSecondary,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        Text(
            value,
            style = KursiType.numeric.copy(fontSize = 11.sp),
            color = BrandTokens.GoldAntique,
        )
    }
}
