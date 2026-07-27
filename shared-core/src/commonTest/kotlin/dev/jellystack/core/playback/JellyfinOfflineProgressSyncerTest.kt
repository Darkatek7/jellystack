package dev.jellystack.core.playback

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JellyfinOfflineProgressSyncerTest {
    @Test
    fun failedProgressSyncStaysQueuedAndRetriesLater() =
        runTest {
            val store = MemoryOfflinePlaybackEventStore()
            val reporter = FakeOfflinePlaybackProgressReporter(failNext = true)
            val syncer = JellyfinOfflineProgressSyncer(repository = reporter, store = store)

            syncer.onProgress(mediaId = "episode-1", positionMs = 42_000, durationMs = 120_000)

            assertEquals(1, store.events.size)
            assertTrue(store.events.single() is OfflinePlaybackEvent.Progress)
            assertEquals(1, reporter.progressAttempts)

            syncer.flush()

            assertTrue(store.events.isEmpty())
            assertEquals(2, reporter.progressAttempts)
        }

    @Test
    fun failedCompletionSyncStaysQueuedAndRetriesLater() =
        runTest {
            val store = MemoryOfflinePlaybackEventStore()
            val reporter = FakeOfflinePlaybackProgressReporter(failNext = true)
            val syncer = JellyfinOfflineProgressSyncer(repository = reporter, store = store)

            syncer.onCompleted(mediaId = "movie-1")

            assertEquals(1, store.events.size)
            assertTrue(store.events.single() is OfflinePlaybackEvent.Completed)
            assertEquals(1, reporter.completedAttempts)

            syncer.flush()

            assertTrue(store.events.isEmpty())
            assertEquals(2, reporter.completedAttempts)
        }
}

private class MemoryOfflinePlaybackEventStore : OfflinePlaybackEventStore {
    var events: List<OfflinePlaybackEvent> = emptyList()
        private set

    override fun read(): List<OfflinePlaybackEvent> = events

    override fun write(events: List<OfflinePlaybackEvent>) {
        this.events = events.toList()
    }
}

private class FakeOfflinePlaybackProgressReporter(
    private var failNext: Boolean = false,
) : OfflinePlaybackProgressReporter {
    var progressAttempts = 0
        private set
    var completedAttempts = 0
        private set

    override suspend fun reportOfflineProgress(
        mediaId: String,
        positionMs: Long,
    ) {
        progressAttempts += 1
        failIfRequested()
    }

    override suspend fun markOfflinePlaybackCompleted(mediaId: String) {
        completedAttempts += 1
        failIfRequested()
    }

    private fun failIfRequested() {
        if (failNext) {
            failNext = false
            error("temporary failure")
        }
    }
}
