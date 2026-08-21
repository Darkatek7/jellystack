package app.jellystack.mobile.playback

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsApi
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegmentAction
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
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

internal enum class AndroidPlaybackActionKind {
    SEGMENT_SKIP,
    PLAY_NEXT,
}

internal data class AndroidPlaybackActionLabels(
    val skipIntro: String,
    val skipRecap: String,
    val skipPreview: String,
    val skipCommercial: String,
    val skipCredits: String,
    val playNextEpisode: String,
)

internal data class AndroidPlaybackActionModel(
    val id: String,
    val kind: AndroidPlaybackActionKind,
    val label: String,
    val segmentAction: PlaybackSegmentAction? = null,
)

internal class AndroidPlaybackCommandRouter(
    private val isSyncPlayActive: () -> Boolean,
    private val requestSyncSeek: (Long) -> Unit,
    private val requestPlaybackSeek: (Long) -> Unit,
    private val requestSyncNext: () -> Unit,
) {
    fun seekTo(positionMs: Long) {
        if (isSyncPlayActive()) requestSyncSeek(positionMs) else requestPlaybackSeek(positionMs)
    }

    suspend fun playNext(requestPlaybackNext: suspend () -> Unit) {
        if (isSyncPlayActive()) requestSyncNext() else requestPlaybackNext()
    }
}

internal fun createAndroidPlaybackSegmentCoordinator(
    scope: CoroutineScope,
    segmentService: JellyfinMediaSegmentsService,
    modeProvider: PlaybackSegmentModeProvider,
    commandRouter: AndroidPlaybackCommandRouter,
): PlaybackSegmentCoordinator =
    PlaybackSegmentCoordinator(
        scope = scope,
        segmentService = segmentService,
        modeProvider = modeProvider,
        seekAdapter = PlaybackSeekAdapter(commandRouter::seekTo),
    )

internal fun createAndroidPlaybackContinuationTarget(
    mediaId: String,
    title: String,
    commandRouter: AndroidPlaybackCommandRouter,
    requestPlaybackNext: suspend () -> Unit,
): PlaybackContinuationTarget =
    PlaybackContinuationTarget(mediaId, title) {
        commandRouter.playNext(requestPlaybackNext)
    }

internal class AndroidJellyfinMediaSegmentsService(
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

internal fun AppSettings.segmentSkipMode(type: PlaybackSegmentType): SegmentSkipMode =
    when (type) {
        PlaybackSegmentType.INTRO -> introSkipMode
        PlaybackSegmentType.RECAP -> recapSkipMode
        PlaybackSegmentType.OUTRO -> outroSkipMode
        PlaybackSegmentType.PREVIEW -> previewSkipMode
        PlaybackSegmentType.COMMERCIAL -> commercialSkipMode
    }

internal fun androidPlaybackActionModels(
    segmentState: PlaybackSegmentState,
    continuationState: PlaybackContinuationState,
    isEpisode: Boolean,
    playbackPhase: PlaybackPhase,
    labels: AndroidPlaybackActionLabels,
): List<AndroidPlaybackActionModel> {
    if (playbackPhase == PlaybackPhase.Ended) return emptyList()
    return buildList {
        segmentState.actions.forEach { action ->
            add(
                AndroidPlaybackActionModel(
                    id = "phone-player-action:segment:${action.type.name.lowercase()}:${action.segmentId}",
                    kind = AndroidPlaybackActionKind.SEGMENT_SKIP,
                    label = action.type.skipLabel(labels),
                    segmentAction = action,
                ),
            )
        }
        val hasActiveOutro = segmentState.activeSegments.any { it.type == PlaybackSegmentType.OUTRO }
        val nextTarget = continuationState.nextTarget
        if (isEpisode && hasActiveOutro && nextTarget != null) {
            add(
                AndroidPlaybackActionModel(
                    id = "phone-player-action:play-next:${nextTarget.mediaId}",
                    kind = AndroidPlaybackActionKind.PLAY_NEXT,
                    label = labels.playNextEpisode,
                ),
            )
        }
    }
}

internal data class AndroidPlaybackPromptState(
    val visibleActionIds: Set<String> = emptySet(),
)

/** Owns only the transient phone presentation window; shared coordinators own playback decisions. */
internal class AndroidPlaybackPromptCoordinator(
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(AndroidPlaybackPromptState())
    val state: StateFlow<AndroidPlaybackPromptState> = mutableState.asStateFlow()

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
            AndroidPlaybackPromptState(
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
        mutableState.value = AndroidPlaybackPromptState()
    }

    private fun startStandaloneWindow(actionId: String) {
        presentedActionIds += actionId
        mutableState.value =
            AndroidPlaybackPromptState(
                visibleActionIds = mutableState.value.visibleActionIds + actionId,
            )
        expiryJobs[actionId] =
            scope.launch {
                delay(STANDALONE_PROMPT_MILLIS)
                expiryJobs.remove(actionId)
                mutableState.value =
                    AndroidPlaybackPromptState(
                        visibleActionIds = mutableState.value.visibleActionIds - actionId,
                    )
            }
    }

    private companion object {
        const val STANDALONE_PROMPT_MILLIS = 8_000L
    }
}

private fun PlaybackSegmentType.skipLabel(labels: AndroidPlaybackActionLabels): String =
    when (this) {
        PlaybackSegmentType.INTRO -> labels.skipIntro
        PlaybackSegmentType.RECAP -> labels.skipRecap
        PlaybackSegmentType.PREVIEW -> labels.skipPreview
        PlaybackSegmentType.COMMERCIAL -> labels.skipCommercial
        PlaybackSegmentType.OUTRO -> labels.skipCredits
    }
