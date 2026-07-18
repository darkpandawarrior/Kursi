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
            // Consolidation #10: AiProvider/AiMessage/AiConfig/AiProviderConfig/buildProviderChain +
            // the real Anthropic/OpenAI/Gemini Ktor clients moved to toolkit :llm-chat. Only
            // IsmctsOnlyProvider (implements AiProvider) and OnDeviceAiProvider.* (consumes toolkit
            // :ai) stayed here — neither needs ktor directly anymore, so the ktor-client-* deps this
            // module used to declare for the (now-moved) provider impls are gone too.
            implementation("com.siddharth.kmp:llm-chat:1.0.0")
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.jvm routes through toolkit :ai's UnavailableOnDeviceLlm
            // instead of hand-rolling the same always-false stub.
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        androidMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.android routes through toolkit :ai's real
            // MlKitGenAiOnDeviceLlm/MediaPipeOnDeviceLlm backends (CompositeOnDeviceLlm chain).
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        iosMain.dependencies {
            // Consolidation #7: OnDeviceAiProvider.ios routes through toolkit :ai's
            // FoundationModelsOnDeviceLlm/MediaPipeOnDeviceLlm (both stubs pending a Swift bridge —
            // same behavior as before, now sourced from the shared toolkit instead of a local dupe).
            implementation("com.siddharth.kmp:ai:1.0.0")
        }
        wasmJsMain.dependencies {
            // ponytail: toolkit :ai has NO wasmJs target (jvm/iosArm64/iosSimulatorArm64/android only)
            // — can't consume it here. OnDeviceAiProvider.wasmJs stays a local stub. Upgrade path: add
            // a wasmJs target to toolkit :ai (UnavailableOnDeviceLlm-equivalent) if that ever changes.
            // (:llm-chat DOES have a wasmJs target now — consolidation #10 — so this source set picks
            // it up fine via the commonMain dependency above; only the on-device arm stays stubbed.)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
