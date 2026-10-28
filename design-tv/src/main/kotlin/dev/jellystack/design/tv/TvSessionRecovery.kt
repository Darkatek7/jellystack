@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyfin.AuthExpirySignal
import dev.jellystack.core.jellyfin.AuthExpiryWatcher
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerErrorCategory
import dev.jellystack.players.syncplay.SyncPlayErrorCode

/** What the profile gate does about the selected profile right now. */
internal enum class TvActivationDecision {
    /** Open the server session of the selected profile. */
    ACTIVATE,

    /** The profile's token is known to be rejected: send the user to sign in instead of activating it again. */
    SHOW_RECONNECT,

    /** Nothing to do (not ready, already active, a switch or the sign-in flow is running). */
    WAIT,
}

internal data class TvActivationInputs(
    val initialized: Boolean,
    val pickerVisible: Boolean,
    /** The id of the selected profile, or `null` when there is none (or it no longer exists). */
    val selectedProfileId: String?,
    val activatedProfileId: String?,
    val switchingProfile: Boolean,
    /** The sign-in screen for a profile is showing (or about to). */
    val reconnectPending: Boolean,
    /** The selected profile's token was rejected and no sign-in has succeeded since. */
    val signInRequired: Boolean,
)

/**
 * Decides whether the profile gate should open the selected profile's session. A rejected token must never be
 * activated again on its own: activation succeeds locally (the server entry and binding still exist), the first
 * request is rejected again, and the gate would loop against the server until the user signs in.
 */
internal fun decideTvActivation(inputs: TvActivationInputs): TvActivationDecision =
    when {
        !inputs.initialized || inputs.pickerVisible || inputs.selectedProfileId == null -> TvActivationDecision.WAIT
        inputs.activatedProfileId == inputs.selectedProfileId || inputs.switchingProfile -> TvActivationDecision.WAIT
        inputs.reconnectPending -> TvActivationDecision.WAIT
        inputs.signInRequired -> TvActivationDecision.SHOW_RECONNECT
        else -> TvActivationDecision.ACTIVATE
    }

/**
 * Remembers which profiles have to sign in again. A profile enters through [markExpired] and leaves only through
 * [markSignedIn], so neither the auto-activation nor picking the profile again from the picker re-opens a session
 * with a token the server already rejected. [markExpired] returns `true` only for the first report, which keeps
 * several parallel failures from starting the recovery more than once.
 */
internal class TvSessionRecovery {
    private var signInRequired by mutableStateOf<Set<String>>(emptySet())

    fun requiresSignIn(profileId: String?): Boolean = profileId != null && profileId in signInRequired

    fun markExpired(profileId: String): Boolean {
        if (profileId in signInRequired) return false
        signInRequired = signInRequired + profileId
        return true
    }

    fun markSignedIn(profileId: String) {
        signInRequired = signInRequired - profileId
    }
}

@Composable
internal fun rememberTvSessionRecovery(): TvSessionRecovery = remember { TvSessionRecovery() }

/** The playback failure when it means the server rejected the session's credentials, otherwise `null`. */
internal fun PlaybackState.unauthorizedPlaybackFailure(): PlaybackState.PlaybackError? =
    (this as? PlaybackState.PlaybackError)?.takeIf { it.category == PlayerErrorCategory.UNAUTHORIZED }

/**
 * Feeds every place that can notice a rejected session into one verification: Jellyfin `401`s (reported by the
 * network clients through [signal]), a playback failure with unauthorized credentials and a SyncPlay `401`.
 * [AuthExpiryWatcher] collapses bursts and asks the server whether the token is really gone; only then does
 * [JellyfinSessionRepository] publish the expiry that the root turns into the sign-in flow.
 */
@Composable
internal fun TvAuthExpiryEffect(
    signal: AuthExpirySignal,
    environmentProvider: JellyfinEnvironmentProvider,
    sessionRepository: JellyfinSessionRepository,
    playback: PlaybackState,
    syncPlayError: SyncPlayErrorCode?,
) {
    // The repository is created per signed-in account generation, so the watcher restarts with it.
    LaunchedEffect(sessionRepository) {
        AuthExpiryWatcher(
            signal = signal,
            currentAccessToken = { environmentProvider.current()?.accessToken },
            verifySession = { sessionRepository.verifyAuthentication() },
        ).run()
    }
    val unauthorizedPlayback = playback.unauthorizedPlaybackFailure()
    LaunchedEffect(unauthorizedPlayback) {
        if (unauthorizedPlayback != null) signal.report()
    }
    LaunchedEffect(syncPlayError) {
        if (syncPlayError == SyncPlayErrorCode.UNAUTHORIZED) signal.report()
    }
}
