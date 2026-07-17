package com.kursi.feature.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.BluffOdds
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.board.*
import com.kursi.feature.game.docks.*
import kotlinx.coroutines.launch

// ─────────────────────────── GamePhase ───────────────────────────

sealed interface GamePhase {
    object Idle : GamePhase

    object PickAction : GamePhase

    data class PickTarget(
        val action: Action,
    ) : GamePhase

    data class Confirm(
        val action: Action,
        val target: PlayerId?,
    ) : GamePhase

    data class ReactionWindow(
        val step: ReactionStep,
        val actor: PlayerId,
        val action: Action,
        val claimedRole: Role?,
        val blocker: PlayerId?,
        val blockRole: Role?,
        val myLegalResponses: List<Intent>,
    ) : GamePhase

    object LoseInfluence : GamePhase

    data class Exchange(
        val drawn: List<CardId>,
    ) : GamePhase

    /** Jaanch follow-up — the human examiner decides whether to force the target to redraw. */
    data class InvestigatePeek(
        val target: PlayerId,
        val peekedRole: Role?,
    ) : GamePhase

    data class GameOver(
        val winnerSeat: Int,
    ) : GamePhase
}

// ─────────────────────────── deriveGamePhase ───────────────────────────

fun deriveGamePhase(
    uiState: GameUiState,
    localPhase: GamePhase?,
): GamePhase {
    if (uiState.isGameOver) {
        return GamePhase.GameOver(uiState.winnerSeat ?: 0)
    }

    val phase = uiState.view.phase

    if (phase is PhaseView.Over) {
        return GamePhase.GameOver(phase.winner.raw)
    }

    if (phase is PhaseView.InfluenceLoss && uiState.isHumanTurn) {
        return GamePhase.LoseInfluence
    }

    if (phase is PhaseView.Exchange && uiState.isHumanTurn) {
        return GamePhase.Exchange(drawn = emptyList())
    }

    if (phase is PhaseView.InvestigatePeek && uiState.isHumanTurn) {
        // The examiner's private peek (examinedCard) is non-null only in the examiner's own view.
        return GamePhase.InvestigatePeek(
            target = phase.target,
            peekedRole = phase.examinedCard?.role,
        )
    }

    if (phase is PhaseView.Reactions && uiState.isHumanTurn) {
        return GamePhase.ReactionWindow(
            step = phase.step,
            actor = phase.actor,
            action = phase.action,
            claimedRole = phase.claimedRole,
            blocker = phase.blocker,
            blockRole = phase.blockRole,
            myLegalResponses = uiState.legalIntents,
        )
    }

    if (uiState.isHumanTurn && phase is PhaseView.Turn) {
        // Keep localPhase if it's still relevant
        if (localPhase is GamePhase.PickTarget || localPhase is GamePhase.Confirm) {
            return localPhase
        }
        return GamePhase.PickAction
    }

    return GamePhase.Idle
}

// ─────────────────────────── GameScreen ───────────────────────────

@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
    initialLocalPhase: GamePhase? = null,
    // First-run onboarding override. In the real app this is null, so the
    // SwearingInPrimer coachmark fires off PrimerPrefs.hasSeenPrimer. The static
    // render harness passes `false` so the actual in-game table (turn spine,
    // opponent chips, hand, action bar, Roznamcha) is captured un-dimmed instead
    // of being washed out behind the centered primer modal.
    showPrimerOverride: Boolean? = null,
    // Render-harness only: pre-open a WhisperChit (floating, un-anchored) so a screenshot
    // can verify chit/dossier content renders rich and fits. Null in the real app.
    initialChit: ChitContent? = null,
    // DECISION-COACH TOGGLE. Wired to GameViewModel.toggleCoach() in the real app; null in
    // the render harness (the toggle is then invisible so the fixture is deterministic).
    onToggleCoach: (() -> Unit)? = null,
    // M3 FEEDBACK. Master sound/haptics gate (AppPrefs.soundFlow). When false the moment
    // overlay stays silent. Defaults false so the static render harness never tries to play.
    soundEnabled: Boolean = false,
    // M3 REDUCED-MOTION. AppPrefs.reducedMotionFlow. When true, moments collapse to the
    // overlay's static/120ms path instead of the full stamp theatre.
    reducedMotion: Boolean = false,
    // Render-harness only: force the pass-and-play handoff guard visible so a screenshot can verify
    // it. Null in the real app (the guard shows itself on a genuine seat handoff).
    forceHandoffOverride: Boolean? = null,
    // M6e TAMASHA / DEMO. When true the table shows a "watch only" banner (the advisor is auto-playing
    // the human seat, so the action bar is informational — the player is a spectator).
    spectator: Boolean = false,
    // The human player's chosen display name — shown in moment beats and chat.
    humanDisplayName: String = "Khiladi",
) {
    var localPhase by remember { mutableStateOf<GamePhase?>(initialLocalPhase) }
    val gamePhase = deriveGamePhase(state, localPhase)

    // Reset localPhase when engine moves away from Turn phase
    LaunchedEffect(state.view.phase) {
        if (state.view.phase !is PhaseView.Turn) {
            localPhase = null
        }
    }

    val humanSeat = state.view.viewer

    // ── Informatics overlay state ─────────────────────────────────────────────
    var showGazette by remember { mutableStateOf(false) }
    var gazetteInitialTab by remember { mutableStateOf(0) }
    var showChit by remember { mutableStateOf<ChitContent?>(initialChit) }
    var chitAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var showPrimer by remember { mutableStateOf(showPrimerOverride ?: !PrimerPrefs.hasSeenPrimer) }
    // DARBAR narrative chat panel
    var showDarbar by remember { mutableStateOf(false) }

    // ── M5 PASS-AND-PLAY handoff guard ────────────────────────────────────────
    // In a multi-human (hot-seat) game the active seat changes between DIFFERENT humans. To stop one
    // player seeing another's hand, we blank the table behind a full-screen guard whenever control
    // hands off to a new human seat, until they tap "ready". [revealedSeat] is the seat the current
    // player has acknowledged; while activeSeat != revealedSeat the guard is up. Single-human vs-AI
    // never trips this (the active seat is always 0).
    var revealedSeat by remember { mutableStateOf(state.activeSeat) }
    val handoffPending =
        forceHandoffOverride ?: (
            state.isPassAndPlay &&
                state.isHumanTurn &&
                state.activeSeat != null &&
                state.activeSeat != revealedSeat
        )

    val onOpenGazette: () -> Unit = { showGazette = true }
    val onOpenGazetteMatrix: () -> Unit = {
        gazetteInitialTab = 3
        showGazette = true
    }

    // MEASURED moment anchors (M4 §1): one registry per game screen. Opponent plates, the
    // human hand and the treasury medallion report their on-screen bounds into it; the moment
    // overlay rebases those to fire coin-trails / stamps / handoffs at the REAL seat instead of
    // a proportional ellipse guess. Provided here so every descendant layout writes into one store.
    val anchorRegistry =
        remember {
            com.kursi.designsystem.moment
                .TableAnchorRegistry()
        }

    CompositionLocalProvider(
        com.kursi.designsystem.moment.LocalTableAnchorRegistry provides anchorRegistry,
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val isDesktop = maxWidth >= 840.dp

            if (isDesktop) {
                DesktopLayout(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    localPhase = localPhase,
                    onLocalPhase = { localPhase = it },
                    onAction = onAction,
                    onOpenGazette = onOpenGazette,
                    onShowChit = { content, anchor ->
                        showChit = content
                        chitAnchor = anchor
                    },
                    onToggleCoach = onToggleCoach,
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                )
            } else {
                PhoneLayout(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    localPhase = localPhase,
                    onLocalPhase = { localPhase = it },
                    onAction = onAction,
                    onOpenGazette = onOpenGazette,
                    onShowChit = { content, anchor ->
                        showChit = content
                        chitAnchor = anchor
                    },
                    onToggleCoach = onToggleCoach,
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                    humanDisplayName = humanDisplayName,
                )
            }

            // M6e TAMASHA — a watch-only banner pinned to the top while the demo auto-plays.
            if (spectator) {
                val voice = LocalKursiVoice.current
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                            .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(6.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = voice.spectatorBanner,
                        style = KursiType.caption.copy(fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = KursiNeutrals.Cream,
                    )
                }
            }

            // ── DARBAR toggle FAB — brass button, top-end, clear of SpectatorBanner ──
            if (state.narrativeEnabled) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = if (spectator) 40.dp else 8.dp, end = 10.dp)
                            .size(44.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (showDarbar) {
                                    BrandTokens.GoldAntique
                                } else {
                                    BrandTokens.TeakDark.copy(alpha = 0.92f)
                                },
                            ).border(
                                1.5.dp,
                                BrandTokens.GoldAntique.copy(alpha = if (showDarbar) 0.0f else 0.8f),
                                androidx.compose.foundation.shape.CircleShape,
                            ).clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                            ) {
                                showDarbar = !showDarbar
                                if (showDarbar) onAction(GameAction.MarkChatRead)
                            }.semantics {
                                contentDescription =
                                    if (showDarbar) "Darbar chat — close" else "Darbar chat${if (state.unreadChat > 0) ", ${state.unreadChat} unread" else ""}"
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "💬",
                        style = KursiType.label_sm.copy(fontSize = 18.sp),
                    )
                    // Unread badge
                    if (state.unreadChat > 0 && !showDarbar) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .size(16.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(BrandTokens.StampRed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (state.unreadChat > 9) "9+" else state.unreadChat.toString(),
                                style = KursiType.caption.copy(fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = KursiNeutrals.Cream,
                            )
                        }
                    }
                }
            }

            // ── Overlay layers (drawn on top of the layout) ───────────────────────
            val chit = showChit
            if (chit != null) {
                WhisperChit(
                    content = chit,
                    onDismiss = { showChit = null },
                    anchorBounds = chitAnchor,
                    containerSize =
                        androidx.compose.ui.unit.IntSize(
                            constraints.maxWidth,
                            constraints.maxHeight,
                        ),
                )
            }

            if (showGazette) {
                NiyamGazette(
                    onDismiss = { showGazette = false },
                    onReplayPrimer = {
                        showGazette = false
                        PrimerPrefs.reset()
                        showPrimer = true
                    },
                    initialTab = gazetteInitialTab,
                )
            }

            if (showPrimer) {
                SwearingInPrimer(
                    onDone = { showPrimer = false },
                )
            }

            // DARBAR narrative chat panel — drawn above primer, below handoff guard.
            if (state.narrativeEnabled && showDarbar) {
                DarbarPanel(
                    state = state,
                    onAction = onAction,
                    onClose = { showDarbar = false },
                )
            }

            // PASS-AND-PLAY handoff guard — drawn last so it fully occludes the table (secrecy).
            if (handoffPending) {
                val seatName =
                    state.activeSeat
                        ?.let { state.opponentPersonas[PlayerId(it)]?.name }
                        ?: LocalKursiVoice.current.selfName
                HandoffGuard(
                    nextSeatName = seatName,
                    onReady = { revealedSeat = state.activeSeat },
                )
            }
        }
    }
}

// ─────────────────────────── Handoff guard (M5 pass-and-play) ───────────────────────────

/**
 * Full-screen "pass the device" guard shown between two different hot-seat human players so a
 * player never glimpses another's hand. Occludes the entire table; the next player taps to reveal.
 */
@Composable
internal fun HandoffGuard(
    nextSeatName: String,
    onReady: () -> Unit,
) {
    val voice = LocalKursiVoice.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BrandTokens.TeakInk)
                .clickable(
                    indication = null,
                    interactionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        },
                    onClick = onReady,
                ).semantics {
                    contentDescription = "${voice.handoffTitle}. ${voice.handoffPrompt(nextSeatName)} ${voice.handoffReveal}"
                    liveRegion = LiveRegionMode.Assertive
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(32.dp),
        ) {
            Text(
                text = voice.handoffTitle,
                style = KursiType.display.copy(fontSize = 22.sp, letterSpacing = 3.sp).rozha(),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(BrandTokens.TeakDark)
                        .border(1.5.dp, BrandTokens.BrassAged.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(
                    text = voice.handoffPrompt(nextSeatName),
                    style = KursiType.title.copy(fontSize = 18.sp),
                    color = KursiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = voice.handoffSecrecy,
                style = KursiType.caption.copy(fontSize = 12.sp),
                color = KursiNeutrals.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.18f))
                        .border(1.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onReady)
                        .padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(
                    text = voice.handoffReveal,
                    style = KursiType.name.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                    color = BrandTokens.GoldAntique,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────── Darbar Panel (narrative chat) ──────────────────

/**
 * Full-table chat overlay — the "Darbar" (court / assembly). Shown when narrativeEnabled=true and
 * the player opens it via the brass FAB. Features:
 *  - Scrolling chat feed rendered as SpeechBubble composables from the design system.
 *  - Quick-reply chips (ChatSuggestion list) the player taps to speak.
 *  - A "live kissa" arc indicator when one or more story arcs are in flight.
 *  - Closes on scrim tap or the close affordance; marks chat read on open.
 */
@Composable
internal fun DarbarPanel(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    onClose: () -> Unit,
) {
    // Resolve display name + accent for a given senderSeat.
    fun nameForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): String =
        when {
            isNarrator -> "Sutradhar"
            fromPlayer -> "Aap"
            else -> state.opponentPersonas[PlayerId(senderSeat)]?.name ?: "Seat $senderSeat"
        }

    fun accentForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): Color =
        when {
            isNarrator -> BrandTokens.GoldAntique
            fromPlayer -> BrandTokens.BrassAged
            else ->
                state.opponentPersonas[PlayerId(senderSeat)]
                    ?.let { Color(it.seatColorArgb) }
                    ?: if (senderSeat >= 0) KursiSeatColors[senderSeat] else BrandTokens.BrassAged
        }

    fun monogramForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): String? =
        when {
            isNarrator -> null
            fromPlayer -> null
            else -> state.opponentPersonas[PlayerId(senderSeat)]?.monogram
        }

    val listState = rememberLazyListState()
    val feedSize = state.chatFeed.size

    // Auto-scroll to the newest message whenever the feed grows.
    LaunchedEffect(feedSize) {
        if (feedSize > 0) listState.animateScrollToItem(feedSize - 1)
    }

    // Full-screen scrim + panel.
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent scrim — tap outside the panel to close.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BrandTokens.TeakInk.copy(alpha = 0.72f))
                    .clickable(
                        indication = null,
                        interactionSource =
                            remember {
                                androidx.compose.foundation.interaction
                                    .MutableInteractionSource()
                            },
                        onClick = onClose,
                    ),
        )

        // Panel card — occupies most of the screen, centered, max 560dp wide.
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight(0.88f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(BrandTokens.TeakDark)
                    .border(
                        1.5.dp,
                        Brush.verticalGradient(
                            listOf(BrandTokens.GoldAntique.copy(alpha = 0.85f), BrandTokens.BrassDark.copy(alpha = 0.5f)),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    // Prevent tap-through to the scrim when tapping inside the panel.
                    .clickable(
                        indication = null,
                        interactionSource =
                            remember {
                                androidx.compose.foundation.interaction
                                    .MutableInteractionSource()
                            },
                        onClick = {},
                    ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Title bar ────────────────────────────────────────────────
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(BrandTokens.TeakInk.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "DARBAR",
                        style = KursiType.display.copy(fontSize = 14.sp, letterSpacing = 3.sp).rozha(),
                        color = BrandTokens.GoldAntique,
                    )
                    Text(
                        text = "· Mehfil",
                        style = KursiType.caption.copy(fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = KursiNeutrals.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    // Live kissa / arc indicator
                    if (state.activeArcs.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BrandTokens.StampRed.copy(alpha = 0.85f))
                                    .border(0.8.dp, BrandTokens.StampRed, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "live kissa",
                                style =
                                    KursiType.caption.copy(
                                        fontSize = 8.sp,
                                        letterSpacing = 0.8.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    ),
                                color = KursiNeutrals.Cream,
                            )
                        }
                    }
                    // Close affordance
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(BrandTokens.BrassAged.copy(alpha = 0.18f))
                                .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            style = KursiType.label_sm.copy(fontSize = 13.sp),
                            color = KursiNeutrals.TextMuted,
                        )
                    }
                }

                // Thin gold divider
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrandTokens.GoldAntique.copy(alpha = 0.35f)),
                )

                // ── Chat feed ─────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.chatFeed.isEmpty()) {
                        item {
                            Text(
                                text = "Koi baat nahin abhi tak...",
                                style =
                                    KursiType.caption.copy(
                                        fontSize = 12.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    ),
                                color = KursiNeutrals.TextMuted,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        items(state.chatFeed, key = { it.id }) { msg ->
                            val name = nameForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
                            val accent = accentForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
                            val monogram = monogramForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
                            val emphatic =
                                msg.tone in
                                    listOf(
                                        com.kursi.feature.game.narrative.MessageTone.HOSTILE,
                                        com.kursi.feature.game.narrative.MessageTone.BOAST,
                                        com.kursi.feature.game.narrative.MessageTone.PANICKED,
                                    )
                            SpeechBubble(
                                speakerName = name,
                                text = msg.body,
                                accent = accent,
                                fromPlayer = msg.fromPlayer,
                                modifier = Modifier.fillMaxWidth(),
                                monogram = monogram,
                                emphatic = emphatic,
                            )
                        }
                    }
                }

                // Thin gold divider
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrandTokens.GoldAntique.copy(alpha = 0.22f)),
                )

                // ── Quick-reply chips ─────────────────────────────────────
                if (state.chatSuggestions.isNotEmpty()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(BrandTokens.TeakInk.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(state.chatSuggestions, key = { it.id }) { suggestion ->
                                Column(
                                    modifier =
                                        Modifier
                                            .widthIn(min = 80.dp, max = 180.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BrandTokens.TeakDark)
                                            .border(
                                                1.dp,
                                                BrandTokens.BrassAged.copy(alpha = 0.7f),
                                                RoundedCornerShape(10.dp),
                                            ).clickable {
                                                onAction(
                                                    GameAction.SendChat(
                                                        com.kursi.feature.game.narrative.HumanChatInput(
                                                            suggestionId = suggestion.id,
                                                            kind = suggestion.kind,
                                                            arc = suggestion.arc,
                                                            targetSeat = suggestion.targetSeat,
                                                        ),
                                                    ),
                                                )
                                            }.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = suggestion.label,
                                        style = KursiType.label_sm.copy(fontSize = 12.sp),
                                        color = KursiNeutrals.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val consequence = suggestion.consequence
                                    if (consequence != null) {
                                        Text(
                                            text = consequence,
                                            style =
                                                KursiType.caption.copy(
                                                    fontSize = 9.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                ),
                                            color = KursiNeutrals.TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Desktop Layout ───────────────────────────
// Hero surface: the FELT TABLE fills the center. Opponents arc across the top of
// the felt; deck+treasury sit in the middle; hand+dock below; log on the right rail.

@Composable
internal fun DesktopLayout(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    localPhase: GamePhase?,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onOpenGazette: () -> Unit = {},
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onToggleCoach: (() -> Unit)? = null,
    soundEnabled: Boolean = false,
    reducedMotion: Boolean = false,
) {
    // Full-window dark ink background
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(KursiFeltColors.Ink),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Status Spine (full width gold bar) ──────────────────────
            StatusSpineBar(
                state = state,
                gamePhase = gamePhase,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Hint Rail (always-on situational hint + NIYAM button) ──
            HintRail(
                gamePhase = gamePhase,
                state = state,
                onOpenGazette = onOpenGazette,
                modifier = Modifier.fillMaxWidth(),
                onToggleCoach = onToggleCoach,
                onPlayBestMove = { onAction(GameAction.PlayBestMove) },
            )

            // ── What-just-happened recap (Clarity, Tenet 1 — always shown) ──
            RecapRail(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())

            // ── Main body: felt table + log rail ──────────────────────
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── FELT TABLE COLUMN (the hero surface) ──────────────
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Felt surface wraps: opponents + deck+treasury.
                    // BoxWithConstraints gives us the felt px-size so the moment overlay
                    // can build proportional table anchors and layer ABOVE the felt but
                    // BELOW the action dock (which is a sibling row below this Column).
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) {
                        val feltWidthPx = constraints.maxWidth.toFloat()
                        val feltHeightPx = constraints.maxHeight.toFloat()

                        FeltTableSurface(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // ── Opponents arc across top of the felt — CAPPED so a sparse
                                //    table doesn't let the strip eat the felt and starve the heart.
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 248.dp),
                                ) {
                                    OpponentArc(
                                        state = state,
                                        gamePhase = gamePhase,
                                        onLocalPhase = onLocalPhase,
                                        onShowChit = onShowChit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // ── Deck / treasury / reaction spotlight — the table heart now
                                //    OWNS the remaining felt (incl. the formerly-dead bottom). A
                                //    radial pedestal glow roots the medallion and fills the lower felt.
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .drawBehind { drawHeartPedestal() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FeltCenterTokens(state = state, gamePhase = gamePhase, onShowChit = onShowChit)
                                }
                            }
                        }

                        // ── Action-moment stamp theatre: above the felt surface, below the dock ──
                        GameMomentLayer(
                            state = state,
                            widthPx = feltWidthPx,
                            heightPx = feltHeightPx,
                            soundEnabled = soundEnabled,
                            reducedMotion = reducedMotion,
                        )
                    }

                    // ── Bottom: hand + dock side by side ──────────────────
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // YOUR HAND
                        YourHandPanel(
                            state = state,
                            gamePhase = gamePhase,
                            humanSeat = humanSeat,
                            onAction = onAction,
                            onShowChit = onShowChit,
                            modifier =
                                Modifier
                                    .weight(0.40f),
                        )
                        // ACTION DOCK
                        ActionDock(
                            state = state,
                            gamePhase = gamePhase,
                            humanSeat = humanSeat,
                            onLocalPhase = onLocalPhase,
                            onAction = onAction,
                            onShowChit = onShowChit,
                            modifier =
                                Modifier
                                    .weight(0.60f),
                        )
                    }
                }

                // ── LOG RAIL (right, ~290dp) ──────────────────────────
                Box(
                    modifier =
                        Modifier
                            .width(290.dp)
                            .fillMaxHeight(),
                ) {
                    GameLog(state = state, onShowChit = onShowChit)
                }
            }
        }
    }
}

// ─────────────────────────── decoPanel (P3 depth surface) ──────────────────
// A tactile teak panel with layered contact shadow, brass emboss edge and a thin
// brass rim — used for the hand, action dock and Roznamcha log so they read as
// raised instruments on the table, not flat fills. [lifted] raises elevation when
// the panel owns the player's attention (their turn / a pending reaction).

@Composable
internal fun Modifier.decoPanel(
    radius: androidx.compose.ui.unit.Dp = KursiRadii.md,
    lifted: Boolean = false,
): Modifier {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(radius)
    val rimAlpha = if (lifted) 0.85f else 0.45f
    return this
        .tableDepth(shape, elevation = 7.dp, lifted = lifted)
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    KursiFeltColors.Surface3.copy(alpha = 0.96f),
                    KursiFeltColors.Surface2,
                    BrandTokens.TeakDark,
                ),
            ),
        ).border(
            if (lifted) 1.5.dp else 1.dp,
            Brush.horizontalGradient(
                listOf(
                    BrandTokens.GoldAntique.copy(alpha = rimAlpha),
                    BrandTokens.BrassAged.copy(alpha = rimAlpha * 0.7f),
                    BrandTokens.BrassDark.copy(alpha = rimAlpha),
                ),
            ),
            shape,
        ).embossEdge(radius)
}

internal fun isValidTarget(
    opp: OpponentView,
    action: Action,
    state: GameUiState,
): Boolean {
    if (opp.eliminated) return false
    return state.legalIntents.filterIsInstance<Intent.DeclareAction>().any { intent ->
        Rules.targetOf(intent.action) == opp.id && actionsSameType(intent.action, action)
    }
}

internal fun actionsSameType(
    a: Action,
    b: Action,
): Boolean =
    when {
        a is Action.Coup && b is Action.Coup -> true
        a is Action.Assassinate && b is Action.Assassinate -> true
        a is Action.Steal && b is Action.Steal -> true
        else -> false
    }

/**
 * The recommended target for [action] during target-select — the seat the player should aim at,
 * surfaced explicitly so they're not left to read per-plate suspicion pips on their own.
 *
 * Resolution (PUBLIC-info only; never touches a hidden card):
 *  1. The DECISION-COACH's own pick — among the advisor's [Intent.DeclareAction] entries of this
 *     action TYPE, the recommended one (else highest winProb). This is the AI brain's read, already
 *     computed for the human, so it stays inside the secrecy boundary.
 *  2. Fallback when advice hasn't arrived: the weakest valid target by public state — fewest
 *     face-down cards (closest to elimination), tie-broken by lowest coins, then seat order.
 *
 * Returns the target [PlayerId] and a short reason ("coach's pick" / "weakest seat"), or null when
 * no valid target exists (the dock then just shows the tap hint).
 */
internal fun recommendedTarget(
    action: Action,
    state: GameUiState,
): Pair<PlayerId, String>? {
    // 1) Coach's own pick for this action type.
    val coachPick =
        state.advice
            .filter { it.intent is Intent.DeclareAction }
            .filter { actionsSameType((it.intent as Intent.DeclareAction).action, action) }
            .let { matches -> matches.firstOrNull { it.recommended } ?: matches.maxByOrNull { it.winProb } }
            ?.let { (it.intent as Intent.DeclareAction).action }
            ?.let { Rules.targetOf(it) }
    if (coachPick != null) return coachPick to "coach's pick"

    // 2) Weakest valid target by public state — fewest face-down, then fewest coins.
    val weakest =
        state.view.players
            .filter { isValidTarget(it, action, state) }
            .minWithOrNull(
                compareBy<OpponentView> { it.faceDownCount }
                    .thenBy { it.coins }
                    .thenBy { it.seatIndex },
            )
            ?: return null
    return weakest.id to "weakest seat"
}

@Composable
internal fun WinnerBanner(
    gamePhase: GamePhase.GameOver,
    state: GameUiState,
) {
    val voice = LocalKursiVoice.current
    val winnerPersona = state.opponentPersonas[com.kursi.engine.PlayerId(gamePhase.winnerSeat)]
    val winnerColor =
        if (winnerPersona != null) {
            Color(winnerPersona.seatColorArgb)
        } else {
            KursiSeatColors[gamePhase.winnerSeat]
        }
    val winnerLabel = personaNameOrDefault(com.kursi.engine.PlayerId(gamePhase.winnerSeat), state, voice.selfName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Brass winner roundel
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged, BrandTokens.BrassDark),
                        ),
                    ).border(2.dp, BrandTokens.GoldAntique, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "I",
                style = KursiType.display.copy(fontSize = 36.sp).rozha(),
                color = BrandTokens.TeakDark,
                textAlign = TextAlign.Center,
            )
        }
        val isHumanWinner = gamePhase.winnerSeat == state.view.viewer.raw
        Text(
            text = if (isHumanWinner) voice.youWin else voice.opponentWins(winnerLabel),
            style = KursiType.display.copy(fontSize = 28.sp).rozha(),
            color = winnerColor,
            textAlign = TextAlign.Center,
        )
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.md))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.70f))
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)),
                        Squircle(KursiRadii.md),
                    ).padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = voice.gameEndSub,
                style = KursiType.title.copy(letterSpacing = 2.sp),
                color = BrandTokens.GoldAntique,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun ReactionSpotlight(
    gamePhase: GamePhase.ReactionWindow,
    state: GameUiState,
) {
    val claimedRole = gamePhase.claimedRole
    val actorName = personaNameOrDefault(gamePhase.actor, state)

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseAlpha",
    )

    // P4 reaction theatre: stage the claim as a focal event ON the table's heart —
    // a debossed brass disc behind the claimed certificate, lit by a role-hued glow,
    // framed like a stamp about to be ruled on. (Coordinates with, does not duplicate,
    // the moment overlay's Challenge/Reveal beats.)
    val roleHue = claimedRole?.let { KursiColors.forRole(it).color } ?: BrandTokens.GoldAntique
    Box(contentAlignment = Alignment.Center) {
        // Focal backdrop disc — brass medallion staging
        Box(
            modifier =
                Modifier
                    .size(300.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .drawBehind {
                        val c =
                            androidx.compose.ui.geometry
                                .Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        // Debossed brass well
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    BrandTokens.BrassDark.copy(alpha = 0.28f),
                                    BrandTokens.TeakInk.copy(alpha = 0.0f),
                                ),
                                center = c,
                                radius = r,
                            ),
                        )
                        // Role-hued focal glow rising behind the card
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    roleHue.copy(alpha = 0.30f * pulseAlpha),
                                    Color.Transparent,
                                ),
                                center = c,
                                radius = r * 0.75f,
                            ),
                        )
                        drawCircle(
                            BrandTokens.GoldAntique.copy(alpha = 0.4f),
                            r * 0.92f,
                            c,
                            style =
                                androidx.compose.ui.graphics.drawscope
                                    .Stroke(1.5.dp.toPx()),
                        )
                    },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$actorName claims",
                style = KursiType.label,
                color = KursiNeutrals.TextSecondary,
            )
            if (claimedRole != null) {
                // RETRO-FUTURIST §2 — the live claim medallion gets a holographic
                // brass↔role-hue↔cyan rim glint that travels the bezel as the claim
                // sits under judgement, marking it as the one LIVE focal instrument.
                val holoPhase by rememberInfiniteTransition(label = "claimHolo").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
                    label = "claimHoloPhase",
                )
                Box(
                    modifier =
                        Modifier
                            .scale(0.96f + 0.06f * pulseAlpha)
                            .holoRimLight(
                                accent = roleHue,
                                phase = holoPhase,
                                cornerRadius = KursiRadii.xl,
                                intensity = 0.55f + 0.45f * pulseAlpha,
                            ).shadow(
                                16.dp,
                                Squircle(KursiRadii.xl),
                                clip = false,
                                ambientColor = roleHue,
                                spotColor = roleHue.copy(alpha = 0.7f),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    RoleCard(role = claimedRole, size = CardSize.Medium, lifted = true)
                }
                Text(
                    text = roleLabel(claimedRole),
                    style = KursiType.cardRole.rozha(),
                    color = KursiColors.forRole(claimedRole).color,
                )
            }
        }
    }
}

// ─────────────────────────── Phone Layout ───────────────────────────

@Composable
internal fun PhoneLayout(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    localPhase: GamePhase?,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onOpenGazette: () -> Unit = {},
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onToggleCoach: (() -> Unit)? = null,
    soundEnabled: Boolean = false,
    reducedMotion: Boolean = false,
    humanDisplayName: String = "Khiladi",
) {
    // HintRail is only relevant when the human has something to decide. During bot
    // turns (Idle) it just repeats the StatusSpine — save the vertical space.
    val isPlayerTurn =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm ||
            gamePhase is GamePhase.ReactionWindow ||
            gamePhase is GamePhase.LoseInfluence ||
            gamePhase is GamePhase.Exchange ||
            gamePhase is GamePhase.InvestigatePeek

    FeltTableBackground(modifier = Modifier.fillMaxSize()) {
        // Box so we can layer the full-screen moment overlay ABOVE the UI Column without
        // changing the Column's layout contract (no verticalScroll, all heights fixed/weighted).
        Box(modifier = Modifier.fillMaxSize()) {
            // No verticalScroll — everything must be on screen at once so the action
            // dock is always reachable without scrolling. Heights are fixed/weighted
            // so the layout adapts to any phone size without overflow.
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ── TOP: turn status + what-just-happened (always visible) ──────────
                StatusSpineBar(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())
                RecapRail(state = state, gamePhase = gamePhase, modifier = Modifier.fillMaxWidth())

                // ── MIDDLE: opponents + felt + hand (fills remaining space) ──────────
                val opponentCount = state.view.players.count { it.id != state.view.viewer }
                val columns = if (opponentCount <= 2) 2 else 3

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Measure the available width to compute the exact plate width that fits the
                    // grid cells. OpponentPlate uses .size(width=plateWidth) so we must pass the
                    // actual cell width — the default 176.dp overflows on phones (cell ≈ 169dp).
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gapTotal = 6.dp * (columns - 1)
                        val cellWidth = (maxWidth - gapTotal) / columns
                        // Clamp to OpponentPlate's safe range so widthFactor stays in [0.78, 1.30]
                        val plateWidth = cellWidth.coerceIn(136.dp, 228.dp)

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (opponentCount <= 2) 104.dp else 172.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val opponents = state.view.players.filter { it.id != state.view.viewer }
                            items(opponents) { opp ->
                                OpponentChipItem(
                                    opp = opp,
                                    state = state,
                                    gamePhase = gamePhase,
                                    onLocalPhase = onLocalPhase,
                                    onShowChit = onShowChit,
                                    plateWidth = plateWidth,
                                )
                            }
                        }
                    }

                    // Compact felt area — centre tokens only. The moment overlay is now
                    // full-screen (sibling Box below), so it's removed from this 80dp strip.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FeltCenterTokens(state = state, gamePhase = gamePhase, onShowChit = onShowChit)
                    }

                    // Hand panel takes all remaining middle space.
                    YourHandPanel(
                        state = state,
                        gamePhase = gamePhase,
                        humanSeat = humanSeat,
                        onAction = onAction,
                        onShowChit = onShowChit,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── BOTTOM: hint (only on player turn) + action dock ────────────────
                // HintRail lives here so it's paired visually with the action dock
                // rather than buried above the table where the eye doesn't look.
                if (isPlayerTurn) {
                    HintRail(
                        gamePhase = gamePhase,
                        state = state,
                        onOpenGazette = onOpenGazette,
                        modifier = Modifier.fillMaxWidth(),
                        onToggleCoach = onToggleCoach,
                        onPlayBestMove = { onAction(GameAction.PlayBestMove) },
                    )
                }

                ActionDock(
                    state = state,
                    gamePhase = gamePhase,
                    humanSeat = humanSeat,
                    onLocalPhase = onLocalPhase,
                    onAction = onAction,
                    onShowChit = onShowChit,
                    compact = true,
                )

                // ── ROZNAMCHA / DARBAR — collapsible bottom drawer with two tabs ──
                CollapsibleLogDrawer(state = state, onShowChit = onShowChit, onAction = onAction)
            }

            // Full-screen moment overlay — sits ABOVE the Column but never blocks input when idle.
            // Must be here (not inside the 80dp felt strip) so coin trails and stamps span the
            // full game area: opponent plates at the top to the human hand at the bottom.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                GameMomentLayer(
                    state = state,
                    widthPx = constraints.maxWidth.toFloat(),
                    heightPx = constraints.maxHeight.toFloat(),
                    soundEnabled = soundEnabled,
                    reducedMotion = reducedMotion,
                    humanDisplayName = humanDisplayName,
                )
            }
        } // end wrapper Box
    }
}

/**
 * Phone: a tap-to-expand drawer at the bottom of the game screen.
 *
 * In narrative mode it shows two tabs — ROZNAMCHA (game event log) and DARBAR (bot chat
 * history). The DARBAR tab is the fix for "I didn't notice what the bots were saying" —
 * the full conversation is always one tap away, no hidden 💬 button needed.
 *
 * In non-narrative mode the DARBAR tab is hidden and the drawer behaves as before.
 */
@Composable
internal fun CollapsibleLogDrawer(
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    onAction: (GameAction) -> Unit = {},
) {
    val voice = LocalKursiVoice.current
    var expanded by remember { mutableStateOf(false) }
    // 0 = ROZNAMCHA, 1 = DARBAR
    var activeTab by remember { mutableStateOf(0) }
    val eventCount = state.recentEvents.size
    val chatCount = state.chatFeed.size
    val unread = state.unreadChat
    val hasNarrative = state.narrativeEnabled

    // Auto-switch to DARBAR tab when new unread chat arrives and drawer is already open
    LaunchedEffect(chatCount) {
        if (expanded && hasNarrative && unread > 0) {
            activeTab = 1
            onAction(GameAction.MarkChatRead)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Handle row ──────────────────────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(Squircle(KursiRadii.sm))
                    .background(Color(0xFF120D07))
                    .border(
                        1.dp,
                        (if (unread > 0 && !expanded) BrandTokens.StampRed else BrandTokens.GoldAntique).copy(alpha = 0.6f),
                        Squircle(KursiRadii.sm),
                    ).inspectable(onClick = { expanded = !expanded }, onLongClick = {})
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(KursiSemantics.Success),
            )
            // ROZNAMCHA label
            Text(
                text = voice.logPanelHeader,
                style =
                    KursiType.label_sm.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 2.sp,
                    ),
                color = if (activeTab == 0 && expanded) BrandTokens.GoldAntique else BrandTokens.GoldAntique.copy(alpha = 0.55f),
            )
            // DARBAR tab label — only when narrative mode is on
            if (hasNarrative) {
                Text(
                    text = "·",
                    color = BrandTokens.BrassDark,
                    style = KursiType.label_sm,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .clip(Squircle(KursiRadii.xs))
                            .clickable(
                                indication = null,
                                interactionSource =
                                    remember {
                                        androidx.compose.foundation.interaction
                                            .MutableInteractionSource()
                                    },
                            ) {
                                activeTab = 1
                                if (!expanded) expanded = true
                                onAction(GameAction.MarkChatRead)
                            }.padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "DARBAR",
                        style =
                            KursiType.label_sm.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 2.sp,
                            ),
                        color =
                            if (activeTab == 1 && expanded) {
                                BrandTokens.GoldAntique
                            } else if (unread > 0) {
                                BrandTokens.StampRed
                            } else {
                                BrandTokens.GoldAntique.copy(alpha = 0.55f)
                            },
                    )
                    // Unread badge — shows even when drawer is closed so player knows new chat arrived
                    if (unread > 0 && !expanded) {
                        Box(
                            modifier =
                                Modifier
                                    .size(14.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(BrandTokens.StampRed),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (unread > 9) "9+" else unread.toString(),
                                style = KursiType.label_micro.copy(fontSize = 7.sp),
                                color = KursiNeutrals.Cream,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (expanded) "▾" else "▴ $eventCount",
                style =
                    KursiType.label_micro.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                color = BrandTokens.BrassAged,
            )
        }

        // ── Expanded body ───────────────────────────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selector bar — only shown in narrative mode
                if (hasNarrative) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, start = 4.dp, end = 4.dp)
                                .clip(Squircle(KursiRadii.xs))
                                .background(Color(0xFF120D07).copy(alpha = 0.7f)),
                    ) {
                        listOf("ROZNAMCHA" to 0, "DARBAR" to 1).forEach { (label, idx) ->
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clickable(
                                            indication = null,
                                            interactionSource =
                                                remember {
                                                    androidx.compose.foundation.interaction
                                                        .MutableInteractionSource()
                                                },
                                        ) {
                                            activeTab = idx
                                            if (idx == 1) onAction(GameAction.MarkChatRead)
                                        }.background(
                                            if (activeTab == idx) {
                                                BrandTokens.BrassDark.copy(alpha = 0.5f)
                                            } else {
                                                Color.Transparent
                                            },
                                        ).padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style =
                                        KursiType.label_micro.copy(
                                            letterSpacing = 1.5.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        ),
                                    color =
                                        if (activeTab == idx) {
                                            BrandTokens.GoldAntique
                                        } else {
                                            BrandTokens.BrassAged.copy(alpha = 0.5f)
                                        },
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 4.dp)) {
                    if (!hasNarrative || activeTab == 0) {
                        GameLog(state = state, onShowChit = onShowChit)
                    } else {
                        // ── DARBAR tab: inline chat feed so the player can review what bots said ──
                        DarbarLogInline(state = state)
                    }
                }
            }
        }
    }
}

/**
 * Inline chat log for the DARBAR tab inside CollapsibleLogDrawer.
 * Shows the full Darbar chat history in a scrollable list — same content as the full
 * DarbarPanel overlay, but embedded in the existing bottom drawer so players don't need
 * to discover the hidden 💬 FAB. Newest message auto-scrolls into view.
 */
@Composable
internal fun DarbarLogInline(state: GameUiState) {
    fun nameForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): String =
        when {
            isNarrator -> "Sutradhar"
            fromPlayer -> "Aap"
            else -> state.opponentPersonas[PlayerId(senderSeat)]?.name ?: "Seat $senderSeat"
        }

    fun accentForSeat(
        senderSeat: Int,
        isNarrator: Boolean,
        fromPlayer: Boolean,
    ): Color =
        when {
            isNarrator -> BrandTokens.GoldAntique
            fromPlayer -> BrandTokens.BrassAged
            else ->
                state.opponentPersonas[PlayerId(senderSeat)]
                    ?.let { Color(it.seatColorArgb) }
                    ?: if (senderSeat >= 0) KursiSeatColors[senderSeat] else BrandTokens.BrassAged
        }

    val listState = rememberLazyListState()
    val feedSize = state.chatFeed.size
    LaunchedEffect(feedSize) {
        if (feedSize > 0) listState.animateScrollToItem(feedSize - 1)
    }

    if (state.chatFeed.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Koi baat nahin abhi tak...",
                style =
                    KursiType.caption.copy(
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                color = KursiNeutrals.TextMuted,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.chatFeed, key = { it.id }) { msg ->
            val name = nameForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
            val accent = accentForSeat(msg.senderSeat, msg.isNarrator, msg.fromPlayer)
            val emphatic =
                msg.tone in
                    listOf(
                        com.kursi.feature.game.narrative.MessageTone.HOSTILE,
                        com.kursi.feature.game.narrative.MessageTone.BOAST,
                        com.kursi.feature.game.narrative.MessageTone.PANICKED,
                    )
            SpeechBubble(
                speakerName = name,
                text = msg.body,
                accent = accent,
                fromPlayer = msg.fromPlayer,
                modifier = Modifier.fillMaxWidth(),
                monogram = null,
                emphatic = emphatic,
            )
        }
    }
}

// ─────────────────────────── WHAT-JUST-HAPPENED recap (Clarity, Tenet 1) ──────
// A compact, plain-language recap of the most-recent resolved beat, shown near the
// spine on EVERY phase. ALWAYS shown — NOT gated under the coach (this is
// comprehension, not advice). Routes all copy through KursiVoice (bilingual).

/** Returns true for events that merit a plain-language recap line. */
internal fun isRecapWorthy(ev: GameEvent): Boolean =
    when (ev) {
        is GameEvent.ActionDeclared, is GameEvent.Blocked, is GameEvent.Challenged,
        is GameEvent.ChallengeRevealed, is GameEvent.InfluenceLost,
        is GameEvent.PlayerEliminated, is GameEvent.CoinsTransferred,
        is GameEvent.Exchanged, is GameEvent.GameEnded,
        -> true
        else -> false
    }

/** The most recent event worth recapping in plain words (skips bookkeeping noise). */
internal fun mostRecentRecapEvent(state: GameUiState): GameEvent? = state.recentEvents.lastOrNull { isRecapWorthy(it) }

/** The last [n] recap-worthy events, oldest first. Used by IdleDock feed. */
internal fun lastNRecapEvents(
    state: GameUiState,
    n: Int,
): List<GameEvent> = state.recentEvents.filter { isRecapWorthy(it) }.takeLast(n)

/** Resolve the primary + secondary display names a recap line needs for [event]. */
internal fun recapNames(
    event: GameEvent,
    state: GameUiState,
): Pair<String, String?> {
    fun n(id: PlayerId) = personaNameOrDefault(id, state)
    return when (event) {
        is GameEvent.ActionDeclared -> n(event.actor) to null
        is GameEvent.Blocked -> n(event.blocker) to null
        is GameEvent.Challenged -> n(event.challenger) to n(event.target)
        is GameEvent.ChallengeRevealed -> n(event.player) to null
        is GameEvent.InfluenceLost -> n(event.player) to null
        is GameEvent.PlayerEliminated -> n(event.player) to null
        is GameEvent.CoinsTransferred -> n(event.from) to n(event.to)
        is GameEvent.Exchanged -> n(event.actor) to null
        is GameEvent.GameEnded -> n(event.winner) to null
        else -> "" to null
    }
}

/**
 * Top-of-screen recap strip — always a single compact line to preserve hand visibility.
 *
 * During PickAction with narrative mode on, swaps to show the last bot chat message so
 * the player can't miss what was said to them while bots were taking turns. Falls back
 * to the normal PICHLA DAV game-event recap when there's no chat. Full thread is in DARBAR.
 *
 * This MUST stay single-line: any vertical expansion here directly reduces the weight(1f)
 * middle Column, squishing YourHandPanel to zero height on small phones.
 */
@Composable
internal fun RecapRail(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val voice = LocalKursiVoice.current
    val isPickAction = gamePhase is GamePhase.PickAction

    // During the player's action phase in narrative mode, surface the last bot chat first —
    // the player may have been distracted during bot turns and missed what was said to them.
    val lastBotMsg =
        if (isPickAction && state.narrativeEnabled) {
            state.chatFeed.lastOrNull { !it.fromPlayer && !it.isNarrator }
        } else {
            null
        }

    if (lastBotMsg != null) {
        val senderPlayer = state.view.players.firstOrNull { it.seatIndex == lastBotMsg.senderSeat }
        val senderName = senderPlayer?.let { state.opponentPersonas[it.id]?.name } ?: "Bot"
        val chatLine = "\"${lastBotMsg.body}\""
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(Squircle(KursiRadii.sm))
                    .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                    .border(
                        KursiDimens.stroke_hairline,
                        KursiNeutrals.TextMuted.copy(alpha = 0.6f),
                        Squircle(KursiRadii.sm),
                    ).padding(horizontal = KursiDimens.space_sm, vertical = 5.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Darbar — $senderName: ${lastBotMsg.body}"
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(Squircle(KursiRadii.xs))
                        .background(KursiNeutrals.TextMuted.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "💬 $senderName",
                    style = KursiType.label_micro.copy(letterSpacing = 0.6.sp),
                    color = BrandTokens.GoldAntique,
                    maxLines = 1,
                )
            }
            Text(
                text = chatLine,
                style = KursiType.label_sm,
                color = KursiNeutrals.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    // No bot chat to surface — show the normal game-event recap.
    val event = mostRecentRecapEvent(state) ?: return
    val (actor, other) = recapNames(event, state)
    val line = voice.recap(event, actor, other) ?: return
    val label = if (isPickAction) "PICHLA DAV" else voice.recapLabel

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Squircle(KursiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                .border(
                    KursiDimens.stroke_hairline,
                    BrandTokens.BrassDark.copy(alpha = 0.5f),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = KursiDimens.space_sm, vertical = 5.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$label: $line"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.xs))
                    .background(BrandTokens.BrassDark.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = KursiType.label_micro.copy(letterSpacing = 0.6.sp),
                color = BrandTokens.GoldAntique,
                maxLines = 1,
            )
        }
        Text(
            text = line,
            style = KursiType.label_sm,
            color = KursiNeutrals.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// ─────────────────────────── StatusSpine ───────────────────────────

@Composable
internal fun StatusSpineBar(
    state: GameUiState,
    gamePhase: GamePhase,
    modifier: Modifier = Modifier,
) {
    val humanSeat = state.view.viewer
    val voice = LocalKursiVoice.current
    val (text, tone) = deriveSpineTextAndTone(state, gamePhase, humanSeat, voice)
    // A11y (M3 §1): the spine is the running narration of the table — whose turn it is and what
    // just happened. Mark it a polite live region so a screen reader announces each change without
    // the player having to hunt for it, and carry the text as the spoken description.
    StatusSpine(
        text = text,
        tone = tone,
        modifier =
            modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

internal fun deriveSpineTextAndTone(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    voice: KursiVoice,
): Pair<String, SpineTone> =
    when (gamePhase) {
        is GamePhase.Idle -> {
            val actorId =
                when (val phase = state.view.phase) {
                    is PhaseView.Turn -> phase.actor
                    is PhaseView.Reactions -> phase.actor
                    is PhaseView.InfluenceLoss -> phase.loser
                    is PhaseView.Exchange -> phase.actor
                    else -> null
                }
            val actorName = actorId?.let { personaNameOrDefault(it, state) } ?: actorName(state)
            voice.opponentActing(actorName) to SpineTone.Info
        }
        is GamePhase.PickAction -> {
            if (state.view.myCoins >= 10) {
                "${voice.forcedCoup} (${state.view.myCoins} coins)" to SpineTone.Danger
            } else {
                voice.yourTurn to SpineTone.Gold
            }
        }
        is GamePhase.PickTarget -> {
            val verb = targetVerb(gamePhase.action)
            val role = Rules.claimedRole(gamePhase.action)
            val roleStr = if (role != null) " (claiming ${roleLabel(role)})" else ""
            "${actionName(gamePhase.action)} — choose who to $verb.$roleStr" to SpineTone.Gold
        }
        is GamePhase.Confirm -> {
            val targetName = gamePhase.target?.let { personaNameOrDefault(it, state, voice.selfName) }
            val summary = confirmSummary(gamePhase.action, gamePhase.target, targetName)
            "$summary — Declare or Cancel." to SpineTone.Gold
        }
        is GamePhase.ReactionWindow -> {
            when (gamePhase.step) {
                ReactionStep.CHALLENGE_ACTION -> {
                    "${voice.challengePrompt} — ${actionName(gamePhase.action)}" to SpineTone.Pending
                }
                ReactionStep.BLOCK -> {
                    voice.blockPrompt to SpineTone.Pending
                }
                ReactionStep.CHALLENGE_BLOCK -> {
                    val blockerName = gamePhase.blocker?.let { personaNameOrDefault(it, state) } ?: "Someone"
                    "$blockerName blocks. ${voice.challengePrompt}" to SpineTone.Pending
                }
            }
        }
        is GamePhase.LoseInfluence -> {
            voice.loseInfluence to SpineTone.Danger
        }
        is GamePhase.Exchange -> {
            voice.exchange to SpineTone.Gold
        }
        is GamePhase.InvestigatePeek -> {
            val targetName = personaNameOrDefault(gamePhase.target, state)
            val seen = gamePhase.peekedRole?.let { " You saw ${roleLabel(it)}." } ?: ""
            "JAANCH — $targetName's card.$seen Spike it or leave it?" to SpineTone.Gold
        }
        is GamePhase.GameOver -> {
            val humanSeatIdx = humanSeat.raw
            if (gamePhase.winnerSeat == humanSeatIdx) {
                voice.youWin to SpineTone.Gold
            } else {
                val winnerName = personaNameOrDefault(com.kursi.engine.PlayerId(gamePhase.winnerSeat), state)
                voice.opponentWins(winnerName) to SpineTone.Info
            }
        }
    }

// ─────────────────────────── Action Dock ───────────────────────────

@Composable
internal fun ActionDock(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onLocalPhase: (GamePhase?) -> Unit,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    // When true (phone layout) suppresses per-chip consequence text to save vertical space.
    compact: Boolean = false,
) {
    // P2: the dock lifts when it's the player's turn or a reaction is on them.
    val docLifted =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm ||
            gamePhase is GamePhase.ReactionWindow ||
            gamePhase is GamePhase.LoseInfluence ||
            gamePhase is GamePhase.Exchange ||
            gamePhase is GamePhase.InvestigatePeek
    Box(modifier = modifier.fillMaxWidth().decoPanel(lifted = docLifted)) {
        Box(modifier = Modifier.padding(12.dp)) {
            when (gamePhase) {
                is GamePhase.PickAction ->
                    PickActionDock(
                        state = state,
                        humanSeat = humanSeat,
                        onLocalPhase = onLocalPhase,
                        onAction = onAction,
                        onShowChit = onShowChit,
                        compact = compact,
                    )
                is GamePhase.PickTarget ->
                    PickTargetDock(
                        action = gamePhase.action,
                        state = state,
                        onLocalPhase = onLocalPhase,
                    )
                is GamePhase.Confirm ->
                    ConfirmDock(
                        action = gamePhase.action,
                        target = gamePhase.target,
                        humanSeat = humanSeat,
                        state = state,
                        onLocalPhase = onLocalPhase,
                        onAction = onAction,
                    )
                is GamePhase.ReactionWindow ->
                    ReactionDock(
                        reactionWindow = gamePhase,
                        humanSeat = humanSeat,
                        state = state,
                        onAction = onAction,
                        onShowChit = onShowChit,
                    )
                is GamePhase.LoseInfluence -> LoseInfluenceDock(state = state)
                is GamePhase.Exchange ->
                    ExchangeDock(
                        state = state,
                        humanSeat = humanSeat,
                        onAction = onAction,
                    )
                is GamePhase.InvestigatePeek ->
                    InvestigatePeekDock(
                        phase = gamePhase,
                        state = state,
                        onAction = onAction,
                    )
                is GamePhase.Idle -> IdleDock(state = state)
                is GamePhase.GameOver -> GameOverDock(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
internal fun ReactionDock(
    reactionWindow: GamePhase.ReactionWindow,
    humanSeat: PlayerId,
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalKursiVoice.current
    val alertRed = Color(0xFF8E2B22)
    val brassColor = BrandTokens.BrassAged
    val verdigris = Color(0xFF3F6B5E)

    // Map a reaction option to its coach advice (null until advisor finishes, or when coach is OFF).
    val challengeAdvice = if (state.coachEnabled) adviceFor(state, Intent.Challenge(humanSeat)) else null
    val passAdvice = if (state.coachEnabled) adviceFor(state, Intent.Pass(humanSeat)) else null

    // The CLAIM under scrutiny for a challenge: the actor's action-claim on a CHALLENGE_ACTION /
    // CHALLENGE_BLOCK-of-the-action, the blocker's block-role on a CHALLENGE_BLOCK. Used to build the
    // coach's belief-grounded "is this a bluff?" read (public card-accounting only).
    // Gated: the belief banner is proactive guidance — only shown when coach is ON.
    val scrutinisedRole: Role? =
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_BLOCK -> reactionWindow.blockRole
            else -> reactionWindow.claimedRole
        }
    val scrutinisedLabel: String =
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_BLOCK -> "block"
            else -> actionName(reactionWindow.action)
        }
    val challengeBelief: String? =
        if (state.coachEnabled) {
            scrutinisedRole?.let { challengeBeliefLine(state, it, scrutinisedLabel) }
        } else {
            null
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── The READ, made visible: the coach's belief-grounded line about the claim on the table,
        // so the player sees WHY a challenge is favourable (or a gamble) before they even long-press.
        if (challengeBelief != null) {
            val favourable = challengeBelief.contains("favourable") || challengeBelief.contains("real odds")
            val readAccent = if (favourable) KursiSemantics.Success else BrandTokens.GoldAntique
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(Squircle(KursiRadii.sm))
                        .background(readAccent.copy(alpha = 0.14f))
                        .border(KursiDimens.stroke_hairline, readAccent.copy(alpha = 0.55f), Squircle(KursiRadii.sm))
                        .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(text = "🔎", style = KursiType.label_sm)
                Text(
                    text = challengeBelief,
                    style = KursiType.label_sm,
                    color = KursiNeutrals.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when (reactionWindow.step) {
            ReactionStep.CHALLENGE_ACTION -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReactionChip(
                        label = "⚡ ${voice.challengeBtn.uppercase()}",
                        familyColor = alertRed,
                        advice = challengeAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                        onShowChit = onShowChit,
                        beliefLine = challengeBelief,
                        consequence = voice.reactionChallengeConsequence,
                    )
                    ReactionChip(
                        label = "↩ ${voice.passChallenge.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintChallenge,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ReactionStep.BLOCK -> {
                val blockRoles = Rules.rolesThatBlock(reactionWindow.action)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    blockRoles.forEach { role ->
                        ReactionChip(
                            label = "🛡 BLOCK (${roleLabel(role)})",
                            familyColor = verdigris,
                            advice = if (state.coachEnabled) adviceFor(state, Intent.Block(humanSeat, role)) else null,
                            onClick = { onAction(GameAction.Submit(Intent.Block(humanSeat, role))) },
                            onShowChit = onShowChit,
                            consequence = voice.reactionBlockConsequence(role),
                        )
                    }
                    if (reactionWindow.myLegalResponses.any { it is Intent.Challenge }) {
                        ReactionChip(
                            label = "⚡ ${voice.challengeBtn.uppercase()}",
                            familyColor = alertRed,
                            advice = challengeAdvice,
                            onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                            onShowChit = onShowChit,
                            beliefLine = challengeBelief,
                            consequence = voice.reactionChallengeConsequence,
                        )
                    }
                    ReactionChip(
                        label = "↩ ${voice.passBlock.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintBlock,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ReactionStep.CHALLENGE_BLOCK -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReactionChip(
                        label = "⚡ ${voice.challengeBtn.uppercase()}",
                        familyColor = alertRed,
                        advice = challengeAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Challenge(humanSeat))) },
                        onShowChit = onShowChit,
                        beliefLine = challengeBelief,
                        consequence = voice.reactionChallengeConsequence,
                    )
                    ReactionChip(
                        label = "↩ ${voice.passBlock.uppercase()}",
                        familyColor = brassColor,
                        advice = passAdvice,
                        onClick = { onAction(GameAction.Submit(Intent.Pass(humanSeat))) },
                        onShowChit = onShowChit,
                        consequence = voice.reactionPassConsequence,
                    )
                }
                Text(
                    text = voice.reactionHintChallengeBlock,
                    style = KursiType.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = KursiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * A reaction option chip with the DECISION-COACH read baked in:
 *  - truthful/bluff TINT (green / oxblood) on the chip border + fill when the move claims a role,
 *  - a compact safety badge + odds pill beneath the label,
 *  - a brass RECOMMENDED star on the advisor's pick (with a rim glow),
 *  - long-press opens the full coach chit (rationale + odds + win chance).
 * Falls back to the plain familyColor styling until advice arrives (or for no-claim moves).
 */
@Composable
internal fun ReactionChip(
    label: String,
    familyColor: Color,
    advice: com.kursi.ai.advisor.MoveAdvice?,
    onClick: () -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    // Belief-grounded read shown in the long-press coach chit (e.g. on a CHALLENGE chip).
    beliefLine: String? = null,
    // CLARITY (Tenet 1) — always-shown plain "what this does + its risk" line. Ungated.
    consequence: String? = null,
) {
    val tone = advice?.let { coachTone(it.truthful, it.bluff) } ?: CoachTone.Neutral
    val recommended = advice?.recommended == true
    // Tint the chip by coaching tone when the move makes a role claim; otherwise keep familyColor.
    val chipColor = if (advice != null && tone != CoachTone.Neutral) coachAccent(tone) else familyColor
    val ringColor = if (recommended) BrandTokens.GoldAntique else chipColor.copy(alpha = 0.6f)
    val ringWidth = if (recommended) 2.dp else KursiDimens.stroke_ring_idle

    // Is this a challenge (odds = P(opponent bluffing)) or a bluff move (odds = P(safe))?
    val isChallenge = advice?.intent is Intent.Challenge

    var chipBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier =
                Modifier
                    .onGloballyPositioned { chipBounds = it.boundsInRoot() }
                    .heightIn(min = 52.dp)
                    .widthIn(min = 96.dp, max = 200.dp)
                    .clip(Squircle(KursiDimens.r_md))
                    .background(chipColor.copy(alpha = 0.15f))
                    .border(ringWidth, ringColor, Squircle(KursiDimens.r_md))
                    .reactionChipSemantics(label = label, recommended = recommended)
                    .inspectable(
                        onClick = onClick,
                        onLongClick = { if (advice != null) onShowChit(coachChitOf(advice, beliefLine), chipBounds) },
                        pressShape = Squircle(KursiDimens.r_md),
                    ).padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (recommended) RecommendedStar()
                AutoSizeText(
                    text = label,
                    style = KursiType.label_sm,
                    color = KursiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    minSize = 8.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        // Coach read strip under the chip: safety badge + odds pill.
        if (advice != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val claimedName = (advice.intent as? Intent.Block)?.role?.let { roleLabel(it) }
                if (tone != CoachTone.Neutral) {
                    CoachBadge(truthful = advice.truthful, bluff = advice.bluff, claimedRoleName = claimedName)
                }
                advice.successOdds?.let { odds ->
                    CoachOddsPill(isChallenge = isChallenge, successOdds = odds)
                }
            }
        }
        // CLARITY (Tenet 1) — ALWAYS-SHOWN what-now line: what this reaction does + its risk.
        // Ungated by the coach; pure comprehension.
        if (consequence != null) {
            Text(
                text = consequence,
                style = KursiType.label_micro,
                color = KursiNeutrals.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

@Composable
internal fun LoseInfluenceDock(state: GameUiState) {
    val voice = LocalKursiVoice.current
    val cause = loseInfluenceCause(state)
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // WHY this card is being sacrificed — the clear cause line.
        Box(
            modifier =
                Modifier
                    .clip(Squircle(KursiRadii.sm))
                    .background(KursiSemantics.Danger.copy(alpha = 0.14f))
                    .border(KursiDimens.stroke_hairline, KursiSemantics.Danger.copy(alpha = 0.6f), Squircle(KursiRadii.sm))
                    .padding(horizontal = KursiDimens.space_sm, vertical = KursiDimens.space_xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = "💀", style = KursiType.label_sm)
                Text(
                    text = cause,
                    style = KursiType.label_md,
                    color = KursiSemantics.Danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            text = voice.centerPrompt(CenterPrompt.LoseInfluence),
            style = KursiType.body,
            color = KursiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The clear "why am I losing a card" cause line for the human's influence-loss prompt — folding the
 * engine's [com.kursi.engine.LossReason] (carried PUBLIC-safe on [PhaseView.InfluenceLoss]) together
 * with the aggressor reconstructed from the recent public event stream, so the player reads
 * "Lost to Bhai Teja's Supari" instead of a bare "choose a card".
 */
internal fun loseInfluenceCause(state: GameUiState): String {
    val phase =
        state.view.phase as? PhaseView.InfluenceLoss
            ?: return "You must give up a card."
    val events = state.recentEvents
    // Most-recent declared action = candidate Supari/Khela aggressor; most-recent challenge = the
    // challenger who exposed your bluff. Mirrors GameSession.routeGrudges' causality reconstruction.
    val lastDeclared = events.filterIsInstance<GameEvent.ActionDeclared>().lastOrNull()
    val lastChallenge = events.filterIsInstance<GameEvent.Challenged>().lastOrNull()

    fun name(id: PlayerId) = personaNameOrDefault(id, state)
    return when (phase.reason) {
        com.kursi.engine.LossReason.ASSASSINATED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's Supari." else "Lost to a Supari hit."
        }
        com.kursi.engine.LossReason.COUPED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's Khela." else "Lost to a Khela."
        }
        com.kursi.engine.LossReason.LOST_CHALLENGE -> {
            val who = lastChallenge?.challenger?.let { name(it) }
            if (who != null) {
                "Your bluff was caught — $who challenged and you couldn't prove it."
            } else {
                "Your claim was challenged and didn't hold."
            }
        }
        com.kursi.engine.LossReason.LOST_BLOCK_CHALLENGE -> {
            val who = lastChallenge?.challenger?.let { name(it) }
            if (who != null) {
                "Your block was challenged by $who — and it didn't hold."
            } else {
                "Your block was challenged and didn't hold."
            }
        }
        com.kursi.engine.LossReason.SABOTAGED -> "You played Bali Khel — sacrificed an influence for coins."
        com.kursi.engine.LossReason.EMERGENCY_COUPED -> {
            val who = lastDeclared?.actor?.let { name(it) }
            if (who != null) "Lost to $who's ADHYADESH." else "Lost to an Emergency Coup."
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExchangeDock(
    state: GameUiState,
    humanSeat: PlayerId,
    onAction: (GameAction) -> Unit,
) {
    val voice = LocalKursiVoice.current
    val exchangeIntents = state.legalIntents.filterIsInstance<Intent.ChooseExchange>()

    // Resolve every CardId the human can see during the exchange to its real role.
    // SECRECY-SAFE: both sources are the viewer's OWN cards only — myCards (face-down hand +
    // face-up reveals) and PhaseView.Exchange.drawn (populated ONLY for the exchanging actor's
    // own view). We never touch an opponent's hidden card.
    val drawnCards: List<OwnCard> = (state.view.phase as? PhaseView.Exchange)?.drawn.orEmpty()
    val drawnIds: Set<CardId> = drawnCards.map { it.id }.toSet()
    val cardById: Map<CardId, OwnCard> =
        (state.view.myCards + drawnCards).associateBy { it.id }

    // The advisor's recommended keep, if advice has arrived. advice covers ChooseExchange intents
    // (one entry per legal intent, exactly one recommended). Star that option in the UI.
    val recommendedKeep: Set<CardId>? =
        state.advice
            .firstOrNull { it.recommended }
            ?.let { (it.intent as? Intent.ChooseExchange)?.keep?.toSet() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = voice.centerPrompt(CenterPrompt.Exchange),
            style = KursiType.body,
            color = KursiNeutrals.TextSecondary,
        )
        // Lay the keep-options out in a 2-wide wrapping grid so they fill the wide dock
        // panel instead of crowding a thin left column with a large empty void to the right.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
        ) {
            exchangeIntents.forEach { intent ->
                val keepCards = intent.keep.mapNotNull { cardById[it] }
                val recommended = recommendedKeep != null && intent.keep.toSet() == recommendedKeep
                ExchangeKeepOption(
                    keepCards = keepCards,
                    drawnIds = drawnIds,
                    recommended = recommended,
                    onClick = { onAction(GameAction.Submit(intent)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One keep-choice in the exchange dock: shows each card the option would KEEP as a mini RoleCard
 * with a clear "Keep NETA + BHAI" label, tags each card as DRAWN vs HAND, and stars the advisor's
 * recommended keep. Replaces the old blind "Keep option N".
 */
@Composable
internal fun ExchangeKeepOption(
    keepCards: List<OwnCard>,
    drawnIds: Set<CardId>,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        if (keepCards.isEmpty()) {
            "Keep —"
        } else {
            "Keep " + keepCards.joinToString(" + ") { roleLabel(it.role) }
        }
    val accent = if (recommended) KursiSemantics.Success else BrandTokens.BrassAged

    Column(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.md))
                .background(KursiFeltColors.Surface3)
                .border(
                    width = if (recommended) 2.dp else 1.dp,
                    color = accent.copy(alpha = if (recommended) 1f else 0.5f),
                    shape = Squircle(KursiRadii.md),
                ).keepOptionSemantics(
                    roleNames = keepCards.map { roleLabelA11y(it.role) },
                    recommended = recommended,
                ).clickable(onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (recommended) RecommendedStar()
            Text(
                text = label,
                style = KursiType.label_sm,
                color = if (recommended) KursiSemantics.Success else KursiNeutrals.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            keepCards.forEach { card ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RoleCard(role = card.role, size = CardSize.Small)
                    Text(
                        text = if (card.id in drawnIds) "DRAWN" else "HAND",
                        style = KursiType.label_sm.copy(letterSpacing = 1.sp),
                        color = if (card.id in drawnIds) BrandTokens.GoldAntique else KursiNeutrals.TextMuted,
                    )
                }
            }
        }
    }
}

/**
 * Jaanch follow-up dock — after the PATRAKAAR examiner privately peeks one of the target's hidden
 * cards, they decide whether to SPIKE it (force the target to shuffle it back and redraw) or LEAVE it.
 * SECRECY: the peeked role is shown ONLY here, in the examiner's own dock (it arrives via the
 * examiner-only PhaseView.InvestigatePeek.examinedCard); no other seat ever sees it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InvestigatePeekDock(
    phase: GamePhase.InvestigatePeek,
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val redrawIntent =
        state.legalIntents
            .filterIsInstance<Intent.ResolveInvestigate>()
            .firstOrNull { it.forceRedraw }
    val keepIntent =
        state.legalIntents
            .filterIsInstance<Intent.ResolveInvestigate>()
            .firstOrNull { !it.forceRedraw }
    val targetName = personaNameOrDefault(phase.target, state)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "JAANCH · $targetName's card",
            style = KursiType.label_sm.copy(letterSpacing = 1.sp),
            color = BrandTokens.GoldAntique,
        )
        if (phase.peekedRole != null) {
            RoleCard(role = phase.peekedRole, size = CardSize.Small)
            Text(
                text = "You peeked: ${roleLabel(phase.peekedRole)}. Spike it back into the deck, or leave it?",
                style = KursiType.body,
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "Decide whether to force $targetName to redraw the examined card.",
                style = KursiType.body,
                color = KursiNeutrals.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (redrawIntent != null) {
                InvestigateChoice(
                    label = "SPIKE IT (redraw)",
                    accent = Color(0xFF8E2B22),
                    onClick = { onAction(GameAction.Submit(redrawIntent)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (keepIntent != null) {
                InvestigateChoice(
                    label = "LEAVE IT",
                    accent = BrandTokens.BrassAged,
                    onClick = { onAction(GameAction.Submit(keepIntent)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun InvestigateChoice(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(Squircle(KursiRadii.md))
                .background(accent.copy(alpha = 0.15f))
                .border(1.dp, accent.copy(alpha = 0.6f), Squircle(KursiRadii.md))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KursiType.label_sm.copy(letterSpacing = 1.sp),
            color = KursiNeutrals.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun GameOverDock(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val winnerSeat = state.winnerSeat ?: 0
    val winnerColor = KursiSeatColors[winnerSeat]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KursiActionButton(
            label = "Play Again",
            sublabel = "${state.view.config.seatCount} players",
            roleAccent = KursiSemantics.Success,
            enabled = true,
            onClick = { onAction(GameAction.NewGame(playerCount = state.view.config.seatCount)) },
        )
        KursiActionButton(
            label = "New Game (4 players)",
            enabled = true,
            onClick = { onAction(GameAction.NewGame(playerCount = 4)) },
        )
    }
}

// ─────────────────────────── Game Log ───────────────────────────
// Modern voiced log: ROZNAMCHA header, grouped rows, icon-led entries.

/**
 * ROZNAMCHA — the sarkari-teleprinter terminal (M4 §3). The primary "what-just-happened"
 * surface. Events grouped by turn into FILE blocks, persona-colored, outcome-badged, each row
 * long-pressable into a full chit. A retro CRT / inked-ribbon texture (scanlines + phosphor
 * vignette + brass masthead) reads premium-futurist. Auto-scrolls to the freshest line; when the
 * reader scrolls up to inspect history, a JUMP-TO-LATEST control surfaces. A sticky current-turn
 * header keeps the active FILE pinned. Multiplatform-safe Canvas/graphics only.
 */
@Composable
internal fun GameLog(
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val voice = LocalKursiVoice.current
    val listState = rememberLazyListState()
    val groups =
        remember(state.recentEvents) {
            groupLogByTurn(collapseLogEvents(state.recentEvents, state, voice.selfName))
        }
    // Flatten groups → header + rows so the LazyColumn can sticky-header per FILE block.
    val rows = remember(groups) { flattenLogGroups(groups) }

    // Auto-scroll to the freshest line as new events land.
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
    }

    // "Scrolled away from the bottom" → reveal the jump-to-latest control.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= rows.size - 1
        }
    }

    // Current active turn (for the sticky header): the latest FILE in the feed.
    val currentTurn = groups.lastOrNull()

    LogTerminalShell(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Masthead ──
                LogMasthead(voice = voice)

                // ── Sticky current-turn (ACTIVE FILE) header ──
                if (currentTurn != null) {
                    LogActiveFileHeader(
                        text = voice.logCurrentTurn(currentTurn.turn, currentTurn.actorName),
                        accent = currentTurn.actorColor,
                    )
                }

                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = voice.logEmpty,
                            style =
                                KursiType.caption.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                ),
                            color = KursiNeutrals.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        items(rows.size) { idx ->
                            when (val r = rows[idx]) {
                                is LogFlatRow.Group ->
                                    LogFileBanner(
                                        text = voice.logTurnGroup(r.turn, r.actorName),
                                        accent = r.actorColor,
                                    )
                                is LogFlatRow.Entry ->
                                    CollapsedLogEntry(
                                        entry = r.entry,
                                        state = state,
                                        onShowChit = onShowChit,
                                    )
                            }
                        }
                    }
                }
            }

            // ── Jump-to-latest control — only when scrolled up into history ──
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && rows.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                JumpToLatestPill(
                    label = voice.logJumpToLatest,
                    onClick = {},
                    listState = listState,
                    targetIndex = rows.size - 1,
                )
            }
        }
    }
}

// ─────────────────────────── Teleprinter terminal chrome (M4 §3) ────────────

/** The CRT / inked-ribbon terminal shell: dark phosphor body, brass rim, scanlines + vignette. */
@Composable
internal fun LogTerminalShell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(KursiRadii.sm)
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .tableDepth(shape, elevation = 8.dp, lifted = true)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF120D07), // deep ink top
                            Color(0xFF1A130B),
                            Color(0xFF0E0A06), // shadowed bottom
                        ),
                    ),
                ).drawBehind { drawTeleprinterTexture() }
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            BrandTokens.GoldAntique.copy(alpha = 0.7f),
                            BrandTokens.BrassDark.copy(alpha = 0.5f),
                        ),
                    ),
                    shape,
                ),
    ) {
        content()
    }
}

/** Phosphor scanlines + a soft green-amber CRT vignette. Multiplatform-safe Canvas. */
internal fun DrawScope.drawTeleprinterTexture() {
    // Horizontal scanlines — the inked-ribbon / CRT raster.
    val gap = 3.dp.toPx()
    var y = 0f
    val lineColor = Color.Black.copy(alpha = 0.18f)
    while (y < size.height) {
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += gap
    }
    // Warm phosphor glow at the top where fresh print lands.
    drawRect(
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        BrandTokens.GoldAntique.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                startY = 0f,
                endY = size.height * 0.4f,
            ),
    )
    // Corner vignette so the tube edges fall into shadow.
    drawRect(
        brush =
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f,
            ),
    )
}

/** Masthead: ROZNAMCHA wordmark + a sarkari live-feed tag and a blinking phosphor cursor dot. */
@Composable
internal fun LogMasthead(voice: KursiVoice) {
    val pulse = rememberInfiniteTransition(label = "logCursor")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "logDot",
    )
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(KursiSemantics.Success.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = voice.logPanelHeader,
                style =
                    KursiType.label_sm.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        letterSpacing = 3.sp,
                    ),
                color = BrandTokens.GoldAntique,
            )
        }
        Text(
            text = voice.logTeleprinterTag,
            style =
                KursiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    fontSize = 8.sp,
                ),
            color = BrandTokens.BrassAged.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 13.dp, top = 1.dp),
        )
    }
    // Perforated tear-strip divider under the masthead.
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 8.dp)) {
        val r = 1.dp.toPx()
        val step = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawCircle(BrandTokens.GoldAntique.copy(alpha = 0.30f), r, Offset(x, size.height / 2f))
            x += step
        }
    }
}

/** Sticky ACTIVE-FILE header pinned at the top of the feed — persona-accented brass strip. */
@Composable
internal fun LogActiveFileHeader(
    text: String,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clip(Squircle(KursiRadii.xs))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.05f)),
                    ),
                ).border(
                    0.5.dp,
                    accent.copy(alpha = 0.5f),
                    Squircle(KursiRadii.xs),
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style =
                KursiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontSize = 9.sp,
                ),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A per-turn FILE banner inside the scrolling body (grouped-by-turn divider). */
@Composable
internal fun LogFileBanner(
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .background(accent.copy(alpha = 0.85f)),
        )
        Text(
            text = text,
            style =
                KursiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontSize = 8.5.sp,
                ),
            color = accent.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(accent.copy(alpha = 0.20f)))
    }
}

/** Jump-to-latest pill — scrolls the feed to the freshest line on tap. */
@Composable
internal fun JumpToLatestPill(
    label: String,
    onClick: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    targetIndex: Int,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            Modifier
                .clip(
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(50),
                ).background(
                    Brush.horizontalGradient(
                        listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged),
                    ),
                ).border(
                    0.5.dp,
                    BrandTokens.GoldAntique,
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(50),
                ).inspectable(
                    onClick = {
                        onClick()
                        scope.launch { listState.animateScrollToItem(targetIndex.coerceAtLeast(0)) }
                    },
                    onLongClick = {},
                ).padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style =
                KursiType.label_micro.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    fontSize = 9.sp,
                ),
            color = BrandTokens.TeakInk,
            maxLines = 1,
        )
    }
}

// ─────────────────────────── Log grouping (M4 §3) ───────────────────────────

/** One turn's block of log entries, with the seat that owns the turn for persona accenting. */
internal data class LogTurnGroup(
    val turn: Int,
    val actorName: String,
    val actorColor: Color,
    val entries: List<LogEntry>,
)

/** Flattened stream the LazyColumn renders: a FILE banner followed by its entry rows. */
internal sealed interface LogFlatRow {
    data class Group(
        val turn: Int,
        val actorName: String,
        val actorColor: Color,
    ) : LogFlatRow

    data class Entry(
        val entry: LogEntry,
    ) : LogFlatRow
}

/**
 * Groups the collapsed entry stream into per-turn FILE blocks. [LogEntry.RoundDivider] marks a
 * new turn boundary; entries before the first divider land in a synthetic opening block.
 */
internal fun groupLogByTurn(entries: List<LogEntry>): List<LogTurnGroup> {
    val groups = mutableListOf<LogTurnGroup>()
    var curTurn = 1
    var curName = ""
    var curColor = BrandTokens.BrassAged
    var bucket = mutableListOf<LogEntry>()

    fun flush() {
        if (bucket.isNotEmpty() || groups.isEmpty()) {
            groups.add(LogTurnGroup(curTurn, curName, curColor, bucket.toList()))
        }
        bucket = mutableListOf()
    }

    for (e in entries) {
        if (e is LogEntry.RoundDivider) {
            flush()
            curTurn = e.turn
            curName = e.actorName
            // RoundDivider doesn't carry color directly; the entries below resolve their own
            // persona color. Keep brass for the header band unless a colored marker exists.
            curColor = BrandTokens.BrassAged
        } else {
            bucket.add(e)
        }
    }
    flush()
    // Drop a leading empty synthetic block if real turns exist.
    return groups.filter { it.entries.isNotEmpty() || groups.size == 1 }
}

/** Turn groups → flat row stream with FILE banners. */
internal fun flattenLogGroups(groups: List<LogTurnGroup>): List<LogFlatRow> {
    val out = mutableListOf<LogFlatRow>()
    for (g in groups) {
        if (g.actorName.isNotEmpty()) {
            out.add(LogFlatRow.Group(g.turn, g.actorName, g.actorColor))
        }
        g.entries.forEach { out.add(LogFlatRow.Entry(it)) }
    }
    return out
}

// Collapsed log entry — either a single event or a merged safe-action row
sealed interface LogEntry {
    data class Single(
        val event: GameEvent,
    ) : LogEntry

    data class SafeAction(
        val actor: PlayerId,
        val action: Action,
    ) : LogEntry

    data class RoundDivider(
        val turn: Int,
        val actorName: String,
    ) : LogEntry
}

internal fun collapseLogEvents(
    events: List<GameEvent>,
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): List<LogEntry> {
    val result = mutableListOf<LogEntry>()
    var i = 0
    while (i < events.size) {
        val ev = events[i]
        // Collapse ActionDeclared + ActionResolved for safe actions (Income, ForeignAid)
        if (ev is GameEvent.ActionDeclared &&
            i + 1 < events.size &&
            events[i + 1] is GameEvent.ActionResolved
        ) {
            val resolved = events[i + 1] as GameEvent.ActionResolved
            val isSafe = ev.action == Action.Income || ev.action == Action.ForeignAid
            if (isSafe && ev.actor == resolved.actor) {
                result.add(LogEntry.SafeAction(actor = ev.actor, action = ev.action))
                i += 2
                continue
            }
        }
        if (ev is GameEvent.TurnAdvanced) {
            result.add(LogEntry.RoundDivider(ev.turnNumber, personaNameOrDefault(PlayerId(ev.toSeat), state, self)))
        } else {
            result.add(LogEntry.Single(ev))
        }
        i++
    }
    return result
}

/** Return the persona color for a PlayerId if available, otherwise TextSecondary. */
internal fun personaColorOrDefault(
    id: PlayerId,
    state: GameUiState,
): Color = state.opponentPersonas[id]?.let { Color(it.seatColorArgb) } ?: KursiNeutrals.TextSecondary

@Composable
internal fun CollapsedLogEntry(
    entry: LogEntry,
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalKursiVoice.current
    when (entry) {
        is LogEntry.SafeAction -> {
            val actorName = personaNameOrDefault(entry.actor, state)
            val actorColor = personaColorOrDefault(entry.actor, state)
            val icon =
                when (entry.action) {
                    Action.Income -> "🪙"
                    Action.ForeignAid -> "💵"
                    else -> "💰"
                }
            val voicedVerb = voice.logActorVerb(actorName, entry.action)
            val declared = GameEvent.ActionDeclared(entry.actor, entry.action, Rules.claimedRole(entry.action))
            LogRow(
                icon = icon,
                text = voicedVerb,
                textColor = BrandTokens.BrassAged.copy(alpha = 0.80f),
                outcome = OutcomeKind.Success,
                onInspect = { bounds ->
                    onShowChit(
                        ChitContent.LogEvent(
                            title = voicedVerb,
                            narration = voice.logNarration(declared, actorName),
                            icon = icon,
                        ),
                        bounds,
                    )
                },
            )
        }
        is LogEntry.RoundDivider -> {
            // Thin brass hairline divider with voiced text centered on it
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BrandTokens.BrassAged.copy(alpha = 0.25f)),
                )
                Box(
                    modifier =
                        Modifier
                            .background(KursiFeltColors.Surface1.copy(alpha = 0.90f))
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = voice.logRoundDivider(entry.turn, entry.actorName),
                        style = KursiType.caption,
                        color = BrandTokens.BrassAged.copy(alpha = 0.70f),
                    )
                }
            }
        }
        is LogEntry.Single -> {
            GameLogEntry(event = entry.event, state = state, onShowChit = onShowChit)
        }
    }
}

/**
 * Shared Roznamcha log row. Icon and outcome badge stay pinned to the top so the
 * voiced text can wrap to two lines and read in full — copy length varies a lot by
 * language and persona name, so single-line + ellipsis was clipping real sentences.
 */
@Composable
internal fun LogRow(
    icon: String,
    text: String,
    textColor: Color,
    outcome: OutcomeKind? = null,
    // Long-press the row → expand this event's deadpan narration in a chit. Receives the
    // row's captured root bounds so the chit anchors with a caret. Null → row is inert.
    onInspect: ((androidx.compose.ui.geometry.Rect?) -> Unit)? = null,
) {
    var rowBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 30.dp)
                .padding(vertical = 2.dp)
                .then(
                    if (onInspect != null) {
                        Modifier
                            .onGloballyPositioned { rowBounds = it.boundsInRoot() }
                            .inspectable(onClick = {}, onLongClick = { onInspect(rowBounds) })
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = icon,
            style = KursiType.caption,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = text,
            style = KursiType.caption.copy(lineHeight = 13.sp),
            color = textColor,
            modifier = Modifier.weight(1f).padding(top = 1.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (outcome != null) {
            OutcomeTag(kind = outcome, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
internal fun GameLogEntry(
    event: GameEvent,
    state: GameUiState,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val voice = LocalKursiVoice.current

    // Build the long-press inspect lambda for a row: opens a LogEvent chit with this
    // event's deadpan narration. [actor]/[other] feed the narrator the right names.
    fun inspect(
        title: String,
        icon: String,
        actor: String,
        other: String? = null,
    ): (androidx.compose.ui.geometry.Rect?) -> Unit =
        { bounds ->
            onShowChit(
                ChitContent.LogEvent(
                    title = title,
                    narration = voice.logNarration(event, actor, other),
                    icon = icon,
                ),
                bounds,
            )
        }
    when (event) {
        is GameEvent.ActionDeclared -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val icon = actionIcon(event.action)
            val target =
                (event.action as? Action.Steal)?.target
                    ?: (event.action as? Action.Assassinate)?.target
                    ?: (event.action as? Action.Coup)?.target
            val voicedText =
                if (target != null) {
                    voice.logActorVerbWithTarget(actorName, event.action, personaNameOrDefault(target, state))
                } else {
                    voice.logActorVerb(actorName, event.action)
                }
            LogRow(
                icon = icon,
                text = voicedText,
                textColor = BrandTokens.BrassAged.copy(alpha = 0.80f),
                outcome = OutcomeKind.Neutral,
                onInspect = inspect(voicedText, icon, actorName, target?.let { personaNameOrDefault(it, state) }),
            )
        }
        is GameEvent.ActionResolved -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, event.action)
            LogRow(
                icon = actionIcon(event.action),
                text = txt,
                textColor = KursiNeutrals.TextPrimary,
                outcome = OutcomeKind.Success,
                onInspect = inspect(txt, actionIcon(event.action), actorName),
            )
        }
        is GameEvent.ActionNegated -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, event.action)
            LogRow(
                icon = "🚫",
                text = txt,
                textColor = KursiNeutrals.TextMuted,
                outcome = OutcomeKind.Block,
                onInspect = inspect(txt, "🚫", actorName),
            )
        }
        is GameEvent.Challenged -> {
            val challengerName = personaNameOrDefault(event.challenger, state)
            val targetName = personaNameOrDefault(event.target, state)
            val txt = voice.logChallenged(challengerName, targetName)
            LogRow(
                icon = "⚡",
                text = txt,
                textColor = KursiSemantics.Danger,
                onInspect = inspect(txt, "⚡", targetName, challengerName),
            )
        }
        is GameEvent.ChallengeRevealed -> {
            val playerName = personaNameOrDefault(event.player, state)
            val voicedText =
                if (event.hadRole) {
                    voice.logBluffTrue(playerName)
                } else {
                    voice.logBluffCaught(playerName)
                }
            LogRow(
                icon = if (event.hadRole) "✓" else "✗",
                text = voicedText,
                textColor = KursiNeutrals.TextSecondary,
                outcome = if (event.hadRole) OutcomeKind.ChallengeFail else OutcomeKind.BluffCaught,
                onInspect = inspect(voicedText, if (event.hadRole) "✓" else "✗", playerName),
            )
        }
        is GameEvent.Blocked -> {
            val blockerName = personaNameOrDefault(event.blocker, state)
            val txt = voice.logBlocked(blockerName, roleLabel(event.role))
            LogRow(
                icon = "🛡",
                text = txt,
                textColor = Color(0xFF3F6B5E),
                outcome = OutcomeKind.Block,
                onInspect = inspect(txt, "🛡", blockerName, roleLabel(event.role)),
            )
        }
        is GameEvent.InfluenceLost -> {
            val playerName = personaNameOrDefault(event.player, state)
            val txt = voice.logInfluenceLost(playerName, roleLabel(event.role))
            LogRow(
                icon = "💀",
                text = txt,
                textColor = KursiSemantics.Danger,
                outcome = OutcomeKind.Eliminated,
                onInspect = inspect(txt, "💀", playerName),
            )
        }
        is GameEvent.PlayerEliminated -> {
            val playerName = personaNameOrDefault(event.player, state)
            val txt = voice.logEliminated(playerName)
            LogRow(
                icon = "🪦",
                text = txt,
                textColor = KursiSemantics.Danger,
                outcome = OutcomeKind.Eliminated,
                onInspect = inspect(txt, "🪦", playerName),
            )
        }
        is GameEvent.CoinsChanged -> {
            // Skip — noise
        }
        is GameEvent.CoinsTransferred -> {
            val fromName = personaNameOrDefault(event.from, state)
            val toName = personaNameOrDefault(event.to, state)
            val txt = "$fromName → $toName: ${event.amount} Khokhas"
            LogRow(
                icon = "🪙",
                text = txt,
                textColor = KursiNeutrals.TextMuted,
                onInspect = inspect(txt, "🪙", fromName, toName),
            )
        }
        is GameEvent.TurnAdvanced -> {
            // handled by RoundDivider in collapse pass — skip individual rendering
        }
        is GameEvent.GameEnded -> {
            val winnerName = personaNameOrDefault(event.winner, state)
            Text(
                text = voice.opponentWins(winnerName),
                style = KursiType.title,
                color = KursiFeltColors.GoldCoin,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        is GameEvent.Exchanged -> {
            val actorName = personaNameOrDefault(event.actor, state)
            val txt = voice.logActorVerb(actorName, Action.Exchange)
            LogRow(
                icon = "🔄",
                text = txt,
                textColor = KursiNeutrals.TextMuted,
                onInspect = inspect(txt, "🔄", actorName),
            )
        }
        else -> {
            // Other events — skip silently (CardReplaced, etc. are implementation noise)
        }
    }
}

internal fun actionIcon(action: Action): String =
    when (action) {
        Action.Income -> "🪙"
        Action.ForeignAid -> "💵"
        Action.Tax -> "💰"
        Action.Exchange -> "🔄"
        is Action.Steal -> "🪤"
        is Action.Investigate -> "🔍"
        is Action.Assassinate -> "🔪"
        is Action.Coup -> "💥"
        Action.BailPe -> "🛡️"
        Action.Sabotage -> "💸"
        is Action.Hawala -> "🤝"
        Action.Emergency -> "⚡"
    }

// ─────────────────────────── Helpers ───────────────────────────

internal fun actorName(
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): String =
    when (val phase = state.view.phase) {
        is PhaseView.Turn -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.Reactions -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.InfluenceLoss -> personaNameOrDefault(phase.loser, state, self)
        is PhaseView.Exchange -> personaNameOrDefault(phase.actor, state, self)
        is PhaseView.InvestigatePeek -> personaNameOrDefault(phase.examiner, state, self)
        is PhaseView.Over -> personaNameOrDefault(phase.winner, state, self)
    }

/** Fallback self-label used by non-composable helpers that have no [KursiVoice] in scope. */
internal const val DEFAULT_SELF_NAME = "Aap"

/**
 * The persona name for [id]: "Aap"/"You" ([self]) for the human viewer, the locked persona name
 * for a bot, and — only as a last resort when no persona is registered — a seat tag. NEVER a raw
 * "P{seat}" for a known seat: the viewer always resolves to [self], and bots always have a persona.
 */
internal fun personaNameOrDefault(
    id: PlayerId,
    state: GameUiState,
    self: String = DEFAULT_SELF_NAME,
): String =
    when {
        id == state.view.viewer -> self
        else -> state.opponentPersonas[id]?.name ?: "Seat ${id.raw}"
    }

internal fun actionName(action: Action): String =
    when (action) {
        Action.Income -> "Dehaadi"
        Action.ForeignAid -> "FDI"
        Action.Tax -> "Ghotala"
        Action.Exchange -> "Setting"
        is Action.Coup -> "Khela"
        is Action.Assassinate -> "Supari"
        is Action.Steal -> "Vasooli"
        is Action.Investigate -> "Jaanch"
        Action.BailPe -> "Bail Pe Bahar"
        Action.Sabotage -> "Bali Khel"
        is Action.Hawala -> "Hawala"
        Action.Emergency -> "Adhyadesh"
    }

internal fun roleLabel(role: Role): String =
    when (role) {
        Role.NETA -> "NETA"
        Role.BHAI -> "BHAI"
        Role.BABU -> "BABU"
        Role.JUGAADU -> "JUGAADU"
        Role.VAKIL -> "VAKIL"
        Role.PATRAKAAR -> "PATRAKAAR"
    }

// ─────────────────────────── Opponent claim summary (Phase TILES) ────────────

/**
 * A standing read of an opponent's role-claims, derived purely from the PUBLIC event
 * history ([GameUiState.recentEvents]). Unlike the transient pending-claim, this persists
 * as events accrue so the plate keeps showing "claimed NETA ×2" etc. across the game
 * instead of collapsing to "— no claim —" on the human's turn.
 */
internal data class OpponentClaimSummary(
    /** The most-recent role this seat claimed (their standing claim), or null if never. */
    val standingRole: Role?,
    /** Short trail label for the plate, e.g. "claimed NETA ×2" / "last: BABU", or null. */
    val trail: String?,
    /** True if any of this seat's role-claims was caught bluffing (revealed without the role). */
    val caught: Boolean,
    /** True if any of this seat's role-claims was proven on a challenge (revealed with the role). */
    val proven: Boolean,
)

/**
 * Scan the public event log for [opp]'s role-claims and summarise them. Pure / deterministic.
 *
 * - `ActionDeclared.claimedRole` is the source of role-claims (Tax→NETA, Vasooli→BABU, …).
 * - `ChallengeRevealed{player=opp, hadRole}` tells us whether a challenged claim held up.
 *   hadRole=false ⇒ a bluff was caught; hadRole=true ⇒ the claim was proven.
 */
internal fun deriveClaimSummary(
    opp: OpponentView,
    events: List<GameEvent>,
): OpponentClaimSummary {
    val myClaims: List<Role> =
        events
            .filterIsInstance<GameEvent.ActionDeclared>()
            .filter { it.actor == opp.id }
            .mapNotNull { it.claimedRole }

    val standingRole = myClaims.lastOrNull()

    val reveals =
        events
            .filterIsInstance<GameEvent.ChallengeRevealed>()
            .filter { it.player == opp.id }
    val caught = reveals.any { !it.hadRole }
    val proven = reveals.any { it.hadRole }

    val trail: String? =
        when {
            standingRole == null -> null
            else -> {
                // Count how many times the standing role was claimed (×N if repeated).
                val n = myClaims.count { it == standingRole }
                val roleName = roleLabel(standingRole)
                if (n >= 2) "claimed $roleName ×$n" else "claimed $roleName"
            }
        }
    return OpponentClaimSummary(standingRole, trail, caught, proven)
}

/**
 * Suspicion / bluff-odds read on [opp]'s STANDING claim, from public state only. Returns
 * (pips, label) for the plate's compact chip, or null when there is no standing claim.
 * Reuses [BluffOdds.estimate] — the same pure estimator the reaction HintRail uses.
 */
internal fun deriveSuspicion(
    opp: OpponentView,
    standingRole: Role?,
    view: PlayerView,
    /**
     * This seat's INFERRED bluffRate (0..1) from the decision-coach's public-info belief, or null
     * if no read yet. When present it nudges the deck-math pips: a known serial-bluffer's claim
     * reads hotter, a straight-shooter's reads cooler — so the suspicion chip blends deck odds with
     * the opponent's observed STYLE rather than the cards alone.
     */
    bluffRate: Double? = null,
): Pair<Int, String>? {
    if (standingRole == null || opp.eliminated) return null
    val cfg = view.config
    val allFaceUp = view.players.flatMap { it.faceUpRoles } + view.myFaceUp
    val eliminatedForRole = allFaceUp.count { it == standingRole }
    val myHandHasRole = view.myInfluence.count { it == standingRole }
    val conf =
        BluffOdds.estimate(
            claimedRole = standingRole,
            copiesPerRole = cfg.copiesPerRole,
            deckSize = cfg.deckSize,
            eliminatedRolesForClaimedRole = eliminatedForRole,
            myHandContainsClaimedRole = myHandHasRole,
            opponentFaceDownCount = opp.faceDownCount,
            totalVisibleCards = allFaceUp.size,
        )
    if (bluffRate == null) return conf.pips to conf.label
    // Style nudge: bluffRate above the 0.20 baseline pushes pips up, below it pulls down. Capped at
    // ±1 pip so the deck math still leads and the read stays legible.
    val nudge =
        when {
            bluffRate >= 0.55 -> 1
            bluffRate <= 0.12 -> -1
            else -> 0
        }
    val pips = (conf.pips + nudge).coerceIn(1, 5)
    // Re-label off the blended pips, and flag when STYLE (not cards) is driving the read.
    val baseLabel =
        when (pips) {
            1 -> "likely honest"
            2 -> "probably real"
            3 -> "coin-flip"
            4 -> "probably bluffing"
            else -> "long shot"
        }
    val label =
        if (nudge > 0) {
            "$baseLabel · shady"
        } else if (nudge < 0) {
            "$baseLabel · steady"
        } else {
            baseLabel
        }
    return pips to label
}

/**
 * Bluff-odds read for the HUMAN's own [action] claim — i.e. "if I declare this and get
 * challenged, how exposed am I?" Returns null for unclaimed (safe) actions. Reuses the
 * same pure [BluffOdds.estimate] estimator the reaction HintRail and plates use, but from
 * the actor's own seat: it folds in whether my hand actually holds the claimed role.
 */
fun riskBluffConf(
    action: Action,
    state: GameUiState,
): BluffOdds.Confidence? {
    val role = Rules.claimedRole(action) ?: return null
    val cfg = state.view.config
    val allFaceUp = state.view.players.flatMap { it.faceUpRoles } + state.view.myFaceUp
    val eliminatedForRole = allFaceUp.count { it == role }
    val myHandHasRole = state.view.myInfluence.count { it == role }
    return BluffOdds.estimate(
        claimedRole = role,
        copiesPerRole = cfg.copiesPerRole,
        deckSize = cfg.deckSize,
        eliminatedRolesForClaimedRole = eliminatedForRole,
        myHandContainsClaimedRole = myHandHasRole,
        opponentFaceDownCount = 1,
        totalVisibleCards = allFaceUp.size,
    )
}

// ─────────────────────────── Decision-coach lookup ───────────────────────────

/** The [MoveAdvice] for [intent] from the coach, or null if none / advice not yet computed. */
internal fun adviceFor(
    state: GameUiState,
    intent: Intent,
): com.kursi.ai.advisor.MoveAdvice? = state.advice.firstOrNull { it.intent == intent }

/**
 * Coach advice for an ACTION chip. Non-target actions (Income/ForeignAid/Tax/Exchange) match
 * exactly. Target actions (Coup/Assassinate/Steal) have a concrete target inside each advice
 * entry but the chip hasn't picked one yet, so we match by action TYPE and surface the
 * recommended-or-best entry of that type — the truthful/bluff verdict is target-independent
 * (it's about the claimed role) and the odds read the same across targets.
 */
internal fun adviceForActionChip(
    state: GameUiState,
    action: Action,
): com.kursi.ai.advisor.MoveAdvice? {
    val declares = state.advice.filter { it.intent is Intent.DeclareAction }

    fun typeMatches(
        a: Action,
        b: Action,
    ): Boolean =
        when {
            a is Action.Coup && b is Action.Coup -> true
            a is Action.Assassinate && b is Action.Assassinate -> true
            a is Action.Steal && b is Action.Steal -> true
            else -> a == b
        }
    val matches = declares.filter { typeMatches((it.intent as Intent.DeclareAction).action, action) }
    return matches.firstOrNull { it.recommended } ?: matches.maxByOrNull { it.winProb }
}

/** Build the long-press DECISION-COACH chit from a [MoveAdvice], with an optional belief read. */
internal fun coachChitOf(
    advice: com.kursi.ai.advisor.MoveAdvice,
    beliefLine: String? = null,
): ChitContent.Coach =
    ChitContent.Coach(
        moveLabel = advice.label,
        truthful = advice.truthful,
        bluff = advice.bluff,
        successOdds = advice.successOdds,
        winProb = advice.winProb,
        recommended = advice.recommended,
        rationale = advice.rationale,
        beliefLine = beliefLine,
    )

/**
 * The belief-grounded "is this claim a bluff?" line the coach leads with when the human is deciding
 * whether to CHALLENGE — built purely from public card-accounting (copies of [claimedRole] minus
 * those eliminated face-up and those in the human's own hand). When every copy is accounted for, the
 * claim is provably a bluff; otherwise it reports how many remain unseen. PUBLIC-info only.
 */
internal fun challengeBeliefLine(
    state: GameUiState,
    claimedRole: Role,
    claimLabel: String,
): String {
    val cfg = state.view.config
    val allFaceUp = state.view.players.flatMap { it.faceUpRoles } + state.view.myFaceUp
    val eliminated = allFaceUp.count { it == claimedRole }
    val mine = state.view.myInfluence.count { it == claimedRole }
    val copies = cfg.copiesPerRole
    val accountedFor = eliminated + mine
    val unseen = (copies - accountedFor).coerceAtLeast(0)
    val roleName = roleLabel(claimedRole)
    return when {
        unseen == 0 ->
            "All $copies $roleName are accounted for — this $claimLabel is a bluff; challenge is favourable."
        unseen == 1 && accountedFor > 0 ->
            "Only 1 $roleName left unseen ($accountedFor already visible) — this $claimLabel is shaky; a challenge has real odds."
        else ->
            "$unseen of $copies $roleName still unseen — they could well hold $roleName; challenge is a gamble."
    }
}

internal fun targetVerb(action: Action): String =
    when (action) {
        is Action.Coup -> "Khela"
        is Action.Assassinate -> "assassinate"
        is Action.Steal -> "steal from"
        is Action.Investigate -> "investigate"
        else -> "target"
    }

internal fun confirmSummary(
    action: Action,
    target: PlayerId?,
    targetName: String? = null,
): String {
    val targetStr = target?.let { " ${targetName ?: "Seat ${it.raw}"}" } ?: ""
    return when (action) {
        is Action.Coup -> "Khela$targetStr (pay 7)"
        is Action.Assassinate -> "Supari$targetStr (pay 3, claims BHAI)"
        is Action.Steal -> "Vasooli from$targetStr (claims BABU)"
        is Action.Investigate -> "Jaanch on$targetStr (claims PATRAKAAR)"
        Action.Tax -> "Ghotala +3 (claims NETA)"
        Action.ForeignAid -> "FDI +2"
        Action.Income -> "Dehaadi +1"
        Action.Exchange -> "Setting (claims JUGAADU)"
        Action.BailPe -> "Bail Pe Bahar (restore influence)"
        Action.Sabotage -> "Bali Khel (sacrifice influence for coins)"
        is Action.Hawala -> "Hawala (gift coins to$targetStr)"
        Action.Emergency -> "ADHYADESH — mass-Coup all opponents"
    }
}
