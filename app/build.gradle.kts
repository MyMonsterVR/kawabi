plugins {
    // AGP 9+ has Kotlin support built in -- no separate
    // org.jetbrains.kotlin.android plugin needed (or allowed).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// versionCode must strictly increase for Android to accept an update -- commit
// count (not e.g. a CI run number) matches what the update manifest's
// commit_count field compares against, see AppUpdateChecker. Computed once,
// not per-usage -- each call shells out to git.
val gitCommitCount: Int = providers.exec {
    commandLine = "git rev-list --count HEAD".split(" ")
}.standardOutput.asText.get().trim().toInt()

android {
    namespace = "com.mymonstervr.kawabi"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.mymonstervr.kawabi"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = gitCommitCount
        versionName = "0.1.0"
        buildConfigField("int", "COMMIT_COUNT", "$gitCommitCount")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Reflected in the update manifest's "version" field CI generates
            // (build.yml) -- keeps the in-app "About" version and the manifest's
            // human-readable version in sync without hand-editing both.
            versionNameSuffix = "-$gitCommitCount"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

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

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp.core)
}
