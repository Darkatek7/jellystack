plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    buildMap {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.readLines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val delimiterIndex = line.indexOf('=')
                if (delimiterIndex <= 0) return@forEach
                put(line.substring(0, delimiterIndex).trim(), line.substring(delimiterIndex + 1).trim())
            }
        }
    }

val verifyTvReleaseManifestPermissions by tasks.registering {
    group = "verification"
    description = "Ensures the TV artifact only requests reviewed TV permissions."
    dependsOn("processReleaseManifest")
    val manifest = layout.buildDirectory.file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
    inputs.file(manifest)
    doLast {
        val permissions =
            Regex("""<uses-permission\b[^>]*android:name="([^"]+)"[^>]*>""")
                .findAll(manifest.get().asFile.readText())
                .map { it.groupValues[1] }
                .toSet()
        val allowed =
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "app.jellystack.mobile.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            )
        check((permissions - allowed).isEmpty()) {
            "TV manifest contains permissions outside the TV allowlist: ${permissions - allowed}"
        }
    }
}

android {
    namespace = "app.jellystack.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.jellystack.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "0.16.0-tv-beta.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            keystoreProperties["storeFile"]?.takeIf(String::isNotBlank)?.let { storeFile = rootProject.file(it) }
            keystoreProperties["storePassword"]?.takeIf(String::isNotBlank)?.let { storePassword = it }
            keystoreProperties["keyAlias"]?.takeIf(String::isNotBlank)?.let { keyAlias = it }
            keystoreProperties["keyPassword"]?.takeIf(String::isNotBlank)?.let { keyPassword = it }
        }
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.named("check").configure {
    dependsOn(verifyTvReleaseManifestPermissions)
}

dependencies {
    implementation(projects.sharedCore)
    implementation(projects.sharedNetwork)
    implementation(projects.sharedDatabase)
    implementation(projects.players)
    implementation(projects.designTv)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.sqldelight.android)
    implementation(libs.multiplatform.settings)
    implementation(libs.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.napier)
    implementation(libs.androidx.profileinstaller)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
