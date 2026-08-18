package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals

class TvLibraryFocusTest {
    @Test
    fun nonEmptyPagingErrorUsesTheStableTrailingRetryTarget() {
        val target =
            tvLibraryTerminalFocusTarget(
                libraryId = "movies",
                itemCount = 32,
                isLibraryLoading = false,
                isPageLoading = false,
                hasError = true,
            )

        assertEquals(TV_LIBRARY_RETRY_TARGET, target)
        assertEquals(
            33,
            tvLibraryGridFocusLocations(
                itemTargetIds = List(32) { index -> tvLibraryTargetId("item-$index") },
                terminalTarget = target,
            )[TV_LIBRARY_RETRY_TARGET],
        )
    }
}
