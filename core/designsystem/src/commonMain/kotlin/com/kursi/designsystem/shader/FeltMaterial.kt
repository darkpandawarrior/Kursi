package com.kursi.designsystem.shader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.kursi.designsystem.LocalReducedMotion
import com.kursi.designsystem.TextureTokens

/**
 * AAA polish pass for the felt hero surface: subtle animated film grain + a warm bloom lift
 * around the lamp key-light pool, layered via [materialShader] on top of the existing
 * procedural felt texture (hatch/guilloché/vignette — see FeltTable.kt / Components.kt). Skips
 * cleanly (this [Modifier] unchanged) below Android API 33 or if the Skia RuntimeEffect
 * misbehaves on a given target — the procedural felt underneath already looks complete, so this
 * is a pure enhancement, never a hard dependency.
 *
 * Honors [LocalReducedMotion]: the grain freezes to a single still frame (no animation loop)
 * instead of disabling the whole pass, so reduced-motion users still get the richer/warmer felt.
 *
 * [bloomCenterYFraction] positions the bloom to match each surface's existing key-light pool —
 * 0.30f on the desktop `FeltTableSurface` (lamp hangs high), 0.46f (the default) on the phone
 * `FeltTableBackground`.
 */
@Composable
fun Modifier.feltMaterial(bloomCenterYFraction: Float = 0.46f): Modifier {
    val reducedMotion = LocalReducedMotion.current
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val infiniteTransition = rememberInfiniteTransition(label = "feltMaterialGrain")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "feltMaterialGrainTime",
    )
    val time = if (reducedMotion) 0f else animatedTime
    val width = sizePx.width.toFloat().coerceAtLeast(1f)
    val height = sizePx.height.toFloat().coerceAtLeast(1f)
    return this
        .onSizeChanged { sizePx = it }
        .materialShader(
            sksl = MaterialShaders.FELT_MATERIAL,
            uniforms =
                mapOf(
                    "resolution" to floatArrayOf(width, height),
                    "bloomCenter" to floatArrayOf(width * 0.5f, height * bloomCenterYFraction),
                    "grainIntensity" to floatArrayOf(TextureTokens.filmGrainIntensity),
                    "bloomStrength" to floatArrayOf(TextureTokens.warmBloomStrength),
                ),
            time = time,
        )
}
