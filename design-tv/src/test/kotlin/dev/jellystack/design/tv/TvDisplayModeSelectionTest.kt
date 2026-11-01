package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvDisplayModeSelectionTest {
    private fun hd(
        id: Int,
        hz: Float,
    ) = TvDisplayMode(id, 1920, 1080, hz)

    private fun uhd(
        id: Int,
        hz: Float,
    ) = TvDisplayMode(id, 3840, 2160, hz)

    private val uhd60 = uhd(1, 60f)

    private fun select(
        frameRate: Float?,
        vararg hz: Float,
        current: TvDisplayMode = uhd60,
    ): Float? =
        selectMatchingDisplayMode(
            current,
            hz.mapIndexed { index, rate -> uhd(index + 1, rate) },
            frameRate,
        )?.refreshRate

    @Test
    fun filmRateWithoutNtscModesPicksTheLowestIntegerMultiple() {
        assertEquals(24f, select(24f, 24f, 48f, 60f, 120f))
        assertEquals(48f, select(24f, 48f, 60f, 120f))
        assertEquals(120f, select(24f, 50f, 60f, 120f))
    }

    @Test
    fun ntscFilmRateAcceptsTheNtscMultiples() {
        assertEquals(23.976f, select(23.976f, 23.976f, 47.952f, 59.94f))
        assertEquals(47.95f, select(23.976f, 47.95f, 59.94f))
        assertEquals(119.88f, select(23.976f, 59.94f, 119.88f))
    }

    @Test
    fun exactMatchBeatsACloseOneEvenWhenTheCloseOneIsALowerMultiple() {
        assertEquals(23.976f, select(23.976f, 24f, 23.976f))
        assertEquals(59.94f, select(29.97f, 30f, 59.94f))
    }

    @Test
    fun closeMatchIsUsedWhenTheDisplayHasNoExactMode() {
        assertEquals(24f, select(23.976f, 24f, 60f))
        assertEquals(30f, select(29.97f, 30f, 120f))
    }

    @Test
    fun pal25And30FpsPickTheirMultiples() {
        assertEquals(25f, select(25f, 25f, 50f, 100f))
        assertEquals(50f, select(25f, 50f, 60f))
        assertEquals(30f, select(30f, 30f, 60f))
        assertEquals(60f, select(30f, 24f, 60f, 120f))
        assertEquals(120f, select(30f, 24f, 50f, 120f))
    }

    @Test
    fun modesOfAnotherPhysicalResolutionAreIgnored() {
        val current = uhd(1, 60f)
        val supported = listOf(current, hd(2, 24f), hd(3, 48f))

        assertNull(selectMatchingDisplayMode(current, supported, 24f))
        assertEquals(uhd(4, 24f), selectMatchingDisplayMode(current, supported + uhd(4, 24f), 24f))
    }

    @Test
    fun currentModeIsReturnedWhenItIsAlreadyTheBestFit() {
        val current = uhd(1, 24f)

        assertEquals(current, selectMatchingDisplayMode(current, listOf(current, uhd(2, 60f)), 24f))
    }

    @Test
    fun noModeFitsMeansNoChange() {
        assertNull(select(24f, 50f, 60f))
        assertNull(select(24f))
        assertNull(select(24f, 0f, Float.NaN))
    }

    @Test
    fun unknownOrInvalidFrameRateMeansNoChange() {
        listOf(null, 0f, -24f, Float.NaN, Float.POSITIVE_INFINITY, 1f, 1_000f).forEach { frameRate ->
            assertNull(select(frameRate, 24f, 48f, 60f), "frame rate $frameRate")
        }
    }
}
