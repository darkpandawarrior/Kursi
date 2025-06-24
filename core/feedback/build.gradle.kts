@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    android {
        namespace = "com.kursi.core.feedback"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
