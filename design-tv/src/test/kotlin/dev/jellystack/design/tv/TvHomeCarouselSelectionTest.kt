package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.SpotlightCandidate
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvHomeCarouselSelectionTest {
    @Test
    fun reconcileReturnsNullForAnEmptyCandidateList() {
        assertNull(reconcileTvHomeCarouselSelection(emptyList(), currentId = "current"))
    }

    @Test
    fun reconcileFallsBackToFirstCandidateWhenCurrentIdIsMissing() {
        assertEquals(
            "first",
            reconcileTvHomeCarouselSelection(listOf("first", "second"), currentId = "missing"),
        )
    }

    @Test
    fun manualPreviousClampsAtFirstAndRequestsNavigationRail() {
        val state = TvHomeCarouselState(selectedId = "first")

        val result =
            moveTvHomeCarouselManually(
                candidateIds = listOf("first", "second"),
                state = state,
                direction = TvHomeCarouselDirection.PREVIOUS,
            )

        assertEquals(state, result.state)
        assertTrue(result.openNavigationRail)
    }

    @Test
    fun manualNextClampsAtLastWithoutOpeningNavigationRail() {
        val state = TvHomeCarouselState(selectedId = "last")

        val result =
            moveTvHomeCarouselManually(
                candidateIds = listOf("first", "last"),
                state = state,
                direction = TvHomeCarouselDirection.NEXT,
            )

        assertEquals(state, result.state)
        assertFalse(result.openNavigationRail)
    }

    @Test
    fun successfulManualMoveSelectsTheAdjacentItem() {
        val state = TvHomeCarouselState(selectedId = "first")

        val result =
            moveTvHomeCarouselManually(
                candidateIds = listOf("first", "second", "last"),
                state = state,
                direction = TvHomeCarouselDirection.NEXT,
            )

        assertEquals("second", result.state.selectedId)
        assertFalse(result.openNavigationRail)
    }

    @Test
    fun spotlightPreviewUsesActionItemIdentity() {
        val displayItem = item("season", "Season artwork")
        val actionItem = item("episode", "Actionable episode")
        val candidate = SpotlightCandidate(displayItem, actionItem, Instant.DISTANT_PAST)

        assertSame(actionItem, candidate.tvHomeTrailerPreviewItem())
    }

    @Test
    fun heroPreviewOnlyRendersForActiveActionItem() {
        val active = TvTrailerPreviewTarget("server", "episode", isEpisode = true, seriesId = "series")
        val other = active.copy(itemId = "other")
        val activeHero = TvTrailerPreviewRequest(TvTrailerPreviewOwner.HERO, active)
        val otherHero = TvTrailerPreviewRequest(TvTrailerPreviewOwner.HERO, other)
        val activeCard = TvTrailerPreviewRequest(TvTrailerPreviewOwner.CARD, active)

        assertTrue(TvTrailerPreviewState.Playing(activeHero).showsTvHomeHeroPreview("episode", heroFocused = true))
        assertFalse(TvTrailerPreviewState.Playing(activeHero).showsTvHomeHeroPreview("episode", heroFocused = false))
        assertFalse(TvTrailerPreviewState.Playing(otherHero).showsTvHomeHeroPreview("episode", heroFocused = true))
        assertFalse(TvTrailerPreviewState.Playing(activeCard).showsTvHomeHeroPreview("episode", heroFocused = true))
        assertFalse(TvTrailerPreviewState.Armed(activeHero).showsTvHomeHeroPreview("episode", heroFocused = true))
        assertFalse(TvTrailerPreviewState.Unavailable(activeHero).showsTvHomeHeroPreview("episode", heroFocused = true))
        assertFalse(TvTrailerPreviewState.Idle.showsTvHomeHeroPreview("episode", heroFocused = true))
    }

    @Test
    fun cardPreviewOnlyRendersForExactCardInstance() {
        val target = TvTrailerPreviewTarget("server", "same", isEpisode = false, seriesId = null)
        val card = TvTrailerPreviewRequest(TvTrailerPreviewOwner.CARD, target, presentationId = "continue:same")
        val hero = TvTrailerPreviewRequest(TvTrailerPreviewOwner.HERO, target)

        assertTrue(TvTrailerPreviewState.Playing(card).showsTvMediaCardPreview("same", "continue:same"))
        assertFalse(TvTrailerPreviewState.Playing(card).showsTvMediaCardPreview("same", "latest:same"))
        assertFalse(TvTrailerPreviewState.Playing(hero).showsTvMediaCardPreview("same", "continue:same"))
        assertFalse(TvTrailerPreviewState.Armed(card).showsTvMediaCardPreview("same", "continue:same"))
    }

    private fun item(
        id: String,
        name: String,
    ) = JellyfinItem(
        id = id,
        libraryId = "library",
        name = name,
        sortName = null,
        overview = null,
        type = "Movie",
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
        dateCreated = null,
    )
}
