package dev.jellystack.design.admin

import dev.jellystack.core.jellyfin.JellyfinActivityEntry
import dev.jellystack.core.jellyfin.JellyfinAdminCounts
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminDashboardTest {
    @Test
    fun dashboardKeepsExactlyFiveNewestActivities() {
        val activity =
            (1L..7L).map { id ->
                JellyfinActivityEntry(
                    id = id,
                    name = "Activity $id",
                    overview = null,
                    type = null,
                    date = "2026-07-${id.toString().padStart(2, '0')}T12:00:00Z",
                    userId = null,
                    severity = null,
                )
            }

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), latestAdminActivity(activity).map { it.id })
    }

    @Test
    fun libraryTotalIncludesEveryDisplayedMetric() {
        val counts =
            JellyfinAdminCounts(
                movies = 2,
                series = 3,
                episodes = 5,
                albums = 7,
                songs = 11,
                artists = 13,
                books = 17,
            )

        assertEquals(58, counts.totalItems())
    }
}
