package dev.jellystack.design.tv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts remote key presses without being state: reading it in composition would recompose the whole screen on every
 * key. Effects follow [changes] instead.
 */
internal class TvInteractionCounter {
    private val count = MutableStateFlow(0)

    /** Emits the current count, then the new one after each [record]. */
    val changes: Flow<Int> get() = count

    fun record() {
        count.update { it + 1 }
    }
}
