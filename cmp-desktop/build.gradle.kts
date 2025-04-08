import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project(":cmp-shared"))
            implementation(project(":feature:game"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:prefs"))
            implementation(project(":core:network"))
            implementation(project(":engine"))
            implementation(project(":ai"))
        }
    }
}

// ── Headless screenshot task ──────────────────────────────────────────────────
tasks.register<JavaExec>("renderScreens") {
    group = "kursi"
    description = "Render GameScreen fixture states to PNG files in build/shots/"
    mainClass.set("com.kursi.desktop.ScreenshotsKt")
    // KMP project: use the jvmRuntimeClasspath configuration for the JVM target
    classpath = configurations["jvmRuntimeClasspath"] +
        kotlin.targets.named("jvm").get().compilations.named("main").get()
            .output.allOutputs
    val shotsDir = layout.buildDirectory.dir("shots").get().asFile
    systemProperty("kursi.shots.dir", shotsDir.absolutePath)
    doFirst { shotsDir.mkdirs() }
    dependsOn("jvmMainClasses")
}

compose.desktop {
    application {
        mainClass = "com.kursi.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
        }
    }
}
