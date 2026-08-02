package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class TvStringsTest {
    @Test
    fun exposesParallelEnglishAndGermanTvLabels() {
        val english = TvStrings.current(AppLanguage.ENGLISH)
        val german = TvStrings.current(AppLanguage.GERMAN)

        assertEquals("Next episode", english.nextEpisode)
        assertEquals("Nächste Folge", german.nextEpisode)
        assertEquals("Server connections", english.connections)
        assertEquals("Serververbindungen", german.connections)
        assertEquals("Show Stats for Nerds", english.showStats)
        assertEquals("Stats for Nerds anzeigen", german.showStats)
        assertEquals("Your Jellyfin and Seerr library, designed for the big screen.", english.tvTagline)
        assertEquals("Automatisch", german.automatic)
    }
}
