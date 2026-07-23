// Root build file. Declares plugins used by submodules without applying
// them here (apply false) -- each module opts in to what it actually needs.
plugins {
    // AGP 9+ bundles Kotlin support for Android modules directly -- there
    // is no separate org.jetbrains.kotlin.android plugin in this project.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
}
