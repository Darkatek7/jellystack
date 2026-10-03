pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Also determines the generated Compose resources package (jellystack_mobile.*); renaming it is a breaking change.
rootProject.name = "jellystack-mobile"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app-android",
    ":app-tv",
    ":app-ios",
    ":shared-core",
    ":shared-network",
    ":shared-database",
    ":players",
    ":players-cast-google",
    ":design",
    ":design-tv",
    ":design-screenshots",
    ":testing",
    ":tools",
)
