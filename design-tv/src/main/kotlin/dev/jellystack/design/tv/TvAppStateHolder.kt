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

    var state by
        mutableStateOf(
            uiState(
                backStack = initialSnapshot.backStack.ifEmpty { listOf(TvRoute.Home) },
                railExpanded = initialSnapshot.railExpanded,
                activeProfileGeneration = initialSnapshot.activeProfileGeneration,
                environmentIdentity = initialSnapshot.environmentIdentity,
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
            backStack = state.backStack.toList(),
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
