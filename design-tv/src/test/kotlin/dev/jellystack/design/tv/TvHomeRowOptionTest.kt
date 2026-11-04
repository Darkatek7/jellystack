package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.preferences.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class TvHomeRowOptionTest {
    private val strings = TvStrings.current(AppLanguage.ENGLISH)

    @Test
    fun homeLeavesOutEmptyRowsButSettingsOfferThem() {
        val empty = JellyfinHomeState()

        val home = tvHomeRowOptions(empty, HomeSectionsState.Unavailable, hasMyList = false, strings = strings)
        val settings =
            tvHomeRowOptions(
                empty,
                HomeSectionsState.Unavailable,
                hasMyList = false,
                strings = strings,
                includeEmpty = true,
            )

        assertEquals(emptyList(), home.map { it.id })
        assertEquals(
            listOf(TV_HOME_MY_LIST_ROW, TV_HOME_CONTINUE_ROW, TV_HOME_NEXT_UP_ROW, TV_HOME_LIBRARIES_ROW),
            settings.map { it.id },
        )
    }
}
