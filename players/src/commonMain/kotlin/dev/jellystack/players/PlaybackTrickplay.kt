package dev.jellystack.players

/**
 * One trickplay resolution of a media source: tile sheets of [columns] × [rows] thumbnails, each
 * [width] × [height] pixels, one thumbnail every [intervalMs].
 */
data class TrickplayManifest(
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
) {
    val isUsable: Boolean
        get() = width > 0 && height > 0 && columns > 0 && rows > 0 && thumbnailCount > 0 && intervalMs > 0
}

/** Trickplay thumbnails available for the media source being played. */
data class PlaybackTrickplay(
    val itemId: String,
    val mediaSourceId: String,
    val manifest: TrickplayManifest,
)

/** Where the thumbnail for a position sits: which sheet image, and the pixel offset inside it. */
data class TrickplayTile(
    val sheetIndex: Int,
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int,
)

/** Thumbnail covering [positionMs]; positions past the last thumbnail show the last one. */
fun TrickplayManifest.tileAt(positionMs: Long): TrickplayTile? {
    if (!isUsable) return null
    val thumbnail = (positionMs.coerceAtLeast(0L) / intervalMs).coerceAtMost((thumbnailCount - 1).toLong()).toInt()
    val perSheet = columns * rows
    val offset = thumbnail % perSheet
    return TrickplayTile(
        sheetIndex = thumbnail / perSheet,
        offsetX = (offset % columns) * width,
        offsetY = (offset / columns) * height,
        width = width,
        height = height,
    )
}

/**
 * Picks the manifest for [mediaSourceId] at the width closest to [preferredWidth]. Source IDs are
 * compared without dashes or case; a single listed source is used even if its ID is formatted differently.
 */
fun selectTrickplayManifest(
    manifests: Map<String, Map<Int, TrickplayManifest>>,
    mediaSourceId: String?,
    preferredWidth: Int,
): Pair<String, TrickplayManifest>? {
    val wanted = mediaSourceId?.normalizedSourceId()
    val entry =
        manifests.entries.firstOrNull { it.key.normalizedSourceId() == wanted }
            ?: manifests.entries.singleOrNull()
            ?: return null
    val manifest =
        entry.value.values
            .filter(TrickplayManifest::isUsable)
            .minByOrNull { kotlin.math.abs(it.width - preferredWidth) }
            ?: return null
    return entry.key to manifest
}

/** URL of one tile sheet; the access token travels as ApiKey, like other Jellyfin image URLs. */
fun trickplaySheetUrl(
    baseUrl: String,
    trickplay: PlaybackTrickplay,
    sheetIndex: Int,
    accessToken: String?,
): String =
    "${baseUrl.trimEnd('/')}/Videos/${trickplay.itemId}/Trickplay/${trickplay.manifest.width}/$sheetIndex.jpg" +
        "?MediaSourceId=${trickplay.mediaSourceId}" +
        accessToken?.takeIf(String::isNotBlank)?.let { "&ApiKey=$it" }.orEmpty()

private fun String.normalizedSourceId(): String = replace("-", "").lowercase()
