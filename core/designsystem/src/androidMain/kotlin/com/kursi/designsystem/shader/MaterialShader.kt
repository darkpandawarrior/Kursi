package com.kursi.designsystem.shader

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

// Compiled RuntimeShader is cached by source string — the RuntimeShader(sksl) constructor
// parses+compiles AGSL and this block re-runs every frame (it captures the animated `time`
// uniform), so without caching every frame paid a full shader recompile.
private val effectCache = mutableMapOf<String, RuntimeShader>()

/**
 * AGSL via [android.graphics.RuntimeShader], applied as a [RenderEffect] on the layer.
 * `RuntimeShader` requires API 33 (Tiramisu) — below that this is a no-op. A shader that fails
 * to compile (or any other runtime hiccup) is swallowed via [runCatching] so a bad shader never
 * crashes the layer; it just renders without the effect.
 */
actual fun Modifier.materialShader(
    sksl: String,
    uniforms: Map<String, FloatArray>,
    time: Float,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return graphicsLayer {
        renderEffect =
            runCatching {
                val shader = effectCache.getOrPut(sksl) { RuntimeShader(sksl) }
                shader.setFloatUniform("time", time)
                for ((name, value) in uniforms) shader.setFloatUniform(name, value)
                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }.getOrNull()
    }
}
