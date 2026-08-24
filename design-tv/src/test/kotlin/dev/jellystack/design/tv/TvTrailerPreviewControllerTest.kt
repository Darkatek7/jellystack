package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvTrailerPreviewControllerTest {
    @Test
    fun clearingMatchingRequestStopsPlayerWhilePlayIsPreparingExactlyOnce() =
        runTest {
            val player = SuspendingPreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { source("trailer") },
                    player = player,
                    focusDelayMillis = 0,
                )
            val request = request(TvTrailerPreviewOwner.CARD, "movie")

            controller.focus(request)
            runCurrent()
            player.playStarted.await()

            controller.clearFocus(request)
            runCurrent()
            controller.release()

            assertEquals(1, player.cancellations)
            assertEquals(1, player.stops)
            assertEquals(TvTrailerPreviewState.Idle, controller.state.value)
        }

    @Test
    fun ownerCleanupOnlyStopsMatchingPreparingRequest() =
        runTest {
            val player = SuspendingPreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { source("trailer") },
                    player = player,
                    focusDelayMillis = 0,
                )

            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            runCurrent()
            player.playStarted.await()

            controller.clearFocus(TvTrailerPreviewOwner.HERO)
            runCurrent()
            assertEquals(0, player.cancellations)
            assertEquals(0, player.stops)

            controller.clearFocus(TvTrailerPreviewOwner.CARD)
            runCurrent()
            controller.release()

            assertEquals(1, player.cancellations)
            assertEquals(1, player.stops)
        }

    @Test
    fun staleRequestCleanupDoesNotStopCurrentPreparingRequest() =
        runTest {
            val player = SuspendingPreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { source("trailer") },
                    player = player,
                    focusDelayMillis = 0,
                )
            val current = request(TvTrailerPreviewOwner.CARD, "current")

            controller.focus(current)
            runCurrent()
            player.playStarted.await()

            controller.clearFocus(request(TvTrailerPreviewOwner.CARD, "stale"))
            runCurrent()
            assertEquals(0, player.cancellations)
            assertEquals(0, player.stops)

            controller.clearFocus(current)
            runCurrent()
            controller.release()

            assertEquals(1, player.cancellations)
            assertEquals(1, player.stops)
        }

    @Test
    fun releaseStopsPlayerWhilePlayIsPreparingExactlyOnce() =
        runTest {
            val player = SuspendingPreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { source("trailer") },
                    player = player,
                    focusDelayMillis = 0,
                )

            controller.focus(request(TvTrailerPreviewOwner.HERO, "movie"))
            runCurrent()
            player.playStarted.await()

            controller.release()
            runCurrent()

            assertEquals(1, player.cancellations)
            assertEquals(1, player.stops)
            assertEquals(TvTrailerPreviewState.Idle, controller.state.value)
        }

    @Test
    fun staleOwnerCleanupDoesNotClearNewerRequestForSameItem() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)
            val cardRequest = request(TvTrailerPreviewOwner.CARD, "same")
            val heroRequest = request(TvTrailerPreviewOwner.HERO, "same")

            controller.focus(cardRequest)
            controller.focus(heroRequest)
            controller.clearFocus(cardRequest)
            advanceTimeBy(3_000L)
            runCurrent()

            assertEquals(heroRequest, assertIs<TvTrailerPreviewState.Playing>(controller.state.value).request)
            assertEquals(0, player.stops)
            controller.release()
        }

    @Test
    fun staleDuplicateCardCleanupDoesNotClearTheFocusedCardInstance() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)
            val oldCard = request(TvTrailerPreviewOwner.CARD, "same").copy(presentationId = "continue:same")
            val focusedCard = request(TvTrailerPreviewOwner.CARD, "same").copy(presentationId = "latest:same")

            controller.focus(oldCard)
            controller.focus(focusedCard)
            controller.clearFocus(oldCard)
            advanceTimeBy(3_000L)
            runCurrent()

            assertEquals(focusedCard, assertIs<TvTrailerPreviewState.Playing>(controller.state.value).request)
            assertEquals(0, player.stops)
            controller.release()
        }

    @Test
    fun clearingHeroOwnerDoesNotCancelFocusedCardPreview() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)
            val cardRequest = request(TvTrailerPreviewOwner.CARD, "card")

            controller.focus(cardRequest)
            advanceTimeBy(3_000L)
            runCurrent()
            controller.clearFocus(TvTrailerPreviewOwner.HERO)

            assertEquals(cardRequest, assertIs<TvTrailerPreviewState.Playing>(controller.state.value).request)
            assertEquals(0, player.stops)
            controller.release()
        }

    @Test
    fun heroSlideChangeClearsAndRearmsHeroOwner() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { target -> source("${target.itemId}-trailer") }, player)
            val first = request(TvTrailerPreviewOwner.HERO, "first")
            val second = request(TvTrailerPreviewOwner.HERO, "second")

            controller.focus(first)
            advanceTimeBy(3_000L)
            runCurrent()
            controller.clearFocus(first)
            controller.focus(second)

            assertEquals(second, assertIs<TvTrailerPreviewState.Armed>(controller.state.value).request)
            assertEquals(1, player.stops)
            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(second, assertIs<TvTrailerPreviewState.Playing>(controller.state.value).request)
            assertEquals(listOf("first-trailer", "second-trailer"), player.played.map { it.item.id })
            controller.release()
        }

    @Test
    fun playerEventsOnlyExposeTerminalPreviewStates() {
        assertEquals(TvTrailerPreviewPlayerEvent.Completed, PlayerEvent.Completed.toTrailerPreviewEvent())
        assertNull(PlayerEvent.Error(IllegalStateException("recoverable")).toTrailerPreviewEvent())
        assertEquals(
            TvTrailerPreviewPlayerEvent.Failed,
            PlaybackState.PlaybackError("broken").toTrailerPreviewEvent(),
        )
        assertNull(PlayerEvent.Ready.toTrailerPreviewEvent())
    }

    @Test
    fun stableFocusStartsExactlyAtOnePointFiveSeconds() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)

            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            runCurrent()
            advanceTimeBy(1_499L)
            runCurrent()
            assertEquals(0, player.played.size)

            advanceTimeBy(1L)
            runCurrent()

            assertEquals(listOf("trailer"), player.played.map { it.item.id })
            assertIs<TvTrailerPreviewState.Playing>(controller.state.value)
            controller.release()
        }

    @Test
    fun backgroundRequiresFreshInteractionAndFreshDwellBeforePreviewCanRestart() =
        runTest {
            val player = FakePreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { source("trailer") },
                    player = player,
                    focusDelayMillis = 1_500L,
                )
            val request = request(TvTrailerPreviewOwner.CARD, "movie")

            controller.focus(request)
            advanceTimeBy(1_500L)
            runCurrent()
            assertEquals(1, player.played.size)

            controller.onBackgrounded()
            controller.focus(request)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, player.played.size)

            controller.onUserInteraction()
            controller.focus(request)
            advanceTimeBy(1_499L)
            runCurrent()
            assertEquals(1, player.played.size)
            advanceTimeBy(1L)
            runCurrent()

            assertEquals(2, player.played.size)
            controller.release()
        }

    @Test
    fun focusChangeCancelsStaleResolutionAndStopsCurrentPlayback() =
        runTest {
            val firstResult = CompletableDeferred<DetailTrailerSource.Local?>()
            val player = FakePreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = { previewTarget ->
                        if (previewTarget.itemId == "first") firstResult.await() else source("second-trailer")
                    },
                    player = player,
                )

            controller.focus(request(TvTrailerPreviewOwner.CARD, "first"))
            runCurrent()
            controller.focus(request(TvTrailerPreviewOwner.CARD, "second"))
            firstResult.complete(source("first-trailer"))
            advanceTimeBy(3_000L)
            runCurrent()

            assertEquals(listOf("second-trailer"), player.played.map { it.item.id })
            controller.clearFocus()
            assertEquals(1, player.stops)
            assertEquals(TvTrailerPreviewState.Idle, controller.state.value)
            controller.release()
        }

    @Test
    fun positiveAndNegativeResultsAreCachedUntilInvalidated() =
        runTest {
            var resolutions = 0
            val player = FakePreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = {
                        resolutions += 1
                        null
                    },
                    player = player,
                )

            repeat(2) {
                controller.focus(request(TvTrailerPreviewOwner.CARD, "missing"))
                advanceTimeBy(3_000L)
                runCurrent()
                controller.clearFocus()
            }
            assertEquals(1, resolutions)

            controller.invalidateCache()
            controller.focus(request(TvTrailerPreviewOwner.CARD, "missing"))
            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(2, resolutions)
            controller.release()
        }

    @Test
    fun disabledPreviewDoesNotResolveAndSoundUpdatesLive() =
        runTest {
            var resolutions = 0
            val player = FakePreviewPlayer()
            val controller =
                TvTrailerPreviewController(
                    scope = this,
                    resolve = {
                        resolutions += 1
                        source("trailer")
                    },
                    player = player,
                )

            controller.setEnabled(false)
            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(0, resolutions)

            controller.setEnabled(true)
            controller.setSoundEnabled(false)
            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            advanceTimeBy(3_000L)
            runCurrent()
            assertFalse(player.soundState)
            assertEquals(1, player.played.size)

            controller.setSoundEnabled(true)
            assertTrue(player.soundState)
            controller.release()
        }

    @Test
    fun completionAndFailureReturnToArtwork() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)
            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            advanceTimeBy(3_000L)
            runCurrent()

            player.events.emit(TvTrailerPreviewPlayerEvent.Completed)
            runCurrent()

            assertEquals(TvTrailerPreviewState.Idle, controller.state.value)
            assertEquals(1, player.stops)
            controller.release()
        }

    private fun target(id: String) =
        TvTrailerPreviewTarget(
            serverKey = "server",
            itemId = id,
            isEpisode = false,
            seriesId = null,
        )

    private fun request(
        owner: TvTrailerPreviewOwner,
        id: String,
    ) = TvTrailerPreviewRequest(owner, target(id))

    private fun source(id: String) = DetailTrailerSource.Local(item(id), detail(id))

    private fun detail(id: String) =
        JellyfinItemDetail(
            id,
            id,
            null,
            emptyList(),
            null,
            null,
            null,
            null,
            null,
            emptyList(),
            emptyList(),
            null,
            emptyList(),
            emptyList(),
            providerIds = emptyMap(),
        )

    private fun item(id: String) =
        JellyfinItem(
            id,
            null,
            id,
            null,
            null,
            "Trailer",
            "Video",
            null,
            emptyList(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
        )

    private class FakePreviewPlayer : TvTrailerPreviewPlayer {
        override val events = MutableSharedFlow<TvTrailerPreviewPlayerEvent>()
        val played = mutableListOf<DetailTrailerSource.Local>()
        var stops = 0
        var soundState = true

        override suspend fun play(source: DetailTrailerSource.Local): Boolean {
            played += source
            return true
        }

        override fun stop() {
            stops += 1
        }

        override fun setSoundEnabled(enabled: Boolean) {
            soundState = enabled
        }
    }

    private class SuspendingPreviewPlayer : TvTrailerPreviewPlayer {
        override val events = MutableSharedFlow<TvTrailerPreviewPlayerEvent>()
        val playStarted = CompletableDeferred<Unit>()
        private val playResult = CompletableDeferred<Boolean>()
        var cancellations = 0
        var stops = 0

        override suspend fun play(source: DetailTrailerSource.Local): Boolean {
            playStarted.complete(Unit)
            return try {
                playResult.await()
            } catch (cancellation: CancellationException) {
                cancellations += 1
                throw cancellation
            }
        }

        override fun stop() {
            stops += 1
        }

        override fun setSoundEnabled(enabled: Boolean) = Unit
    }
}
