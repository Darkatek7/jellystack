import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
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
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
                implementation(libs.napier)
                api(libs.kotlinx.datetime)
                implementation(projects.sharedNetwork)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.http)
                implementation(libs.koin.core)
                implementation(libs.multiplatform.settings)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.androidx.security.crypto)
                implementation(libs.koin.android)
                implementation(libs.koin.compose)
            }
        }
    }
}

android {
    namespace = "dev.jellystack.core"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val bundleJarDeleteAttempts = 120
val bundleJarDeleteDelayMillis = 1_000L

tasks.matching { it.name.startsWith("bundleLibCompileToJar") }.configureEach {
    doFirst("jellystackClearLockedJar") {
        outputs.files.forEach { outputFile ->
            if (!outputFile.exists() || outputFile.extension != "jar") {
                return@forEach
            }

            repeat(bundleJarDeleteAttempts) { attempt ->
                try {
                    FileChannel
                        .open(
                            outputFile.toPath(),
                            StandardOpenOption.WRITE,
                            StandardOpenOption.DELETE_ON_CLOSE,
                        ).use { }

                    if (Files.exists(outputFile.toPath())) {
                        Files.deleteIfExists(outputFile.toPath())
                    }

                    return@forEach
                } catch (error: Exception) {
                    if (!outputFile.exists()) {
                        return@forEach
                    }

                    if (error is InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw error
                    }

                    if (attempt == bundleJarDeleteAttempts - 1) {
                        throw GradleException(
                            "Unable to remove locked jar ${outputFile.absolutePath}",
                            error,
                        )
                    }

                    logger.info(
                        "Retrying removal of ${outputFile.absolutePath} after attempt ${attempt + 1}",
                    )

                    Thread.sleep(bundleJarDeleteDelayMillis)
                }
            }
        }
    }
}
