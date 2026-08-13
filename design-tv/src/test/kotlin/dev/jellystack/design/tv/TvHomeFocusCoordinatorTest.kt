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
    fun heroDownTargetsFirstNonEmptyMediaRow() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        val move = coordinator.beginMove(TvHomeFocusOrigin.Hero, TvHomeVerticalDirection.DOWN)

        assertEquals(TvHomeFocusDestination.Row("portrait", 1, "portrait-1"), move?.destination)
    }

    @Test
    fun boundariesAreConsumedWithoutStartingFocusRequest() {
        val coordinator = TvHomeVerticalFocusCoordinator(rows)

        assertNull(coordinator.beginMove(TvHomeFocusOrigin.Hero, TvHomeVerticalDirection.UP))
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
        val stale = coordinator.beginMove(TvHomeFocusOrigin.Hero, TvHomeVerticalDirection.DOWN)!!

        coordinator.replaceRows(
            listOf(TvHomeFocusRow("new", lazyColumnIndex = 5, firstItemId = "new-1", landscape = false)),
        )

        assertFalse(coordinator.acceptCompletion(stale.requestId))
        assertEquals(
            TvHomeFocusDestination.Row("new", 5, "new-1"),
            coordinator.beginMove(TvHomeFocusOrigin.Hero, TvHomeVerticalDirection.DOWN)?.destination,
        )
    }
}
