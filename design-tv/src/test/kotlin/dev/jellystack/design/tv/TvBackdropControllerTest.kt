package dev.jellystack.design.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TvBackdropControllerTest {
    @Test
    fun newerFocusCancelsPendingLoadAndSuccessfulReplacementCrossfades() =
        runTest {
            val loads = mutableListOf<String>()
            val controller =
                TvBackdropController(
                    scope = this,
                    imageLoader =
                        TvBackdropImageLoader { url ->
                            loads += url
                            true
                        },
                )

            controller.focus(card("a"))
            advanceTimeBy(100)
            controller.focus(card("b"))
            advanceTimeBy(119)
            assertEquals(emptyList(), loads)
            advanceUntilIdle()

            assertEquals(listOf("backdrop-b"), loads)
            assertEquals("backdrop-b", controller.state.value.url)
            assertNull(controller.state.value.previousUrl)

            controller.focus(card("c"))
            advanceUntilIdle()

            assertEquals("backdrop-c", controller.state.value.url)
            assertEquals("backdrop-b", controller.state.value.previousUrl)
            assertEquals(TV_BACKDROP_CROSSFADE_MILLIS, controller.state.value.transitionMillis)
        }

    @Test
    fun failedReplacementRetainsPreviousSuccessfulArtwork() =
        runTest {
            val controller =
                TvBackdropController(
                    scope = this,
                    imageLoader = TvBackdropImageLoader { url -> url != "backdrop-b" },
                )

            controller.focus(card("a"))
            advanceUntilIdle()
            controller.focus(card("b"))
            advanceUntilIdle()

            assertEquals("backdrop-a", controller.state.value.url)
            assertNull(controller.state.value.previousUrl)
        }

    @Test
    fun reducedMotionSwapsWithoutScaleOrCrossfade() =
        runTest {
            val controller =
                TvBackdropController(
                    scope = this,
                    imageLoader = TvBackdropImageLoader { true },
                    reducedMotion = { true },
                )

            controller.focus(card("a"))
            advanceUntilIdle()
            controller.focus(card("b"))
            advanceUntilIdle()

            assertEquals(0, controller.state.value.transitionMillis)
            assertEquals(1f, tvCinematicMotion(reducedMotion = true, highContrastFocus = false).focusScale)
        }

    private fun card(id: String) =
        TvCinematicCard(
            id = id,
            title = "Title $id",
            artworkUrl = "art-$id",
            backdropUrl = "backdrop-$id",
        )
}
