package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Immutable
internal data class TvAppUiState(
    val backStack: List<TvRoute>,
    val currentRoute: TvRoute,
    val railExpanded: Boolean,
    val activeProfileGeneration: Long,
    val isForeground: Boolean,
)

@Serializable
internal data class TvAppStateSnapshot(
    val backStack: List<TvRoute> = listOf(TvRoute.Home),
    val railExpanded: Boolean = false,
    val activeProfileGeneration: Long = 0L,
    val isForeground: Boolean = true,
    val focusSnapshots: Map<String, TvFocusSnapshot> = emptyMap(),
)

internal object TvAppStatePersistence {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: TvAppStateSnapshot): String = json.encodeToString(TvAppStateSnapshot.serializer(), snapshot)

    fun decode(raw: String): TvAppStateSnapshot? =
        runCatching { json.decodeFromString(TvAppStateSnapshot.serializer(), raw) }
            .getOrNull()
            ?.takeIf { it.backStack.isNotEmpty() }
}

/** Lifecycle-agnostic owner for navigation, rail, profile generation, and semantic focus state. */
@Stable
internal class TvAppStateHolder(
    initialSnapshot: TvAppStateSnapshot = TvAppStateSnapshot(),
) {
    private val detailSourceItems = mutableMapOf<String, JellyfinItem>()

    val focusMemory = TvFocusMemory(initialSnapshot.focusSnapshots)

    var state by
        mutableStateOf(
            uiState(
                backStack = initialSnapshot.backStack.ifEmpty { listOf(TvRoute.Home) },
                railExpanded = initialSnapshot.railExpanded,
                activeProfileGeneration = initialSnapshot.activeProfileGeneration,
                isForeground = initialSnapshot.isForeground,
            ),
        )
        private set

    fun push(route: TvRoute) {
        if (state.currentRoute == route) return
        state = state.withBackStack(state.backStack + route)
    }

    fun selectTopLevel(route: TvRoute) {
        val nextBackStack = listOf(route)
        if (state.backStack == nextBackStack) return
        state = state.withBackStack(nextBackStack)
    }

    fun popRoute(): Boolean {
        if (state.backStack.size <= 1) return false
        state = state.withBackStack(state.backStack.dropLast(1))
        return true
    }

    fun openRail() {
        if (state.railExpanded) return
        state = state.copy(railExpanded = true)
    }

    fun closeRail() {
        if (!state.railExpanded) return
        state = state.copy(railExpanded = false)
    }

    fun onForegrounded() {
        if (state.isForeground) return
        state = state.copy(isForeground = true)
    }

    fun onBackgrounded() {
        if (!state.isForeground && !state.railExpanded) return
        state = state.copy(railExpanded = false, isForeground = false)
    }

    fun resetForGeneration(generation: Long) {
        require(generation >= 0L)
        if (state.activeProfileGeneration == generation) return
        focusMemory.clear()
        detailSourceItems.clear()
        state =
            uiState(
                backStack = listOf(TvRoute.Home),
                railExpanded = false,
                activeProfileGeneration = generation,
                isForeground = state.isForeground,
            )
    }

    fun rememberDetailSource(item: JellyfinItem) {
        detailSourceItems[item.id] = item
    }

    fun detailSource(itemId: String): JellyfinItem? = detailSourceItems[itemId]

    fun snapshot(): TvAppStateSnapshot =
        TvAppStateSnapshot(
            backStack = state.backStack.toList(),
            railExpanded = state.railExpanded,
            activeProfileGeneration = state.activeProfileGeneration,
            isForeground = state.isForeground,
            focusSnapshots = focusMemory.snapshot(),
        )

    private fun TvAppUiState.withBackStack(backStack: List<TvRoute>): TvAppUiState =
        uiState(
            backStack = backStack,
            railExpanded = false,
            activeProfileGeneration = activeProfileGeneration,
            isForeground = isForeground,
        )

    private companion object {
        fun uiState(
            backStack: List<TvRoute>,
            railExpanded: Boolean,
            activeProfileGeneration: Long,
            isForeground: Boolean,
        ): TvAppUiState {
            val snapshot = backStack.ifEmpty { listOf(TvRoute.Home) }.toList()
            return TvAppUiState(
                backStack = snapshot,
                currentRoute = snapshot.last(),
                railExpanded = railExpanded,
                activeProfileGeneration = activeProfileGeneration,
                isForeground = isForeground,
            )
        }
    }
}
