plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.screenshot) apply false
}

group = "dev.jellystack"
version = "0.0.1-SNAPSHOT"

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("config/detekt/detekt.yml").filter { it.exists() })
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.register("printProjectStructure") {
    group = "help"
    description = "Prints included Gradle projects."
    doLast {
        println("Projects: ")
        rootProject.subprojects.sortedBy { it.path }.forEach { println(" - ${it.path}") }
    }
}

tasks.register("generateThirdPartyReport") {
    group = "documentation"
    description = "Generates a reviewed report of the major declared third-party dependencies and licenses."
    val output = layout.buildDirectory.file("reports/third-party/dependencies.md")
    outputs.file(output)
    doLast {
        val catalog =
            project.extensions
                .getByType<org.gradle.api.artifacts.VersionCatalogsExtension>()
                .named("libs")

        fun version(alias: String): String = catalog.findVersion(alias).get().requiredVersion

        val dependencies =
            listOf(
                Triple("Kotlin", version("kotlin"), "Apache-2.0"),
                Triple("Compose Multiplatform", version("compose"), "Apache-2.0"),
                Triple("Ktor", version("ktor"), "Apache-2.0"),
                Triple("Kotlin Coroutines", version("coroutines"), "Apache-2.0"),
                Triple("Kotlin Serialization", version("serialization"), "Apache-2.0"),
                Triple("SQLDelight", version("sqldelight"), "Apache-2.0"),
                Triple("Koin", version("koin"), "Apache-2.0"),
                Triple("Coil", version("coil3"), "Apache-2.0"),
                Triple("Napier", version("napier"), "Apache-2.0"),
                Triple("Multiplatform Settings", version("multiplatform-settings"), "Apache-2.0"),
                Triple("Voyager", version("voyager"), "MIT"),
                Triple("AndroidX Media3", version("media3"), "Apache-2.0"),
                Triple("Google Cast SDK", version("play-services-cast"), "Google APIs Terms"),
            )
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("# Generated third-party dependency report")
                    appendLine()
                    appendLine("Generated from `gradle/libs.versions.toml`. Verify upstream license files before release.")
                    appendLine()
                    appendLine("| Component | Version | License |")
                    appendLine("| --- | --- | --- |")
                    dependencies.forEach { (name, version, license) ->
                        appendLine("| $name | $version | $license |")
                    }
                },
            )
        }
    }
}
