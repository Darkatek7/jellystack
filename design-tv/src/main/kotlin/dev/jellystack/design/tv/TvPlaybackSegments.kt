package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.SegmentSkipMode
import dev.jellystack.network.jellyfin.JellyfinClientIdentity
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsApi
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsResult
import dev.jellystack.network.jellyfin.JellyfinMediaSegmentsService
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasApi
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasResult
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasService
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
    WATCH_CREDITS,
}

internal data class TvPlaybackActionModel(
    val id: String,
    val kind: TvPlaybackActionKind,
    val label: String,
    val segmentAction: PlaybackSegmentAction? = null,
    /** Up-next cards: a small heading, the next episode, and its still. */
    val kicker: String? = null,
    val detail: String? = null,
    val imageUrl: String? = null,
)

/** What the prompts depend on besides the segment and continuation state. */
internal data class TvPlaybackActionContext(
    val isEpisode: Boolean,
    val phase: PlaybackPhase,
    /** From [dev.jellystack.players.shouldOfferUpNext]: credits started or the episode is about to end. */
    val upNextDue: Boolean,
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
    private var persistentActionIds: Set<String> = emptySet()
    private val presentedActionIds = mutableSetOf<String>()
    private val expiryJobs = mutableMapOf<String, Job>()

    fun onPresentationChanged(
        actionIds: List<String>,
        controlsVisible: Boolean,
        persistentActionIds: Set<String> = emptySet(),
    ) {
        this.persistentActionIds = persistentActionIds
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
        if (actionId in persistentActionIds) return
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
            identity =
                JellyfinClientIdentity(
                    appVersion = environment.clientVersion,
                    deviceName = environment.deviceName,
                    deviceId = environment.deviceId ?: "unknown",
                ),
        ).fetchSegments(itemId)
    }
}

internal class TvJellyfinPlaybackExtrasService(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val client: HttpClient,
) : JellyfinPlaybackExtrasService {
    override suspend fun fetchExtras(itemId: String): JellyfinPlaybackExtrasResult {
        val environment = environmentProvider.current() ?: return JellyfinPlaybackExtrasResult.Unavailable
        return JellyfinPlaybackExtrasApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            userId = environment.userId,
            identity =
                JellyfinClientIdentity(
                    appVersion = environment.clientVersion,
                    deviceName = environment.deviceName,
                    deviceId = environment.deviceId ?: "unknown",
                ),
        ).fetchExtras(itemId)
    }
}

internal fun tvPlaybackActionModels(
    segmentState: PlaybackSegmentState,
    continuationState: PlaybackContinuationState,
    context: TvPlaybackActionContext,
    strings: TvStrings,
): List<TvPlaybackActionModel> {
    if (context.phase == PlaybackPhase.Ended) return emptyList()
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
        val nextTarget = continuationState.nextTarget
        if (context.isEpisode && context.upNextDue && nextTarget != null) {
            add(
                TvPlaybackActionModel(
                    id = "tv-player-action:play-next:${nextTarget.mediaId}",
                    kind = TvPlaybackActionKind.PLAY_NEXT,
                    label = strings.player.playNextEpisode,
                    kicker = strings.player.upNext,
                    detail = listOfNotNull(nextTarget.subtitle, nextTarget.title).joinToString(" · "),
                    imageUrl = nextTarget.imageUrl,
                ),
            )
            add(
                TvPlaybackActionModel(
                    id = "tv-player-action:watch-credits:${nextTarget.mediaId}",
                    kind = TvPlaybackActionKind.WATCH_CREDITS,
                    label = strings.player.watchCredits,
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
        PlaybackSegmentType.INTRO -> strings.player.skipIntro
        PlaybackSegmentType.RECAP -> strings.player.skipRecap
        PlaybackSegmentType.PREVIEW -> strings.player.skipPreview
        PlaybackSegmentType.COMMERCIAL -> strings.player.skipCommercial
        PlaybackSegmentType.OUTRO -> strings.player.skipCredits
    }
