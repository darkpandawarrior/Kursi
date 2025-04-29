plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("com.kursi.server.AppKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":shared-protocol"))
    implementation(project(":ai"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.kotlinx.coroutines.core)

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.testHost)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    // M7: drive the REAL :core:network client against a real embedded Netty server (round-trip test).
    testImplementation(project(":core:network"))
    testImplementation(libs.ktor.client.okhttp)
}
