package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.narrative.ArcId
import com.kursi.shared.strings.LocalKursiStrings
import kotlin.random.Random

// ── Arc metadata ──────────────────────────────────────────────────────────────

private data class ArcMeta(
    val arcId: ArcId?,           // null = Free Darbar
    val code: String,            // ArcId.name or ""
    val title: String,
    val subtitle: String,
    val blurb: String,
    val glyph: String,
)

private val ARC_METAS = listOf(
    ArcMeta(
        arcId    = null,
        code     = "",
        title    = "FREE DARBAR",
        subtitle = "Koi agenda nahi — sab apni-apni chalenge",
        blurb    = "No lead arc. Bots will conspire freely. The table decides its own story.",
        glyph    = "⊙",
    ),
    ArcMeta(
        arcId    = ArcId.GATHBANDHAN,
        code     = ArcId.GATHBANDHAN.name,
        title    = "GATHBANDHAN",
        subtitle = "Coalition / Milkaa Hua Daftar",
        blurb    = "Rivals will whisper alliances. A coalition forms and fractures. You can join — or play both sides.",
        glyph    = "🤝",
    ),
    ArcMeta(
        arcId    = ArcId.AFWAAH,
        code     = ArcId.AFWAAH.name,
        title    = "AFWAAH",
        subtitle = "Rumour / Suni-Sunaayi Baat",
        blurb    = "Dangerous gossip spreads seat to seat. A target gets painted. Who started the rumour? You choose.",
        glyph    = "📣",
    ),
    ArcMeta(
        arcId    = ArcId.STING,
        code     = ArcId.STING.name,
        title    = "PHASAAO",
        subtitle = "Honeytrap / Sting Operation",
        blurb    = "Someone is being set up. Flattery, false friendship, a trap laid in plain sight. You can pull the strings — or cut them.",
        glyph    = "🪤",
    ),
    ArcMeta(
        arcId    = ArcId.BADLA,
        code     = ArcId.BADLA.name,
        title    = "BADLA",
        subtitle = "Vendetta / Hisaab Barabar",
        blurb    = "Old grudges surface. One bot has a score to settle and does not hide it. Join the vendetta or shield the target.",
        glyph    = "⚔",
    ),
)

/**
 * StoryScreen — KISSA hub in the License-Raj-Deco identity.
 *
 * Briefly explains narrative mode (bots chat, conspire, can be manipulated; four
 * lead arcs available). Shows arc selection cards + Free Darbar, a player-count
 * stepper, a difficulty quick-pick, and a start CTA.
 *
 * @param onBack       navigate back to Home.
 * @param onStart      (seed, players, difficulty, arcCode) → start a narrative game.
 */
@Composable
fun StoryScreen(
    onBack: () -> Unit,
    onStart: (seed: Long, players: Int, difficulty: Difficulty, arc: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val scroll = rememberScrollState()

    var selectedArcCode by remember { mutableStateOf("") }
    var playerCount by remember { mutableIntStateOf(4) }
    var difficulty by remember { mutableStateOf(Difficulty.Medium) }

    val difficultyMetas = listOf(
        Triple(Difficulty.Easy,        s.diffEasyName,        "NAYA BHARTI"),
        Triple(Difficulty.Medium,      s.diffMediumName,      "PERMANENT BABU"),
        Triple(Difficulty.Hard,        s.diffHardName,        "SECTION OFFICER"),
        Triple(Difficulty.Expert,      s.diffExpertName,      "HEAD CLERK SAAB"),
        Triple(Difficulty.Grandmaster, s.diffGrandmasterName, "CABINET SECRETARY"),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrandTokens.TeakInk),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.back,
                style = KursiType.body.copy(fontSize = 13.sp),
                color = BrandTokens.BrassAged,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = s.storyTitle,
                style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.3f))
                    .border(0.8.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "KISSA · DARBAR",
                    style = KursiType.caption.copy(fontSize = 8.sp),
                    color = KursiNeutrals.TextMuted,
                )
            }
        }

        // ── Scrollable body ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {

                // ── Tagline / explainer ───────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandTokens.BrassDark.copy(alpha = 0.22f))
                        .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = s.storyTagline,
                            style = KursiType.title.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
                            color = BrandTokens.GoldAntique,
                        )
                        Text(
                            text = "Bots will chat, conspire, and react to each other mid-game. You can read their whispers, " +
                                "placate rivals, fan grudges, or pull the whole table into one of four story arcs. " +
                                "Pick a lead arc below — or let the Darbar unfold freely.",
                            style = KursiType.body.copy(fontSize = 11.sp),
                            color = KursiNeutrals.TextSecondary,
                        )
                    }
                }

                // ── Arc section header ────────────────────────────────────────
                StoryFormSectionTitle(text = "KISSA KA DHARA — Choose your arc")

                // ── Arc cards ─────────────────────────────────────────────────
                ARC_METAS.forEach { meta ->
                    ArcCard(
                        meta = meta,
                        selected = selectedArcCode == meta.code,
                        onSelect = { selectedArcCode = meta.code },
                    )
                }

                // ── Player count ──────────────────────────────────────────────
                StoryFormSection(
                    label = "KITNE LOG",
                    sublabel = "How many seats at the Darbar table",
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("2", style = KursiType.caption, color = KursiNeutrals.TextMuted)
                            Text(
                                text = "⊙ $playerCount",
                                style = KursiType.title.copy(fontSize = 20.sp),
                                color = BrandTokens.GoldAntique,
                            )
                            Text("10", style = KursiType.caption, color = KursiNeutrals.TextMuted)
                        }
                        Slider(
                            value = playerCount.toFloat(),
                            onValueChange = { playerCount = it.toInt() },
                            valueRange = 2f..10f,
                            steps = 7,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandTokens.GoldAntique,
                                activeTrackColor = BrandTokens.BrassAged,
                                inactiveTrackColor = BrandTokens.BrassDark.copy(alpha = 0.4f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ── Difficulty quick-pick ─────────────────────────────────────
                StoryFormSection(
                    label = "DARBAR KA TAJURBA",
                    sublabel = "Opponent calibre",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        difficultyMetas.forEach { (tier, name, _) ->
                            val selected = difficulty == tier
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) BrandTokens.BrassAged.copy(alpha = 0.22f)
                                        else Color.Transparent,
                                    )
                                    .border(
                                        if (selected) 1.5.dp else 1.dp,
                                        if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { difficulty = tier }
                                    .semantics(mergeDescendants = true) {
                                        role = androidx.compose.ui.semantics.Role.RadioButton
                                        contentDescription = name
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = name,
                                    style = KursiType.caption.copy(
                                        fontSize = 9.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Footer CTA ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val arcLabel = ARC_METAS.firstOrNull { it.code == selectedArcCode }?.title ?: "FREE DARBAR"
            StampChit(
                label = s.storyStartCta,
                sublabel = "$arcLabel · ${playerCount}p · ${difficulty.name}",
                isHero = true,
                onClick = {
                    onStart(Random.nextLong(), playerCount, difficulty, selectedArcCode)
                },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── Arc card ────────────────────────────────────────

@Composable
private fun ArcCard(
    meta: ArcMeta,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) BrandTokens.GoldAntique.copy(alpha = 0.12f)
                else BrandTokens.PaperCream.copy(alpha = 0.05f),
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.RadioButton
                contentDescription = "${meta.title}. ${meta.subtitle}. ${meta.blurb}"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Glyph roundel
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                BrandTokens.BrassAged.copy(alpha = 0.30f),
                                BrandTokens.BrassDark.copy(alpha = 0.20f),
                            ),
                        ),
                    )
                    .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(meta.glyph, style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = meta.title,
                    style = KursiType.title.copy(fontSize = 14.sp, letterSpacing = 0.5.sp),
                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = meta.subtitle,
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextSecondary,
                )
                Text(
                    text = meta.blurb,
                    style = KursiType.body.copy(fontSize = 11.sp),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 2,
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.22f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        "CHUNA ✓",
                        style = KursiType.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Section helpers (local) ─────────────────────────

@Composable
private fun StoryFormSectionTitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        BrandTokens.BrassDark.copy(alpha = 0.4f),
                        BrandTokens.BrassAged.copy(alpha = 0.2f),
                        BrandTokens.BrassDark.copy(alpha = 0.1f),
                    ),
                ),
            )
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = KursiType.display.copy(fontSize = 13.sp, letterSpacing = 2.sp),
            color = BrandTokens.GoldAntique,
        )
    }
}

@Composable
private fun StoryFormSection(
    label: String,
    sublabel: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.06f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                text = label,
                style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = sublabel,
                style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.4f), Color.Transparent),
                    ),
                ),
        )
        content()
    }
}
