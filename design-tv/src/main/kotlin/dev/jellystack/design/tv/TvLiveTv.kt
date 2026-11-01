package dev.jellystack.design.tv

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.livetv.LiveTvChannel
import dev.jellystack.core.livetv.LiveTvNowNext
import dev.jellystack.core.livetv.LiveTvProgram
import dev.jellystack.core.livetv.LiveTvRepository
import dev.jellystack.core.livetv.nowAndNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Where the Live TV screen reads channels and guide entries from. */
internal interface TvLiveTvLoader {
    suspend fun channels(): List<LiveTvChannel>

    suspend fun programs(
        channelIds: List<String>,
        at: Instant,
    ): Map<String, List<LiveTvProgram>>
}

internal fun LiveTvRepository.tvLoader() =
    object : TvLiveTvLoader {
        override suspend fun channels() = this@tvLoader.channels()

        override suspend fun programs(
            channelIds: List<String>,
            at: Instant,
        ) = this@tvLoader.programs(channelIds, at)
    }

internal sealed interface TvLiveTvState {
    data object Loading : TvLiveTvState

    /** No channels: the server has no Live TV, or none this user may watch. */
    data object Empty : TvLiveTvState

    data object Failed : TvLiveTvState

    /** [guide] holds the programs of the channels that were on screen; [at] is when they were last evaluated. */
    data class Ready(
        val channels: List<LiveTvChannel>,
        val guide: Map<String, List<LiveTvProgram>>,
        val at: Instant,
    ) : TvLiveTvState {
        fun nowAndNext(channelId: String): LiveTvNowNext = guide[channelId].orEmpty().nowAndNext(at)
    }
}

/**
 * Holds the channels and the guide outside the screen, so Back from the player returns to the list as it was.
 *
 * Programs load for the channels on screen only ([setVisible]). One timer waits for the first change among them (a
 * running program ending), then re-evaluates and loads what is missing; there is no polling.
 */
internal class TvLiveTvCoordinator(
    private val scope: CoroutineScope,
    private val loader: TvLiveTvLoader,
    private val clock: Clock = Clock.System,
) {
    private val mutableState = MutableStateFlow<TvLiveTvState>(TvLiveTvState.Loading)
    val state: StateFlow<TvLiveTvState> = mutableState.asStateFlow()
    private var visibleIds: List<String> = emptyList()
    private var channelsJob: Job? = null
    private var guideJob: Job? = null
    private var refreshJob: Job? = null

    /** Shows the channels now (the loaded ones, else a loading state) and refreshes them from the server. */
    fun show() = loadChannels(showLoading = mutableState.value !is TvLiveTvState.Ready)

    fun retry() = loadChannels(showLoading = true)

    /** The channels on screen; their programs are the only ones loaded. */
    fun setVisible(channelIds: List<String>) {
        if (channelIds == visibleIds) return
        visibleIds = channelIds
        loadGuide()
    }

    private fun loadChannels(showLoading: Boolean) {
        channelsJob?.cancel()
        if (showLoading) mutableState.value = TvLiveTvState.Loading
        channelsJob =
            scope.launch {
                runSuspendCatching { loader.channels() }
                    .onSuccess { channels ->
                        mutableState.update { current -> current.withChannels(channels) }
                        loadGuide()
                    }.onFailure {
                        // A refresh that fails keeps what the user already sees.
                        mutableState.update { current -> current as? TvLiveTvState.Ready ?: TvLiveTvState.Failed }
                    }
            }
    }

    private fun TvLiveTvState.withChannels(channels: List<LiveTvChannel>): TvLiveTvState {
        if (channels.isEmpty()) return TvLiveTvState.Empty
        val ids = channels.mapTo(HashSet(), LiveTvChannel::id)
        val known = (this as? TvLiveTvState.Ready)?.guide.orEmpty().filterKeys { it in ids }
        return TvLiveTvState.Ready(channels, known, clock.now())
    }

    private fun loadGuide() {
        val ready = mutableState.value as? TvLiveTvState.Ready ?: return
        val now = clock.now()
        val missing = visibleIds.filter { id -> ready.nowAndNextAt(id, now).isIncomplete() }
        guideJob?.cancel()
        if (missing.isEmpty()) {
            scheduleRefresh()
            return
        }
        guideJob =
            scope.launch {
                // The guide is advisory: a failed load leaves what is shown, and the channels stay playable.
                runSuspendCatching { loader.programs(missing, now) }.onSuccess { loaded ->
                    mutableState.update { current ->
                        if (current is TvLiveTvState.Ready) {
                            val loadedGuide = missing.associateWith { loaded[it].orEmpty() }
                            current.copy(guide = current.guide + loadedGuide, at = clock.now())
                        } else {
                            current
                        }
                    }
                }
                scheduleRefresh()
            }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        val ready = mutableState.value as? TvLiveTvState.Ready ?: return
        val now = clock.now()
        val changesAt = visibleIds.mapNotNull { ready.nowAndNextAt(it, now).changesAt }.minOrNull() ?: return
        refreshJob =
            scope.launch {
                delay((changesAt - now + REFRESH_SLACK).coerceAtLeast(MIN_REFRESH_DELAY))
                mutableState.update { current ->
                    if (current is TvLiveTvState.Ready) current.copy(at = clock.now()) else current
                }
                loadGuide()
            }
    }

    /** Without a running or a following program the guide may be stale or short; ask again. */
    private fun LiveTvNowNext.isIncomplete() = now == null || next == null

    private fun TvLiveTvState.Ready.nowAndNextAt(
        channelId: String,
        at: Instant,
    ) = guide[channelId].orEmpty().nowAndNext(at)

    private companion object {
        /** Past the boundary, so the clock has certainly left the program that ended. */
        val REFRESH_SLACK: Duration = 500.milliseconds

        /** Keeps a program that is about to change from turning the timer into a loop. */
        val MIN_REFRESH_DELAY: Duration = 1.seconds
    }
}

/** What the Live TV screen draws with, and where it remembers focus. */
internal class TvLiveTvContext(
    val images: TvLibraryGridImages,
    val routeKey: String,
    val strings: TvStrings,
    val focusMemory: TvFocusMemory,
)

/** What the Live TV screen can do: play a channel, load again, and learn which channels are on screen. */
internal class TvLiveTvActions(
    val onPlay: (LiveTvChannel) -> Unit,
    val onRetry: () -> Unit,
    val onVisibleChannels: (List<String>) -> Unit,
)

/** The channel as a playable item for the launcher: a name and the picture are all the player start needs. */
internal fun LiveTvChannel.toPlayableItem(): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = null,
        name = name,
        sortName = null,
        overview = null,
        type = LIVE_TV_CHANNEL_TYPE,
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = imageTag,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

private const val LIVE_TV_CHANNEL_TYPE = "TvChannel"

/** Whether the item is a Live TV channel: it plays from the live edge and has no detail, resume point or queue. */
internal fun JellyfinItem.isLiveTvChannel(): Boolean = type == LIVE_TV_CHANNEL_TYPE
