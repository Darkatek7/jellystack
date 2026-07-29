package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import kotlinx.coroutines.CancellationException

sealed interface DetailTrailerSource {
    data class Local(
        val item: JellyfinItem,
        val detail: JellyfinItemDetail,
    ) : DetailTrailerSource

    data class YouTube(
        val trailer: JellyseerrMediaTrailer,
    ) : DetailTrailerSource
}

data class DetailTrailerContext(
    val itemId: String,
    val isEpisode: Boolean,
    val seriesId: String?,
    val detail: JellyfinItemDetail,
    val isSeries: Boolean = false,
)

class DetailTrailerResolver(
    private val fetchLocalTrailers: suspend (String) -> List<JellyfinItem>,
    private val fetchItemDetail: suspend (String) -> JellyfinItemDetail?,
    private val fetchSeerrTrailer: suspend (tmdbId: Int, isShow: Boolean) -> JellyseerrMediaTrailer?,
) {
    suspend fun resolve(context: DetailTrailerContext): DetailTrailerSource? {
        localTrailer(context.itemId)?.let { return it }

        val parentDetail =
            if (context.isEpisode && !context.seriesId.isNullOrBlank()) {
                localTrailer(context.seriesId)?.let { return it }
                optional { fetchItemDetail(context.seriesId) }
            } else {
                null
            }
        val providerIds = parentDetail?.providerIds.orEmpty() + context.detail.providerIds
        val tmdbId =
            providerIds.entries
                .firstOrNull { it.key.equals("tmdb", ignoreCase = true) }
                ?.value
                ?.toIntOrNull()
                ?: return null
        val trailer = optional { fetchSeerrTrailer(tmdbId, context.isEpisode || context.isSeries) }
        return trailer?.let(DetailTrailerSource::YouTube)
    }

    private suspend fun localTrailer(parentId: String): DetailTrailerSource.Local? {
        val item = optional { fetchLocalTrailers(parentId).firstOrNull() } ?: return null
        val detail = optional { fetchItemDetail(item.id) } ?: return null
        return DetailTrailerSource.Local(item, detail)
    }
}

private suspend fun <T> optional(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
