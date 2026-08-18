package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvHomeFocusCoordinatorTest {
    private val rows =
        listOf(
            TvHomeFocusRow("portrait", lazyColumnIndex = 1, itemIds = listOf("portrait-1", "portrait-2"), landscape = false),
            TvHomeFocusRow("empty-gap", lazyColumnIndex = 2, itemIds = emptyList(), landscape = true),
            TvHomeFocusRow(
                "landscape",
                lazyColumnIndex = 3,
                itemIds = listOf("landscape-1", "landscape-2", "landscape-3"),
                landscape = true,
            ),
        )

    @Test
    fun downTargetsFirstItemInNextNonEmptyRowAcrossMixedLayouts() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val move = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait", "portrait-1"), TvHomeVerticalDirection.DOWN)

        assertEquals(TvHomeFocusDestination.Row("landscape", 3, "landscape-1", 0), move?.destination)
    }

    @Test
    fun upTargetsPreviousNonEmptyRowThenHeroFromFirstRow() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val previous = coordinator.beginMove(TvHomeFocusOrigin.Row("landscape", "landscape-1"), TvHomeVerticalDirection.UP)
        val hero = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait", "portrait-1"), TvHomeVerticalDirection.UP)

        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-1", 0), previous?.destination)
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
        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-1", 0), row?.destination)
    }

    @Test
    fun rowDownBoundaryDoesNotStartFocusRequest() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        assertNull(coordinator.beginMove(TvHomeFocusOrigin.Row("landscape", "landscape-1"), TvHomeVerticalDirection.DOWN))
    }

    @Test
    fun newerMoveCancelsStaleCompletion() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val stale = coordinator.beginMove(TvHomeFocusOrigin.Row("portrait", "portrait-1"), TvHomeVerticalDirection.DOWN)!!
        val current = coordinator.beginMove(TvHomeFocusOrigin.Row("landscape", "landscape-1"), TvHomeVerticalDirection.UP)!!

        assertFalse(coordinator.acceptCompletion(stale.requestId))
        assertTrue(coordinator.acceptCompletion(current.requestId))
    }

    @Test
    fun repeatedMoveBeforeCompletionReusesPendingRequestAndCancelsPreviewOnce() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        var cancellations = 0

        val first =
            coordinator.beginMove(TvHomeFocusOrigin.Row("portrait", "portrait-1"), TvHomeVerticalDirection.DOWN) {
                cancellations += 1
            }
        val repeated =
            coordinator.beginMove(TvHomeFocusOrigin.Row("portrait", "portrait-1"), TvHomeVerticalDirection.DOWN) {
                cancellations += 1
            }

        assertSame(first, repeated)
        assertEquals(1, cancellations)
    }

    @Test
    fun focusTargetRegistryPreservesStableRequestersAcrossRowRefreshes() {
        val registry = TvHomeFocusTargetRegistry { Any() }
        val original = registry.reconcile(rows).getValue("portrait").getValue("portrait-2")

        val refreshed =
            registry.reconcile(
                listOf(
                    rows.first().copy(lazyColumnIndex = 2),
                    TvHomeFocusRow("new", 3, listOf("new-1"), landscape = true),
                ),
            )

        assertSame(original, refreshed.getValue("portrait").getValue("portrait-2"))
    }

    @Test
    fun heldVerticalKeyRepeatsAreConsumedWithoutStartingAnotherMove() {
        assertTrue(shouldHandleTvHomeVerticalKey(repeatCount = 0))
        assertFalse(shouldHandleTvHomeVerticalKey(repeatCount = 1))
    }

    @Test
    fun unrelatedModelReplacementPreservesPendingRequest() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val pending = coordinator.beginMove(TvHomeFocusOrigin.HeroActions, TvHomeVerticalDirection.DOWN)!!

        val reconciled = coordinator.replaceRows(rows.map { it.copy() })

        assertEquals(pending, reconciled)
        assertTrue(coordinator.acceptCompletion(pending.requestId))
    }

    @Test
    fun verticalMovePreservesHorizontalPositionAndClampsAtRowEnd() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val preserved =
            coordinator.beginMove(
                TvHomeFocusOrigin.Row("portrait", "portrait-2"),
                TvHomeVerticalDirection.DOWN,
            )
        val clamped =
            coordinator.beginMove(
                TvHomeFocusOrigin.Row("landscape", "landscape-3"),
                TvHomeVerticalDirection.UP,
            )

        assertEquals(TvHomeFocusDestination.Row("landscape", 3, "landscape-2", 1), preserved?.destination)
        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-2", 1), clamped?.destination)
    }

    @Test
    fun pendingMoveReconcilesToNearestStableTargetWhenAsyncRowsChange() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val pending =
            coordinator.beginMove(
                TvHomeFocusOrigin.Row("portrait", "portrait-2"),
                TvHomeVerticalDirection.DOWN,
            )!!

        val reconciled =
            coordinator.replaceRows(
                listOf(
                    rows.first(),
                    TvHomeFocusRow("replacement", 4, listOf("replacement-1"), landscape = true),
                ),
            )

        assertEquals(pending.requestId, reconciled?.requestId)
        assertEquals(TvHomeFocusDestination.Row("replacement", 4, "replacement-1", 0), reconciled?.destination)
        assertTrue(coordinator.acceptCompletion(pending.requestId))
    }

    @Test
    fun acceptedMoveCancelsPreviewBeforeItIsDispatched() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)
        val events = mutableListOf<String>()

        val move =
            coordinator.beginMove(
                origin = TvHomeFocusOrigin.Row("portrait", "portrait-1"),
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
                origin = TvHomeFocusOrigin.Row("landscape", "landscape-1"),
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
