package dev.jellystack.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class DatabaseMigrationTest {
    private lateinit var driver: JdbcSqliteDriver

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createIncludesSeriesColumns() {
        JellystackDatabase.Schema.create(driver)
        val columns = columnsFor("jellyfin_items")
        assertTrue(
            columns.containsAll(EXPECTED_LATEST_COLUMNS),
            "Schema.create should include latest item columns.",
        )
    }

    @Test
    fun migrateFromVersion1AddsSeriesColumns() {
        driver.execute(null, LEGACY_CREATE_SERVERS, 0)
        driver.execute(null, LEGACY_CREATE_ITEMS, 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)

        JellystackDatabase.Schema.migrate(driver, 1, JellystackDatabase.Schema.version)

        val columns = columnsFor("jellyfin_items")
        assertTrue(
            columns.containsAll(EXPECTED_SERIES_COLUMNS),
            "Migration should add missing series-related columns.",
        )
    }

    @Test
    fun migrateFromVersion5AddsDateCreated() {
        driver.execute(null, LEGACY_CREATE_ITEMS_AT_VERSION_5, 0)
        driver.execute(null, "PRAGMA user_version = 5", 0)

        JellystackDatabase.Schema.migrate(driver, 5, JellystackDatabase.Schema.version)

        val columns = columnsFor("jellyfin_items")
        assertTrue(
            "date_created" in columns,
            "Migration should add Jellyfin DateCreated cache column.",
        )
    }

    @Test
    fun migrateToLatestAddsFavoritesTable() {
        driver.execute(null, LEGACY_CREATE_ITEMS_AT_LATEST_KNOWN, 0)
        driver.execute(null, "PRAGMA user_version = $KNOWN_VERSION", 0)

        JellystackDatabase.Schema.migrate(driver, KNOWN_VERSION, JellystackDatabase.Schema.version)

        assertTrue("jellyfin_favorites" in tablesFor(driver))
    }

    @Test
    fun createIncludesHouseholdProfilesSavedMediaAndProviderIds() {
        JellystackDatabase.Schema.create(driver)

        assertTrue(
            tablesFor(driver).containsAll(
                setOf("household_profiles", "profile_connection_bindings", "profile_saved_media"),
            ),
        )
        assertTrue(columnsFor("jellyfin_items").containsAll(setOf("tmdb_id", "tvdb_id")))
    }

    @Test
    fun migrateFromVersion7PreservesItemsAndAddsRelease2Schema() {
        driver.execute(null, LEGACY_CREATE_ITEMS_AT_LATEST_KNOWN, 0)
        driver.execute(
            null,
            "INSERT INTO jellyfin_items (id, server_id, name, type, updated_at) VALUES ('item', 'server', 'Movie', 'Movie', 1)",
            0,
        )
        driver.execute(null, "PRAGMA user_version = $KNOWN_VERSION", 0)

        JellystackDatabase.Schema.migrate(driver, KNOWN_VERSION, JellystackDatabase.Schema.version)

        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM jellyfin_items"))
        assertTrue(columnsFor("jellyfin_items").containsAll(setOf("tmdb_id", "tvdb_id")))
        assertTrue(
            tablesFor(driver).containsAll(
                setOf("household_profiles", "profile_connection_bindings", "profile_saved_media"),
            ),
        )
    }

    @Test
    fun savedMediaIdentityIsUniqueWithinProfileButReusableAcrossProfiles() {
        JellystackDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO household_profiles VALUES ('p1', 'One', 'one', 1, 1, NULL)", 0)
        driver.execute(null, "INSERT INTO household_profiles VALUES ('p2', 'Two', 'two', 1, 1, NULL)", 0)
        val insert =
            "INSERT INTO profile_saved_media " +
                "(profile_id, media_type, provider, provider_id, title, created_at, updated_at) " +
                "VALUES (?, 'movie', 'tmdb', '603', 'The Matrix', 1, 1)"

        driver.execute(null, insert, 1) { bindString(0, "p1") }
        assertFails { driver.execute(null, insert, 1) { bindString(0, "p1") } }
        driver.execute(null, insert, 1) { bindString(0, "p2") }

        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM profile_saved_media"))
    }

    private fun columnsFor(table: String): Set<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA table_info($table)",
                mapper = { cursor: SqlCursor ->
                    QueryResult.Value(
                        buildSet<String> {
                            while (cursor.next().value) {
                                add(requireNotNull(cursor.getString(1)))
                            }
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun tablesFor(driver: JdbcSqliteDriver): Set<String> =
        driver
            .executeQuery(
                identifier = null,
                sql = "SELECT name FROM sqlite_master WHERE type = 'table'",
                mapper = { cursor: SqlCursor ->
                    QueryResult.Value(
                        buildSet<String> {
                            while (cursor.next().value) {
                                add(requireNotNull(cursor.getString(0)))
                            }
                        },
                    )
                },
                parameters = 0,
            ).value

    private fun scalarLong(sql: String): Long =
        driver
            .executeQuery(
                identifier = null,
                sql = sql,
                mapper = { cursor ->
                    check(cursor.next().value)
                    QueryResult.Value(requireNotNull(cursor.getLong(0)))
                },
                parameters = 0,
            ).value

    private companion object {
        private val EXPECTED_SERIES_COLUMNS =
            setOf(
                "series_id",
                "series_primary_image_tag",
                "series_thumb_image_tag",
                "series_backdrop_image_tag",
                "parent_logo_image_tag",
            )

        private val EXPECTED_LATEST_COLUMNS = EXPECTED_SERIES_COLUMNS + "date_created"

        private const val LEGACY_CREATE_ITEMS =
            """
            CREATE TABLE jellyfin_items (
                id TEXT NOT NULL PRIMARY KEY,
                server_id TEXT NOT NULL,
                library_id TEXT,
                name TEXT NOT NULL,
                sort_name TEXT,
                overview TEXT,
                type TEXT NOT NULL,
                media_type TEXT,
                taglines TEXT,
                parent_id TEXT,
                primary_image_tag TEXT,
                thumb_image_tag TEXT,
                backdrop_image_tag TEXT,
                run_time_ticks INTEGER,
                position_ticks INTEGER,
                played_percentage REAL,
                production_year INTEGER,
                premiere_date TEXT,
                community_rating REAL,
                official_rating TEXT,
                index_number INTEGER,
                parent_index_number INTEGER,
                series_name TEXT,
                season_id TEXT,
                episode_title TEXT,
                last_played TEXT,
                updated_at INTEGER NOT NULL
            );
            """

        private const val LEGACY_CREATE_SERVERS =
            """
            CREATE TABLE servers (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                username TEXT,
                device_id TEXT,
                api_key TEXT,
                access_token TEXT,
                user_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """

        private const val LEGACY_CREATE_ITEMS_AT_VERSION_5 =
            """
            CREATE TABLE jellyfin_items (
                id TEXT NOT NULL PRIMARY KEY,
                server_id TEXT NOT NULL,
                library_id TEXT,
                name TEXT NOT NULL,
                sort_name TEXT,
                overview TEXT,
                type TEXT NOT NULL,
                media_type TEXT,
                taglines TEXT,
                parent_id TEXT,
                primary_image_tag TEXT,
                thumb_image_tag TEXT,
                backdrop_image_tag TEXT,
                series_id TEXT,
                series_primary_image_tag TEXT,
                series_thumb_image_tag TEXT,
                series_backdrop_image_tag TEXT,
                parent_logo_image_tag TEXT,
                run_time_ticks INTEGER,
                position_ticks INTEGER,
                played_percentage REAL,
                production_year INTEGER,
                premiere_date TEXT,
                community_rating REAL,
                official_rating TEXT,
                index_number INTEGER,
                parent_index_number INTEGER,
                series_name TEXT,
                season_id TEXT,
                episode_title TEXT,
                last_played TEXT,
                updated_at INTEGER NOT NULL
            );
            """

        private const val KNOWN_VERSION = 7L

        private const val LEGACY_CREATE_ITEMS_AT_LATEST_KNOWN =
            """
            CREATE TABLE jellyfin_items (
                id TEXT NOT NULL PRIMARY KEY,
                server_id TEXT NOT NULL,
                library_id TEXT,
                name TEXT NOT NULL,
                sort_name TEXT,
                overview TEXT,
                type TEXT NOT NULL,
                media_type TEXT,
                location_type TEXT,
                taglines TEXT,
                parent_id TEXT,
                primary_image_tag TEXT,
                thumb_image_tag TEXT,
                backdrop_image_tag TEXT,
                series_id TEXT,
                series_primary_image_tag TEXT,
                series_thumb_image_tag TEXT,
                series_backdrop_image_tag TEXT,
                parent_logo_image_tag TEXT,
                run_time_ticks INTEGER,
                position_ticks INTEGER,
                played_percentage REAL,
                production_year INTEGER,
                premiere_date TEXT,
                community_rating REAL,
                official_rating TEXT,
                index_number INTEGER,
                parent_index_number INTEGER,
                series_name TEXT,
                season_id TEXT,
                episode_title TEXT,
                last_played TEXT,
                date_created TEXT,
                updated_at INTEGER NOT NULL
            );
            """
    }
}
