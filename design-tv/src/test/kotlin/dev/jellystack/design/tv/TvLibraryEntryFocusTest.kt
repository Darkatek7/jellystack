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

    @Test
    fun headerControlsWaitForTheCardsWhenTheyMayNotLead() {
        val session = TvSemanticFocusRestorationSession(snapshot = null, interactionRevision = 0L)

        // Loaded, but the cards register a frame after the header: the header must not take the first focus.
        assertNull(
            session.preferredTargetId(
                listOf(sort, filter),
                contentAuthoritativelyLoaded = true,
                queryControlsMayLead = false,
            ),
        )
    }

    @Test
    fun queryControlsLeadOnlyForTheRoutesOwnEmptyLibrary() {
        val route = TvRoute.Library(libraryId = "library", mode = TvLibraryMode.ALL_TITLES)
        val emptyAndLoaded = JellyfinHomeState(selectedLibraryId = "library", endReached = true)

        assertTrue(tvLibraryQueryControlsMayLead(route, emptyAndLoaded))
        val withItems = emptyAndLoaded.copy(libraryItems = listOf(goldenItem("Movie", "Colour Bars")))
        assertFalse(tvLibraryQueryControlsMayLead(route, withItems))
        assertFalse(tvLibraryQueryControlsMayLead(route, emptyAndLoaded.copy(selectedLibraryId = "other")))
        assertFalse(tvLibraryQueryControlsMayLead(route, JellyfinHomeState(selectedLibraryId = "library")))
        assertTrue(tvLibraryQueryControlsMayLead(TvRoute.Search, emptyAndLoaded))
        // A browse route that falls back to the titles grid follows the same rule.
        assertFalse(tvLibraryQueryControlsMayLead(TvRoute.Library(libraryId = "library"), withItems))
    }
}
