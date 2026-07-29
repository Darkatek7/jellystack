import app.cash.sqldelight.gradle.VerifyMigrationTask
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.sqldelight)
    id("com.android.library")
}

configurations.maybeCreate("sqldelightMigrationClasspath")

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
                implementation(libs.coroutines.core)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android)
            }
        }
        val iosMain =
            maybeCreate("iosMain").apply {
                dependencies {
                    implementation(libs.sqldelight.native)
                }
            }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.sqldelight.driver.jvm)
            }
        }
    }
}

android {
    namespace = "dev.jellystack.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("sqldelightMigrationClasspath", libs.sqlite.jdbc)
}

sqldelight {
    databases {
        create("JellystackDatabase") {
            packageName.set("dev.jellystack.database")
            if (OperatingSystem.current().isWindows) {
                verifyMigrations.set(false)
            }
        }
    }
}

if (OperatingSystem.current().isWindows) {
    tasks.withType<VerifyMigrationTask>().configureEach {
        enabled = false
    }
}
