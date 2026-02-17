package com.kursi.ai

/**
 * Wasm/browser disables the heavy ISMCTS sims: each multi-hundred-game suite runs synchronously and
 * starves Karma's 2000 ms ping, killing the browser. Gated tests early-return as a no-op skip so the
 * wasm target still discovers and runs the lighter functional AI tests.
 */
actual fun heavyAiSimsEnabled(): Boolean = false
