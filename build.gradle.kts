plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

subprojects {
    plugins.withId("com.android.base") {
        configure<com.android.build.gradle.BaseExtension> {
            testOptions {
                unitTests.isReturnDefaultValues = true
            }
        }
    }
}
