import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

val jvmTarget = kotlin.targets.getByName("jvm") as KotlinJvmTarget
val jvmCompilation = jvmTarget.compilations.getByName("main")

tasks.register<JavaExec>("generateApis") {
    group = "codegen"
    description = "Generates API clients from curated OpenAPI-inspired specs."
    dependsOn(jvmCompilation.compileTaskProvider)
    mainClass.set("dev.jellystack.tools.GeneratorScaffoldKt")
    classpath =
        files(
            jvmCompilation.output.allOutputs,
            jvmCompilation.runtimeDependencyFiles,
        )
    args(rootProject.rootDir.absolutePath)
}
