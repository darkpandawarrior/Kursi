package com.kursi.feature.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Builds a real [GameUiState] the same way [GameViewModelTest] does — a fresh 2p game, seed 1. */
private fun sampleUiState(): GameUiState {
    val vm = GameViewModel()
    vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))
    return requireNotNull(vm.state.value) { "state should be non-null after NewGame" }
}

class DensityLayerTest {
    @Test
    fun fromName_maps_known_values() {
        assertEquals(DensityLayer.FOCUS, DensityLayer.fromName("FOCUS"))
        assertEquals(DensityLayer.GUIDED, DensityLayer.fromName("GUIDED"))
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName("ANALYST"))
    }

    @Test
    fun fromName_unknown_or_null_defaults_to_analyst() {
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName(null))
        assertEquals(DensityLayer.ANALYST, DensityLayer.fromName("garbage"))
    }

    @Test
    fun uiState_defaults_to_analyst() {
        // ANALYST default preserves today's full-instrument behavior.
        assertEquals(DensityLayer.ANALYST, sampleUiState().densityLayer)
    }
}
