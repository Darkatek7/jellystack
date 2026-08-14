package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvHomeFocusCoordinatorTest {
    private val rows =
        listOf(
            TvHomeFocusRow("portrait", lazyColumnIndex = 1, firstItemId = "portrait-1", landscape = false),
            TvHomeFocusRow("empty-gap", lazyColumnIndex = 2, firstItemId = null, landscape = true),
            TvHomeFocusRow("landscape", lazyColumnIndex = 3, firstItemId = "landscape-1", landscape = true),
        )

    @Test
    fun downTargetsFirstItemInNextNonEmptyRowAcrossMixedLayouts() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val move = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait"), TvHomeVerticalDirection.DOWN)

        assertEquals(TvHomeFocusDestination.Row("landscape", 3, "landscape-1"), move?.destination)
    }

    @Test
    fun upTargetsPreviousNonEmptyRowThenHeroFromFirstRow() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val previous = coordinator.beginMove(TvHomeFocusOrigin.Row("landscape"), TvHomeVerticalDirection.UP)
        val hero = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait"), TvHomeVerticalDirection.UP)

        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-1"), previous?.destination)
        assertEquals(TvHomeFocusDestination.HeroPrimary, hero?.destination)
    }

    @Test
    fun heroCarouselDownTargetsPrimaryAction() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val move = coordinator.beginMove(TvHomeFocusOrigin.HeroCarousel, TvHomeVerticalDirection.DOWN)

        assertEquals(TvHomeFocusDestination.HeroPrimary, move?.destination)
    }

    @Test
    fun heroCarouselUpDoesNotStartFocusRequest() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        assertNull(coordinator.beginMove(TvHomeFocusOrigin.HeroCarousel, TvHomeVerticalDirection.UP))
    }

    @Test
    fun heroActionsUpTargetsCarouselAndDownTargetsFirstNonEmptyMediaRow() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val carousel = coordinator.beginMove(TvHomeFocusOrigin.HeroActions, TvHomeVerticalDirection.UP)
        val row = coordinator.beginMove(TvHomeFocusOrigin.HeroActions, TvHomeVerticalDirection.DOWN)

        assertEquals(TvHomeFocusDestination.HeroCarousel, carousel?.destination)
        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-1"), row?.destination)
    }

    @Test
    fun rowDownBoundaryDoesNotStartFocusRequest() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        assertNull(coordinator.beginMove(TvHomeFocusOrigin.Row("landscape"), TvHomeVerticalDirection.DOWN))
    }

    @Test
    fun newerMoveCancelsStaleCompletion() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val stale = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait"), TvHomeVerticalDirection.DOWN)!!
        val current = coordinator.beginMove(TvHomeFocusOrigin.Row("landscape"), TvHomeVerticalDirection.UP)!!

        assertFalse(coordinator.acceptCompletion(stale.requestId))
        assertTrue(coordinator.acceptCompletion(current.requestId))
    }

    @Test
    fun modelReplacementCancelsPendingRequestAndUsesNewIndices() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val stale = coordinator.beginMove(TvHomeFocusOrigin.HeroActions, TvHomeVerticalDirection.DOWN)!!

        coordinator.replaceRows(
            listOf(TvHomeFocusRow("new", lazyColumnIndex = 5, firstItemId = "new-1", landscape = false)),
        )

        assertFalse(coordinator.acceptCompletion(stale.requestId))
        assertEquals(
            TvHomeFocusDestination.Row("new", 5, "new-1"),
            coordinator.beginMove(TvHomeFocusOrigin.HeroActions, TvHomeVerticalDirection.DOWN)?.destination,
        )
    }

    @Test
    fun acceptedMoveCancelsPreviewBeforeItIsDispatched() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val events = mutableListOf<String>()

        val move =
            coordinator.beginMove(
                origin = TvHomeFocusOrigin.Row("portrait"),
                direction = TvHomeVerticalDirection.DOWN,
                onAccepted = { events += "cancel" },
            )
        if (move != null) events += "dispatch"

        assertEquals(listOf("cancel", "dispatch"), events)
    }

    @Test
    fun boundaryMoveDoesNotCancelPreview() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        var cancellations = 0

        val move =
            coordinator.beginMove(
                origin = TvHomeFocusOrigin.Row("landscape"),
                direction = TvHomeVerticalDirection.DOWN,
                onAccepted = { cancellations += 1 },
            )

        assertNull(move)
        assertEquals(0, cancellations)
    }

    @Test
    fun acceptedMovesCancelPreviewExactlyOnce() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        var cancellations = 0

        val move =
            coordinator.beginMove(
                origin = TvHomeFocusOrigin.HeroActions,
                direction = TvHomeVerticalDirection.UP,
                onAccepted = { cancellations += 1 },
            )

        assertEquals(TvHomeFocusDestination.HeroCarousel, move?.destination)
        assertEquals(1, cancellations)
    }
}
