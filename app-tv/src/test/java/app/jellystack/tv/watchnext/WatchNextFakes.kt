package app.jellystack.tv.watchnext

import dev.jellystack.design.tv.TvWatchNextArtAspect
import dev.jellystack.design.tv.TvWatchNextArtwork
import dev.jellystack.design.tv.TvWatchNextKind
import dev.jellystack.design.tv.TvWatchNextMediaType

internal fun entry(
    id: String,
    title: String = "Example Movie",
    positionMs: Long? = 60_000L,
    artUri: String? = null,
): WatchNextEntry =
    WatchNextEntry(
        internalId = id,
        mediaType = TvWatchNextMediaType.MOVIE,
        kind = TvWatchNextKind.CONTINUE,
        title = title,
        episodeTitle = null,
        seasonNumber = null,
        episodeNumber = null,
        durationMs = 3_600_000L,
        positionMs = positionMs,
        lastEngagementMillis = 1_000L,
        artUri = artUri,
        artAspect = TvWatchNextArtAspect.LANDSCAPE_16_9.takeIf { artUri != null },
        deepLink = "jellystack://item/0123456789abcdef0123456789abcdef?s=0123456789abcdef",
    )

internal fun artwork(
    itemId: String = "0123456789abcdef0123456789abcdef",
    tag: String = "tag1",
    type: String = "Thumb",
) = TvWatchNextArtwork(itemId, tag, type, TvWatchNextArtAspect.LANDSCAPE_16_9)

/** An in-memory stand-in for the provider table, with the launcher behaviours the reconciler must cope with. */
internal class FakeGateway : WatchNextGateway {
    class Row(
        val rowId: Long,
        var entry: WatchNextEntry,
        var browsable: Boolean = true,
    )

    val rows = mutableListOf<Row>()
    val operations = mutableListOf<String>()
    var failOn: String? = null
    var failRead: Boolean = false
    private var nextId = 1L

    fun idsInTable(): Set<String> = rows.map { it.entry.internalId }.toSet()

    /** The user removes the card in the launcher; [keepHidden] mirrors a system that keeps the row with browsable=0. */
    fun userRemoves(
        internalId: String,
        keepHidden: Boolean,
    ) {
        val row = rows.first { it.entry.internalId == internalId }
        if (keepHidden) row.browsable = false else rows.remove(row)
    }

    override fun readPublished(): List<PublishedProgram> {
        if (failRead) throw SecurityException("provider refused")
        return rows.map { PublishedProgram(it.rowId, it.entry.internalId, it.browsable) }
    }

    override fun insert(entry: WatchNextEntry): Boolean {
        operations += "insert ${entry.internalId}"
        if (failOn == "insert") error("insert failed")
        rows += Row(nextId++, entry)
        return true
    }

    override fun update(
        rowId: Long,
        entry: WatchNextEntry,
    ): Boolean {
        operations += "update ${entry.internalId}"
        if (failOn == "update") error("update failed")
        rows.first { it.rowId == rowId }.entry = entry
        return true
    }

    override fun delete(rowId: Long): Boolean {
        val row = rows.first { it.rowId == rowId }
        operations += "delete ${row.entry.internalId}"
        rows.remove(row)
        return true
    }
}

internal class FakeState : WatchNextStateStore {
    override var owner: String? = null
    override var known: Set<String> = emptySet()
    override var dismissed: Set<String> = emptySet()
    override val artSalt: String = "salt"

    override fun reset() {
        owner = null
        known = emptySet()
        dismissed = emptySet()
    }
}

internal class FakeArt(
    private val uris: Map<String, String> = emptyMap(),
) : WatchNextArt {
    val requested = mutableListOf<String>()
    var retained: Set<String>? = null
    var cleared = false

    override suspend fun uriFor(
        serverKey: String,
        artwork: TvWatchNextArtwork,
    ): String? {
        requested += artwork.itemId
        return uris[artwork.itemId]
    }

    override fun retain(keepUris: Set<String>) {
        retained = keepUris
    }

    override fun clear() {
        cleared = true
    }
}
