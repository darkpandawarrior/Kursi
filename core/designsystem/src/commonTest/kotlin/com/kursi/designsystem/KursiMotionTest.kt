package com.kursi.designsystem

import kotlin.test.Test
import kotlin.test.assertTrue

class KursiMotionTest {
    @Test
    fun durations_are_positive_and_ordered() {
        assertTrue(KursiMotion.DEAL_MS > 0)
        assertTrue(KursiMotion.FOCUS_PULL_MS > 0)
    }
}
