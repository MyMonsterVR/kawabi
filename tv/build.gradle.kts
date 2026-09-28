plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Same versionCode/commit-count scheme as :app -- see its build.gradle.kts for why. The TV
// app has no in-app updater (v1 is sideload-only, see PLAN Phase 8), but Android still
// requires a strictly-increasing versionCode for any future update path.
val gitCommitCount: Int = providers.exec {
    commandLine = "git rev-list --count HEAD".split(" ")
}.standardOutput.asText.get().trim().toInt()

android {
    namespace = "com.mymonstervr.kawabi.tv"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        // Separate applicationId from the phone app -- installs side by side, own Koin
        // graph/TokenStore, matches the plan's "own install" decision.
        applicationId = "com.mymonstervr.kawabi.tv"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = gitCommitCount
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            versionNameSuffix = "-$gitCommitCount"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":player-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tv.material)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp.core)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.zxing.core)
}
