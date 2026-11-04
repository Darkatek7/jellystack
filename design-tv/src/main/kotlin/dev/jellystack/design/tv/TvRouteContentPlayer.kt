@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.profile.mediaIdentity
import kotlinx.coroutines.launch

@Composable
internal fun TvSeerrDetailRouteContent(
    route: TvRoute.SeerrDetail,
    ctx: TvRouteContext,
) {
    val seerr = ctx.data.seerr
    val recommendationsCoordinator = ctx.coordinators.recommendations
    val key = route.mediaType to route.tmdbId
    val routeItem = route.toSearchItem()
    // Ready means Seerr's server is known; a load before that ends without a result.
    val seerrReady = seerr.recommendations is JellyseerrRecommendationsState.Ready
    LaunchedEffect(key, seerrReady) {
        if (seerrReady) recommendationsCoordinator.loadDetail(routeItem)
    }
    TvSeerrDetailScreen(
        route = route,
        detailState = seerr.details[key],
        requestsState = seerr.requests,
        requestsCoordinator = ctx.coordinators.requests,
        strings = ctx.ui.strings,
        onOpenItem = ctx.nav.openSeerr,
        castActions = TvSeerrCastActions(ctx.services.repositories.browse::findPersonByName, ctx.nav.openPerson),
        isInMyList =
            ctx.data.browse.savedMedia
                .any { it.identity == routeItem.mediaIdentity() },
        onToggleMyList = { item, save ->
            ctx.services.scope.launch {
                val myListRepository = ctx.services.repositories.myList
                if (save) {
                    myListRepository.saveSeerr(ctx.ui.profile.myListProfileId, item)
                } else {
                    myListRepository.removeSeerr(ctx.ui.profile.myListProfileId, item)
                }
            }
        },
        onRetry = { recommendationsCoordinator.reloadDetail(routeItem) },
    )
}

@Composable
@OptIn(UnstableApi::class)
internal fun TvPlayerRouteContent(ctx: TvRouteContext) {
    val playback = ctx.playback
    val player = ctx.data.player
    val settings = ctx.ui.settings
    val coordinators = playback.extras.coordinators
    val homeState = ctx.data.browse.homeState
    TvPlaybackScreen(
        controller = playback.controller,
        engine = playback.extras.engine,
        syncPlay = playback.syncPlay,
        playbackState = player.stateHolder.value,
        startState = playback.launcher.startState,
        syncState = player.syncPlayState,
        segmentState = player.segments.state,
        continuationState = player.continuationState,
        seekBackSeconds = settings.seekBackSeconds,
        seekForwardSeconds = settings.seekForwardSeconds,
        subtitleTextSize = settings.subtitleTextSize,
        subtitleBackground = settings.subtitleBackground,
        videoScale = settings.videoScale,
        onVideoScaleSelected = ctx.services.repositories.settings::setVideoScale,
        matchRefreshRate = settings.matchRefreshRate,
        extrasState = player.extrasState,
        timelineSegments = player.segments.timeline,
        imageBaseUrl = homeState.imageBaseUrl,
        imageAccessToken = homeState.imageAccessToken,
        onSeekTo = playback.commandRouter::seekTo,
        onSetPaused = playback.commandRouter::setPaused,
        loadEpisodes = ctx.services.repositories.browse::latestEpisodesForSeries,
        onPlayEpisode = playback.launcher::play,
        onSkipSegment = coordinators.segment::skip,
        onPlayNext = coordinators.continuation::playNext,
        onCancelAutoplay = coordinators.continuation::cancelAutoplay,
        strings = ctx.ui.strings,
        startActions =
            TvPlayerStartActions(
                onRetry = playback.launcher::retryStart,
                onCancel = playback.launcher::cancelStart,
                onFailureSeen = playback.launcher::cancelStart,
            ),
        audioQueue =
            TvAudioQueueControls(
                hasPrevious = { playback.extras.queue.entryBefore(it) != null },
                playPrevious = playback.launcher::playPrevious,
            ),
        stopPlayback = playback.extras.stopPlayback,
        onClose = {
            playback.launcher.cancelStart()
            // Closing the player leaves a SyncPlay group, as on the phone.
            if (playback.syncPlay.state.value.currentGroup != null) playback.syncPlay.leaveGroup()
            playback.extras.stopPlayback()
            ctx.services.appStateHolder.popRoute()
        },
    )
}

internal fun TvRoute.SeerrDetail.toSearchItem(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        overview = overview,
        releaseYear = releaseYear,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = null,
        tvdbId = tvdbId,
        availability = JellyseerrMediaAvailability(null, null),
        requests = emptyList(),
    )
