package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDetailUiStateTest {
    @Test
    fun jellyfinSectionsUseRenderedOrderAndStableItemIds() {
        val episodes = listOf(jellyfinItem("episode-2"), jellyfinItem("episode-1"))
        val cast = listOf(jellyfinPerson("person-2"), jellyfinPerson("person-1"))
        val similar = listOf(jellyfinItem("similar-2"), jellyfinItem("similar-1"))

        val state =
            buildTvJellyfinDetailUiState(
                routeKey = "series-1",
                facts = listOf("2026", "45 min"),
                overview = "Overview",
                tagline = "Tagline",
                seasonGroups =
                    listOf(
                        TvSeasonGroup(1, episodes),
                        TvSeasonGroup(2, emptyList()),
                    ),
                selectedSeasonIndex = 0,
                episodes = episodes,
                cast = cast,
                similar = similar,
            )

        assertEquals(
            listOf("facts", "overview", "seasons", "episodes", "cast", "similar"),
            state.sections.map(TvDetailSection::id),
        )
        assertEquals(state.sections.size, state.sections.map(TvDetailSection::id).toSet().size)
        assertEquals(listOf("episode-2", "episode-1"), state.section("episodes")?.itemIds)
        assertEquals(listOf("person-2", "person-1"), state.section("cast")?.itemIds)
        assertEquals(listOf("similar-2", "similar-1"), state.section("similar")?.itemIds)
    }

    @Test
    fun jellyfinOptionalSectionsDisappearAsPayloadsBecomeEmpty() {
        val state =
            buildTvJellyfinDetailUiState(
                routeKey = "movie-1",
                facts = emptyList(),
                overview = null,
                tagline = null,
                seasonGroups = emptyList(),
                selectedSeasonIndex = 0,
                episodes = emptyList(),
                cast = emptyList(),
                similar = emptyList(),
            )

        assertEquals(listOf("facts", "overview"), state.sections.map(TvDetailSection::id))
        assertNull(state.section("episodes"))
        assertNull(state.section("cast"))
        assertNull(state.section("similar"))
    }

    @Test
    fun seerrAnchorResolvesBySectionAndItemIdsAcrossAppearAndReorder() {
        val anchor = TvFocusAnchor("cast", "person-2", TvFocusDestination.SECTION_ITEM)
        val initial =
            buildTvSeerrDetailUiState(
                routeKey = "tv:42",
                overview = "Overview",
                tagline = null,
                ratings = null,
                cast = listOf(seerrPerson(1), seerrPerson(2)),
                similar = emptyList(),
            )
        val changed =
            buildTvSeerrDetailUiState(
                routeKey = "tv:42",
                overview = "Overview",
                tagline = null,
                ratings = JellyseerrMediaRatings(8.0, null, null, null),
                cast = listOf(seerrPerson(2), seerrPerson(1)),
                similar = listOf(seerrItem(12), seerrItem(11)),
            )

        assertEquals(listOf("overview", "cast"), initial.sections.map(TvDetailSection::id))
        assertEquals(listOf("overview", "ratings", "cast", "similar"), changed.sections.map(TvDetailSection::id))
        assertEquals(TvResolvedFocusAnchor(sectionIndex = 2, itemIndex = 0), changed.resolve(anchor))
        assertEquals(listOf("person-2", "person-1"), changed.section("cast")?.itemIds)
        assertEquals(listOf("tv:12", "tv:11"), changed.section("similar")?.itemIds)
        assertTrue(changed.sections.map(TvDetailSection::id).let { it.size == it.toSet().size })
    }

    private fun jellyfinPerson(id: String) =
        JellyfinPerson(
            id = id,
            name = id,
            role = "Role",
            type = "Actor",
            primaryImageTag = null,
        )

    private fun jellyfinItem(id: String) =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = id,
            sortName = null,
            overview = null,
            type = "Episode",
            mediaType = null,
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
        )

    private fun seerrPerson(id: Int) = JellyseerrPerson(id = id, name = "Person $id")

    private fun seerrItem(id: Int) =
        JellyseerrSearchItem(
            tmdbId = id,
            mediaType = JellyseerrMediaType.TV,
            title = "Similar $id",
            overview = null,
            releaseYear = null,
            posterPath = null,
            backdropPath = null,
            mediaInfoId = null,
            tvdbId = null,
            availability = JellyseerrMediaAvailability(null, null),
            requests = emptyList(),
        )
}
