@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS (not FAIL_ON_PROJECT_REPOS): the Kotlin/Wasm Node.js toolchain setup plugin
    // adds a nodejs distribution repo programmatically at project-level. FAIL_ON_PROJECT_REPOS
    // rejects that and breaks :engine:build when wasmJs { nodejs() } is declared.
    // PREFER_SETTINGS still uses settings repos first; project-level repos only used as fallback.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
        // Node.js distribution for Kotlin/Wasm nodejs() target: resolves org.nodejs:node artifacts.
        ivy {
            name = "Node.js Distributions"
            url = uri("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Yarn distribution for Kotlin/Wasm browser() target: resolves com.yarnpkg:yarn artifacts.
        ivy {
            name = "Yarn Distributions"
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        // Binaryen (Wasm optimizer) for Kotlin/Wasm browser() production builds.
        // kotlinWasmBinaryenSetup resolves com.github.webassembly:binaryen:<version>.
        ivy {
            name = "Binaryen Distributions"
            url = uri("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout { artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "Kursi"

// Shared MVI primitives (EffectEmitter) — vendored as a submodule, wired via composite build so
// `com.siddharth.kmp:mvi-core:1.0.0` resolves to the local checkout instead of a remote repo.
// Explicit substitution needed: the KMP root publication is renamed to "mvi-core" only at publish
// time (see external/kmp-mvi-core/lib/build.gradle.kts) — the subproject itself is still `:lib`,
// which Gradle would otherwise auto-substitute as `com.siddharth.kmp:lib`, not `:mvi-core`.
includeBuild("external/kmp-mvi-core") {
    dependencySubstitution {
        substitute(module("com.siddharth.kmp:mvi-core")).using(project(":lib"))
    }
}

// Multiplatform sound/haptics/share-result primitives — vendored as a submodule, wired via
// composite build so `com.siddharth.kmp:feedback:1.0.0` resolves to the local checkout instead
// of a remote repo. Subproject is `:feedbacklib` (not `:lib`, unlike kmp-mvi-core) — two included
// builds both naming their subproject `:lib` under the same `com.siddharth.kmp` group made Gradle's
// composite dependency substitution ambiguous (mvi-core kept resolving to kmp-feedback's project).
includeBuild("external/kmp-feedback") {
    dependencySubstitution {
        substitute(module("com.siddharth.kmp:feedback")).using(project(":feedbacklib"))
    }
}

// ── M0: PURE DOMAIN CORE (JVM target only until an Android SDK + apps land in M1/M2) ──
include(":engine")            // PURE deterministic reducer. LEAF — depends on nothing.
include(":ai")                // Bot policies (Easy, Medium). Depends on :engine only.

// ── PROTOCOL (T8: serialization wire types + envelopes + mappers; multi-target) ──────
include(":shared-protocol")   // @Serializable mirrors of PlayerView/Intent; ClientMessage/ServerMessage envelopes. Depends on :engine only.

// ── CORE (T3: first Compose Multiplatform module — JVM/desktop target only at M0) ──
include(":core:designsystem") // CMP theme engine, color tokens, reusable composables (CardFace, CoinPill, etc.)

// ── CORE (T10: multiplatform Ktor WebSocket client — android/ios/jvm/wasmJs) ─────
include(":core:network")      // KursiClient + OnlineGameSession: WS connect, encode/decode, reconnect.
include(":core:prefs")        // Multiplatform key-value preferences (AppPrefs) backed by multiplatform-settings.
// core:feedback extracted to external/kmp-feedback (composite build above) — expect/actual
// SoundPlayer + haptics (jvm tone synth, android AudioTrack/Vibrator, ios AVAudioPlayer, wasm Web Audio).

// ── FEATURES (T4: offline game-driving layer + MVI ViewModel + table composables) ──
include(":feature:game")      // Offline game session, MVI GameViewModel, GameScreen composables.

// ── APP ENTRYPOINTS (T5: shared Compose root + JVM desktop shell) ──────────────
include(":cmp-shared")        // Shared KursiApp() root composable; wraps KursiTheme + GameViewModel + GameScreen.
include(":cmp-desktop")       // JVM Compose Desktop application shell; calls KursiApp().

// ── APP ENTRYPOINTS (T12: Android application shell) ─────────────────────────
include(":cmp-android")       // Android application shell; hosts KursiApp() via ComponentActivity.setContent.

// ── APP ENTRYPOINTS (T13: iOS framework umbrella) ────────────────────────────
include(":cmp-ios")           // iOS umbrella framework; export()s :cmp-shared so an Xcode/SwiftUI app can host KursiKit.framework.

// ── APP ENTRYPOINTS (T11: wasmJs browser client) ─────────────────────────────
include(":cmp-web")           // wasmJs browser shell; calls ComposeViewport(document.body!!) { KursiApp() }.

// ── SERVER (T9: JVM Ktor authoritative game server) ───────────────────────────
include(":server")            // Ktor/Netty WebSocket server; authoritative GameState; Channel-actor per match.
