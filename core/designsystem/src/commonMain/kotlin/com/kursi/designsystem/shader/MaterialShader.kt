package com.kursi.designsystem.shader

import androidx.compose.ui.Modifier

/**
 * Applies an AGSL/SkSL runtime-shader effect to this composable's own rendered pixels.
 *
 * [sksl] is the SAME source string compiled as AGSL by [android.graphics.RuntimeShader] on
 * Android (API 33+) and as SkSL by `org.jetbrains.skia.RuntimeEffect` on desktop/iOS/wasm — see
 * docs/resources.md (ShaderX pattern). It must declare `uniform shader content;` and sample it
 * via `content.eval(coord)`; the platform binds this element's own rendered content to that
 * uniform automatically (no need to pass it via [uniforms]).
 *
 * [uniforms] sets additional scalar/vector uniforms — a 1..4-length [FloatArray] maps to
 * `float`/`float2`/`float3`/`float4`. [time] is wired unconditionally as `uniform float time;`,
 * so every [sksl] source passed here must declare it (even if unused).
 *
 * No-op (returns [this] unchanged) below Android API 33, or if the shader fails to compile /
 * RuntimeEffect misbehaves on a given target — callers should treat this as a pure visual
 * enhancement with a working fallback already in place, never a hard dependency.
 */
expect fun Modifier.materialShader(
    sksl: String,
    uniforms: Map<String, FloatArray> = emptyMap(),
    time: Float = 0f,
): Modifier
