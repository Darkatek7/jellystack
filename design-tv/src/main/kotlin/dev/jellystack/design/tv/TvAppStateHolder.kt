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
    val environmentIdentity: TvAuthenticatedEnvironmentIdentity?,
    val isForeground: Boolean,
)

@Serializable
internal data class TvAuthenticatedEnvironmentIdentity(
    val serverConnectionId: String,
    val principalId: String,
)

@Serializable
internal data class TvAppStateSnapshot(
    val backStack: List<TvRoute> = listOf(TvRoute.Home),
    val railExpanded: Boolean = false,
    val activeProfileGeneration: Long = 0L,
    val environmentIdentity: TvAuthenticatedEnvironmentIdentity? = null,
    val isForeground: Boolean = true,
    val focusSnapshots: Map<String, TvFocusSnapshot> = emptyMap(),
)

@Serializable
private data class TvAppStateEnvelope(
    val version: Int,
    val snapshot: TvAppStateSnapshot,
)

private const val TV_APP_STATE_VERSION = 1

internal object TvAppStatePersistence {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: TvAppStateSnapshot): String =
        json.encodeToString(
            TvAppStateEnvelope.serializer(),
            TvAppStateEnvelope(version = TV_APP_STATE_VERSION, snapshot = snapshot),
        )

    fun decode(raw: String): TvAppStateSnapshot? =
        runCatching { json.decodeFromString(TvAppStateEnvelope.serializer(), raw).snapshot }
            .getOrNull()
            ?.valid()
            ?: runCatching { json.decodeFromString(TvAppStateSnapshot.serializer(), raw) }.getOrNull()?.valid()
            ?: TvRouteBackStack
                .decode(raw)
                ?.takeIf(List<TvRoute>::isNotEmpty)
                ?.let { TvAppStateSnapshot(backStack = it) }

    private fun TvAppStateSnapshot.valid(): TvAppStateSnapshot? = takeIf { it.backStack.isNotEmpty() }
}

/** Lifecycle-agnostic owner for navigation, rail, profile generation, and semantic focus state. */
@Stable
@Suppress("TooManyFunctions") // This is the single state-machine action surface for the TV root.
internal class TvAppStateHolder(
    initialSnapshot: TvAppStateSnapshot = TvAppStateSnapshot(),
) {
    private val detailSourceItems = mutableMapOf<String, JellyfinItem>()

    val focusMemory = TvFocusMemory(initialSnapshot.focusSnapshots)

    /**
     * Told the focus route key of a page that left the back stack, after its [focusMemory] entry is gone, so the owner
     * of the focus coordinator can drop what that remembers too.
     */
    var onRouteForgotten: (String) -> Unit = {}

    var state by
        mutableStateOf(
            uiState(
                backStack = initialSnapshot.backStack.withoutPlayerRoute(),
                railExpanded = initialSnapshot.railExpanded,
                activeProfileGeneration = initialSnapshot.activeProfileGeneration,
                environmentIdentity = initialSnapshot.environmentIdentity,
                isForeground = initialSnapshot.isForeground,
            ),
        )
        private set

    fun push(route: TvRoute) {
        if (state.currentRoute == route) return
        val nextBackStack =
            if (state.currentRoute.isDetailRoute() && route.isDetailRoute()) {
                state.backStack.dropLast(1) + route
            } else {
                state.backStack + route
            }
        replaceBackStack(nextBackStack)
    }

    fun selectTopLevel(route: TvRoute) {
        val nextBackStack = listOf(route)
        if (state.backStack == nextBackStack) return
        replaceBackStack(nextBackStack)
    }

    fun popRoute(): Boolean {
        if (state.backStack.size <= 1) return false
        replaceBackStack(state.backStack.dropLast(1))
        return true
    }

    /**
     * Pages opened from another screen start on their default focus every time they are opened. Going back to one
     * from a page opened on top of it still restores focus, because it never left the back stack.
     */
    private fun replaceBackStack(nextBackStack: List<TvRoute>) {
        val previous = state.backStack
        state = state.withBackStack(nextBackStack)
        val kept = state.backStack.mapTo(mutableSetOf()) { it.focusRouteKey() }
        previous
            .filter(TvRoute::forgetsFocusWhenClosed)
            .map { it.focusRouteKey() }
            .distinct()
            .filterNot(kept::contains)
            .forEach { routeKey ->
                focusMemory.forget(routeKey)
                onRouteForgotten(routeKey)
            }
    }

    /** Leaves the player route after playback stopped outside it; a no-op anywhere else. */
    fun popPlayerRoute(): Boolean = state.currentRoute == TvRoute.Player && popRoute()

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
                environmentIdentity = state.environmentIdentity,
                isForeground = state.isForeground,
            )
    }

    /** Validates restored state against the authenticated server connection and principal. */
    fun activateEnvironment(identity: TvAuthenticatedEnvironmentIdentity): Boolean {
        if (state.environmentIdentity == identity) return false
        focusMemory.clear()
        detailSourceItems.clear()
        val firstIdentityBinding = state.environmentIdentity == null
        state =
            uiState(
                backStack = if (firstIdentityBinding) state.backStack else listOf(TvRoute.Home),
                railExpanded = false,
                activeProfileGeneration = state.activeProfileGeneration + 1L,
                environmentIdentity = identity,
                isForeground = state.isForeground,
            )
        return true
    }

    fun deactivateEnvironment(): Boolean {
        val alreadyClean =
            state.environmentIdentity == null &&
                state.backStack == listOf(TvRoute.Home) &&
                !state.railExpanded &&
                focusMemory.snapshot().isEmpty() &&
                detailSourceItems.isEmpty()
        if (alreadyClean) return false
        focusMemory.clear()
        detailSourceItems.clear()
        state =
            uiState(
                backStack = listOf(TvRoute.Home),
                railExpanded = false,
                activeProfileGeneration = state.activeProfileGeneration + 1L,
                environmentIdentity = null,
                isForeground = state.isForeground,
            )
        return true
    }

    fun rememberDetailSource(item: JellyfinItem) {
        detailSourceItems[item.id] = item
    }

    fun detailSource(itemId: String): JellyfinItem? = detailSourceItems[itemId]

    fun snapshot(): TvAppStateSnapshot =
        TvAppStateSnapshot(
            backStack = state.backStack.withoutPlayerRoute(),
            railExpanded = state.railExpanded,
            activeProfileGeneration = state.activeProfileGeneration,
            environmentIdentity = state.environmentIdentity,
            isForeground = state.isForeground,
            focusSnapshots = focusMemory.snapshot(),
        )

    private fun TvAppUiState.withBackStack(backStack: List<TvRoute>): TvAppUiState =
        uiState(
            backStack = backStack,
            railExpanded = false,
            activeProfileGeneration = activeProfileGeneration,
            environmentIdentity = environmentIdentity,
            isForeground = isForeground,
        )

    private companion object {
        fun uiState(
            backStack: List<TvRoute>,
            railExpanded: Boolean,
            activeProfileGeneration: Long,
            environmentIdentity: TvAuthenticatedEnvironmentIdentity?,
            isForeground: Boolean,
        ): TvAppUiState {
            val snapshot = backStack.ifEmpty { listOf(TvRoute.Home) }.toList()
            return TvAppUiState(
                backStack = snapshot,
                currentRoute = snapshot.last(),
                railExpanded = railExpanded,
                activeProfileGeneration = activeProfileGeneration,
                environmentIdentity = environmentIdentity,
                isForeground = isForeground,
            )
        }
    }
}

/** Detail, person, and collection pages are opened for one title; the rail destinations and the library keep theirs. */
internal fun TvRoute.forgetsFocusWhenClosed(): Boolean =
    this is TvRoute.JellyfinDetail ||
        this is TvRoute.SeerrDetail ||
        this is TvRoute.Person ||
        this is TvRoute.Collection

private fun TvRoute.isDetailRoute(): Boolean = this is TvRoute.JellyfinDetail || this is TvRoute.SeerrDetail

/**
 * The player route is never saved or restored: playback does not survive the process or a stop, so a
 * restored player route would be an empty screen. An empty result falls back to Home.
 */
private fun List<TvRoute>.withoutPlayerRoute(): List<TvRoute> {
    val saved = filterNot { it == TvRoute.Player }
    return saved.ifEmpty { listOf(TvRoute.Home) }
}
