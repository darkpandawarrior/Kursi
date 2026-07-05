package com.kursi.protocol.wire

import kotlinx.serialization.Serializable

// ─────────────────────────── Primitive wrappers ───────────────────────────

/**
 * Wire representation of [com.kursi.engine.PlayerId].
 * Serialized as its Int seat index to keep frames compact and human-readable.
 */
typealias WirePlayerId = Int

/**
 * Wire representation of [com.kursi.engine.CardId].
 * Serialized as its Int raw value.
 */
typealias WireCardId = Int

// ─────────────────────────── Role ───────────────────────────

/**
 * Serializable mirror of [com.kursi.engine.Role].
 *
 * Kept as a separate wire enum so the engine's [com.kursi.engine.Role] stays free of
 * any serialization annotation or kotlinx-serialization dependency (engine purity — INV-1).
 * Ordinal positions are stable; adding a 6th role later is additive.
 */
@Serializable
enum class WireRole {
    NETA,
    BHAI,
    BABU,
    JUGAADU,
    VAKIL,
    PATRAKAAR,
}

// ─────────────────────────── Action ───────────────────────────

/**
 * Serializable mirror of sealed interface [com.kursi.engine.Action].
 *
 * Target players are carried as [WirePlayerId] (Int seat) rather than the engine's value class
 * so the serialization layer has no dependency on expect/actual [com.kursi.engine.PlayerId].
 *
 * The `type` discriminator is stable and versioned (see [H7] in 13_integration_notes.md).
 */
@Serializable
sealed interface WireAction {
    @Serializable
    data object Income : WireAction

    @Serializable
    data object ForeignAid : WireAction

    @Serializable
    data class Coup(
        val target: WirePlayerId,
    ) : WireAction

    @Serializable
    data object Tax : WireAction

    @Serializable
    data class Assassinate(
        val target: WirePlayerId,
    ) : WireAction

    @Serializable
    data class Steal(
        val target: WirePlayerId,
    ) : WireAction

    @Serializable
    data object Exchange : WireAction

    @Serializable
    data class Investigate(
        val target: WirePlayerId,
    ) : WireAction

    // ── Variant actions (all default=disabled; classic path never produces these) ──
    @Serializable data object BailPe : WireAction

    @Serializable data object Sabotage : WireAction

    @Serializable data class Hawala(
        val to: WirePlayerId,
    ) : WireAction

    @Serializable data object Emergency : WireAction
}

// ─────────────────────────── Intent ───────────────────────────

/**
 * Serializable mirror of sealed interface [com.kursi.engine.Intent].
 *
 * Clients send this to the server. The server validates it and calls [com.kursi.engine.applyIntent].
 * [actor] is the seat index of the player submitting the intent.
 */
@Serializable
sealed interface WireIntent {
    val actor: WirePlayerId

    @Serializable
    data class DeclareAction(
        override val actor: WirePlayerId,
        val action: WireAction,
    ) : WireIntent

    @Serializable
    data class Challenge(
        override val actor: WirePlayerId,
    ) : WireIntent

    @Serializable
    data class Block(
        override val actor: WirePlayerId,
        val role: WireRole,
    ) : WireIntent

    @Serializable
    data class Pass(
        override val actor: WirePlayerId,
    ) : WireIntent

    @Serializable
    data class ChooseInfluenceToLose(
        override val actor: WirePlayerId,
        val card: WireCardId,
    ) : WireIntent

    @Serializable
    data class ChooseExchange(
        override val actor: WirePlayerId,
        val keep: List<WireCardId>,
    ) : WireIntent

    @Serializable
    data class ResolveInvestigate(
        override val actor: WirePlayerId,
        val forceRedraw: Boolean,
    ) : WireIntent
}

// ─────────────────────────── ReactionStep ───────────────────────────

/** Serializable mirror of [com.kursi.engine.ReactionStep]. */
@Serializable
enum class WireReactionStep {
    CHALLENGE_ACTION,
    BLOCK,
    CHALLENGE_BLOCK,
}

// ─────────────────────────── LossReason ───────────────────────────

/** Serializable mirror of [com.kursi.engine.LossReason]. */
@Serializable
enum class WireLossReason {
    LOST_CHALLENGE,
    LOST_BLOCK_CHALLENGE,
    ASSASSINATED,
    COUPED,
    SABOTAGED,
    EMERGENCY_COUPED,
}

// ─────────────────────────── PhaseView ───────────────────────────

/**
 * Serializable mirror of sealed interface [com.kursi.engine.PhaseView].
 *
 * Action / claimedRole are nullable in [Reactions] to mirror the engine model exactly.
 * The `type` discriminator lets clients dispatch to the right UI panel without extra parsing.
 */
@Serializable
sealed interface WirePhaseView {
    @Serializable
    data class Turn(
        val actor: WirePlayerId,
    ) : WirePhaseView

    @Serializable
    data class Reactions(
        val actor: WirePlayerId,
        val action: WireAction,
        val claimedRole: WireRole?,
        val step: WireReactionStep,
        val toRespond: WirePlayerId?,
        val blocker: WirePlayerId?,
        val blockRole: WireRole?,
    ) : WirePhaseView

    @Serializable
    data class InfluenceLoss(
        val loser: WirePlayerId,
        val reason: WireLossReason,
    ) : WirePhaseView

    /**
     * Serializable mirror of [com.kursi.engine.PhaseView.Exchange].
     *
     * SECRECY-CRITICAL: [drawn] holds the cards the exchanging actor drew from the deck. The engine's
     * `redact()` populates `PhaseView.Exchange.drawn` ONLY for the acting viewer's own projection and
     * leaves it EMPTY for everyone else — this mirror preserves that exactly. The acting viewer needs
     * the drawn [WireCardId]s to build a [WireIntent.ChooseExchange] keep-set; no other seat ever
     * receives them, so the drawn roles never leak. (Previously this field was dropped entirely, which
     * left even the acting viewer unable to construct the keep-choice from the wire.)
     */
    @Serializable
    data class Exchange(
        val actor: WirePlayerId,
        val drawn: List<WireOwnCard> = emptyList(),
    ) : WirePhaseView

    /**
     * Serializable mirror of [com.kursi.engine.PhaseView.InvestigatePeek].
     *
     * SECRECY-CRITICAL: this mirror deliberately carries ONLY the public facts — WHO is examining
     * ([examiner]) and WHOSE card is under examination ([target]). It intentionally OMITS the engine's
     * `examinedCard` ([com.kursi.engine.PeekedCard], the privately-learned role/CardId), exactly as
     * [Exchange] omits the actor's privately-drawn cards. `redact()` already gates the examined card to
     * the examiner alone before serialization; dropping it from the wire mirror makes it structurally
     * impossible to ever leak the peeked role to another seat. The examiner's own client knows the peeked
     * card out-of-band (it is one of the target's face-down cards it just examined locally), so no
     * gameplay information is lost for the entitled viewer.
     */
    @Serializable
    data class InvestigatePeek(
        val examiner: WirePlayerId,
        val target: WirePlayerId,
    ) : WirePhaseView

    @Serializable
    data class Over(
        val winner: WirePlayerId,
    ) : WirePhaseView
}

// ─────────────────────────── OwnCard ───────────────────────────

/**
 * Serializable mirror of [com.kursi.engine.OwnCard] — one of the VIEWER'S OWN cards resolved to its
 * [role] and [faceUp] state, tagged with its [id].
 *
 * SECRECY-CRITICAL: like its engine counterpart, this type only EVER carries the viewer's own card
 * identities. `redact()` populates [com.kursi.engine.PlayerView.myCards] exclusively from the viewer's
 * own face-down + face-up cards, so a [WireOwnCard] is never produced from an opponent's hidden card.
 *
 * Why it must be on the wire: a client needs the [WireCardId]↔[role] mapping to construct the
 * [WireIntent.ChooseInfluenceToLose] (pick which own card to flip) and [WireIntent.ChooseExchange]
 * (pick which own/drawn cards to keep) intents — those intents address cards by [WireCardId], which
 * the client can only know for its OWN cards. Omitting this made those two intents un-constructable
 * by a real client (the round-trip was structurally lossy).
 */
@Serializable
data class WireOwnCard(
    val id: WireCardId,
    val role: WireRole,
    val faceUp: Boolean,
)

// ─────────────────────────── OpponentView ───────────────────────────

/**
 * Serializable mirror of [com.kursi.engine.OpponentView].
 * Carries only what every other player is allowed to see: public roles, coin count, face-down card count.
 */
@Serializable
data class WireOpponentView(
    val id: WirePlayerId,
    val seatIndex: Int,
    val coins: Int,
    /** Roles that are face-up (publicly revealed) for this opponent. */
    val faceUpRoles: List<WireRole>,
    /** Number of face-down (still-secret) influence cards remaining. */
    val faceDownCount: Int,
    val eliminated: Boolean,
)

// ─────────────────────────── GameConfig (wire subset) ───────────────────────────

/**
 * Wire-safe subset of [com.kursi.engine.GameConfig].
 *
 * Clients need this to validate local rules, display UI constraints (e.g. "you must Coup at ≥10"),
 * and render the coin-supply bar. Fields not needed by clients (e.g. deckSize derivation) are omitted.
 */
@Serializable
data class WireGameConfig(
    val seatCount: Int,
    val copiesPerRole: Int,
    val roleCount: Int,
    val influencePerPlayer: Int,
    val startingCoins: Int,
    val coupCost: Int,
    val assassinateCost: Int,
    val taxAmount: Int,
    val foreignAidAmount: Int,
    val stealAmount: Int,
    val incomeAmount: Int,
    val exchangeDrawCount: Int,
    val forcedCoupThreshold: Int,
    val coinSupply: Int,
)

// ─────────────────────────── PlayerView ───────────────────────────

/**
 * Serializable mirror of [com.kursi.engine.PlayerView].
 *
 * This is the **only** object the server ever serializes to a client. It contains:
 * - The viewer's own secret hand ([myInfluence]) — never present in any other player's [WirePlayerView].
 * - Every opponent's *public* view only — hidden card roles are structurally absent.
 *
 * Enforces the secrecy boundary: [com.kursi.engine.GameState] has no wire mirror here (INV-2 / C1 in
 * 13_integration_notes.md). You cannot accidentally serialize secret state — the type system prevents it.
 */
@Serializable
data class WirePlayerView(
    /** The seat index of the player this view was projected for. */
    val viewer: WirePlayerId,
    val config: WireGameConfig,
    val treasury: Int,
    val deckCount: Int,
    val turnNumber: Int,
    /** The viewer's own coin count. */
    val myCoins: Int,
    /** The viewer's own face-down (secret) roles — only ever sent to the owning player. */
    val myInfluence: List<WireRole>,
    /** The viewer's own face-up (publicly revealed) roles. */
    val myFaceUp: List<WireRole>,
    /**
     * The viewer's OWN cards as ([WireCardId], role, faceUp) — covers both face-down influence and
     * face-up reveals. SECRECY-CRITICAL: contains ONLY the viewer's own cards (mirror of
     * [com.kursi.engine.PlayerView.myCards]); never an opponent's. [myInfluence]/[myFaceUp] stay the
     * canonical role-only lists; [myCards] is the additive CardId↔role resolver a client needs to
     * address a specific owned card when building [WireIntent.ChooseInfluenceToLose] / [WireIntent.ChooseExchange].
     *
     * Defaults to empty for backward-compatible decoding of pre-myCards frames (the additive-evolution
     * contract); the current server always populates it via [com.kursi.engine.PlayerView.myCards].
     */
    val myCards: List<WireOwnCard> = emptyList(),
    /** Public view of every player at the table (including the viewer themselves). */
    val players: List<WireOpponentView>,
    val phase: WirePhaseView,
    /**
     * Schema version tag (H7). Increment whenever a non-additive change is made to the wire format.
     * Clients may use this to detect a server running a newer protocol version and display an
     * "update required" message rather than deserializing incorrectly.
     */
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        /** Monotonic wire schema version. Bump on every breaking change. */
        const val SCHEMA_VERSION: Int = 1
    }
}

// ─────────────────────────── GameEvent ───────────────────────────

/**
 * Serializable mirror of sealed interface [com.kursi.engine.GameEvent] — the descriptive, append-only
 * fact stream the server may attach to a [ServerMessage.StateUpdate] for UI history / animation cues.
 *
 * SECRECY MODEL: most [com.kursi.engine.GameEvent]s describe PUBLIC facts (coins changed, a card flipped
 * face-up during a loss, a player eliminated) and are safe to broadcast verbatim. A few, however, embed
 * a viewer's PRIVATE [WireCardId]s:
 *   - [CardReplaced.drawn] — the fresh face-down card a player drew (replacement / Jaanch redraw).
 *   - [Exchanged.kept]     — the cards the exchanging actor chose to keep face-down.
 * Those CardIds identify still-secret cards; broadcasting them to other seats would let opponents track
 * a specific physical card. They are therefore carried as NULLABLE and projected per-viewer by
 * [com.kursi.protocol.wire.redactEventFor]: the owning seat sees the real CardId, every other seat sees
 * null. The PUBLIC shape of the event (who, the role that was revealed, etc.) is preserved for everyone.
 *
 * Note: [InfluenceLost.role] and [ChallengeRevealed.role] are intentionally PUBLIC — both describe a
 * card being turned FACE-UP for the whole table, which is a public reveal by the rules.
 */
@Serializable
sealed interface WireGameEvent {
    @Serializable
    data class ActionDeclared(
        val actor: WirePlayerId,
        val action: WireAction,
        val claimedRole: WireRole?,
    ) : WireGameEvent

    @Serializable
    data class Challenged(
        val challenger: WirePlayerId,
        val target: WirePlayerId,
        val claimedRole: WireRole,
    ) : WireGameEvent

    /** A card revealed face-up to prove (or disprove) a claim — public by the rules. */
    @Serializable
    data class ChallengeRevealed(
        val player: WirePlayerId,
        val card: WireCardId,
        val role: WireRole,
        val hadRole: Boolean,
    ) : WireGameEvent

    /**
     * A proven card was shuffled back and a fresh one drawn. [returned] is public (it leaves the hand),
     * but [drawn] is the new SECRET face-down card: non-null only in the owning [player]'s projection.
     */
    @Serializable
    data class CardReplaced(
        val player: WirePlayerId,
        val returned: WireCardId,
        val drawn: WireCardId?,
    ) : WireGameEvent

    @Serializable
    data class Blocked(
        val blocker: WirePlayerId,
        val role: WireRole,
        val action: WireAction,
    ) : WireGameEvent

    @Serializable
    data class ActionResolved(
        val actor: WirePlayerId,
        val action: WireAction,
    ) : WireGameEvent

    @Serializable
    data class ActionNegated(
        val actor: WirePlayerId,
        val action: WireAction,
    ) : WireGameEvent

    @Serializable
    data class CoinsChanged(
        val player: WirePlayerId,
        val delta: Int,
    ) : WireGameEvent

    @Serializable
    data class CoinsTransferred(
        val from: WirePlayerId,
        val to: WirePlayerId,
        val amount: Int,
    ) : WireGameEvent

    /** A card flipped face-up (lost). Role is public — it is now visible to the whole table. */
    @Serializable
    data class InfluenceLost(
        val player: WirePlayerId,
        val card: WireCardId,
        val role: WireRole,
        val reason: WireLossReason,
    ) : WireGameEvent

    @Serializable
    data class PlayerEliminated(
        val player: WirePlayerId,
    ) : WireGameEvent

    /**
     * The exchanging actor finished. [returned] cards (to deck) are public; [kept] are the actor's new
     * SECRET face-down cards: non-null only in the [actor]'s own projection, null for everyone else.
     */
    @Serializable
    data class Exchanged(
        val actor: WirePlayerId,
        val kept: List<WireCardId>?,
        val returned: List<WireCardId>,
    ) : WireGameEvent

    /**
     * Jaanch resolved — public fact ONLY. Deliberately carries no role/CardId: per the engine model the
     * examiner's peek is surfaced solely via [WirePhaseView.InvestigatePeek] (and even there the role is
     * dropped), never via this broadcast fact. No other seat ever learns the examined card from this.
     */
    @Serializable
    data class Investigated(
        val examiner: WirePlayerId,
        val target: WirePlayerId,
    ) : WireGameEvent

    /** The examiner forced a redraw. No role leaked publicly. */
    @Serializable
    data class InvestigateRedraw(
        val target: WirePlayerId,
    ) : WireGameEvent

    @Serializable
    data class TurnAdvanced(
        val toSeat: Int,
        val turnNumber: Int,
    ) : WireGameEvent

    @Serializable
    data class GameEnded(
        val winner: WirePlayerId,
    ) : WireGameEvent

    // ── Variant events (emitted only in variant game modes) ────────────────────────────────────────────

    /** BAIL PE BAHAR — a face-up card was restored to face-down by paying the bail cost. */
    @Serializable
    data class InfluenceRestored(
        val player: WirePlayerId,
        val card: WireCardId,
        val role: WireRole,
    ) : WireGameEvent

    /** HAWALA — coins transferred directly between players (bypasses treasury). */
    @Serializable
    data class CoinsGifted(
        val from: WirePlayerId,
        val to: WirePlayerId,
        val amount: Int,
    ) : WireGameEvent

    /** ADHYADESH (Emergency) — declared; actor pays all coins and chain-Coups all alive opponents. */
    @Serializable
    data class EmergencyDeclared(
        val actor: WirePlayerId,
    ) : WireGameEvent

    /** KHAZANA RAJ — a player reached the lifetime coin milestone and won. */
    @Serializable
    data class KhazanaWon(
        val winner: WirePlayerId,
        val lifetimeCoins: Int,
    ) : WireGameEvent

    /** DARJA milestone reached (corruption level 1-4 unlocked by lifetime coins). */
    @Serializable
    data class DarjaReached(
        val player: WirePlayerId,
        val level: Int,
        val lifetimeCoins: Int,
    ) : WireGameEvent
}
