package dev.jellystack.design.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveProfileTest {
    @Test
    fun classifiesCompactWidthBelowSixHundredDp() {
        val profile = responsiveProfile(widthDp = 599f, heightDp = 800f)

        assertEquals(JellystackWidthClass.Compact, profile.widthClass)
        assertFalse(profile.isShortHeight)
    }

    @Test
    fun classifiesMediumWidthFromSixHundredDp() {
        val profile = responsiveProfile(widthDp = 600f, heightDp = 800f)

        assertEquals(JellystackWidthClass.Medium, profile.widthClass)
    }

    @Test
    fun classifiesExpandedWidthFromEightHundredFortyDp() {
        val profile = responsiveProfile(widthDp = 840f, heightDp = 800f)

        assertEquals(JellystackWidthClass.Expanded, profile.widthClass)
    }

    @Test
    fun marksHeightBelowFourHundredEightyDpAsShort() {
        val profile = responsiveProfile(widthDp = 900f, heightDp = 479f)

        assertTrue(profile.isShortHeight)
    }

    @Test
    fun usesDockBelowExpandedWidthAndRailAtExpandedWidth() {
        assertEquals(
            JellystackNavigationMode.Dock,
            responsiveProfile(widthDp = 839f, heightDp = 800f).navigationMode,
        )
        assertEquals(
            JellystackNavigationMode.Rail,
            responsiveProfile(widthDp = 840f, heightDp = 800f).navigationMode,
        )
    }

    @Test
    fun shortHeightDockKeepsDestinationsAccessibleWithoutLabels() {
        val profile = responsiveProfile(widthDp = 700f, heightDp = 479f)

        assertEquals(JellystackNavigationMode.Dock, profile.navigationMode)
        assertFalse(profile.dockShowsAllLabels)
    }
}
