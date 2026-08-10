package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLibraryPagingTest {
    @Test
    fun loadsWithinTwoFourColumnRowsOfTheEnd() {
        assertTrue(
            shouldLoadNextLibraryPage(
                lastVisibleIndex = 22,
                totalItemCount = 30,
                isInitialLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun doesNotLoadWhileBusyOrAtTheEnd() {
        assertFalse(shouldLoadNextLibraryPage(29, 30, isInitialLoading = true, isPageLoading = false, endReached = false, hasError = false))
        assertFalse(shouldLoadNextLibraryPage(29, 30, isInitialLoading = false, isPageLoading = true, endReached = false, hasError = false))
        assertFalse(shouldLoadNextLibraryPage(29, 30, isInitialLoading = false, isPageLoading = false, endReached = true, hasError = false))
        assertFalse(shouldLoadNextLibraryPage(29, 30, isInitialLoading = false, isPageLoading = false, endReached = false, hasError = true))
    }

    @Test
    fun doesNotLoadBeforeTheThresholdOrWithoutItems() {
        assertFalse(shouldLoadNextLibraryPage(10, 30, isInitialLoading = false, isPageLoading = false, endReached = false, hasError = false))
        assertFalse(shouldLoadNextLibraryPage(-1, 0, isInitialLoading = false, isPageLoading = false, endReached = false, hasError = false))
    }
}
