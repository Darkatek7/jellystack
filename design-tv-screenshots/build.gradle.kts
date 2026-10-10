plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

// Screenshot tests for the production Compose for TV components of design-tv. Kept apart from
// design-screenshots, which pins Compose 1.7 for the phone UI.
android {
    namespace = "dev.jellystack.design.tv.screenshots"
    compileSdk = 36

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.designTv)
    implementation(projects.sharedCore)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
