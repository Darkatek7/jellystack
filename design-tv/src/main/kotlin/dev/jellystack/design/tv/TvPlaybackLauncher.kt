package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.core.jellyfin.isEpisodeParent
import dev.jellystack.core.jellyfin.isPlayableAudio
import dev.jellystack.core.jellyfin.opensAsItemList
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.logging.sanitizeIdentifier
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackQueue
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartDecision
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.decidePlaybackStart
import dev.jellystack.players.forLiveChannel
import dev.jellystack.players.formatPlaybackTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

/** A play action waiting for the user to choose between resuming and restarting. */
internal data class TvResumeAsk(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail?,
    val positionLabel: String,
    val choice: TvPlayChoice? = null,
)

/**
 * Audio, subtitle and version the user picked on the detail screen before playing. Stream indexes are the server's
 * stream indexes; [subtitlesOff] plays without subtitles, whatever the language preferences say.
 */
internal data class TvPlayChoice(
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val subtitlesOff: Boolean = false,
)

/** A track id no stream has: the controller then finds no subtitle track to select, so playback starts without. */
private const val SUBTITLES_OFF_TRACK_ID = "jellystack:subtitles-off"

/** [request] limited to the chosen version and starting with the chosen tracks. */
internal fun PlaybackRequest.withChoice(choice: TvPlayChoice?): PlaybackRequest {
    if (choice == null) return this
    val chosenSource = choice.mediaSourceId?.let { id -> mediaSources.firstOrNull { it.id == id } }
    return copy(
        mediaSources = chosenSource?.let(::listOf) ?: mediaSources,
        preferredAudioTrackId = choice.audioStreamIndex?.toString() ?: preferredAudioTrackId,
        preferredSubtitleTrackId =
            when {
                choice.subtitlesOff -> SUBTITLES_OFF_TRACK_ID
                else -> choice.subtitleStreamIndex?.toString() ?: preferredSubtitleTrackId
            },
    )
}

/** Where the launcher reads the item detail, the episodes of a series, and the entries of a collection from. */
internal class TvPlaybackSources(
    val loadDetail: suspend (String) -> JellyfinItemDetail?,
    val loadEpisodes: suspend (JellyfinItem) -> List<JellyfinItem>,
    /** The entries of a collection or playlist, in the order they play. */
    val loadListEntries: suspend (JellyfinItem) -> List<JellyfinItem> = { emptyList() },
    /** Receives the entries "Play all" is about to play, so the continuation coordinator can move on through them. */
    val queue: PlaybackQueue = PlaybackQueue(),
    /** Decides the order of a shuffled list, once per play action. */
    val random: Random = Random.Default,
)

/** Starts a resolved request; returns false when there is no server to play from. */
internal fun interface TvPlaybackStarter {
    suspend fun start(
        request: PlaybackRequest,
        settings: AppSettings,
    ): Boolean
}

/** Plays on [controller] and applies the default speed and the stats preference. */
internal fun controllerPlaybackStarter(
    controller: PlaybackController,
    environmentProvider: JellyfinEnvironmentProvider,
) = TvPlaybackStarter { request, settings ->
    val environment = environmentProvider.current() ?: return@TvPlaybackStarter false
    controller.play(request, environment)
    controller.setPlaybackSpeed(settings.defaultPlaybackSpeed)
    controller.setStatsForNerdsEnabled(settings.statsForNerdsEnabled)
    true
}

/** Progress of a play action the user triggered, from the press until the controller owns the outcome. */
internal sealed interface TvPlaybackStartState {
    data object Idle : TvPlaybackStartState

    /** Detail, source negotiation or the player start is still running for [item]. */
    data class Starting(
        val item: JellyfinItem,
    ) : TvPlaybackStartState

    /** The start ended before the controller took over (detail unavailable, no server, unexpected failure). */
    data class Failed(
        val item: JellyfinItem,
        val failure: TvStartFailure = TvStartFailure.COULD_NOT_START,
    ) : TvPlaybackStartState
}

/** Why a start ended before the controller took over; it picks the copy of the start error panel. */
internal enum class TvStartFailure {
    /** No server, the item detail was unavailable, or an unexpected error. */
    COULD_NOT_START,

    /** A series or season without an episode that can be played. */
    NO_PLAYABLE_EPISODE,

    /** A collection or playlist without an entry that can be played. */
    NO_PLAYABLE_ITEM,
}

private sealed interface TvStartOutcome {
    data object Started : TvStartOutcome

    data class Failed(
        val failure: TvStartFailure,
        val reason: String,
    ) : TvStartOutcome

    /** The episode a series Play resolved to has saved progress and the resume mode asks what to do. */
    data class AskResume(
        val ask: TvResumeAsk,
    ) : TvStartOutcome
}

private data class TvStartAttempt(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail?,
    val policy: PlaybackStartPolicy,
    /** Set when another SyncPlay member started the item: the group decides the position and the speed. */
    val groupStartPositionMs: Long? = null,
    /** For a list: play it in a random order instead of its own. */
    val shuffle: Boolean = false,
    /** The version and tracks picked on the detail screen. */
    val choice: TvPlayChoice? = null,
)

/**
 * The one TV entry point for starting Jellyfin playback from Home, Library, Search, and Detail.
 * Applies the resume rule from [decidePlaybackStart] before handing the request to [starter].
 *
 * [openPlayer] runs as soon as a start begins, so the player route can show progress while the server
 * answers; [cancelStart] and a failure are reported through [startState].
 *
 * A Series or Season has no media source of its own: [TvPlaybackSources.loadEpisodes] lists its episodes and
 * [SeriesPlaybackTargetResolver] picks the one to play (the one in progress, else the first unwatched, else the first).
 *
 * Playing a collection, playlist, album, or artist plays all of it: the entries become [TvPlaybackSources.queue], the
 * first playable one starts, and the continuation coordinator moves on through the queue. Playing anything else empties
 * it. Music never asks whether to resume (see [startDecision]).
 */
internal class TvPlaybackLauncher(
    private val scope: CoroutineScope,
    private val sources: TvPlaybackSources,
    private val starter: TvPlaybackStarter,
    private val currentSettings: () -> AppSettings,
    private val openPlayer: () -> Unit,
) {
    var pendingAsk by mutableStateOf<TvResumeAsk?>(null)
        private set

    var startState by mutableStateOf<TvPlaybackStartState>(TvPlaybackStartState.Idle)
        private set

    private val seriesTargetResolver = SeriesPlaybackTargetResolver()
    private var startJob: Job? = null
    private var lastAttempt: TvStartAttempt? = null

    /**
     * Plays [item], or from the beginning with [fromStart]; [detail] skips the detail request when the caller already
     * has it. A Live TV channel starts from the live edge at once: it has no detail to load, never asks to resume, and
     * plays at normal speed.
     */
    fun play(
        item: JellyfinItem,
        detail: JellyfinItemDetail? = null,
        choice: TvPlayChoice? = null,
        fromStart: Boolean = false,
    ) {
        when {
            fromStart -> {
                // An explicit "start over" never asks; a series restarts the episode it would have played next.
                pendingAsk = null
                sources.queue.clear()
                val known = detail.takeUnless { item.isEpisodeParent() }
                start(TvStartAttempt(item, known, PlaybackStartPolicy.RESTART, choice = choice))
            }
            item.opensAsItemList() -> playAll(item)
            item.isEpisodeParent() || item.isLiveTvChannel() -> {
                // The episode to play is only known once the episodes are loaded, so the resume rule runs in the start.
                // A channel has no resume point: the start plays it from the live edge.
                sources.queue.clear()
                pendingAsk = null
                start(TvStartAttempt(item, detail = null, policy = PlaybackStartPolicy.INHERIT))
            }
            else -> {
                sources.queue.clear()
                when (val decision = item.startDecision(currentSettings().resumeMode)) {
                    is PlaybackStartDecision.Ask ->
                        pendingAsk = TvResumeAsk(item, detail, formatPlaybackTime(decision.resumePositionMs), choice)
                    is PlaybackStartDecision.Start ->
                        start(TvStartAttempt(item, detail, decision.policy, choice = choice))
                }
            }
        }
    }

    /**
     * Plays the entries of [container] (a collection, playlist, album, or artist) one after the other, from the first
     * playable one, or in a random order that is decided now when [shuffle] is set.
     */
    fun playAll(
        container: JellyfinItem,
        shuffle: Boolean = false,
    ) {
        pendingAsk = null
        start(TvStartAttempt(container, detail = null, policy = PlaybackStartPolicy.INHERIT, shuffle = shuffle))
    }

    /**
     * Plays [tracks] as one queue starting at [startIndex]: the tracks after it follow, the ones before it are what
     * "previous" goes back to. Music never asks to resume. [shuffle] keeps the chosen track first and mixes the rest.
     */
    fun playTracks(
        tracks: List<JellyfinItem>,
        startIndex: Int?,
        shuffle: Boolean = false,
    ) {
        pendingAsk = null
        // A track the server lists but has no file for cannot play; the chosen track keeps its place among the rest.
        val playable = tracks.filter(JellyfinItem::isPlayableAudio)
        val chosen = startIndex?.let(tracks::getOrNull)
        val playableIndex = chosen?.let { track -> playable.indexOfFirst { it.id == track.id } }
        val first = sources.queue.start(playable, playableIndex, if (shuffle) sources.random else null) ?: return
        start(TvStartAttempt(first, detail = null, policy = PlaybackStartPolicy.RESTART))
    }

    /** Plays the track before [currentMediaId] in the queue; does nothing for the first track or outside a queue. */
    fun playPrevious(currentMediaId: String) {
        val previous = sources.queue.entryBefore(currentMediaId) ?: return
        pendingAsk = null
        start(TvStartAttempt(previous, detail = null, policy = PlaybackStartPolicy.RESTART))
    }

    /**
     * Plays [item] because another member of the joined SyncPlay group started it at [startPositionMs]. It opens the
     * player route like a local play action (without it the audio would play behind the browse UI) and skips the
     * resume prompt, because the group decides where to start.
     */
    fun playForGroup(
        item: JellyfinItem,
        startPositionMs: Long,
    ) {
        pendingAsk = null
        sources.queue.clear()
        val attempt = TvStartAttempt(item, detail = null, policy = PlaybackStartPolicy.RESUME)
        start(attempt.copy(groupStartPositionMs = startPositionMs))
    }

    /** Answers the pending prompt; `null` cancels it. */
    fun answerAsk(policy: PlaybackStartPolicy?) {
        val ask = pendingAsk ?: return
        pendingAsk = null
        if (policy != null) start(TvStartAttempt(ask.item, ask.detail, policy, choice = ask.choice))
    }

    /** Runs the failed start again. */
    fun retryStart() {
        lastAttempt?.let(::start)
    }

    /** Abandons a start that has not reached the controller yet, or one that failed. */
    fun cancelStart() {
        startJob?.cancel()
        startJob = null
        startState = TvPlaybackStartState.Idle
    }

    private fun start(attempt: TvStartAttempt) {
        startJob?.cancel()
        lastAttempt = attempt
        startState = TvPlaybackStartState.Starting(attempt.item)
        openPlayer()
        startJob =
            scope.launch {
                val outcome =
                    runSuspendCatching {
                        if (attempt.item.isLiveTvChannel()) {
                            beginLive(starter, currentSettings(), attempt.item)
                        } else if (attempt.item.isEpisodeParent() || attempt.item.opensAsItemList()) {
                            runContainer(attempt.item, attempt.shuffle, attempt.policy)
                        } else {
                            sources.begin(starter, currentSettings(), attempt)
                        }
                    }.onFailure { failure -> JellystackLog.e("TV playback could not start", failure) }
                        .getOrDefault(TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "unexpected error"))
                startState =
                    when (outcome) {
                        TvStartOutcome.Started -> TvPlaybackStartState.Idle
                        is TvStartOutcome.AskResume -> {
                            pendingAsk = outcome.ask
                            TvPlaybackStartState.Idle
                        }
                        is TvStartOutcome.Failed -> {
                            val itemId = sanitizeIdentifier(attempt.item.id)
                            JellystackLog.w("TV playback did not start: ${outcome.reason} (item=$itemId)")
                            TvPlaybackStartState.Failed(attempt.item, outcome.failure)
                        }
                    }
            }
    }

    /** Starts a container: the episode of a series or season to play, else the first entry of the list it plays. */
    private suspend fun runContainer(
        container: JellyfinItem,
        shuffle: Boolean,
        policy: PlaybackStartPolicy,
    ): TvStartOutcome {
        val episodeParent = container.isEpisodeParent()
        val first =
            if (episodeParent) {
                sources.episodeToPlay(container, seriesTargetResolver)
            } else {
                sources.queueFirstOf(container, shuffle)
            }
        if (first == null) {
            return if (episodeParent) {
                TvStartOutcome.Failed(TvStartFailure.NO_PLAYABLE_EPISODE, "no playable episode")
            } else {
                TvStartOutcome.Failed(TvStartFailure.NO_PLAYABLE_ITEM, "no playable entry")
            }
        }
        startState = TvPlaybackStartState.Starting(first)
        // An explicit "start over" never asks.
        val decision =
            if (policy == PlaybackStartPolicy.RESTART) {
                PlaybackStartDecision.Start(PlaybackStartPolicy.RESTART)
            } else {
                first.startDecision(currentSettings().resumeMode)
            }
        return when (decision) {
            is PlaybackStartDecision.Ask ->
                TvStartOutcome.AskResume(TvResumeAsk(first, null, formatPlaybackTime(decision.resumePositionMs)))
            is PlaybackStartDecision.Start -> {
                val attempt = TvStartAttempt(first, detail = null, policy = decision.policy)
                sources.begin(starter, currentSettings(), attempt)
            }
        }
    }
}

/** Resolves [target] into a request (with the group's position and speed for SyncPlay) and hands it to [starter]. */
private suspend fun TvPlaybackSources.begin(
    starter: TvPlaybackStarter,
    appSettings: AppSettings,
    target: TvStartAttempt,
): TvStartOutcome {
    val detail =
        target.detail ?: loadDetail(target.item.id)
            ?: return TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "item detail unavailable")
    val fromItem = PlaybackRequest.from(target.item, detail, startPolicy = target.policy).withChoice(target.choice)
    val groupStart = target.groupStartPositionMs
    // SyncPlay keeps the whole group at normal speed, whatever the default speed setting says.
    val groupTicks = groupStart?.times(TICKS_PER_MILLISECOND)
    val request = if (groupTicks == null) fromItem else fromItem.copy(resumePositionTicks = groupTicks)
    // Music plays at normal speed, whatever the default speed for video is.
    val normalSpeed = groupStart != null || target.item.isPlayableAudio()
    val settings = if (normalSpeed) appSettings.copy(defaultPlaybackSpeed = 1f) else appSettings
    return if (starter.start(request, settings)) {
        TvStartOutcome.Started
    } else {
        TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "no server connection")
    }
}

/** Hands the request for the Live TV channel [channel] to [starter]; live video plays at normal speed. */
private suspend fun beginLive(
    starter: TvPlaybackStarter,
    appSettings: AppSettings,
    channel: JellyfinItem,
): TvStartOutcome {
    val request = PlaybackRequest.forLiveChannel(channel.id, channel.name, channel.primaryImageTag)
    return if (starter.start(request, appSettings.copy(defaultPlaybackSpeed = 1f))) {
        TvStartOutcome.Started
    } else {
        TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "no server connection")
    }
}

/** The episode a series or season plays: the one in progress, else the first unwatched, else the first. */
private suspend fun TvPlaybackSources.episodeToPlay(
    container: JellyfinItem,
    resolver: SeriesPlaybackTargetResolver,
): JellyfinItem? = resolver.resolve(loadEpisodes(container), completedDownloadIds = emptySet())?.episode

/** Makes the entries of [container] the queue (shuffled once if [shuffle]) and returns the one to play first. */
private suspend fun TvPlaybackSources.queueFirstOf(
    container: JellyfinItem,
    shuffle: Boolean,
): JellyfinItem? =
    queue.start(
        buildPlayAllQueue(loadListEntries(container), loadEpisodes),
        startIndex = if (shuffle) null else 0,
        shuffle = if (shuffle) random else null,
    )

private const val TICKS_PER_MILLISECOND = 10_000L
