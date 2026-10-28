package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
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

    private fun TestScope.launcher(
        resumeMode: ResumeMode,
        startSucceeds: Boolean = true,
        loadDetail: suspend () -> JellyfinItemDetail? = { detail() },
        startGate: CompletableDeferred<Unit>? = null,
    ) = TvPlaybackLauncher(
        scope = this,
        loadDetail = { loadDetail() },
        starter =
            TvPlaybackStarter { request, _ ->
                startGate?.await()
                if (startSucceeds) started += request
                startSucceeds
            },
        currentSettings = { AppSettings(resumeMode = resumeMode) },
        openPlayer = { startedRoutes += 1 },
    )

    private fun item(positionTicks: Long?) =
        JellyfinItem(
            id = "movie",
            libraryId = null,
            name = "Movie",
            sortName = null,
            overview = null,
            type = "Movie",
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
