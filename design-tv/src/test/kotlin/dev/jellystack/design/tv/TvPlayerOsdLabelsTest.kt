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

    @Test
    fun trackRowsUseTheChipLabelAndKeepRepeatedTracksApart() {
        val strings = TvStrings.current(AppLanguage.GERMAN)
        val audioTracks =
            listOf(
                audio("de").copy(id = "main", title = "Stereo - German - AC3 - Default", codec = "ac3", channels = 2),
                audio("de").copy(id = "commentary", title = "Commentary", isDefault = false, channels = 2),
                audio("ja").copy(id = "japanese", title = "Japanese", isDefault = false, channels = 6),
            )
        val forced =
            subtitle("de").copy(id = "forced", title = "Forced - German", format = SubtitleFormat.ASS, isForced = true)

        val audio = tvAudioTrackRows(audioTracks, strings)
        val subtitles = tvSubtitleTrackRows(listOf(forced, subtitle("en")), strings)

        assertEquals(audioTracks.map { tvAudioButtonLabel(it, strings.locale) }, audio.map { it.title })
        assertEquals("AC3 · ${strings.player.defaultTrack} · Stereo - German - AC3 - Default", audio[0].detail)
        assertEquals("AAC · Commentary", audio[1].detail)
        assertEquals("AAC", audio[2].detail)
        assertEquals(tvSubtitleButtonLabel(forced, strings.locale), subtitles[0].title)
        assertEquals("${strings.player.forcedTrack} · ASS", subtitles[0].detail)
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
