import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose)
}

val verifyComposeResourceParity by tasks.registering {
    val english = file("src/commonMain/composeResources/values/strings.xml")
    val german = file("src/commonMain/composeResources/values-de/strings.xml")
    inputs.files(english, german)
    doLast {
        val keyPattern = Regex("""<(string|plurals|string-array)\s+name="([^"]+)"""")

        fun keys(file: File): Set<String> =
            keyPattern
                .findAll(file.readText())
                .map { match -> match.groupValues[1] + ":" + match.groupValues[2] }
                .toSet()

        val missingGerman = keys(english) - keys(german)
        val missingEnglish = keys(german) - keys(english)
        check(missingGerman.isEmpty() && missingEnglish.isEmpty()) {
            "Compose resource mismatch. Missing de=$missingGerman; missing en=$missingEnglish"
        }
    }
}

tasks
    .matching {
        it.name == "allTests" ||
            it.name == "check" ||
            it.name.endsWith("Test", ignoreCase = false)
    }.configureEach {
        dependsOn(verifyComposeResourceParity)
    }

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.sharedCore)
                implementation(projects.players)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons)
                implementation(libs.compose.components.resources)
                implementation(libs.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.koin.core)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
                implementation(libs.napier)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.mediarouter)
                implementation(libs.google.cast.framework)
                implementation(libs.androidx.biometric)
            }
        }
    }
}

android {
    namespace = "dev.jellystack.design"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        targetSdk = 36
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
}
