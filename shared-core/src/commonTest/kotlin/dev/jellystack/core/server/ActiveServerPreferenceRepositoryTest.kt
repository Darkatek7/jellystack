package dev.jellystack.core.server

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveServerPreferenceRepositoryTest {
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
