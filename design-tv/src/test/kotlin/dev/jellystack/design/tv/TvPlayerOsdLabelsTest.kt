package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleFormat
import dev.jellystack.players.SubtitleTrack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TvPlayerOsdLabelsTest {
    private lateinit var systemLocale: Locale

    @Before
    fun rememberSystemLocale() {
        systemLocale = Locale.getDefault()
    }

    @After
    fun restoreSystemLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun trackLanguagesFollowTheAppLanguageOnAGermanSystem() {
        Locale.setDefault(Locale.GERMAN)
        val english = TvStrings.current(AppLanguage.ENGLISH).locale

        assertEquals("Japanese", tvAudioButtonLabel(audio("ja"), english))
        assertEquals("English", tvSubtitleButtonLabel(subtitle("en"), english))
    }

    @Test
    fun trackLanguagesFollowTheAppLanguageOnAnEnglishSystem() {
        Locale.setDefault(Locale.ENGLISH)
        val german = TvStrings.current(AppLanguage.GERMAN).locale

        assertEquals("Japanisch", tvAudioButtonLabel(audio("ja"), german))
        assertEquals("Englisch", tvSubtitleButtonLabel(subtitle("en"), german))
    }

    private fun audio(language: String) =
        AudioTrack(id = "a", language = language, title = null, codec = "aac", isDefault = true, streamIndex = 1)

    private fun subtitle(language: String) =
        SubtitleTrack(
            id = "s",
            language = language,
            title = null,
            format = SubtitleFormat.SRT,
            isDefault = false,
            isForced = false,
            streamIndex = 2,
        )
}
