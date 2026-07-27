package dev.jellystack.core.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineDownloadPolicyTest {
    @Test
    fun wifiOnlyBlocksMeteredNetworkAndAllowsUnmeteredNetwork() {
        val policy = DownloadNetworkPolicy(wifiOnly = true, isUnmetered = false)

        assertFalse(policy.canDownload)
        assertTrue(policy.copy(isUnmetered = true).canDownload)
        assertTrue(policy.copy(wifiOnly = false).canDownload)
    }

    @Test
    fun defaultClearAllRemovesCompletedQueuedAndSubtitleEntries() {
        val manager = RecordingDownloadManager()

        manager.clearAll()

        assertEquals(setOf("video", "video::sub::2", "queued"), manager.removed.toSet())
    }
}

private class RecordingDownloadManager : OfflineDownloadManager {
    override val statuses =
        MutableStateFlow<Map<String, DownloadStatus>>(
            mapOf("video" to DownloadStatus.Queued("video"), "queued" to DownloadStatus.Queued("queued")),
        )
    override val offlineMedia =
        MutableStateFlow(
            listOf(
                OfflineMedia("video", "/video", null, null, 20),
                OfflineMedia("video::sub::2", "/subtitle", null, null, 2, kind = OfflineMediaKind.SUBTITLE),
            ),
        )
    val removed = mutableListOf<String>()

    override fun enqueue(request: DownloadRequest) = Unit

    override fun pause(mediaId: String) = Unit

    override fun resume(mediaId: String) = Unit

    override fun remove(mediaId: String) {
        removed += mediaId
    }
}
