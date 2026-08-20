package dev.jellystack.design.tv

import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackSegmentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvPlaybackSegmentIntegrationTest {
    @Test
    fun standalonePromptExpiresExactlyEightSecondsAfterActionEntry() =
        runTest {
            val coordinator = TvPlaybackPromptCoordinator(this)

            coordinator.onActionsChanged(listOf("segment:intro"))
            assertEquals(setOf("segment:intro"), coordinator.state.value.visibleActionIds)

            advanceTimeBy(7_999L)
            runCurrent()
            assertEquals(setOf("segment:intro"), coordinator.state.value.visibleActionIds)

            advanceTimeBy(1L)
            runCurrent()
            assertTrue(coordinator.state.value.visibleActionIds.isEmpty())
        }

    @Test
    fun newlyAvailableActionRestartsPromptWithoutRevivingRemovedActions() =
        runTest {
            val coordinator = TvPlaybackPromptCoordinator(this)

            coordinator.onActionsChanged(listOf("segment:outro"))
            advanceTimeBy(6_000L)
            coordinator.onActionsChanged(listOf("play-next"))

            assertEquals(setOf("play-next"), coordinator.state.value.visibleActionIds)
            advanceTimeBy(7_999L)
            runCurrent()
            assertEquals(setOf("play-next"), coordinator.state.value.visibleActionIds)
            advanceTimeBy(1L)
            runCurrent()
            assertTrue(coordinator.state.value.visibleActionIds.isEmpty())
        }

    @Test
    fun outroSkipAndPlayNextAreIndependentSimultaneousActions() {
        val segmentState =
            PlaybackSegmentState(
                mediaId = "episode-1",
                activeSegments = listOf(outroSegment()),
                actions = listOf(outroAction()),
            )
        val continuation =
            PlaybackContinuationState(
                mediaId = "episode-1",
                nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
            )

        val actions = tvPlaybackActionModels(segmentState, continuation, isEpisode = true, strings())

        assertEquals(listOf(TvPlaybackActionKind.SEGMENT_SKIP, TvPlaybackActionKind.PLAY_NEXT), actions.map { it.kind })
        assertEquals(listOf("Skip credits", "Play next episode"), actions.map { it.label })
        assertEquals(
            listOf(
                "tv-player-action:segment:outro:outro-1",
                "tv-player-action:play-next:episode-2",
            ),
            actions.map { it.id },
        )
    }

    @Test
    fun playNextRemainsWhenOutroSkipModeProducedNoSkipAction() {
        val segmentState =
            PlaybackSegmentState(
                mediaId = "episode-1",
                activeSegments = listOf(outroSegment()),
            )
        val continuation =
            PlaybackContinuationState(
                mediaId = "episode-1",
                nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
            )

        val actions = tvPlaybackActionModels(segmentState, continuation, isEpisode = true, strings())

        assertEquals(listOf(TvPlaybackActionKind.PLAY_NEXT), actions.map { it.kind })
    }

    @Test
    fun playNextIsAbsentForMoviesAndLastEpisodes() {
        val segmentState =
            PlaybackSegmentState(
                mediaId = "item-1",
                activeSegments = listOf(outroSegment()),
            )
        val availableNext =
            PlaybackContinuationState(
                mediaId = "item-1",
                nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
            )

        assertTrue(tvPlaybackActionModels(segmentState, availableNext, isEpisode = false, strings()).isEmpty())
        assertTrue(tvPlaybackActionModels(segmentState, PlaybackContinuationState(), isEpisode = true, strings()).isEmpty())
    }

    @Test
    fun segmentSeekUsesSyncPlayOnlyWhileAGroupIsActive() {
        val events = mutableListOf<String>()

        routeTvSegmentSeek(
            positionMs = 42_000L,
            syncPlayActive = true,
            requestSyncSeek = { events += "sync:$it" },
            requestLocalSeek = { events += "local:$it" },
        )
        routeTvSegmentSeek(
            positionMs = 84_000L,
            syncPlayActive = false,
            requestSyncSeek = { events += "sync:$it" },
            requestLocalSeek = { events += "local:$it" },
        )

        assertEquals(listOf("sync:42000", "local:84000"), events)
    }

    @Test
    fun playNextUsesSyncPlayOnlyWhileAGroupIsActive() =
        runTest {
            val events = mutableListOf<String>()

            routeTvPlayNext(
                syncPlayActive = true,
                requestSyncNext = { events += "sync" },
                requestLocalNext = { events += "local" },
            )
            routeTvPlayNext(
                syncPlayActive = false,
                requestSyncNext = { events += "sync" },
                requestLocalNext = { events += "local" },
            )

            assertEquals(listOf("sync", "local"), events)
        }

    @Test
    fun allFivePlaybackSegmentSettingsDispatchTheirOwnMode() {
        val events = mutableListOf<Pair<PlaybackSegmentType, SegmentSkipMode>>()
        val models =
            tvSegmentSkipSettingModels(
                settings = AppSettings(),
                strings = strings(),
                onModeSelected = { type, mode -> events += type to mode },
            )

        assertEquals(
            listOf(
                PlaybackSegmentType.INTRO,
                PlaybackSegmentType.RECAP,
                PlaybackSegmentType.OUTRO,
                PlaybackSegmentType.PREVIEW,
                PlaybackSegmentType.COMMERCIAL,
            ),
            models.map { it.type },
        )
        assertEquals(
            listOf("Intros", "Recaps", "Credits", "Previews", "Commercials"),
            models.map { it.title },
        )

        models.forEach { it.onModeSelected(SegmentSkipMode.AUTO_SKIP) }

        assertEquals(models.map { it.type to SegmentSkipMode.AUTO_SKIP }, events)
    }

    private fun strings() = TvStrings.current(AppLanguage.ENGLISH)

    private fun outroSegment() =
        PlaybackSegment(
            id = "outro-1",
            type = PlaybackSegmentType.OUTRO,
            startPositionMs = 80_000L,
            endPositionMs = 90_000L,
        )

    private fun outroAction() =
        PlaybackSegmentAction(
            mediaId = "episode-1",
            segmentId = "outro-1",
            type = PlaybackSegmentType.OUTRO,
            endPositionMs = 90_000L,
        )
}
