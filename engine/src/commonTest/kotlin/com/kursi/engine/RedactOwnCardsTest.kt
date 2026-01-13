package com.kursi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the additive redaction API: [PlayerView.myCards] (CardId↔role resolver for the viewer's OWN
 * cards) and [PhaseView.Exchange.drawn] (the drawn cards, surfaced ONLY to the exchanging actor).
 * Secrecy is the hard requirement: nothing here may leak an opponent's hidden card identity.
 */
class RedactOwnCardsTest {

    // (a) A viewer sees its OWN card roles via myCards, matching the underlying hand exactly.
    @Test
    fun myCards_resolves_the_viewers_own_cards_to_roles() {
        for (n in 3..6) {
            val state = initialState(GameConfig.forPlayers(n), seed = 42L + n)
            for (seat in 0 until n) {
                val viewer = PlayerId(seat)
                val view = redact(state, viewer)

                // myCards must be exactly the viewer's face-down + face-up cards, with correct roles + faceUp.
                val expected =
                    state.faceDownInfluence(viewer).map { OwnCard(it, state.cards.getValue(it), faceUp = false) } +
                        state.faceUpCards(viewer).map { OwnCard(it, state.cards.getValue(it), faceUp = true) }
                assertEquals(expected.toSet(), view.myCards.toSet(),
                    "myCards must resolve exactly the viewer's own cards (seat $seat, n=$n)")

                // Consistency with the canonical role-only lists.
                assertEquals(
                    view.myInfluence.sortedBy { it.ordinal },
                    view.myCards.filter { !it.faceUp }.map { it.role }.sortedBy { it.ordinal },
                    "face-down roles in myCards must agree with myInfluence",
                )
                assertEquals(
                    view.myFaceUp.sortedBy { it.ordinal },
                    view.myCards.filter { it.faceUp }.map { it.role }.sortedBy { it.ordinal },
                    "face-up roles in myCards must agree with myFaceUp",
                )
            }
        }
    }

    // (b) SECRECY: no opponent's hidden (face-down) card identity may appear in any viewer's myCards.
    @Test
    fun myCards_never_leaks_an_opponents_hidden_card() {
        val n = 5
        val state = initialState(GameConfig.forPlayers(n), seed = 7L)
        for (seat in 0 until n) {
            val viewer = PlayerId(seat)
            val view = redact(state, viewer)
            val mine = view.myCards.map { it.id }.toSet()

            // Every CardId in myCards belongs to the viewer; none belongs to any opponent's face-down hand.
            for (other in 0 until n) {
                if (other == seat) continue
                val opponentHidden = state.faceDownInfluence(PlayerId(other)).toSet()
                assertTrue(opponentHidden.none { it in mine },
                    "viewer $seat must not see opponent $other's hidden cards via myCards")
            }
            // Positive: all of the viewer's own face-down cards ARE present.
            assertTrue(state.faceDownInfluence(viewer).all { it in mine })
        }
    }

    // (b') Indistinguishability: swapping an opponent's hidden role must not change the viewer's myCards.
    @Test
    fun myCards_is_invariant_to_an_opponents_hidden_role() {
        val config = GameConfig.forPlayers(4)
        val s = initialState(config, seed = 11L)
        val viewer = PlayerId(0)
        val other = PlayerId(1)
        val otherCard = s.faceDownInfluence(other).first()
        val otherRole = s.cards.getValue(otherCard)
        val deckCard = s.deckCards.first { s.cards.getValue(it) != otherRole }
        val deckRole = s.cards.getValue(deckCard)
        val swapped = s.copy(cards = s.cards + mapOf(otherCard to deckRole, deckCard to otherRole))
        assertEquals(redact(s, viewer).myCards, redact(swapped, viewer).myCards,
            "viewer's myCards must not depend on an opponent's hidden card identity")
    }

    // Helper: drive a fresh game until PlayerId(0) is the actor in AwaitingExchange (declares Exchange/Setting).
    private fun reachExchange(): GameState {
        // 2-player game: seat 0 always acts first; declare Exchange — no one is eligible to challenge a
        // bluff except seat 1; force a deterministic path by passing every reaction.
        var state = initialState(GameConfig.forPlayers(2), seed = 3L)
        // Seat 0 declares Exchange (Setting). Bluffing is allowed, so this is always legal.
        state = (applyIntent(state, Intent.DeclareAction(PlayerId(0), Action.Exchange)) as ApplyOutcome.Accepted).state
        // Resolve the CHALLENGE_ACTION window: everyone passes so the action survives to the effect.
        var guard = 0
        while (state.phase is Phase.AwaitingReactions) {
            val who = whoActsNext(state)!!
            state = (applyIntent(state, Intent.Pass(who)) as ApplyOutcome.Accepted).state
            check(guard++ < 16)
        }
        return state
    }

    // (c) PhaseView.Exchange.drawn is populated for the actor's OWN view and empty for everyone else.
    @Test
    fun exchange_drawn_is_populated_for_actor_and_empty_for_others() {
        val state = reachExchange()
        val ex = state.phase
        assertTrue(ex is Phase.AwaitingExchange, "expected AwaitingExchange, was ${state.phase}")
        val actor = ex.actor
        assertEquals(PlayerId(0), actor)
        assertTrue(ex.drawn.isNotEmpty(), "engine should have drawn cards for the exchange")

        // Actor's own view: drawn carries the actual drawn cards, resolved to roles, all face-down.
        val actorView = redact(state, actor)
        val actorPhase = actorView.phase
        assertTrue(actorPhase is PhaseView.Exchange)
        assertEquals(actor, actorPhase.actor)
        val expectedDrawn = ex.drawn.map { OwnCard(it, state.cards.getValue(it), faceUp = false) }
        assertEquals(expectedDrawn, actorPhase.drawn, "actor must see its own drawn cards")

        // Every OTHER viewer: drawn is empty (the drawn cards are the actor's private info).
        for (seat in 0 until state.config.seatCount) {
            if (PlayerId(seat) == actor) continue
            val otherView = redact(state, PlayerId(seat))
            val otherPhase = otherView.phase
            assertTrue(otherPhase is PhaseView.Exchange)
            assertEquals(actor, otherPhase.actor, "the actor's identity is public")
            assertTrue(otherPhase.drawn.isEmpty(),
                "viewer $seat must NOT see the actor's drawn cards")
        }
    }

    // (c') SECRECY: the drawn CardIds must never appear in a non-actor viewer's myCards either.
    @Test
    fun exchange_drawn_cards_do_not_leak_into_other_viewers_myCards() {
        val state = reachExchange()
        val ex = state.phase as Phase.AwaitingExchange
        val drawnIds = ex.drawn.toSet()
        for (seat in 0 until state.config.seatCount) {
            if (PlayerId(seat) == ex.actor) continue
            val view = redact(state, PlayerId(seat))
            assertTrue(view.myCards.none { it.id in drawnIds },
                "viewer $seat must not see the actor's drawn cards via myCards")
            val phase = view.phase as PhaseView.Exchange
            assertTrue(phase.drawn.none { it.id in drawnIds })
        }
    }
}
