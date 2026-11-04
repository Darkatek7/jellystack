package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvLibraryEntryFocusTest {
    private val sort = tvFocusTarget(TV_LIBRARY_SORT_TARGET)
    private val filter = tvFocusTarget(TV_LIBRARY_FILTER_TARGET)
    private val firstCard = tvFocusTarget(tvLibraryTargetId("title-0"))

    @Test
    fun headerControlsDoNotTakeEntryFocusWhileTheItemsAreOnTheirWay() {
        val session = TvSemanticFocusRestorationSession(snapshot = null, interactionRevision = 0L)

        assertNull(session.preferredTargetId(listOf(sort, filter), contentAuthoritativelyLoaded = false))
        assertEquals(
            firstCard.targetId,
            session.preferredTargetId(listOf(firstCard, sort, filter), contentAuthoritativelyLoaded = true),
        )
    }

    @Test
    fun headerControlsTakeEntryFocusOnceTheLibraryIsKnownToBeEmpty() {
        val session = TvSemanticFocusRestorationSession(snapshot = null, interactionRevision = 0L)

        val preferred = session.preferredTargetId(listOf(sort, filter), contentAuthoritativelyLoaded = true)
        assertEquals(sort.targetId, preferred)
    }

    @Test
    fun otherTargetsStillGetEntryFocusBeforeTheContentIsAuthoritative() {
        val session = TvSemanticFocusRestorationSession(snapshot = null, interactionRevision = 0L)

        assertEquals(
            firstCard.targetId,
            session.preferredTargetId(listOf(sort, firstCard), contentAuthoritativelyLoaded = false),
        )
    }

    @Test
    fun libraryContentIsUndeterminedUntilItemsArriveOrTheLoadEnds() {
        val fresh = JellyfinHomeState(selectedLibraryId = "library")

        assertFalse(tvLibraryContentDetermined(fresh))
        assertTrue(tvLibraryContentDetermined(fresh.copy(endReached = true)))
        assertTrue(tvLibraryContentDetermined(fresh.copy(libraryErrorMessage = "offline")))
        assertTrue(tvLibraryContentDetermined(fresh.copy(totalLibraryItemCount = 0L)))
        assertTrue(
            tvLibraryContentDetermined(
                fresh.copy(libraries = listOf(JellyfinLibrary("library", "Movies", "movies", 0L, null))),
            ),
        )
        assertFalse(
            tvLibraryContentDetermined(
                fresh.copy(libraries = listOf(JellyfinLibrary("library", "Movies", "movies", 2L, null))),
            ),
        )
    }
}
