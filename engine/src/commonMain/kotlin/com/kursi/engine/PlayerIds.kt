package com.kursi.engine

/**
 * Type-safe wrapper for a player seat index.
 *
 * KMP expect/actual: JVM and Android targets require @JvmInline, while native/wasmJs
 * targets forbid it (the annotation lives in kotlin.jvm which is JVM-only).
 * The actual declarations live in jvmMain, androidMain, nativeMain, and wasmJsMain.
 */
expect value class PlayerId(
    val raw: Int,
)

/**
 * Type-safe wrapper for a card identity.
 *
 * Same JVM vs non-JVM split as [PlayerId].
 */
expect value class CardId(
    val raw: Int,
)
