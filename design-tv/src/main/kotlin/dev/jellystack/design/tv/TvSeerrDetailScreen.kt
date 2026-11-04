@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "LongMethod", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.jellyseerr.SeerrPrimaryAction

/** The Seerr page for a title Jellyfin may not have: the same hero as the Jellyfin page, plus the request actions. */
@Composable
internal fun TvSeerrDetailScreen(
    route: TvRoute.SeerrDetail,
    detailState: JellyseerrMediaDetailState?,
    requestsState: JellyseerrRequestsState,
    requestsCoordinator: JellyseerrRequestsCoordinator,
    strings: TvStrings,
    onOpenItem: (JellyseerrSearchItem) -> Unit,
    isInMyList: Boolean = false,
    onToggleMyList: (JellyseerrSearchItem, Boolean) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val fallbackItem = remember(route) { tvSeerrFallbackItem(route) }
    val detail = (detailState as? JellyseerrMediaDetailState.Loaded)?.detail
    val readyRequests = requestsState as? JellyseerrRequestsState.Ready
    val activeRequest = readyRequests?.currentRequestsByMedia?.get(route.mediaType to route.tmdbId)
    val requestState =
        resolveTvSeerrRequestState(
            mediaType = route.mediaType,
            detail = detail,
            activeRequest = activeRequest,
            capabilities = readyRequests?.capabilities ?: JellyseerrRequestCapabilities.NONE,
        )
    val requestCommand = requestState.command
    var showRequestDialog by remember(route) { mutableStateOf(false) }
    LaunchedEffect(activeRequest?.id) {
        if (activeRequest != null) showRequestDialog = false
    }
    val hasRequestAction = readyRequests != null && requestCommand.primaryAction != null
    val requestMoreSeasons = requestCommand.primaryAction == SeerrPrimaryAction.RequestMoreSeasons
    val trailerUrl = detail?.trailer?.url ?: detail?.videos?.firstOrNull { it.url != null }?.url
    val status = detailState.tvSeerrDetailStatus()
    val primaryAction = tvSeerrPrimaryAction(status, canRequest = hasRequestAction, hasTrailer = trailerUrl != null)
    val myListFocusRequester = remember(route) { FocusRequester() }
    val title = detail?.title ?: route.title
    val uiState =
        buildTvSeerrDetailUiState(
            routeKey = route.focusRouteKey(),
            overview = detail?.overview ?: route.overview,
            tagline = detail?.tagline,
            ratings = detail?.ratings,
            cast = detail?.cast.orEmpty(),
            similar = detail?.enrichment?.similar.orEmpty(),
            detailRows = tvSeerrDetailRows(detail, strings),
        )
    val heroModel = tvSeerrHeroModel(route, detail, strings)
    val statusLabel = tvSeerrStatusLabel(requestState, readyRequests != null, strings)
    TvDetailFocusLayout(
        uiState = uiState,
        heroContentDescription = title,
        hasPrimaryAction = true,
        modifier = modifier,
        heroContent = { primaryActionModifier, actionRowModifier ->
            TvDetailHero(heroModel) {
                TvSeerrDetailLoadingLine(status, strings)
                statusLabel?.let { TvDetailStatusChip(it) }
                TvSeerrHeroActions(
                    state =
                        TvSeerrHeroActionsState(
                            primaryAction = primaryAction,
                            hasRequestAction = hasRequestAction,
                            requestMoreSeasons = requestMoreSeasons,
                            isInMyList = isInMyList,
                            hasTrailer = trailerUrl != null,
                        ),
                    callbacks =
                        TvSeerrHeroActionCallbacks(
                            onRetry = {
                                // Retry leaves while the detail reloads; My List stays, so it keeps the focus.
                                runCatching { myListFocusRequester.requestFocus() }
                                onRetry()
                            },
                            onRequest = { showRequestDialog = true },
                            onToggleMyList = { onToggleMyList(fallbackItem, !isInMyList) },
                            onTrailer = { trailerUrl?.let { url -> runCatching { uriHandler.openUri(url) } } },
                        ),
                    strings = strings,
                    myListFocusRequester = myListFocusRequester,
                    primaryActionModifier = primaryActionModifier,
                    actionRowModifier = actionRowModifier,
                )
            }
        },
    ) { bodyFocusModifier, sectionFocusModifiers ->
        tvSeerrDetailSections(
            uiState = uiState,
            strings = strings,
            bodyFocusModifier = bodyFocusModifier,
            sectionFocusModifiers = sectionFocusModifiers,
            onOpenItem = onOpenItem,
        )
    }
    if (showRequestDialog && readyRequests != null) {
        TvRequestDialog(
            title = title,
            mediaType = route.mediaType,
            seasons = tvSeerrRequestSeasons(requestMoreSeasons, requestState, detail),
            onlyListedSeasons = requestMoreSeasons,
            requestsState = readyRequests,
            strings = strings,
            onDismiss = { showRequestDialog = false },
            onSubmit = { profile, selection, variant ->
                requestsCoordinator.submitRequest(
                    item = fallbackItem,
                    profileSelection = profile,
                    seasons = selection,
                    variant = variant,
                )
            },
        )
    }
}

/** The chip under the facts: the availability, or that this user cannot request when nothing else can be done. */
internal fun tvSeerrStatusLabel(
    requestState: TvSeerrRequestState,
    requestsReady: Boolean,
    strings: TvStrings,
): String? {
    val command = requestState.command
    return when {
        command.showStatus && !command.permissionDenied -> requestState.mediaStatus.label(strings)
        requestsReady && command.primaryAction == null -> strings.cannotRequest
        else -> null
    }
}

/** The seasons the request dialog offers: the ones still requestable, else every real season of the show. */
internal fun tvSeerrRequestSeasons(
    requestMoreSeasons: Boolean,
    requestState: TvSeerrRequestState,
    detail: JellyseerrMediaDetail?,
): List<Int> =
    if (requestMoreSeasons) {
        requestState.requestableSeasons
    } else {
        detail
            ?.seasons
            .orEmpty()
            .filter { it.seasonNumber > 0 }
            .map { it.seasonNumber }
    }

private fun tvSeerrFallbackItem(route: TvRoute.SeerrDetail) =
    JellyseerrSearchItem(
        tmdbId = route.tmdbId,
        mediaType = route.mediaType,
        title = route.title,
        overview = route.overview,
        releaseYear = route.releaseYear,
        posterPath = route.posterPath,
        backdropPath = route.backdropPath,
        mediaInfoId = null,
        tvdbId = route.tvdbId,
        availability = JellyseerrMediaAvailability(null, null),
        requests = emptyList(),
    )

/** Which of the Seerr actions exist; [primaryAction] is the one that takes focus first. */
internal data class TvSeerrHeroActionsState(
    val primaryAction: TvSeerrPrimaryAction,
    val hasRequestAction: Boolean,
    val requestMoreSeasons: Boolean,
    val isInMyList: Boolean,
    val hasTrailer: Boolean,
)

internal class TvSeerrHeroActionCallbacks(
    val onRetry: () -> Unit,
    val onRequest: () -> Unit,
    val onToggleMyList: () -> Unit,
    val onTrailer: () -> Unit,
)

/**
 * Retry or Request as the primary pill, My List as a pill (the primary one when it is the only), and the trailer as a
 * round icon button. The element [TvSeerrHeroActionsState.primaryAction] names gets the first focus.
 */
@Composable
internal fun TvSeerrHeroActions(
    state: TvSeerrHeroActionsState,
    callbacks: TvSeerrHeroActionCallbacks,
    strings: TvStrings,
    myListFocusRequester: FocusRequester,
    primaryActionModifier: Modifier,
    actionRowModifier: Modifier,
) {
    val focusFor: (TvSeerrPrimaryAction) -> Modifier = { action ->
        (if (state.primaryAction == action) primaryActionModifier else Modifier).then(actionRowModifier)
    }
    val pillWidth = Modifier.widthIn(min = TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(TV_DETAIL_ACTION_GAP_DP.dp), verticalAlignment = Alignment.Top) {
        if (state.primaryAction == TvSeerrPrimaryAction.RETRY) {
            TvActionButton(
                strings.retry,
                primary = true,
                onClick = callbacks.onRetry,
                modifier = focusFor(TvSeerrPrimaryAction.RETRY).then(pillWidth),
            )
        }
        if (state.hasRequestAction) {
            TvActionButton(
                if (state.requestMoreSeasons) strings.requestMoreSeasons else strings.request,
                primary = true,
                onClick = callbacks.onRequest,
                modifier = focusFor(TvSeerrPrimaryAction.REQUEST).then(pillWidth),
            )
        }
        TvActionButton(
            label = if (state.isInMyList) strings.remove else strings.myList,
            onClick = callbacks.onToggleMyList,
            primary = !state.hasRequestAction && state.primaryAction != TvSeerrPrimaryAction.RETRY,
            selected = state.isInMyList,
            modifier = Modifier.focusRequester(myListFocusRequester).then(focusFor(TvSeerrPrimaryAction.MY_LIST)).then(pillWidth),
        )
        if (state.hasTrailer) {
            TvDetailRoundButton(
                icon = Icons.Default.LocalMovies,
                label = strings.trailer,
                onClick = callbacks.onTrailer,
                modifier = focusFor(TvSeerrPrimaryAction.TRAILER),
            )
        }
    }
}

/** The Seerr hero: year, runtime, age rating and TMDB score in one line, then the genres and three lines of overview. */
internal fun tvSeerrHeroModel(
    route: TvRoute.SeerrDetail,
    detail: JellyseerrMediaDetail?,
    strings: TvStrings,
): TvDetailHeroModel =
    TvDetailHeroModel(
        title = detail?.title ?: route.title,
        backdropUrl =
            tmdbImageUrl(
                detail?.backdropPath ?: route.backdropPath ?: detail?.posterPath ?: route.posterPath,
                backdrop = (detail?.backdropPath ?: route.backdropPath) != null,
            ),
        logoUrl = null,
        titleColor = TvText,
        metaLine =
            tvDetailMetaLine(
                year = detail?.year ?: route.releaseYear,
                runtimeMinutes = detail?.runtimeMinutes?.toLong(),
                officialRating = detail?.certification,
                communityRating = detail?.ratings?.tmdb,
                endsAt = null,
                strings = strings,
            ),
        subtitle =
            detail
                ?.genres
                .orEmpty()
                .take(4)
                .joinToString(TV_DETAIL_META_SEPARATOR),
        tagline = detail?.tagline?.takeIf(String::isNotBlank),
        overview = (detail?.overview ?: route.overview)?.takeIf(String::isNotBlank),
    )

/** Director and studios, the credits Seerr knows. */
internal fun tvSeerrDetailRows(
    detail: JellyseerrMediaDetail?,
    strings: TvStrings,
): List<TvDetailRow> {
    detail ?: return emptyList()
    val copy = strings.detailScreen
    val directors =
        detail.crew
            .filter { it.job.equals("Director", ignoreCase = true) }
            .map { it.name }
            .distinct()
    return listOf(copy.directors to directors, copy.studios to detail.studios)
        .filter { (_, values) -> values.isNotEmpty() }
        .map { (label, values) -> TvDetailRow(label, values.joinToString(", ")) }
}

/** The availability or request status as a small outlined chip under the facts. */
@Composable
internal fun TvDetailStatusChip(label: String) {
    Text(
        label,
        color = TvText,
        fontSize = TvTextSize.Body,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier =
            Modifier
                .background(TvPurpleStrong.copy(alpha = 0.35f), TvShapes.Pill)
                .border(1.dp, TvPurple.copy(alpha = 0.6f), TvShapes.Pill)
                .padding(horizontal = 16.dp, vertical = 5.dp),
    )
}
