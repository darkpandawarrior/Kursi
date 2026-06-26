package com.kursi.core.network

import com.kursi.engine.CardId
import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.LossReason
import com.kursi.engine.OpponentView
import com.kursi.engine.OwnCard
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.engine.ReactionStep
import com.kursi.engine.Role
import com.kursi.engine.baseRoles
import com.kursi.protocol.wire.WireGameConfig
import com.kursi.protocol.wire.WireGameEvent
import com.kursi.protocol.wire.WireLossReason
import com.kursi.protocol.wire.WireOpponentView
import com.kursi.protocol.wire.WireOwnCard
import com.kursi.protocol.wire.WirePhaseView
import com.kursi.protocol.wire.WirePlayerView
import com.kursi.protocol.wire.WireReactionStep
import com.kursi.protocol.wire.WireRole
import com.kursi.protocol.wire.toEngine

/**
 * CLIENT-SIDE reconstruction of engine *view* types from their wire mirrors.
 *
 * WHY THIS EXISTS — and why it is NOT in :shared-protocol:
 * [com.kursi.protocol.wire.Mappers] deliberately ships NO `WirePlayerView.toEngine()`: a client must
 * never be able to rebuild a [com.kursi.engine.GameState] (the secrecy boundary, INV-2). That contract
 * is about full game STATE. The *redacted* [com.kursi.engine.PlayerView], by contrast, is exactly what
 * the offline UI already renders, and it carries ONLY what this seat is entitled to see. Reconstructing
 * that view (and nothing more) lets the existing `GameScreen` render an online match unchanged.
 *
 * This reconstruction lives in :core:network (the client layer), not :shared-protocol, so the protocol's
 * "no client-side state reconstruction" guarantee is untouched: the wire types still cannot produce a
 * `GameState`. We only re-hydrate the per-viewer projection the server already chose to send.
 *
 * LOSSY-BY-DESIGN FIELD: [WirePhaseView.InvestigatePeek] intentionally drops the examiner's privately
 * peeked card (`examinedCard`) — the wire never carries it. We rebuild [PhaseView.InvestigatePeek] with
 * `examinedCard = null`. The examiner's own client knows the peeked card out-of-band (it examined the
 * target's face-down card locally), exactly as the wire doc describes; the rebuilt engine view loses no
 * information the wire ever carried.
 */

fun WireRole.toEngineRole(): Role = this.toEngine()

fun WireLossReason.toEngineReason(): LossReason = when (this) {
    WireLossReason.LOST_CHALLENGE -> LossReason.LOST_CHALLENGE
    WireLossReason.LOST_BLOCK_CHALLENGE -> LossReason.LOST_BLOCK_CHALLENGE
    WireLossReason.ASSASSINATED -> LossReason.ASSASSINATED
    WireLossReason.COUPED -> LossReason.COUPED
    WireLossReason.SABOTAGED -> LossReason.SABOTAGED
    WireLossReason.EMERGENCY_COUPED -> LossReason.EMERGENCY_COUPED
}

fun WireReactionStep.toEngineStep(): ReactionStep = when (this) {
    WireReactionStep.CHALLENGE_ACTION -> ReactionStep.CHALLENGE_ACTION
    WireReactionStep.BLOCK -> ReactionStep.BLOCK
    WireReactionStep.CHALLENGE_BLOCK -> ReactionStep.CHALLENGE_BLOCK
}

fun WireOwnCard.toEngine(): OwnCard = OwnCard(id = CardId(id), role = role.toEngine(), faceUp = faceUp)

fun WireOpponentView.toEngine(): OpponentView = OpponentView(
    id = PlayerId(id),
    seatIndex = seatIndex,
    coins = coins,
    faceUpRoles = faceUpRoles.map { it.toEngine() },
    faceDownCount = faceDownCount,
    eliminated = eliminated,
)

/**
 * Reconstructs a [GameConfig] from its wire subset.
 *
 * The wire [WireGameConfig] carries [WireGameConfig.roleCount] but NOT the concrete `activeRoles` list
 * (clients never need the identities — only the count gates UI like the Jaanch/PATRAKAAR availability).
 * We re-derive `activeRoles` from the count using the engine's own ladder: 5 → [baseRoles], 6 → baseRoles
 * + [Role.PATRAKAAR]. The remaining numeric fields map 1:1.
 *
 * NOTE: [GameConfig]'s `init` block validates the deck-buffer floor against [seatCount]/[copiesPerRole].
 * The server only ever serializes a config that ALREADY passed that validation engine-side, so the
 * reconstructed config satisfies the same `require`s by construction — we are mirroring a value the
 * server already proved valid, not synthesizing an arbitrary one.
 */
fun WireGameConfig.toEngine(): GameConfig {
    val roles: List<Role> = if (roleCount >= 6) baseRoles + Role.PATRAKAAR else baseRoles
    return GameConfig(
        seatCount = seatCount,
        copiesPerRole = copiesPerRole,
        activeRoles = roles,
        influencePerPlayer = influencePerPlayer,
        startingCoins = startingCoins,
        coupCost = coupCost,
        assassinateCost = assassinateCost,
        taxAmount = taxAmount,
        foreignAidAmount = foreignAidAmount,
        stealAmount = stealAmount,
        incomeAmount = incomeAmount,
        exchangeDrawCount = exchangeDrawCount,
        forcedCoupThreshold = forcedCoupThreshold,
        coinSupply = coinSupply,
    )
}

fun WirePhaseView.toEngine(): PhaseView = when (this) {
    is WirePhaseView.Turn -> PhaseView.Turn(PlayerId(actor))
    is WirePhaseView.Reactions -> PhaseView.Reactions(
        actor = PlayerId(actor),
        action = action.toEngine(),
        claimedRole = claimedRole?.toEngine(),
        step = step.toEngineStep(),
        toRespond = toRespond?.let { PlayerId(it) },
        blocker = blocker?.let { PlayerId(it) },
        blockRole = blockRole?.toEngine(),
    )
    is WirePhaseView.InfluenceLoss -> PhaseView.InfluenceLoss(PlayerId(loser), reason.toEngineReason())
    is WirePhaseView.Exchange -> PhaseView.Exchange(
        actor = PlayerId(actor),
        // `drawn` is non-empty ONLY in the acting viewer's projection (server redaction); mapping it
        // straight through preserves that — every other seat receives an empty list and stays blind.
        drawn = drawn.map { it.toEngine() },
    )
    // SECRECY-PRESERVING: the wire never carried `examinedCard`, so we cannot (and must not) invent it.
    // null mirrors a non-examiner's view exactly; the examiner's client knows the peek out-of-band.
    is WirePhaseView.InvestigatePeek -> PhaseView.InvestigatePeek(
        examiner = PlayerId(examiner),
        target = PlayerId(target),
        examinedCard = null,
    )
    is WirePhaseView.Over -> PhaseView.Over(PlayerId(winner))
}

/**
 * Re-hydrates the engine [PlayerView] the offline UI renders from the redacted [WirePlayerView] the
 * server sent THIS seat. Carries exactly the entitled facts: this seat's own cards/hand, every
 * opponent's public face, the phase, and the table config. No opponent hidden role is reconstructable —
 * the wire never carried one, so neither does this.
 */
fun WirePlayerView.toEngineView(): PlayerView = PlayerView(
    viewer = PlayerId(viewer),
    config = config.toEngine(),
    treasury = treasury,
    deckCount = deckCount,
    turnNumber = turnNumber,
    myCoins = myCoins,
    myInfluence = myInfluence.map { it.toEngine() },
    myFaceUp = myFaceUp.map { it.toEngine() },
    myCards = myCards.map { it.toEngine() },
    players = players.map { it.toEngine() },
    phase = phase.toEngine(),
)

/**
 * Re-hydrates an engine [GameEvent] from its already-per-viewer-redacted wire form.
 *
 * The server projected each event for this recipient via [com.kursi.protocol.wire.toWireFor] BEFORE it
 * reached us, nulling any secret CardId this seat isn't entitled to (the `drawn`/`kept` fields). Those
 * arrive null here for non-owners; we surface them as a sentinel [HIDDEN_CARD] CardId so the engine
 * event type (whose fields are non-null) can be rebuilt for the history panel. The offline UI's history
 * renderer keys off the PUBLIC fields (who/what/role) — it never inspects these secret CardIds for a
 * non-owner — so the sentinel is display-inert and never leaks a real identity.
 */
fun WireGameEvent.toEngineEvent(): GameEvent = when (this) {
    is WireGameEvent.ActionDeclared ->
        GameEvent.ActionDeclared(PlayerId(actor), action.toEngine(), claimedRole?.toEngine())
    is WireGameEvent.Challenged ->
        GameEvent.Challenged(PlayerId(challenger), PlayerId(target), claimedRole.toEngine())
    is WireGameEvent.ChallengeRevealed ->
        GameEvent.ChallengeRevealed(PlayerId(player), CardId(card), role.toEngine(), hadRole)
    is WireGameEvent.CardReplaced ->
        GameEvent.CardReplaced(PlayerId(player), CardId(returned), CardId(drawn ?: HIDDEN_CARD))
    is WireGameEvent.Blocked ->
        GameEvent.Blocked(PlayerId(blocker), role.toEngine(), action.toEngine())
    is WireGameEvent.ActionResolved -> GameEvent.ActionResolved(PlayerId(actor), action.toEngine())
    is WireGameEvent.ActionNegated -> GameEvent.ActionNegated(PlayerId(actor), action.toEngine())
    is WireGameEvent.CoinsChanged -> GameEvent.CoinsChanged(PlayerId(player), delta)
    is WireGameEvent.CoinsTransferred -> GameEvent.CoinsTransferred(PlayerId(from), PlayerId(to), amount)
    is WireGameEvent.InfluenceLost ->
        GameEvent.InfluenceLost(PlayerId(player), CardId(card), role.toEngine(), reason.toEngineReason())
    is WireGameEvent.PlayerEliminated -> GameEvent.PlayerEliminated(PlayerId(player))
    is WireGameEvent.Exchanged ->
        GameEvent.Exchanged(
            actor = PlayerId(actor),
            kept = (kept ?: emptyList()).map { CardId(it) },
            returned = returned.map { CardId(it) },
        )
    is WireGameEvent.Investigated -> GameEvent.Investigated(PlayerId(examiner), PlayerId(target))
    is WireGameEvent.InvestigateRedraw -> GameEvent.InvestigateRedraw(PlayerId(target))
    is WireGameEvent.TurnAdvanced -> GameEvent.TurnAdvanced(toSeat, turnNumber)
    is WireGameEvent.GameEnded -> GameEvent.GameEnded(PlayerId(winner))
    is WireGameEvent.InfluenceRestored -> GameEvent.InfluenceRestored(PlayerId(player), CardId(card), role.toEngine())
    is WireGameEvent.CoinsGifted -> GameEvent.CoinsGifted(PlayerId(from), PlayerId(to), amount)
    is WireGameEvent.EmergencyDeclared -> GameEvent.EmergencyDeclared(PlayerId(actor))
    is WireGameEvent.KhazanaWon -> GameEvent.KhazanaWon(PlayerId(winner), lifetimeCoins)
    is WireGameEvent.DarjaReached -> GameEvent.DarjaReached(PlayerId(player), level, lifetimeCoins)
}

/**
 * Sentinel [CardId] raw used when a secret CardId was redacted out for this seat (server sent null).
 * Negative so it can never collide with a real, server-assigned CardId (which are >= 0). Display-inert:
 * the offline history renderer never reads a non-owner's secret CardId.
 */
const val HIDDEN_CARD: Int = -1
