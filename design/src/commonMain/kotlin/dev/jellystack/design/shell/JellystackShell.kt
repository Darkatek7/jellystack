package dev.jellystack.design.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.jellystack.design.layout.AdaptivePaneLayout
import dev.jellystack.design.layout.JellystackNavigationMode
import dev.jellystack.design.layout.LocalResponsiveProfile

@Composable
internal fun JellystackShell(
    state: JellystackShellState,
    onAction: (JellystackShellAction) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    primaryContent: @Composable (PaddingValues) -> Unit,
    secondaryContent: (@Composable (PaddingValues) -> Unit)? = null,
) {
    val profile = LocalResponsiveProfile.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedback?.id) {
        val feedback = state.feedback ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = feedback.message,
                actionLabel = feedback.actionLabel,
            )
        onAction(
            if (result == SnackbarResult.ActionPerformed) {
                JellystackShellAction.FeedbackAction
            } else {
                JellystackShellAction.FeedbackShown
            },
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (state.showNavigation && profile.navigationMode == JellystackNavigationMode.Rail) {
            JellystackNavigationRail(selected = state.primary, destinations = state.destinations) { destination ->
                onAction(JellystackShellAction.SelectPrimary(destination))
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = topBar,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            bottomBar = {
                if (state.showNavigation && profile.navigationMode == JellystackNavigationMode.Dock) {
                    JellystackBottomDock(
                        selected = state.primary,
                        showLabels = profile.dockShowsAllLabels,
                        destinations = state.destinations,
                    ) { destination ->
                        onAction(JellystackShellAction.SelectPrimary(destination))
                    }
                }
            },
        ) { measuredPadding ->
            AdaptivePaneLayout(
                paneMode = state.paneMode,
                contentPadding = measuredPadding,
                primaryContent = primaryContent,
                secondaryContent = secondaryContent,
            )
        }
    }
}
