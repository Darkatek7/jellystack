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
        window: Duration,
    ): Map<String, List<LiveTvProgram>>
}

internal fun LiveTvRepository.tvLoader() =
    object : TvLiveTvLoader {
        override suspend fun channels() = this@tvLoader.channels()

        override suspend fun programs(
            channelIds: List<String>,
            at: Instant,
            window: Duration,
        ) = this@tvLoader.programs(channelIds, at, window)
    }

internal sealed interface TvLiveTvState {
    data object Loading : TvLiveTvState

    /** No channels: the server has no Live TV, or none this user may watch. */
    data object Empty : TvLiveTvState

    data object Failed : TvLiveTvState

    /**
     * [guide] holds the programs of the channels that were on screen; [at] is when they were last evaluated.
     * [guideUntil] is, per channel, how far the guide grid has loaded: programs starting before it are all there.
     */
    data class Ready(
        val channels: List<LiveTvChannel>,
        val guide: Map<String, List<LiveTvProgram>>,
        val at: Instant,
        val guideUntil: Map<String, Instant> = emptyMap(),
    ) : TvLiveTvState {
        fun nowAndNext(channelId: String): LiveTvNowNext = guide[channelId].orEmpty().nowAndNext(at)

        /** The programs just [loaded] for [ids] join the known ones; [until] is how far the grid has them now. */
        fun withPrograms(
            ids: List<String>,
            loaded: Map<String, List<LiveTvProgram>>,
            until: Instant?,
            now: Instant,
        ): Ready {
            val merged = ids.associateWith { mergedPrograms(guide[it].orEmpty(), loaded[it].orEmpty()) }
            val reached = if (until != null) ids.associateWith { until } else emptyMap()
            return copy(guide = guide + merged, at = now, guideUntil = guideUntil + reached)
        }
    }
}

/**
 * Holds the channels and the guide outside the screen, so Back from the player returns to the list as it was.
 *
 * Programs load for the channels on screen only ([setVisible]; the guide grid asks with [setGuideWindow] for the
 * channels and the time range it shows). One timer waits for the first change among them (a running program ending),
 * then re-evaluates and loads what is missing; there is no polling.
 *
 * Whether the guide grid or the channel list is shown, and where the grid was left, outlive the screen too.
 */
internal class TvLiveTvCoordinator(
    private val scope: CoroutineScope,
    private val loader: TvLiveTvLoader,
    private val clock: Clock = Clock.System,
) {
    private val mutableState = MutableStateFlow<TvLiveTvState>(TvLiveTvState.Loading)
    val state: StateFlow<TvLiveTvState> = mutableState.asStateFlow()
    private val mutableGuideMode = MutableStateFlow(false)

    /** Whether the guide grid is shown instead of the channel list; kept for the session. */
    val guideMode: StateFlow<Boolean> = mutableGuideMode.asStateFlow()

    /** Where the grid was left, so Back from the player returns to it. */
    val guideMemory = TvLiveTvGuideMemory()
    private var visibleIds: List<String> = emptyList()
    private var guideRange: GuideRange? = null
    private var channelsJob: Job? = null
    private var guideJob: Job? = null
    private var refreshJob: Job? = null

    /** Shows the channels now (the loaded ones, else a loading state) and refreshes them from the server. */
    fun show() = loadChannels(showLoading = mutableState.value !is TvLiveTvState.Ready)

    fun retry() = loadChannels(showLoading = true)

    fun toggleGuide() {
        mutableGuideMode.update { !it }
    }

    /** The channels on screen; their programs are the only ones loaded. */
    fun setVisible(channelIds: List<String>) {
        if (channelIds == visibleIds && guideRange == null) return
        visibleIds = channelIds
        guideRange = null
        loadGuide()
    }

    /** The channels on screen in the guide grid and the time range it shows: programs from [from] until [until]. */
    fun setGuideWindow(
        channelIds: List<String>,
        from: Instant,
        until: Instant,
    ) {
        val range = GuideRange(from, until)
        if (channelIds == visibleIds && range == guideRange) return
        visibleIds = channelIds
        guideRange = range
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
        val previous = this as? TvLiveTvState.Ready
        val known = previous?.guide.orEmpty().filterKeys { it in ids }
        val until = previous?.guideUntil.orEmpty().filterKeys { it in ids }
        return TvLiveTvState.Ready(channels, known, clock.now(), until)
    }

    private fun loadGuide() {
        val ready = mutableState.value as? TvLiveTvState.Ready ?: return
        val now = clock.now()
        val range = guideRange
        val missing =
            if (range != null) {
                visibleIds.filter { id -> (ready.guideUntil[id] ?: Instant.DISTANT_PAST) < range.until }
            } else {
                visibleIds.filter { id -> ready.nowAndNextAt(id, now).isIncomplete() }
            }
        guideJob?.cancel()
        if (missing.isEmpty()) {
            scheduleRefresh()
            return
        }
        guideJob =
            scope.launch {
                // The guide is advisory: a failed load leaves what is shown, and the channels stay playable.
                val from = range?.from ?: now
                val window = range?.let { it.until - it.from } ?: LiveTvRepository.DEFAULT_PROGRAM_WINDOW
                runSuspendCatching { loader.programs(missing, from, window) }.onSuccess { loaded ->
                    mutableState.update { current ->
                        if (current is TvLiveTvState.Ready) {
                            current.withPrograms(missing, loaded, range?.until, clock.now())
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

    private data class GuideRange(
        val from: Instant,
        val until: Instant,
    )

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

/** The loaded programs win over the known ones of the same id; programs outside the loaded range stay. */
private fun mergedPrograms(
    known: List<LiveTvProgram>,
    loaded: List<LiveTvProgram>,
): List<LiveTvProgram> {
    if (known.isEmpty()) return loaded
    return (loaded + known).distinctBy(LiveTvProgram::id).sortedBy(LiveTvProgram::start)
}

/** Without a running or a following program the guide may be stale or short; ask again. */
private fun LiveTvNowNext.isIncomplete() = now == null || next == null

/** What the Live TV screen draws with, and where it remembers focus. */
internal class TvLiveTvContext(
    val images: TvLibraryGridImages,
    val routeKey: String,
    val strings: TvStrings,
    val focusMemory: TvFocusMemory,
)

/**
 * What the Live TV screen can do: play a channel, load again, learn which channels are on screen (the list) or which
 * channels and time range (the guide grid), and switch between the two views.
 */
internal class TvLiveTvActions(
    val onPlay: (LiveTvChannel) -> Unit,
    val onRetry: () -> Unit,
    val onVisibleChannels: (List<String>) -> Unit,
    val guide: TvLiveTvGuideActions = TvLiveTvGuideActions(),
)

/** What the guide grid and its header button do besides what the list does too. */
internal class TvLiveTvGuideActions(
    val onWindow: (channelIds: List<String>, from: Instant, until: Instant) -> Unit = { _, _, _ -> },
    val onToggle: () -> Unit = {},
    val memory: TvLiveTvGuideMemory = TvLiveTvGuideMemory(),
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
