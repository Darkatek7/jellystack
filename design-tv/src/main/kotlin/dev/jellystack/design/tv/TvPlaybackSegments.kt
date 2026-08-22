package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsApi
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackSegmentType
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class TvPlaybackActionKind {
    SEGMENT_SKIP,
    PLAY_NEXT,
}

internal data class TvPlaybackActionModel(
    val id: String,
    val kind: TvPlaybackActionKind,
    val label: String,
    val segmentAction: PlaybackSegmentAction? = null,
)

internal data class TvPlaybackPromptState(
    val visibleActionIds: Set<String> = emptySet(),
)

internal data class TvSegmentSkipSettingModel(
    val type: PlaybackSegmentType,
    val title: String,
    val mode: SegmentSkipMode,
    val onModeSelected: (SegmentSkipMode) -> Unit,
)

/** Owns only the transient TV presentation window; playback decisions stay in the shared coordinators. */
internal class TvPlaybackPromptCoordinator(
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(TvPlaybackPromptState())
    val state: StateFlow<TvPlaybackPromptState> = mutableState.asStateFlow()

    private var currentActionIds: Set<String> = emptySet()
    private val presentedActionIds = mutableSetOf<String>()
    private val expiryJobs = mutableMapOf<String, Job>()

    fun onPresentationChanged(
        actionIds: List<String>,
        controlsVisible: Boolean,
    ) {
        val updatedIds = actionIds.toCollection(linkedSetOf())
        val removedIds = currentActionIds - updatedIds
        removedIds.forEach { actionId ->
            expiryJobs.remove(actionId)?.cancel()
            presentedActionIds -= actionId
        }
        currentActionIds = updatedIds
        mutableState.value =
            TvPlaybackPromptState(
                visibleActionIds = mutableState.value.visibleActionIds.intersect(updatedIds),
            )
        if (!controlsVisible) {
            updatedIds.filterNot(presentedActionIds::contains).forEach(::startStandaloneWindow)
        }
    }

    fun release() {
        expiryJobs.values.forEach(Job::cancel)
        expiryJobs.clear()
        presentedActionIds.clear()
        currentActionIds = emptySet()
        mutableState.value = TvPlaybackPromptState()
    }

    private fun startStandaloneWindow(actionId: String) {
        presentedActionIds += actionId
        mutableState.value =
            TvPlaybackPromptState(
                visibleActionIds = mutableState.value.visibleActionIds + actionId,
            )
        expiryJobs[actionId] =
            scope.launch {
                delay(STANDALONE_PROMPT_MILLIS)
                expiryJobs.remove(actionId)
                mutableState.value =
                    TvPlaybackPromptState(
                        visibleActionIds = mutableState.value.visibleActionIds - actionId,
                    )
            }
    }

    private companion object {
        const val STANDALONE_PROMPT_MILLIS = 8_000L
    }
}

internal class TvJellyfinMediaSegmentsService(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val client: HttpClient,
) : JellyfinMediaSegmentsService {
    override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult {
        val environment = environmentProvider.current() ?: return JellyfinMediaSegmentsResult.Unavailable
        return JellyfinMediaSegmentsApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
        ).fetchSegments(itemId)
    }
}

internal fun tvPlaybackActionModels(
    segmentState: PlaybackSegmentState,
    continuationState: PlaybackContinuationState,
    isEpisode: Boolean,
    playbackPhase: PlaybackPhase,
    strings: TvStrings,
): List<TvPlaybackActionModel> {
    if (playbackPhase == PlaybackPhase.Ended) return emptyList()
    return buildList {
        segmentState.actions.forEach { action ->
            add(
                TvPlaybackActionModel(
                    id = "tv-player-action:segment:${action.type.name.lowercase()}:${action.segmentId}",
                    kind = TvPlaybackActionKind.SEGMENT_SKIP,
                    label = action.type.skipLabel(strings),
                    segmentAction = action,
                ),
            )
        }
        val hasActiveOutro = segmentState.activeSegments.any { it.type == PlaybackSegmentType.OUTRO }
        val nextTarget = continuationState.nextTarget
        if (isEpisode && hasActiveOutro && nextTarget != null) {
            add(
                TvPlaybackActionModel(
                    id = "tv-player-action:play-next:${nextTarget.mediaId}",
                    kind = TvPlaybackActionKind.PLAY_NEXT,
                    label = strings.playNextEpisode,
                ),
            )
        }
    }
}

internal fun routeTvSegmentSeek(
    positionMs: Long,
    syncPlayActive: Boolean,
    requestSyncSeek: (Long) -> Unit,
    requestLocalSeek: (Long) -> Unit,
) {
    if (syncPlayActive) requestSyncSeek(positionMs) else requestLocalSeek(positionMs)
}

internal suspend fun routeTvPlayNext(
    syncPlayActive: Boolean,
    requestSyncNext: () -> Unit,
    requestLocalNext: suspend () -> Unit,
) {
    if (syncPlayActive) requestSyncNext() else requestLocalNext()
}

internal class TvPlaybackCommandRouter(
    private val isSyncPlayActive: () -> Boolean,
    private val requestSyncSeek: (Long) -> Unit,
    private val requestLocalSeek: (Long) -> Unit,
    private val requestSyncNext: () -> Unit,
) {
    fun seekTo(positionMs: Long) {
        routeTvSegmentSeek(
            positionMs = positionMs,
            syncPlayActive = isSyncPlayActive(),
            requestSyncSeek = requestSyncSeek,
            requestLocalSeek = requestLocalSeek,
        )
    }

    suspend fun playNext(requestLocalNext: suspend () -> Unit) {
        routeTvPlayNext(
            syncPlayActive = isSyncPlayActive(),
            requestSyncNext = requestSyncNext,
            requestLocalNext = requestLocalNext,
        )
    }
}

internal fun AppSettings.segmentSkipMode(type: PlaybackSegmentType): SegmentSkipMode =
    when (type) {
        PlaybackSegmentType.INTRO -> introSkipMode
        PlaybackSegmentType.RECAP -> recapSkipMode
        PlaybackSegmentType.OUTRO -> outroSkipMode
        PlaybackSegmentType.PREVIEW -> previewSkipMode
        PlaybackSegmentType.COMMERCIAL -> commercialSkipMode
    }

internal fun tvSegmentSkipSettingModels(
    settings: AppSettings,
    strings: TvStrings,
    onModeSelected: (PlaybackSegmentType, SegmentSkipMode) -> Unit,
): List<TvSegmentSkipSettingModel> =
    listOf(
        TvSegmentSkipSettingModel(
            PlaybackSegmentType.INTRO,
            strings.introSegments,
            settings.introSkipMode,
        ) { onModeSelected(PlaybackSegmentType.INTRO, it) },
        TvSegmentSkipSettingModel(
            PlaybackSegmentType.RECAP,
            strings.recapSegments,
            settings.recapSkipMode,
        ) { onModeSelected(PlaybackSegmentType.RECAP, it) },
        TvSegmentSkipSettingModel(
            PlaybackSegmentType.OUTRO,
            strings.outroSegments,
            settings.outroSkipMode,
        ) { onModeSelected(PlaybackSegmentType.OUTRO, it) },
        TvSegmentSkipSettingModel(
            PlaybackSegmentType.PREVIEW,
            strings.previewSegments,
            settings.previewSkipMode,
        ) { onModeSelected(PlaybackSegmentType.PREVIEW, it) },
        TvSegmentSkipSettingModel(
            PlaybackSegmentType.COMMERCIAL,
            strings.commercialSegments,
            settings.commercialSkipMode,
        ) { onModeSelected(PlaybackSegmentType.COMMERCIAL, it) },
    )

private fun PlaybackSegmentType.skipLabel(strings: TvStrings): String =
    when (this) {
        PlaybackSegmentType.INTRO -> strings.skipIntro
        PlaybackSegmentType.RECAP -> strings.skipRecap
        PlaybackSegmentType.PREVIEW -> strings.skipPreview
        PlaybackSegmentType.COMMERCIAL -> strings.skipCommercial
        PlaybackSegmentType.OUTRO -> strings.skipCredits
    }
