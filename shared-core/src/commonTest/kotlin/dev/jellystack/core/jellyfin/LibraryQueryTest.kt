package dev.jellystack.core.jellyfin

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryQueryTest {
    @Test
    fun mapsEverySupportedCollectionType() {
        val expected =
            mapOf(
                "movies" to LibraryQuery("Movie", true),
                "tvshows" to LibraryQuery("Series", true),
                "music" to LibraryQuery("MusicArtist,MusicAlbum,Audio", false),
                "boxsets" to LibraryQuery("BoxSet", false),
                "musicvideos" to LibraryQuery("MusicVideo", false),
                "photos" to LibraryQuery("Folder,PhotoAlbum,Photo,Video", false),
                "books" to LibraryQuery("Folder,Book,AudioBook,Audio", false),
                "playlists" to LibraryQuery("Playlist", false),
                "mixed" to LibraryQuery(null, false),
            )

        expected.forEach { (collectionType, query) ->
            assertEquals(query, libraryQueryForCollectionType(collectionType), collectionType)
        }
    }
}
