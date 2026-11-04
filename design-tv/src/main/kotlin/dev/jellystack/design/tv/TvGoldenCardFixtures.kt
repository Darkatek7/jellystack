@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

/** Two Discover rows whose cards show every Seerr badge: available, partial, requested, and none. */
@Composable
internal fun GoldenDiscoverRows(strings: TvStrings) {
    val focusMemory = remember { TvFocusMemory() }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val rows = listOf("Trending" to goldenSeerrItems(), "Popular shows" to goldenSeerrItems().reversed())
        rows.forEach { (title, items) ->
            TvSeerrRow(
                title = title,
                strings = strings,
                items = items,
                focusMemory = focusMemory,
                routeKey = "discover",
                onItem = {},
                listState = rememberLazyListState(),
                focusTargetId = { id -> "golden-$title-$id" },
            )
        }
    }
}

private fun goldenSeerrItems(): List<JellyseerrSearchItem> =
    listOf(
        goldenSeerrItem(1, "The Last Horizon", "2024", JellyseerrMediaStatus.AVAILABLE),
        goldenSeerrItem(2, "Harbor Lights", "2021", JellyseerrMediaStatus.PARTIALLY_AVAILABLE),
        goldenSeerrItem(3, "A Remarkably Long Title That Cannot Fit", "2023", JellyseerrMediaStatus.PROCESSING),
        goldenSeerrItem(4, "Paper Moon", "1973", null, requested = true),
        goldenSeerrItem(5, "Northern Lights", "2019", null),
    )

private fun goldenSeerrItem(
    id: Int,
    title: String,
    year: String,
    status: JellyseerrMediaStatus?,
    requested: Boolean = false,
) = JellyseerrSearchItem(
    tmdbId = id,
    mediaType = JellyseerrMediaType.MOVIE,
    title = title,
    overview = null,
    releaseYear = year,
    posterPath = null,
    backdropPath = null,
    mediaInfoId = null,
    tvdbId = null,
    availability = JellyseerrMediaAvailability(standard = status, `4k` = null),
    requests = if (requested) listOf(goldenSeerrRequest(id)) else emptyList(),
)

private fun goldenSeerrRequest(id: Int) =
    JellyseerrRequestSummary(
        id = id,
        mediaId = null,
        tmdbId = id,
        tvdbId = null,
        title = null,
        originalTitle = null,
        mediaType = JellyseerrMediaType.MOVIE,
        requestStatus = JellyseerrRequestStatus.PENDING,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        is4k = false,
        canRemoveFromService = false,
        createdAt = null,
        updatedAt = null,
        requestedBy = null,
        profileName = null,
        seasons = emptyList(),
        posterPath = null,
        backdropPath = null,
    )
