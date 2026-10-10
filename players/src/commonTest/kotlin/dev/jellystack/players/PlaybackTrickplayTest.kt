package dev.jellystack.players

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackTrickplayTest {
    private val manifest =
        TrickplayManifest(width = 320, height = 180, columns = 10, rows = 10, thumbnailCount = 250, intervalMs = 10_000)

    @Test
    fun positionMapsToSheetAndTileOffset() {
        assertEquals(TrickplayTile(0, 0, 0, 320, 180), manifest.tileAt(0))
        assertEquals(TrickplayTile(0, 320, 0, 320, 180), manifest.tileAt(19_999))
        // Thumbnail 23 is on the first sheet, row 2, column 3.
        assertEquals(TrickplayTile(0, 960, 360, 320, 180), manifest.tileAt(230_000))
        // Thumbnail 105 opens the second sheet at row 0, column 5.
        assertEquals(TrickplayTile(1, 1600, 0, 320, 180), manifest.tileAt(1_050_000))
    }

    @Test
    fun positionsPastTheLastThumbnailShowTheLastOneOnThePartlyFilledSheet() {
        // 250 thumbnails: the third sheet holds 50, the last at row 4, column 9.
        assertEquals(TrickplayTile(2, 2880, 720, 320, 180), manifest.tileAt(9_999_000))
        assertEquals(manifest.tileAt(0), manifest.tileAt(-5_000))
    }

    @Test
    fun unusableManifestsHaveNoTiles() {
        assertNull(manifest.copy(intervalMs = 0).tileAt(1_000))
        assertNull(manifest.copy(columns = 0).tileAt(1_000))
        assertNull(manifest.copy(thumbnailCount = 0).tileAt(1_000))
    }

    @Test
    fun sourceIsMatchedWithoutDashesAndTheClosestWidthWins() {
        val small = manifest.copy(width = 240)
        val large = manifest.copy(width = 480)
        val manifests =
            mapOf(
                "0a1b2c3d4e5f60718293a4b5c6d7e8f9" to mapOf(240 to small, 480 to large),
                "ffffffffffffffffffffffffffffffff" to mapOf(320 to manifest),
            )

        val selected = selectTrickplayManifest(manifests, "0A1B2C3D-4E5F-6071-8293-A4B5C6D7E8F9", preferredWidth = 320)

        assertEquals("0a1b2c3d4e5f60718293a4b5c6d7e8f9" to small, selected)
    }

    @Test
    fun singleSourceIsUsedEvenWithAnUnmatchedIdButSeveralUnmatchedSourcesAreNot() {
        assertEquals("only" to manifest, selectTrickplayManifest(mapOf("only" to mapOf(320 to manifest)), "other", 320))
        assertNull(
            selectTrickplayManifest(
                mapOf("a" to mapOf(320 to manifest), "b" to mapOf(320 to manifest)),
                "other",
                320,
            ),
        )
        assertNull(selectTrickplayManifest(mapOf("a" to mapOf(320 to manifest.copy(intervalMs = 0))), "a", 320))
    }

    @Test
    fun sheetUrlNamesWidthSheetAndSource() {
        val trickplay = PlaybackTrickplay("item-1", "source-1", manifest)

        assertEquals(
            "https://example.test/Videos/item-1/Trickplay/320/2.jpg?MediaSourceId=source-1&ApiKey=token",
            trickplaySheetUrl("https://example.test/", trickplay, sheetIndex = 2, accessToken = "token"),
        )
        assertEquals(
            "https://example.test/Videos/item-1/Trickplay/320/0.jpg?MediaSourceId=source-1",
            trickplaySheetUrl("https://example.test", trickplay, sheetIndex = 0, accessToken = null),
        )
    }
}
