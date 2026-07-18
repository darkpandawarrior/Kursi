package com.kursi.designsystem.shader

/**
 * Shared AGSL/SkSL shader sources. The exact same string compiles as AGSL on Android's
 * `android.graphics.RuntimeShader` (API 33+) and as SkSL on Skia's
 * `org.jetbrains.skia.RuntimeEffect` (desktop/iOS/wasm) — see docs/resources.md (ShaderX).
 */
internal object MaterialShaders {
    /**
     * Felt material pass: animated per-pixel film grain (very low amplitude) + a warm bloom
     * lift around the lamp key-light pool. The felt's hatch/guilloché/vignette are already
     * drawn procedurally (Canvas) underneath — see FeltTable.kt / Components.kt — so this
     * shader only adds what per-pixel noise and additive glow can do that a static gradient
     * can't. It must read as "richer felt", not a filter-demo overlay.
     */
    val FELT_MATERIAL =
        """
        uniform shader content;
        uniform float time;
        uniform float2 resolution;
        uniform float2 bloomCenter;
        uniform float grainIntensity;
        uniform float bloomStrength;

        float grainHash(float2 p) {
            float3 p3 = fract(p.xyx * float3(0.1031, 0.1030, 0.0973));
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);

            // Animated per-pixel grain, centred on zero so it can lighten or darken a texel.
            float grain = grainHash(fragCoord + time * 71.0) - 0.5;
            color.rgb += grain * grainIntensity;

            // Warm additive bloom around the lamp key-light pool.
            float2 uv = fragCoord / resolution;
            float2 c = bloomCenter / resolution;
            float dist = distance(uv, c);
            float bloom = (1.0 - smoothstep(0.0, 0.5, dist)) * bloomStrength;
            half3 warm = half3(1.0, 0.85, 0.6);
            color.rgb += warm * bloom;

            return color;
        }
        """.trimIndent()
}
