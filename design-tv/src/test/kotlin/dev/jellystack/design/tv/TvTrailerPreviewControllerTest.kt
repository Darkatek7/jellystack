package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerEvent
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
    fun stableFocusStartsExactlyAtThreeSeconds() =
        runTest {
            val player = FakePreviewPlayer()
            val controller = TvTrailerPreviewController(this, { source("trailer") }, player)

            controller.focus(request(TvTrailerPreviewOwner.CARD, "movie"))
            runCurrent()
            advanceTimeBy(2_999L)
            runCurrent()
            assertEquals(0, player.played.size)

            advanceTimeBy(1L)
            runCurrent()

            assertEquals(listOf("trailer"), player.played.map { it.item.id })
            assertIs<TvTrailerPreviewState.Playing>(controller.state.value)
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
}
