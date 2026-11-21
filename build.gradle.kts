// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

subprojects {
    configurations.configureEach {
        // F-Droid flags OpenCensus as a tracker when Gradle resolves Android test tooling.
        // AutoPie does not use it, so keep it out of all project dependency graphs.
        exclude(group = "io.opencensus")
        // Guava already provides ListenableFuture; AndroidX's standalone copy duplicates it.
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
}
