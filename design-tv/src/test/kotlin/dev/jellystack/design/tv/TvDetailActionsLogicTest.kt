@file:Suppress("MaxLineLength")

package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.PlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDetailActionsLogicTest {
    private val english = TvStrings.English
    private val german = TvStrings.current(AppLanguage.GERMAN)

    @Test
    fun primaryLabelNamesTheResumeTimeForAMovie() {
        assertEquals("Play", tvDetailPrimaryLabel(null, hasResume = false, nextEpisode = null, strings = english))
        assertEquals(
            "Continue · 38 min left",
            tvDetailPrimaryLabel(38, hasResume = true, nextEpisode = null, strings = english),
        )
        assertEquals(
            "Fortsetzen · noch 38 Min.",
            tvDetailPrimaryLabel(38, hasResume = true, nextEpisode = null, strings = german),
        )
        assertEquals("Continue", tvDetailPrimaryLabel(null, hasResume = true, nextEpisode = null, strings = english))
    }

    @Test
    fun primaryLabelNamesTheEpisodeASeriesPlays() {
        val fresh = TvDetailNextEpisode(season = 2, episode = 3, inProgress = false)
        val started = fresh.copy(inProgress = true)

        assertEquals("Play S2 E3", tvDetailPrimaryLabel(null, false, fresh, english))
        assertEquals("S2 F3 abspielen", tvDetailPrimaryLabel(null, false, fresh, german))
        assertEquals("Continue S2 E3", tvDetailPrimaryLabel(null, true, started, english))
        assertEquals("S2 F3 fortsetzen", tvDetailPrimaryLabel(null, true, started, german))
    }

    @Test
    fun startOverOnlyAppearsWhenPlayingWouldContinue() {
        assertFalse(tvDetailOffersStartOver(hasResume = false, nextEpisode = null))
        assertTrue(tvDetailOffersStartOver(hasResume = true, nextEpisode = null))
        assertFalse(tvDetailOffersStartOver(hasResume = true, nextEpisode = TvDetailNextEpisode(1, 1, inProgress = false)))
        assertTrue(tvDetailOffersStartOver(hasResume = false, nextEpisode = TvDetailNextEpisode(1, 1, inProgress = true)))
    }

    @Test
    fun runtimeReadsAsHoursAndMinutesInBothLanguages() {
        assertEquals("2h 46m", tvRuntimeLabel(166, english))
        assertEquals("2 Std 46 Min", tvRuntimeLabel(166, german))
        assertEquals("45m", tvRuntimeLabel(45, english))
        assertEquals("1h 0m", tvRuntimeLabel(60, english))
        assertNull(tvRuntimeLabel(0, english))
        assertNull(tvRuntimeLabel(null, english))
    }

    @Test
    fun metaLineKeepsTheOrderAndSkipsMissingParts() {
        assertEquals(
            "2024  ·  2h 46m  ·  PG-13  ·  ★ 7.8  ·  Ends at 10:41 PM",
            tvDetailMetaLine("2024", 166, "PG-13", 7.8, "Ends at 10:41 PM", english),
        )
        assertEquals("2024  ·  45m", tvDetailMetaLine("2024", 45, "0", null, null, english))
        assertEquals("", tvDetailMetaLine(null, null, null, null, null, english))
    }

    @Test
    fun trackLineNamesAudioSubtitlesAndTheVersionOnlyWithSeveralFiles() {
        val single = detail(listOf(source("a", "Movie 1080p")))

        assertEquals(
            "Audio: German 5.1  ·  Subtitles: Off",
            tvTrackLineSummary(tvPlaybackOptions(single, TvDetailSelection(), english), english),
        )

        val several = detail(listOf(source("a", "Movie 1080p"), source("b", "Movie 4K")))
        val picked = TvDetailSelection(sourceId = "b", subtitleStreamIndex = 4)

        assertEquals(
            "Audio: German 5.1  ·  Subtitles: English  ·  Version: Movie 4K",
            tvTrackLineSummary(tvPlaybackOptions(several, picked, english), english),
        )
        assertEquals(
            "Ton: Deutsch 5.1  ·  Untertitel: Englisch  ·  Version: Movie 4K",
            tvTrackLineSummary(tvPlaybackOptions(several, picked, german), german),
        )
    }

    @Test
    fun trackLineIsHiddenWithoutAnyStreams() {
        val bare = detail(listOf(source("a", null).copy(streams = emptyList())))

        assertNull(tvTrackLineSummary(tvPlaybackOptions(bare, TvDetailSelection(), english), english))
        assertNull(tvTrackLineSummary(null, english))
    }

    @Test
    fun pickingAnotherVersionResetsTheTracks() {
        val selection = TvDetailSelection().withAudio(2).withSubtitle(null).withVersion("b")

        assertEquals(TvDetailSelection(sourceId = "b"), selection)
        assertNull(TvDetailSelection().toPlayChoice())
        assertEquals(
            TvPlayChoice(audioStreamIndex = 2, subtitlesOff = true),
            TvDetailSelection().withAudio(2).withSubtitle(null).toPlayChoice(),
        )
    }

    @Test
    fun choiceLimitsTheRequestToTheChosenVersionAndSetsTheTracks() {
        val request = PlaybackRequest.from(item(), detail(listOf(source("a", "1080p"), source("b", "4K"))))

        val chosen = request.withChoice(TvPlayChoice(mediaSourceId = "b", audioStreamIndex = 2, subtitleStreamIndex = 4))
        val off = request.withChoice(TvPlayChoice(subtitlesOff = true))

        assertEquals(listOf("b"), chosen.mediaSources.map { it.id })
        assertEquals("2", chosen.preferredAudioTrackId)
        assertEquals("4", chosen.preferredSubtitleTrackId)
        assertEquals(2, off.mediaSources.size)
        // Off is a track id no stream has, so the controller finds no subtitle to select.
        val offId = off.preferredSubtitleTrackId
        assertTrue(offId != null && off.mediaSources.none { s -> s.streams.any { it.index.toString() == offId } })
        assertEquals(request, request.withChoice(null))
    }

    @Test
    fun moreMenuOffersOnlyWhatApplies() {
        assertEquals(emptyList<TvDetailMoreEntry>(), tvDetailMoreEntries(isEpisode = false, hasSeries = false, versionCount = 0))
        assertEquals(
            listOf(TvDetailMoreEntry.GO_TO_SERIES, TvDetailMoreEntry.VERSION),
            tvDetailMoreEntries(isEpisode = true, hasSeries = true, versionCount = 2),
        )
        assertEquals(emptyList<TvDetailMoreEntry>(), tvDetailMoreEntries(isEpisode = true, hasSeries = false, versionCount = 1))
    }

    @Test
    fun seasonSiblingsAreTheOtherEpisodesOfTheSameSeasonInOrder() {
        val one = item().copy(id = "e1", parentIndexNumber = 1, indexNumber = 1)
        val two = item().copy(id = "e2", parentIndexNumber = 1, indexNumber = 2)
        val other = item().copy(id = "x1", parentIndexNumber = 2, indexNumber = 1)

        assertEquals(listOf("e1", "e2"), tvSeasonSiblings(two, listOf(two, other, one)).map { it.id })
        assertEquals(emptyList<JellyfinItem>(), tvSeasonSiblings(one, listOf(one, other)))
    }

    @Test
    fun seriesStandInCarriesTheSeriesIdAndArtwork() {
        val episode = item().copy(id = "e1", seriesId = "s1", seriesName = "Harbor Lights", seriesPrimaryImageTag = "poster")

        val series = tvSeriesStubOf(episode)

        assertEquals("s1", series?.id)
        assertEquals("Series", series?.type)
        assertEquals("Harbor Lights", series?.name)
        assertEquals("poster", series?.primaryImageTag)
        assertNull(tvSeriesStubOf(episode.copy(seriesId = null)))
    }

    @Test
    fun detailRowsOnlyListCreditsTheItemHas() {
        val withCredits =
            detail(listOf(source("a", null))).copy(
                people =
                    listOf(
                        JellyfinPerson("1", "Robin Example", null, "Director", null),
                        JellyfinPerson("2", "Alex Example", "Lena", "Actor", null),
                    ),
                studios = listOf("Northern Light Pictures"),
            )

        val labels = tvJellyfinDetailRows(withCredits, english).map { it.label }

        assertEquals(listOf("Director", "Studios", "Audio languages", "Subtitle languages"), labels)
        assertEquals(listOf("Alex Example"), withCredits.tvCastPeople().map { it.name })
        assertEquals(emptyList<TvDetailRow>(), tvJellyfinDetailRows(detail(emptyList()), english))
    }

    private fun stream(
        type: JellyfinMediaStreamType,
        index: Int,
        language: String?,
        isDefault: Boolean = false,
        channels: Int? = null,
    ) = JellyfinMediaStream(
        type = type,
        index = index,
        displayTitle = null,
        codec = "x",
        language = language,
        isDefault = isDefault,
        isForced = false,
        channels = channels,
    )

    private fun source(
        id: String,
        name: String?,
    ) = JellyfinMediaSource(
        id = id,
        name = name,
        runTimeTicks = null,
        container = "mkv",
        videoBitrate = null,
        supportsDirectPlay = true,
        supportsDirectStream = true,
        supportsTranscoding = true,
        streams =
            listOf(
                stream(JellyfinMediaStreamType.VIDEO, 0, null),
                stream(JellyfinMediaStreamType.AUDIO, 1, "de", isDefault = true, channels = 6),
                stream(JellyfinMediaStreamType.AUDIO, 2, "en", channels = 6),
                stream(JellyfinMediaStreamType.SUBTITLE, 3, "de"),
                stream(JellyfinMediaStreamType.SUBTITLE, 4, "en"),
            ),
    )

    private fun detail(sources: List<JellyfinMediaSource>) =
        JellyfinItemDetail(
            id = "m",
            name = "Movie",
            overview = null,
            taglines = emptyList(),
            runTimeTicks = null,
            productionYear = null,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            genres = emptyList(),
            studios = emptyList(),
            primaryImageTag = null,
            backdropImageTags = emptyList(),
            mediaSources = sources,
        )

    private fun item() = goldenItem("Movie", "Movie")
}
