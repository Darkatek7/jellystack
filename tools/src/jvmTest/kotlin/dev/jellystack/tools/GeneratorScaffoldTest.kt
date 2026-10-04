package dev.jellystack.tools

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class GeneratorScaffoldTest {
    @Test
    fun jellyfinGenerationPreservesModernLoginContract() =
        withGeneratedApis { outputDir ->
            val source = Files.readString(outputDir.resolve("jellyfin/JellyfinAuthApi.kt"))

            assertContains(source, "@SerialName(\"Pw\")")
            assertFalse(source.contains("legacyPasswordHash"))
            assertFalse(source.contains("@SerialName(\"Password\")"))
            assertContains(source, "private val deviceName: String = JELLYSTACK_CLIENT_NAME")
            assertContains(source, "private val clientVersion: String = \"unknown\"")
            assertContains(source, "appendJellyfinAuthorization(")
            assertContains(source, "appVersion = clientVersion")
            assertContains(source, "deviceName = deviceName")
            assertContains(source, "deviceId = payload.deviceId ?: \"unknown\"")
            assertContains(source, "token = null")
            assertFalse(source.contains("X-Emby"))
            assertContains(source, "}.consumeAsJson()")
            assertContains(source, "if (!status.isSuccess())")
            assertContains(source, "throw JellyfinAuthHttpException(status.value, bodyText)")
        }

    @Test
    fun otherProvidersKeepTheirApiKeyAuthentication() =
        withGeneratedApis { outputDir ->
            mapOf(
                "sonarr" to "SonarrSystemApi",
                "radarr" to "RadarrSystemApi",
                "jellyseerr" to "JellyseerrStatusApi",
            ).forEach { (provider, clientName) ->
                val source = Files.readString(outputDir.resolve("$provider/$clientName.kt"))
                assertContains(source, "private val apiKey: String?")
                assertContains(source, "header(\"X-Api-Key\", apiKey)")
                assertFalse(source.contains("JellyfinClientIdentity"))
                assertFalse(source.contains("JellyfinAuthHttpException"))
            }
        }

    private fun withGeneratedApis(check: (Path) -> Unit) {
        val projectRoot = Files.createTempDirectory("jellystack-api-generator-")
        try {
            main(arrayOf(projectRoot.toString()))
            check(projectRoot.resolve("shared-network/src/commonMain/kotlin/dev/jellystack/network/generated"))
        } finally {
            Files.walk(projectRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
