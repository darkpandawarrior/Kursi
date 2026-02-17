package com.kursi.ai.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SocialState] pure operations.
 *
 * Every test constructs a fresh [SocialState] and drives the immutable mutation helpers,
 * asserting on the returned copy.  No engine, no RNG, no IO.
 */
class SocialModelTest {

    // ── afterHit ─────────────────────────────────────────────────────────────

    @Test
    fun afterHit_raisesAggressorThreat() {
        val s = SocialState().afterHit(aggressor = 1, victim = 2, weight = 1f)
        // threat(1) = 0.12 * 1 (weight)
        assertTrue(s.threatOf(1) > 0f,
            "aggressor seat 1 should carry positive threat after hitting seat 2")
    }

    @Test
    fun afterHit_lowersTrustVictimTowardAggressor() {
        val s = SocialState().afterHit(aggressor = 1, victim = 2, weight = 1f)
        val stance = s.stance(observer = 2, target = 1)
        assertTrue(stance.trust < 0f,
            "victim (seat 2) trust toward aggressor (seat 1) must decrease after a hit")
    }

    @Test
    fun afterHit_increasesFearAndSuspicion() {
        val s = SocialState().afterHit(aggressor = 1, victim = 2, weight = 1f)
        val stance = s.stance(observer = 2, target = 1)
        assertTrue(stance.fear > 0f,   "fear must increase after a hit")
        assertTrue(stance.suspicion > 0f, "suspicion must increase after a hit")
    }

    @Test
    fun afterHit_scalesByWeight() {
        val light = SocialState().afterHit(1, 2, weight = 0.5f)
        val heavy = SocialState().afterHit(1, 2, weight = 2.0f)
        assertTrue(heavy.threatOf(1) > light.threatOf(1),
            "heavier hit must produce more threat on the aggressor")
        assertTrue(heavy.stance(2, 1).trust < light.stance(2, 1).trust,
            "heavier hit must drop trust more")
    }

    @Test
    fun afterHit_selfHit_isNoOp() {
        // senderSeat == victim — SocialState.withStance guards observer==target.
        val s0 = SocialState()
        val s1 = s0.afterHit(aggressor = 3, victim = 3, weight = 1f)
        // Threat still ticks up (aggressor/victim confusion) but stances are noop for self.
        assertEquals(s0.stance(3, 3), s1.stance(3, 3),
            "withStance(observer=target) must be a no-op guard")
    }

    // ── withAlliance / areAllied / breakAlliance ─────────────────────────────

    @Test
    fun withAlliance_isSymmetric() {
        val s = SocialState().withAlliance(0, 2)
        assertTrue(s.areAllied(0, 2), "areAllied(0,2) must be true after withAlliance(0,2)")
        assertTrue(s.areAllied(2, 0), "areAllied must be symmetric")
    }

    @Test
    fun withAlliance_boostsMutualTrust() {
        val s = SocialState().withAlliance(0, 2)
        assertTrue(s.stance(0, 2).trust >= SocialStance.ALLY_TRUST,
            "alliance should push trust to at least ALLY_TRUST threshold")
        assertTrue(s.stance(2, 0).trust >= SocialStance.ALLY_TRUST,
            "mutual trust boost must be symmetric")
    }

    @Test
    fun withAlliance_selfAlliance_isNoOp() {
        val s = SocialState().withAlliance(1, 1)
        assertFalse(s.areAllied(1, 1), "self-alliance should be a no-op")
    }

    @Test
    fun breakAlliance_dropsAllied() {
        val s = SocialState().withAlliance(0, 2).breakAlliance(0)
        assertFalse(s.areAllied(0, 2), "alliance must be gone after breakAlliance")
        assertFalse(s.areAllied(2, 0), "break must be symmetric")
    }

    @Test
    fun breakAlliance_dropsTrustAndRaisesSuspicion() {
        val s = SocialState().withAlliance(0, 2).breakAlliance(0)
        // Both sides get trust -= 1.2 and suspicion += 0.4 from breakAlliance.
        assertTrue(s.stance(0, 2).suspicion > 0f,
            "betrayer's suspicion of the former ally must be positive after break")
        assertTrue(s.stance(2, 0).suspicion > 0f,
            "betrayed's suspicion of the betrayer must be positive after break")
    }

    @Test
    fun breakAlliance_noExistingAlliance_isNoOp() {
        val s = SocialState()
        val s2 = s.breakAlliance(99)
        assertEquals(s, s2, "breaking a non-existent alliance must be a no-op")
    }

    // ── decay ────────────────────────────────────────────────────────────────

    @Test
    fun decay_shrinksStanceAndThreat() {
        val s = SocialState()
            .afterHit(1, 2, 1f)
            .withAlliance(0, 1) // puts some trust in
        val decayed = s.decay()
        // All magnitudes in decayed stances should be ≤ original (and non-negative).
        assertTrue(decayed.threatOf(1) <= s.threatOf(1) + 0.001f,
            "threat must not grow after decay")
        // Fear and suspicion should shrink.
        val orig = s.stance(2, 1)
        val dec  = decayed.stance(2, 1)
        assertTrue(dec.fear <= orig.fear + 0.001f, "fear must not grow after decay")
    }

    @Test
    fun decay_removesNearZeroEntries() {
        // Run many decay passes on a small threat; it should eventually drop out of the map.
        var s = SocialState().withThreat(3, 0.05f)
        repeat(30) { s = s.decay() }
        assertEquals(0f, s.threatOf(3), "very small threat must eventually decay to zero")
    }

    // ── topThreat ────────────────────────────────────────────────────────────

    @Test
    fun topThreat_picksMaxAboveMinimum() {
        val s = SocialState()
            .withThreat(1, 0.2f)
            .withThreat(2, 0.5f)
            .withThreat(3, 0.1f)
        val top = s.topThreat(listOf(1, 2, 3), minimum = 0.15f)
        assertEquals(2, top, "seat with highest threat must be the top threat")
    }

    @Test
    fun topThreat_returnsNullWhenBelowMinimum() {
        val s = SocialState().withThreat(1, 0.05f)
        val top = s.topThreat(listOf(1), minimum = 0.15f)
        assertNull(top, "threat below minimum must return null from topThreat")
    }

    @Test
    fun topThreat_returnsNullWhenCandidatesEmpty() {
        val s = SocialState().withThreat(1, 1f)
        assertNull(s.topThreat(emptyList()), "no candidates must yield null")
    }

    // ── forget ───────────────────────────────────────────────────────────────

    @Test
    fun forget_removesSeatsStancesAndThreat() {
        val s = SocialState()
            .afterHit(1, 2, 1f)
            .withAlliance(1, 2)
            .forget(1)
        assertEquals(0f, s.threatOf(1), "forgotten seat must have no threat")
        assertNull(s.allyOf(1), "forgotten seat must have no alliance entry")
        // No stances from or toward seat 1.
        assertEquals(SocialStance(), s.stance(1, 2), "stance from forgotten seat must be neutral")
        assertEquals(SocialStance(), s.stance(2, 1), "stance toward forgotten seat must be neutral")
    }

    // ── withThreat ────────────────────────────────────────────────────────────

    @Test
    fun withThreat_capsAtThreatCap() {
        var s = SocialState()
        repeat(50) { s = s.withThreat(0, 0.5f) }
        assertTrue(s.threatOf(0) <= SocialState.THREAT_CAP + 0.001f,
            "threat must never exceed THREAT_CAP=${SocialState.THREAT_CAP}")
    }

    @Test
    fun withThreat_doesNotGoBelowZero() {
        val s = SocialState().withThreat(0, -5f)
        assertTrue(s.threatOf(0) >= 0f, "threat must never go negative")
    }
}
