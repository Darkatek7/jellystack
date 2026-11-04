@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.collectLatest

/** What decides where focus belongs: the route, whether its rail or content has focus, and whether it can be shown. */
internal data class TvRootFocusState(
    val currentRoute: TvRoute,
    val routeKey: String,
    val showRail: Boolean,
    val railExpanded: Boolean,
    val isForeground: Boolean,
    val contentAuthoritativelyLoaded: Boolean,
    /** Whether a library's sort and filter controls may take the first focus: only for a library known to be empty. */
    val queryControlsMayLead: Boolean = contentAuthoritativelyLoaded,
)

internal class TvRootFocusSessions(
    val content: TvSemanticFocusRestorationSession,
    val rail: TvSemanticFocusRestorationSession,
)

/**
 * Restores focus for the current route (or the navigation rail) whenever the route, its content or its focus targets
 * change. The coordinator's registration revision is read inside the effect, so a card registering itself restarts the
 * restoration without recomposing the root.
 */
@Composable
internal fun TvRootFocusRestoration(
    focusCoordinator: TvFocusCoordinator<FocusRequester>,
    appStateHolder: TvAppStateHolder,
    focus: TvRootFocusState,
    sessions: TvRootFocusSessions,
) {
    LaunchedEffect(
        focus.showRail,
        focus.railExpanded,
        focus.routeKey,
        focus.isForeground,
        focus.contentAuthoritativelyLoaded,
        sessions.content,
    ) {
        if (!focus.showRail || !focus.isForeground) return@LaunchedEffect
        snapshotFlow { focusCoordinator.registrationRevision }.collectLatest {
            if (focus.railExpanded) {
                restoreRailFocus(focusCoordinator, appStateHolder, focus, sessions.rail)
            } else {
                restoreContentFocus(focusCoordinator, focus, sessions.content)
            }
        }
    }
}

private fun tryFocus(requester: FocusRequester): Boolean = runCatching { requester.requestFocus() }.getOrDefault(false)

private suspend fun restoreRailFocus(
    focusCoordinator: TvFocusCoordinator<FocusRequester>,
    appStateHolder: TvAppStateHolder,
    focus: TvRootFocusState,
    railRestorationSession: TvSemanticFocusRestorationSession,
) {
    val railRestoration =
        railRestorationSession.restoreOnce(
            coordinator = focusCoordinator,
            routeKey = TV_FOCUS_RAIL_ROUTE,
            preferredTargetId = tvRailTargetId(focus.currentRoute),
            requestFocus = ::tryFocus,
        )
    if (railRestoration is TvFocusRestoration.Failed) {
        appStateHolder.closeRail()
        focusCoordinator.onUserMovement()
        focusCoordinator.restoreFocus(routeKey = focus.routeKey, requestFocus = ::tryFocus)
    }
}

private suspend fun restoreContentFocus(
    focusCoordinator: TvFocusCoordinator<FocusRequester>,
    focus: TvRootFocusState,
    semanticRestorationSession: TvSemanticFocusRestorationSession,
) {
    // A missing content target must not turn route restoration (including Back) into a rail opener.
    semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
    if (!semanticRestorationSession.isPending) return
    val semanticTargetId =
        semanticRestorationSession.preferredTargetId(
            availableTargets = focusCoordinator.focusTargets(focus.routeKey),
            contentAuthoritativelyLoaded = focus.contentAuthoritativelyLoaded,
            queryControlsMayLead = focus.queryControlsMayLead,
        ) ?: return
    val restoration =
        focusCoordinator.restoreFocus(
            routeKey = focus.routeKey,
            preferredTargetId = semanticTargetId,
            includeFallback = false,
            requiredInteractionRevision = semanticRestorationSession.interactionRevision,
            requestFocus = ::tryFocus,
        )
    when (restoration) {
        is TvFocusRestoration.Focused -> semanticRestorationSession.complete()
        TvFocusRestoration.Cancelled ->
            semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
        TvFocusRestoration.Failed -> Unit
    }
}
