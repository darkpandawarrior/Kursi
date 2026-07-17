package com.kursi.feature.game.overlays

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.designsystem.*
import com.kursi.engine.*
import com.kursi.feature.game.*

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
