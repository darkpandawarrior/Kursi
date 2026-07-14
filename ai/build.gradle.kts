plugins {
    id("kursi.kmp.pure")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.kursi.ai"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            // api, not implementation: SearchBudget/Policy from this module are part of :ai's own
            // public surface (KursiRules.Policy typealias, IsmctsSearch/MoveAdvisor budget params),
            // consumed transitively by :feature:game and :server.
            api("com.siddharth.kmp:bots-policy:1.0.0")
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Consolidation #7: OnDeviceAiProvider.jvm routes through toolkit :ai's UnavailableOnDeviceLlm
            // instead of hand-rolling the same always-false stub.
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Consolidation #7: OnDeviceAiProvider.android routes through toolkit :ai's real
            // MlKitGenAiOnDeviceLlm/MediaPipeOnDeviceLlm backends (CompositeOnDeviceLlm chain).
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // Consolidation #7: OnDeviceAiProvider.ios routes through toolkit :ai's
            // FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm (both stubs pending a Swift bridge —
            // same behavior as before, now sourced from the shared toolkit instead of a local dupe).
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            // ponytail: toolkit :ai has NO wasmJs target (jvm/iosArm64/iosSimulatorArm64/android only)
            // — can't consume it here. OnDeviceAiProvider.wasmJs stays a local stub. Upgrade path: add
            // a wasmJs target to toolkit :ai (UnavailableOnDeviceLlm-equivalent) if that ever changes.
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
