@file:Suppress("CyclomaticComplexMethod", "MaxLineLength", "ReturnCount", "TooManyFunctions")

package dev.jellystack.design.tv

import androidx.navigation3.runtime.NavKey
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
sealed interface TvRoute : NavKey {
    @Serializable
    data object Home : TvRoute

    @Serializable
    data class Library(
        val libraryId: String? = null,
        val title: String? = null,
    ) : TvRoute

    @Serializable
    data object Search : TvRoute

    @Serializable
    data object Discover : TvRoute

    @Serializable
    data class Settings(
        val section: String? = null,
    ) : TvRoute

    @Serializable
    data class JellyfinDetail(
        val itemId: String,
    ) : TvRoute

    @Serializable
    data class SeerrDetail(
        val tmdbId: Int,
        val mediaType: JellyseerrMediaType,
        val title: String,
        val overview: String? = null,
        val posterPath: String? = null,
        val backdropPath: String? = null,
        val releaseYear: String? = null,
        val tvdbId: Int? = null,
    ) : TvRoute

    @Serializable
    data object Player : TvRoute
}

internal fun TvRoute.focusRouteKey(libraryPath: List<String> = emptyList()): String =
    when (this) {
        TvRoute.Home -> "home"
        is TvRoute.Library ->
            if (libraryId == null) {
                "library:list"
            } else {
                buildString {
                    append("library:")
                    append(libraryId)
                    if (libraryPath.isNotEmpty()) {
                        append("/path:")
                        append(libraryPath.joinToString("/"))
                    }
                }
            }
        TvRoute.Search -> "search"
        TvRoute.Discover -> "discover"
        is TvRoute.Settings -> "settings:${TvSettingsCategory.fromRouteSection(section)?.routeKey ?: "root"}"
        is TvRoute.JellyfinDetail -> "jellyfin-detail:$itemId"
        is TvRoute.SeerrDetail -> "seerr-detail:${mediaType.name}:$tmdbId"
        TvRoute.Player -> "player"
    }

internal fun tvSettingsServerActionTargetId(
    serverIdentity: String,
    actionKey: String,
): String = "settings:server:$serverIdentity:action:$actionKey"

internal const val TV_FOCUS_RAIL_ROUTE = "navigation-rail"
internal const val TV_HOME_HERO_TARGET = "home:hero"
internal const val TV_HOME_PRIMARY_TARGET = "home:hero:primary"
internal const val TV_HOME_DETAILS_TARGET = "home:hero:details"
internal const val TV_HOME_RETRY_TARGET = "home:retry"
internal const val TV_SEARCH_QUERY_TARGET = "search:query"
internal const val TV_SEARCH_JELLYFIN_RETRY_TARGET = "search:jellyfin:retry"
internal const val TV_SEARCH_SEERR_RETRY_TARGET = "search:seerr:retry"
internal const val TV_DISCOVER_CONNECT_TARGET = "discover:connect"
internal const val TV_DISCOVER_EMPTY_TARGET = "discover:empty"
internal const val TV_DISCOVER_LOADING_TARGET = "discover:loading"
internal const val TV_DISCOVER_RETRY_TARGET = "discover:retry"
internal const val TV_LIBRARY_LOADING_TARGET = "library:loading"
internal const val TV_LIBRARY_RETRY_TARGET = "library:retry"
internal const val TV_LIBRARY_EMPTY_TARGET = "library:empty"

internal fun tvHomeCardTargetId(
    rowId: String,
    itemId: String,
): String = "home:row:$rowId:item:$itemId"

internal fun tvLibraryTargetId(
    itemId: String,
    sectionId: String = "items",
): String = "library:$sectionId:item:$itemId"

internal fun tvSearchSourceTargetId(source: String): String = "search:source:$source"

internal fun tvSearchResultTargetId(
    source: String,
    itemId: String,
): String = "search:$source:item:$itemId"

internal fun tvDiscoverItemTargetId(
    railId: String,
    itemId: String,
): String = "discover:rail:$railId:item:$itemId"

internal fun tvSettingsControlTargetId(controlKey: String): String = "settings:control:$controlKey"

internal fun tvRailTargetId(route: TvRoute): String =
    "rail:" +
        when (route) {
            TvRoute.Home -> TvRoute.Home.focusRouteKey()
            is TvRoute.Library -> TvRoute.Library().focusRouteKey()
            TvRoute.Search -> TvRoute.Search.focusRouteKey()
            TvRoute.Discover -> TvRoute.Discover.focusRouteKey()
            is TvRoute.Settings -> TvRoute.Settings().focusRouteKey()
            else -> route.focusRouteKey()
        }

internal enum class TvBackAction { POP_LIBRARY_PATH, POP_ROUTE, CLOSE_RAIL, SYSTEM_EXIT }

/**
 * Persists the navigation back stack across process death. TV systems kill background apps
 * aggressively; without this the app restarts on Home and loses deep navigation.
 */
internal object TvRouteBackStack {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TvRoute.serializer())

    fun encode(routes: List<TvRoute>): String = json.encodeToString(serializer, routes)

    /** Returns null (instead of throwing) on corrupt or unknown saved state. */
    fun decode(raw: String): List<TvRoute>? = runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
}

internal fun tvBackAction(
    currentRoute: TvRoute,
    backStackSize: Int,
    libraryPathDepth: Int,
    railVisible: Boolean,
    selectedLibraryId: String?,
): TvBackAction =
    when {
        currentRoute is TvRoute.Library &&
            currentRoute.libraryId != null &&
            currentRoute.libraryId == selectedLibraryId &&
            libraryPathDepth > 0 -> TvBackAction.POP_LIBRARY_PATH
        backStackSize > 1 -> TvBackAction.POP_ROUTE
        railVisible -> TvBackAction.CLOSE_RAIL
        else -> TvBackAction.SYSTEM_EXIT
    }

@Serializable
internal data class TvFocusSnapshot(
    val anchor: TvFocusAnchor,
    val verticalIndex: Int,
    val horizontalIndex: Int,
    val horizontalCenter: Float,
) {
    val rowKey: String?
        get() = anchor.sectionId

    val itemId: String?
        get() = anchor.itemId
}

/**
 * Keeps a process-restored semantic anchor pending while route content is still being discovered.
 * The session deliberately owns the original snapshot so transient loading/status nodes cannot
 * replace it before a real target or an authoritative fallback exists.
 */
internal class TvSemanticFocusRestorationSession(
    private val snapshot: TvFocusSnapshot?,
    val interactionRevision: Long,
) {
    var isPending: Boolean = true
        private set

    fun preferredTargetId(
        availableTargets: List<TvFocusTarget>,
        contentAuthoritativelyLoaded: Boolean,
    ): String? {
        if (!isPending) return null
        val actionable = availableTargets.filter(TvFocusTarget::actionable)
        val remembered = snapshot ?: return actionable.firstOrNull()?.targetId
        actionable.firstOrNull { it.anchor == remembered.anchor }?.let { return it.targetId }
        if (!contentAuthoritativelyLoaded) return null
        val sameSection = actionable.filter { it.anchor.sectionId == remembered.anchor.sectionId }
        return (sameSection.ifEmpty { actionable })
            .minWithOrNull(
                compareBy<TvFocusTarget> { kotlin.math.abs(it.horizontalCenter - remembered.horizontalCenter) }
                    .thenBy { kotlin.math.abs(it.horizontalIndex - remembered.horizontalIndex) },
            )?.targetId
    }

    fun complete() {
        isPending = false
    }

    fun cancelAfterInteraction(interactionRevision: Long) {
        if (interactionRevision != this.interactionRevision) isPending = false
    }
}

@androidx.compose.runtime.Immutable
internal data class TvFocusTarget(
    val targetId: String,
    val anchor: TvFocusAnchor,
    val horizontalCenter: Float,
    val horizontalIndex: Int,
    val actionable: Boolean = true,
)

/** Builds semantic browse anchors from the stable requester IDs already used by production UI. */
internal fun tvFocusTarget(
    targetId: String,
    horizontalCenter: Float = 0f,
    horizontalIndex: Int = 0,
    actionable: Boolean = true,
): TvFocusTarget {
    val (anchor, statusTarget) =
        when {
            targetId == TV_HOME_HERO_TARGET -> TvFocusAnchor("hero", null, TvFocusDestination.HERO) to false
            targetId == TV_HOME_PRIMARY_TARGET || targetId == TV_HOME_DETAILS_TARGET ->
                TvFocusAnchor("hero-actions", targetId.substringAfterLast(':'), TvFocusDestination.PRIMARY_ACTION) to false
            targetId.startsWith("home:row:") && ":item:" in targetId -> {
                val value = targetId.removePrefix("home:row:")
                TvFocusAnchor(value.substringBefore(":item:"), value.substringAfter(":item:"), TvFocusDestination.SECTION_ITEM) to false
            }
            targetId.startsWith("library:") && ":item:" in targetId -> {
                val value = targetId.removePrefix("library:")
                TvFocusAnchor(value.substringBefore(":item:"), value.substringAfter(":item:"), TvFocusDestination.SECTION_ITEM) to false
            }
            targetId == TV_LIBRARY_LOADING_TARGET -> TvFocusAnchor("status", "loading", TvFocusDestination.BODY) to true
            targetId == TV_LIBRARY_EMPTY_TARGET -> TvFocusAnchor("status", "empty", TvFocusDestination.BODY) to true
            targetId == TV_LIBRARY_RETRY_TARGET -> TvFocusAnchor("status", "retry", TvFocusDestination.PRIMARY_ACTION) to false
            targetId == TV_SEARCH_QUERY_TARGET -> TvFocusAnchor("query", null, TvFocusDestination.PRIMARY_ACTION) to false
            targetId.startsWith("search:source:") ->
                TvFocusAnchor("sources", targetId.removePrefix("search:source:"), TvFocusDestination.SECTION_ITEM) to false
            targetId.startsWith("search:") && ":item:" in targetId -> {
                val value = targetId.removePrefix("search:")
                TvFocusAnchor(value.substringBefore(":item:"), value.substringAfter(":item:"), TvFocusDestination.SECTION_ITEM) to false
            }
            targetId.startsWith("discover:rail:") && ":item:" in targetId -> {
                val value = targetId.removePrefix("discover:rail:")
                TvFocusAnchor(value.substringBefore(":item:"), value.substringAfter(":item:"), TvFocusDestination.SECTION_ITEM) to false
            }
            targetId == TV_DISCOVER_LOADING_TARGET -> TvFocusAnchor("status", "loading", TvFocusDestination.BODY) to true
            targetId == TV_DISCOVER_EMPTY_TARGET -> TvFocusAnchor("status", "empty", TvFocusDestination.BODY) to true
            targetId == TV_DISCOVER_CONNECT_TARGET || targetId == TV_DISCOVER_RETRY_TARGET ->
                TvFocusAnchor("status", targetId.substringAfterLast(':'), TvFocusDestination.PRIMARY_ACTION) to false
            targetId.startsWith("settings:") ->
                TvFocusAnchor("settings", targetId.removePrefix("settings:"), TvFocusDestination.PRIMARY_ACTION) to false
            else -> TvFocusAnchor(targetId.substringBefore(':'), targetId, TvFocusDestination.SECTION_ITEM) to false
        }
    return TvFocusTarget(
        targetId = targetId,
        anchor = anchor,
        horizontalCenter = horizontalCenter.takeIf(Float::isFinite) ?: horizontalIndex.toFloat(),
        horizontalIndex = horizontalIndex.coerceAtLeast(0),
        actionable = actionable && !statusTarget,
    )
}

/** Keeps route-local focus stable while asynchronous rows refresh around it. */
internal class TvFocusMemory(
    initialSnapshots: Map<String, TvFocusSnapshot> = emptyMap(),
) {
    private val snapshots = initialSnapshots.toMutableMap()

    fun remember(
        routeKey: String,
        rowKey: String?,
        itemId: String?,
        verticalIndex: Int = 0,
        horizontalIndex: Int = 0,
    ) = remember(
        routeKey = routeKey,
        anchor = TvFocusAnchor(rowKey, itemId, TvFocusDestination.SECTION_ITEM),
        verticalIndex = verticalIndex,
        horizontalIndex = horizontalIndex,
        horizontalCenter = horizontalIndex.toFloat(),
    )

    fun remember(
        routeKey: String,
        anchor: TvFocusAnchor,
        horizontalCenter: Float,
        verticalIndex: Int = 0,
        horizontalIndex: Int = 0,
    ) {
        val safeHorizontalIndex = horizontalIndex.coerceAtLeast(0)
        snapshots[routeKey] =
            TvFocusSnapshot(
                anchor = anchor,
                verticalIndex = verticalIndex.coerceAtLeast(0),
                horizontalIndex = safeHorizontalIndex,
                horizontalCenter = horizontalCenter.takeIf(Float::isFinite) ?: safeHorizontalIndex.toFloat(),
            )
    }

    fun restore(routeKey: String): TvFocusSnapshot? = snapshots[routeKey]

    internal fun snapshot(): Map<String, TvFocusSnapshot> = snapshots.toMap()

    internal fun clear() = snapshots.clear()

    internal fun resolve(
        routeKey: String,
        availableTargets: List<TvFocusTarget>,
    ): TvFocusTarget? {
        val actionableTargets = availableTargets.filter(TvFocusTarget::actionable)
        if (actionableTargets.isEmpty()) return null
        val snapshot = snapshots[routeKey] ?: return actionableTargets.first()
        actionableTargets.firstOrNull { it.anchor == snapshot.anchor }?.let { return it }
        val sameSection = actionableTargets.filter { it.anchor.sectionId == snapshot.anchor.sectionId }
        if (sameSection.isEmpty()) return actionableTargets.first()
        return sameSection
            .minWithOrNull(
                compareBy<TvFocusTarget> { kotlin.math.abs(it.horizontalCenter - snapshot.horizontalCenter) }
                    .thenBy { kotlin.math.abs(it.horizontalIndex - snapshot.horizontalIndex) },
            )
    }

    fun resolveItem(
        routeKey: String,
        availableIds: List<String>,
    ): String? =
        if (availableIds.isEmpty()) {
            null
        } else {
            val sectionId = snapshots[routeKey]?.anchor?.sectionId
            resolve(
                routeKey,
                availableIds.mapIndexed { index, id ->
                    TvFocusTarget(
                        targetId = id,
                        anchor = TvFocusAnchor(sectionId, id, TvFocusDestination.SECTION_ITEM),
                        horizontalCenter = index.toFloat(),
                        horizontalIndex = index,
                    )
                },
            )?.anchor?.itemId
        }
}

/** Executes the one app-level Back hierarchy used by both Navigation3 and the root fallback handler. */
internal class TvAppBackDispatcher(
    private val holder: TvAppStateHolder,
    private val libraryPathDepth: () -> Int,
    private val selectedLibraryId: () -> String?,
    private val popLibraryPath: () -> Unit,
    private val cancelFocusRestoration: () -> Unit,
) {
    val action: TvBackAction
        get() =
            tvBackAction(
                currentRoute = holder.state.currentRoute,
                backStackSize = holder.state.backStack.size,
                libraryPathDepth = libraryPathDepth(),
                railVisible = holder.state.railExpanded,
                selectedLibraryId = selectedLibraryId(),
            )

    /** Navigation3 owns Back whenever it has a previous entry; the root only handles non-route layers. */
    val rootHandlerEnabled: Boolean
        get() = holder.state.backStack.size == 1 && action != TvBackAction.SYSTEM_EXIT

    fun dispatch(): Boolean {
        val consumed =
            when (action) {
                TvBackAction.POP_LIBRARY_PATH -> {
                    popLibraryPath()
                    holder.closeRail()
                    true
                }
                TvBackAction.POP_ROUTE -> holder.popRoute()
                TvBackAction.CLOSE_RAIL -> {
                    holder.closeRail()
                    true
                }
                TvBackAction.SYSTEM_EXIT -> false
            }
        if (consumed) cancelFocusRestoration()
        return consumed
    }
}
