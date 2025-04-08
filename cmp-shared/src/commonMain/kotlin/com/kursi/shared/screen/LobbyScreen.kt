package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.kursi.ai.persona.BarkEvent
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.BotPersona
import com.kursi.ai.persona.PersonaAssigner
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.ActionMomentOverlay
import com.kursi.designsystem.moment.KursiMoment
import com.kursi.designsystem.moment.TableAnchors
import com.kursi.designsystem.moment.rememberMomentHost
import com.kursi.feature.game.Difficulty
import com.kursi.shared.strings.LocalKursiStrings

/**
 * LobbyScreen — Hazri Register (S3) from 17_app_plan.md §4.
 *
 * Shows the deterministic roster of bots for the given seed + difficulty.
 * "DOBARA BULAO" re-rolls (seed+1).
 * "MUHAR LAGAO" commits → Game.
 */
@Composable
fun LobbyScreen(
    seed: Long,
    players: Int,
    difficulty: Difficulty,
    onBack: () -> Unit,
    onDealIn: (seed: Long, players: Int, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    /** M5 pass-and-play: number of hot-seat humans (1 = vs-AI). Bots fill players - humanCount. */
    humanCount: Int = 1,
    /** M6e TEAM KHEL: number of teams (last-team-standing). < 2 = classic free-for-all. */
    teamCount: Int = 0,
) {
    var currentSeed by remember { mutableLongStateOf(seed) }
    val botDifficulty = remember(difficulty) { difficulty.toBotDifficulty() }
    val botCount = (players - humanCount).coerceAtLeast(0)

    val roster = remember(currentSeed, botCount, botDifficulty) {
        if (botCount <= 0) emptyList()
        else PersonaAssigner.assign(
            seatCount = botCount.coerceIn(1, 9),
            difficulty = botDifficulty,
            seed = currentSeed,
        )
    }

    val scrollState = rememberScrollState()

    // ── MUHAR LAGAO stamp moment ──────────────────────────────────────────────
    // Pressing commit plays a centred RubberStamp moment over the register, then
    // navigates into the Game once the moment finishes (host goes idle).
    val momentHost = rememberMomentHost()
    var committing by remember { mutableStateOf(false) }
    val tableAnchors = remember { centeredAnchors() }

    LaunchedEffect(committing, momentHost.isActive) {
        if (committing && !momentHost.isActive) {
            committing = false
            onDealIn(currentSeed, players, difficulty)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk)) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Header
        LobbyHeader(seed = currentSeed, onBack = onBack)

        // Attendance register — centred on both axes so the register reads as a bound
        // ledger page floating on the teak desk, instead of stretching edge-to-edge on
        // wide windows or top-anchoring with a dead band above the footer. The outer
        // column scrolls (content may exceed the viewport at 9 bots); when the roster
        // fits, Arrangement.Center settles the ledger in the optical middle of the canvas.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          Column(
            modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            val ls = LocalKursiStrings.current
            // Register title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.25f))
                    .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        ls.lobbyHeader,
                        style = KursiType.title.copy(fontSize = 14.sp, letterSpacing = 1.sp),
                        color = BrandTokens.GoldAntique,
                    )
                    Text(
                        "Seed #$currentSeed · ${players}p · ${difficulty.name}",
                        style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextMuted,
                    )
                }
            }

            // M6e TEAM KHEL — alternating seat→team split (seat i → team i % teamCount). The human (seat
            // 0) anchors team 0; bots fill seats humanCount.. so their seat index is humanCount + i.
            val teams = teamCount >= 2
            if (teams) {
                Text(
                    text = ls.lobbyTeamHeader,
                    style = KursiType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.GoldAntique,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Human seat (fixed, seat 0)
            PersonaRegisterRow(
                name = ls.humanSeatName,
                title = ls.humanSeatTitle,
                archetype = ls.humanSeatArchetype,
                monogram = "AAP",
                seatColor = Color(0xFFE63946),
                personalityLine = ls.humanSeatPersonality,
                isHuman = true,
                sampleBark = ls.humanSeatBark,
                teamBadge = if (teams) ls.teamBadge(0 % teamCount) else null,
                teamId = if (teams) 0 % teamCount else -1,
            )

            // Bot seats (seat = humanCount + index)
            roster.forEachIndexed { index, (persona, _) ->
                val personalityLine = buildPersonalityLine(persona)
                val sampleBark = persona.barks.lines[BarkEvent.ACT]?.firstOrNull() ?: ""
                val seatIdx = humanCount + index
                val tId = if (teams) seatIdx % teamCount else -1
                PersonaRegisterRow(
                    name = persona.name,
                    title = persona.title,
                    archetype = persona.archetype,
                    monogram = persona.monogram,
                    seatColor = Color(persona.seatColorArgb),
                    personalityLine = personalityLine,
                    sampleBark = sampleBark,
                    teamBadge = if (teams) ls.teamBadge(tId) else null,
                    teamId = tId,
                )
            }
          }
        }

        // Footer with re-roll + commit
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val lb = LocalKursiStrings.current
                // Reroll button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { currentSeed += 1L }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = lb.lobbyReroll,
                        style = KursiType.title.copy(fontSize = 13.sp),
                        color = KursiNeutrals.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                // Deal In commit
                StampChit(
                    label = lb.lobbyDealIn,
                    sublabel = lb.lobbyDealInSub,
                    isHero = true,
                    onClick = {
                        if (!committing) {
                            committing = true
                            momentHost.play(
                                KursiMoment.Tax(actorSeat = 0, roleHue = BrandTokens.GoldAntique),
                            )
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }
    }

        // ── Stamp moment overlay — above the register, plays MUHAR LAGAO ─────────
        ActionMomentOverlay(
            host = momentHost,
            anchors = tableAnchors,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Anchors that drive every moment to the screen centre — no live table to measure here. */
private fun centeredAnchors(): TableAnchors = TableAnchors(
    seatCenters = mapOf(0 to Offset(720f, 450f)),
    treasuryCenter = Offset(720f, 450f),
)

// ─────────────────────────── Helpers ─────────────────────────────────────────

private fun Difficulty.toBotDifficulty(): BotDifficulty = when (this) {
    Difficulty.Easy        -> BotDifficulty.EASY
    Difficulty.Medium      -> BotDifficulty.MEDIUM
    Difficulty.Hard        -> BotDifficulty.HARD
    Difficulty.Expert      -> BotDifficulty.EXPERT
    Difficulty.Grandmaster -> BotDifficulty.GRANDMASTER
}

private fun buildPersonalityLine(persona: BotPersona): String {
    val p = persona.personality
    val bluffStr = "bluffs ${(p.bluffRate * 100).toInt()}%"
    val challengeStr = when {
        p.challengeAggression > 0.6f -> "challenge-hungry"
        p.challengeAggression < 0.3f -> "lets things slide"
        else -> "moderate challenger"
    }
    val targeting = when (p.targetingBias.name) {
        "LEADER" -> "aankh leader pe"
        "WEAKEST" -> "targets weakest"
        "VINDICTIVE" -> "yaad rakhta hai"
        "RANDOM" -> "unpredictable"
        else -> p.targetingBias.name.lowercase()
    }
    return "$bluffStr · $challengeStr · $targeting"
}

// ─────────────────────────── Register Row ─────────────────────────────────────

@Composable
private fun PersonaRegisterRow(
    name: String,
    title: String,
    archetype: String,
    monogram: String,
    seatColor: Color,
    personalityLine: String,
    isHuman: Boolean = false,
    sampleBark: String = "",
    /** M6e TEAM KHEL: faction label + id for the seat, or null in classic free-for-all. */
    teamBadge: String? = null,
    teamId: Int = -1,
) {
    var flipped by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isHuman) BrandTokens.BrassAged.copy(alpha = 0.12f)
                else Color(0xFF14110D),
            )
            .border(
                1.dp,
                if (isHuman) BrandTokens.GoldAntique.copy(alpha = 0.5f)
                else seatColor.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp),
            )
            .clickable { flipped = !flipped }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (flipped && sampleBark.isNotEmpty()) {
            // Flipped — show sample bark
            Column {
                Text(
                    "\"$sampleBark\"",
                    style = KursiType.body.copy(fontStyle = FontStyle.Italic, fontSize = 14.sp),
                    color = KursiNeutrals.TextPrimary,
                )
                Text(
                    "— $name  (tap to flip back)",
                    style = KursiType.caption.copy(fontSize = 10.sp),
                    color = KursiNeutrals.TextMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Monogram roundel
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(seatColor.copy(alpha = 0.9f), seatColor.copy(alpha = 0.4f))))
                        .border(1.5.dp, BrandTokens.BrassAged.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        monogram,
                        style = KursiType.name.copy(fontSize = 13.sp),
                        color = KursiNeutrals.Cream,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = KursiType.name.copy(fontSize = 14.sp),
                            color = if (isHuman) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
                            fontWeight = if (isHuman) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            "· $archetype",
                            style = KursiType.caption.copy(fontSize = 9.sp),
                            color = KursiNeutrals.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (teamBadge != null) {
                            val teamHue = if (teamId == 0) BrandTokens.GoldAntique else BrandTokens.StampRed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(teamHue.copy(alpha = 0.16f))
                                    .border(0.7.dp, teamHue.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(teamBadge, style = KursiType.caption.copy(fontSize = 7.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = teamHue)
                            }
                        }
                    }
                    Text(
                        title,
                        style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextSecondary,
                        maxLines = 1,
                    )
                    Text(
                        personalityLine,
                        style = KursiType.caption.copy(fontSize = 9.sp),
                        color = KursiNeutrals.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    "tap →",
                    style = KursiType.caption.copy(fontSize = 9.sp),
                    color = BrandTokens.BrassDark.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun LobbyHeader(seed: Long, onBack: () -> Unit) {
    val s = LocalKursiStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandTokens.TeakDark)
            .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            s.back,
            style = KursiType.body.copy(fontSize = 13.sp),
            color = BrandTokens.BrassAged,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.weight(1f))
        Text(
            s.lobbyHeader,
            style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp),
            color = KursiNeutrals.TextPrimary,
        )
        Spacer(Modifier.weight(1f))
    }
}
