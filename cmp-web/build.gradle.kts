plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.ui)
            implementation(libs.material3)
            implementation(project(":cmp-shared"))
            implementation(project(":feature:game"))
            implementation(project(":core:designsystem"))
        }
    }
}
