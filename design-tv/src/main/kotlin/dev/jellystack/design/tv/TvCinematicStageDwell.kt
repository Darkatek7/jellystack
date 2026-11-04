package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** Focus must rest this long on a card before the stage text changes to it; a held D-pad key animates no text. */
internal const val TV_CINEMATIC_STAGE_DWELL_MS = 200L

/**
 * The card whose text the stage shows. Focus itself is not delayed; the stage follows once focus rests on a card for
 * [TV_CINEMATIC_STAGE_DWELL_MS], so passing over cards starts no text crossfade. The first card, a change of the same
 * card (live watch state) and the return to no card are staged at once.
 */
@Composable
internal fun rememberTvStagedCard(focused: TvCinematicCard?): TvCinematicCard? {
    var staged by remember { mutableStateOf(focused) }
    val latest by rememberUpdatedState(focused)
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.followStagedCard(TV_CINEMATIC_STAGE_DWELL_MS, stagedId = { staged?.id }) { staged = it }
    }
    return staged
}

internal suspend fun Flow<TvCinematicCard?>.followStagedCard(
    dwellMs: Long,
    stagedId: () -> String?,
    onStaged: (TvCinematicCard?) -> Unit,
) = collectLatest { target ->
    val shown = stagedId()
    if (target != null && shown != null && shown != target.id) delay(dwellMs)
    onStaged(target)
}
