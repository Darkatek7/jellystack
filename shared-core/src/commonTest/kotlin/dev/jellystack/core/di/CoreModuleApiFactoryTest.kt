package dev.jellystack.core.di

import dev.jellystack.core.jellyfin.HomeSectionsApiFactory
import dev.jellystack.core.jellyfin.JellyfinBrowseApiFactory
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinSessionApiFactory
import dev.jellystack.network.jellyfin.HomeSectionsApi
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import dev.jellystack.network.jellyfin.JellyfinSessionApi
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertIs

class CoreModuleApiFactoryTest {
    @Test
    fun `api factories resolve to their declared api types`() {
        val koin = koinApplication { modules(coreModule()) }.koin
        val environment =
            JellyfinEnvironment(
                serverKey = "test-server",
                baseUrl = "https://media.example",
                accessToken = "dummy-token",
                userId = "dummy-user",
                deviceId = "dummy-device",
                deviceName = "Test device",
            )

        assertIs<JellyfinBrowseApi>(
            koin.get<JellyfinBrowseApiFactory>(jellyfinBrowseApiFactoryQualifier)(environment),
        )
        assertIs<HomeSectionsApi>(
            koin.get<HomeSectionsApiFactory>(homeSectionsApiFactoryQualifier)(environment),
        )
        assertIs<JellyfinSessionApi>(
            koin.get<JellyfinSessionApiFactory>(jellyfinSessionApiFactoryQualifier)(environment),
        )
    }
}
