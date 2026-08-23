package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvFocusMemoryTest {
    @Test
    fun restoresRememberedItemWhenItStillExists() {
        val memory = TvFocusMemory()
        memory.remember("home", "continue", "episode-2", verticalIndex = 3, horizontalIndex = 1)

        assertEquals("episode-2", memory.resolveItem("home", listOf("episode-1", "episode-2")))
        assertEquals(3, memory.restore("home")?.verticalIndex)
    }

    @Test
    fun exactSemanticSectionAndItemWinsAfterSectionsReorder() {
        val memory = TvFocusMemory()
        val remembered = TvFocusAnchor("cast", "person-2", TvFocusDestination.SECTION_ITEM)
        memory.remember("detail", remembered, horizontalCenter = 600f, horizontalIndex = 2)

        assertEquals(
            remembered,
            memory.resolve(
                "detail",
                listOf(
                    TvFocusTarget(TvFocusAnchor("similar", "movie-1", TvFocusDestination.SECTION_ITEM), 200f, 0),
                    TvFocusTarget(remembered, 900f, 4),
                    TvFocusTarget(TvFocusAnchor("cast", "person-3", TvFocusDestination.SECTION_ITEM), 610f, 1),
                ),
            ),
        )
    }

    @Test
    fun removedItemUsesNearestSurvivingHorizontalCenterInSameSection() {
        val memory = TvFocusMemory()
        memory.remember(
            "detail",
            TvFocusAnchor("episodes", "removed", TvFocusDestination.SECTION_ITEM),
            horizontalCenter = 640f,
            horizontalIndex = 3,
        )

        assertEquals(
            "episode-near",
            memory
                .resolve(
                    "detail",
                    listOf(
                        TvFocusTarget(
                            TvFocusAnchor("cast", "person", TvFocusDestination.SECTION_ITEM),
                            horizontalCenter = 641f,
                            horizontalIndex = 0,
                        ),
                        TvFocusTarget(
                            TvFocusAnchor("episodes", "episode-far", TvFocusDestination.SECTION_ITEM),
                            horizontalCenter = 200f,
                            horizontalIndex = 1,
                        ),
                        TvFocusTarget(
                            TvFocusAnchor("episodes", "episode-near", TvFocusDestination.SECTION_ITEM),
                            horizontalCenter = 620f,
                            horizontalIndex = 4,
                        ),
                    ),
                )?.itemId,
        )
    }

    @Test
    fun missingSectionFallsBackToFirstActionableTarget() {
        val memory = TvFocusMemory()
        memory.remember(
            "detail",
            TvFocusAnchor("removed-section", "removed", TvFocusDestination.SECTION_ITEM),
            horizontalCenter = 400f,
            horizontalIndex = 2,
        )

        assertEquals(
            "first-action",
            memory
                .resolve(
                    "detail",
                    listOf(
                        TvFocusTarget(
                            TvFocusAnchor("loading", "status", TvFocusDestination.BODY),
                            horizontalCenter = 0f,
                            horizontalIndex = 0,
                            actionable = false,
                        ),
                        TvFocusTarget(
                            TvFocusAnchor("actions", "first-action", TvFocusDestination.PRIMARY_ACTION),
                            horizontalCenter = 100f,
                            horizontalIndex = 0,
                        ),
                    ),
                )?.itemId,
        )
    }

    @Test
    fun removedItemFallsBackToNearestRememberedIndex() {
        val memory = TvFocusMemory()
        memory.remember("library:movies", "grid", "removed", horizontalIndex = 2)

        assertEquals("movie-3", memory.resolveItem("library:movies", listOf("movie-1", "movie-2", "movie-3")))
        assertNull(memory.resolveItem("missing", emptyList()))
    }

    @Test
    fun libraryPathsKeepIndependentGridSnapshots() {
        val memory = TvFocusMemory()
        val seasonOne = TvRoute.Library("shows").focusRouteKey(listOf("show", "season-1"))
        val seasonTwo = TvRoute.Library("shows").focusRouteKey(listOf("show", "season-2"))
        memory.remember(seasonOne, "grid", "episode-2", horizontalIndex = 1)
        memory.remember(seasonTwo, "grid", "episode-8", horizontalIndex = 7)

        assertEquals("episode-2", memory.restore(seasonOne)?.itemId)
        assertEquals("episode-8", memory.restore(seasonTwo)?.itemId)
    }
}
