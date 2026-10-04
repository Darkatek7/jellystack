package dev.jellystack.core.profile

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveProfileRepositoryTest {
    @Test
    fun activeProfileSurvivesRecreationAndCanBeCleared() {
        val settings = InMemorySettings()
        val repository = ActiveProfileRepository(settings)

        repository.activate("profile-a")

        assertEquals("profile-a", ActiveProfileRepository(settings).profileId.value)
        repository.clear()
        assertNull(repository.profileId.value)
        assertNull(ActiveProfileRepository(settings).profileId.value)
    }
}
