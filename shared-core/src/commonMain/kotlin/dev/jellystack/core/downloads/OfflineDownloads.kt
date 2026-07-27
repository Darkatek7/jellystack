package dev.jellystack.core.downloads

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class DownloadRequest(
    val mediaId: String,
    val downloadUrl: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val expectedSizeBytes: Long?,
    val checksumSha256: String?,
    val kind: OfflineMediaKind = OfflineMediaKind.VIDEO,
    val language: String? = null,
    val relativePath: String? = null,
    val metadata: OfflineMediaMetadata? = null,
)

@Serializable
data class OfflineMediaMetadata(
    val itemId: String,
    val libraryId: String? = null,
    val name: String,
    val sortName: String? = null,
    val overview: String? = null,
    val type: String,
    val mediaType: String? = null,
    val primaryImageTag: String? = null,
    val thumbImageTag: String? = null,
    val backdropImageTag: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesPrimaryImageTag: String? = null,
    val seriesThumbImageTag: String? = null,
    val seriesBackdropImageTag: String? = null,
    val parentLogoImageTag: String? = null,
    val runTimeTicks: Long? = null,
    val positionTicks: Long? = null,
    val playedPercentage: Double? = null,
    val productionYear: Int? = null,
    val premiereDate: String? = null,
    val officialRating: String? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val seasonId: String? = null,
    val episodeTitle: String? = null,
    val dateCreated: String? = null,
    val logoImageTag: String? = null,
    val artImageTag: String? = null,
    val bannerImageTag: String? = null,
    val seriesLogoImageTag: String? = null,
    val seriesArtImageTag: String? = null,
    val seriesBannerImageTag: String? = null,
)

sealed class DownloadStatus {
    data class Queued(
        val mediaId: String,
    ) : DownloadStatus()

    data class InProgress(
        val mediaId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus()

    data class Paused(
        val mediaId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus()

    data class WaitingForNetwork(
        val mediaId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus()

    data class Completed(
        val mediaId: String,
        val filePath: String,
        val bytesDownloaded: Long,
    ) : DownloadStatus()

    data class Failed(
        val mediaId: String,
        val cause: Throwable,
    ) : DownloadStatus()
}

interface OfflineDownloadManager {
    val statuses: StateFlow<Map<String, DownloadStatus>>
    val offlineMedia: StateFlow<List<OfflineMedia>>

    fun enqueue(request: DownloadRequest)

    fun pause(mediaId: String)

    fun resume(mediaId: String)

    fun remove(mediaId: String)

    fun clearAll() {
        (offlineMedia.value.map { it.mediaId } + statuses.value.keys)
            .distinct()
            .forEach(::remove)
    }
}

data class DownloadNetworkPolicy(
    val wifiOnly: Boolean,
    val isUnmetered: Boolean,
) {
    val canDownload: Boolean get() = !wifiOnly || isUnmetered
}

@Serializable
enum class OfflineMediaKind {
    VIDEO,
    SUBTITLE,
}

@Serializable
data class OfflineMedia(
    val mediaId: String,
    val filePath: String,
    val mimeType: String?,
    val checksumSha256: String?,
    val sizeBytes: Long?,
    val kind: OfflineMediaKind = OfflineMediaKind.VIDEO,
    val language: String? = null,
    val relativePath: String? = null,
    val metadata: OfflineMediaMetadata? = null,
) {
    fun isValid(): Boolean = filePath.isNotBlank()
}

interface OfflineMediaStore {
    fun read(mediaId: String): OfflineMedia?

    fun write(media: OfflineMedia)

    fun writeAll(media: List<OfflineMedia>)

    fun remove(mediaId: String)

    fun list(): List<OfflineMedia>
}

class InMemoryOfflineMediaStore : OfflineMediaStore {
    private val backing = mutableMapOf<String, OfflineMedia>()

    override fun read(mediaId: String): OfflineMedia? = backing[mediaId]

    override fun write(media: OfflineMedia) {
        backing[media.mediaId] = media
    }

    override fun writeAll(media: List<OfflineMedia>) {
        media.forEach { write(it) }
    }

    override fun remove(mediaId: String) {
        backing.remove(mediaId)
    }

    override fun list(): List<OfflineMedia> = backing.values.toList()
}
