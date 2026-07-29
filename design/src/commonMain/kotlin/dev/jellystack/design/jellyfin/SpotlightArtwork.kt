package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.JellyfinItem

internal data class SpotlightArtwork(
    val itemId: String,
    val tag: String,
    val imageType: String,
)

internal fun JellyfinItem.selectSpotlightArtwork(): SpotlightArtwork? {
    val inheritedOwner = seriesId?.takeIf { it.isNotBlank() && it != id }
    val directBackdrop =
        backdropImageTag?.takeUnless { tag ->
            inheritedOwner != null && tag == seriesBackdropImageTag
        }
    val directThumb =
        thumbImageTag?.takeUnless { tag ->
            inheritedOwner != null && tag == seriesThumbImageTag
        }
    return directBackdrop?.let { tag ->
        SpotlightArtwork(itemId = id, tag = tag, imageType = "Backdrop")
    } ?: seriesBackdropImageTag?.takeIf { inheritedOwner != null }?.let { tag ->
        SpotlightArtwork(itemId = requireNotNull(inheritedOwner), tag = tag, imageType = "Backdrop")
    } ?: directThumb?.let { tag ->
        SpotlightArtwork(itemId = id, tag = tag, imageType = "Thumb")
    } ?: seriesThumbImageTag?.takeIf { inheritedOwner != null }?.let { tag ->
        SpotlightArtwork(itemId = requireNotNull(inheritedOwner), tag = tag, imageType = "Thumb")
    }
}

internal fun JellyfinItem.selectSpotlightLogoArtwork(): SpotlightArtwork? {
    val inheritedOwner = seriesId?.takeIf { it.isNotBlank() } ?: id
    val directArtwork =
        logoImageTag?.let { tag ->
            SpotlightArtwork(itemId = id, tag = tag, imageType = "Logo")
        }
    val inheritedArtwork =
        (seriesLogoImageTag ?: parentLogoImageTag)?.let { tag ->
            SpotlightArtwork(itemId = inheritedOwner, tag = tag, imageType = "Logo")
        }
    return if (type.equals("Movie", ignoreCase = true)) {
        directArtwork ?: inheritedArtwork
    } else {
        inheritedArtwork ?: directArtwork
    }
}
