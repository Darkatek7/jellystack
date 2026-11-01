package app.jellystack.tv.watchnext

import dev.jellystack.design.tv.TvWatchNextArtAspect
import dev.jellystack.design.tv.TvWatchNextArtwork
import dev.jellystack.design.tv.TvWatchNextKind
import dev.jellystack.design.tv.TvWatchNextMediaType

/** One program as it is written to the system provider. The art is a local content URI, never a server URL. */
internal data class WatchNextEntry(
    val internalId: String,
    val mediaType: TvWatchNextMediaType,
    val kind: TvWatchNextKind,
    val title: String,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val durationMs: Long?,
    val positionMs: Long?,
    val lastEngagementMillis: Long,
    val artUri: String?,
    val artAspect: TvWatchNextArtAspect?,
    val deepLink: String,
)

/** A row of this app in the provider (the provider only ever returns the caller's own rows). */
internal data class PublishedProgram(
    val rowId: Long,
    val internalId: String?,
    /** False once the user removed the card from the launcher; the system keeps such a row hidden. */
    val browsable: Boolean,
)

/** Blocking access to this app's Watch Next rows; every method may throw when the provider refuses. */
internal interface WatchNextGateway {
    fun readPublished(): List<PublishedProgram>

    fun insert(entry: WatchNextEntry): Boolean

    fun update(
        rowId: Long,
        entry: WatchNextEntry,
    ): Boolean

    fun delete(rowId: Long): Boolean
}

/** What survives a process restart: whose items are published, which ids exist, and which the user removed. */
internal interface WatchNextStateStore {
    var owner: String?
    var known: Set<String>
    var dismissed: Set<String>

    /** Random per install; keeps artwork file names unguessable for other apps. */
    val artSalt: String

    fun reset()
}

/** Artwork the launcher can read without credentials. */
internal interface WatchNextArt {
    /** A `content://` URI for the image, downloading it first when needed; null if it is unavailable. */
    suspend fun uriFor(
        serverKey: String,
        artwork: TvWatchNextArtwork,
    ): String?

    /** Deletes cached images other than those behind [keepUris]. */
    fun retain(keepUris: Set<String>)

    fun clear()
}
