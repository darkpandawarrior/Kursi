package com.kursi.engine

/** Player-id shorthands for tests. */
val P = (0..9).map { PlayerId(it) }

fun ApplyOutcome.ok(): GameState = (this as ApplyOutcome.Accepted).state
fun ApplyOutcome.evts(): List<GameEvent> = (this as ApplyOutcome.Accepted).events

fun cfg(n: Int): GameConfig = GameConfig.forPlayers(n)

/**
 * Builds a fully-controlled state: each seat gets the exact face-down [hands] roles, the rest of the deck
 * is filled deterministically, and card-conservation (I1) holds by construction. For unit rulings only.
 */
fun buildState(
    config: GameConfig,
    hands: List<List<Role>>,
    coins: List<Int>,
    phase: Phase = Phase.AwaitingAction(0),
    turnNumber: Int = 1,
): GameState {
    require(hands.size == config.seatCount && coins.size == config.seatCount)
    val cards = LinkedHashMap<CardId, Role>()
    var id = 0
    for (role in config.activeRoles) repeat(config.copiesPerRole) { cards[CardId(id++)] = role }
    val byRole: Map<Role, MutableList<CardId>> =
        config.activeRoles.associateWith { r -> cards.filter { it.value == r }.keys.sortedBy { it.raw }.toMutableList() }

    val locations = LinkedHashMap<CardId, CardLocation>()
    val players = ArrayList<PlayerState>()
    hands.forEachIndexed { seat, hand ->
        val pid = PlayerId(seat)
        for (role in hand) {
            val c = byRole.getValue(role).removeAt(0)
            locations[c] = CardLocation.Hand(pid, faceUp = false)
        }
        players.add(PlayerState(pid, seat, coins[seat]))
    }
    for (i in 0 until config.deckSize) if (!locations.containsKey(CardId(i))) locations[CardId(i)] = CardLocation.Deck

    val treasury = config.coinSupply - coins.sum()
    return GameState(config, cards, locations, players, treasury, phase, RngState(seed = 1L, step = 0L), turnNumber)
}
