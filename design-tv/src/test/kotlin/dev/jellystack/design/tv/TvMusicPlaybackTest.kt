package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.PlaybackMediaKind
import dev.jellystack.players.PlaybackMetadata
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import dev.jellystack.players.PlaybackStartDecision
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.ResolvedPlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvMusicPlaybackTest {
    private val images = TvPlayerImageSource("https://example.com", "token")

    @Test
    fun musicStartsFromTheBeginningWithoutAskingWhateverTheResumeSetting() {
        val track = listItem("t1", "Audio", positionTicks = 600_000_000L)
        ResumeMode.entries.forEach { mode ->
            assertEquals(mode.name, PlaybackStartDecision.Start(PlaybackStartPolicy.RESTART), track.startDecision(mode))
        }
    }

    @Test
    fun anythingElseStillFollowsTheResumeRule() {
        val movie = listItem("m1", "Movie", positionTicks = 600_000_000L)

        assertTrue(movie.startDecision(ResumeMode.ASK) is PlaybackStartDecision.Ask)
        assertEquals(PlaybackStartDecision.Start(PlaybackStartPolicy.INHERIT), movie.startDecision(ResumeMode.RESUME))
    }

    @Test
    fun theRouteShowsTheNowPlayingViewOnlyForMusic() {
        val idle = TvPlaybackStartState.Idle
        assertEquals(TvPlayerSurface.AUDIO, tvPlayerSurface(active(PlaybackMediaKind.AUDIO), idle))
        assertEquals(TvPlayerSurface.VIDEO, tvPlayerSurface(active(PlaybackMediaKind.VIDEO), idle))
        assertEquals(
            TvPlayerSurface.AUDIO,
            tvPlayerSurface(PlaybackState.Preparing("t1", null, PlaybackMediaKind.AUDIO), idle),
        )
    }

    @Test
    fun beforeTheControllerKnowsTheKindTheItemTheUserStartedDecides() {
        fun starting(item: JellyfinItem) = tvPlayerSurface(PlaybackState.Stopped, TvPlaybackStartState.Starting(item))

        assertEquals(TvPlayerSurface.AUDIO, starting(listItem("t1", "Audio")))
        assertEquals(TvPlayerSurface.AUDIO, starting(listItem("album", "MusicAlbum")))
        assertEquals(TvPlayerSurface.AUDIO, starting(listItem("artist", "MusicArtist")))
        assertEquals(TvPlayerSurface.VIDEO, starting(listItem("m1", "Movie")))
        assertEquals(
            TvPlayerSurface.AUDIO,
            tvPlayerSurface(PlaybackState.Stopped, TvPlaybackStartState.Failed(listItem("t1", "Audio"))),
        )
    }

    @Test
    fun nothingRunningLeavesTheSurfaceAsItWas() {
        assertNull(tvPlayerSurface(PlaybackState.Stopped, TvPlaybackStartState.Idle))
    }

    @Test
    fun previousStartsOverATrackThatHasPlayedAWhileAndGoesBackEarlyInTheTrack() {
        assertEquals(TvAudioPreviousAction.PREVIOUS_TRACK, tvAudioPreviousAction(1_000L, hasPrevious = true))
        assertEquals(
            TvAudioPreviousAction.PREVIOUS_TRACK,
            tvAudioPreviousAction(TV_AUDIO_RESTART_THRESHOLD_MS, hasPrevious = true),
        )
        assertEquals(
            TvAudioPreviousAction.RESTART_TRACK,
            tvAudioPreviousAction(TV_AUDIO_RESTART_THRESHOLD_MS + 1, hasPrevious = true),
        )
    }

    @Test
    fun previousOnTheFirstTrackAlwaysRestartsIt() {
        assertEquals(TvAudioPreviousAction.RESTART_TRACK, tvAudioPreviousAction(positionMs = 0L, hasPrevious = false))
    }

    @Test
    fun theTransportGoesBackOrRestartsDependingOnWhereTheTrackIs() {
        val queue = mutableListOf<String>()
        val seeks = mutableListOf<Long>()
        val controls = TvAudioQueueControls(hasPrevious = { true }, playPrevious = { queue += it })

        tvAudioTransport(active(PlaybackMediaKind.AUDIO, positionMs = 1_000L), controls, {}, { seeks += it }, {})
            .onPreviousTrack()
        tvAudioTransport(active(PlaybackMediaKind.AUDIO, positionMs = 90_000L), controls, {}, { seeks += it }, {})
            .onPreviousTrack()

        assertEquals(listOf("t1"), queue)
        assertEquals(listOf(0L), seeks)
    }

    @Test
    fun theNowPlayingViewNamesTheTrackItsArtistAlbumAndHowFarItIs() {
        val nowPlaying = active(PlaybackMediaKind.AUDIO, positionMs = 30_000L).toAudioNowPlaying(images, "Untitled")

        assertEquals("Opening Theme", nowPlaying.title)
        assertEquals("Example Band", nowPlaying.artist)
        assertEquals("First Light", nowPlaying.album)
        assertEquals("0:30", nowPlaying.positionLabel)
        assertEquals("3:00", nowPlaying.durationLabel)
        assertEquals(30_000f / 180_000f, nowPlaying.progress, 0.0001f)
    }

    @Test
    fun theCoverIsTheAlbumsAndFallsBackToThePictureOfTheTrack() {
        val withAlbum = active(PlaybackMediaKind.AUDIO).toAudioNowPlaying(images, "Untitled")
        val albumCover = "https://example.com/Items/album-1/Images/Primary?tag=album-tag"
        assertTrue(withAlbum.artworkUrl.orEmpty().startsWith(albumCover))

        val noAlbumCover = trackMetadata().copy(albumId = null, albumPrimaryImageTag = null)
        val trackOnly = active(PlaybackMediaKind.AUDIO, metadata = noAlbumCover).toAudioNowPlaying(images, "Untitled")
        val trackCover = "https://example.com/Items/t1/Images/Primary?tag=track-tag"
        assertTrue(trackOnly.artworkUrl.orEmpty().startsWith(trackCover))
    }

    @Test
    fun aTrackWithoutNamesStillShowsAndAnUnknownLengthHasNoProgress() {
        val bare = PlaybackMetadata(null, null, null, null, null, null)
        val nowPlaying =
            active(PlaybackMediaKind.AUDIO, durationMs = null, metadata = bare).toAudioNowPlaying(images, "Untitled")

        assertEquals("Untitled", nowPlaying.title)
        assertNull(nowPlaying.artist)
        assertNull(nowPlaying.album)
        assertNull(nowPlaying.durationLabel)
        assertEquals(0f, nowPlaying.progress, 0f)
    }

    @Test
    fun trackRowsFollowTheOrderOfTheAlbumWithTheirNumbersAndLengths() {
        val rows =
            buildTrackRows(
                listOf(
                    track("a", number = 1, ticks = 1_850_000_000L),
                    track("b", number = 2, ticks = null),
                    track("c", number = null, ticks = 600_000_000L),
                ),
            )

        val tracks = rows.filterIsInstance<TvTrackRow.Track>()
        assertEquals(listOf("a", "b", "c"), tracks.map { it.item.id })
        assertEquals(listOf(1, 2, 3), tracks.map { it.number })
        assertEquals(listOf("3:05", null, "1:00"), tracks.map { it.duration })
        assertEquals(listOf(0, 1, 2), tracks.map { it.index })
        assertTrue(rows.none { it is TvTrackRow.DiscHeader })
    }

    @Test
    fun anAlbumOfSeveralDiscsGetsAHeadingBeforeEachDisc() {
        val rows =
            buildTrackRows(
                listOf(
                    track("a", number = 1, disc = 1),
                    track("b", number = 2, disc = 1),
                    track("c", number = 1, disc = 2),
                ),
            )

        assertEquals(
            listOf("disc:1", "track:a", "track:b", "disc:2", "track:c"),
            rows.map { row ->
                when (row) {
                    is TvTrackRow.DiscHeader -> "disc:${row.disc}"
                    is TvTrackRow.Track -> "track:${row.item.id}"
                }
            },
        )
        val locations = trackFocusLocations(rows)
        assertEquals(1, locations[tvLibraryTargetId("a")])
        assertEquals(4, locations[tvLibraryTargetId("c")])
    }

    @Test
    fun theSubtitleCountsTheTracksAndAddsTheTotalLength() {
        val music = TvStrings.current(AppLanguage.ENGLISH).messages.music
        val tracks = listOf(track("a", 1, ticks = 1_800_000_000L), track("b", 2, ticks = 1_800_000_000L))

        val expected = listOf("Album", "2 tracks", "6:00").joinToString(TV_CARD_SEPARATOR)
        assertEquals(expected, trackListSubtitle("Album", tracks, music))
        assertEquals("Album", trackListSubtitle("Album", emptyList(), music))
        assertEquals("1 track", music.trackCount(1))
    }

    @Test
    fun everyMusicLabelExistsInEnglishAndGerman() {
        listOf(AppLanguage.ENGLISH, AppLanguage.GERMAN).forEach { language ->
            val music = TvStrings.current(language).messages.music
            val labels = listOf(music.shuffle, music.albums, music.album, music.artist, music.nowPlaying)
            (labels + music.previousTrack + music.nextTrack).forEach { assertTrue("$language", it.isNotBlank()) }
            assertTrue("$language", music.disc(2).contains("2"))
            assertTrue("$language", music.trackCount(7).contains("7"))
        }
        assertEquals(
            "Zufällig",
            TvStrings
                .current(AppLanguage.GERMAN)
                .messages.music.shuffle,
        )
    }

    private fun track(
        id: String,
        number: Int?,
        disc: Int? = null,
        ticks: Long? = null,
    ) = listItem(id, "Audio").copy(indexNumber = number, parentIndexNumber = disc, runTimeTicks = ticks)

    private fun trackMetadata() =
        PlaybackMetadata(
            title = "Opening Theme",
            seriesId = null,
            seriesName = null,
            episodeName = null,
            artworkUrl = null,
            primaryImageTag = "track-tag",
            artist = "Example Band",
            albumName = "First Light",
            albumId = "album-1",
            albumPrimaryImageTag = "album-tag",
        )

    private fun active(
        kind: PlaybackMediaKind,
        positionMs: Long = 0L,
        durationMs: Long? = 180_000L,
        metadata: PlaybackMetadata = trackMetadata(),
    ): PlaybackState.Active =
        PlaybackState.LocalPlayback(
            mediaId = "t1",
            deviceName = "test",
            stream =
                PlaybackStreamSelection(
                    sourceId = "source",
                    mode = PlaybackMode.DIRECT,
                    container = "flac",
                    videoCodec = null,
                    audioCodec = "flac",
                    videoBitrate = null,
                    audioTracks = emptyList(),
                    subtitleTracks = emptyList(),
                    maxBitrate = null,
                    qualityOptions = emptyList(),
                    selectedQualityId = PlaybackQualityOption.AUTO_ID,
                ),
            positionMs = positionMs,
            durationMs = durationMs,
            audioTrack = null,
            subtitleTrack = null,
            isPaused = false,
            source =
                ResolvedPlaybackSource(
                    url = "https://example.com/audio",
                    headers = emptyMap(),
                    mode = PlaybackMode.DIRECT,
                    mimeType = "audio/flac",
                    subtitles = emptyList(),
                    playSessionId = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = null,
                ),
            qualityOptions = emptyList(),
            selectedQualityId = PlaybackQualityOption.AUTO_ID,
            metadata = metadata,
            mediaKind = kind,
        )
}
