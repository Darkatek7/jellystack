package dev.jellystack.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinItemRecord
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.MediaProviderIds
import dev.jellystack.core.profile.ProfileConnectionBinding
import dev.jellystack.core.profile.SavedMediaRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightProfileStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: JellystackDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JellystackDatabase.Schema.create(driver)
        database = JellystackDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun profilesBindingsAndSavedMediaRoundTripWithoutCrossProfileLeakage() =
        runTest {
            val store = SqlDelightProfileStore(database.householdProfilesQueries, StandardTestDispatcher(testScheduler))
            val first = profile("first", "First", 1L)
            val second = profile("second", "Second", 2L)
            store.upsertProfile(first)
            store.upsertProfile(second)
            store.upsertBinding(ProfileConnectionBinding("first", "jellyfin-a", "seerr-a"))
            store.upsertBinding(ProfileConnectionBinding("second", "jellyfin-b"))

            store.upsertSavedMedia(saved("first", "Original", 1L))
            store.upsertSavedMedia(saved("first", "Updated", 2L))
            store.upsertSavedMedia(saved("second", "Second user's copy", 3L))

            assertEquals(listOf("second", "first"), store.observeProfiles().first().map { it.id })
            assertEquals(ProfileConnectionBinding("first", "jellyfin-a", "seerr-a"), store.getBinding("first"))
            assertEquals(listOf("Updated"), store.listSavedMedia("first").map { it.title })
            assertEquals(listOf("Second user's copy"), store.listSavedMedia("second").map { it.title })

            store.deleteProfile("first")

            assertNull(store.getProfile("first"))
            assertNull(store.getBinding("first"))
            assertEquals(emptyList(), store.listSavedMedia("first"))
            assertEquals(1, store.listSavedMedia("second").size)
        }

    @Test
    fun jellyfinProviderIdsRoundTripThroughTheCanonicalItemCache() =
        runTest {
            val store = SqlDelightJellyfinItemStore(database.jellyfinItemsQueries)
            val record = jellyfinRecord()

            store.upsert(listOf(record))

            assertEquals(record.providerIds, store.get(record.id)?.providerIds)
        }

    private fun profile(
        id: String,
        name: String,
        timestamp: Long,
    ) = HouseholdProfile(
        id = id,
        displayName = name,
        avatarSeed = id,
        createdAt = Instant.fromEpochMilliseconds(timestamp),
        updatedAt = Instant.fromEpochMilliseconds(timestamp),
        lastActiveAt = Instant.fromEpochMilliseconds(timestamp),
    )

    private fun saved(
        profileId: String,
        title: String,
        timestamp: Long,
    ) = SavedMediaRecord(
        profileId = profileId,
        mediaType = "movie",
        providerIds = MediaProviderIds(tmdbId = "603", sourceLocalId = "local"),
        title = title,
        posterPath = "/poster.jpg",
        backdropPath = "/backdrop.jpg",
        createdAt = Instant.fromEpochMilliseconds(1L),
        updatedAt = Instant.fromEpochMilliseconds(timestamp),
    )

    private fun jellyfinRecord() =
        JellyfinItemRecord(
            id = "jellyfin-local",
            serverId = "server",
            libraryId = "library",
            name = "Movie",
            sortName = null,
            overview = null,
            type = "Movie",
            mediaType = "Video",
            locationType = null,
            taglines = emptyList(),
            parentId = null,
            primaryImageTag = null,
            thumbImageTag = null,
            backdropImageTag = null,
            seriesId = null,
            seriesPrimaryImageTag = null,
            seriesThumbImageTag = null,
            seriesBackdropImageTag = null,
            parentLogoImageTag = null,
            runTimeTicks = null,
            positionTicks = null,
            playedPercentage = null,
            productionYear = 1999L,
            premiereDate = null,
            communityRating = null,
            officialRating = null,
            indexNumber = null,
            parentIndexNumber = null,
            seriesName = null,
            seasonId = null,
            episodeTitle = null,
            lastPlayed = null,
            updatedAt = Instant.fromEpochMilliseconds(1L),
            providerIds =
                MediaProviderIds(
                    tmdbId = "603",
                    tvdbId = "123",
                    sourceLocalId = "jellyfin-local",
                ),
        )
}
