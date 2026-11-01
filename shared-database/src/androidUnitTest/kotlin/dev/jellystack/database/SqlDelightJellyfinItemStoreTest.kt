package dev.jellystack.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinItemDetailRecord
import dev.jellystack.core.jellyfin.JellyfinItemRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightJellyfinItemStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightJellyfinItemStore
    private lateinit var detailStore: SqlDelightJellyfinItemDetailStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JellystackDatabase.Schema.create(driver)
        val database = JellystackDatabase(driver)
        store = SqlDelightJellyfinItemStore(database.jellyfinItemsQueries, Dispatchers.Unconfined)
        detailStore = SqlDelightJellyfinItemDetailStore(database.jellyfinItemDetailsQueries, Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun episodesForSeriesLeaveOutTheSeasonsOfThatSeries() =
        runTest {
            store.upsert(
                listOf(
                    seriesChild("season-1", type = "Season", indexNumber = 1L),
                    seriesChild("episode-2", type = "Episode", indexNumber = 2L),
                    seriesChild("episode-1", type = "Episode", indexNumber = 1L),
                ),
            )

            assertEquals(listOf("episode-1", "episode-2"), store.listEpisodesForSeries("server", "series-1").map { it.id })
        }

    @Test
    fun upsertStoresEveryRowOfTheBatch() =
        runTest {
            store.upsert((1..5).map { seriesChild("episode-$it", type = "Episode", indexNumber = it.toLong()) })

            assertEquals(5, store.listEpisodesForSeries("server", "series-1").size)
        }

    @Test
    fun aBatchThatFailsHalfwayLeavesNoPartialRows() =
        runTest {
            val broken = seriesChild("broken", type = "Episode", indexNumber = 2L).copy(taglines = ThrowingTaglines)

            assertFailsWith<IllegalStateException> {
                store.upsert(listOf(seriesChild("episode-1", type = "Episode", indexNumber = 1L), broken))
            }

            assertNull(store.get("episode-1"))
        }

    @Test
    fun aFailedReplacementKeepsTheRowsItWasReplacing() =
        runTest {
            store.upsert(listOf(seriesChild("old", type = "Episode", indexNumber = 1L)))
            val broken = seriesChild("broken", type = "Episode", indexNumber = 2L).copy(taglines = ThrowingTaglines)

            assertFailsWith<IllegalStateException> {
                store.replaceForLibrary("server", "library", listOf(seriesChild("new", type = "Episode", indexNumber = 1L), broken))
            }

            assertEquals(listOf("old"), store.listEpisodesForSeries("server", "series-1").map { it.id })
            assertNull(store.get("new"))
        }

    @Test
    fun nextUpReplacementDropsEarlierEntriesAndKeepsTheNewOrder() =
        runTest {
            val first = seriesChild("episode-1", type = "Episode", indexNumber = 1L)
            val second = seriesChild("episode-2", type = "Episode", indexNumber = 2L)
            store.upsert(listOf(first, second))
            store.replaceNextUp("server", listOf("episode-1"), Instant.fromEpochMilliseconds(1L))

            store.replaceNextUp("server", listOf("episode-2", "episode-1"), Instant.fromEpochMilliseconds(2L))

            assertEquals(listOf("episode-2", "episode-1"), store.listNextUp("server", limit = 10).map { it.id })
        }

    @Test
    fun detailExistenceDoesNotNeedTheStoredJsonToBeValid() =
        runTest {
            assertFalse(detailStore.exists("server:item"))

            detailStore.upsert(JellyfinItemDetailRecord("server:item", "not json", Instant.fromEpochMilliseconds(1L)))

            assertTrue(detailStore.exists("server:item"))
            assertFalse(detailStore.exists("server:other"))
        }

    /** Throws when the store looks at the taglines, which happens while the row is being written. */
    private object ThrowingTaglines : AbstractList<String>() {
        override val size: Int get() = throw IllegalStateException("tagline access failed")

        override fun get(index: Int): String = throw IllegalStateException("tagline access failed")
    }

    private fun seriesChild(
        id: String,
        type: String,
        indexNumber: Long,
    ) = JellyfinItemRecord(
        id = id,
        serverId = "server",
        libraryId = "library",
        name = id,
        sortName = null,
        overview = null,
        type = type,
        mediaType = null,
        locationType = null,
        taglines = emptyList(),
        parentId = "series-1",
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = "series-1",
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = indexNumber,
        parentIndexNumber = 1L,
        seriesName = "Series",
        seasonId = "season-1",
        episodeTitle = null,
        lastPlayed = null,
        updatedAt = Instant.fromEpochMilliseconds(1L),
    )
}
