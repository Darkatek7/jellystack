package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SideloadedSubtitleSupportTest {
    @Test
    fun trackIdsMatchBothPlainAndMergePrefixedFormatIds() {
        assertTrue(formatIdMatches("5", "5"))
        assertTrue(formatIdMatches("1:5", "5"))
        assertTrue(formatIdMatches("0:5", "5"))
        assertTrue(formatIdMatches("12:Deutsch", "deutsch"))
    }

    @Test
    fun otherTrackIdsDoNotMatchEvenWhenTheyShareAPrefix() {
        assertFalse(formatIdMatches("1:15", "5"))
        assertFalse(formatIdMatches("1:5", "1"))
        assertFalse(formatIdMatches(null, "5"))
        assertFalse(formatIdMatches("1:5", null))
    }

    @Test
    fun mergedSourceIndexIsOnlyReportedForMergePrefixedIds() {
        assertEquals(2, mergedSourceIndex("2:7"))
        assertEquals(0, mergedSourceIndex("0:"))
        assertNull(mergedSourceIndex("7"))
        assertNull(mergedSourceIndex(null))
    }
}
