package dev.jellystack.design.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class DestinationChangeDispatcherTest {
    @Test
    fun dispatcherClearsFocusAndKeyboardBeforeMutation() {
        val events = mutableListOf<String>()
        val dispatcher =
            DestinationChangeDispatcher(
                clearFocus = { events += "focus" },
                hideKeyboard = { events += "keyboard" },
            )

        dispatcher.dispatch { events += "route" }

        assertEquals(listOf("focus", "keyboard", "route"), events)
    }

    @Test
    fun boundNestedDestinationCallbacksKeepTheSameOrdering() {
        val events = mutableListOf<String>()
        val dispatcher =
            DestinationChangeDispatcher(
                clearFocus = { events += "focus" },
                hideKeyboard = { events += "keyboard" },
            )
        val libraryChange = dispatcher.callback<String> { events += "library:$it" }
        val nestedDetail = dispatcher.action { events += "detail" }

        libraryChange("downloads")
        nestedDetail()

        assertEquals(
            listOf("focus", "keyboard", "library:downloads", "focus", "keyboard", "detail"),
            events,
        )
    }
}
