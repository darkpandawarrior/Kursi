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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.GauntletProgress
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GauntletLadder
import com.kursi.feature.game.GauntletRung
import com.kursi.shared.strings.KursiStrings
import com.kursi.shared.strings.LocalKursiStrings

/**
 * M6e — GAUNTLET / Tarakki ki Seedhi: the escalating promotion ladder. A vertical bracket of five
 * difficulty rungs (Easy → Grandmaster); the player beats one to be promoted to the next. The current
 * target rung is highlighted with a Start CTA; cleared rungs carry a stamp and a replay action; locked
 * rungs are dimmed. Progress comes from [com.kursi.core.prefs.GauntletProgress] (persisted in AppPrefs).
 *
 * [onPlayRung] routes to a gauntlet match for the given 0-based rung index (the app tags the Game route
 * with the rung so game-over records the result + promotes on a win).
 */
@Composable
fun GauntletScreen(
    progress: GauntletProgress,
    onPlayRung: (rungIndex: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val scroll = rememberScrollState()
    val rungs = GauntletLadder.RUNGS
    val targetIndex = progress.targetRung.coerceIn(0, rungs.lastIndex)
    val conquered = progress.isConquered(rungs.lastIndex)

    Box(modifier = modifier.fillMaxSize().background(BrandTokens.TeakInk)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──
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
                        .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .clickable(onClick = onBack)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = s.back, style = KursiType.body.copy(fontSize = 13.sp), color = BrandTokens.BrassAged)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = s.gauntletHeader,
                    style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp).rozha(),
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
                    Text(s.gauntletBadge, style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Tagline + progress
                    Text(
                        text = s.gauntletTagline,
                        style = KursiType.body.copy(fontSize = 13.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Text(
                        text = s.gauntletStripLabel(progress.clearedCount.coerceIn(0, rungs.size), rungs.size),
                        style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.GoldAntique,
                    )

                    if (conquered) {
                        ConqueredBanner(title = s.gauntletConqueredTitle, body = s.gauntletConqueredBody)
                    }

                    Spacer(Modifier.height(4.dp))

                    // The ladder, top rung first (most prestigious at the top so it reads as a climb).
                    rungs.asReversed().forEach { rung ->
                        val state = when {
                            rung.index <= progress.clearedRung -> RungState.CLEARED
                            rung.index == targetIndex && !conquered -> RungState.CURRENT
                            else -> RungState.LOCKED
                        }
                        GauntletRungCard(
                            rung = rung,
                            state = state,
                            strings = s,
                            onPlay = { onPlayRung(rung.index) },
                        )
                    }
                }
            }
        }
    }
}

private enum class RungState { CLEARED, CURRENT, LOCKED }

@Composable
private fun ConqueredBanner(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandTokens.GoldAntique.copy(alpha = 0.16f))
            .border(1.5.dp, BrandTokens.GoldAntique, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = KursiType.title.copy(fontSize = 16.sp).rozha(), color = BrandTokens.GoldAntique)
            Text(body, style = KursiType.body.copy(fontSize = 12.sp), color = KursiNeutrals.TextSecondary)
        }
    }
}

@Composable
private fun GauntletRungCard(
    rung: GauntletRung,
    state: RungState,
    strings: KursiStrings,
    onPlay: () -> Unit,
) {
    val nameplate = rungNameOf(rung.index, strings)
    val difficultyName = difficultyNameOf(rung.difficulty, strings)
    val locked = state == RungState.LOCKED
    val current = state == RungState.CURRENT
    val cleared = state == RungState.CLEARED

    val borderColor = when {
        current -> BrandTokens.GoldAntique
        cleared -> BrandTokens.BrassAged.copy(alpha = 0.7f)
        else -> BrandTokens.BrassDark.copy(alpha = 0.35f)
    }
    val bg = when {
        current -> BrandTokens.GoldAntique.copy(alpha = 0.14f)
        cleared -> BrandTokens.BrassAged.copy(alpha = 0.10f)
        else -> BrandTokens.TeakDark.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(if (current) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (!locked) Modifier.clickable(onClick = onPlay) else Modifier)
            .semantics(mergeDescendants = true) {
                if (!locked) role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "$nameplate. $difficultyName. ${rung.players} seats. " + when (state) {
                    RungState.CURRENT -> strings.gauntletCurrentTag
                    RungState.CLEARED -> strings.gauntletClearedTag
                    RungState.LOCKED -> strings.gauntletLockedTag
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .alpha(if (locked) 0.55f else 1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Rung numeral roundel
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (cleared || current) Brush.radialGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark))
                        else Brush.radialGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.4f), BrandTokens.TeakDark)),
                    )
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (cleared) "✓" else "${rung.index + 1}",
                    style = KursiType.title.copy(fontSize = 18.sp).rozha(),
                    color = if (cleared || current) BrandTokens.TeakDark else KursiNeutrals.TextMuted,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameplate,
                    style = KursiType.name.copy(fontSize = 15.sp, letterSpacing = 0.5.sp),
                    color = if (current) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$difficultyName · ${rung.players} kursiyaan",
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // State badge
            val tag = when (state) {
                RungState.CURRENT -> strings.gauntletCurrentTag
                RungState.CLEARED -> strings.gauntletClearedTag
                RungState.LOCKED -> strings.gauntletLockedTag
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (state) {
                            RungState.CURRENT -> BrandTokens.GoldAntique.copy(alpha = 0.18f)
                            RungState.CLEARED -> BrandTokens.BrassAged.copy(alpha = 0.18f)
                            RungState.LOCKED -> BrandTokens.StampRed.copy(alpha = 0.10f)
                        },
                    )
                    .border(
                        0.7.dp,
                        when (state) {
                            RungState.CURRENT -> BrandTokens.GoldAntique.copy(alpha = 0.7f)
                            RungState.CLEARED -> BrandTokens.BrassAged.copy(alpha = 0.6f)
                            RungState.LOCKED -> BrandTokens.StampRed.copy(alpha = 0.4f)
                        },
                        RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = tag,
                    style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                    color = when (state) {
                        RungState.CURRENT -> BrandTokens.GoldAntique
                        RungState.CLEARED -> BrandTokens.BrassAged
                        RungState.LOCKED -> BrandTokens.StampRed.copy(alpha = 0.75f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // CTA row (only for reachable rungs)
        if (current || cleared) {
            StampChit(
                label = if (current) strings.gauntletStartCta else strings.gauntletReplayCta,
                sublabel = if (current) strings.gauntletStartCtaSub else null,
                isHero = current,
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun rungNameOf(index: Int, s: KursiStrings): String = when (index) {
    0 -> s.gauntletRung0Name
    1 -> s.gauntletRung1Name
    2 -> s.gauntletRung2Name
    3 -> s.gauntletRung3Name
    else -> s.gauntletRung4Name
}

private fun difficultyNameOf(d: Difficulty, s: KursiStrings): String = when (d) {
    Difficulty.Easy -> s.diffEasyName
    Difficulty.Medium -> s.diffMediumName
    Difficulty.Hard -> s.diffHardName
    Difficulty.Expert -> s.diffExpertName
    Difficulty.Grandmaster -> s.diffGrandmasterName
}
