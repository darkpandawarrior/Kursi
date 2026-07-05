// Root build. Plugin declarations with apply=false put the plugin artifact on every
// subproject's buildscript classpath so convention plugins can apply them programmatically.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// ── Quality gates — applied to every Kotlin subproject ────────────────────────
subprojects {
    // ktlint: code style enforcement (line length, imports, formatting)
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(false)      // KMP project — don't inject android-specific rules
        outputToConsole.set(true)
        filter {
            exclude("**/build/**/*.kt")
            exclude("**/generated/**/*.kt")
        }
    }

    // detekt: static analysis
    apply(plugin = "dev.detekt")
    configure<dev.detekt.gradle.extensions.DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        // detekt 2.x's default ruleset + renamed rules surfaced pre-existing findings that
        // 1.23.x's config didn't catch; baseline grandfathers those in, new code is still gated.
        baseline = file("detekt-baseline.xml")
        // Only scan hand-authored source; skip generated + build output
        source.setFrom(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/jvmMain/kotlin",
            "src/iosMain/kotlin",
            "src/wasmJsMain/kotlin",
        )
    }
}
