package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
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
    fun itemsWithoutPositionStartImmediatelyAndRouteOnlyAfterAStart() =
        runTest {
            launcher(ResumeMode.ASK).play(item(positionTicks = null))
            runCurrent()
            assertEquals(listOf(PlaybackStartPolicy.INHERIT), started.map { it.startPolicy })
            assertEquals(1, startedRoutes)

            launcher(ResumeMode.RESUME, startSucceeds = false).play(item(positionTicks = 7_540_000_000L))
            runCurrent()
            assertEquals("no server: no player route", 1, startedRoutes)
        }

    @Test
    fun missingDetailOrFailingDetailRequestStartsNothing() =
        runTest {
            val unstarted = item(positionTicks = null)
            launcher(ResumeMode.RESUME, detail = null).play(unstarted)
            launcher(ResumeMode.RESUME, detailFailure = IllegalStateException("offline")).play(unstarted)
            runCurrent()

            assertEquals(emptyList<PlaybackRequest>(), started)
            assertEquals(0, startedRoutes)
        }

    private fun TestScope.launcher(
        resumeMode: ResumeMode,
        startSucceeds: Boolean = true,
        detail: JellyfinItemDetail? = detail(),
        detailFailure: Exception? = null,
    ) = TvPlaybackLauncher(
        scope = this,
        loadDetail = { detailFailure?.let { throw it } ?: detail },
        starter =
            TvPlaybackStarter { request, _ ->
                if (startSucceeds) started += request
                startSucceeds
            },
        currentSettings = { AppSettings(resumeMode = resumeMode) },
        onStarted = { startedRoutes += 1 },
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
