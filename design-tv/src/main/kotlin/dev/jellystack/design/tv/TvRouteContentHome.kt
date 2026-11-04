@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.LibraryBrowseQuery

@Composable
internal fun TvHomeRouteContent(
    homeBack: TvHomeBackState,
    ctx: TvRouteContext,
) {
    val browse = ctx.data.browse
    val trailer = ctx.data.trailer
    val trailerPreviewCoordinator = ctx.data.trailer.coordinator
    val serverKey = trailer.hooks.serverKey
    val settings = ctx.ui.settings
    TvHomeScreen(
        homeBack = homeBack,
        state = browse.homeState,
        homeSections = browse.homeSections,
        strings = ctx.ui.strings,
        trailerPreviewState = trailer.state,
        focusMemory = ctx.ui.focusMemory,
        onRefresh = {
            trailerPreviewCoordinator.invalidateCache()
            ctx.coordinators.browse.bootstrap(true)
        },
        onPreviewFocus = { owner, item, presentationId ->
            if (trailer.hooks.currentRoute.allowsTrailerPreview() && serverKey != null) {
                trailerPreviewCoordinator.focus(trailerPreviewRequest(serverKey, owner, item, presentationId))
            }
        },
        onPreviewBlur = { owner, item, presentationId ->
            if (serverKey != null) {
                trailerPreviewCoordinator.clearFocus(trailerPreviewRequest(serverKey, owner, item, presentationId))
            }
        },
        onCancelPreview = trailerPreviewCoordinator::clearFocus,
        trailerPreviewEngine = trailer.engine,
        previewSoundEnabled = settings.trailerPreviewSoundEnabled,
        previewProgress = trailer.progress,
        onPlayItem = { item ->
            trailerPreviewCoordinator.clearFocus()
            ctx.playback.launcher.play(item)
        },
        onItem = {
            trailerPreviewCoordinator.clearFocus()
            ctx.nav.openJellyfinDetail(it)
        },
        onHomeLibrary = ctx.nav.openLibrary,
        onLibrary = { ctx.nav.openLibrary(it.id, it.name) },
        onSeerrItem = ctx.nav::openSeerrRoute,
        myList = browse.myList,
        onMyListEntry = { entry ->
            entry.jellyfinItem?.let(ctx.nav.openJellyfinDetail)
                ?: entry.savedMedia?.toTvRoute()?.let(ctx.nav.push)
        },
        spotlightAutoAdvance = settings.spotlightAutoCycle,
        rowPreferences = settings.homeRows,
        heroHintsSeen = settings.heroHintsSeen,
        onHeroHintsUsed = {
            ctx.services.repositories.settings
                .setHeroHintsSeen(true)
        },
    )
}

private fun trailerPreviewRequest(
    serverKey: String,
    owner: TvTrailerPreviewOwner,
    item: JellyfinItem,
    presentationId: String?,
): TvTrailerPreviewRequest =
    TvTrailerPreviewRequest(
        owner = owner,
        presentationId = presentationId,
        target =
            TvTrailerPreviewTarget(
                serverKey = serverKey,
                itemId = item.id,
                isEpisode = item.type.equals("Episode", true),
                seriesId = item.seriesId,
            ),
    )

@Composable
internal fun TvLibraryRouteContent(
    route: TvRoute.Library,
    ctx: TvRouteContext,
) {
    val homeState = ctx.data.browse.homeState
    val library = homeState.libraries.firstOrNull { it.id == route.libraryId }
    val rememberedQuery = rememberTvLibraryRememberedQuery(route, ctx)
    val trailer = ctx.data.trailer
    val browseCoordinator = ctx.coordinators.browse
    TvLibraryScreen(
        route = route,
        state = homeState,
        strings = ctx.ui.strings,
        focusMemory = ctx.ui.focusMemory,
        onSelectLibrary = { id ->
            val selected = homeState.libraries.firstOrNull { it.id == id }
            if (route.libraryId == null) {
                ctx.nav.openLibrary(id, selected?.name)
            } else {
                browseCoordinator.selectLibrary(id)
            }
        },
        onOpenItem = ctx.nav.openJellyfinDetail,
        onOpenContainer = { container ->
            container.itemListRoute()?.let(ctx.nav.push) ?: browseCoordinator.openContainer(container)
        },
        onLoadMore = browseCoordinator::loadNextPage,
        onJumpToLetter = browseCoordinator::jumpToLibraryLetter,
        onLoadLatest = browseCoordinator::loadLibraryLatest,
        onLetterJumpHandled = browseCoordinator::consumeLibraryLetterJump,
        onRetry = {
            if (route.libraryId == null) {
                browseCoordinator.refreshLibraries()
            } else {
                browseCoordinator.refreshSelectedLibrary()
            }
        },
        homeSections = ctx.data.browse.homeSections,
        myListItems =
            ctx.data.browse.myList
                .mapNotNull { it.jellyfinItem },
        collectionType = library?.collectionType,
        rememberedQuery = rememberedQuery,
        onModeChanged = { mode ->
            when {
                mode == route.mode -> Unit
                mode == TvLibraryMode.ALL_TITLES -> ctx.nav.push(route.copy(mode = mode))
                route.mode == TvLibraryMode.ALL_TITLES -> ctx.services.appStateHolder.popRoute()
                else -> Unit
            }
        },
        onQueryChanged = { query ->
            val profileId = ctx.ui.profile.activeProfileId
            val libraryId = route.libraryId
            if (profileId != null && libraryId != null) {
                ctx.services.repositories.profiles.preferences
                    .setLibraryBrowseQuery(profileId, libraryId, query)
            }
            browseCoordinator.setLibraryBrowseQuery(query)
        },
        onPlayItem = { item ->
            ctx.data.trailer.coordinator
                .clearFocus()
            ctx.playback.launcher.play(item)
        },
        cinematicModesEnabled = true,
        trailerPreviewState = trailer.state,
        trailerPreviewEngine = trailer.engine,
        previewSoundEnabled = ctx.ui.settings.trailerPreviewSoundEnabled,
        previewProgress = trailer.progress,
        onPreviewFocus = trailer.hooks.focusCinematicTrailer,
        onPreviewClear = {
            ctx.data.trailer.coordinator
                .clearFocus(TvTrailerPreviewOwner.CARD)
        },
    )
}

/**
 * The saved query is the screen's starting point only; reading it again on every save could reset a newer choice
 * when a write lands late.
 */
@Composable
private fun rememberTvLibraryRememberedQuery(
    route: TvRoute.Library,
    ctx: TvRouteContext,
): LibraryBrowseQuery {
    val activeProfileId = ctx.ui.profile.activeProfileId
    val profilePreferencesRepository = ctx.services.repositories.profiles.preferences
    return remember(activeProfileId, route.libraryId) {
        if (activeProfileId != null && route.libraryId != null) {
            profilePreferencesRepository.libraryBrowseQuery(activeProfileId, route.libraryId).value
        } else {
            LibraryBrowseQuery.DEFAULT
        }
    }
}
