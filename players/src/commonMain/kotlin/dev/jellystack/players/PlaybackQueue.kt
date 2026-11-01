package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/**
 * Tells the [PlaybackContinuationCoordinator] what plays after an item when the item belongs to a list the user
 * started on purpose (a collection or a playlist) instead of a series.
 */
interface PlaybackQueueSource {
    /** Whether [mediaId] is an entry of the list being played. */
    fun contains(mediaId: String): Boolean

    /** The target after [mediaId], or null when it is the last one or nothing after it can be played. */
    suspend fun resolveNext(mediaId: String): PlaybackContinuationTarget?
}

/**
 * The ordered entries of "Play all" and of an album or artist (videos or music tracks). It only holds the order; how an entry becomes a [PlaybackContinuationTarget]
 * (detail request, artwork, the player command) is up to the caller of [source], which keeps this class platform-free.
 */
class PlaybackQueue {
    private var entries: List<JellyfinItem> = emptyList()

    fun replace(items: List<JellyfinItem>) {
        entries = items.distinctBy(JellyfinItem::id)
    }

    /**
     * Replaces the entries with [items] and returns the one to play first, or null for an empty list.
     *
     * Without [shuffle] the order is kept and [startIndex] picks where to start, so "play from this track" lets the
     * rest of the list follow it. With [shuffle] the order is decided here, once, by [shuffle]: a [startIndex] puts
     * that entry first and shuffles the others after it; no [startIndex] shuffles everything.
     */
    fun start(
        items: List<JellyfinItem>,
        startIndex: Int? = 0,
        shuffle: Random? = null,
    ): JellyfinItem? {
        val unique = items.distinctBy(JellyfinItem::id)
        val chosen = startIndex?.let(unique::getOrNull)
        entries =
            when {
                shuffle == null -> unique
                chosen == null -> unique.shuffled(shuffle)
                else -> listOf(chosen) + (unique - chosen).shuffled(shuffle)
            }
        return when {
            entries.isEmpty() -> null
            shuffle != null -> entries.first()
            else -> chosen ?: entries.first()
        }
    }

    fun clear() {
        entries = emptyList()
    }

    fun contains(mediaId: String): Boolean = entries.any { it.id == mediaId }

    /** The entries after [mediaId], in play order; empty for the last entry or an id outside the queue. */
    fun entriesAfter(mediaId: String): List<JellyfinItem> {
        val snapshot = entries
        val index = snapshot.indexOfFirst { it.id == mediaId }
        return if (index < 0) emptyList() else snapshot.drop(index + 1)
    }

    /** The entry before [mediaId], or null for the first entry or an id outside the queue. */
    fun entryBefore(mediaId: String): JellyfinItem? {
        val snapshot = entries
        val index = snapshot.indexOfFirst { it.id == mediaId }
        return if (index > 0) snapshot[index - 1] else null
    }

    /** A source that turns entries into targets with [load]; an entry [load] cannot turn into one is skipped. */
    fun source(load: suspend (JellyfinItem) -> PlaybackContinuationTarget?): PlaybackQueueSource =
        object : PlaybackQueueSource {
            override fun contains(mediaId: String): Boolean = this@PlaybackQueue.contains(mediaId)

            override suspend fun resolveNext(mediaId: String): PlaybackContinuationTarget? {
                for (candidate in entriesAfter(mediaId)) {
                    val target =
                        try {
                            load(candidate)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                    if (target != null) return target
                }
                return null
            }
        }
}
