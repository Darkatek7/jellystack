@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.LocalTrailerContext
import dev.jellystack.core.jellyfin.LocalTrailerResolver
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackState
import kotlinx.coroutines.flow.Flow
import org.koin.core.Koin

/** The trailer resolvers and the card-focus preview coordinator of one account session. */
internal class TvTrailerWiring(
    val detailResolver: DetailTrailerResolver,
    val previewCoordinator: TvTrailerPreviewController,
)

/** The controller and engine that play trailer previews, separate from the main player. */
@OptIn(UnstableApi::class)
internal class TvTrailerPreviewPlayback(
    val controller: PlaybackController,
    val engine: AndroidPlayerEngine,
)

@Composable
@OptIn(UnstableApi::class)
internal fun rememberTvTrailerWiring(
    accountGeneration: TvAccountGeneration,
    browseRepository: JellyfinBrowseRepository,
    environmentProvider: JellyfinEnvironmentProvider,
    previewPlayback: TvTrailerPreviewPlayback,
    koin: Koin,
): TvTrailerWiring {
    val scope = accountGeneration.scope
    val detailTrailerResolver =
        remember(accountGeneration) {
            DetailTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
                fetchSeerrTrailer = { tmdbId, isShow ->
                    koin.get<JellyseerrEnvironmentProvider>().current()?.let { environment ->
                        koin
                            .get<JellyseerrRepository>()
                            .fetchRecommendationDetail(
                                environment,
                                tmdbId,
                                if (isShow) JellyseerrMediaType.TV else JellyseerrMediaType.MOVIE,
                            ).trailer
                    }
                },
            )
        }
    val localTrailerResolver =
        remember(accountGeneration) {
            LocalTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
            )
        }
    val trailerPreviewPlayer =
        remember(accountGeneration, previewPlayback.controller, previewPlayback.engine, environmentProvider) {
            TvPlaybackTrailerPreviewPlayer(
                controller = previewPlayback.controller,
                engine = previewPlayback.engine,
                environmentProvider = environmentProvider,
            )
        }
    val trailerPreviewCoordinator =
        remember(accountGeneration, localTrailerResolver, trailerPreviewPlayer) {
            TvTrailerPreviewController(
                scope = scope,
                resolve = { target ->
                    if (environmentProvider.current()?.serverKey != target.serverKey) {
                        null
                    } else {
                        localTrailerResolver.resolve(
                            LocalTrailerContext(
                                itemId = target.itemId,
                                isEpisode = target.isEpisode,
                                seriesId = target.seriesId,
                            ),
                        )
                    }
                },
                player = trailerPreviewPlayer,
            )
        }
    return TvTrailerWiring(detailTrailerResolver, trailerPreviewCoordinator)
}

/** Applies the trailer preview settings to [coordinator] and keeps it quiet while the main player is active. */
@Composable
internal fun TvTrailerPreviewSettingsEffects(
    coordinator: TvTrailerPreviewController,
    settings: AppSettings,
    playbackStates: Flow<PlaybackState>,
    accountGeneration: TvAccountGeneration,
    serverRepository: ServerRepository,
) {
    LaunchedEffect(settings.trailerPreviewsEnabled) {
        coordinator.setEnabled(settings.trailerPreviewsEnabled)
    }
    LaunchedEffect(settings.trailerPreviewSoundEnabled) {
        coordinator.setSoundEnabled(settings.trailerPreviewSoundEnabled)
    }
    LaunchedEffect(settings.trailerPreviewDelayMillis) {
        coordinator.setFocusDelayMillis(settings.trailerPreviewDelayMillis.toLong())
    }
    LaunchedEffect(playbackStates) {
        playbackStates.collect { state ->
            if (state is PlaybackState.Active || state is PlaybackState.Preparing) coordinator.clearFocus()
        }
    }
    LaunchedEffect(accountGeneration, serverRepository.currentServers()) { coordinator.invalidateCache() }
}

/** What each route's screen has finished loading, as far as focus restoration may rely on it. */
internal class TvFocusLoadInputs(
    val homeState: JellyfinHomeState,
    val homeSections: HomeSectionsState,
    val searchState: TvSearchUiState,
    val recommendations: JellyseerrRecommendationsState,
    val itemListStates: Map<String, TvItemListState>,
)

internal fun tvFocusContentAuthoritativelyLoaded(
    route: TvRoute,
    inputs: TvFocusLoadInputs,
): Boolean =
    when (route) {
        TvRoute.Home ->
            !inputs.homeState.isInitialLoading &&
                !inputs.homeState.isHomeLoading &&
                inputs.homeSections !is HomeSectionsState.Loading
        is TvRoute.Library ->
            !inputs.homeState.isLibraryLoading &&
                !inputs.homeState.isPageLoading &&
                (route.mode != TvLibraryMode.ALL_TITLES || tvLibraryContentDetermined(inputs.homeState))
        TvRoute.Search ->
            !inputs.searchState.jellyfin.isLoading && !inputs.searchState.seerr.isLoading
        TvRoute.Discover -> discoverContentLoaded(inputs.recommendations)
        is TvRoute.Person, is TvRoute.Collection, is TvRoute.Music ->
            inputs.itemListStates[requireNotNull(route.itemListSource()).key()] !is TvItemListState.Loading
        else -> true
    }

private fun discoverContentLoaded(recommendations: JellyseerrRecommendationsState): Boolean =
    recommendations !is JellyseerrRecommendationsState.Loading &&
        (recommendations as? JellyseerrRecommendationsState.Ready)
            ?.rails
            ?.values
            ?.none { it.isLoading } != false
