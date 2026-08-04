plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    // Declared apply false to pin the KGP version AGP 9's built-in Kotlin uses.
    // Must match the compose compiler plugin version in libs.versions.toml.
    alias(libs.plugins.kotlin.android) apply false
}
