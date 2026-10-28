plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Producer module of the TV app's baseline profile; it also holds the real-app macrobenchmarks that compare
// startup and frame timing with and without that profile. The synthetic-fixture benchmark stays in :tv-benchmark.
android {
    namespace = "dev.jellystack.tv.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Profile collection needs API 28+; the tested app itself supports API 24.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    targetProjectPath = ":app-tv"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.uiautomator)
}
