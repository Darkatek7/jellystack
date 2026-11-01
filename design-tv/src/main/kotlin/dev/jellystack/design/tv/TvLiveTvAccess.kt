@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.isLiveTv
import dev.jellystack.core.jellyfin.withBrowsableLibraries
import dev.jellystack.core.livetv.LiveTvRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Whether Live TV is offered, and the coordinator behind its screen. The server lists a Live TV library when Live TV is
 * set up for the user; it is offered only when that library is there and the server also has at least one channel, so
 * a server without tuners (or an old server) never shows it.
 */
internal class TvLiveTvAccess(
    val coordinator: TvLiveTvCoordinator,
    private val available: Boolean,
) {
    /** The libraries to show: the Live TV library only when [available]. */
    fun libraries(home: JellyfinHomeState): JellyfinHomeState = if (available) home else home.withBrowsableLibraries()
}

@Composable
internal fun rememberTvLiveTvAccess(
    repository: LiveTvRepository,
    scope: CoroutineScope,
    libraries: List<JellyfinLibrary>,
): TvLiveTvAccess {
    val coordinator = remember(repository, scope) { TvLiveTvCoordinator(scope, repository.tvLoader()) }
    val hasLibrary = libraries.any(JellyfinLibrary::isLiveTv)
    // A failed probe hides Live TV until the libraries change again; the feature is optional.
    val available by produceState(false, repository, hasLibrary) {
        value = hasLibrary && runSuspendCatching { repository.hasChannels() }.getOrDefault(false)
    }
    return TvLiveTvAccess(coordinator, available)
}
