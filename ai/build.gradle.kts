plugins {
    id("kursi.kmp.pure")
}

kotlin {
    android {
        namespace = "com.kursi.ai"
        compileSdk = 37
        minSdk = 24
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":engine"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
