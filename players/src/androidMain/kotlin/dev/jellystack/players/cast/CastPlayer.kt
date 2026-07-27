package dev.jellystack.players.cast

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val DEFAULT_CONTENT_TYPE = "application/x-mpegurl"

internal class CastPlayer(
    private val clientProvider: () -> RemoteMediaClient?,
    private val onLoadResult: ((Status) -> Unit)? = null,
    private val onSubtitleFallback: ((String) -> Unit)? = null,
) {
    private var lastLoadedMediaId: String? = null
    private var lastLoadedStreamUrl: String? = null

    suspend fun ensureLoaded(snapshot: CastSessionSnapshot): SubtitleSelectionResult {
        val client = clientProvider() ?: return SubtitleSelectionResult(snapshot.selectedSubtitleTrackId, null)
        val buildResult = buildLoadRequestResult(snapshot)
        if (snapshot.streamUrl == lastLoadedStreamUrl && snapshot.mediaId == lastLoadedMediaId) {
            return SubtitleSelectionResult(
                selectedTrackId = buildResult.selectedSubtitleTrackId,
                userMessage = null,
            )
        }
        val mediaLoadRequest = buildResult.request
        withContext(Dispatchers.Main) {
            client.load(mediaLoadRequest).setResultCallback { result ->
                onLoadResult?.invoke(result.status)
            }
        }
        lastLoadedMediaId = snapshot.mediaId
        lastLoadedStreamUrl = snapshot.streamUrl
        buildResult.userMessage?.let { message -> onSubtitleFallback?.invoke(message) }
        return SubtitleSelectionResult(
            selectedTrackId = buildResult.selectedSubtitleTrackId,
            userMessage = buildResult.userMessage,
        )
    }

    suspend fun play() {
        clientProvider()?.let { client ->
            withContext(Dispatchers.Main) {
                client.play()
            }
        }
    }

    suspend fun pause() {
        clientProvider()?.let { client ->
            withContext(Dispatchers.Main) {
                client.pause()
            }
        }
    }

    suspend fun stop() {
        clientProvider()?.let { client ->
            withContext(Dispatchers.Main) {
                client.stop()
            }
        }
    }

    suspend fun seek(positionMs: Long) {
        if (positionMs < 0) return
        clientProvider()?.let { client ->
            val options =
                MediaSeekOptions
                    .Builder()
                    .setPosition(positionMs)
                    .build()
            withContext(Dispatchers.Main) {
                client.seek(options)
            }
        }
    }

    suspend fun selectSubtitleTrack(
        snapshot: CastSessionSnapshot,
        trackId: String?,
    ): SubtitleSelectionResult {
        val client = clientProvider() ?: return SubtitleSelectionResult(trackId, null)
        val selection = resolveSubtitleSelection(snapshot, requestedTrackId = trackId)
        withContext(Dispatchers.Main) {
            client.setActiveMediaTracks(selection.activeTrackIds)
        }
        selection.userMessage?.let { message -> onSubtitleFallback?.invoke(message) }
        return SubtitleSelectionResult(selection.selectedSubtitleTrackId, selection.userMessage)
    }

    internal fun buildLoadRequest(snapshot: CastSessionSnapshot): MediaLoadRequestData = buildLoadRequestResult(snapshot).request

    private fun buildLoadRequestResult(snapshot: CastSessionSnapshot): BuildLoadRequestResult {
        val metadata =
            MediaMetadata(
                if (!snapshot.seriesName.isNullOrBlank() || !snapshot.episodeName.isNullOrBlank()) {
                    MediaMetadata.MEDIA_TYPE_TV_SHOW
                } else {
                    MediaMetadata.MEDIA_TYPE_MOVIE
                },
            ).apply {
                val primaryTitle = snapshot.title ?: snapshot.episodeName ?: snapshot.seriesName ?: snapshot.mediaId
                putString(MediaMetadata.KEY_TITLE, primaryTitle)
                snapshot.seriesName?.let { putString(MediaMetadata.KEY_SERIES_TITLE, it) }
                snapshot.episodeName?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
                snapshot.artworkUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { artworkUrl ->
                        addImage(WebImage(Uri.parse(artworkUrl)))
                    }
            }
        val subtitleSelection = resolveSubtitleSelection(snapshot, requestedTrackId = snapshot.selectedSubtitleTrackId)
        val tracks =
            subtitleSelection.supportedTracks.map { track ->
                val trackBuilder =
                    MediaTrack
                        .Builder(castTrackIdFor(track.id), MediaTrack.TYPE_TEXT)
                        .setContentId(track.url)
                        .setContentType(track.mimeType)
                        .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                track.language?.let { trackBuilder.setLanguage(it) }
                track.label?.let { trackBuilder.setName(it) }
                trackBuilder.setCustomData(
                    JSONObject().apply {
                        put("appTrackId", track.id)
                    },
                )
                trackBuilder.build()
            }
        val mediaInfo =
            MediaInfo
                .Builder(snapshot.streamUrl)
                .setContentType(snapshot.contentType ?: DEFAULT_CONTENT_TYPE)
                .setStreamType(
                    when (snapshot.streamType) {
                        CastStreamType.LIVE -> MediaInfo.STREAM_TYPE_LIVE
                        CastStreamType.BUFFERED -> MediaInfo.STREAM_TYPE_BUFFERED
                    },
                ).setMetadata(metadata)
                .setMediaTracks(tracks)
                .setTextTrackStyle(TextTrackStyle())
                .setCustomData(
                    JSONObject()
                        .apply {
                            put("mediaId", snapshot.mediaId)
                            snapshot.title?.let { put("title", it) }
                            snapshot.seriesName?.let { put("seriesName", it) }
                            snapshot.episodeName?.let { put("episodeName", it) }
                            snapshot.artworkUrl?.let { put("artworkUrl", it) }
                        },
                ).build()
        val requestBuilder =
            MediaLoadRequestData
                .Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(!snapshot.isPaused)
        if (snapshot.positionMs > 0) {
            requestBuilder.setCurrentTime(snapshot.positionMs)
        }
        requestBuilder.setActiveTrackIds(subtitleSelection.activeTrackIds)
        return BuildLoadRequestResult(
            request = requestBuilder.build(),
            selectedSubtitleTrackId = subtitleSelection.selectedSubtitleTrackId,
            userMessage = subtitleSelection.userMessage,
        )
    }

    private fun resolveSubtitleSelection(
        snapshot: CastSessionSnapshot,
        requestedTrackId: String?,
    ): SubtitleSelection {
        val supportedTracks = snapshot.subtitleTracks.filter { track -> isCastSupportedSubtitle(track.mimeType) }
        val explicitOff = requestedTrackId == null
        if (explicitOff) {
            return SubtitleSelection(
                supportedTracks = supportedTracks,
                selectedSubtitleTrackId = null,
                activeTrackIds = longArrayOf(),
                userMessage = null,
            )
        }

        val selectedSupported = supportedTracks.firstOrNull { track -> track.id == requestedTrackId }
        if (selectedSupported != null) {
            return SubtitleSelection(
                supportedTracks = supportedTracks,
                selectedSubtitleTrackId = selectedSupported.id,
                activeTrackIds = longArrayOf(castTrackIdFor(selectedSupported.id)),
                userMessage = null,
            )
        }

        val requestedTrack = snapshot.subtitleTracks.firstOrNull { track -> track.id == requestedTrackId }
        val fallbackTrack =
            requestedTrack
                ?.language
                ?.let { requestedLanguage ->
                    supportedTracks.firstOrNull { track ->
                        track.language?.equals(requestedLanguage, ignoreCase = true) == true
                    }
                } ?: supportedTracks.firstOrNull()
        return if (fallbackTrack != null) {
            SubtitleSelection(
                supportedTracks = supportedTracks,
                selectedSubtitleTrackId = fallbackTrack.id,
                activeTrackIds = longArrayOf(castTrackIdFor(fallbackTrack.id)),
                userMessage =
                    "Cast subtitle fallback: Selected subtitle is unsupported; using ${fallbackTrack.label ?: fallbackTrack.language ?: "WebVTT"}.",
            )
        } else {
            SubtitleSelection(
                supportedTracks = supportedTracks,
                selectedSubtitleTrackId = null,
                activeTrackIds = longArrayOf(),
                userMessage =
                    "Cast subtitle fallback: Selected subtitle format is unsupported. Subtitles were turned off.",
            )
        }
    }

    private fun isCastSupportedSubtitle(mimeType: String?): Boolean {
        val normalized = mimeType?.trim()?.lowercase().orEmpty()
        return normalized == "text/vtt" || normalized == "application/vtt"
    }

    private fun castTrackIdFor(trackId: String): Long {
        var hash = 1125899906842597L
        trackId.forEach { character ->
            hash = (hash * 31) + character.code
        }
        return (hash and Long.MAX_VALUE).coerceAtLeast(1L)
    }

    internal data class SubtitleSelectionResult(
        val selectedTrackId: String?,
        val userMessage: String?,
    )

    private data class SubtitleSelection(
        val supportedTracks: List<CastSubtitleTrack>,
        val selectedSubtitleTrackId: String?,
        val activeTrackIds: LongArray,
        val userMessage: String?,
    )

    private data class BuildLoadRequestResult(
        val request: MediaLoadRequestData,
        val selectedSubtitleTrackId: String?,
        val userMessage: String?,
    )
}
