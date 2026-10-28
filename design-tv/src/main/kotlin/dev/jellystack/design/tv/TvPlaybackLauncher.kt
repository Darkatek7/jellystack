package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.logging.JellystackLog
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
    ) : TvPlaybackStartState
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
 */
internal class TvPlaybackLauncher(
    private val scope: CoroutineScope,
    private val loadDetail: suspend (String) -> JellyfinItemDetail?,
    private val starter: TvPlaybackStarter,
    private val currentSettings: () -> AppSettings,
    private val openPlayer: () -> Unit,
) {
    var pendingAsk by mutableStateOf<TvResumeAsk?>(null)
        private set

    var startState by mutableStateOf<TvPlaybackStartState>(TvPlaybackStartState.Idle)
        private set

    private var startJob: Job? = null
    private var lastAttempt: TvStartAttempt? = null

    /** Plays [item]; [detail] skips the detail request when the caller already has it. */
    fun play(
        item: JellyfinItem,
        detail: JellyfinItemDetail? = null,
    ) {
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
                val started =
                    runSuspendCatching { run(attempt) }
                        .onFailure { failure -> JellystackLog.e("TV playback could not start", failure) }
                        .getOrDefault(false)
                startState = if (started) TvPlaybackStartState.Idle else TvPlaybackStartState.Failed(attempt.item)
            }
    }

    private suspend fun run(attempt: TvStartAttempt): Boolean {
        val detail = attempt.detail ?: loadDetail(attempt.item.id) ?: return false
        val fromItem = PlaybackRequest.from(attempt.item, detail, startPolicy = attempt.policy)
        val groupStart = attempt.groupStartPositionMs
        // SyncPlay keeps the whole group at normal speed, whatever the default speed setting says.
        val groupTicks = groupStart?.times(TICKS_PER_MILLISECOND)
        val request = if (groupTicks == null) fromItem else fromItem.copy(resumePositionTicks = groupTicks)
        val settings = currentSettings().let { if (groupStart == null) it else it.copy(defaultPlaybackSpeed = 1f) }
        return starter.start(request, settings)
    }
}

private const val TICKS_PER_MILLISECOND = 10_000L
