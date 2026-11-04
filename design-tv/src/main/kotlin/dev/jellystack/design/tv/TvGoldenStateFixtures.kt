@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jellystack.core.preferences.MotionPreference

/** Screen states the golden fixtures draw with the production composables; see [TvGoldenFixture]. */
internal val GOLDEN_STATE_FIXTURES =
    setOf(
        TvGoldenFixture.LIBRARY_GRID,
        TvGoldenFixture.LIBRARY_FILTER_PANEL,
        TvGoldenFixture.LIBRARY_LANDING,
        TvGoldenFixture.EMPTY_STATE,
        TvGoldenFixture.ERROR_STATE,
        TvGoldenFixture.SKELETON_ROWS,
        TvGoldenFixture.DETAIL_LOAD_ERROR,
        TvGoldenFixture.NAVIGATION_RAIL_OPEN,
        TvGoldenFixture.RESUME_DIALOG,
        TvGoldenFixture.DISCOVER_ROWS,
        TvGoldenFixture.LIVE_TV_GUIDE,
    )

/** The library and Live TV screens draw their own safe-area padding. */
private val GOLDEN_LIBRARY_FIXTURES =
    setOf(
        TvGoldenFixture.LIBRARY_GRID,
        TvGoldenFixture.LIBRARY_FILTER_PANEL,
        TvGoldenFixture.LIBRARY_LANDING,
        TvGoldenFixture.LIVE_TV_GUIDE,
    )

/** Reduced motion keeps the loading placeholders still, so the images are the same on every run. */
@Composable
internal fun GoldenStateFixture(
    fixture: TvGoldenFixture,
    strings: TvStrings,
    modifier: Modifier = Modifier,
) {
    JellystackTvTheme(motionPreference = MotionPreference.REDUCED) {
        Box(
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(
                    horizontal = if (fixture in GOLDEN_LIBRARY_FIXTURES) 0.dp else TvLayoutTokens.SafeInsets.horizontal,
                    vertical = if (fixture in GOLDEN_LIBRARY_FIXTURES) 0.dp else TvLayoutTokens.SafeInsets.vertical,
                ),
        ) {
            when (fixture) {
                TvGoldenFixture.LIBRARY_GRID -> GoldenLibraryGrid(strings, panelOpen = false)
                TvGoldenFixture.LIBRARY_FILTER_PANEL -> GoldenLibraryGrid(strings, panelOpen = true)
                TvGoldenFixture.LIBRARY_LANDING -> GoldenLibraryLanding(strings)
                TvGoldenFixture.EMPTY_STATE,
                TvGoldenFixture.ERROR_STATE,
                -> GoldenStatusState(fixture, strings, Modifier.align(Alignment.Center))
                TvGoldenFixture.LIVE_TV_GUIDE -> GoldenLiveTvGuide(strings)
                TvGoldenFixture.SKELETON_ROWS -> TvSkeletonRows(strings.loading, rowCount = 2)
                TvGoldenFixture.DETAIL_LOAD_ERROR ->
                    TvDetailLoadError(strings.detailText.detailLoadFailed, strings.retry, onRetry = {})
                TvGoldenFixture.NAVIGATION_RAIL_OPEN -> GoldenNavigationRail(strings)
                TvGoldenFixture.DISCOVER_ROWS -> GoldenDiscoverRows(strings)
                TvGoldenFixture.RESUME_DIALOG ->
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) {
                        TvResumeAskDialogContent("12:34", strings) {}
                    }
                else -> Unit
            }
        }
    }
}

@Composable
private fun GoldenNavigationRail(strings: TvStrings) {
    Row(Modifier.fillMaxSize()) {
        TvNavigationRail(
            selected = TvRoute.Home,
            expanded = true,
            strings = strings,
            profileName = "Alex",
            callbacks = TvNavigationRailCallbacks(onProfile = {}, onSelected = {}, onDismiss = {}),
        )
    }
}

/** An empty library, or a Discover source that failed, centred. */
@Composable
private fun GoldenStatusState(
    fixture: TvGoldenFixture,
    strings: TvStrings,
    modifier: Modifier,
) {
    if (fixture == TvGoldenFixture.EMPTY_STATE) {
        TvEmptyState(
            icon = Icons.Default.VideoLibrary,
            title = strings.emptyStates.noLibraries,
            hint = strings.emptyStates.librariesHint,
            modifier = modifier,
            action = TvEmptyStateAction(strings.retry, TV_LIBRARY_EMPTY_ACTION_TARGET) {},
        )
    } else {
        TvSearchSourceFailure(
            message = strings.seerr.discoverLoadFailed,
            retryLabel = strings.retry,
            focusTargetId = TV_DISCOVER_RETRY_TARGET,
            modifier = modifier,
            onRetry = {},
        )
    }
}
