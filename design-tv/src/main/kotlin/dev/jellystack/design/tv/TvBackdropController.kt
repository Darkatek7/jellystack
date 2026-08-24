package dev.jellystack.design.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val TV_BACKDROP_DWELL_MILLIS = 120L
internal const val TV_BACKDROP_CROSSFADE_MILLIS = 240

internal fun interface TvBackdropImageLoader {
    suspend fun load(url: String): Boolean
}

internal class TvBackdropController(
    private val scope: CoroutineScope,
    private val imageLoader: TvBackdropImageLoader,
    private val reducedMotion: () -> Boolean = { false },
) {
    private val mutableState = MutableStateFlow(TvCinematicBackdrop())
    val state: StateFlow<TvCinematicBackdrop> = mutableState.asStateFlow()

    private var focusJob: Job? = null

    fun focus(card: TvCinematicCard) {
        focusJob?.cancel()
        val requestedUrl = card.backdropUrl ?: card.artworkUrl ?: return
        if (requestedUrl == mutableState.value.url) return
        focusJob =
            scope.launch {
                delay(TV_BACKDROP_DWELL_MILLIS)
                if (!imageLoader.load(requestedUrl)) return@launch
                val current = mutableState.value
                if (current.url == requestedUrl) return@launch
                val transition = if (current.url != null && !reducedMotion()) TV_BACKDROP_CROSSFADE_MILLIS else 0
                mutableState.value =
                    TvCinematicBackdrop(
                        url = requestedUrl,
                        previousUrl = current.url.takeIf { transition > 0 },
                        transitionMillis = transition,
                        revision = current.revision + 1,
                    )
            }
    }

    fun cancelPending() {
        focusJob?.cancel()
        focusJob = null
    }
}
