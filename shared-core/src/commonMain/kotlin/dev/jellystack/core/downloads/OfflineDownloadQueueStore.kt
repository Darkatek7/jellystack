package dev.jellystack.core.downloads

interface OfflineDownloadQueueStore {
    fun all(): List<DownloadRequest>

    fun put(request: DownloadRequest)

    fun remove(mediaId: String)

    fun clear()
}
