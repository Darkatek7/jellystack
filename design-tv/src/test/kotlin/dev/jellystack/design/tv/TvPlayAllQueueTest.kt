package dev.jellystack.design.tv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TvPlayAllQueueTest {
    @Test
    fun theQueueKeepsTheOrderOfTheListAndLeavesOutWhatCannotBePlayed() =
        runTest {
            val entries =
                listOf(
                    listItem("first"),
                    listItem("missing", locationType = "Virtual"),
                    listItem("folder", type = "Folder"),
                    listItem("nested", type = "BoxSet"),
                    listItem("song", type = "Audio"),
                    listItem("second", type = "Episode"),
                    listItem("first"),
                )

            val queue = buildPlayAllQueue(entries) { emptyList() }

            assertEquals(listOf("first", "song", "second"), queue.map { it.id })
        }

    @Test
    fun musicTracksPlayInTheOrderOfTheListAndAMissingTrackIsLeftOut() =
        runTest {
            val entries =
                listOf(
                    listItem("t1", type = "Audio"),
                    listItem("gone", type = "Audio", locationType = "Virtual"),
                    listItem("album", type = "MusicAlbum"),
                    listItem("t2", type = "Audio"),
                )

            val queue = buildPlayAllQueue(entries) { emptyList() }

            assertEquals(listOf("t1", "t2"), queue.map { it.id })
        }

    @Test
    fun aSeriesInTheListPlaysAsItsEpisodesInSeasonOrder() =
        runTest {
            val entries = listOf(listItem("movie"), listItem("show", type = "Series"), listItem("later"))
            val episodes =
                listOf(
                    episode("s2e1", season = 2, number = 1),
                    episode("special", season = 0, number = 1),
                    episode("s1e1", season = 1, number = 1),
                )

            val queue = buildPlayAllQueue(entries) { episodes }

            assertEquals(listOf("movie", "s1e1", "s2e1", "special", "later"), queue.map { it.id })
        }

    @Test
    fun aSeriesWhoseEpisodesCannotBeLoadedIsSkippedInsteadOfFailingTheList() =
        runTest {
            val entries = listOf(listItem("show", type = "Series"), listItem("movie"))

            val queue = buildPlayAllQueue(entries) { error("offline") }

            assertEquals(listOf("movie"), queue.map { it.id })
        }

    private fun episode(
        id: String,
        season: Int,
        number: Int,
    ) = listItem(id, type = "Episode", locationType = "FileSystem")
        .copy(parentIndexNumber = season, indexNumber = number)
}
