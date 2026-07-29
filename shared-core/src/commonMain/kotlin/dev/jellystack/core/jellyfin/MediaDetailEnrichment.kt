package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

data class MediaDetailEnrichment(
    val seerrDetail: JellyseerrMediaDetail? = null,
    val similarItems: List<JellyfinItem> = emptyList(),
)

class MediaDetailEnrichmentLoader(
    private val fetchSimilarItems: suspend (itemId: String, limit: Int) -> List<JellyfinItem>,
    private val fetchSeerrDetail:
        suspend (tmdbId: Int, mediaType: JellyseerrMediaType) -> JellyseerrMediaDetail?,
) {
    suspend fun load(
        item: JellyfinItem,
        detail: JellyfinItemDetail,
        similarLimit: Int = DEFAULT_SIMILAR_LIMIT,
    ): MediaDetailEnrichment =
        supervisorScope {
            val similar =
                async {
                    runOptional { fetchSimilarItems(item.id, similarLimit) }
                        .orEmpty()
                        .filterNot { it.id == item.id }
                }
            val seerr =
                async {
                    val mediaType =
                        when {
                            item.type.equals("Movie", ignoreCase = true) -> JellyseerrMediaType.MOVIE
                            item.type.equals("Series", ignoreCase = true) -> JellyseerrMediaType.TV
                            else -> null
                        }
                    val tmdbId =
                        detail.providerIds.entries
                            .firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
                            ?.value
                            ?.toIntOrNull()
                    if (mediaType == null || tmdbId == null) {
                        null
                    } else {
                        runOptional { fetchSeerrDetail(tmdbId, mediaType) }
                    }
                }
            MediaDetailEnrichment(
                seerrDetail = seerr.await(),
                similarItems = similar.await(),
            )
        }

    private companion object {
        const val DEFAULT_SIMILAR_LIMIT = 12
    }
}

private suspend fun <T> runOptional(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
