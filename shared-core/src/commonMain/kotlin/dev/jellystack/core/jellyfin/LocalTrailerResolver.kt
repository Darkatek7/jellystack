package dev.jellystack.core.jellyfin

import kotlinx.coroutines.CancellationException

data class LocalTrailerContext(
    val itemId: String,
    val isEpisode: Boolean,
    val seriesId: String?,
)

class LocalTrailerResolver(
    private val fetchLocalTrailers: suspend (String) -> List<JellyfinItem>,
    private val fetchItemDetail: suspend (String) -> JellyfinItemDetail?,
) {
    suspend fun resolve(context: LocalTrailerContext): DetailTrailerSource.Local? {
        localTrailer(context.itemId)?.let { return it }
        if (context.isEpisode && !context.seriesId.isNullOrBlank()) {
            localTrailer(context.seriesId)?.let { return it }
        }
        return null
    }

    private suspend fun localTrailer(parentId: String): DetailTrailerSource.Local? {
        val item = optionalTrailerLookup { fetchLocalTrailers(parentId).firstOrNull() } ?: return null
        val detail = optionalTrailerLookup { fetchItemDetail(item.id) } ?: return null
        return DetailTrailerSource.Local(item, detail)
    }
}

internal suspend fun <T> optionalTrailerLookup(block: suspend () -> T): T? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
