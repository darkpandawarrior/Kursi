package com.kursi.feature.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.kursi.feature.game.overlays.*
import com.kursi.feature.game.status.*

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
