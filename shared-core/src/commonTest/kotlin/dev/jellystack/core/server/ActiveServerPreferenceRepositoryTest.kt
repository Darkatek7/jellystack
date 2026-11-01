package dev.jellystack.core.server

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveServerPreferenceRepositoryTest {
    @Test
    fun providerSelectionSurvivesRestartWithoutOverwritingSeerrConnection() {
        val settings = InMemorySettings()
        val repository = ActiveServerPreferenceRepository(settings)
        repository.setActiveServerId(ServerType.JELLYSEERR, "seerr-a")
        repository.setActiveServerId(ServerType.MEDIARIUM, "mediarium-a")
        repository.selectRequestProvider(ServerType.MEDIARIUM)
        val restored = ActiveServerPreferenceRepository(settings)
        assertEquals(ServerType.MEDIARIUM, restored.requestProviderType())
        assertEquals("seerr-a", restored.activeServerId(ServerType.JELLYSEERR))
        restored.selectRequestProvider(ServerType.JELLYSEERR)
        assertEquals("mediarium-a", restored.activeServerId(ServerType.MEDIARIUM))
    }

    @Test
    fun storesSelectionsIndependentlyByService() {
        val repository = ActiveServerPreferenceRepository(InMemorySettings())

        repository.setActiveServerId(ServerType.JELLYFIN, "jellyfin-a")
        repository.setActiveServerId(ServerType.JELLYSEERR, "seerr-a")

        assertEquals("jellyfin-a", repository.activeServerId(ServerType.JELLYFIN))
        assertEquals("seerr-a", repository.activeServerId(ServerType.JELLYSEERR))
        assertNull(repository.activeServerId(ServerType.RADARR))
    }

    @Test
    fun nullSelectionClearsStoredId() {
        val repository = ActiveServerPreferenceRepository(InMemorySettings())
        repository.setActiveServerId(ServerType.JELLYFIN, "jellyfin-a")

        repository.setActiveServerId(ServerType.JELLYFIN, null)

        assertNull(repository.activeServerId(ServerType.JELLYFIN))
    }
}
