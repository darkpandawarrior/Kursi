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
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
