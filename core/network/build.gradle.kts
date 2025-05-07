plugins {
    id("kursi.kmp.pure")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "com.kursi.core.network"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
            implementation(project(":shared-protocol"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        // JVM: OkHttp engine (also used as the host-test engine for androidHostTest)
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        // Android: OkHttp engine
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        // iOS: Darwin (NSURLSession-backed) engine
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        // wasmJs: JS engine (ktor-client-js supports wasmJs in Ktor 3.x)
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
