package com.kursi.feature.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M6e TAMASHA / DEMO — in spectator mode the advisor auto-plays the human seat too, so the whole game
 * runs itself with zero human input. We run a real-dispatcher VM (FAST turn speed to keep it brisk) and
 * poll until it reaches game-over on its own.
 *
 * Uses a real [Dispatchers.Default] scope rather than virtual time because the spectator chain interleaves
 * the paced bot-advance loop with ISMCTS best-move computation across many nested coroutines; a wall-clock
 * poll is the most robust way to assert the demo plays itself end-to-end.
 */
class SpectatorModeTest {

    @Test
    fun spectatorGame_playsItselfToGameOver() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vm = GameViewModel(scope = scope)
        vm.onAction(
            GameAction.NewGame(
                playerCount = 2,
                difficulty = Difficulty.Easy,
                seed = 1L,
                spectator = true,
            ),
        )
        // No human input at all — poll until the spectator loop carries the game to game-over.
        val reachedOver = withTimeoutOrNull(20_000) {
            while (vm.state.value?.isGameOver != true) delay(25)
            true
        }
        val state = vm.state.value
        vm.clear()
        assertNotNull(state)
        assertTrue(
            reachedOver == true && state.isGameOver,
            "a spectator demo must auto-play all the way to game-over (turn=${state.view.turnNumber})",
        )
    }

    @Test
    fun nonSpectatorGame_doesNotAutoPlay_andWaitsForHuman() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vm = GameViewModel(scope = scope)
        vm.onAction(
            GameAction.NewGame(
                playerCount = 2,
                difficulty = Difficulty.Easy,
                seed = 1L,
                spectator = false,
            ),
        )
        // Give any (incorrect) auto-play a generous window to misbehave; it must NOT advance past the human.
        delay(1500)
        val state = vm.state.value
        vm.clear()
        assertNotNull(state)
        // Seat 0 opens with seed 1; without spectator auto-play the VM must STILL be waiting on the human.
        assertTrue(state.isHumanTurn && !state.isGameOver, "non-spectator game waits for the human's move")
    }
}
