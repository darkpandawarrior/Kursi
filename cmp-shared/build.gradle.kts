@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()

        // CMP 1.12.0-rc01 promoted checkComposeUiTestConfigurationForWasmJs to a hard build
        // failure (CMP-4906): Compose UI tests can't load the Skiko runtime from a bare klib,
        // so the wasmJs target must declare a real webpack-bundled executable.
        binaries.executable()
    }

    android {
        namespace = "com.kursi.shared"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(project(":feature:game"))
            implementation(project(":core:designsystem"))
            implementation("com.siddharth.kmp:feedback:1.0.0")
            implementation(project(":core:prefs"))
            implementation(project(":core:network"))
            implementation(project(":engine"))
            implementation(project(":ai"))
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
