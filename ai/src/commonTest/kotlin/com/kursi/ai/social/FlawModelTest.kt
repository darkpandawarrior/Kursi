package com.kursi.ai.social

import com.kursi.ai.persona.PersonalityProfile
import com.kursi.ai.persona.TargetingBias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [FlawModel] — pure, deterministic mapping from [PersonalityProfile] to flaws.
 *
 * We build profiles inline (no roster dependency) so every assertion is self-contained.
 */
class FlawModelTest {

    // ── Profile fixtures ──────────────────────────────────────────────────────

    /**
     * High-vindictiveness enforcer: should yield VENGEANCE as dominant flaw.
     * Based on Bhai Teja's shape (vindictiveness=0.70, bluffRate=0.20, challengeAggression=0.55).
     */
    private val vindictiveEnforcer = PersonalityProfile(
        bluffRate            = 0.20f,
        challengeAggression  = 0.55f,
        economicAggression   = 0.85f,
        targetingBias        = TargetingBias.LEADER,
        risk                 = 0.55f,
        vindictiveness       = 0.90f,  // very high — should dominate
        predictability       = 0.75f,
    )

    /**
     * High-bluff LEADER-targeting schemer: EGO flaw. VENGEANCE susceptibility stays low
     * (vindictiveness=0.10).  EGO = 0.55*bluffRate + 0.45*(economicAggression*leader) which is
     * 0.55*0.80 + 0.45*(0.60*1) = 0.44 + 0.27 = 0.71.
     */
    private val highBluffLeaderSchemer = PersonalityProfile(
        bluffRate            = 0.80f,
        challengeAggression  = 0.30f,
        economicAggression   = 0.60f,
        targetingBias        = TargetingBias.LEADER,
        risk                 = 0.55f,
        vindictiveness       = 0.05f,
        predictability       = 0.45f,
    )

    /**
     * Cautious banker: GREED flaw is low economicAggression but non-zero; PARANOIA is elevated
     * because challengeAggression=0.60.  Dominant flaw should be PARANOIA.
     */
    private val cautiousChallengerProfile = PersonalityProfile(
        bluffRate            = 0.15f,
        challengeAggression  = 0.85f,   // high challenge → high PARANOIA susceptibility
        economicAggression   = 0.20f,
        targetingBias        = TargetingBias.WEAKEST,
        risk                 = 0.20f,
        vindictiveness       = 0.10f,
        predictability       = 0.70f,
    )

    /**
     * Totally impulsive wildcard: low predictability → high IMPULSE. Should surface IMPULSE.
     */
    private val impulsiveWildcard = PersonalityProfile(
        bluffRate            = 0.60f,
        challengeAggression  = 0.45f,
        economicAggression   = 0.50f,
        targetingBias        = TargetingBias.RANDOM,
        risk                 = 0.70f,
        vindictiveness       = 0.20f,
        predictability       = 0.05f,  // very unpredictable → IMPULSE = 1 - 0.05 = 0.95
    )

    // ── dominantFlaw ─────────────────────────────────────────────────────────

    @Test
    fun dominantFlaw_highVindictiveness_returnsVengeance() {
        assertEquals(CharacterFlaw.VENGEANCE, FlawModel.dominantFlaw(vindictiveEnforcer),
            "high-vindictiveness enforcer should have VENGEANCE as dominant flaw")
    }

    @Test
    fun dominantFlaw_highBluffLeader_returnsEgo() {
        assertEquals(CharacterFlaw.EGO, FlawModel.dominantFlaw(highBluffLeaderSchemer),
            "high-bluff LEADER-targeting profile should have EGO as dominant flaw")
    }

    @Test
    fun dominantFlaw_impulsiveWildcard_returnsImpulse() {
        assertEquals(CharacterFlaw.IMPULSE, FlawModel.dominantFlaw(impulsiveWildcard),
            "very low predictability should surface IMPULSE as dominant flaw")
    }

    @Test
    fun dominantFlaw_highChallenger_returnsParanoia() {
        assertEquals(CharacterFlaw.PARANOIA, FlawModel.dominantFlaw(cautiousChallengerProfile),
            "very high challengeAggression should surface PARANOIA as dominant flaw")
    }

    // ── flawsOf ──────────────────────────────────────────────────────────────

    @Test
    fun flawsOf_alwaysContainsDominantFlaw() {
        listOf(vindictiveEnforcer, highBluffLeaderSchemer, impulsiveWildcard, cautiousChallengerProfile)
            .forEach { profile ->
                val flaws = FlawModel.flawsOf(profile)
                val dominant = FlawModel.dominantFlaw(profile)
                assertTrue(dominant in flaws,
                    "flawsOf must always include the dominant flaw ($dominant)")
            }
    }

    @Test
    fun flawsOf_highVindictiveness_includesVengeance() {
        assertTrue(CharacterFlaw.VENGEANCE in FlawModel.flawsOf(vindictiveEnforcer),
            "high-vindictiveness profile must expose VENGEANCE in flawsOf")
    }

    @Test
    fun flawsOf_highBluffLeader_includesEgo() {
        assertTrue(CharacterFlaw.EGO in FlawModel.flawsOf(highBluffLeaderSchemer),
            "high-bluff LEADER profile must expose EGO in flawsOf")
    }

    // ── susceptibility ───────────────────────────────────────────────────────

    @Test
    fun susceptibility_alwaysInZeroToOne() {
        val profiles = listOf(vindictiveEnforcer, highBluffLeaderSchemer, impulsiveWildcard, cautiousChallengerProfile)
        for (profile in profiles) {
            for (flaw in CharacterFlaw.entries) {
                val s = FlawModel.susceptibility(profile, flaw)
                assertTrue(s in 0f..1f,
                    "susceptibility($flaw) must be in [0,1] for any profile, got $s")
            }
        }
    }

    @Test
    fun susceptibility_vindictiveness_scalesLinearly() {
        // VENGEANCE susceptibility = vindictiveness (raw). No other axis contributes.
        val low  = vindictiveEnforcer.copy(vindictiveness = 0.1f)
        val high = vindictiveEnforcer.copy(vindictiveness = 0.9f)
        assertTrue(FlawModel.susceptibility(high, CharacterFlaw.VENGEANCE) >
                   FlawModel.susceptibility(low,  CharacterFlaw.VENGEANCE),
            "higher vindictiveness must yield higher VENGEANCE susceptibility")
    }

    @Test
    fun susceptibility_impulse_inverseOfPredictability() {
        // IMPULSE = 1 - predictability (exactly).
        val highPredictability = impulsiveWildcard.copy(predictability = 0.90f)
        val lowPredictability  = impulsiveWildcard.copy(predictability = 0.05f)
        assertTrue(FlawModel.susceptibility(lowPredictability,  CharacterFlaw.IMPULSE) >
                   FlawModel.susceptibility(highPredictability, CharacterFlaw.IMPULSE),
            "lower predictability must yield higher IMPULSE susceptibility")
    }

    @Test
    fun susceptibility_presentThreshold_matchesDeclaredConstant() {
        // A flaw that passes PRESENT_THRESHOLD appears in flawsOf.
        val profile = vindictiveEnforcer
        for (flaw in CharacterFlaw.entries) {
            val s = FlawModel.susceptibility(profile, flaw)
            if (s >= FlawModel.PRESENT_THRESHOLD) {
                assertTrue(flaw in FlawModel.flawsOf(profile),
                    "flaw $flaw with susceptibility $s >= threshold must be in flawsOf")
            }
        }
    }
}
