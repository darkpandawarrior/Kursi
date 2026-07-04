import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// ── Single-source versioning ──────────────────────────────────────────────────
// VERSION (semver) + BUILD_NUMBER at the repo root are the ONE place versions
// change. Bump with scripts/bump_version.sh --patch|--minor|--major.
// versionCode = 1 + BUILD_NUMBER so the first release ships as versionCode 1.
val versionCodeBase = 1

fun readVersionName(): String =
    rootProject.file("VERSION").takeIf { it.exists() }
        ?.readText()?.trim()?.ifEmpty { "1.0.0" } ?: "1.0.0"

fun readBuildNumber(): Int =
    rootProject.file("BUILD_NUMBER").takeIf { it.exists() }
        ?.readText()?.trim()?.toIntOrNull() ?: 0

// ── Release signing ────────────────────────────────────────────────────────────
// Reads from keystore.properties (copy keystore.properties.template and fill in).
// Falls back to RELEASE_* environment variables for CI.
// Falls back to debug signing if neither is present so `assembleRelease` still
// works locally and in CI without secrets configured.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseSigning =
    keystorePropertiesFile.exists() || System.getenv("RELEASE_STORE_FILE") != null

android {
    namespace = "com.kursi.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kursi.android"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeBase + readBuildNumber()
        versionName = readVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(
                    keystoreProperties.getProperty("storeFile")
                        ?: System.getenv("RELEASE_STORE_FILE")
                )
                storePassword =
                    keystoreProperties.getProperty("storePassword")
                        ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias =
                    keystoreProperties.getProperty("keyAlias")
                        ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword =
                    keystoreProperties.getProperty("keyPassword")
                        ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        // QA/staging: minified but debug-signed; installs alongside the debug build.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            versionNameSuffix = "-staging"
            matchingFallbacks += "release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":cmp-shared"))
    implementation(project(":feature:game"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:feedback"))
    implementation(project(":core:prefs"))

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
}
