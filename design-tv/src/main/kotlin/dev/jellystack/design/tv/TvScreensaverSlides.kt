package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlin.random.Random

/**
 * One backdrop of the screensaver. [imageUrl] carries the access token (Jellyfin 12 needs `ApiKey` on image URLs),
 * so it stays out of [toString] and therefore out of logs.
 */
class TvScreensaverSlide(
    val id: String,
    val title: String,
    val year: Int?,
    val imageUrl: String,
) {
    override fun toString(): String = "TvScreensaverSlide"
}

/**
 * Hands out the slides in a shuffled order without repeating one until the whole list has been shown. A new round
 * never starts with the slide the previous round ended on.
 */
internal class TvScreensaverRotation(
    slides: List<TvScreensaverSlide>,
    private val random: Random = Random.Default,
) {
    private val pool = slides.distinctBy { it.id }
    private val queue = ArrayDeque<TvScreensaverSlide>()
    private var last: TvScreensaverSlide? = null

    /** The next slide, or null when there is nothing to show. */
    fun next(): TvScreensaverSlide? {
        if (pool.isEmpty()) return null
        if (queue.isEmpty()) startRound()
        return queue.removeFirst().also { last = it }
    }

    private fun startRound() {
        queue.addAll(pool.shuffled(random))
        if (pool.size > 1 && queue.first().id == last?.id) queue.addLast(queue.removeFirst())
    }
}

/**
 * The slides for [items]: one per backdrop, skipping items that have none. Unlike the home screen this never asks
 * the server for an untagged backdrop, because the screensaver must not cause requests for artwork that may not exist.
 */
internal fun tvScreensaverSlides(
    items: List<JellyfinItem>,
    baseUrl: String?,
    token: String?,
): List<TvScreensaverSlide> {
    if (baseUrl.isNullOrBlank()) return emptyList()
    return items
        .mapNotNull { item ->
            val artwork = tvScreensaverBackdrop(item) ?: return@mapNotNull null
            val url = jellyfinImageUrl(baseUrl, token, artwork, TvArtworkSize.HERO) ?: return@mapNotNull null
            TvScreensaverSlide(
                id = artwork.itemId,
                title = tvScreensaverTitle(item),
                year = item.productionYear,
                imageUrl = url,
            )
        }.distinctBy { it.id }
}

private fun tvScreensaverBackdrop(item: JellyfinItem): TvJellyfinArtwork? {
    val seriesId = item.seriesId
    val seriesTag = item.seriesBackdropImageTag
    val ownTag = item.backdropImageTag
    return when {
        !seriesId.isNullOrBlank() && !seriesTag.isNullOrBlank() -> TvJellyfinArtwork(seriesId, seriesTag, "Backdrop")
        !ownTag.isNullOrBlank() -> TvJellyfinArtwork(item.id, ownTag, "Backdrop")
        else -> null
    }
}

private fun tvScreensaverTitle(item: JellyfinItem): String {
    val isEpisode = item.type.equals("Episode", ignoreCase = true)
    return if (isEpisode) item.seriesName?.takeIf(String::isNotBlank) ?: item.name else item.name
}
