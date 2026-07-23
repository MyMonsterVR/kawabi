// Cross-cutting utilities with no Android framework dependency (coroutine
// dispatcher wrappers, logging, small extension functions) -- depended on
// by every other module. Pure Kotlin/JVM so it stays trivially testable
// and reusable if a non-Android client is ever added.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.koin.core)
}
