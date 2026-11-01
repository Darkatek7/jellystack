package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** A row card the home stage can show, with the id that tells apart the same title in two rows. */
internal data class TvHomeStageTarget(
    val item: JellyfinItem,
    val presentationId: String?,
)

/**
 * Which row card the home stage shows. Focus itself is not delayed; the stage follows a focused card only once focus
 * has rested on it for the dwell, so passing over cards loads and fades nothing. The first card to take focus while
 * the stage shows the spotlight itself, as when the screen opens or comes back, is staged at once.
 *
 * Both values are snapshot state that only [follow] and the stage's own readers touch, so a focus move does not
 * recompose the screen; only a change of [staged] does.
 */
internal class TvHomeStageDwell {
    private var focused by mutableStateOf<TvHomeStageTarget?>(null)

    /** The card on the stage, or null while the stage shows the spotlight itself, whose Play and Details act on it. */
    var staged by mutableStateOf<TvHomeStageTarget?>(null)
        private set

    fun onCardFocused(
        item: JellyfinItem,
        presentationId: String?,
    ) {
        focused = TvHomeStageTarget(item, presentationId)
    }

    /** A card that loses focus no longer waits to be staged; the stage keeps what it shows. */
    fun onCardBlurred(
        item: JellyfinItem,
        presentationId: String?,
    ) {
        // By id, because live watch state can replace the item while the card has focus.
        if (focused?.item?.id == item.id && focused?.presentationId == presentationId) focused = null
    }

    /** The spotlight has focus, so the stage shows it again at once and nothing is left waiting. */
    fun onSpotlightFocused() {
        focused = null
        staged = null
    }

    /** Stages the focused card once focus rests on it for [dwellMs], and hands it to [onSettled] as it is staged. */
    suspend fun follow(
        dwellMs: Long,
        onSettled: (TvHomeStageTarget) -> Unit,
    ) = snapshotFlow { focused }.followAfterDwell(dwellMs, hasStaged = { staged != null }) { target ->
        staged = target
        onSettled(target)
    }
}

/** Remembers a [TvHomeStageDwell] and keeps it following focus; [onSettled] runs for each card the stage takes on. */
@Composable
internal fun rememberTvHomeStageDwell(onSettled: (TvHomeStageTarget) -> Unit): TvHomeStageDwell {
    val dwell = remember { TvHomeStageDwell() }
    val currentOnSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(dwell) { dwell.follow(TV_HOME_STAGE_DWELL_MS) { currentOnSettled(it) } }
    return dwell
}

/**
 * Calls [onSettled] with the last target of this flow once it has stayed for [dwellMs]; a newer target, or null for
 * none, drops one that still waits. With nothing staged ([hasStaged] false) a target settles at once.
 */
internal suspend fun <T : Any> Flow<T?>.followAfterDwell(
    dwellMs: Long,
    hasStaged: () -> Boolean,
    onSettled: (T) -> Unit,
) = collectLatest { target ->
    if (target == null) return@collectLatest
    if (hasStaged()) delay(dwellMs)
    onSettled(target)
}
