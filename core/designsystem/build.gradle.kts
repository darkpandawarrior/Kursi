@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    android {
        namespace = "com.kursi.designsystem"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            // material-icons-extended: AccountBalance, LocalFireDepartment, Work, SwapHoriz, Gavel
            // for role glyphs — all targets supported by CMP 1.11.x
            implementation(libs.material.icons.extended)
            // compose.components.resources: enables Res.font.* for bundled TTF files
            // under src/commonMain/composeResources/font/ (Rozha One, Marcellus, DM Mono)
            implementation(libs.components.resources)
            implementation(project(":engine"))
            implementation(project(":core:feedback"))
        }
    }
}
