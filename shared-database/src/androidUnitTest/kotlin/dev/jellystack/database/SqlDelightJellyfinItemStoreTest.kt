package dev.jellystack.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.jellystack.core.jellyfin.JellyfinItemRecord
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightJellyfinItemStoreTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: SqlDelightJellyfinItemStore

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JellystackDatabase.Schema.create(driver)
        store = SqlDelightJellyfinItemStore(JellystackDatabase(driver).jellyfinItemsQueries)
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
