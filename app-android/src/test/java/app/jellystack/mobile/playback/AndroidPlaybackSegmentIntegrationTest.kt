package app.jellystack.mobile.playback

import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackPhase
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
class AndroidPlaybackSegmentIntegrationTest {
    @Test
    fun standaloneActionGetsFullEightSecondsWhenControlsHide() =
        runTest {
            val coordinator = AndroidPlaybackPromptCoordinator(this)

            coordinator.onPresentationChanged(listOf("segment:intro"), controlsVisible = true)
            advanceTimeBy(5_000L)
            runCurrent()
            assertTrue(
                coordinator.state.value.visibleActionIds
                    .isEmpty(),
            )

            coordinator.onPresentationChanged(listOf("segment:intro"), controlsVisible = false)
            assertEquals(setOf("segment:intro"), coordinator.state.value.visibleActionIds)

            advanceTimeBy(7_999L)
            runCurrent()
            assertEquals(setOf("segment:intro"), coordinator.state.value.visibleActionIds)
            advanceTimeBy(1L)
            runCurrent()
            assertTrue(
                coordinator.state.value.visibleActionIds
                    .isEmpty(),
            )
        }

    @Test
    fun laterOverlappingActionGetsItsOwnStandaloneWindow() =
        runTest {
            val coordinator = AndroidPlaybackPromptCoordinator(this)

            coordinator.onPresentationChanged(listOf("segment:intro"), controlsVisible = false)
            advanceTimeBy(4_000L)
            runCurrent()
            coordinator.onPresentationChanged(
                listOf("segment:intro", "segment:recap"),
                controlsVisible = false,
            )

            advanceTimeBy(4_000L)
            runCurrent()
            assertEquals(setOf("segment:recap"), coordinator.state.value.visibleActionIds)
            advanceTimeBy(3_999L)
            runCurrent()
            assertEquals(setOf("segment:recap"), coordinator.state.value.visibleActionIds)
            advanceTimeBy(1L)
            runCurrent()
            assertTrue(
                coordinator.state.value.visibleActionIds
                    .isEmpty(),
            )
        }

    @Test
    fun overlappingButtonsAndPreparedOutroTargetRemainDistinctActions() {
        val intro = PlaybackSegment("intro", PlaybackSegmentType.INTRO, 0L, 20_000L)
        val recap = PlaybackSegment("recap", PlaybackSegmentType.RECAP, 5_000L, 25_000L)
        val outro = PlaybackSegment("outro", PlaybackSegmentType.OUTRO, 10_000L, 30_000L)
        val segmentState =
            PlaybackSegmentState(
                mediaId = "episode-1",
                activeSegments = listOf(intro, recap, outro),
                actions =
                    listOf(
                        PlaybackSegmentAction("episode-1", "intro", PlaybackSegmentType.INTRO, 20_000L),
                        PlaybackSegmentAction("episode-1", "recap", PlaybackSegmentType.RECAP, 25_000L),
                        PlaybackSegmentAction("episode-1", "outro", PlaybackSegmentType.OUTRO, 30_000L),
                    ),
            )
        val continuation =
            PlaybackContinuationState(
                mediaId = "episode-1",
                nextTarget = PlaybackContinuationTarget("episode-2", "Episode 2") {},
            )

        val actions =
            androidPlaybackActionModels(
                segmentState = segmentState,
                continuationState = continuation,
                isEpisode = true,
                playbackPhase = PlaybackPhase.Ready,
                labels = labels,
            )

        assertEquals(
            listOf("Skip intro", "Skip recap", "Skip credits", "Play next episode"),
            actions.map { it.label },
        )
        assertEquals(4, actions.map { it.id }.distinct().size)
    }

    @Test
    fun actionRouterSamplesCurrentSyncPlayStateAtEachInvocation() =
        runTest {
            var syncPlayActive = false
            val events = mutableListOf<String>()
            val router =
                AndroidPlaybackCommandRouter(
                    isSyncPlayActive = { syncPlayActive },
                    requestSyncSeek = { events += "sync-seek:$it" },
                    requestPlaybackSeek = { events += "playback-seek:$it" },
                    requestSyncNext = { events += "sync-next" },
                )

            router.seekTo(20_000L)
            syncPlayActive = true
            router.playNext { events += "playback-next" }
            syncPlayActive = false
            router.playNext { events += "playback-next" }

            assertEquals(
                listOf("playback-seek:20000", "sync-next", "playback-next"),
                events,
            )
        }

    private companion object {
        val labels =
            AndroidPlaybackActionLabels(
                skipIntro = "Skip intro",
                skipRecap = "Skip recap",
                skipPreview = "Skip preview",
                skipCommercial = "Skip commercial",
                skipCredits = "Skip credits",
                playNextEpisode = "Play next episode",
            )
    }
}
