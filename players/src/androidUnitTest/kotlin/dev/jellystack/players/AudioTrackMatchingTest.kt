package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioTrackMatchingTest {
    // Matroska numbers the video track 1, so German is track 2 and English track 3, while Jellyfin reports
    // German as stream 1 and English as stream 2.
    private val matroskaTracks =
        listOf(
            candidate(formatId = "2", language = "de"),
            candidate(formatId = "3", language = "en"),
        )

    @Test
    fun directPlayPicksEnglishByPositionInsteadOfTheGermanTrackNumber() {
        val english = audioTrack(streamIndex = 2, audioIndex = 1, language = "eng")

        assertEquals(1, match(matroskaTracks, english, trackLanguage = "en", idsAreStreamIndexes = false))
    }

    @Test
    fun directPlayPicksGermanByPositionInsteadOfTheVideoTrackNumber() {
        val german = audioTrack(streamIndex = 1, audioIndex = 0, language = "ger")

        assertEquals(0, match(matroskaTracks, german, trackLanguage = "de", idsAreStreamIndexes = false))
    }

    @Test
    fun directPlayFallsBackToLanguageWhenThePositionNamesAnotherLanguage() {
        val english = audioTrack(streamIndex = 2, audioIndex = 0, language = "eng")

        assertEquals(1, match(matroskaTracks, english, trackLanguage = "en", idsAreStreamIndexes = false))
    }

    @Test
    fun directPlayIgnoresTheRegionOfAContainerLanguage() {
        val tracks =
            listOf(
                candidate(formatId = "2", language = "en-us"),
                candidate(formatId = "3", language = "en-gb"),
            )
        val secondEnglish = audioTrack(streamIndex = 2, audioIndex = 1, language = "eng")

        assertEquals(1, match(tracks, secondEnglish, trackLanguage = "en", idsAreStreamIndexes = false))
    }

    @Test
    fun directPlaySkipsAnUnsupportedTrackAtThePosition() {
        val tracks =
            listOf(
                candidate(formatId = "2", language = "en", isSupported = false),
                candidate(formatId = "3", language = "en"),
            )
        val english = audioTrack(streamIndex = 1, audioIndex = 0, language = "eng")

        assertEquals(1, match(tracks, english, trackLanguage = "en", idsAreStreamIndexes = false))
    }

    @Test
    fun hlsStillMatchesStreamIndexIds() {
        val tracks =
            listOf(
                candidate(formatId = "1", language = "de"),
                candidate(formatId = "2", language = "en"),
            )
        val english = audioTrack(streamIndex = 2, audioIndex = 1, language = "eng")

        assertEquals(1, match(tracks, english, trackLanguage = "en", idsAreStreamIndexes = true))
    }

    @Test
    fun hlsUsesTheOnlyRenditionTheServerTranscodedForTheTrack() {
        val tracks = listOf(candidate(formatId = "audio", language = null))
        val english = audioTrack(streamIndex = 2, audioIndex = 1, language = "eng")

        assertEquals(
            0,
            match(tracks, english, trackLanguage = "en", idsAreStreamIndexes = true, serverSelectedAudioStreamIndex = 2),
        )
        assertNull(match(tracks, english, trackLanguage = "en", idsAreStreamIndexes = true, serverSelectedAudioStreamIndex = 1))
    }

    private fun match(
        candidates: List<AudioTrackCandidate>,
        track: AudioTrack,
        trackLanguage: String?,
        idsAreStreamIndexes: Boolean,
        serverSelectedAudioStreamIndex: Int? = null,
    ): Int? = matchAudioTrackCandidate(candidates, track, trackLanguage, idsAreStreamIndexes, serverSelectedAudioStreamIndex)

    private fun candidate(
        formatId: String,
        language: String?,
        isSupported: Boolean = true,
    ) = AudioTrackCandidate(formatId = formatId, language = language, label = null, codecMatches = true, isSupported = isSupported)

    private fun audioTrack(
        streamIndex: Int,
        audioIndex: Int,
        language: String,
    ) = AudioTrack(
        id = streamIndex.toString(),
        language = language,
        title = null,
        codec = "eac3",
        isDefault = false,
        streamIndex = streamIndex,
        audioIndex = audioIndex,
    )
}
