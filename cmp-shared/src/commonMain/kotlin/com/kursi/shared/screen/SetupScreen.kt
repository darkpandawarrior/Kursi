package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.MatchPreset
import com.kursi.feature.game.DraftPresets
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.shared.strings.LocalKursiStrings
import kotlin.random.Random

// ── Difficulty descriptors ────────────────────────────────────────────────────

private data class DifficultyMeta(
    val tier: Difficulty,
    val nameplate: String,
    val voiceLine: String,
)

// ── Mode descriptors ──────────────────────────────────────────────────────────

private data class ModeMeta(
    val id: String,
    val label: String,
    val sublabel: String,
    val available: Boolean,
)

/** The two live modes a player can actually start. */
private enum class PlayMode { VS_AI, PASS_AND_PLAY }

/**
 * SetupScreen — Requisition Form (S2) from 17_app_plan.md §4.
 *
 * A cream certificate that fills as you choose:
 * - FORM 1-A: mode (vs AI selected; others MUHAR PENDING disabled)
 * - FORM 1-B: player count 2–10 (brass abacus slider)
 * - FORM 1-C: difficulty (4 enamel tabs with in-voice descriptions)
 *
 * onNext(seed) → Lobby(seed)
 */
@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onNext: (seed: Long, players: Int, difficulty: Difficulty, humanCount: Int, teamCount: Int, narrative: Boolean, anarchy: Boolean, draftCode: String) -> Unit,
    initialPlayers: Int = 4,
    initialDifficulty: Difficulty = Difficulty.Medium,
    /**
     * M5 ONBOARD — start a match immediately from a fully-resolved config, bypassing the form
     * (used by QUICK-MATCH and the named persona-lineup presets). Defaults to the same handler as
     * [onNext] so a fresh seed is generated; the app layer overrides it to route through the Lobby.
     */
    onStartPreset: (seed: Long, players: Int, difficulty: Difficulty) -> Unit =
        { seed, players, difficulty -> onNext(seed, players, difficulty, 1, 0, false, false, "") },
    /**
     * M7 ONLINE — the three online modes (PRIVATE KAMRA / KHULI BOLI / EK HI LAN) now route into the
     * Online Mehfil hub instead of sitting "JALD AANE WAALA". Defaults to a no-op so older call sites
     * (and the static screenshot harness) compile unchanged.
     */
    onOnline: () -> Unit = {},
    /**
     * Seeds the Team Khel (1-D) toggle ON at first composition. Defaults OFF for the live app (the
     * player opts in); the static screenshot harness sets it true so the captured `setup_teams` shot
     * actually demonstrates the team-assignment row in its enabled state. Only takes effect while the
     * row is eligible (vs-AI, >= 3 seats); ignored otherwise, exactly like a user tap would be.
     */
    initialTeamPlay: Boolean = false,
    /**
     * Vertical scroll state for the form body. Injectable so the screenshot harness can render a first
     * pass, scroll the team row into view, and render a second pass (a static [ImageComposeScene] does
     * not pump a scroll animation). Live call sites get a fresh [rememberScrollState].
     */
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    var playerCount by remember { mutableIntStateOf(initialPlayers) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    var playMode by remember { mutableStateOf(PlayMode.VS_AI) }
    // Hot-seat human count (pass-and-play only). Always < playerCount so at least one bot can fill in;
    // clamped whenever playerCount shrinks below the chosen human count.
    var humanCount by remember { mutableIntStateOf(2) }
    if (humanCount > playerCount) humanCount = playerCount
    // M6e TEAM KHEL: when on, the table splits into two teams (last-team-standing). Surfaced only in
    // vs-AI mode (teams + hot-seat humans is deferred) and needs >= 3 seats for a meaningful 2v(N-2).
    var teamPlay by remember { mutableStateOf(initialTeamPlay) }
    val teamEligible = playMode == PlayMode.VS_AI && playerCount >= 3
    if (!teamEligible) teamPlay = false

    // Narrative (Darbar / Kissa) toggle — vs-AI only
    var narrativeEnabled by remember { mutableStateOf(false) }
    val narrativeEligible = playMode == PlayMode.VS_AI
    if (!narrativeEligible) narrativeEnabled = false

    // Anarchy (Andher Nagari) toggle — vs-AI only
    var anarchyEnabled by remember { mutableStateOf(false) }
    val anarchyEligible = playMode == PlayMode.VS_AI
    if (!anarchyEligible) anarchyEnabled = false

    // Draft preset picker — vs-AI only; "" = classic (no draft)
    var selectedDraftCode by remember { mutableStateOf("") }
    val draftEligible = playMode == PlayMode.VS_AI

    val difficultyMeta = listOf(
        DifficultyMeta(Difficulty.Easy,   s.diffEasyName,   s.diffEasyVoice),
        DifficultyMeta(Difficulty.Medium, s.diffMediumName, s.diffMediumVoice),
        DifficultyMeta(Difficulty.Hard,   s.diffHardName,   s.diffHardVoice),
        DifficultyMeta(Difficulty.Expert, s.diffExpertName, s.diffExpertVoice),
        DifficultyMeta(Difficulty.Grandmaster, s.diffGrandmasterName, s.diffGrandmasterVoice),
    )
    // M7 — all five modes are now LIVE: vs-AI + pass-and-play run locally; the three online modes
    // (PRIVATE KAMRA / KHULI BOLI / EK HI LAN) route into the Online Mehfil hub (onOnline).
    val modes = listOf(
        ModeMeta("vs_ai",   s.modeAILabel,      s.modeAISub,      true),
        ModeMeta("local",   s.modeLocalLabel,    s.modeLocalSub,   true),
        ModeMeta("private", s.modePrivateLabel,  s.modePrivateSub, true),
        ModeMeta("open",    s.modeOpenLabel,     s.modeOpenSub,    true),
        ModeMeta("lan",     s.modeLanLabel,      s.modeLanSub,     true),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrandTokens.TeakInk),
    ) {
        // Header bar
        SetupHeader(onBack = onBack)

        // Scrollable form body — centred certificate column so the form reads as a
        // requisition slip rather than a full-bleed banner on wide desktop windows.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                // Asymmetric bottom padding so the last FORM 1-C card (NAYA BHARTI →
                // HEAD CLERK SAAB) clears the sticky AAGE BADHO footer instead of being
                // clipped behind it.
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            // Form title
            FormSectionTitle(text = s.setupFormTitle)

            // ── M5 ONBOARD: Quick-match + curated presets ─────────────────────
            // A one-tap "start from my saved defaults" hero, plus three named persona-lineup
            // requisitions that pre-fill the whole form. All route through onStartPreset so the
            // deal stays deterministic and flows through the same Lobby → Game path.
            QuickMatchChit(
                label = s.setupQuickMatchLabel,
                sublabel = s.setupQuickMatchSub,
                onClick = { onStartPreset(Random.nextLong(), initialPlayers, initialDifficulty) },
            )

            FormSection(
                label = s.setupPresetSectionLabel,
                sublabel = s.setupPresetSectionSub,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presetMeta = listOf(
                        Triple(MatchPreset.CLASSIC_CABINET, s.presetCabinetName, s.presetCabinetSub),
                        Triple(MatchPreset.SNAKE_PIT,       s.presetSnakePitName, s.presetSnakePitSub),
                        Triple(MatchPreset.CHAOS_TEN,       s.presetChaosName,    s.presetChaosSub),
                    )
                    presetMeta.forEach { (preset, name, sub) ->
                        PresetChit(
                            name = name,
                            sublabel = sub,
                            stamp = s.setupPresetRequisition,
                            lineupMonograms = preset.lineup().map { it.monogram },
                            onClick = {
                                onStartPreset(preset.seed, preset.playerCount, preset.difficulty.toUiDifficulty())
                            },
                        )
                    }
                }
            }

            // ── 1-A: Mode ─────────────────────────────────────────────────────
            FormSection(
                label = s.setupModeLabel,
                sublabel = s.setupModeSublabel,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { mode ->
                        val selected = when (mode.id) {
                            "vs_ai" -> playMode == PlayMode.VS_AI
                            "local" -> playMode == PlayMode.PASS_AND_PLAY
                            else -> false
                        }
                        val isOnline = mode.id == "private" || mode.id == "open" || mode.id == "lan"
                        ModeChit(
                            mode = mode,
                            selected = selected,
                            comingSoonBadge = s.modeComingSoonBadge,
                            onlineBadge = if (isOnline) s.modeOnlineBadge else null,
                            onSelect = {
                                when {
                                    isOnline -> onOnline()
                                    mode.id == "local" -> playMode = PlayMode.PASS_AND_PLAY
                                    else -> playMode = PlayMode.VS_AI
                                }
                            },
                        )
                    }
                    // Hot-seat human-count picker — only meaningful for pass-and-play.
                    if (playMode == PlayMode.PASS_AND_PLAY) {
                        HumanCountPicker(
                            humanCount = humanCount,
                            playerCount = playerCount,
                            label = s.setupHumanCountLabel,
                            sublabel = s.setupHumanCountSublabel(humanCount, playerCount),
                            onChange = { humanCount = it.coerceIn(2, playerCount) },
                        )
                    }
                }
            }

            // ── 1-B: Player count ──────────────────────────────────────────────
            FormSection(
                label = s.setupPlayerSectionLabel,
                sublabel = s.setupPlayerSublabel(playerCount),
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
                    // Brass abacus visual
                    BrassAbacusRail(count = playerCount, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }

            // ── 1-D: Team Khel (TEAMS variant) ─────────────────────────────────
            if (teamEligible) {
                FormSection(
                    label = s.setupTeamLabel,
                    sublabel = s.setupTeamSublabel,
                ) {
                    TeamToggle(
                        on = teamPlay,
                        onLabel = s.setupTeamToggleOn,
                        offLabel = s.setupTeamToggleOff,
                        teamAName = s.teamNameA,
                        teamBName = s.teamNameB,
                        onToggle = { teamPlay = it },
                    )
                }
            }

            // ── 1-E: Darbar (narrative/chat) ───────────────────────────────────
            if (narrativeEligible) {
                FormSection(
                    label = s.setupDarbarLabel,
                    sublabel = s.setupDarbarSub,
                ) {
                    TeamToggle(
                        on = narrativeEnabled,
                        onLabel = "DARBAR · CHALU",
                        offLabel = "Classic (no chat)",
                        teamAName = "",
                        teamBName = "",
                        onToggle = { narrativeEnabled = it },
                    )
                }
            }

            // ── 1-F: Anarchy (Andher Nagari) ───────────────────────────────────
            if (anarchyEligible) {
                FormSection(
                    label = s.setupAnarchyLabel,
                    sublabel = s.setupAnarchySub,
                ) {
                    TeamToggle(
                        on = anarchyEnabled,
                        onLabel = "ANDHER NAGARI · CHALU",
                        offLabel = "Classic rules",
                        teamAName = "",
                        teamBName = "",
                        onToggle = { anarchyEnabled = it },
                    )
                }
            }

            // ── 1-G: Deck draft (Nilaami) ──────────────────────────────────────
            if (draftEligible) {
                FormSection(
                    label = s.setupDraftLabel,
                    sublabel = s.setupDraftSub,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // CLASSIC option (no draft)
                        DraftOptionChit(
                            title = "CLASSIC",
                            subtitle = "Standard deck — no draft",
                            selected = selectedDraftCode.isEmpty(),
                            onSelect = { selectedDraftCode = "" },
                        )
                        DraftPresets.ALL.forEach { preset ->
                            DraftOptionChit(
                                title = preset.title,
                                subtitle = preset.subtitle,
                                selected = selectedDraftCode == preset.code,
                                onSelect = { selectedDraftCode = preset.code },
                            )
                        }
                    }
                }
            }

            // ── 1-C: Difficulty ────────────────────────────────────────────────
            FormSection(
                label = s.setupDifficultyLabel,
                sublabel = s.setupDifficultySublabel,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    difficultyMeta.forEach { meta ->
                        DifficultyTab(meta = meta, selected = difficulty == meta.tier) {
                            difficulty = meta.tier
                        }
                    }
                }
            }
          }
        }

        // Footer CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            StampChit(
                label = s.setupCta,
                sublabel = s.setupCtaSub,
                isHero = true,
                onClick = {
                    val seed = Random.nextLong()
                    val humans = if (playMode == PlayMode.PASS_AND_PLAY) humanCount.coerceIn(2, playerCount) else 1
                    val teamCount = if (teamPlay && teamEligible) 2 else 0
                    onNext(
                        seed, playerCount, difficulty, humans, teamCount,
                        narrativeEnabled && narrativeEligible,
                        anarchyEnabled && anarchyEligible,
                        if (draftEligible) selectedDraftCode else "",
                    )
                },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── Sub-components ───────────────────────────────────

@Composable
private fun SetupHeader(onBack: () -> Unit) {
    val s = LocalKursiStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandTokens.TeakDark)
            .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = s.back,
            style = KursiType.body.copy(fontSize = 13.sp),
            color = BrandTokens.BrassAged,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = s.setupTitle,
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
            Text(s.setupFormBadge, style = KursiType.caption.copy(fontSize = 8.sp), color = KursiNeutrals.TextMuted)
        }
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.horizontalGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.4f), BrandTokens.BrassAged.copy(alpha = 0.2f), BrandTokens.BrassDark.copy(alpha = 0.1f))))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = KursiType.display.copy(fontSize = 14.sp, letterSpacing = 2.sp),
            color = BrandTokens.GoldAntique,
        )
    }
}

@Composable
private fun FormSection(
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
            modifier = Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.4f), Color.Transparent)),
            ),
        )
        content()
    }
}

@Composable
private fun ModeChit(
    mode: ModeMeta,
    selected: Boolean,
    comingSoonBadge: String,
    onSelect: () -> Unit,
    onlineBadge: String? = null,
) {
    val isAvailable = mode.available
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> BrandTokens.GoldAntique.copy(alpha = 0.18f)
                    isAvailable -> BrandTokens.BrassAged.copy(alpha = 0.15f)
                    else -> BrandTokens.TeakDark.copy(alpha = 0.5f)
                },
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                when {
                    selected -> BrandTokens.GoldAntique
                    isAvailable -> BrandTokens.BrassAged.copy(alpha = 0.7f)
                    else -> BrandTokens.BrassDark.copy(alpha = 0.3f)
                },
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = isAvailable, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(if (isAvailable) 1f else 0.5f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(
                        if (selected) BrandTokens.GoldAntique
                        else if (isAvailable) BrandTokens.BrassDark.copy(alpha = 0.5f)
                        else BrandTokens.TeakDark,
                    ).border(1.5.dp, BrandTokens.BrassAged, RoundedCornerShape(50)),
                )
                Column {
                    Text(
                        text = mode.label,
                        style = KursiType.name.copy(fontSize = 13.sp),
                        color = if (isAvailable) KursiNeutrals.TextPrimary else KursiNeutrals.TextDisabled,
                    )
                    Text(
                        text = mode.sublabel,
                        style = KursiType.caption.copy(fontSize = 9.sp),
                        color = KursiNeutrals.TextMuted,
                    )
                }
            }
            if (!isAvailable) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.StampRed.copy(alpha = 0.12f))
                        .border(0.7.dp, BrandTokens.StampRed.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(comingSoonBadge, style = KursiType.caption.copy(fontSize = 7.sp), color = BrandTokens.StampRed.copy(alpha = 0.7f))
                }
            } else if (onlineBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.15f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(onlineBadge, style = KursiType.caption.copy(fontSize = 7.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold), color = BrandTokens.GoldAntique)
                }
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("✓", style = KursiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = BrandTokens.GoldAntique)
                }
            }
        }
    }
}

/** Hot-seat human-count picker (pass-and-play). Brass +/- stepper over a labelled rail. */
@Composable
private fun HumanCountPicker(
    humanCount: Int,
    playerCount: Int,
    label: String,
    sublabel: String,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandTokens.GoldAntique.copy(alpha = 0.06f))
            .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = KursiType.label.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.GoldAntique,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(symbol = "−", enabled = humanCount > 2) { onChange(humanCount - 1) }
            Text(
                text = "$humanCount",
                style = KursiType.title.copy(fontSize = 22.sp),
                color = BrandTokens.GoldAntique,
            )
            StepperButton(symbol = "+", enabled = humanCount < playerCount) { onChange(humanCount + 1) }
            Spacer(Modifier.weight(1f))
            Text(
                text = sublabel,
                style = KursiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** M6e TEAM KHEL — an enamel toggle that splits the table into two factions (last-team-standing). */
@Composable
private fun TeamToggle(
    on: Boolean,
    onLabel: String,
    offLabel: String,
    teamAName: String,
    teamBName: String,
    onToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) BrandTokens.GoldAntique.copy(alpha = 0.10f) else BrandTokens.BrassAged.copy(alpha = 0.08f))
            .border(if (on) 1.5.dp else 1.dp, if (on) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onToggle(!on) }
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Switch
                contentDescription = if (on) onLabel else offLabel
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (on) onLabel else offLabel,
                style = KursiType.name.copy(fontSize = 13.sp),
                color = if (on) BrandTokens.GoldAntique else KursiNeutrals.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // Brass pill switch
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) BrandTokens.GoldAntique.copy(alpha = 0.4f) else BrandTokens.TeakDark)
                    .border(1.dp, if (on) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(50)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (on) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.7f))
                        .border(1.dp, BrandTokens.BrassAged, CircleShape),
                )
            }
        }
        if (on) {
            // Show the two faction nameplates so the player knows what they're signing up for.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamPill(name = teamAName, teamId = 0)
                Text("vs", style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic), color = KursiNeutrals.TextMuted)
                TeamPill(name = teamBName, teamId = 1)
            }
        }
    }
}

/** A small faction nameplate pill used in the Team Khel toggle + lobby. */
@Composable
private fun TeamPill(name: String, teamId: Int) {
    val hue = if (teamId == 0) BrandTokens.GoldAntique else BrandTokens.StampRed
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(hue.copy(alpha = 0.14f))
            .border(0.8.dp, hue.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(name, style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = hue)
    }
}

@Composable
private fun StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) BrandTokens.BrassAged.copy(alpha = 0.25f) else BrandTokens.TeakDark.copy(alpha = 0.4f))
            .border(1.dp, if (enabled) BrandTokens.GoldAntique.copy(alpha = 0.7f) else BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
    }
}

/** A selectable draft-preset option row for the Nilaami picker. */
@Composable
private fun DraftOptionChit(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) BrandTokens.GoldAntique.copy(alpha = 0.14f)
                else BrandTokens.BrassAged.copy(alpha = 0.08f),
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect)
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.RadioButton
                contentDescription = "$title. $subtitle"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) BrandTokens.GoldAntique
                        else BrandTokens.BrassDark.copy(alpha = 0.4f),
                    )
                    .border(1.dp, BrandTokens.BrassAged, RoundedCornerShape(50)),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = KursiType.name.copy(fontSize = 13.sp),
                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = KursiType.caption.copy(fontSize = 9.sp),
                    color = KursiNeutrals.TextMuted,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "✓",
                        style = KursiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }
        }
    }
}

/** Map the AI tier enum back to the UI-layer difficulty used by the Setup/Lobby flow. */
private fun BotDifficulty.toUiDifficulty(): Difficulty = when (this) {
    BotDifficulty.EASY        -> Difficulty.Easy
    BotDifficulty.MEDIUM      -> Difficulty.Medium
    BotDifficulty.HARD        -> Difficulty.Hard
    BotDifficulty.EXPERT      -> Difficulty.Expert
    BotDifficulty.GRANDMASTER -> Difficulty.Grandmaster
}

/** One-tap QUICK MATCH — a hero requisition that stamps straight to the table from saved defaults. */
@Composable
private fun QuickMatchChit(label: String, sublabel: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.BrassAged)
            .border(1.5.dp, BrandTokens.GoldAntique, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "$label. $sublabel"
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = KursiType.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = BrandTokens.TeakDark)
                Text(sublabel, style = KursiType.caption.copy(fontSize = 10.sp), color = BrandTokens.BrassDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // APPROVED-style instant stamp
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                    .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("⚡ START", style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = KursiNeutrals.Cream)
            }
        }
    }
}

/** A curated persona-lineup preset card — name, sublabel, a brass monogram rail of the cast. */
@Composable
private fun PresetChit(
    name: String,
    sublabel: String,
    stamp: String,
    lineupMonograms: List<String>,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BrandTokens.GoldAntique.copy(alpha = 0.08f))
            .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "$name. $sublabel"
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = KursiType.name.copy(fontSize = 14.sp, letterSpacing = 0.5.sp), color = BrandTokens.GoldAntique)
                    Text(sublabel, style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic), color = KursiNeutrals.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(stamp, style = KursiType.caption.copy(fontSize = 7.sp, letterSpacing = 0.6.sp), color = BrandTokens.GoldAntique.copy(alpha = 0.85f))
                }
            }
            // Brass monogram rail — the curated cast, capped so 10p stays legible.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val shown = lineupMonograms.take(6)
                shown.forEach { mono ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(BrandTokens.BrassAged, BrandTokens.BrassDark)))
                            .border(0.8.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(mono.take(2), style = KursiType.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold), color = BrandTokens.TeakDark)
                    }
                }
                if (lineupMonograms.size > shown.size) {
                    Text("+${lineupMonograms.size - shown.size}", style = KursiType.caption.copy(fontSize = 9.sp), color = KursiNeutrals.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun BrassAbacusRail(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { i ->
            val isActive = i < count
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isActive) Brush.radialGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark))
                        else Brush.radialGradient(listOf(BrandTokens.BrassDark.copy(alpha = 0.3f), BrandTokens.TeakDark.copy(alpha = 0.5f))),
                    )
                    .border(1.dp, if (isActive) BrandTokens.BrassAged else BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                if (i == 0) {
                    Text("A", style = KursiType.caption.copy(fontSize = 6.sp), color = BrandTokens.TeakDark, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DifficultyTab(
    meta: DifficultyMeta,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent,
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(
                    if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.5f),
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta.nameplate,
                    style = KursiType.name.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextSecondary,
                )
                Text(
                    text = "\"${meta.voiceLine}\"",
                    style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("✓", style = KursiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = BrandTokens.GoldAntique)
                }
            }
        }
    }
}
