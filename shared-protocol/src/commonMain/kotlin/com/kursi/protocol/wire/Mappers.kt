package com.kursi.protocol.wire

import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.GameConfig
import com.kursi.engine.GameEvent
import com.kursi.engine.Intent
import com.kursi.engine.LossReason
import com.kursi.engine.OpponentView
import com.kursi.engine.OwnCard
import com.kursi.engine.PhaseView
import com.kursi.engine.PlayerId
import com.kursi.engine.PlayerView
import com.kursi.engine.ReactionStep
import com.kursi.engine.Role

// ─────────────────────────── Role ───────────────────────────

fun Role.toWire(): WireRole = when (this) {
    Role.NETA -> WireRole.NETA
    Role.BHAI -> WireRole.BHAI
    Role.BABU -> WireRole.BABU
    Role.JUGAADU -> WireRole.JUGAADU
    Role.VAKIL -> WireRole.VAKIL
    Role.PATRAKAAR -> WireRole.PATRAKAAR
}

fun WireRole.toEngine(): Role = when (this) {
    WireRole.NETA -> Role.NETA
    WireRole.BHAI -> Role.BHAI
    WireRole.BABU -> Role.BABU
    WireRole.JUGAADU -> Role.JUGAADU
    WireRole.VAKIL -> Role.VAKIL
    WireRole.PATRAKAAR -> Role.PATRAKAAR
}

// ─────────────────────────── Action ───────────────────────────

fun Action.toWire(): WireAction = when (this) {
    Action.Income -> WireAction.Income
    Action.ForeignAid -> WireAction.ForeignAid
    is Action.Coup -> WireAction.Coup(target.raw)
    Action.Tax -> WireAction.Tax
    is Action.Assassinate -> WireAction.Assassinate(target.raw)
    is Action.Steal -> WireAction.Steal(target.raw)
    Action.Exchange -> WireAction.Exchange
    is Action.Investigate -> WireAction.Investigate(target.raw)
}

fun WireAction.toEngine(): Action = when (this) {
    WireAction.Income -> Action.Income
    WireAction.ForeignAid -> Action.ForeignAid
    is WireAction.Coup -> Action.Coup(PlayerId(target))
    WireAction.Tax -> Action.Tax
    is WireAction.Assassinate -> Action.Assassinate(PlayerId(target))
    is WireAction.Steal -> Action.Steal(PlayerId(target))
    WireAction.Exchange -> Action.Exchange
    is WireAction.Investigate -> Action.Investigate(PlayerId(target))
}

// ─────────────────────────── ReactionStep ───────────────────────────

fun ReactionStep.toWire(): WireReactionStep = when (this) {
    ReactionStep.CHALLENGE_ACTION -> WireReactionStep.CHALLENGE_ACTION
    ReactionStep.BLOCK -> WireReactionStep.BLOCK
    ReactionStep.CHALLENGE_BLOCK -> WireReactionStep.CHALLENGE_BLOCK
}

fun WireReactionStep.toEngine(): ReactionStep = when (this) {
    WireReactionStep.CHALLENGE_ACTION -> ReactionStep.CHALLENGE_ACTION
    WireReactionStep.BLOCK -> ReactionStep.BLOCK
    WireReactionStep.CHALLENGE_BLOCK -> ReactionStep.CHALLENGE_BLOCK
}

// ─────────────────────────── LossReason ───────────────────────────

fun LossReason.toWire(): WireLossReason = when (this) {
    LossReason.LOST_CHALLENGE -> WireLossReason.LOST_CHALLENGE
    LossReason.LOST_BLOCK_CHALLENGE -> WireLossReason.LOST_BLOCK_CHALLENGE
    LossReason.ASSASSINATED -> WireLossReason.ASSASSINATED
    LossReason.COUPED -> WireLossReason.COUPED
}

fun WireLossReason.toEngine(): LossReason = when (this) {
    WireLossReason.LOST_CHALLENGE -> LossReason.LOST_CHALLENGE
    WireLossReason.LOST_BLOCK_CHALLENGE -> LossReason.LOST_BLOCK_CHALLENGE
    WireLossReason.ASSASSINATED -> LossReason.ASSASSINATED
    WireLossReason.COUPED -> LossReason.COUPED
}

// ─────────────────────────── OwnCard ───────────────────────────

fun OwnCard.toWire(): WireOwnCard = WireOwnCard(id = id.raw, role = role.toWire(), faceUp = faceUp)

// ─────────────────────────── PhaseView ───────────────────────────

fun PhaseView.toWire(): WirePhaseView = when (this) {
    is PhaseView.Turn -> WirePhaseView.Turn(actor.raw)
    is PhaseView.Reactions -> WirePhaseView.Reactions(
        actor = actor.raw,
        action = action.toWire(),
        claimedRole = claimedRole?.toWire(),
        step = step.toWire(),
        toRespond = toRespond?.raw,
        blocker = blocker?.raw,
        blockRole = blockRole?.toWire(),
    )
    is PhaseView.InfluenceLoss -> WirePhaseView.InfluenceLoss(loser.raw, reason.toWire())
    // `drawn` is already gated by redact(): it is non-empty ONLY in the acting viewer's projection and
    // empty for every other seat, so mapping it straight through preserves the secrecy boundary.
    is PhaseView.Exchange -> WirePhaseView.Exchange(actor.raw, drawn.map { it.toWire() })
    // SECRECY: drop `examinedCard` (the examiner's privately-peeked PeekedCard). redact() already
    // gates it to the examiner alone; the wire mirror carries only the public examiner/target seats,
    // mirroring how Exchange omits the actor's privately-drawn cards. The peeked role is NEVER serialized.
    is PhaseView.InvestigatePeek -> WirePhaseView.InvestigatePeek(examiner.raw, target.raw)
    is PhaseView.Over -> WirePhaseView.Over(winner.raw)
}

// ─────────────────────────── OpponentView ───────────────────────────

fun OpponentView.toWire(): WireOpponentView = WireOpponentView(
    id = id.raw,
    seatIndex = seatIndex,
    coins = coins,
    faceUpRoles = faceUpRoles.map { it.toWire() },
    faceDownCount = faceDownCount,
    eliminated = eliminated,
)

// ─────────────────────────── GameConfig ───────────────────────────

fun GameConfig.toWire(): WireGameConfig = WireGameConfig(
    seatCount = seatCount,
    copiesPerRole = copiesPerRole,
    roleCount = roleCount,
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

// ─────────────────────────── PlayerView ───────────────────────────

/**
 * Converts a server-side redacted [PlayerView] to its wire form [WirePlayerView].
 *
 * This is the **only** conversion direction for [PlayerView] — the server calls `redact()` then
 * `.toWire()` before serializing. Clients never send a [WirePlayerView]; they only receive it.
 * There is deliberately no `WirePlayerView.toEngine()` function — clients cannot reconstruct
 * a [PlayerView] from the wire (and certainly not a [com.kursi.engine.GameState]).
 */
fun PlayerView.toWire(): WirePlayerView = WirePlayerView(
    viewer = viewer.raw,
    config = config.toWire(),
    treasury = treasury,
    deckCount = deckCount,
    turnNumber = turnNumber,
    myCoins = myCoins,
    myInfluence = myInfluence.map { it.toWire() },
    myFaceUp = myFaceUp.map { it.toWire() },
    // myCards is, by redact()'s contract, ONLY the viewer's own cards — safe to serialize wholesale.
    myCards = myCards.map { it.toWire() },
    players = players.map { it.toWire() },
    phase = phase.toWire(),
)

// ─────────────────────────── Intent ───────────────────────────

/**
 * Converts the engine [Intent] to its wire form.
 * Servers use this when echoing an intent back to clients (e.g. in a replay log).
 */
fun Intent.toWire(): WireIntent = when (this) {
    is Intent.DeclareAction -> WireIntent.DeclareAction(actor.raw, action.toWire())
    is Intent.Challenge -> WireIntent.Challenge(actor.raw)
    is Intent.Block -> WireIntent.Block(actor.raw, role.toWire())
    is Intent.Pass -> WireIntent.Pass(actor.raw)
    is Intent.ChooseInfluenceToLose -> WireIntent.ChooseInfluenceToLose(actor.raw, card.raw)
    is Intent.ChooseExchange -> WireIntent.ChooseExchange(actor.raw, keep.map { it.raw })
    is Intent.ResolveInvestigate -> WireIntent.ResolveInvestigate(actor.raw, forceRedraw)
}

/**
 * Converts a [WireIntent] received from a client into the engine [Intent].
 * The server calls this before passing the intent to [com.kursi.engine.applyIntent].
 */
fun WireIntent.toEngine(): Intent = when (this) {
    is WireIntent.DeclareAction -> Intent.DeclareAction(PlayerId(actor), action.toEngine())
    is WireIntent.Challenge -> Intent.Challenge(PlayerId(actor))
    is WireIntent.Block -> Intent.Block(PlayerId(actor), role.toEngine())
    is WireIntent.Pass -> Intent.Pass(PlayerId(actor))
    is WireIntent.ChooseInfluenceToLose -> Intent.ChooseInfluenceToLose(PlayerId(actor), CardId(card))
    is WireIntent.ChooseExchange -> Intent.ChooseExchange(PlayerId(actor), keep.map { CardId(it) })
    is WireIntent.ResolveInvestigate -> Intent.ResolveInvestigate(PlayerId(actor), forceRedraw)
}

// ─────────────────────────── GameEvent (per-viewer redaction) ───────────────────────────

/**
 * Projects an engine [GameEvent] to its wire form FOR A SPECIFIC [viewer] (the recipient seat).
 *
 * This is the secrecy boundary for the event stream — the analogue of `redact()` for [PlayerView].
 * Most events are public and map straight through. The two events that embed a secret face-down
 * [CardId] — [GameEvent.CardReplaced.drawn] and [GameEvent.Exchanged.kept] — have those CardIds
 * NULLED unless [viewer] is the card's owner. This makes it structurally impossible to leak a
 * still-secret card identity to another seat while preserving the public shape for everyone.
 *
 * The server MUST call this per connected seat (never broadcast a single shared list of events).
 */
fun GameEvent.toWireFor(viewer: PlayerId): WireGameEvent = when (this) {
    is GameEvent.ActionDeclared ->
        WireGameEvent.ActionDeclared(actor.raw, action.toWire(), claimedRole?.toWire())
    is GameEvent.Challenged ->
        WireGameEvent.Challenged(challenger.raw, target.raw, claimedRole.toWire())
    is GameEvent.ChallengeRevealed ->
        WireGameEvent.ChallengeRevealed(player.raw, card.raw, role.toWire(), hadRole)
    is GameEvent.CardReplaced ->
        // `drawn` is a fresh SECRET face-down card — reveal its CardId only to its owner.
        WireGameEvent.CardReplaced(player.raw, returned.raw, if (viewer == player) drawn.raw else null)
    is GameEvent.Blocked ->
        WireGameEvent.Blocked(blocker.raw, role.toWire(), action.toWire())
    is GameEvent.ActionResolved -> WireGameEvent.ActionResolved(actor.raw, action.toWire())
    is GameEvent.ActionNegated -> WireGameEvent.ActionNegated(actor.raw, action.toWire())
    is GameEvent.CoinsChanged -> WireGameEvent.CoinsChanged(player.raw, delta)
    is GameEvent.CoinsTransferred -> WireGameEvent.CoinsTransferred(from.raw, to.raw, amount)
    is GameEvent.InfluenceLost ->
        WireGameEvent.InfluenceLost(player.raw, card.raw, role.toWire(), reason.toWire())
    is GameEvent.PlayerEliminated -> WireGameEvent.PlayerEliminated(player.raw)
    is GameEvent.Exchanged ->
        // `kept` are the actor's new SECRET face-down cards — reveal their CardIds only to the actor.
        WireGameEvent.Exchanged(
            actor = actor.raw,
            kept = if (viewer == actor) kept.map { it.raw } else null,
            returned = returned.map { it.raw },
        )
    is GameEvent.Investigated -> WireGameEvent.Investigated(examiner.raw, target.raw)
    is GameEvent.InvestigateRedraw -> WireGameEvent.InvestigateRedraw(target.raw)
    is GameEvent.TurnAdvanced -> WireGameEvent.TurnAdvanced(toSeat, turnNumber)
    is GameEvent.GameEnded -> WireGameEvent.GameEnded(winner.raw)
}
