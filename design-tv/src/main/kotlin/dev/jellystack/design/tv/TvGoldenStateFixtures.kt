@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.core.preferences.MotionPreference

/** Screen states the golden fixtures draw with the production composables; see [TvGoldenFixture]. */
internal val GOLDEN_STATE_FIXTURES =
    setOf(
        TvGoldenFixture.LIBRARY_GRID,
        TvGoldenFixture.EMPTY_STATE,
        TvGoldenFixture.ERROR_STATE,
        TvGoldenFixture.SKELETON_ROWS,
        TvGoldenFixture.DETAIL_LOAD_ERROR,
        TvGoldenFixture.NAVIGATION_RAIL_OPEN,
        TvGoldenFixture.RESUME_DIALOG,
        TvGoldenFixture.DISCOVER_ROWS,
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
                    horizontal = TvLayoutTokens.SafeInsets.horizontal,
                    vertical = TvLayoutTokens.SafeInsets.vertical,
                ),
        ) {
            when (fixture) {
                TvGoldenFixture.LIBRARY_GRID -> GoldenLibraryGrid(strings)
                TvGoldenFixture.EMPTY_STATE ->
                    TvEmptyState(
                        icon = Icons.Default.VideoLibrary,
                        title = strings.emptyStates.noLibraries,
                        hint = strings.emptyStates.librariesHint,
                        modifier = Modifier.align(Alignment.Center),
                        action = TvEmptyStateAction(strings.retry, TV_LIBRARY_EMPTY_ACTION_TARGET) {},
                    )
                TvGoldenFixture.ERROR_STATE ->
                    TvSearchSourceFailure(
                        message = strings.discoverLoadFailed,
                        retryLabel = strings.retry,
                        focusTargetId = TV_DISCOVER_RETRY_TARGET,
                        modifier = Modifier.align(Alignment.Center),
                        onRetry = {},
                    )
                TvGoldenFixture.SKELETON_ROWS -> TvSkeletonRows(strings.loading, rowCount = 2)
                TvGoldenFixture.DETAIL_LOAD_ERROR ->
                    TvDetailLoadError(strings.detailLoadFailed, strings.retry, onRetry = {})
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
private fun GoldenLibraryGrid(strings: TvStrings) {
    val titles =
        listOf(
            "The Last Horizon",
            "Northern Lights",
            "Paper Moon",
            "Harbor Lights",
            "Quiet Signal",
            "Northbound",
            "Glass Harbor",
            "Late Tide",
        )
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(strings.library, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            titles.forEachIndexed { index, title ->
                item(key = title) {
                    TvMediaCard(
                        title = title,
                        subtitle = (2015 + index).toString(),
                        imageUrl = null,
                        onClick = {},
                        fillWidth = true,
                        selected = index == 1,
                        watched = index == 2,
                    )
                }
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
