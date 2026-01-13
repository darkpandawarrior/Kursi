plugins {
    id("kursi.kmp.pure")
}

kotlin {
    android {
        namespace = "com.kursi.engine"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // NOTHING. Pure Kotlin stdlib only — no coroutines, datetime, serialization.
            // Determinism (seed + event log => byte-for-byte state) forbids any platform-dependent dep.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Purity tripwire: the engine must stay a dependency-free leaf. Fails the build if compose/ktor/
// serialization/coroutines ever creep onto its runtime classpath. (A dependency-guard baseline can
// replace this once that plugin is wired; for now this is plugin-free and explicit.)
val forbiddenEngineDeps = listOf(
    "org.jetbrains.compose", "androidx.compose", "io.ktor",
    "kotlinx-serialization", "kotlinx-coroutines", "kotlinx-datetime",
)
tasks.register("checkEnginePurity") {
    group = "verification"
    description = "Fails if :engine pulls in compose/ktor/serialization/coroutines/datetime."
    doLast {
        val cfg = configurations.findByName("jvmRuntimeClasspath") ?: return@doLast
        val deps = cfg.incoming.resolutionResult.allDependencies.map { it.requested.toString() }
        val violations = deps.filter { d -> forbiddenEngineDeps.any { d.contains(it) } }
        check(violations.isEmpty()) { "ENGINE PURITY VIOLATION: $violations" }
    }
}
tasks.named("check") { dependsOn("checkEnginePurity") }
