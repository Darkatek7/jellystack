package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.LibraryLoadErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvLibraryPagingTest {
    @Test
    fun retainedItemsFirstPageFailureMapsRetryToRefresh() {
        assertEquals(
            TvLibraryRetryAction.REFRESH,
            tvLibraryRetryAction(LibraryLoadErrorKind.FIRST_PAGE),
        )
    }

    @Test
    fun laterPageFailureMapsRetryToNextPage() {
        assertEquals(
            TvLibraryRetryAction.NEXT_PAGE,
            tvLibraryRetryAction(LibraryLoadErrorKind.NEXT_PAGE),
        )
    }

    @Test
    fun loadsWithinTwoFourColumnRowsOfTheEnd() {
        assertTrue(
            shouldLoadNextLibraryPage(
                lastVisibleIndex = 22,
                totalItemCount = 30,
                isLibraryLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
    }

    @Test
    fun doesNotLoadWhileBusyOrAtTheEnd() {
        assertPagingBlocked(isLibraryLoading = true)
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
                isLibraryLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
        assertFalse(
            shouldLoadNextLibraryPage(
                -1,
                0,
                isLibraryLoading = false,
                isPageLoading = false,
                endReached = false,
                hasError = false,
            ),
        )
    }

    private fun assertPagingBlocked(
        isLibraryLoading: Boolean = false,
        isPageLoading: Boolean = false,
        endReached: Boolean = false,
        hasError: Boolean = false,
    ) {
        assertFalse(
            shouldLoadNextLibraryPage(
                29,
                30,
                isLibraryLoading = isLibraryLoading,
                isPageLoading = isPageLoading,
                endReached = endReached,
                hasError = hasError,
            ),
        )
    }
}
