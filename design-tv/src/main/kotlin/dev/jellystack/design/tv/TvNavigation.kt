package dev.jellystack.design.tv

import androidx.navigation3.runtime.NavKey
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import kotlinx.serialization.Serializable

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
