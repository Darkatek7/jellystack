package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasResult
import dev.jellystack.network.jellyfin.JellyfinPlaybackExtrasService
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackExtrasCoordinator
import dev.jellystack.players.PlaybackQueue
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal data class TvJellyfinPlaybackIdentity(
    val serverKey: String,
    val userId: String,
)

internal data class TvPlaybackCoordinators(
    val segment: PlaybackSegmentCoordinator,
    val continuation: PlaybackContinuationCoordinator,
    val extras: PlaybackExtrasCoordinator,
)

@Composable
internal fun rememberTvPlaybackCoordinators(
    identity: TvJellyfinPlaybackIdentity?,
    playbackState: PlaybackState,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
    createExtrasCoordinator: (CoroutineScope) -> PlaybackExtrasCoordinator = ::defaultTvExtrasCoordinator,
): TvPlaybackCoordinators {
    val coordinators =
        rememberTvPlaybackCoordinatorHolder(
            identity,
            createSegmentCoordinator,
            createContinuationCoordinator,
            createExtrasCoordinator,
        )
    LaunchedEffect(playbackState, coordinators) { coordinators.onPlaybackState(playbackState) }
    return coordinators
}

/**
 * Like the overload above, but follows [playbackStates] inside an effect. Playback state changes with every position
 * tick, so the caller must not read it in composition just to hand it to the coordinators.
 */
@Composable
internal fun rememberTvPlaybackCoordinators(
    identity: TvJellyfinPlaybackIdentity?,
    playbackStates: Flow<PlaybackState>,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
    createExtrasCoordinator: (CoroutineScope) -> PlaybackExtrasCoordinator = ::defaultTvExtrasCoordinator,
): TvPlaybackCoordinators {
    val coordinators =
        rememberTvPlaybackCoordinatorHolder(
            identity,
            createSegmentCoordinator,
            createContinuationCoordinator,
            createExtrasCoordinator,
        )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(playbackStates, coordinators, lifecycle) {
        // Same as collecting the state with the lifecycle: nothing is delivered while the app is stopped.
        playbackStates.flowWithLifecycle(lifecycle).collect(coordinators::onPlaybackState)
    }
    return coordinators
}

private fun TvPlaybackCoordinators.onPlaybackState(playbackState: PlaybackState) {
    segment.onPlaybackState(playbackState)
    continuation.onPlaybackState(playbackState)
    extras.onPlaybackState(playbackState)
}

private fun defaultTvExtrasCoordinator(scope: CoroutineScope): PlaybackExtrasCoordinator =
    PlaybackExtrasCoordinator(scope, JellyfinPlaybackExtrasService { JellyfinPlaybackExtrasResult.Unavailable })

@Composable
private fun rememberTvPlaybackCoordinatorHolder(
    identity: TvJellyfinPlaybackIdentity?,
    createSegmentCoordinator: (CoroutineScope) -> PlaybackSegmentCoordinator,
    createContinuationCoordinator: (CoroutineScope) -> PlaybackContinuationCoordinator,
    createExtrasCoordinator: (CoroutineScope) -> PlaybackExtrasCoordinator,
): TvPlaybackCoordinators {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinators =
        remember(identity) {
            TvPlaybackCoordinators(
                segment = createSegmentCoordinator(scope),
                continuation = createContinuationCoordinator(scope),
                extras = createExtrasCoordinator(scope),
            )
        }

    DisposableEffect(lifecycleOwner, coordinators) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START,
                    Lifecycle.Event.ON_RESUME,
                    -> coordinators.continuation.setForeground(true)

                    Lifecycle.Event.ON_STOP,
                    Lifecycle.Event.ON_DESTROY,
                    -> coordinators.continuation.setForeground(false)

                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        coordinators.continuation.setForeground(
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycle.removeObserver(observer)
            coordinators.continuation.setForeground(false)
            coordinators.segment.release()
            coordinators.continuation.release()
            coordinators.extras.release()
        }
    }

    return coordinators
}

/** What the Jellyfin playback coordinators read; [settings] and [images] are read each time they are used. */
internal class TvPlaybackCoordinatorSources(
    val environmentProvider: JellyfinEnvironmentProvider,
    val browseRepository: JellyfinBrowseRepository,
    val settings: () -> AppSettings,
    val images: () -> TvPlayerImages,
    /** The entries of the list being played with "Play all"; continuation follows it before it follows a series. */
    val queue: PlaybackQueue = PlaybackQueue(),
)

/** Segment, continuation, and extras coordinators for the signed-in server; seeks and "next" go through [router]. */
@Composable
internal fun rememberTvJellyfinPlaybackCoordinators(
    identity: TvJellyfinPlaybackIdentity?,
    playbackStates: Flow<PlaybackState>,
    controller: PlaybackController,
    router: TvPlaybackCommandRouter,
    sources: TvPlaybackCoordinatorSources,
): TvPlaybackCoordinators {
    // The coordinators live until the identity changes; read the latest sources and router when they run.
    val currentSources by rememberUpdatedState(sources)
    val currentRouter by rememberUpdatedState(router)
    val httpClient =
        remember(sources.environmentProvider) { NetworkClientFactory.create(ClientConfig(installLogging = false)) }
    DisposableEffect(httpClient) { onDispose(httpClient::close) }
    return rememberTvPlaybackCoordinators(
        identity = identity,
        playbackStates = playbackStates,
        createSegmentCoordinator = { scope ->
            PlaybackSegmentCoordinator(
                scope = scope,
                segmentService = TvJellyfinMediaSegmentsService(sources.environmentProvider, httpClient),
                modeProvider = PlaybackSegmentModeProvider { type -> currentSources.settings().segmentSkipMode(type) },
                seekAdapter = PlaybackSeekAdapter { positionMs -> currentRouter.seekTo(positionMs) },
            )
        },
        createContinuationCoordinator = { scope ->
            PlaybackContinuationCoordinator(
                scope = scope,
                modeProvider = { currentSources.settings().autoplayNextMode },
                resolveNext = { mediaId, seriesId ->
                    currentSources.resolveNextEpisode(mediaId, seriesId, controller, currentRouter)
                },
                queue =
                    sources.queue.source { entry ->
                        currentSources.continuationTarget(
                            next = entry,
                            subtitle =
                                if (entry.type.equals(
                                        "Episode",
                                        true,
                                    )
                                ) {
                                    tvEpisodeCode(entry.parentIndexNumber, entry.indexNumber)
                                } else {
                                    null
                                },
                            fromQueue = true,
                            controller = controller,
                            router = currentRouter,
                        )
                    },
            )
        },
        createExtrasCoordinator = { scope ->
            PlaybackExtrasCoordinator(scope, TvJellyfinPlaybackExtrasService(sources.environmentProvider, httpClient))
        },
    )
}

private suspend fun TvPlaybackCoordinatorSources.resolveNextEpisode(
    mediaId: String,
    seriesId: String,
    controller: PlaybackController,
    router: TvPlaybackCommandRouter,
): PlaybackContinuationTarget? {
    val next = selectNextTvEpisode(browseRepository.latestEpisodesForSeries(seriesId), mediaId) ?: return null
    return continuationTarget(
        next = next,
        subtitle = tvEpisodeCode(next.parentIndexNumber, next.indexNumber),
        fromQueue = false,
        controller = controller,
        router = router,
    )
}

/** The up-next target that plays [next]; null while its detail or the server connection is missing. */
private suspend fun TvPlaybackCoordinatorSources.continuationTarget(
    next: JellyfinItem,
    subtitle: String?,
    fromQueue: Boolean,
    controller: PlaybackController,
    router: TvPlaybackCommandRouter,
): PlaybackContinuationTarget? {
    val detail = browseRepository.getItemDetail(next.id)
    val environment = environmentProvider.current()
    if (detail == null || environment == null) return null
    val images = images()
    return PlaybackContinuationTarget(
        mediaId = next.id,
        title = next.episodeTitle ?: next.name,
        subtitle = subtitle,
        imageUrl = jellyfinImageUrl(images.baseUrl, images.accessToken, next.id, next.primaryImageTag),
        fromQueue = fromQueue,
    ) {
        router.playNext {
            controller.play(PlaybackRequest.from(next, detail, startPolicy = PlaybackStartPolicy.RESTART), environment)
            controller.setPlaybackSpeed(settings().defaultPlaybackSpeed)
            controller.setStatsForNerdsEnabled(settings().statsForNerdsEnabled)
        }
    }
}
