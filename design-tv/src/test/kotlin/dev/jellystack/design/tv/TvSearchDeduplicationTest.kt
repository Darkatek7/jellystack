package dev.jellystack.design.tv

import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TvSearchDeduplicationTest {
    @Test
    fun exactTmdbDuplicateBecomesOnePlayableResult() {
        val presentation =
            tvSearchPresentation(
                TvSearchUiState.completed(
                    query = "dune",
                    jellyfin = listOf(jellyfinItem("jf", name = "Dune", tmdbId = "438631")),
                    seerr = listOf(seerrItem("Dune", tmdbId = 438631)),
                ),
            )

        assertEquals(1, presentation.results.size)
        assertEquals(TvSearchResultAction.PLAY, presentation.results.single().action)
        assertNotNull(presentation.results.single().jellyfinItem)
        assertNotNull(presentation.results.single().seerrItem)
    }

    @Test
    fun exactTvdbDuplicateMergesWhenTmdbIsUnavailable() {
        val presentation =
            tvSearchPresentation(
                TvSearchUiState.completed(
                    query = "show",
                    jellyfin = listOf(jellyfinItem("jf", type = "Series", tvdbId = "123")),
                    seerr = listOf(seerrItem("Show", JellyseerrMediaType.TV, tmdbId = 0, tvdbId = 123)),
                ),
            )

        assertEquals(1, presentation.results.size)
        assertEquals(TvSearchResultAction.PLAY, presentation.results.single().action)
    }

    @Test
    fun titleOnlyMatchesAndCrossMediaProviderCollisionsStaySeparate() {
        val titleOnly =
            tvSearchPresentation(
                TvSearchUiState.completed(
                    query = "same",
                    jellyfin = listOf(jellyfinItem("jf", name = "Same title")),
                    seerr = listOf(seerrItem("Same title", tmdbId = 99)),
                ),
            )
        assertEquals(2, titleOnly.results.size)

        val crossMedia =
            tvSearchPresentation(
                TvSearchUiState.completed(
                    query = "same provider",
                    jellyfin = listOf(jellyfinItem("movie", tmdbId = "99")),
                    seerr = listOf(seerrItem("Series", JellyseerrMediaType.TV, tmdbId = 99)),
                ),
            )
        assertEquals(2, crossMedia.results.size)
    }

    @Test
    fun seerrOnlyResultUsesRequestAction() {
        val presentation =
            tvSearchPresentation(
                TvSearchUiState.completed(query = "new", seerr = listOf(seerrItem("New", tmdbId = 77))),
            )

        assertEquals(TvSearchResultAction.REQUEST, presentation.results.single().action)
    }
}
