package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.logging.sanitizeIdentifier
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartDecision
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.decidePlaybackStart
import dev.jellystack.players.formatPlaybackTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A play action waiting for the user to choose between resuming and restarting. */
internal data class TvResumeAsk(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail?,
    val positionLabel: String,
)

private fun JellyfinItem.isEpisodeContainer(): Boolean =
    type.equals("Series", ignoreCase = true) || type.equals("Season", ignoreCase = true)

/** Where the launcher reads the item detail and the episodes of a series from. */
internal class TvPlaybackSources(
    val loadDetail: suspend (String) -> JellyfinItemDetail?,
    val loadEpisodes: suspend (JellyfinItem) -> List<JellyfinItem>,
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

    /** Plays [item]; [detail] skips the detail request when the caller already has it. */
    fun play(
        item: JellyfinItem,
        detail: JellyfinItemDetail? = null,
    ) {
        if (item.isEpisodeContainer()) {
            // The episode to play is only known once the episodes are loaded, so the resume rule runs in the start.
            pendingAsk = null
            start(TvStartAttempt(item, detail = null, policy = PlaybackStartPolicy.INHERIT))
            return
        }
        when (val decision = decidePlaybackStart(item.positionTicks, currentSettings().resumeMode)) {
            is PlaybackStartDecision.Ask ->
                pendingAsk = TvResumeAsk(item, detail, formatPlaybackTime(decision.resumePositionMs))
            is PlaybackStartDecision.Start -> start(TvStartAttempt(item, detail, decision.policy))
        }
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
        val attempt = TvStartAttempt(item, detail = null, policy = PlaybackStartPolicy.RESUME)
        start(attempt.copy(groupStartPositionMs = startPositionMs))
    }

    /** Answers the pending prompt; `null` cancels it. */
    fun answerAsk(policy: PlaybackStartPolicy?) {
        val ask = pendingAsk ?: return
        pendingAsk = null
        if (policy != null) start(TvStartAttempt(ask.item, ask.detail, policy))
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
                    runSuspendCatching { run(attempt) }
                        .onFailure { failure -> JellystackLog.e("TV playback could not start", failure) }
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

    private suspend fun run(attempt: TvStartAttempt): TvStartOutcome =
        if (attempt.item.isEpisodeContainer()) runEpisodeOf(attempt.item) else begin(attempt)

    private suspend fun runEpisodeOf(container: JellyfinItem): TvStartOutcome {
        val episode = resolveEpisode(container)
        if (episode == null) return TvStartOutcome.Failed(TvStartFailure.NO_PLAYABLE_EPISODE, "no playable episode")
        startState = TvPlaybackStartState.Starting(episode)
        return when (val decision = decidePlaybackStart(episode.positionTicks, currentSettings().resumeMode)) {
            is PlaybackStartDecision.Ask ->
                TvStartOutcome.AskResume(TvResumeAsk(episode, null, formatPlaybackTime(decision.resumePositionMs)))
            is PlaybackStartDecision.Start -> begin(TvStartAttempt(episode, detail = null, policy = decision.policy))
        }
    }

    /** The episode a Play on [container] starts, or null when it has no playable one. */
    private suspend fun resolveEpisode(container: JellyfinItem): JellyfinItem? =
        seriesTargetResolver.resolve(sources.loadEpisodes(container), completedDownloadIds = emptySet())?.episode

    private suspend fun begin(target: TvStartAttempt): TvStartOutcome {
        val detail =
            target.detail ?: sources.loadDetail(target.item.id)
                ?: return TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "item detail unavailable")
        val fromItem = PlaybackRequest.from(target.item, detail, startPolicy = target.policy)
        val groupStart = target.groupStartPositionMs
        // SyncPlay keeps the whole group at normal speed, whatever the default speed setting says.
        val groupTicks = groupStart?.times(TICKS_PER_MILLISECOND)
        val request = if (groupTicks == null) fromItem else fromItem.copy(resumePositionTicks = groupTicks)
        val settings = currentSettings().let { if (groupStart == null) it else it.copy(defaultPlaybackSpeed = 1f) }
        return if (starter.start(request, settings)) {
            TvStartOutcome.Started
        } else {
            TvStartOutcome.Failed(TvStartFailure.COULD_NOT_START, "no server connection")
        }
    }
}

private const val TICKS_PER_MILLISECOND = 10_000L
