package dev.jellystack.core.playback

import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class JellyfinOfflineProgressSyncer(
    private val repository: OfflinePlaybackProgressReporter,
    private val store: OfflinePlaybackEventStore,
) : OfflineProgressSyncer {
    private val mutex = Mutex()
    private val pending = store.read().toMutableList()

    override suspend fun onProgress(
        mediaId: String,
        positionMs: Long,
        durationMs: Long?,
    ) {
        mutex.withLock {
            pending.removeAll { it is OfflinePlaybackEvent.Progress && it.mediaId == mediaId }
            pending +=
                OfflinePlaybackEvent.Progress(
                    mediaId = mediaId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    timestampMs = Clock.System.now().toEpochMilliseconds(),
                )
            store.write(pending)
        }
        flush()
    }

    override suspend fun onCompleted(mediaId: String) {
        mutex.withLock {
            pending.removeAll { it.mediaId == mediaId }
            pending +=
                OfflinePlaybackEvent.Completed(
                    mediaId = mediaId,
                    timestampMs = Clock.System.now().toEpochMilliseconds(),
                )
            store.write(pending)
        }
        flush()
    }

    override suspend fun flush() {
        mutex.withLock {
            if (pending.isEmpty()) return
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val event = iterator.next()
                try {
                    when (event) {
                        is OfflinePlaybackEvent.Progress ->
                            repository.reportOfflineProgress(event.mediaId, event.positionMs)
                        is OfflinePlaybackEvent.Completed ->
                            repository.markOfflinePlaybackCompleted(event.mediaId)
                    }
                    iterator.remove()
                } catch (t: Throwable) {
                    JellystackLog.d("Unable to sync offline playback for ${event.mediaId}: ${t.message.orEmpty()}")
                    break
                }
            }
            store.write(pending)
        }
    }
}
