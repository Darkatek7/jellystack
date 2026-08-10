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
        assertPagingBlocked(isInitialLoading = true)
        assertPagingBlocked(isPageLoading = true)
        assertPagingBlocked(endReached = true)
        assertPagingBlocked(hasError = true)
    }

    @Test
    fun doesNotLoadBeforeTheThresholdOrWithoutItems() {
        assertFalse(
            shouldLoadNextLibraryPage(
                10,
                30,
                isInitialLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
        assertFalse(
            shouldLoadNextLibraryPage(
                -1,
                0,
                isInitialLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
    }

    private fun assertPagingBlocked(
        isInitialLoading: Boolean = false,
        isPageLoading: Boolean = false,
        endReached: Boolean = false,
        hasError: Boolean = false,
    ) {
        assertFalse(
            shouldLoadNextLibraryPage(
                29,
                30,
                isInitialLoading = isInitialLoading,
                isPageLoading = isPageLoading,
                endReached = endReached,
                hasError = hasError,
            ),
        )
    }
}
