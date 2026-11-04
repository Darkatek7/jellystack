package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvHomeRowsTest {
    @Test
    fun indicatorShowsEveryPageOfAShortCarousel() {
        val dots = tvHomeHeroIndicatorDots(TvHomeHeroPage(index = 1, count = 3))

        assertEquals(listOf(0, 1, 2), dots.map { it.pageIndex })
        assertEquals(TvHeroIndicatorDotSize.SELECTED, dots[1].size)
        assertEquals(2, dots.count { it.size == TvHeroIndicatorDotSize.NORMAL })
    }

    @Test
    fun indicatorWindowFollowsTheSelectedPageOfALongCarousel() {
        fun window(index: Int) = tvHomeHeroIndicatorDots(TvHomeHeroPage(index, count = 10)).map { it.pageIndex }

        assertEquals(listOf(0, 1, 2, 3, 4), window(0))
        assertEquals(listOf(0, 1, 2, 3, 4), window(2))
        assertEquals(listOf(3, 4, 5, 6, 7), window(5))
        assertEquals(listOf(5, 6, 7, 8, 9), window(9))
    }

    @Test
    fun indicatorShrinksOuterDotsOnlyWherePagesLieBeyond() {
        fun sizes(index: Int) = tvHomeHeroIndicatorDots(TvHomeHeroPage(index, count = 10)).map { it.size }
        val small = TvHeroIndicatorDotSize.SMALL
        val normal = TvHeroIndicatorDotSize.NORMAL
        val selected = TvHeroIndicatorDotSize.SELECTED

        assertEquals(listOf(selected, normal, normal, normal, small), sizes(0))
        assertEquals(listOf(small, normal, selected, normal, small), sizes(5))
        assertEquals(listOf(small, normal, normal, normal, selected), sizes(9))
    }
}
