package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TvRouteFocusForgettingTest {
    private val detail = TvRoute.JellyfinDetail("movie-1")
    private val other = TvRoute.JellyfinDetail("movie-2")

    private fun TvAppStateHolder.focusOn(route: TvRoute) {
        focusMemory.remember(route.focusRouteKey(), "similar", "movie-9", horizontalIndex = 4)
        focusMemory.rows.remember(route.focusRouteKey(), "similar", "movie-9")
    }

    @Test
    fun reopeningADetailPageAfterBackStartsWithoutRememberedFocus() {
        val holder = TvAppStateHolder()
        val forgotten = mutableListOf<String>()
        holder.onRouteForgotten = forgotten::add
        holder.push(detail)
        holder.focusOn(detail)

        holder.popRoute()
        holder.push(detail)

        assertNull(holder.focusMemory.restore(detail.focusRouteKey()))
        assertEquals(0, holder.focusMemory.rows.landingIndex(detail.focusRouteKey(), "similar", listOf("a", "movie-9")))
        assertEquals(listOf(detail.focusRouteKey()), forgotten)
    }

    @Test
    fun backFromAPageOpenedOnTopOfADetailKeepsTheDetailsFocus() {
        val holder = TvAppStateHolder()
        val person = TvRoute.Person("person-1", "Alex Example")
        holder.push(detail)
        holder.focusOn(detail)
        holder.push(person)
        holder.focusOn(person)

        holder.popRoute()

        assertNotNull(holder.focusMemory.restore(detail.focusRouteKey()))
        assertNull(holder.focusMemory.restore(person.focusRouteKey()))
    }

    @Test
    fun aDetailPageThatReplacesAnotherForgetsTheReplacedOne() {
        val holder = TvAppStateHolder()
        holder.push(detail)
        holder.focusOn(detail)

        holder.push(other)

        assertNull(holder.focusMemory.restore(detail.focusRouteKey()))
    }

    @Test
    fun aPageStillLowerInTheBackStackKeepsItsFocusWhenTheSameRouteAboveItCloses() {
        val holder = TvAppStateHolder()
        val person = TvRoute.Person("person-1", "Alex Example")
        holder.push(detail)
        holder.push(person)
        holder.push(TvRoute.Collection("collection-1", "Saga"))
        holder.push(person)
        holder.focusOn(person)

        holder.popRoute()

        assertNotNull(holder.focusMemory.restore(person.focusRouteKey()))
    }

    @Test
    fun leavingForATopLevelDestinationForgetsOpenPages() {
        val holder = TvAppStateHolder()
        holder.push(detail)
        holder.focusOn(detail)

        holder.selectTopLevel(TvRoute.Search)

        assertNull(holder.focusMemory.restore(detail.focusRouteKey()))
    }

    @Test
    fun topLevelDestinationsKeepTheirFocusMemory() {
        val holder = TvAppStateHolder()
        holder.focusOn(TvRoute.Home)
        holder.push(detail)

        holder.popRoute()

        assertNotNull(holder.focusMemory.restore(TvRoute.Home.focusRouteKey()))
    }
}
