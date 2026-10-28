package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPlaybackLauncherTest {
    private val started = mutableListOf<PlaybackRequest>()
    private val startedSettings = mutableListOf<AppSettings>()
    private var startedRoutes = 0

    @Test
    fun askModeWithSavedPositionWaitsForTheAnswerAndStartsWithIt() =
        runTest {
            val launcher = launcher(ResumeMode.ASK)

            launcher.play(item(positionTicks = 7_540_000_000L))
            runCurrent()

            assertEquals("12:34", launcher.pendingAsk?.positionLabel)
            assertEquals(emptyList<PlaybackRequest>(), started)

            launcher.answerAsk(PlaybackStartPolicy.RESTART)
            runCurrent()

            assertNull(launcher.pendingAsk)
            assertEquals(listOf(PlaybackStartPolicy.RESTART), started.map { it.startPolicy })
            assertEquals(1, startedRoutes)
        }

    @Test
    fun cancellingTheAskStartsNothing() =
        runTest {
            val launcher = launcher(ResumeMode.ASK)

            launcher.play(item(positionTicks = 7_540_000_000L))
            launcher.answerAsk(null)
            runCurrent()

            assertNull(launcher.pendingAsk)
            assertEquals(emptyList<PlaybackRequest>(), started)
        }

    @Test
    fun theStartOpensThePlayerRightAwayAndEndsIdleOnceTheControllerTookOver() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val launcher = launcher(ResumeMode.ASK, startGate = gate)

            val movie = item(positionTicks = null)
            launcher.play(movie)
            assertEquals("the route opens before the server answered", 1, startedRoutes)
            assertEquals(TvPlaybackStartState.Starting(movie), launcher.startState)

            runCurrent()
            assertEquals(TvPlaybackStartState.Starting(movie), launcher.startState)
            gate.complete(Unit)
            runCurrent()

            assertEquals(listOf(PlaybackStartPolicy.INHERIT), started.map { it.startPolicy })
            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
        }

    @Test
    fun missingServerDetailOrFailingDetailRequestEndInAFailedStartInsteadOfNothing() =
        runTest {
            val movie = item(positionTicks = null)

            val noServer = launcher(ResumeMode.RESUME, startSucceeds = false)
            noServer.play(movie)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(movie), noServer.startState)

            val noDetail = launcher(ResumeMode.RESUME, loadDetail = { null })
            noDetail.play(movie)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(movie), noDetail.startState)

            val failingDetail = launcher(ResumeMode.RESUME, loadDetail = { throw IllegalStateException("offline") })
            failingDetail.play(movie)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(movie), failingDetail.startState)

            assertEquals(emptyList<PlaybackRequest>(), started)
        }

    @Test
    fun retryRunsTheFailedStartAgain() =
        runTest {
            var detailAvailable = false
            val movie = item(positionTicks = null)
            val launcher = launcher(ResumeMode.RESUME, loadDetail = { if (detailAvailable) detail() else null })

            launcher.play(movie)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(movie), launcher.startState)

            detailAvailable = true
            launcher.retryStart()
            assertEquals(TvPlaybackStartState.Starting(movie), launcher.startState)
            runCurrent()

            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
            assertEquals(1, started.size)
        }

    @Test
    fun cancellingWhileTheStartIsPendingStopsItBeforeThePlayerStarts() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val launcher = launcher(ResumeMode.RESUME, startGate = gate)

            launcher.play(item(positionTicks = null))
            runCurrent()
            launcher.cancelStart()
            gate.complete(Unit)
            runCurrent()

            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
            assertEquals(emptyList<PlaybackRequest>(), started)
        }

    @Test
    fun aNewStartSupersedesAPendingOne() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val first = item(positionTicks = null)
            val second = item(positionTicks = null).copy(id = "other", name = "Other")
            val launcher = launcher(ResumeMode.RESUME, startGate = gate)

            launcher.play(first)
            runCurrent()
            launcher.play(second)
            gate.complete(Unit)
            runCurrent()

            assertEquals(listOf("other"), started.map { it.mediaId })
            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
        }

    @Test
    fun aQueueFromTheSyncPlayGroupOpensThePlayerAtTheGroupPositionWithoutAskingOrSpeedingUp() =
        runTest {
            val launcher = launcher(ResumeMode.ASK, defaultPlaybackSpeed = 1.5f)

            launcher.playForGroup(item(positionTicks = 7_540_000_000L), startPositionMs = 90_000L)
            assertEquals("the route opens before the server answered", 1, startedRoutes)
            runCurrent()

            assertNull(launcher.pendingAsk)
            assertEquals(listOf(900_000_000L), started.map { it.resumePositionTicks })
            assertEquals(listOf(1f), startedSettings.map { it.defaultPlaybackSpeed })
            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
        }

    @Test
    fun playingASeriesStartsTheEpisodeInProgress() =
        runTest {
            val episodes =
                listOf(
                    episode("s1e1", season = 1, number = 1, playedPercentage = 100.0),
                    episode("s1e2", season = 1, number = 2, playedPercentage = 40.0, positionTicks = 7_540_000_000L),
                    episode("s1e3", season = 1, number = 3),
                )
            val launcher = launcher(ResumeMode.RESUME, episodes = episodes)

            launcher.play(series())
            runCurrent()

            assertEquals(listOf("s1e2"), started.map { it.mediaId })
            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
        }

    @Test
    fun playingASeriesWithoutProgressStartsTheFirstEpisodeInSeasonOrder() =
        runTest {
            val episodes =
                listOf(
                    episode("s2e1", season = 2, number = 1),
                    episode("s1e2", season = 1, number = 2),
                    episode("s1e1", season = 1, number = 1),
                )
            val launcher = launcher(ResumeMode.RESUME, episodes = episodes)

            launcher.play(series())
            runCurrent()

            assertEquals(listOf("s1e1"), started.map { it.mediaId })
        }

    @Test
    fun playingAFullyWatchedSeriesStartsItFromTheFirstEpisode() =
        runTest {
            val episodes =
                listOf(
                    episode("s1e2", season = 1, number = 2, playedPercentage = 100.0),
                    episode("s1e1", season = 1, number = 1, playedPercentage = 100.0),
                )
            val launcher = launcher(ResumeMode.RESUME, episodes = episodes)

            launcher.play(series())
            runCurrent()

            assertEquals(listOf("s1e1"), started.map { it.mediaId })
        }

    @Test
    fun anInProgressEpisodeOfASeriesAsksBeforeResumingInAskMode() =
        runTest {
            val episodes = listOf(episode("s1e2", season = 1, number = 2, playedPercentage = 40.0, positionTicks = 7_540_000_000L))
            val launcher = launcher(ResumeMode.ASK, episodes = episodes)

            launcher.play(series())
            runCurrent()

            assertEquals("s1e2", launcher.pendingAsk?.item?.id)
            assertEquals("12:34", launcher.pendingAsk?.positionLabel)
            assertEquals(TvPlaybackStartState.Idle, launcher.startState)
            assertEquals(emptyList<PlaybackRequest>(), started)

            launcher.answerAsk(PlaybackStartPolicy.RESTART)
            runCurrent()

            assertEquals(listOf("s1e2"), started.map { it.mediaId })
            assertEquals(listOf(PlaybackStartPolicy.RESTART), started.map { it.startPolicy })
        }

    @Test
    fun aSeriesWithoutAPlayableEpisodeEndsInAFailedStartWithItsOwnReason() =
        runTest {
            val series = series()
            val noEpisodes = launcher(ResumeMode.RESUME, episodes = emptyList())
            noEpisodes.play(series)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(series, TvStartFailure.NO_PLAYABLE_EPISODE), noEpisodes.startState)

            val missingOnly =
                launcher(ResumeMode.RESUME, episodes = listOf(episode("s1e1", season = 1, number = 1, locationType = "Virtual")))
            missingOnly.play(series)
            runCurrent()
            assertEquals(TvPlaybackStartState.Failed(series, TvStartFailure.NO_PLAYABLE_EPISODE), missingOnly.startState)

            assertEquals(emptyList<PlaybackRequest>(), started)
        }

    @Test
    fun aFailingEpisodeListEndsInAGenericFailedStart() =
        runTest {
            val series = series()
            val launcher = launcher(ResumeMode.RESUME, loadEpisodes = { throw IllegalStateException("offline") })

            launcher.play(series)
            runCurrent()

            assertEquals(TvPlaybackStartState.Failed(series, TvStartFailure.COULD_NOT_START), launcher.startState)
        }

    @Test
    fun theStartErrorPanelNamesTheReasonOfTheFailure() {
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        val series = series()

        assertEquals(
            strings.player.noPlayableEpisodeMessage,
            tvStartFailureMessage(TvPlaybackStartState.Failed(series, TvStartFailure.NO_PLAYABLE_EPISODE), strings),
        )
        assertEquals(
            strings.player.startFailedMessage,
            tvStartFailureMessage(TvPlaybackStartState.Failed(series, TvStartFailure.COULD_NOT_START), strings),
        )
    }

    private fun TestScope.launcher(
        resumeMode: ResumeMode,
        startSucceeds: Boolean = true,
        loadDetail: suspend () -> JellyfinItemDetail? = { detail() },
        episodes: List<JellyfinItem> = emptyList(),
        loadEpisodes: suspend () -> List<JellyfinItem> = { episodes },
        startGate: CompletableDeferred<Unit>? = null,
        defaultPlaybackSpeed: Float = 1f,
    ) = TvPlaybackLauncher(
        scope = this,
        loadDetail = { loadDetail() },
        loadEpisodes = { loadEpisodes() },
        starter =
            TvPlaybackStarter { request, settings ->
                startGate?.await()
                if (startSucceeds) started += request
                startedSettings += settings
                startSucceeds
            },
        currentSettings = { AppSettings(resumeMode = resumeMode, defaultPlaybackSpeed = defaultPlaybackSpeed) },
        openPlayer = { startedRoutes += 1 },
    )

    private fun series() = item(positionTicks = null, id = "series", type = "Series")

    private fun episode(
        id: String,
        season: Int,
        number: Int,
        playedPercentage: Double? = null,
        positionTicks: Long? = null,
        locationType: String = "FileSystem",
    ) = item(positionTicks, id = id, type = "Episode").copy(
        locationType = locationType,
        playedPercentage = playedPercentage,
        parentIndexNumber = season,
        indexNumber = number,
        seriesId = "series",
    )

    private fun item(
        positionTicks: Long?,
        id: String = "movie",
        type: String = "Movie",
    ) = JellyfinItem(
        id = id,
        libraryId = null,
        name = id,
        sortName = null,
        overview = null,
        type = type,
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = positionTicks,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

    private fun detail() =
        JellyfinItemDetail(
            id = "movie",
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
            mediaSources = emptyList(),
        )
}
