package com.kursi.shared.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.BotPersona
import com.kursi.ai.persona.PersonaRoster
import com.kursi.core.prefs.AppPrefs
import com.kursi.designsystem.*
import com.kursi.shared.strings.LocalKursiStrings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Ticker headlines ──────────────────────────────────────────────────────────

private val TICKER_LINES = listOf(
    "ROZNAMCHA ⌁  Netaji Vachan promises to keep every promise he forgot to make.",
    "ROZNAMCHA ⌁  Babu Filewala locates a file last seen during the previous government.",
    "ROZNAMCHA ⌁  Seth Khokhawala donates generously; receipt unavailable.",
    "ROZNAMCHA ⌁  Inspector Damaad clarifies the raid was purely ceremonial.",
    "ROZNAMCHA ⌁  Madam Sarpanch wins unanimously; turnout disputed by mathematics.",
    "ROZNAMCHA ⌁  Vakil Loophole files a stay against the laws of physics.",
    "ROZNAMCHA ⌁  Jugaadu Chhotu fixes the budget with one zip-tie and confidence.",
    "ROZNAMCHA ⌁  Dalla Tiwari \"knows a guy\" for that too.",
    "ROZNAMCHA ⌁  Maaji Anna fasts in protest; breaks for tea.",
    "ROZNAMCHA ⌁  Bhai Teja offers protection from problems Bhai Teja invented.",
)

// ── On-duty captions per persona ──────────────────────────────────────────────

private fun onDutyCaption(personaId: String): String = when (personaId) {
    "netaji_vachan"    -> "On duty. Will see you after the rally."
    "bhai_teja"        -> "On duty. Smiling is not permitted at this counter."
    "babu_filewala"    -> "On duty. Kindly join the queue (no queue exists)."
    "jugaadu_chhotu"   -> "On duty. Will fix everything. Probably."
    "vakil_loophole"   -> "On duty. Filing objections. Against everything."
    "inspector_damaad" -> "On duty. Under investigation. Himself."
    "seth_khokhawala"  -> "On duty. Cash counter closes when convenient."
    "madam_sarpanch"   -> "On duty. All decisions final. Husband concurs."
    "dalla_tiwari"     -> "On duty. Available for introductions only."
    "maaji_anna"       -> "On duty. Fasting. But available for donations."
    else               -> "On duty. File pending."
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * HomeScreen — License Raj Deco "waiting room outside the Minister's office."
 *
 * §3 spec from 17_app_plan.md + 17b_start_page.md.
 * Regions: A=ticker, B=brass seal, C=wordmark+tagline, D=persona on duty, E=CTA stack, F=footer.
 */
@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    launchIndex: Int = 0, // rotates duty persona
    modifier: Modifier = Modifier,
    // KISSA / Story mode entry.
    onStory: () -> Unit = {},
    // M5 ONBOARD — Pehli Hazri / interactive tutorial entry.
    onTutorial: () -> Unit = {},
    // M3 — lifetime career ledger surfaced as a compact strip (hidden when no games played).
    ledger: com.kursi.core.prefs.StatsLedger = com.kursi.core.prefs.StatsLedger(),
    onCareer: () -> Unit = {},
    // M3 — an in-progress match to offer resuming. Null = no resumable game.
    resumeLabel: String? = null,
    onResume: () -> Unit = {},
    // M6d — ranked ELO standing surfaced as a compact strip + the local leaderboard entry.
    ranked: com.kursi.core.prefs.RankedStanding = com.kursi.core.prefs.RankedStanding(),
    onLeaderboard: () -> Unit = {},
    // M6d — daily challenge (Aaj ki Chunauti) entry + standing.
    daily: com.kursi.core.prefs.DailyStanding = com.kursi.core.prefs.DailyStanding(),
    todayDailyDone: Boolean = false,
    onDaily: () -> Unit = {},
    // M6e — gauntlet ladder (Tarakki ki Seedhi) entry + progress, and the watch-only Tamasha demo.
    gauntlet: com.kursi.core.prefs.GauntletProgress = com.kursi.core.prefs.GauntletProgress(),
    gauntletRungCount: Int = 0,
    onGauntlet: () -> Unit = {},
    onSpectate: () -> Unit = {},
    // Render harness: pre-select a mode tile so the right-panel preview is visible on frame 1.
    initialSelectedKey: String? = null,
) {
    val persona = remember(launchIndex) {
        PersonaRoster.ALL[launchIndex % PersonaRoster.ALL.size]
    }

    // Read reduced-motion once per composition — stable snapshot from storage.
    val reducedMotion = remember { AppPrefs().reducedMotion }

    // ── Staggered entrance ────────────────────────────────────────────────────
    // visible flips true on first composition; animate* then tween to 1f / 0.dp.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val enterDuration = if (reducedMotion) 0 else 420

    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 0),
        label = "wordmarkAlpha",
    )
    val wordmarkSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 22f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 0),
        label = "wordmarkSlide",
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 80),
        label = "taglineAlpha",
    )
    val sealAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 120),
        label = "sealAlpha",
    )
    val sealScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 120),
        label = "sealScale",
    )
    val personaAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 180),
        label = "personaAlpha",
    )
    val personaSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 180),
        label = "personaSlide",
    )
    val ctaAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 260),
        label = "ctaAlpha",
    )
    val ctaSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 260),
        label = "ctaSlide",
    )

    // ── Continuous ambient: seal slow rotation ────────────────────────────────
    val infiniteAmbient = rememberInfiniteTransition(label = "ambient")
    val sealRotation by if (reducedMotion) {
        remember { mutableStateOf(0f) }.let { s -> derivedStateOf { s.value } }
    } else {
        infiniteAmbient.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sealRotation",
        )
    }

    // ── Continuous ambient: persona card gentle bob ───────────────────────────
    val personaBob by if (reducedMotion) {
        remember { mutableStateOf(0f) }.let { s -> derivedStateOf { s.value } }
    } else {
        infiniteAmbient.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "personaBob",
        )
    }
    // Bob offset: ±4dp mapped from [0,1] → [-4, +4]
    val personaBobPx = (personaBob * 2f - 1f) * 4f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BrandTokens.TeakInk)
            .drawBehind { drawHomeDepth() },
    ) {
        val isExpanded = maxWidth >= 1024.dp

        Column(modifier = Modifier.fillMaxSize()) {

            // ── A: Teleprinter ticker ──────────────────────────────────────────
            TeleprinterTicker(
                lines = TICKER_LINES,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )

            // ── Main body ─────────────────────────────────────────────────────
            if (isExpanded) {
                ExpandedHomeLayout(
                    persona = persona,
                    onNewGame = onNewGame,
                    onGazette = onGazette,
                    onSettings = onSettings,
                    onOnlineTap = onOnlineTap,
                    onStory = onStory,
                    onTutorial = onTutorial,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                    sealAlpha = sealAlpha,
                    sealScale = sealScale,
                    sealRotation = sealRotation,
                    personaAlpha = personaAlpha,
                    personaSlide = personaSlide,
                    personaBobPx = personaBobPx,
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                    onSpectate = onSpectate,
                    initialSelectedKey = initialSelectedKey,
                )
            } else {
                CompactHomeLayout(
                    persona = persona,
                    onNewGame = onNewGame,
                    onGazette = onGazette,
                    onSettings = onSettings,
                    onOnlineTap = onOnlineTap,
                    onStory = onStory,
                    onTutorial = onTutorial,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                    sealAlpha = sealAlpha,
                    sealScale = sealScale,
                    sealRotation = sealRotation,
                    personaAlpha = personaAlpha,
                    personaSlide = personaSlide,
                    personaBobPx = personaBobPx,
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                    onSpectate = onSpectate,
                )
            }
        }
    }
}

// ─────────────────────────── Expanded layout (≥1024dp) ───────────────────────

@Composable
private fun ColumnScope.ExpandedHomeLayout(
    persona: BotPersona,
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    onStory: () -> Unit,
    onTutorial: () -> Unit,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
    sealAlpha: Float,
    sealScale: Float,
    sealRotation: Float,
    personaAlpha: Float,
    personaSlide: Float,
    personaBobPx: Float,
    ctaAlpha: Float,
    ctaSlide: Float,
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
    onSpectate: () -> Unit,
    initialSelectedKey: String? = null,
) {
    val s = LocalKursiStrings.current
    var selectedKey by remember(initialSelectedKey) { mutableStateOf(initialSelectedKey) }
    val modes = remember(s) {
        listOf(
            HomeModeData(
                key = "new_game", icon = "♛", accentArgb = 0xFFC9873AL,
                label = s.homeCtaNewGame, sublabel = s.homeCtaNewGameSub,
                description = "Challenge the Cabinet in a single-player match. Outmanoeuvre AI opponents, seize the Kursi, and survive the vote of no confidence.",
                details = listOf("Players" to "2 – 5", "Opponent" to "AI Cabinet", "Duration" to "~20 min"),
                isHero = true, onClick = onNewGame,
            ),
            HomeModeData(
                key = "story", icon = "◉", accentArgb = 0xFF2D1F5EL,
                label = s.homeCtaStory, sublabel = s.homeCtaStorySub,
                description = "Enter the Darbar. Bots talk, conspire, and betray each other in real time. You observe the room and pull the strings from the shadows.",
                details = listOf("Format" to "Narrative", "Control" to "Indirect", "Bots" to "Fully reactive"),
                onClick = onStory,
            ),
            HomeModeData(
                key = "gauntlet", icon = "▲", accentArgb = 0xFF1B4A2EL,
                label = s.homeCtaGauntlet, sublabel = s.homeCtaGauntletSub,
                description = "Climb from Peon to Prime Minister. Each rung is a harder table — beat it to advance. Lose and the rung resets. Progress persists between sessions.",
                details = listOf("Format" to "Gauntlet", "Tiers" to "Progressive", "Progress" to "Persistent"),
                onClick = onGauntlet,
            ),
            HomeModeData(
                key = "spectate", icon = "◎", accentArgb = 0xFF1A2C4AL,
                label = s.homeCtaSpectate, sublabel = s.homeCtaSpectateSub,
                description = "Watch a full game with no input required. Today's Tamasha seed is deterministic — the exact same table plays out for every player.",
                details = listOf("Input" to "None", "Seed" to "Today's date", "Players" to "4 bots"),
                onClick = onSpectate,
            ),
            HomeModeData(
                key = "tutorial", icon = "✎", accentArgb = 0xFF1A3A3AL,
                label = s.homeCtaTutorial, sublabel = s.homeCtaTutorialSub,
                description = "Your first day in the daftar. The game walks you through every rule interactively, one move at a time. No reading required.",
                details = listOf("Format" to "Interactive", "Pace" to "Self-guided", "For" to "New players"),
                onClick = onTutorial,
            ),
            HomeModeData(
                key = "rules", icon = "§", accentArgb = 0xFF4A1A1AL,
                label = s.homeCtaRules, sublabel = s.homeCtaRulesSub,
                description = "The complete NIYAM Gazette. Every card, every action class, who beats whom, and the full laws of succession — in one reference.",
                details = listOf("Contents" to "Full rules", "Beat chart" to "Included", "Cards" to "All 15"),
                onClick = onGazette,
            ),
            HomeModeData(
                key = "settings", icon = "⚙", accentArgb = 0xFF1A1E2EL,
                label = s.homeCtaSettings, sublabel = s.homeCtaSettingsSub,
                description = "Adjust sound, motion reduction, language, and AI difficulty defaults. All preferences carry across every game session.",
                details = listOf("Sound" to "On / Off", "Language" to "EN / HI", "Motion" to "Full / Reduced"),
                onClick = onSettings,
            ),
            HomeModeData(
                key = "multiplayer", icon = "⊕", accentArgb = 0xFF2A1A1AL,
                label = s.homeCtaMultiplayer, sublabel = s.homeCtaMultiplayerSub,
                description = "Open the Mehfil to outsiders. Create a private room and share the code, or drop into a quick match. Works online and on local network — no account required.",
                details = listOf("Players" to "2 – 5", "Network" to "Online + LAN", "Rooms" to "Private / Quick"),
                onClick = onOnlineTap,
            ),
        )
    }
    val selectedMode = modes.find { it.key == selectedKey }

    // Two even columns that together fill the whole body — the left "file spine"
    // (wordmark + CTAs) and the right "stamped sidebar" (seal + persona on duty)
    // each take real width and full height, so neither the canvas nor the right
    // half reads as dead space. Generous symmetric padding keeps it gallery-framed.
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 72.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left 52%: the file spine — scrollable so all 8 mode tiles are reachable.
        Box(modifier = Modifier.weight(0.52f).fillMaxHeight()) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
            ) {
                KursiWordmark(
                    displaySize = 84,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                )
                Spacer(Modifier.height(14.dp))
                HomeContinuityStrips(
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                )
                Spacer(Modifier.height(8.dp))
                ModeGrid(
                    modes = modes,
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it },
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }
            // Fade at the bottom to signal more content below
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BrandTokens.TeakInk.copy(alpha = 0.92f)),
                        ),
                    ),
            )
        }

        // Right 48%: mode preview when a tile is selected, persona panel otherwise.
        // the right field. Large seal + a brass divider + the persona-on-duty card,
        // all centred in a bordered panel that fills the column height. This is what
        // fills the former void and balances the left spine.
        Crossfade(
            targetState = selectedMode,
            modifier = Modifier.weight(0.48f).fillMaxHeight(),
            label = "rightPanel",
        ) { mode ->
            if (mode != null) {
                ModePreviewPanel(mode = mode, personaAlpha = personaAlpha, personaSlide = personaSlide)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(BrandTokens.TeakDark.copy(alpha = 0.55f))
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        BrandTokens.BrassAged.copy(alpha = 0.55f),
                                        BrandTokens.BrassDark.copy(alpha = 0.25f),
                                    ),
                                ),
                                RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 40.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = s.homeRosterHeader,
                            style = KursiType.caption.copy(
                                fontSize = 11.sp,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = BrandTokens.BrassAged,
                            modifier = Modifier.graphicsLayer { alpha = sealAlpha },
                        )
                        Spacer(Modifier.height(28.dp))
                        BrassSeal(
                            modifier = Modifier
                                .size(168.dp)
                                .graphicsLayer {
                                    alpha = sealAlpha
                                    scaleX = sealScale
                                    scaleY = sealScale
                                    rotationZ = sealRotation
                                },
                        )
                        Spacer(Modifier.height(32.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            BrandTokens.BrassAged.copy(alpha = 0.6f),
                                            BrandTokens.GoldAntique.copy(alpha = 0.6f),
                                            BrandTokens.BrassAged.copy(alpha = 0.6f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                        Spacer(Modifier.height(32.dp))
                        PersonaOnDutyCard(
                            persona = persona,
                            enlarged = true,
                            personaAlpha = personaAlpha,
                            personaSlide = personaSlide,
                            bobOffsetPx = personaBobPx,
                        )
                    }
                }
            }
        }
    }

    // ── F: Footer ──────────────────────────────────────────────────────────────
    HomeFooter()
}

// ─────────────────────────── Compact layout (<1024dp) ────────────────────────

@Composable
private fun ColumnScope.CompactHomeLayout(
    persona: BotPersona,
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    onStory: () -> Unit,
    onTutorial: () -> Unit,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
    sealAlpha: Float,
    sealScale: Float,
    sealRotation: Float,
    personaAlpha: Float,
    personaSlide: Float,
    personaBobPx: Float,
    ctaAlpha: Float,
    ctaSlide: Float,
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
    onSpectate: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        KursiWordmark(
            displaySize = 56,
            wordmarkAlpha = wordmarkAlpha,
            wordmarkSlide = wordmarkSlide,
            taglineAlpha = taglineAlpha,
        )
        BrassSeal(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    alpha = sealAlpha
                    scaleX = sealScale
                    scaleY = sealScale
                    rotationZ = sealRotation
                },
        )
        PersonaOnDutyCard(
            persona = persona,
            enlarged = false,
            personaAlpha = personaAlpha,
            personaSlide = personaSlide,
            bobOffsetPx = personaBobPx,
        )
        HomeContinuityStrips(
            ledger = ledger,
            onCareer = onCareer,
            resumeLabel = resumeLabel,
            onResume = onResume,
            ranked = ranked,
            onLeaderboard = onLeaderboard,
            daily = daily,
            todayDailyDone = todayDailyDone,
            onDaily = onDaily,
            gauntlet = gauntlet,
            gauntletRungCount = gauntletRungCount,
            onGauntlet = onGauntlet,
        )
        CtaStack(
            onNewGame = onNewGame,
            onGazette = onGazette,
            onSettings = onSettings,
            onOnlineTap = onOnlineTap,
            onStory = onStory,
            onTutorial = onTutorial,
            onGauntlet = onGauntlet,
            onSpectate = onSpectate,
            fullWidth = true,
            ctaAlpha = ctaAlpha,
            ctaSlide = ctaSlide,
        )
    }
    } // Box

    HomeFooter()
}

/**
 * Resume + career strips shown between the wordmark/persona and the CTA stack. Each renders only
 * when there's something to show — a resumable in-progress match, and/or a non-empty lifetime
 * ledger — so a brand-new player's hub stays clean.
 */
@Composable
private fun HomeContinuityStrips(
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resumeLabel != null) {
            ResumeStrip(label = resumeLabel, onResume = onResume)
        }
        // M6d — daily challenge + ranked rank/rating strips. The ranked strip always renders (even
        // provisional) so the player sees their starting rank; the daily strip drives Aaj ki Chunauti.
        DailyChallengeStrip(daily = daily, todayDone = todayDailyDone, onStart = onDaily, modifier = Modifier.fillMaxWidth())
        RankedStrip(ranked = ranked, onOpen = onLeaderboard, modifier = Modifier.fillMaxWidth())
        // M6e — gauntlet ladder progress strip (only once a run has started so a fresh hub stays clean).
        if (!gauntlet.isFresh && gauntletRungCount > 0) {
            GauntletStrip(gauntlet = gauntlet, total = gauntletRungCount, onOpen = onGauntlet, modifier = Modifier.fillMaxWidth())
        }
        CareerStrip(ledger = ledger, onOpen = onCareer, modifier = Modifier.fillMaxWidth())
    }
}

/** A "Tarakki ki Seedhi" progress strip — shows rungs cleared, links to the gauntlet ladder. */
@Composable
private fun GauntletStrip(
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    total: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val label = s.gauntletStripLabel(gauntlet.clearedCount.coerceIn(0, total), total)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.GoldAntique.copy(alpha = 0.10f))
            .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "${s.homeCtaGauntlet}. $label"
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("▲", style = KursiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
            Column(modifier = Modifier.weight(1f)) {
                Text(s.homeCtaGauntlet, style = KursiType.title.copy(fontSize = 14.sp), color = KursiNeutrals.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = KursiType.caption.copy(fontSize = 10.sp), color = KursiNeutrals.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = KursiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
        }
    }
}

/** A single "Khel jaari rakho" (resume in-progress match) strip, styled like a pending file. */
@Composable
private fun ResumeStrip(label: String, onResume: () -> Unit) {
    val s = LocalKursiStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandTokens.BrassAged.copy(alpha = 0.18f))
            .border(1.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onResume)
            .semantics(mergeDescendants = true) { contentDescription = "Resume in-progress match: $label" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⟳", style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
            Column(modifier = Modifier.weight(1f)) {
                Text(s.homeResumeLabel, style = KursiType.title.copy(fontSize = 14.sp), color = KursiNeutrals.TextPrimary)
                Text(label, style = KursiType.caption.copy(fontSize = 10.sp), color = KursiNeutrals.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = KursiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
        }
    }
}

// ─────────────────────────── Teleprinter ticker ───────────────────────────────

/**
 * TeleprinterTicker — continuous right-to-left marquee of ROZNAMCHA headlines.
 *
 * Bug fix: the old code animated an `offsetX` float but never applied it to
 * any modifier, so the text sat static. This implementation:
 *  1. Measures the natural pixel width of one copy of the joined text via
 *     `onGloballyPositioned` on an invisible reference render.
 *  2. Drives `translationX` in [0, -contentWidth] via `infiniteTransition`.
 *  3. Renders TWO copies of the content side-by-side so the seam is invisible
 *     when the loop restarts at 0.
 *  4. Clips the outer Box so nothing bleeds outside the ticker strip.
 *  5. Respects reducedMotion — shows static (non-scrolling) text if true.
 */
@Composable
private fun TeleprinterTicker(
    lines: List<String>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // One full sweep of content. Appending a copy below gives seamless looping.
    val text = remember { lines.joinToString("     ◆     ") + "     ◆     " }

    // Natural pixel width of one content copy, measured on first layout.
    var contentWidthPx by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "ticker")

    // Only animate once we know the content width (avoids an ugly instant-jump
    // on first frame). When reducedMotion is on, targetValue stays at 0f so
    // the transition never moves and the text is static.
    val scrollDurationMs = 45_000 // ~45 s for a full loop — readable, not rushed
    val translateX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion || contentWidthPx == 0f) 0f else -contentWidthPx,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (reducedMotion || contentWidthPx == 0f) 1 else scrollDurationMs,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tickerTranslateX",
    )

    Box(
        modifier = modifier
            .background(BrandTokens.PaperCream)
            .border(1.dp, BrandTokens.BrassAged, RoundedCornerShape(0.dp))
            .clip(RoundedCornerShape(0.dp)), // clip scrolling content to ticker strip
        contentAlignment = Alignment.CenterStart,
    ) {
        // Two copies of the content in a Row, translated left.
        // When translateX reaches -contentWidthPx the second copy fills
        // exactly the space the first vacated, so the loop is seamless.
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = translateX }
                .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reference measure — invisible Text sized to natural content width.
            // Its onGloballyPositioned captures the pixel width we need.
            Text(
                text = text,
                style = KursiType.caption.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                color = Color.Transparent, // invisible; measurement only
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        if (coords.size.width > 0) {
                            contentWidthPx = coords.size.width.toFloat()
                        }
                    }
                    .then(Modifier), // kept in layout pass only
            )
        }

        // The actual scrolling content — two copies so the wrap is seamless.
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = translateX }
                .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Copy 1
            TickerTextBlock(text = text)
            // Copy 2 — identical, placed immediately after copy 1 so that
            // when the first copy has fully exited left, the second fills in
            // and the translateX resets to 0 without a visible gap.
            TickerTextBlock(text = text)
        }

        // Right-edge fade vignette — gives the illusion of text scrolling off into a machine.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(40.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandTokens.PaperCream.copy(alpha = 0.95f)),
                    ),
                ),
        )
    }
}

@Composable
private fun TickerTextBlock(text: String) {
    Text(
        text = text,
        style = KursiType.caption.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp,
        ),
        color = BrandTokens.CreamInk,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

// ─────────────────────────── Kursi Wordmark ───────────────────────────────────

@Composable
private fun KursiWordmark(
    displaySize: Int,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
) {
    val brassGradient = Brush.horizontalGradient(
        listOf(
            BrandTokens.GoldAntique,
            BrandTokens.BrassAged,
            BrandTokens.GoldAntique,
        ),
    )

    Column {
        // Chair glyph + wordmark on same line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.graphicsLayer {
                alpha = wordmarkAlpha
                translationY = wordmarkSlide
            },
        ) {
            // Chair glyph — mini brass roundel
            Box(
                modifier = Modifier
                    .size((displaySize * 0.65f).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandTokens.BrassAged, BrandTokens.BrassDark),
                        ),
                    )
                    .drawBehind {
                        // Simple chair silhouette via lines
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val r = size.width * 0.35f
                        val col = BrandTokens.TeakDark.copy(alpha = 0.9f)
                        // Seat back arc
                        drawCircle(col, r * 0.6f, Offset(cx, cy * 0.75f), style = Stroke(width = size.width * 0.1f))
                        // Seat legs
                        drawLine(col, Offset(cx - r * 0.4f, cy), Offset(cx - r * 0.4f, cy + r * 0.8f), strokeWidth = size.width * 0.09f)
                        drawLine(col, Offset(cx + r * 0.4f, cy), Offset(cx + r * 0.4f, cy + r * 0.8f), strokeWidth = size.width * 0.09f)
                        drawLine(col, Offset(cx - r * 0.55f, cy + r * 0.5f), Offset(cx + r * 0.55f, cy + r * 0.5f), strokeWidth = size.width * 0.08f)
                    },
                contentAlignment = Alignment.Center,
            ) {}

            // KURSI wordmark in Rozha One with brass gradient
            Text(
                text = "KURSI",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = displaySize.sp,
                    brush = brassGradient,
                    letterSpacing = 2.sp,
                ),
                maxLines = 1,
            )
        }

        // Brass rule
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BrandTokens.BrassAged, BrandTokens.GoldAntique, BrandTokens.BrassAged, Color.Transparent),
                    ),
                )
                .graphicsLayer { alpha = wordmarkAlpha },
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = LocalKursiStrings.current.homeTagline,
            style = KursiType.body.copy(fontStyle = FontStyle.Italic, fontSize = (displaySize * 0.18f).sp),
            color = KursiNeutrals.TextSecondary,
            modifier = Modifier.graphicsLayer { alpha = taglineAlpha },
        )
    }
}

// ─────────────────────────── Brass Seal ──────────────────────────────────────

@Composable
fun BrassSeal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(BrandTokens.GoldAntique.copy(alpha = 0.3f), BrandTokens.BrassDark.copy(alpha = 0.5f)),
                ),
            )
            .border(2.dp, Brush.sweepGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)), CircleShape)
            .drawBehind {
                // Engraved ring text approximation — 3 concentric rings
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.width * 0.45f
                val col = BrandTokens.BrassAged.copy(alpha = 0.6f)
                drawCircle(col, r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                drawCircle(col, r * 0.75f, Offset(cx, cy), style = Stroke(0.7.dp.toPx()))
                // Radiating lines
                val rays = 12
                for (i in 0 until rays) {
                    val angle = (i * 2 * PI / rays).toFloat()
                    drawLine(
                        col.copy(alpha = 0.35f),
                        Offset(cx + r * 0.78f * cos(angle), cy + r * 0.78f * sin(angle)),
                        Offset(cx + r * 0.98f * cos(angle), cy + r * 0.98f * sin(angle)),
                        strokeWidth = 0.8.dp.toPx(),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "KURSI",
                style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.GoldAntique,
            )
            Text(
                text = "·",
                style = KursiType.caption.copy(fontSize = 6.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = "ADHIKAAR",
                style = KursiType.caption.copy(fontSize = 6.sp, letterSpacing = 1.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = "·",
                style = KursiType.caption.copy(fontSize = 6.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = "SATYANAASH",
                style = KursiType.caption.copy(fontSize = 6.sp, letterSpacing = 1.sp),
                color = BrandTokens.BrassAged,
            )
        }
    }
}

// ─────────────────────────── Persona on Duty ──────────────────────────────────

@Composable
private fun PersonaOnDutyCard(
    persona: BotPersona,
    enlarged: Boolean,
    personaAlpha: Float = 1f,
    personaSlide: Float = 0f,
    bobOffsetPx: Float = 0f,
) {
    val seatColor = Color(persona.seatColorArgb)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.graphicsLayer {
            alpha = personaAlpha
            translationY = personaSlide + bobOffsetPx
        },
    ) {
        // Enamel oval nameplate
        Box(
            modifier = Modifier
                .then(if (enlarged) Modifier.fillMaxWidth().height(104.dp) else Modifier.fillMaxWidth().height(64.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF14110D))
                .border(
                    2.dp,
                    Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, seatColor, BrandTokens.BrassAged)),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Monogram roundel
                Box(
                    modifier = Modifier
                        .size(if (enlarged) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(seatColor, seatColor.copy(alpha = 0.5f))))
                        .border(1.dp, BrandTokens.BrassAged, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = persona.monogram,
                        style = KursiType.caption.copy(fontSize = if (enlarged) 11.sp else 9.sp),
                        color = KursiNeutrals.Cream,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = persona.name,
                    style = KursiType.name.copy(fontSize = if (enlarged) 14.sp else 11.sp),
                    color = KursiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = persona.title,
                    style = KursiType.caption.copy(fontSize = if (enlarged) 10.sp else 9.sp, fontStyle = FontStyle.Italic),
                    color = KursiNeutrals.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // On-duty caption
        Text(
            text = "\"${onDutyCaption(persona.id)}\"",
            style = KursiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
            color = KursiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ─────────────────────────── Mode Grid (expanded home) ───────────────────────

private data class HomeModeData(
    val key: String,
    val icon: String,
    val accentArgb: Long,
    val label: String,
    val sublabel: String,
    val description: String = "",
    val details: List<Pair<String, String>> = emptyList(),
    val isHero: Boolean = false,
    val isDisabled: Boolean = false,
    val disabledStamp: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun ModeGrid(
    modes: List<HomeModeData>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    ctaAlpha: Float = 1f,
    ctaSlide: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.graphicsLayer { alpha = ctaAlpha; translationY = ctaSlide },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val cols = 3
        modes.chunked(cols).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModes.forEach { mode ->
                    ModeGridTile(
                        mode = mode,
                        isSelected = selectedKey == mode.key,
                        onTap = { onSelect(if (selectedKey == mode.key) null else mode.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(cols - rowModes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ModeGridTile(
    mode: HomeModeData,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "tilePress_${mode.key}",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.35f,
        animationSpec = tween(180),
        label = "tileBorder_${mode.key}",
    )
    val accentColor = Color(mode.accentArgb)
    val contentAlpha = if (mode.isDisabled) 0.45f else 1f

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF211A12) else Color(0xFF181309))
            .border(if (isSelected) 2.dp else 1.5.dp, BrandTokens.GoldAntique.copy(alpha = borderAlpha), RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onTap)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon area — accent-tinted header strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(accentColor.copy(alpha = if (mode.isDisabled) 0.22f else 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mode.icon,
                style = KursiType.title.copy(fontSize = 22.sp),
                color = Color.White.copy(alpha = 0.88f),
            )
            val badge = when {
                mode.isHero -> "APPROVED"
                mode.disabledStamp != null -> mode.disabledStamp
                else -> null
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (mode.isHero) BrandTokens.StampRed.copy(alpha = 0.9f)
                            else BrandTokens.StampRed.copy(alpha = 0.22f)
                        )
                        .border(
                            1.dp,
                            BrandTokens.StampRed.copy(alpha = if (mode.isHero) 0f else 0.5f),
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badge,
                        style = KursiType.caption.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = if (mode.isHero) KursiNeutrals.Cream else BrandTokens.StampRed.copy(alpha = 0.8f),
                    )
                }
            }
        }
        // Label area
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = mode.label,
                style = KursiType.title.copy(fontSize = 10.sp),
                color = if (mode.isHero) BrandTokens.GoldAntique else KursiNeutrals.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mode.sublabel,
                style = KursiType.caption.copy(fontSize = 9.sp),
                color = KursiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModePreviewPanel(
    mode: HomeModeData,
    personaAlpha: Float,
    personaSlide: Float,
) {
    val accentColor = Color(mode.accentArgb)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer { alpha = personaAlpha; translationY = personaSlide }
            .clip(RoundedCornerShape(20.dp))
            .background(BrandTokens.TeakDark.copy(alpha = 0.55f))
            .border(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = 0.7f),
                        BrandTokens.BrassDark.copy(alpha = 0.3f),
                    ),
                ),
                RoundedCornerShape(20.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top accent block with large icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
                .background(accentColor.copy(alpha = if (mode.isDisabled) 0.2f else 0.38f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mode.icon,
                style = KursiType.title.copy(fontSize = 72.sp),
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        // Info + CTA
        Column(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Title row
                if (mode.isHero) {
                    Text(
                        "APPROVED",
                        style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.StampRed,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = mode.label,
                    style = KursiType.title.copy(fontSize = 18.sp),
                    color = KursiNeutrals.TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mode.sublabel,
                    style = KursiType.caption.copy(fontSize = 10.sp),
                    color = KursiNeutrals.TextMuted,
                )

                // Description
                if (mode.description.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BrandTokens.BrassDark.copy(alpha = 0.4f)))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = mode.description,
                        style = KursiType.body.copy(fontSize = 11.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                }

                // Detail rows — styled as mini file fields
                if (mode.details.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BrandTokens.BrassDark.copy(alpha = 0.4f)))
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        mode.details.forEachIndexed { i, (fieldLabel, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (i % 2 == 0) Color(0xFF1A1208).copy(alpha = 0.6f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = fieldLabel.uppercase(),
                                    style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
                                    color = BrandTokens.BrassAged.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = value,
                                    style = KursiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = KursiNeutrals.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            // ENTER / disabled stamp button
            if (!mode.isDisabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (mode.isHero) BrandTokens.BrassAged else BrandTokens.BrassAged.copy(alpha = 0.7f))
                        .border(1.dp, BrandTokens.GoldAntique, RoundedCornerShape(8.dp))
                        .clickable(onClick = mode.onClick)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "ENTER →",
                        style = KursiType.title.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.TeakDark,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.disabledStamp ?: "UNAVAILABLE",
                        style = KursiType.caption.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
                        color = BrandTokens.StampRed.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────── CTA Stack ───────────────────────────────────────

@Composable
private fun CtaStack(
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    onStory: () -> Unit = {},
    onTutorial: () -> Unit = {},
    onGauntlet: () -> Unit = {},
    onSpectate: () -> Unit = {},
    fullWidth: Boolean = false,
    ctaAlpha: Float = 1f,
    ctaSlide: Float = 0f,
) {
    val s = LocalKursiStrings.current
    Column(
        modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 360.dp))
            .graphicsLayer {
                alpha = ctaAlpha
                translationY = ctaSlide
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ① HERO — red APPROVED stamp CTA
        StampChit(
            label = s.homeCtaNewGame,
            sublabel = s.homeCtaNewGameSub,
            isHero = true,
            onClick = onNewGame,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ② KISSA — Story / narrative Darbar mode
        StampChit(
            label = s.homeCtaStory,
            sublabel = s.homeCtaStorySub,
            isHero = false,
            onClick = onStory,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ④ Tarakki ki Seedhi — the escalating gauntlet ladder
        StampChit(
            label = s.homeCtaGauntlet,
            sublabel = s.homeCtaGauntletSub,
            isHero = false,
            onClick = onGauntlet,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑤ Tamasha — watch-only demo game
        StampChit(
            label = s.homeCtaSpectate,
            sublabel = s.homeCtaSpectateSub,
            isHero = false,
            onClick = onSpectate,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑥ Pehli Hazri — interactive tutorial
        StampChit(
            label = s.homeCtaTutorial,
            sublabel = s.homeCtaTutorialSub,
            isHero = false,
            onClick = onTutorial,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑦ Rules
        StampChit(
            label = s.homeCtaRules,
            sublabel = s.homeCtaRulesSub,
            isHero = false,
            onClick = onGazette,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑧ Settings
        StampChit(
            label = s.homeCtaSettings,
            sublabel = s.homeCtaSettingsSub,
            isHero = false,
            onClick = onSettings,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑨ Multiplayer — disabled PENDING SANCTION
        StampChit(
            label = s.homeCtaMultiplayer,
            sublabel = s.homeCtaMultiplayerSub,
            isHero = false,
            isDisabled = true,
            disabledStamp = s.homeCtaMultiplayerBadge,
            onClick = onOnlineTap,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

// ─────────────────────────── StampChit (CTA primitive) ───────────────────────

@Composable
fun StampChit(
    label: String,
    sublabel: String? = null,
    isHero: Boolean = false,
    isDisabled: Boolean = false,
    disabledStamp: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isDisabled -> BrandTokens.TeakDark
        isHero     -> BrandTokens.BrassAged
        else       -> Color(0xFF1E1610)
    }
    val borderColor = when {
        isDisabled -> BrandTokens.BrassDark.copy(alpha = 0.4f)
        isHero     -> BrandTokens.GoldAntique
        else       -> BrandTokens.BrassAged.copy(alpha = 0.7f)
    }
    val contentAlpha = if (isDisabled) 0.45f else 1f

    // Press scale feedback — scale dips to 0.96 on press, restores on release.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isDisabled) 0.96f else 1f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "pressScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            // A11y: each CTA is a button; merge the label/sublabel/stamp into one spoken node and
            // mark disabled CTAs (e.g. multiplayer "pending sanction") so they're announced as such.
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                if (isDisabled) disabled()
                contentDescription = if (sublabel != null) "$label. $sublabel" else label
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isDisabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = if (isHero) 18.dp else 12.dp)
            .alpha(contentAlpha),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = if (isHero) MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp)
                            else KursiType.title.copy(fontSize = 15.sp),
                    color = when {
                        isDisabled -> KursiNeutrals.TextDisabled
                        isHero     -> BrandTokens.TeakDark
                        else       -> KursiNeutrals.TextPrimary
                    },
                    fontWeight = if (isHero) FontWeight.Bold else FontWeight.Normal,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = KursiType.caption.copy(fontSize = 11.sp),
                        color = if (isHero) BrandTokens.BrassDark else KursiNeutrals.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Stamp impression
            if (isHero && !isDisabled) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                        .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "APPROVED",
                        style = KursiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = KursiNeutrals.Cream,
                    )
                }
            }
            if (isDisabled && disabledStamp != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BrandTokens.StampRed.copy(alpha = 0.15f))
                        .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = disabledStamp,
                        style = KursiType.caption.copy(fontSize = 8.sp, letterSpacing = 0.8.sp),
                        color = BrandTokens.StampRed.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Home Footer ──────────────────────────────────────

@Composable
private fun HomeFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandTokens.TeakDark.copy(alpha = 0.8f))
            .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.3f), RoundedCornerShape(0.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sf = LocalKursiStrings.current
            Text(
                text = sf.homeFooterLeft,
                style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = KursiNeutrals.TextMuted,
            )
            Text(
                text = sf.homeFooterRight,
                style = KursiType.caption.copy(fontSize = 9.sp),
                color = KursiNeutrals.TextDisabled,
            )
        }
    }
}

// ─────────────────────────── Background depth ─────────────────────────────────

/**
 * Very-low-contrast certificate furniture behind the Home content:
 *  - a thin double inner-border (the engraved frame of a government certificate),
 *  - an oversized guilloche seal watermark anchored in the wide right field,
 *    so the bare teak expanse reads as embossed stationery rather than empty space.
 *
 * All strokes sit at ≤ 6% alpha brass: present on a calm read, never competing with
 * the wordmark, CTAs, or the live brass seal.
 */
private fun DrawScope.drawHomeDepth() {
    val brass = BrandTokens.BrassAged
    val gold = BrandTokens.GoldAntique

    // ── Thin certificate inner-border (double rule, inset from the edges) ──────
    val inset = 18f
    val gap = 6f
    drawRect(
        color = brass.copy(alpha = 0.10f),
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = 1f),
    )
    drawRect(
        color = brass.copy(alpha = 0.05f),
        topLeft = Offset(inset + gap, inset + gap),
        size = androidx.compose.ui.geometry.Size(size.width - (inset + gap) * 2, size.height - (inset + gap) * 2),
        style = Stroke(width = 1f),
    )

    // ── Oversized guilloche seal watermark — a faint emboss centred across the
    // whole sheet so the teak field reads as engraved stationery edge to edge,
    // not a spotlight under one corner. ──────────────────────────────────────
    val cx = size.width * 0.50f
    val cy = size.height * 0.52f
    val baseR = minOf(size.width, size.height) * 0.58f

    // Concentric engraved rings.
    val ringAlphas = listOf(0.06f, 0.045f, 0.035f, 0.025f)
    ringAlphas.forEachIndexed { i, a ->
        drawCircle(
            color = brass.copy(alpha = a),
            radius = baseR * (1f - i * 0.16f),
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )
    }

    // Guilloche rosette — interlocking petal arcs traced as a ring of small circles,
    // the classic spirograph lacework of banknote/seal engraving.
    val petals = 36
    val petalR = baseR * 0.62f
    val orbit = baseR * 0.40f
    for (i in 0 until petals) {
        val angle = (i * 2.0 * PI / petals).toFloat()
        val px = cx + orbit * cos(angle)
        val py = cy + orbit * sin(angle)
        drawCircle(
            color = gold.copy(alpha = 0.04f),
            radius = petalR,
            center = Offset(px, py),
            style = Stroke(width = 0.8f),
        )
    }

    // Faint radiating ticks at the rim — the "official seal" sunburst.
    val rays = 48
    for (i in 0 until rays) {
        val angle = (i * 2.0 * PI / rays).toFloat()
        drawLine(
            color = brass.copy(alpha = 0.05f),
            start = Offset(cx + baseR * 0.86f * cos(angle), cy + baseR * 0.86f * sin(angle)),
            end = Offset(cx + baseR * 1.0f * cos(angle), cy + baseR * 1.0f * sin(angle)),
            strokeWidth = 0.8f,
        )
    }
}
