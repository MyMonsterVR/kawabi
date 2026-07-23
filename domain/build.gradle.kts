// Business logic: domain models, repository interfaces, interactors.
// Pure Kotlin/JVM, no Android framework and no SQLDelight/network
// dependency -- it only knows about repository interfaces, which `data`
// implements. See kawabi_app/docs/architecture-overview.md #1.
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
