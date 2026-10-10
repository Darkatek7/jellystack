package dev.jellystack.players

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasResult
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Chapters and trickplay of the playing item; empty while loading or when the server has none. */
data class PlaybackExtrasState(
    val mediaId: String? = null,
    val chapters: List<PlaybackChapter> = emptyList(),
    val trickplay: PlaybackTrickplay? = null,
)

/**
 * Loads chapters and trickplay once per played video and picks the trickplay manifest of the media
 * source in use. Like segments, extras never block playback: failures leave the state empty.
 */
class PlaybackExtrasCoordinator(
    private val scope: CoroutineScope,
    private val service: JellyfinPlaybackExtrasService,
    private val preferredThumbnailWidth: Int = DEFAULT_THUMBNAIL_WIDTH,
) {
    private val mutableState = MutableStateFlow(PlaybackExtrasState())
    val state: StateFlow<PlaybackExtrasState> = mutableState.asStateFlow()

    private var currentMediaId: String? = null
    private var currentSourceId: String? = null
    private var loaded: JellyfinPlaybackExtrasDto? = null
    private var loadJob: Job? = null
    private var generation = 0L

    fun onPlaybackState(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Preparing ->
                if (playbackState.mediaKind == PlaybackMediaKind.VIDEO) observe(playbackState.mediaId, null) else reset()
            is PlaybackState.Active ->
                if (playbackState.mediaKind == PlaybackMediaKind.VIDEO) {
                    observe(playbackState.mediaId, playbackState.stream.sourceId)
                } else {
                    reset()
                }
            PlaybackState.Stopped, is PlaybackState.PlaybackError -> reset()
        }
    }

    fun release() = reset()

    private fun observe(
        mediaId: String,
        sourceId: String?,
    ) {
        if (mediaId != currentMediaId) {
            startLoad(mediaId)
        }
        if (sourceId != null && sourceId != currentSourceId) {
            currentSourceId = sourceId
            publish()
        }
    }

    private fun startLoad(mediaId: String) {
        loadJob?.cancel()
        generation += 1
        val loadGeneration = generation
        currentMediaId = mediaId
        currentSourceId = null
        loaded = null
        mutableState.value = PlaybackExtrasState(mediaId = mediaId)
        loadJob =
            scope.launch {
                val result = runSuspendCatching { service.fetchExtras(mediaId) }.getOrNull()
                if (loadGeneration != generation) return@launch
                loaded = (result as? JellyfinPlaybackExtrasResult.Available)?.extras?.takeIf { it.id == mediaId }
                publish()
            }
    }

    private fun publish() {
        val mediaId = currentMediaId ?: return
        val extras = loaded ?: return
        val manifests =
            extras.trickplay.orEmpty().mapValues { (_, byWidth) ->
                byWidth
                    .mapNotNull { (width, info) ->
                        width.toIntOrNull()?.let {
                            it to
                                TrickplayManifest(
                                    width = info.width,
                                    height = info.height,
                                    columns = info.tileWidth,
                                    rows = info.tileHeight,
                                    thumbnailCount = info.thumbnailCount,
                                    intervalMs = info.interval,
                                )
                        }
                    }.toMap()
            }
        mutableState.value =
            PlaybackExtrasState(
                mediaId = mediaId,
                chapters =
                    extras.chapters
                        .orEmpty()
                        .mapIndexed { index, chapter ->
                            PlaybackChapter(
                                index = index,
                                name = chapter.name?.takeIf(String::isNotBlank),
                                startPositionMs = chapter.startPositionTicks.toMillisFromTicks(),
                                imageTag = chapter.imageTag,
                            )
                        }.filter { it.startPositionMs >= 0L }
                        .sortedBy(PlaybackChapter::startPositionMs),
                trickplay =
                    selectTrickplayManifest(manifests, currentSourceId, preferredThumbnailWidth)
                        ?.let { (sourceId, manifest) -> PlaybackTrickplay(mediaId, sourceId, manifest) },
            )
    }

    private fun reset() {
        generation += 1
        loadJob?.cancel()
        loadJob = null
        currentMediaId = null
        currentSourceId = null
        loaded = null
        mutableState.value = PlaybackExtrasState()
    }

    private companion object {
        const val DEFAULT_THUMBNAIL_WIDTH = 320
    }
}
