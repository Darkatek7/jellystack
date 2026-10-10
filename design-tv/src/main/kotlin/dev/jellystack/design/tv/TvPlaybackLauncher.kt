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

/**
 * The one TV entry point for starting Jellyfin playback from Home, Library, Search, and Detail.
 * Applies the resume rule from [decidePlaybackStart] before handing the request to [starter].
 */
internal class TvPlaybackLauncher(
    private val scope: CoroutineScope,
    private val loadDetail: suspend (String) -> JellyfinItemDetail?,
    private val starter: TvPlaybackStarter,
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
            runSuspendCatching {
                val detail = knownDetail ?: loadDetail(item.id) ?: return@runSuspendCatching
                val request = PlaybackRequest.from(item, detail, startPolicy = policy)
                if (starter.start(request, currentSettings())) onStarted()
            }.onFailure { failure -> JellystackLog.e("TV playback could not start", failure) }
        }
    }
}
