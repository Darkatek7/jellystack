package dev.jellystack.design.jellyseerr

import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import kotlin.test.Test
import kotlin.test.assertEquals

class JellyseerrImmersiveDetailContentTest {
    @Test
    fun sectionsOnlyExposeContentThatExists() {
        val detail =
            detail().copy(
                overview = "A complete synopsis.",
                videos =
                    listOf(
                        JellyseerrMediaVideo(
                            id = "trailer",
                            name = "Official trailer",
                            site = "YouTube",
                            type = "Trailer",
                            key = "abc",
                            url = null,
                            official = true,
                            publishedAt = null,
                        ),
                    ),
                releaseDate = "2026-07-24",
            )

        assertEquals(
            listOf(
                JellyseerrDetailSection.Overview,
                JellyseerrDetailSection.Extras,
                JellyseerrDetailSection.Info,
            ),
            visibleSeerrDetailSections(
                detail = detail,
                enrichment = JellyseerrMediaDetailEnrichment(),
                enrichmentLoading = false,
            ),
        )
    }

    @Test
    fun emptyDetailDoesNotCreateBrokenTabs() {
        assertEquals(
            emptyList(),
            visibleSeerrDetailSections(
                detail = detail(),
                enrichment = JellyseerrMediaDetailEnrichment(),
                enrichmentLoading = false,
            ),
        )
    }

    @Test
    fun stagedEnrichmentKeepsOverviewVisibleWhileLoading() {
        assertEquals(
            listOf(JellyseerrDetailSection.Overview),
            visibleSeerrDetailSections(
                detail = detail(),
                enrichment = JellyseerrMediaDetailEnrichment(),
                enrichmentLoading = true,
            ),
        )
    }

    @Test
    fun relatedRailsAndCastUseProductCaps() {
        val related = (1..20).map(::searchItem)
        val cast =
            (1..20).map { index ->
                JellyseerrPerson(
                    id = index,
                    name = "Person $index",
                    order = 21 - index,
                )
            }

        assertEquals(12, seerrVisibleRelatedItems(related).size)
        assertEquals(16, seerrVisibleCast(cast).size)
        assertEquals(20, seerrVisibleCast(cast).first().id)
    }

    @Test
    fun keyCrewKeepsCentralRolesAndCapsTheList() {
        val crew =
            (1..14).map { index ->
                JellyseerrPerson(
                    id = index,
                    name = "Director $index",
                    job = "Director",
                )
            } +
                JellyseerrPerson(
                    id = 100,
                    name = "Lighting lead",
                    job = "Gaffer",
                )

        val visible = keySeerrCrew(crew)

        assertEquals(10, visible.size)
        assertEquals((1..10).toList(), visible.map { it.id })
    }
}

private fun detail(): JellyseerrMediaDetail =
    JellyseerrMediaDetail(
        tmdbId = 1,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Title",
        year = null,
        overview = null,
        runtimeMinutes = null,
        genres = emptyList(),
        releaseDate = null,
        revenue = null,
        originalLanguage = null,
        productionCountries = emptyList(),
        studios = emptyList(),
        ratings = null,
        trailer = null,
        posterPath = null,
        backdropPath = null,
        jellyseerrUrl = null,
        jellyfinUrl = null,
        imdbId = null,
        tvdbId = null,
    )

private fun searchItem(id: Int): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = id,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Title $id",
        overview = null,
        releaseYear = null,
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )
