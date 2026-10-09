package dev.jellystack.design.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A play action waiting for the user to choose between resuming and restarting. */
internal data class TvResumeAsk(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail?,
    val positionLabel: String,
)

/**
 * The one TV entry point for starting Jellyfin playback from Home, Library, Search, and Detail.
 * Applies the resume rule from [decidePlaybackStart], then the default speed and stats preference.
 */
internal class TvPlaybackLauncher(
    private val scope: CoroutineScope,
    private val repository: JellyfinBrowseRepository,
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val playbackController: PlaybackController,
    private val currentSettings: () -> AppSettings,
    private val onStarted: () -> Unit,
) {
    var pendingAsk by mutableStateOf<TvResumeAsk?>(null)
        private set

    /** Plays [item]; [detail] skips the detail request when the caller already has it. */
    fun play(
        item: JellyfinItem,
        detail: JellyfinItemDetail? = null,
    ) {
        when (val decision = decidePlaybackStart(item.positionTicks, currentSettings().resumeMode)) {
            is PlaybackStartDecision.Ask ->
                pendingAsk = TvResumeAsk(item, detail, formatPlaybackTime(decision.resumePositionMs))
            is PlaybackStartDecision.Start -> start(item, detail, decision.policy)
        }
    }

    /** Answers the pending prompt; `null` cancels it. */
    fun answerAsk(policy: PlaybackStartPolicy?) {
        val ask = pendingAsk ?: return
        pendingAsk = null
        if (policy != null) start(ask.item, ask.detail, policy)
    }

    private fun start(
        item: JellyfinItem,
        knownDetail: JellyfinItemDetail?,
        policy: PlaybackStartPolicy,
    ) {
        scope.launch {
            try {
                val detail = knownDetail ?: repository.getItemDetail(item.id) ?: return@launch
                val environment = environmentProvider.current() ?: return@launch
                val settings = currentSettings()
                playbackController.play(PlaybackRequest.from(item, detail, startPolicy = policy), environment)
                playbackController.setPlaybackSpeed(settings.defaultPlaybackSpeed)
                playbackController.setStatsForNerdsEnabled(settings.statsForNerdsEnabled)
                onStarted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                JellystackLog.e("TV playback could not start", error)
            }
        }
    }
}
