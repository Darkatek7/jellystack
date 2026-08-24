package dev.jellystack.design.tv

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvProfilePresentationTest {
    @Test
    fun rememberedProfileReceivesInitialFocusWhenItStillExists() {
        val profiles = listOf(profile("alice"), profile("bob"))

        assertEquals("bob", selectInitialTvProfileId(profiles, rememberedProfileId = "bob"))
    }

    @Test
    fun missingRememberedProfileFallsBackToFirstStableProfile() {
        val profiles = listOf(profile("alice"), profile("bob"))

        assertEquals("alice", selectInitialTvProfileId(profiles, rememberedProfileId = "removed"))
        assertNull(selectInitialTvProfileId(emptyList(), rememberedProfileId = "removed"))
    }

    private fun profile(id: String) =
        TvProfilePresentation(
            id = id,
            displayName = id.replaceFirstChar(Char::uppercase),
            avatarUrl = null,
            pinRequired = false,
            lastActiveAt = Instant.fromEpochMilliseconds(0),
        )
}
