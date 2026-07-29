package dev.jellystack.design.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JellystackPreviewFixtureTest {
    @Test
    fun acceptedFixtureNamesAreStable() {
        assertEquals(
            setOf("home", "library", "discover", "requests", "settings", "onboarding", "detail"),
            JellystackPreviewData.acceptedNames,
        )
    }

    @Test
    fun unknownFixtureNameFailsFast() {
        assertFailsWith<IllegalArgumentException> {
            JellystackPreviewData.scenario("unknown")
        }
    }
}
