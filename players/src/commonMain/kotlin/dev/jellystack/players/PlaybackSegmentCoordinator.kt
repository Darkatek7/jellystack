package dev.jellystack.players

import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentDto
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaybackSegmentType {
    INTRO,
    RECAP,
    PREVIEW,
    COMMERCIAL,
    OUTRO,
}

data class PlaybackSegment(
    val id: String,
    val type: PlaybackSegmentType,
    val startPositionMs: Long,
    val endPositionMs: Long,
)

data class PlaybackSegmentAction(
    val mediaId: String,
    val segmentId: String,
    val type: PlaybackSegmentType,
    val endPositionMs: Long,
)

data class PlaybackSegmentState(
    val mediaId: String? = null,
    val isLoading: Boolean = false,
    val activeSegments: List<PlaybackSegment> = emptyList(),
    val actions: List<PlaybackSegmentAction> = emptyList(),
)

fun interface PlaybackSegmentModeProvider {
    fun modeFor(type: PlaybackSegmentType): SegmentSkipMode
}

fun interface PlaybackSeekAdapter {
    suspend fun seekTo(positionMs: Long)
}

class PlaybackSegmentCoordinator(
    private val scope: CoroutineScope,
    private val segmentService: JellyfinMediaSegmentsService,
    private val modeProvider: PlaybackSegmentModeProvider,
    private val seekAdapter: PlaybackSeekAdapter,
) {
    private val mutableState = MutableStateFlow(PlaybackSegmentState())
    val state: StateFlow<PlaybackSegmentState> = mutableState.asStateFlow()

    private var currentMediaId: String? = null
    private var currentPositionMs = 0L
    private var segments: List<PlaybackSegment> = emptyList()
    private val consumedAutoSegments = mutableSetOf<PlaybackSegment>()
    private var loadJob: Job? = null
    private var seekJob: Job? = null
    private var generation = 0L

    fun onPlaybackState(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Active -> {
                if (playbackState.mediaKind != PlaybackMediaKind.VIDEO) {
                    resetForNonVideo(playbackState.mediaId)
                    return
                }
                observeVideo(playbackState.mediaId, playbackState.positionMs)
            }
            is PlaybackState.Preparing -> {
                if (playbackState.mediaKind != PlaybackMediaKind.VIDEO) {
                    resetForNonVideo(playbackState.mediaId)
                    return
                }
                observeVideo(playbackState.mediaId, 0L)
            }
            PlaybackState.Stopped -> reset()
            is PlaybackState.PlaybackError -> reset()
        }
    }

    fun release() {
        reset()
    }

    fun skip(action: PlaybackSegmentAction) {
        if (action !in mutableState.value.actions) return
        dispatchSeek(action.endPositionMs)
    }

    private fun observeVideo(
        mediaId: String,
        positionMs: Long,
    ) {
        currentPositionMs = positionMs
        if (currentMediaId != mediaId) {
            startLoad(mediaId)
        } else {
            publishDerivedState(isLoading = loadJob?.isActive == true)
        }
    }

    private fun startLoad(mediaId: String) {
        loadJob?.cancel()
        seekJob?.cancel()
        generation += 1
        val loadGeneration = generation
        currentMediaId = mediaId
        segments = emptyList()
        consumedAutoSegments.clear()
        mutableState.value = PlaybackSegmentState(mediaId = mediaId, isLoading = true)
        loadJob =
            scope.launch {
                val loaded =
                    try {
                        when (val result = segmentService.fetchSegments(mediaId)) {
                            is JellyfinMediaSegmentsResult.Available ->
                                result.segments.mapNotNull { it.toPlaybackSegment(mediaId) }
                            JellyfinMediaSegmentsResult.Unavailable -> emptyList()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        emptyList()
                    }
                if (loadGeneration != generation || currentMediaId != mediaId) return@launch
                segments = loaded
                publishDerivedState(isLoading = false)
            }
    }

    private fun publishDerivedState(isLoading: Boolean) {
        val mediaId = currentMediaId ?: return
        val active = segments.filter { currentPositionMs >= it.startPositionMs && currentPositionMs < it.endPositionMs }
        val activeWithModes = active.map { it to modeProvider.modeFor(it.type) }
        mutableState.value =
            PlaybackSegmentState(
                mediaId = mediaId,
                isLoading = isLoading,
                activeSegments = active,
                actions =
                    activeWithModes
                        .filter { (_, mode) -> mode == SegmentSkipMode.SHOW_BUTTON }
                        .map { (segment, _) ->
                            PlaybackSegmentAction(
                                mediaId = mediaId,
                                segmentId = segment.id,
                                type = segment.type,
                                endPositionMs = segment.endPositionMs,
                            )
                        },
            )
        val pendingAutoSegments =
            activeWithModes
                .filter { (segment, mode) -> mode == SegmentSkipMode.AUTO_SKIP && segment !in consumedAutoSegments }
                .map { (segment, _) -> segment }
        if (pendingAutoSegments.isNotEmpty()) {
            consumedAutoSegments += pendingAutoSegments
            dispatchSeek(pendingAutoSegments.maxOf { it.endPositionMs })
        }
    }

    private fun dispatchSeek(positionMs: Long) {
        seekJob?.cancel()
        seekJob = scope.launch { seekAdapter.seekTo(positionMs) }
    }

    private fun resetForNonVideo(mediaId: String) {
        if (currentMediaId == mediaId && segments.isEmpty() && loadJob?.isActive != true) return
        reset()
        currentMediaId = mediaId
        mutableState.value = PlaybackSegmentState(mediaId = mediaId)
    }

    private fun reset() {
        generation += 1
        loadJob?.cancel()
        loadJob = null
        seekJob?.cancel()
        seekJob = null
        currentMediaId = null
        currentPositionMs = 0L
        segments = emptyList()
        consumedAutoSegments.clear()
        mutableState.value = PlaybackSegmentState()
    }
}

private fun JellyfinMediaSegmentDto.toPlaybackSegment(expectedItemId: String): PlaybackSegment? {
    if (itemId != expectedItemId || startTicks < 0L || endTicks <= startTicks) return null
    val segmentType = PlaybackSegmentType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return null
    val startPositionMs = startTicks.toMillisFromTicks()
    val endPositionMs = endTicks.toMillisFromTicks()
    if (endPositionMs <= startPositionMs) return null
    return PlaybackSegment(
        id = id,
        type = segmentType,
        startPositionMs = startPositionMs,
        endPositionMs = endPositionMs,
    )
}
