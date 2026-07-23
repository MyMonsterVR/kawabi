import java.util.Properties

// Repository implementations satisfying `domain`'s interfaces, backed by
// SQLDelight (local) and the kawabi-server API (remote). Android
// library (not pure JVM) because the SQLDelight driver needs an Android
// Context. Schema + repositories land in plan step 2; networking in step 3.
plugins {
    // AGP 9+ has Kotlin support built in -- no separate
    // org.jetbrains.kotlin.android plugin needed (or allowed).
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

// Not hardcoded so the app stays open-sourceable: anyone building it points
// at their own kawabi-server, not the maintainer's. Resolution order:
// local.properties `kawabi.baseUrl=` (gitignored, for local dev) -> the
// KAWABI_BASE_URL env var (CI secret) -> an obviously-fake fallback so a
// fresh clone fails loudly instead of silently talking to nothing.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { stream -> load(stream) }
}
val kawabiBaseUrl = (localProperties.getProperty("kawabi.baseUrl"))
    ?: System.getenv("KAWABI_BASE_URL")
    ?: "https://your-kawabi-server.example"

// Same resolution order as kawabiBaseUrl above: local.properties (gitignored,
// local dev) -> CI secret -> an obviously-fake placeholder so a fresh clone
// fails loudly instead of silently using someone else's tracker app. MAL
// requires its own registered app (myanimelist.net/apiconfig) with redirect
// `kawabi://myanimelist-auth`; set `kawabi.malClientId` locally or the
// KAWABI_MAL_CLIENT_ID CI secret. Kitsu has never implemented per-app
// registration -- its own API docs publish a single shared client_id/secret
// for all third-party apps to use, so set kawabi.kitsuClientId/
// kawabi.kitsuClientSecret to that documented pair (not a private credential
// of anyone's, but still not hardcoded here, same open-sourceability
// reasoning as kawabiBaseUrl).
val malClientId = (localProperties.getProperty("kawabi.malClientId"))
    ?: System.getenv("KAWABI_MAL_CLIENT_ID")
    ?: "REPLACE-MAL-CLIENT-ID"
val kitsuClientId = (localProperties.getProperty("kawabi.kitsuClientId"))
    ?: System.getenv("KAWABI_KITSU_CLIENT_ID")
    ?: "REPLACE-KITSU-CLIENT-ID"
val kitsuClientSecret = (localProperties.getProperty("kawabi.kitsuClientSecret"))
    ?: System.getenv("KAWABI_KITSU_CLIENT_SECRET")
    ?: "REPLACE-KITSU-CLIENT-SECRET"

android {
    namespace = "com.mymonstervr.kawabi.data"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        buildConfigField("String", "BASE_URL", "\"$kawabiBaseUrl\"")
        buildConfigField("String", "MAL_CLIENT_ID", "\"$malClientId\"")
        buildConfigField("String", "KITSU_CLIENT_ID", "\"$kitsuClientId\"")
        buildConfigField("String", "KITSU_CLIENT_SECRET", "\"$kitsuClientSecret\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

sqldelight {
    databases {
        create("KawabiDatabase") {
            packageName.set("com.mymonstervr.kawabi.data.db")
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))

    api(libs.sqldelight.android.driver)
    api(libs.sqldelight.coroutines.extensions)
    implementation(libs.koin.core)

    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
}
