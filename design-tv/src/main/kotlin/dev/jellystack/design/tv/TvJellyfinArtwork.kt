package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.JellyfinItem

internal data class TvJellyfinArtwork(
    val itemId: String,
    val imageTag: String,
    val imageType: String,
)

internal fun resolveTvJellyfinArtwork(
    item: JellyfinItem,
    landscape: Boolean = true,
): TvJellyfinArtwork? {
    val seriesId = item.seriesId?.takeIf { item.type.equals("Episode", ignoreCase = true) }

    fun direct(
        tag: String?,
        type: String,
    ): TvJellyfinArtwork? = tag?.takeIf(String::isNotBlank)?.let { TvJellyfinArtwork(item.id, it, type) }

    fun inherited(
        tag: String?,
        type: String,
    ): TvJellyfinArtwork? =
        seriesId?.let { id ->
            tag?.takeIf(String::isNotBlank)?.let { TvJellyfinArtwork(id, it, type) }
        }

    val candidates =
        if (landscape) {
            listOf(
                inherited(item.seriesThumbImageTag, "Thumb"),
                direct(item.thumbImageTag, "Thumb"),
                inherited(item.seriesBackdropImageTag, "Backdrop"),
                direct(item.backdropImageTag, "Backdrop"),
                inherited(item.seriesPrimaryImageTag, "Primary"),
                direct(item.primaryImageTag, "Primary"),
            )
        } else {
            listOf(
                inherited(item.seriesPrimaryImageTag, "Primary"),
                direct(item.primaryImageTag, "Primary"),
                inherited(item.seriesThumbImageTag, "Thumb"),
                direct(item.thumbImageTag, "Thumb"),
                inherited(item.seriesBackdropImageTag, "Backdrop"),
                direct(item.backdropImageTag, "Backdrop"),
            )
        }
    return candidates.firstNotNullOfOrNull { it }
}

internal fun jellyfinImageUrl(
    baseUrl: String?,
    token: String?,
    artwork: TvJellyfinArtwork?,
    maxWidth: Int = 1000,
): String? =
    artwork?.let {
        jellyfinImageUrl(
            baseUrl = baseUrl,
            token = token,
            itemId = it.itemId,
            tag = it.imageTag,
            type = it.imageType,
            maxWidth = maxWidth,
        )
    }

internal fun resolveTvHomeSectionImageUrl(
    item: HomeSectionItem,
    baseUrl: String?,
    token: String?,
): String? {
    val directImage = item.imageUrl?.takeIf(String::isNotBlank)
    val jellyfinItem = item.jellyfinItem
    return when {
        directImage != null -> directImage
        jellyfinItem == null -> null
        else -> {
            val artwork = resolveTvJellyfinArtwork(jellyfinItem)
            if (artwork != null) {
                jellyfinImageUrl(baseUrl, token, artwork)
            } else {
                jellyfinImageUrl(
                    baseUrl = baseUrl,
                    token = token,
                    itemId = jellyfinItem.id,
                    tag = null,
                    type = "Primary",
                )
            }
        }
    }
}
