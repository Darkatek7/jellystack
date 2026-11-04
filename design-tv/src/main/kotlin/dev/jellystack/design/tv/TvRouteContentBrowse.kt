@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import dev.jellystack.core.profile.mediaIdentity
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator

@Composable
internal fun TvSearchRouteContent(ctx: TvRouteContext) {
    TvSearchScreen(
        searchState = ctx.data.seerr.searchState,
        homeState = ctx.data.browse.homeState,
        strings = ctx.ui.strings,
        focusMemory = ctx.ui.focusMemory,
        actions = tvSearchActions(ctx.coordinators.search, ctx.nav.openJellyfinDetail, ctx.nav.openSeerr),
    )
}

@Composable
internal fun TvDiscoverRouteContent(ctx: TvRouteContext) {
    val savedMedia = ctx.data.browse.savedMedia
    TvDiscoverScreen(
        ctx.data.seerr.recommendations,
        ctx.data.seerr.requests,
        ctx.ui.strings,
        ctx.ui.focusMemory,
        ctx.nav.openSeerr,
        onConnectSeerr = ctx.nav::openSettingsConnections,
        onRetry = ctx.coordinators.recommendations::refreshAll,
        isSaved = { item ->
            savedMedia.any { it.identity == item.mediaIdentity() }
        },
    )
}

@Composable
internal fun TvSettingsRouteContent(
    route: TvRoute.Settings,
    ctx: TvRouteContext,
) {
    val browse = ctx.data.browse
    val repositories = ctx.services.repositories
    TvSettingsScreen(
        section = route.section,
        settings = ctx.ui.settings,
        repository = repositories.settings,
        serverRepository = repositories.server,
        connectionCoordinator = ctx.services.koin.get<ServerConnectionCoordinator>(),
        quickConnectCoordinator = ctx.services.koin.get<JellyfinQuickConnectCoordinator>(),
        appVersion = ctx.ui.appVersion,
        strings = ctx.ui.strings,
        onOpenCategory = { category -> ctx.nav.push(tvSettingsRoute(category)) },
        onServersChanged = {
            ctx.coordinators.browse.bootstrap(true)
            ctx.coordinators.recommendations.refreshAll()
        },
        profileId = ctx.ui.profile.activeProfileId,
        profilePreferencesRepository = repositories.profiles.preferences,
        profileRepository = repositories.profiles.household,
        watchNextAvailable = ctx.ui.profile.watchNextAvailable,
        homeRows =
            tvHomeRowOptions(
                browse.homeState,
                browse.homeSections,
                browse.myList.isNotEmpty(),
                ctx.ui.strings,
                includeEmpty = true,
            ),
    )
}

@Composable
internal fun TvJellyfinDetailRouteContent(
    route: TvRoute.JellyfinDetail,
    ctx: TvRouteContext,
) {
    TvJellyfinDetailScreen(
        route = route,
        initialItem = ctx.services.appStateHolder.detailSource(route.itemId),
        homeState = ctx.data.browse.homeState,
        repository = ctx.services.repositories.browse,
        browseCoordinator = ctx.coordinators.browse,
        environmentProvider = ctx.services.environmentProvider,
        playbackController = ctx.playback.controller,
        playbackLauncher = ctx.playback.launcher,
        trailerResolver = ctx.data.trailer.hooks.detailResolver,
        strings = ctx.ui.strings,
        onOpenItem = ctx.nav.openJellyfinDetail,
        onOpenPerson = ctx.nav.openPerson,
        onPlaybackStarted = { ctx.nav.push(TvRoute.Player) },
    )
}

@Composable
internal fun TvItemListRouteContent(
    route: TvRoute,
    entryRouteKey: String,
    ctx: TvRouteContext,
) {
    val homeState = ctx.data.browse.homeState
    TvItemListRoute(
        route = route,
        data =
            TvItemListRouteData(
                routeKey = entryRouteKey,
                coordinator = ctx.coordinators.itemLists,
                states = ctx.data.browse.lists.itemListStates,
                images = TvLibraryGridImages(homeState.imageBaseUrl, homeState.imageAccessToken),
                favorites = homeState.favorites,
            ),
        playback = TvItemListRoutePlayback(ctx.playback.launcher, ctx.nav.openJellyfinDetail),
        strings = ctx.ui.strings,
        focusMemory = ctx.ui.focusMemory,
    )
}

@Composable
internal fun TvLiveTvRouteContent(
    entryRouteKey: String,
    ctx: TvRouteContext,
) {
    val homeState = ctx.data.browse.homeState
    TvLiveTvRoute(
        coordinator = ctx.data.browse.lists.liveTv.coordinator,
        context =
            TvLiveTvContext(
                images = TvLibraryGridImages(homeState.imageBaseUrl, homeState.imageAccessToken),
                routeKey = entryRouteKey,
                strings = ctx.ui.strings,
                focusMemory = ctx.ui.focusMemory,
            ),
        launcher = ctx.playback.launcher,
    )
}
