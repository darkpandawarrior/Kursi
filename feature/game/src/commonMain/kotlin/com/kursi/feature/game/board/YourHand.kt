package com.kursi.feature.game.board

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.kursi.designsystem.*
import com.kursi.designsystem.moment.reportAnchor
import com.kursi.engine.*
import com.kursi.feature.game.*

// ─────────────────────────── Your Hand Panel ───────────────────────────

@Composable
internal fun YourHandPanel(
    state: GameUiState,
    gamePhase: GamePhase,
    humanSeat: PlayerId,
    onAction: (GameAction) -> Unit,
    onShowChit: (ChitContent, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardTilt")
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "tiltAngle",
    )

    val lossIntents =
        if (gamePhase is GamePhase.LoseInfluence) {
            state.legalIntents.filterIsInstance<Intent.ChooseInfluenceToLose>()
        } else {
            emptyList()
        }

    val claimedRole: Role? =
        when (gamePhase) {
            is GamePhase.PickTarget -> Rules.claimedRole(gamePhase.action)
            is GamePhase.Confirm -> Rules.claimedRole(gamePhase.action)
            else -> null
        }

    val myTurn =
        gamePhase is GamePhase.PickAction ||
            gamePhase is GamePhase.PickTarget ||
            gamePhase is GamePhase.Confirm
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // M4 §1: the hand is seat 0's anchor + the off-table entry origin for moments.
                .reportAnchor(
                    com.kursi.designsystem.moment.AnchorKey
                        .Seat(humanSeat.raw),
                ).reportAnchor(com.kursi.designsystem.moment.AnchorKey.Hand)
                .decoPanel(lifted = myTurn),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(
                    text = "Your hand",
                    style = KursiType.caption,
                    color = KursiNeutrals.TextMuted,
                )
                CoinPill(count = state.view.myCoins)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Alive cards — give each card room to breathe at Large size.
                // Long-press = inspect (Identity chit). During LoseInfluence, tap = reveal.
                // DEPTH3D: the hand reads as a held fan — cards splay symmetrically with a
                // slight rotationZ + a perspective rotationY tilt, and the RELEVANT card
                // (the role this turn's claim needs, or a loss-choice) straightens upright
                // and lifts toward the viewer so it pops out of the spread.
                val handCount = state.view.myInfluence.size
                state.view.myInfluence.forEachIndexed { idx, role ->
                    val lifted = claimedRole == role
                    var cardBounds by remember(role, idx) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                    val isLossChoice = gamePhase is GamePhase.LoseInfluence && idx < lossIntents.size

                    // Fan geometry: spread cards around the row centre. A 2-card hand fans
                    // ±5°, wider hands tighten per-card so the overall spread stays tasteful.
                    val centerIdx = (handCount - 1) / 2f
                    val rel = idx - centerIdx
                    val fanZ = if (handCount > 1) rel * 6f else 0f
                    // The relevant card un-fans, rises and tilts to face the player.
                    val relevant = lifted || isLossChoice
                    val fanTarget by animateFloatAsState(
                        targetValue = if (relevant) 0f else fanZ,
                        animationSpec = tween(260),
                        label = "handFanZ",
                    )
                    val liftTarget by animateFloatAsState(
                        targetValue = if (relevant) -10f else 0f,
                        animationSpec = tween(260),
                        label = "handFanLift",
                    )

                    val baseGraphics =
                        Modifier
                            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
                            .graphicsLayer {
                                cameraDistance = 12f * density
                                // Living breathing perspective tilt + the static fan rotation.
                                rotationY = tiltAngle * 0.5f
                                rotationZ = fanTarget
                                translationY = liftTarget * density
                            }
                    val cardModifier =
                        if (isLossChoice) {
                            baseGraphics
                                .clip(
                                    androidx.compose.foundation.shape
                                        .RoundedCornerShape(KursiRadii.sm),
                                ).border(
                                    width = 2.dp,
                                    color = KursiSemantics.Danger,
                                    shape =
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(KursiRadii.sm),
                                ).loseInfluenceCardSemantics(role)
                        } else {
                            baseGraphics.handCardSemantics(role = role, faceUp = false)
                        }

                    val lossIntent = if (isLossChoice) lossIntents[idx] else null
                    RoleCard(
                        role = role,
                        size = CardSize.Large,
                        faceUp = true,
                        lost = false,
                        lifted = lifted,
                        modifier = cardModifier,
                        onClick =
                            if (lossIntent != null) {
                                { onAction(GameAction.Submit(Intent.ChooseInfluenceToLose(humanSeat, lossIntent.card))) }
                            } else {
                                null
                            },
                        onLongClick = { onShowChit(ChitContent.Identity(role), cardBounds) },
                    )
                }

                // Lost/revealed cards
                state.view.myFaceUp.forEach { role ->
                    RoleCard(
                        role = role,
                        size = CardSize.Large,
                        faceUp = true,
                        lost = true,
                    )
                }
            }
        }
    }
}
