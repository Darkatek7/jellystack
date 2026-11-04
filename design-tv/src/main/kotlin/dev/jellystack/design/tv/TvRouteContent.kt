@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable

/** The screen for one route of the signed-in app, rendered inside its [TvRouteFocusScope]. */
@Composable
internal fun TvRouteContent(
    route: TvRoute,
    entryRouteKey: String,
    homeBack: TvHomeBackState,
    ctx: TvRouteContext,
) {
    when (route) {
        TvRoute.Home -> TvHomeRouteContent(homeBack, ctx)
        is TvRoute.Library -> TvLibraryRouteContent(route, ctx)
        TvRoute.Search -> TvSearchRouteContent(ctx)
        TvRoute.Discover -> TvDiscoverRouteContent(ctx)
        is TvRoute.Settings -> TvSettingsRouteContent(route, ctx)
        is TvRoute.JellyfinDetail -> TvJellyfinDetailRouteContent(route, ctx)
        is TvRoute.Person, is TvRoute.Collection, is TvRoute.Music -> TvItemListRouteContent(route, entryRouteKey, ctx)
        TvRoute.LiveTv -> TvLiveTvRouteContent(entryRouteKey, ctx)
        is TvRoute.SeerrDetail -> TvSeerrDetailRouteContent(route, ctx)
        TvRoute.Player -> TvPlayerRouteContent(ctx)
    }
}
