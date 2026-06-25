package com.kursi.shared.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.core.prefs.AppPrefs
import com.kursi.core.prefs.TurnSpeed
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.shared.strings.LocalKursiStrings

/**
 * SettingsScreen — Daftari (S6) from 17_app_plan.md §4.
 *
 * Cream form backed by AppPrefs:
 * - AAWAZ: sound toggle
 * - DIKHAWA: reduced motion toggle
 * - KHEL: default difficulty, default player count
 * - SEEKH: replay primer link
 * - BAARE MEIN: about / disclaimer
 */
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
    onReplayPrimer: () -> Unit,
    onGazette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val scrollState = rememberScrollState()

    // Read prefs into local state
    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var reducedMotion by remember { mutableStateOf(prefs.reducedMotion) }
    var defaultDifficulty by remember { mutableStateOf(prefs.defaultDifficulty) }
    var defaultPlayers by remember { mutableIntStateOf(prefs.defaultPlayerCount) }
    var language by remember { mutableStateOf(prefs.language) }
    var coachEnabled by remember { mutableStateOf(prefs.coachEnabled) }
    var turnSpeed by remember { mutableStateOf(prefs.turnSpeed) }
    var autoPass by remember { mutableStateOf(prefs.autoPass) }
    var autoForced by remember { mutableStateOf(prefs.autoPlayForced) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BrandTokens.TeakInk),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandTokens.TeakDark)
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(0.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
                    .semantics(mergeDescendants = true) { role = Role.Button }
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.back, style = KursiType.body.copy(fontSize = 13.sp), color = BrandTokens.BrassAged)
            }
            Spacer(Modifier.weight(1f))
            Text(
                s.settingsTitle,
                style = KursiType.title.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Centred daftari column: keeps toggles next to their labels instead of
          // flinging the switch to the far edge on wide desktop windows.
          Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            // ── BHASHA (Language) ──────────────────────────────────────────────
            SettingsSection(s.settingsLanguageSection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(s.settingsLangHinglish to "HINGLISH", s.settingsLangEnglish to "ENGLISH").forEach { (label, code) ->
                        val selected = language == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    language = code
                                    prefs.language = code
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = KursiType.caption.copy(fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                            )
                        }
                    }
                }
            }

            // ── AAWAZ (Sound) ──────────────────────────────────────────────────
            SettingsSection(s.settingsSoundSection) {
                SettingsToggleRow(
                    label = s.settingsSoundLabel,
                    sublabel = s.settingsSoundSub,
                    checked = soundEnabled,
                    onCheckedChange = { v ->
                        soundEnabled = v
                        prefs.soundEnabled = v
                    },
                )
            }

            // ── MASHWARA (Decision Coach) ──────────────────────────────────────
            SettingsSection(s.settingsCoachSection) {
                SettingsToggleRow(
                    label = s.settingsCoachLabel,
                    sublabel = s.settingsCoachSub,
                    checked = coachEnabled,
                    onCheckedChange = { v ->
                        coachEnabled = v
                        prefs.coachEnabled = v
                    },
                )
            }

            // ── AUTO-MODE (M5 turn-speed + assistant) ──────────────────────────
            SettingsSection(s.settingsAutoSection) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        s.settingsTurnSpeedLabel,
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            TurnSpeed.SLOW to s.settingsSpeedSlow,
                            TurnSpeed.NORMAL to s.settingsSpeedNormal,
                            TurnSpeed.FAST to s.settingsSpeedFast,
                        ).forEach { (speed, label) ->
                            val selected = turnSpeed == speed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable {
                                        turnSpeed = speed
                                        prefs.turnSpeed = speed
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = KursiType.caption.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                                )
                            }
                        }
                    }
                    SettingsToggleRow(
                        label = s.settingsAutoPassLabel,
                        sublabel = s.settingsAutoPassSub,
                        checked = autoPass,
                        onCheckedChange = { v -> autoPass = v; prefs.autoPass = v },
                    )
                    SettingsToggleRow(
                        label = s.settingsAutoForcedLabel,
                        sublabel = s.settingsAutoForcedSub,
                        checked = autoForced,
                        onCheckedChange = { v -> autoForced = v; prefs.autoPlayForced = v },
                    )
                }
            }

            // ── DIKHAWA (Motion & display) ─────────────────────────────────────
            SettingsSection(s.settingsMotionSection) {
                SettingsToggleRow(
                    label = s.settingsMotionLabel,
                    sublabel = s.settingsMotionSub,
                    checked = reducedMotion,
                    onCheckedChange = { v ->
                        reducedMotion = v
                        prefs.reducedMotion = v
                    },
                )
            }

            // ── KHEL (Gameplay defaults) ───────────────────────────────────────
            SettingsSection(s.settingsDefaultsSection) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        s.settingsDefaultDiffLabel,
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Difficulty.entries.forEach { d ->
                            val selected = d.name == defaultDifficulty
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable {
                                        defaultDifficulty = d.name
                                        prefs.defaultDifficulty = d.name
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.name,
                                    style = KursiType.caption.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.settingsDefaultPlayersLabel(defaultPlayers),
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        (2..10).forEach { n ->
                            val selected = n == defaultPlayers
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .clickable {
                                        defaultPlayers = n
                                        prefs.defaultPlayerCount = n
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$n",
                                    style = KursiType.caption.copy(fontSize = 11.sp),
                                    color = if (selected) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            // ── SEEKH (Learning) ───────────────────────────────────────────────
            SettingsSection(s.settingsLearningSection) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsLinkRow(
                        label = s.settingsReplayPrimerLabel,
                        sublabel = s.settingsReplayPrimerSub,
                        onClick = onReplayPrimer,
                    )
                    SettingsLinkRow(
                        label = s.settingsRulesLabel,
                        sublabel = s.settingsRulesSub,
                        onClick = onGazette,
                    )
                }
            }

            // ── BAARE MEIN (About) ─────────────────────────────────────────────
            SettingsSection(s.settingsAboutSection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.settingsAboutTitle,
                        style = KursiType.body.copy(fontSize = 13.sp),
                        color = KursiNeutrals.TextPrimary,
                    )
                    Text(
                        s.settingsAboutDisclaimer,
                        style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextMuted,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(1.dp).background(
                            Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.3f), Color.Transparent)),
                        ),
                    )
                    Text(
                        s.settingsAboutFooter,
                        style = KursiType.caption.copy(fontSize = 9.sp),
                        color = KursiNeutrals.TextDisabled,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
          }
        }
    }
}

// ─────────────────────────── Sub-components ───────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.PaperCream.copy(alpha = 0.04f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = KursiType.label.copy(fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
            color = BrandTokens.BrassAged,
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(
                Brush.horizontalGradient(listOf(Color.Transparent, BrandTokens.BrassAged.copy(alpha = 0.3f), Color.Transparent)),
            ),
        )
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = KursiType.name.copy(fontSize = 13.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Text(
                sublabel,
                style = KursiType.caption.copy(fontSize = 10.sp),
                color = KursiNeutrals.TextMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandTokens.GoldAntique,
                checkedTrackColor = BrandTokens.BrassAged.copy(alpha = 0.5f),
                uncheckedThumbColor = BrandTokens.BrassDark,
                uncheckedTrackColor = BrandTokens.TeakDark,
            ),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    sublabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BrandTokens.BrassDark.copy(alpha = 0.1f))
            .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = KursiType.name.copy(fontSize = 13.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Text(
                sublabel,
                style = KursiType.caption.copy(fontSize = 10.sp),
                color = KursiNeutrals.TextMuted,
            )
        }
        Text(
            "→",
            style = KursiType.title.copy(fontSize = 16.sp),
            color = BrandTokens.BrassAged,
        )
    }
}
