package dev.jellystack.core.preferences

import dev.jellystack.core.testing.InMemorySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRowPreferencesTest {
    private val available = listOf("continue", "next", "plugin:latest-movies", "libraries")

    @Test
    fun defaultKeepsTheBuiltOrderAndShowsEverything() {
        val rows = HomeRowPreferences().arrange(available)

        assertEquals(available, rows.map { it.id })
        assertEquals(true, rows.all { it.visible })
    }

    @Test
    fun savedOrderWinsAndNewRowsFollowVisibleAtTheEnd() {
        val prefs = HomeRowPreferences(order = listOf("libraries", "continue", "gone"), hidden = setOf("next"))

        val rows = prefs.arrange(available + "plugin:added-later")

        assertEquals(
            listOf("libraries", "continue", "next", "plugin:latest-movies", "plugin:added-later"),
            rows.map { it.id },
        )
        assertEquals(listOf(false), rows.filter { it.id == "next" }.map { it.visible })
        assertEquals(true, rows.last().visible)
    }

    @Test
    fun hiddenRowsDropOutOfTheVisibleIds() {
        val prefs = HomeRowPreferences(hidden = setOf("continue", "libraries"))

        assertEquals(listOf("next", "plugin:latest-movies"), prefs.visibleRowIds(available))
    }

    @Test
    fun moveStopsAtTheEndsAndKeepsRowsOfOtherLayouts() {
        val prefs = HomeRowPreferences(order = listOf("my-list"))

        val down = prefs.move(available, "continue", 1)
        assertEquals(listOf("next", "continue", "plugin:latest-movies", "libraries"), down.arrange(available).map { it.id })
        assertEquals(listOf("my-list"), down.order.filter { it == "my-list" })

        assertEquals(prefs, prefs.move(available, "continue", -1))
        val last = prefs.move(available, "continue", 10)
        assertEquals("continue", last.arrange(available).last().id)
    }

    @Test
    fun hidingAndShowingARowRoundTrips() {
        val hidden = HomeRowPreferences().withVisibility("next", visible = false)
        assertEquals(setOf("next"), hidden.hidden)
        assertEquals(HomeRowPreferences(), hidden.withVisibility("next", visible = true))
    }

    @Test
    fun repositoryPersistsRowsAndTheHintsFlag() {
        val storage = InMemorySettings()
        val prefs = HomeRowPreferences(order = listOf("next", "plugin:a b"), hidden = setOf("libraries"))

        AppSettingsRepository(storage).apply {
            setHomeRows(prefs)
            setHeroHintsSeen(true)
        }

        val reloaded = AppSettingsRepository(storage).settings.value
        assertEquals(prefs, reloaded.homeRows)
        assertEquals(true, reloaded.heroHintsSeen)
    }
}
