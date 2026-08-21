plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose)
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val verifyAndroidResourceParity by tasks.registering {
    val english = file("src/main/res/values/strings.xml")
    val german = file("src/main/res/values-de/strings.xml")
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
            "Android resource mismatch. Missing de=$missingGerman; missing en=$missingEnglish"
        }
    }
}

val verifyReleaseManifestPermissions by tasks.registering {
    group = "verification"
    description = "Fails when the release manifest requests an undeclared or over-broad Android permission."
    dependsOn("processReleaseManifest")

    val mergedManifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml",
        )
    inputs.file(mergedManifest)

    doLast {
        val manifest = mergedManifest.get().asFile.readText()
        val permissionTags =
            Regex("""<uses-permission\b[^>]*>""")
                .findAll(manifest)
                .map { it.value }
                .toList()
        val permissions =
            permissionTags
                .mapNotNull { tag ->
                    Regex("""android:name="([^"]+)"""").find(tag)?.groupValues?.get(1)
                }.toSet()
        val allowed =
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.NEARBY_WIFI_DEVICES",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.USE_BIOMETRIC",
                "android.permission.USE_FINGERPRINT",
                "app.jellystack.mobile.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            )
        val forbidden =
            setOf(
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
            )
        check((permissions intersect forbidden).isEmpty()) {
            "Release manifest contains forbidden permissions: ${permissions intersect forbidden}"
        }
        check((permissions - allowed).isEmpty()) {
            "Release manifest contains permissions outside the reviewed allowlist: ${permissions - allowed}"
        }

        listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ).forEach { permission ->
            val tag = permissionTags.singleOrNull { it.contains("""android:name="$permission"""") }
            check(tag?.contains("""android:maxSdkVersion="32"""") == true) {
                "$permission must be limited to API 32 and earlier."
            }
        }
        val nearbyTag =
            permissionTags.singleOrNull {
                it.contains("""android:name="android.permission.NEARBY_WIFI_DEVICES"""")
            }
        check(nearbyTag?.contains("""android:usesPermissionFlags="neverForLocation"""") == true) {
            "NEARBY_WIFI_DEVICES must use neverForLocation."
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyAndroidResourceParity)
    dependsOn(verifyReleaseManifestPermissions)
}

val keystoreProperties =
    run {
        val map = mutableMapOf<String, String>()
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.readLines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val delimiterIndex = line.indexOf('=')
                if (delimiterIndex < 0) return@forEach
                val key = line.substring(0, delimiterIndex).trim()
                val value = line.substring(delimiterIndex + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        map.toMap()
    }

android {
    namespace = "app.jellystack.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.jellystack.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 18
        versionName = "0.15.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            keystoreProperties["storeFile"]?.toString().takeUnless { it.isNullOrBlank() }?.let { storeFile = file(it) }
            keystoreProperties["storePassword"]?.toString()?.takeIf { it.isNotBlank() }?.let { storePassword = it }
            keystoreProperties["keyAlias"]?.toString()?.takeIf { it.isNotBlank() }?.let { keyAlias = it }
            keystoreProperties["keyPassword"]?.toString()?.takeIf { it.isNotBlank() }?.let { keyPassword = it }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.sharedCore)
    implementation(projects.sharedNetwork)
    implementation(projects.sharedDatabase)
    implementation(projects.players)
    implementation(projects.playersCastGoogle)
    implementation(projects.design)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.google.cast.framework)
    implementation(libs.androidx.media)
    implementation(libs.androidx.mediarouter)
    implementation(libs.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.compose.navigation)
    implementation(libs.sqldelight.android)
    implementation(libs.coroutines.android)
    implementation(libs.koin.compose)
    implementation(libs.napier)
    implementation(libs.multiplatform.settings)
    implementation(libs.koin.android)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.tracing)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.concurrent.futures)
    androidTestImplementation(libs.androidx.concurrent.futures.ktx)
    androidTestImplementation(libs.androidx.tracing)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
