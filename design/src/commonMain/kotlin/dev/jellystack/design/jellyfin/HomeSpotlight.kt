package dev.jellystack.design.jellyfin

import dev.jellystack.core.jellyfin.SpotlightCandidate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.spotlight_position
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal fun reconcileSpotlightSelection(
    selectedId: String?,
    candidateIds: List<String>,
): String? = selectedId?.takeIf(candidateIds::contains) ?: candidateIds.firstOrNull()

internal suspend fun runSpotlightAutoAdvanceCycle(
    cycleDurationMillis: Long,
    onProgress: (Float) -> Unit,
    progressDelay: suspend (Long) -> Unit = { delay(it) },
    deadlineDelay: suspend (Long) -> Unit = { delay(it) },
) {
    val durationMillis = cycleDurationMillis.coerceAtLeast(1L)
    onProgress(0f)
    coroutineScope {
        val progressUpdater =
            launch {
                var elapsedMillis = 0L
                while (elapsedMillis < durationMillis) {
                    val tickMillis = minOf(32L, durationMillis - elapsedMillis)
                    progressDelay(tickMillis)
                    elapsedMillis += tickMillis
                    onProgress(elapsedMillis.toFloat() / durationMillis.toFloat())
                }
            }
        try {
            deadlineDelay(durationMillis)
        } finally {
            progressUpdater.cancel()
        }
    }
    onProgress(1f)
}

@Composable
internal fun HomeSpotlight(
    candidates: List<SpotlightCandidate>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    autoAdvanceEnabled: Boolean = true,
    autoAdvanceIntervalMillis: Long = 6_000L,
    content: @Composable (SpotlightCandidate, Int, Int) -> Unit,
) {
    val candidateIds = candidates.map { candidate -> candidate.displayItem.id }
    val selected = reconcileSpotlightSelection(selectedId, candidateIds)
    val latestOnSelected = rememberUpdatedState(onSelected)
    if (candidates.isEmpty()) {
        LaunchedEffect(candidateIds, selectedId) {
            if (selectedId != null) {
                latestOnSelected.value(null)
            }
        }
        return
    }

    val initialPage = candidateIds.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { candidates.size }
    val autoAdvanceProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pagerState, candidateIds, selected, selectedId) {
        if (selected != selectedId) {
            latestOnSelected.value(selected)
            return@LaunchedEffect
        }
        val targetPage = candidateIds.indexOf(selected).coerceAtLeast(0)
        if (targetPage != pagerState.settledPage) {
            pagerState.animateScrollToPage(targetPage)
        }
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val settledId = candidateIds.getOrNull(page)
                if (settledId != selectedId) {
                    latestOnSelected.value(settledId)
                }
            }
    }
    LaunchedEffect(pagerState, candidateIds, autoAdvanceEnabled, autoAdvanceIntervalMillis) {
        if (!autoAdvanceEnabled || candidateIds.size < 2) {
            autoAdvanceProgress.floatValue = 0f
            return@LaunchedEffect
        }
        val cycleDurationMillis = autoAdvanceIntervalMillis.coerceAtLeast(1L)
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collectLatest { settledPage ->
                runSpotlightAutoAdvanceCycle(
                    cycleDurationMillis = cycleDurationMillis,
                    onProgress = { autoAdvanceProgress.floatValue = it },
                )
                if (!pagerState.isScrollInProgress && pagerState.settledPage == settledPage) {
                    pagerState.animateScrollToPage((settledPage + 1) % candidateIds.size)
                }
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            key = { page -> candidateIds[page] },
            modifier = Modifier.fillMaxWidth().testTag(SpotlightTestTags.PAGER),
        ) { page ->
            Box(modifier = Modifier.fillMaxWidth()) {
                content(candidates[page], page, candidates.size)
                if (autoAdvanceEnabled && candidateIds.size > 1 && page == pagerState.settledPage) {
                    SpotlightAutoCycleProgress(
                        progress = autoAdvanceProgress,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        SpotlightIndicators(
            candidateIds = candidateIds,
            selectedId = selected,
            onSelected = { candidateId ->
                if (candidateId != selected) {
                    latestOnSelected.value(candidateId)
                }
            },
        )
    }
}

@Composable
private fun SpotlightAutoCycleProgress(
    progress: FloatState,
    modifier: Modifier = Modifier,
) {
    val boundedProgress = progress.floatValue.coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.24f))
                .testTag(SpotlightTestTags.AUTO_CYCLE_PROGRESS)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(boundedProgress, 0f..1f)
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(boundedProgress)
                    .fillMaxHeight()
                    .background(Color.White),
        )
    }
}

@Composable
private fun SpotlightIndicators(
    candidateIds: List<String>,
    selectedId: String?,
    onSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = candidateIds,
            key = { _, candidateId -> candidateId },
        ) { index, candidateId ->
            val isSelected = candidateId == selectedId
            val positionLabel =
                stringResource(
                    Res.string.spotlight_position,
                    index + 1,
                    candidateIds.size,
                )
            IconButton(
                onClick = { onSelected(candidateId) },
                modifier =
                    Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = positionLabel
                            selected = isSelected
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(if (isSelected) 32.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
                                },
                            ),
                )
            }
        }
    }
}
