package com.kursi.feature.game.session

import com.kursi.engine.Action
import com.kursi.engine.CardId
import com.kursi.engine.Intent
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.narrative.ArcId
import com.kursi.feature.game.narrative.ChatActionKind
import com.kursi.feature.game.narrative.HumanChatInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MatchSnapshot — the persisted record of an in-progress local game (M3 §2).
 *
 * The engine is fully deterministic: given the same [seed] and [config][players]/[difficulty],
 * replaying the [humanLog] of human intents reconstructs the EXACT same `GameState` (bots replay
 * from the same policies + redacted views). So we persist only the cheap, stable inputs — never
 * the heavyweight `GameState` itself. [GameSession.restore] consumes the decoded intents.
 *
 * Intents are mirrored into [SnapIntent] so the value-class `PlayerId`/`CardId` raw ints
 * serialize cleanly without annotating the pure engine types.
 */
@Serializable
data class MatchSnapshot(
    val version: Int = CURRENT_VERSION,
    val seed: Long,
    val players: Int,
    val difficulty: String, // Difficulty.name
    val humanLog: List<SnapIntent>,
    /** M5 pass-and-play: number of hot-seat humans (default 1 = vs-AI, for back-compat decode). */
    val humanCount: Int = 1,
    /** DARBAR: was this a narrative-mode match (bots chat + arcs + social nudges)? (v2; defaults false). */
    val narrativeEnabled: Boolean = false,
    /** DARBAR: the player's chat log, in send order, each tagged with the human-move index (v2). */
    val chatLog: List<SnapChat> = emptyList(),
) {
    val difficultyEnum: Difficulty
        get() = Difficulty.entries.firstOrNull { it.name == difficulty } ?: Difficulty.Medium

    fun encode(): String = json.encodeToString(this)

    companion object {
        // v2 adds the DARBAR chat log. encodeDefaults + ignoreUnknownKeys keep v1<->v2 cross-readable.
        const val CURRENT_VERSION = 2
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Decode a persisted snapshot string; returns null on corruption or a newer-than-known version. */
        fun decode(raw: String?): MatchSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<MatchSnapshot>(raw) }
                .getOrNull()
                ?.takeIf { it.version in 1..CURRENT_VERSION }
        }

        /** Build a snapshot from a live session's identity + its human action log (+ optional Darbar log). */
        fun of(
            seed: Long,
            players: Int,
            difficulty: Difficulty,
            humanLog: List<Intent>,
            humanCount: Int = 1,
            narrativeEnabled: Boolean = false,
            chatLog: List<Pair<Int, HumanChatInput>> = emptyList(),
        ): MatchSnapshot = MatchSnapshot(
            seed = seed,
            players = players,
            difficulty = difficulty.name,
            humanLog = humanLog.map { it.toSnap() },
            humanCount = humanCount,
            narrativeEnabled = narrativeEnabled,
            chatLog = chatLog.map { (after, input) -> input.toSnap(after) },
        )
    }
}

/** Serializable mirror of a [HumanChatInput], tagged with the human-move index it was sent at. */
@Serializable
data class SnapChat(
    val afterHumanMoves: Int,
    val suggestionId: String,
    val kind: String,
    val arc: String? = null,
    val targetSeat: Int? = null,
    val freeText: String? = null,
)

fun HumanChatInput.toSnap(afterHumanMoves: Int): SnapChat = SnapChat(
    afterHumanMoves = afterHumanMoves,
    suggestionId = suggestionId,
    kind = kind.name,
    arc = arc?.name,
    targetSeat = targetSeat,
    freeText = freeText,
)

fun SnapChat.toInput(): HumanChatInput = HumanChatInput(
    suggestionId = suggestionId,
    kind = ChatActionKind.entries.firstOrNull { it.name == kind } ?: ChatActionKind.TAUNT,
    arc = arc?.let { a -> ArcId.entries.firstOrNull { it.name == a } },
    targetSeat = targetSeat,
    freeText = freeText,
)

/** Serializable mirror of the engine [Role]. PATRAKAAR appended LAST to keep prior ordinals stable. */
@Serializable
enum class SnapRole { NETA, BHAI, BABU, JUGAADU, VAKIL, PATRAKAAR }

/** Serializable mirror of [Action] — targets carried as raw seat ints. */
@Serializable
sealed interface SnapAction {
    @Serializable data object Income : SnapAction
    @Serializable data object ForeignAid : SnapAction
    @Serializable data class Coup(val target: Int) : SnapAction
    @Serializable data object Tax : SnapAction
    @Serializable data class Assassinate(val target: Int) : SnapAction
    @Serializable data class Steal(val target: Int) : SnapAction
    @Serializable data object Exchange : SnapAction
    @Serializable data class Investigate(val target: Int) : SnapAction
}

/** Serializable mirror of [Intent] — the only thing the human ever contributes to the replay. */
@Serializable
sealed interface SnapIntent {
    val actor: Int
    @Serializable data class DeclareAction(override val actor: Int, val action: SnapAction) : SnapIntent
    @Serializable data class Challenge(override val actor: Int) : SnapIntent
    @Serializable data class Block(override val actor: Int, val role: SnapRole) : SnapIntent
    @Serializable data class Pass(override val actor: Int) : SnapIntent
    @Serializable data class ChooseInfluenceToLose(override val actor: Int, val card: Int) : SnapIntent
    @Serializable data class ChooseExchange(override val actor: Int, val keep: List<Int>) : SnapIntent
    @Serializable data class ResolveInvestigate(override val actor: Int, val forceRedraw: Boolean) : SnapIntent
}

// ─────────────────────────── codecs ───────────────────────────

private fun Role.toSnap(): SnapRole = when (this) {
    Role.NETA -> SnapRole.NETA
    Role.BHAI -> SnapRole.BHAI
    Role.BABU -> SnapRole.BABU
    Role.JUGAADU -> SnapRole.JUGAADU
    Role.VAKIL -> SnapRole.VAKIL
    Role.PATRAKAAR -> SnapRole.PATRAKAAR
}

private fun SnapRole.toEngine(): Role = when (this) {
    SnapRole.NETA -> Role.NETA
    SnapRole.BHAI -> Role.BHAI
    SnapRole.BABU -> Role.BABU
    SnapRole.JUGAADU -> Role.JUGAADU
    SnapRole.VAKIL -> Role.VAKIL
    SnapRole.PATRAKAAR -> Role.PATRAKAAR
}

private fun Action.toSnap(): SnapAction = when (this) {
    Action.Income -> SnapAction.Income
    Action.ForeignAid -> SnapAction.ForeignAid
    is Action.Coup -> SnapAction.Coup(target.raw)
    Action.Tax -> SnapAction.Tax
    is Action.Assassinate -> SnapAction.Assassinate(target.raw)
    is Action.Steal -> SnapAction.Steal(target.raw)
    Action.Exchange -> SnapAction.Exchange
    is Action.Investigate -> SnapAction.Investigate(target.raw)
}

private fun SnapAction.toEngine(): Action = when (this) {
    SnapAction.Income -> Action.Income
    SnapAction.ForeignAid -> Action.ForeignAid
    is SnapAction.Coup -> Action.Coup(PlayerId(target))
    SnapAction.Tax -> Action.Tax
    is SnapAction.Assassinate -> Action.Assassinate(PlayerId(target))
    is SnapAction.Steal -> Action.Steal(PlayerId(target))
    SnapAction.Exchange -> Action.Exchange
    is SnapAction.Investigate -> Action.Investigate(PlayerId(target))
}

fun Intent.toSnap(): SnapIntent = when (this) {
    is Intent.DeclareAction -> SnapIntent.DeclareAction(actor.raw, action.toSnap())
    is Intent.Challenge -> SnapIntent.Challenge(actor.raw)
    is Intent.Block -> SnapIntent.Block(actor.raw, role.toSnap())
    is Intent.Pass -> SnapIntent.Pass(actor.raw)
    is Intent.ChooseInfluenceToLose -> SnapIntent.ChooseInfluenceToLose(actor.raw, card.raw)
    is Intent.ChooseExchange -> SnapIntent.ChooseExchange(actor.raw, keep.map { it.raw })
    is Intent.ResolveInvestigate -> SnapIntent.ResolveInvestigate(actor.raw, forceRedraw)
}

fun SnapIntent.toEngine(): Intent = when (this) {
    is SnapIntent.DeclareAction -> Intent.DeclareAction(PlayerId(actor), action.toEngine())
    is SnapIntent.Challenge -> Intent.Challenge(PlayerId(actor))
    is SnapIntent.Block -> Intent.Block(PlayerId(actor), role.toEngine())
    is SnapIntent.Pass -> Intent.Pass(PlayerId(actor))
    is SnapIntent.ChooseInfluenceToLose -> Intent.ChooseInfluenceToLose(PlayerId(actor), CardId(card))
    is SnapIntent.ChooseExchange -> Intent.ChooseExchange(PlayerId(actor), keep.map { CardId(it) })
    is SnapIntent.ResolveInvestigate -> Intent.ResolveInvestigate(PlayerId(actor), forceRedraw)
}
