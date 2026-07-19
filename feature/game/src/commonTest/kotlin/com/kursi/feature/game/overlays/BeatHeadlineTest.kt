package com.kursi.feature.game.overlays

import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.GameAction
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.GameViewModel
import com.kursi.feature.game.KursiVoice
import com.kursi.feature.game.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks in the MUNSHI upgrade-in-place rule (spec §8.1, §8.6): [displayHeadlineFor] must show the
 * templated [headlineFor] line whenever [GameUiState.narrationText] hasn't landed (null or blank —
 * the AI-off / no-provider floor), and the narration line the moment it has.
 */
class BeatHeadlineTest {
    private val voice = KursiVoice(Language.HINGLISH)

    private fun freshState(): GameUiState {
        val vm = GameViewModel()
        vm.onAction(GameAction.NewGame(playerCount = 2, difficulty = Difficulty.Easy, seed = 1L))
        return requireNotNull(vm.state.value)
    }

    @Test
    fun displayHeadlineFor_matchesTemplatedLine_whenNarrationTextIsNull() {
        val state = freshState()

        assertEquals(headlineFor(state.recentEvents, state, voice), displayHeadlineFor(state.recentEvents, state, voice))
    }

    @Test
    fun displayHeadlineFor_matchesTemplatedLine_whenNarrationTextIsBlank() {
        val state = freshState().copy(narrationText = "   ")

        assertEquals(headlineFor(state.recentEvents, state, voice), displayHeadlineFor(state.recentEvents, state, voice))
    }

    @Test
    fun displayHeadlineFor_prefersNarrationText_whenPresent() {
        val state = freshState().copy(narrationText = "Bahenji stamped the Neta's seal for tax.")

        assertEquals("Bahenji stamped the Neta's seal for tax.", displayHeadlineFor(state.recentEvents, state, voice))
    }
}
