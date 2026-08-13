package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TvHomePresentationTest {
    private val now = Instant.parse("2026-08-13T12:00:00Z")

    @Test
    fun strictRecentCandidatesWinAndUseRecentMode() {
        val strict = item("strict", dateCreated = "2026-08-12T12:00:00Z")
        val older = item("older", dateCreated = "2026-06-01T12:00:00Z")

        val result =
            buildTvHomeHeroPresentation(
                state = JellyfinHomeState(recentMovies = listOf(older, strict)),
                homeSections = HomeSectionsState.Unavailable,
                now = now,
            )

        assertEquals(TvHomeHeroMode.RECENT, result.mode)
        assertEquals(listOf("strict"), result.candidates.map { it.actionItem.id })
    }

    @Test
    fun latestFallbackKeepsRecentListsAheadOfLocalHomeContent() {
        val recent = item("recent-undated")
        val local = item("local-undated")

        val result =
            buildTvHomeHeroPresentation(
                state = JellyfinHomeState(recentMovies = listOf(recent)),
                homeSections = readySection(local, HomeSectionAction.JELLYFIN),
                now = now,
            )

        assertEquals(TvHomeHeroMode.LATEST, result.mode)
        assertEquals(listOf("recent-undated", "local-undated"), result.candidates.map { it.actionItem.id })
    }

    @Test
    fun localFallbackUsesSectionsThenContinueNextUpAndLibraryItems() {
        val section = item("section")
        val continueItem = item("continue")
        val next = item("next")
        val library = item("library")

        val result =
            buildTvHomeHeroPresentation(
                state =
                    JellyfinHomeState(
                        continueWatching = listOf(continueItem),
                        nextUp = listOf(next),
                        libraryItems = listOf(library),
                    ),
                homeSections = readySection(section, HomeSectionAction.JELLYFIN),
                now = now,
            )

        assertEquals(TvHomeHeroMode.LIBRARY, result.mode)
        assertEquals(listOf("section", "continue", "next", "library"), result.candidates.map { it.actionItem.id })
    }

    @Test
    fun datedAdditionalOnlyFallbackUsesLibraryMode() {
        val local = item("dated-local", dateCreated = "2026-08-12T12:00:00Z")

        val result =
            buildTvHomeHeroPresentation(
                state = JellyfinHomeState(continueWatching = listOf(local)),
                homeSections = HomeSectionsState.Unavailable,
                now = now,
            )

        assertEquals(TvHomeHeroMode.LIBRARY, result.mode)
        assertEquals(listOf("dated-local"), result.candidates.map { it.actionItem.id })
    }

    @Test
    fun seerrAndInformationHomeItemsNeverEnterHeroFallback() {
        val seerr = item("seerr")
        val information = item("information")

        val result =
            buildTvHomeHeroPresentation(
                state = JellyfinHomeState(),
                homeSections =
                    HomeSectionsState.Ready(
                        sections =
                            listOf(
                                sectionOf(seerr, HomeSectionAction.SEERR),
                                sectionOf(information, HomeSectionAction.INFORMATION),
                            ),
                        imageBaseUrl = "https://example.test",
                        imageAccessToken = "token",
                    ),
                now = now,
            )

        assertEquals(TvHomeHeroMode.EMPTY, result.mode)
        assertEquals(emptyList(), result.candidates)
    }

    private fun readySection(item: JellyfinItem, action: HomeSectionAction): HomeSectionsState.Ready =
        HomeSectionsState.Ready(
            sections = listOf(sectionOf(item, action)),
            imageBaseUrl = "https://example.test",
            imageAccessToken = "token",
        )

    private fun sectionOf(item: JellyfinItem, action: HomeSectionAction): HomeSection =
        HomeSection(
            id = "section-${item.id}",
            title = "Section",
            viewMode = HomeSectionViewMode.LANDSCAPE,
            displayTitle = true,
            showDetailsMenu = false,
            items =
                listOf(
                    HomeSectionItem(
                        id = item.id,
                        name = item.name,
                        overview = null,
                        productionYear = null,
                        communityRating = null,
                        imageUrl = null,
                        jellyfinItem = item,
                        action = action,
                    ),
                ),
        )

    private fun item(id: String, dateCreated: String? = null): JellyfinItem =
        JellyfinItem(
            id = id,
            libraryId = "library",
            name = id,
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
            dateCreated = dateCreated,
        )
}
