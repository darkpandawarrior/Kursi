package com.kursi.feature.game.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kursi.designsystem.BrandTokens
import com.kursi.designsystem.KursiDimens
import com.kursi.designsystem.KursiNeutrals
import com.kursi.designsystem.KursiRadii
import com.kursi.designsystem.KursiType
import com.kursi.designsystem.Squircle
import com.kursi.engine.GameEvent
import com.kursi.feature.game.GameUiState
import com.kursi.feature.game.KursiVoice
import com.kursi.feature.game.LocalKursiVoice
import com.kursi.feature.game.status.isRecapWorthy
import com.kursi.feature.game.status.recapNames

/**
 * FOCUS/GUIDED "what's happening" headline (spec §6, §8.1) — one calm, plain-language sentence
 * for the most recent meaningful beat, replacing ANALYST's denser [com.kursi.feature.game.status.RecapRail].
 *
 * Templated + deterministic today: it reuses [KursiVoice.recap], the same bilingual copy source
 * ANALYST's recap strip already draws from. This is a deliberate seam — Track 3 (the AI Munshi
 * narrator, spec §8.1) swaps the body of [headlineFor] for an LLM-generated line without any
 * caller needing to change, since the signature (events in, one sentence out) stays the same.
 */
fun headlineFor(
    events: List<GameEvent>,
    state: GameUiState,
    voice: KursiVoice,
): String {
    val event = events.lastOrNull { isRecapWorthy(it) } ?: return voice.opponentActing(actorName(state))
    val (actor, other) = recapNames(event, state)
    return voice.recap(event, actor, other) ?: voice.opponentActing(actorName(state))
}

@Composable
internal fun BeatHeadline(
    state: GameUiState,
    modifier: Modifier = Modifier,
) {
    val voice = LocalKursiVoice.current
    val line = headlineFor(state.recentEvents, state, voice)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Squircle(KursiRadii.sm))
                .background(BrandTokens.TeakDark.copy(alpha = 0.85f))
                .border(
                    KursiDimens.stroke_hairline,
                    BrandTokens.BrassDark.copy(alpha = 0.5f),
                    Squircle(KursiRadii.sm),
                ).padding(horizontal = KursiDimens.space_sm, vertical = 6.dp)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = line
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KursiDimens.space_sm),
    ) {
        Text(
            text = line,
            style = KursiType.body,
            color = KursiNeutrals.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
