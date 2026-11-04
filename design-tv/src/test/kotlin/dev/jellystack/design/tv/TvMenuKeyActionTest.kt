package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvMenuKeyActionTest {
    @Test
    fun mainScreensOpenThePicker() {
        listOf(TvRoute.Home, TvRoute.Search, TvRoute.Discover, TvRoute.LiveTv).forEach { route ->
            assertEquals(TvMenuKeyAction.OPEN_PROFILE_PICKER, tvMenuKeyAction(route, dialogOpen = false))
        }
        assertEquals(TvMenuKeyAction.OPEN_PROFILE_PICKER, tvMenuKeyAction(TvRoute.Settings(), dialogOpen = false))
        assertEquals(TvMenuKeyAction.OPEN_PROFILE_PICKER, tvMenuKeyAction(TvRoute.Library(), dialogOpen = false))
    }

    @Test
    fun playerAndDetailPagesIgnoreMenu() {
        assertEquals(TvMenuKeyAction.NONE, tvMenuKeyAction(TvRoute.Player, dialogOpen = false))
        assertEquals(TvMenuKeyAction.NONE, tvMenuKeyAction(TvRoute.JellyfinDetail("item"), dialogOpen = false))
    }

    @Test
    fun openDialogSuppressesMenu() {
        assertEquals(TvMenuKeyAction.NONE, tvMenuKeyAction(TvRoute.Home, dialogOpen = true))
    }
}

class TvPickerBackTest {
    @Test
    fun onlyAUserOpenedPickerRemembersTheActiveProfile() {
        assertEquals("a", tvPickerReturnProfileId(userOpened = true, activeProfileId = "a"))
        assertEquals(null, tvPickerReturnProfileId(userOpened = false, activeProfileId = "a"))
        assertEquals(null, tvPickerReturnProfileId(userOpened = true, activeProfileId = null))
    }

    @Test
    fun backReturnsToTheRememberedProfileOnlyWhenItStillExistsAndNoSubScreenIsOpen() {
        val ids = listOf("a", "b")
        assertEquals("a", tvPickerBackTarget("a", ids, subScreenOpen = false))
        assertEquals(null, tvPickerBackTarget("a", ids, subScreenOpen = true))
        assertEquals(null, tvPickerBackTarget("gone", ids, subScreenOpen = false))
        assertEquals(null, tvPickerBackTarget(null, ids, subScreenOpen = false))
    }
}
