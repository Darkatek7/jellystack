package dev.jellystack.design.tv

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
internal const val TV_DISCOVER_CONNECT_TARGET = "discover:connect"
internal const val TV_DISCOVER_EMPTY_TARGET = "discover:empty"
internal const val TV_LIBRARY_LOADING_TARGET = "library:loading"
internal const val TV_LIBRARY_RETRY_TARGET = "library:retry"
internal const val TV_LIBRARY_EMPTY_TARGET = "library:empty"

internal fun tvHomeCardTargetId(
    rowId: String,
    itemId: String,
): String = "home:row:$rowId:item:$itemId"

internal fun tvLibraryTargetId(itemId: String): String = "library:item:$itemId"

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

internal enum class TvBackAction { POP_LIBRARY_PATH, POP_ROUTE, CLOSE_RAIL, OPEN_RAIL }

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

internal val TvRouteBackStackSaver: Saver<SnapshotStateList<TvRoute>, String> =
    Saver(
        save = { routes -> TvRouteBackStack.encode(routes) },
        restore = { raw -> TvRouteBackStack.decode(raw)?.toMutableStateList() },
    )

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
        else -> TvBackAction.OPEN_RAIL
    }

data class TvFocusSnapshot(
    val rowKey: String?,
    val itemId: String?,
    val verticalIndex: Int,
    val horizontalIndex: Int,
)

/** Keeps route-local focus stable while asynchronous rows refresh around it. */
class TvFocusMemory {
    private val snapshots = mutableMapOf<String, TvFocusSnapshot>()

    fun remember(
        routeKey: String,
        rowKey: String?,
        itemId: String?,
        verticalIndex: Int = 0,
        horizontalIndex: Int = 0,
    ) {
        snapshots[routeKey] =
            TvFocusSnapshot(
                rowKey = rowKey,
                itemId = itemId,
                verticalIndex = verticalIndex.coerceAtLeast(0),
                horizontalIndex = horizontalIndex.coerceAtLeast(0),
            )
    }

    fun restore(routeKey: String): TvFocusSnapshot? = snapshots[routeKey]

    fun resolveItem(
        routeKey: String,
        availableIds: List<String>,
    ): String? =
        if (availableIds.isEmpty()) {
            null
        } else {
            val snapshot = snapshots[routeKey]
            when {
                snapshot == null -> availableIds.first()
                snapshot.itemId in availableIds -> snapshot.itemId
                else -> availableIds.getOrNull(snapshot.horizontalIndex.coerceIn(0, availableIds.lastIndex))
            }
        }
}
