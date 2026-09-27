// Anime playback business logic: the ExoPlayer wrapper plus everything around it (stream/
// hoster selection incl. sub/dub + quality preference matching, proxy fallback, bounded
// transient-error retry, resume position, progress persistence, skip-intro/outro via
// AniSkip-or-source timestamps, auto-advance-to-next-episode, subtitle track selection).
// Shared between the phone app and the TV app -- deliberately no Compose or media3-ui
// dependency here; each app builds its own UI (touch vs D-pad) on top of the same
// PlayerViewModel.
plugins {
    // AGP 9+ has Kotlin support built in -- no separate
    // org.jetbrains.kotlin.android plugin needed (or allowed).
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.mymonstervr.kawabi.player"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.koin.android)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
}
