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
        assertEquals("More", english.more)
        assertEquals("Mehr", german.more)
        assertEquals("More options", english.moreOptions)
        assertEquals("Weitere Optionen", german.moreOptions)
        assertEquals("Playback could not continue", english.playbackFailedTitle)
        assertEquals("Wiedergabe konnte nicht fortgesetzt werden", german.playbackFailedTitle)
        assertEquals("Your Jellyfin and Seerr library, designed for the big screen.", english.tvTagline)
        assertEquals("Recently added", english.recentlyAdded)
        assertEquals("Last 30 days", english.lastThirtyDays)
        assertEquals("Latest additions", english.latestAdditions)
        assertEquals("From your library", english.fromYourLibrary)
        assertEquals("Neu hinzugefügt", german.recentlyAdded)
        assertEquals("Letzte 30 Tage", german.lastThirtyDays)
        assertEquals("Zuletzt hinzugefügt", german.latestAdditions)
        assertEquals("Aus deiner Mediathek", german.fromYourLibrary)
        assertEquals("Details", english.details)
        assertEquals("Details", german.details)
        assertEquals("Trailer previews", english.trailerPreviews)
        assertEquals("Preview sound", english.trailerPreviewSound)
        assertEquals("Automatisch", german.automatic)
        assertEquals("Skip intro", english.skipIntro)
        assertEquals("Intro überspringen", german.skipIntro)
        assertEquals("Skip recap", english.skipRecap)
        assertEquals("Rückblick überspringen", german.skipRecap)
        assertEquals("Skip preview", english.skipPreview)
        assertEquals("Vorschau überspringen", german.skipPreview)
        assertEquals("Skip commercial", english.skipCommercial)
        assertEquals("Werbung überspringen", german.skipCommercial)
        assertEquals("Skip credits", english.skipCredits)
        assertEquals("Abspann überspringen", german.skipCredits)
        assertEquals("Play next episode", english.playNextEpisode)
        assertEquals("Nächste Folge abspielen", german.playNextEpisode)
        assertEquals("Show skip button", english.showSkipButton)
        assertEquals("Skip automatically", english.skipAutomatically)
    }
}
