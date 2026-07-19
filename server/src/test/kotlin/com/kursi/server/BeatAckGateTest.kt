@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kursi.server

import com.kursi.engine.Action
import com.kursi.engine.GameEvent
import com.kursi.engine.PlayerId
import com.kursi.engine.Role
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deterministic, sleep-free tests for [BeatAckGate] — Track 6's bounded server-side beat-ack timeout.
 * Every test drives [BeatAckGate] with the [runTest] virtual-time [kotlinx.coroutines.test.TestScope] as
 * its [kotlinx.coroutines.CoroutineScope] and advances time explicitly instead of sleeping, per the
 * module's "NO real sleeps in tests" rule.
 */
class BeatAckGateTest {
    @Test
    fun `fires onTimeout after the bounded wait elapses with no ack`() =
        runTest {
            var fired = 0
            val gate = BeatAckGate(scope = this, timeoutMs = 1_000L) { fired++ }

            gate.arm()
            assertTrue(gate.isPending, "gate should be pending immediately after arm()")
            assertEquals(0, fired, "must not fire before the timeout elapses")

            advanceTimeBy(999L)
            runCurrent()
            assertEquals(0, fired, "must not fire a millisecond early")

            advanceTimeBy(2L)
            runCurrent()
            assertEquals(1, fired, "must auto-continue once the bounded wait elapses")
            assertFalse(gate.isPending, "gate must clear itself once it fires")
        }

    @Test
    fun `cancel before the timeout suppresses onTimeout — the ack path`() =
        runTest {
            var fired = 0
            val gate = BeatAckGate(scope = this, timeoutMs = 1_000L) { fired++ }

            gate.arm()
            gate.cancel() // simulates MatchActor.forceAdvanceNow() releasing an early human ack

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(0, fired, "a cancelled wait must never fire onTimeout")
            assertFalse(gate.isPending)
        }

    @Test
    fun `cancel with nothing pending is a harmless no-op`() =
        runTest {
            var fired = 0
            val gate = BeatAckGate(scope = this, timeoutMs = 1_000L) { fired++ }

            gate.cancel() // stray ack — no beat was ever armed
            assertFalse(gate.isPending)

            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(0, fired)
        }

    @Test
    fun `re-arming replaces the in-flight wait instead of stacking timers`() =
        runTest {
            var fired = 0
            val gate = BeatAckGate(scope = this, timeoutMs = 1_000L) { fired++ }

            gate.arm()
            advanceTimeBy(600L)
            runCurrent()

            gate.arm() // a second meaningful beat lands before the first wait elapsed — restart the clock
            advanceTimeBy(600L) // 1200ms since the FIRST arm(), but only 600ms since the SECOND
            runCurrent()
            assertEquals(0, fired, "the stale first timer must not fire after being replaced")

            advanceTimeBy(500L) // now 1100ms since the second arm()
            runCurrent()
            assertEquals(1, fired, "exactly one onTimeout fires, from the LATEST arm()")
        }

    @Test
    fun `isMeaningfulBeat classifies dramatic and routine events as meaningful, bookkeeping as trivial`() {
        val p0 = PlayerId(0)
        val p1 = PlayerId(1)

        // Dramatic: a challenge is meaningful.
        assertTrue(isMeaningfulBeat(listOf(GameEvent.Challenged(p0, p1, Role.NETA))))
        // Routine: a declared action is meaningful.
        assertTrue(isMeaningfulBeat(listOf(GameEvent.ActionDeclared(p0, Action.Tax, Role.NETA))))
        // Trivial: a bare coin tick alone must NOT be paced (mirrors offline BeatGate.tierFor).
        assertFalse(isMeaningfulBeat(listOf(GameEvent.CoinsChanged(p0, delta = 1))))
        assertFalse(isMeaningfulBeat(emptyList()))
    }
}
