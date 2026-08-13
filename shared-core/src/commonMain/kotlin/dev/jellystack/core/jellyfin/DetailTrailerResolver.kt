package dev.jellystack.core.jellyfin

import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer

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
    private val localTrailerResolver = LocalTrailerResolver(fetchLocalTrailers, fetchItemDetail)

    suspend fun resolve(context: DetailTrailerContext): DetailTrailerSource? {
        localTrailerResolver
            .resolve(LocalTrailerContext(context.itemId, context.isEpisode, context.seriesId))
            ?.let { return it }

        val parentDetail =
            if (context.isEpisode && !context.seriesId.isNullOrBlank()) {
                optionalTrailerLookup { fetchItemDetail(context.seriesId) }
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
        val trailer = optionalTrailerLookup { fetchSeerrTrailer(tmdbId, context.isEpisode || context.isSeries) }
        return trailer?.let(DetailTrailerSource::YouTube)
    }
}
