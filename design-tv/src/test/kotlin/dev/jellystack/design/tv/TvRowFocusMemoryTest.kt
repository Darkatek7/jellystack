package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvRowFocusMemoryTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun landsOnTheRememberedCard() {
        val memory = TvRowFocusMemory()
        memory.remember("home", "row", "c")

        assertEquals(2, memory.landingIndex("home", "row", ids))
    }

    @Test
    fun landsOnTheFirstCardWithoutMemoryOrWhenTheCardIsGone() {
        val memory = TvRowFocusMemory()
        assertEquals(0, memory.landingIndex("home", "row", ids))

        memory.remember("home", "row", "removed")
        assertEquals(0, memory.landingIndex("home", "row", ids))
    }

    @Test
    fun followsTheCardWhenItMovesToTheFront() {
        val memory = TvRowFocusMemory()
        memory.remember("home", "row", "c")

        assertEquals(0, memory.landingIndex("home", "row", listOf("c", "a", "b", "d")))
    }

    @Test
    fun rowsAndRoutesAreRememberedSeparately() {
        val memory = TvRowFocusMemory()
        memory.remember("home", "one", "b")
        memory.remember("home", "two", "d")
        memory.remember("library", "one", "c")

        assertEquals(1, memory.landingIndex("home", "one", ids))
        assertEquals(3, memory.landingIndex("home", "two", ids))
        assertEquals(2, memory.landingIndex("library", "one", ids))
    }

    @Test
    fun retainRowsForgetsRowsThatAreNoLongerShownOnThatRouteOnly() {
        val memory = TvRowFocusMemory()
        memory.remember("home", "kept", "b")
        memory.remember("home", "dropped", "b")
        memory.remember("library", "dropped", "b")

        memory.retainRows("home", listOf("kept"))

        assertEquals(1, memory.landingIndex("home", "kept", ids))
        assertEquals(0, memory.landingIndex("home", "dropped", ids))
        assertEquals(1, memory.landingIndex("library", "dropped", ids))
    }

    @Test
    fun clearingTheFocusMemoryForAnAccountChangeForgetsRows() {
        val focusMemory = TvFocusMemory()
        focusMemory.rows.remember("home", "row", "d")

        focusMemory.clear()

        assertEquals(0, focusMemory.rows.landingIndex("home", "row", ids))
    }
}
