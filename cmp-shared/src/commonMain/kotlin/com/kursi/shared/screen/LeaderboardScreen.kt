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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.DailyStanding
import com.kursi.core.prefs.RankedStanding
import com.kursi.core.prefs.SarkariRank
import com.kursi.designsystem.*
import com.kursi.shared.strings.LocalKursiStrings
import com.kursi.shared.strings.rankName

/**
 * M6d — Leaderboard / Darja-Suchi: the LOCAL standings screen. Shows the ranked ELO standing (rating
 * + sarkari rank tier + rating-history spark-line), best results, and the daily-challenge streak.
 * Server-backed standings are deferred to the M7 online tie-in; this reads only persisted AppPrefs
 * behind a clean seam (the footer notes the pending online table).
 */
@Composable
fun LeaderboardScreen(
    ranked: RankedStanding,
    daily: DailyStanding,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * M7 ONLINE TIE-IN — the live online-standings seam. When non-null it renders an ONLINE
     * DARJA-SUCHI card reading the server-backed rows; when the list is empty it shows the connected
     * "ranking is forming" sub-line. Null keeps the original local-only screen with the pending footer.
     */
    onlineStandings: OnlineStandings? = null,
) {
    val s = LocalKursiStrings.current
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
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onBack)
                        .semantics { contentDescription = "Back to home" }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(s.leaderboardBack, style = KursiType.title.copy(fontSize = 13.sp), color = KursiNeutrals.TextPrimary)
                }
                Text(
                    s.leaderboardHeader,
                    style = KursiType.title.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
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
                    if (ranked.isProvisional && !daily.hasPlayed) {
                        LeaderboardEmpty()
                    } else {
                        RankPlaque(ranked)
                        RatingHistoryCard(ranked)
                        if (daily.hasPlayed) DailyStandingCard(daily)
                    }
                    // M7 — the live online standings seam. Shown only when connected to a server;
                    // otherwise the local screen falls back to the honest "pending" footer below.
                    if (onlineStandings != null) {
                        OnlineStandingsCard(onlineStandings)
                    } else {
                        Text(
                            s.leaderboardServerPending,
                            style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                            color = KursiNeutrals.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardEmpty() {
    val s = LocalKursiStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.05f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(28.dp)
            .semantics { contentDescription = s.leaderboardEmpty },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            s.leaderboardEmpty,
            style = KursiType.body.copy(fontSize = 12.sp),
            color = KursiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The headline rank plaque: rating roundel + sarkari rank name + a tier-progress bar. */
@Composable
private fun RankPlaque(ranked: RankedStanding) {
    val s = LocalKursiStrings.current
    val rank = ranked.rank
    val rankColor = rankColor(rank)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BrandTokens.BrassDark.copy(alpha = 0.5f), BrandTokens.TeakDark.copy(alpha = 0.7f)),
                ),
            )
            .border(1.5.dp, rankColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(20.dp)
            .semantics {
                contentDescription =
                    "Rank ${s.rankName(rank)}, rating ${ranked.rating}, peak ${ranked.peak}, over ${ranked.games} ranked games."
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            s.leaderboardRankSection,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.14f))
                    .border(2.dp, rankColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${ranked.rating}", style = KursiType.display.copy(fontSize = 22.sp), color = rankColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s.rankName(rank),
                    style = KursiType.title.copy(fontSize = 18.sp, letterSpacing = 0.5.sp),
                    color = KursiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val nextHint = rank.next?.let { nx ->
                    s.rankedToNext((nx.floor - ranked.rating).coerceAtLeast(0), s.rankName(nx))
                } ?: s.rankedTopTier
                Text(
                    nextHint,
                    style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextSecondary,
                )
            }
        }
        // Tier-progress bar
        TierProgressBar(progress = ranked.tierProgress, color = rankColor)
        // Peak / games row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(s.leaderboardPeakLabel, "${ranked.peak}", Modifier.weight(1f))
            MiniStat(s.leaderboardGamesLabel, "${ranked.games}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun TierProgressBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BrandTokens.TeakInk.copy(alpha = 0.6f))
            .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))),
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.05f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(14.dp)
            .semantics { contentDescription = "$label $value" },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = KursiType.numeric.copy(fontSize = 20.sp), color = BrandTokens.GoldAntique)
            Text(label, style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted, textAlign = TextAlign.Center)
        }
    }
}

/** Rating spark-line over the retained history window. */
@Composable
private fun RatingHistoryCard(ranked: RankedStanding) {
    val s = LocalKursiStrings.current
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
            s.leaderboardHistorySection,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        val pts = ranked.history
        if (pts.size < 2) {
            Text(
                s.leaderboardHistoryEmpty,
                style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextSecondary,
            )
        } else {
            RatingSparkline(
                points = pts,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .semantics {
                        contentDescription =
                            "Rating history: ${pts.size} points, from ${pts.first()} to ${pts.last()}."
                    },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${pts.min()}", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
                Text("${pts.max()}", style = KursiType.caption.copy(fontSize = 9.sp), color = BrandTokens.GoldAntique)
            }
        }
    }
}

@Composable
private fun RatingSparkline(points: List<Int>, modifier: Modifier = Modifier) {
    val lo = points.min()
    val hi = points.max()
    val span = (hi - lo).coerceAtLeast(1)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BrandTokens.TeakInk.copy(alpha = 0.5f))
            .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (points.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val stepX = w / (points.size - 1)
            fun yAt(v: Int): Float = h - ((v - lo).toFloat() / span) * h
            // Baseline rule
            drawLine(
                color = BrandTokens.BrassDark.copy(alpha = 0.3f),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 1f,
            )
            // Spark-line
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = BrandTokens.GoldAntique,
                    start = Offset(i * stepX, yAt(points[i])),
                    end = Offset((i + 1) * stepX, yAt(points[i + 1])),
                    strokeWidth = 2f,
                )
            }
            // Last point marker
            val lastX = (points.size - 1) * stepX
            drawCircle(
                color = BrandTokens.GoldAntique,
                radius = 3f,
                center = Offset(lastX, yAt(points.last())),
            )
            drawCircle(
                color = BrandTokens.GoldAntique,
                radius = 5f,
                center = Offset(lastX, yAt(points.last())),
                style = Stroke(width = 1f),
            )
        }
    }
}

/** The daily-challenge standing card: streak + best streak + lifetime daily wins. */
@Composable
private fun DailyStandingCard(daily: DailyStanding) {
    val s = LocalKursiStrings.current
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
            s.leaderboardDailySection,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(s.leaderboardStreakLabel, "${daily.streak}", Modifier.weight(1f))
            MiniStat(s.leaderboardBestStreakLabel, "${daily.bestStreak}", Modifier.weight(1f))
            MiniStat(s.leaderboardDailyWinsLabel, "${daily.won}/${daily.played}", Modifier.weight(1f))
        }
    }
}

// ─────────────────────────── M7 online standings seam ─────────────────────────

/**
 * The server-backed online standings, surfaced when the app is connected to a Kursi server. This is the
 * clean SEAM the M6d footer promised: the screen reads these rows when present and otherwise stays the
 * local-only standings. The server today has no standings endpoint, so the app supplies [rows] from the
 * live ranked ladder (the player's own standing) and flips [connected] off when no server is reachable —
 * the moment the server gains a `/standings` route, only the data source changes, not this screen.
 *
 * @param connected whether a server connection backs these rows right now.
 * @param rows      the standings rows, best-first (empty while the online ranking is still forming).
 */
data class OnlineStandings(
    val connected: Boolean,
    val rows: List<OnlineStandingRow> = emptyList(),
)

/** One online-standings row: a rank position, a display name, and a rating. */
data class OnlineStandingRow(
    val position: Int,
    val name: String,
    val rating: Int,
    /** True for the local player's own row (highlighted). */
    val isMe: Boolean = false,
)

@Composable
private fun OnlineStandingsCard(standings: OnlineStandings) {
    val s = LocalKursiStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.04f))
            .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = if (standings.connected) s.leaderboardOnlineConnectedSub else s.leaderboardOnlineOfflineSub
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(10.dp).clip(CircleShape)
                    .background(if (standings.connected) KursiSemantics.Success else BrandTokens.BrassDark),
            )
            Text(
                s.leaderboardOnlineSection,
                style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.GoldAntique,
            )
        }
        Text(
            if (standings.connected) s.leaderboardOnlineConnectedSub else s.leaderboardOnlineOfflineSub,
            style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
            color = KursiNeutrals.TextSecondary,
        )
        if (standings.rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                standings.rows.forEach { row -> OnlineStandingRowView(row) }
            }
        }
    }
}

@Composable
private fun OnlineStandingRowView(row: OnlineStandingRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (row.isMe) BrandTokens.GoldAntique.copy(alpha = 0.12f) else BrandTokens.TeakDark.copy(alpha = 0.5f))
            .border(1.dp, if (row.isMe) BrandTokens.GoldAntique.copy(alpha = 0.6f) else BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "${row.position}. ${row.name}, rating ${row.rating}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("#${row.position}", style = KursiType.numeric.copy(fontSize = 12.sp), color = BrandTokens.BrassAged)
        Text(
            row.name,
            style = KursiType.name.copy(fontSize = 13.sp),
            color = if (row.isMe) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${row.rating}", style = KursiType.numeric.copy(fontSize = 13.sp), color = BrandTokens.GoldAntique)
    }
}

/** Per-tier accent colour — climbs from brass through gold to the success green at the top. */
private fun rankColor(rank: SarkariRank): Color = when (rank) {
    SarkariRank.CLERK -> BrandTokens.BrassAged
    SarkariRank.SECTION_OFFICER -> BrandTokens.BrassAged
    SarkariRank.UNDER_SECRETARY -> BrandTokens.GoldAntique
    SarkariRank.DEPUTY_SECRETARY -> BrandTokens.GoldAntique
    SarkariRank.JOINT_SECRETARY -> BrandTokens.GoldAntique
    SarkariRank.SECRETARY -> KursiSemantics.Success
    SarkariRank.CABINET_SECRETARY -> KursiSemantics.Success
}

// ─────────────────────────── Home strip + daily entry (reused) ────────────────

/**
 * Compact ranked strip for the Home hub — a single-line rank + rating summary that taps through to
 * the [LeaderboardScreen]. Always rendered (even provisional) so the player sees their starting rank.
 */
@Composable
fun RankedStrip(
    ranked: RankedStanding,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val rank = ranked.rank
    val rankColor = rankColor(rank)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.TeakDark.copy(alpha = 0.7f))
            .border(1.dp, rankColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription =
                    "Rank ${s.rankName(rank)}, rating ${ranked.rating}. Tap to open standings."
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.14f))
                    .border(1.5.dp, rankColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${ranked.rating}", style = KursiType.numeric.copy(fontSize = 11.sp), color = rankColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        s.rankedStripTag,
                        style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.BrassAged,
                    )
                    Text(
                        s.rankName(rank),
                        style = KursiType.title.copy(fontSize = 13.sp),
                        color = KursiNeutrals.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val sub = if (ranked.isProvisional) {
                    s.rankedProvisional
                } else rank.next?.let { nx ->
                    s.rankedToNext((nx.floor - ranked.rating).coerceAtLeast(0), s.rankName(nx))
                } ?: s.rankedTopTier
                Text(
                    sub,
                    style = KursiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = KursiType.title.copy(fontSize = 16.sp), color = KursiNeutrals.TextMuted)
        }
    }
}

/**
 * Home "Aaj ki Chunauti" daily-challenge entry strip. Shows whether today's daily is still open (with
 * a stamp badge) and the current streak. Tapping starts (or, when done, re-opens) today's challenge.
 */
@Composable
fun DailyChallengeStrip(
    daily: DailyStanding,
    todayDone: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val accent = if (todayDone) BrandTokens.BrassAged else BrandTokens.StampRed
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.TeakDark.copy(alpha = 0.7f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onStart)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    if (todayDone) "${s.dailyCta}. ${s.dailyDoneSub}" else "${s.dailyCta}. ${s.dailyCtaSub}"
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✦", style = KursiType.title.copy(fontSize = 16.sp), color = accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(s.dailyCta, style = KursiType.title.copy(fontSize = 14.sp), color = KursiNeutrals.TextPrimary)
                Text(
                    if (todayDone) s.dailyDoneSub else s.dailyCtaSub,
                    style = KursiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (daily.streak > 0) {
                    Text(
                        s.dailyStreakLabel(daily.streak),
                        style = KursiType.caption.copy(fontSize = 9.sp),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }
            // Stamp badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    if (todayDone) s.dailyDoneBadge else s.dailyOpenBadge,
                    style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }
        }
    }
}
