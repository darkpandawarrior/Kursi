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
    wasmJs { browser() }

    android {
        namespace = "com.kursi.shared"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(project(":feature:game"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:prefs"))
            implementation(project(":core:network"))
            implementation(project(":engine"))
            implementation(project(":ai"))
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
