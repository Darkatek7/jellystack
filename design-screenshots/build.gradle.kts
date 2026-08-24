plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

private val screenshotComposeGroups =
    setOf(
        "androidx.compose.animation",
        "androidx.compose.foundation",
        "androidx.compose.material",
        "androidx.compose.ui",
    )

configurations.configureEach {
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        resolutionStrategy.eachDependency {
            if (requested.group in screenshotComposeGroups) {
                useVersion("1.7.1")
                because("Compose Preview Screenshot alpha15 renders against the Compose 1.7 ABI")
            }
            if (requested.group == "androidx.activity" && requested.name == "activity-compose") {
                useVersion("1.9.3")
                because("Preview Screenshot alpha15 cannot host the Navigation Event back-handler runtime")
            }
        }
    }
}

android {
    namespace = "dev.jellystack.design.screenshots"
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
    implementation(projects.design)
    implementation(projects.designTv)
    implementation(projects.players)
    implementation(projects.sharedCore)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.compose.foundation)
    screenshotTestImplementation(libs.compose.material3)
}
