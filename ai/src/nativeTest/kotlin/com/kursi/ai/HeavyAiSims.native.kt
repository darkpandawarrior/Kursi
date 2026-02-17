package com.kursi.ai

/** Native (iOS) test executables run the heavy sims synchronously without a browser ping budget. */
actual fun heavyAiSimsEnabled(): Boolean = true
