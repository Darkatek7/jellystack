package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvItemListTest {
    private val person = TvItemListSource.Person("person-1", "Ada Example")
    private val saga = TvItemListSource.Container("box-1", "Example Saga", isPlaylist = false)

    @Test
    fun collectionsAndPlaylistsOpenTheirEntriesAndEveryOtherTitleOpensItsDetail() {
        val box = listItem("box-1", "BoxSet").copy(name = "Saga")
        val playlist = listItem("pl-1", "Playlist").copy(name = "Mix")
        assertEquals(TvRoute.Collection("box-1", "Saga", isPlaylist = false), box.itemListRoute())
        assertEquals(TvRoute.Collection("pl-1", "Mix", isPlaylist = true), playlist.itemListRoute())
        assertNull(listItem("movie-1", "Movie").itemListRoute())
        assertNull(listItem("series-1", "Series").itemListRoute())
        assertNull(listItem("folder-1", "Folder").itemListRoute())
    }

    @Test
    fun albumsAndArtistsOpenTheirMusicPage() {
        val album = listItem("album-1", "MusicAlbum").copy(name = "First Light")
        val artist = listItem("artist-1", "MusicArtist").copy(name = "Example Band")
        assertEquals(TvRoute.Music("album-1", "First Light", isAlbum = true), album.itemListRoute())
        assertEquals(TvRoute.Music("artist-1", "Example Band", isAlbum = false), artist.itemListRoute())
        assertNull(listItem("track-1", "Audio").itemListRoute())
    }

    @Test
    fun musicRoutesMapToTheListTheyShowAndKeepSeparateKeys() {
        val album = TvRoute.Music("a", "First Light", isAlbum = true)
        val artist = TvRoute.Music("a", "Example Band", isAlbum = false)
        assertEquals(TvItemListSource.Album("a", "First Light"), album.itemListSource())
        assertEquals(TvItemListSource.Artist("a", "Example Band"), artist.itemListSource())
        assertEquals("album:a", TvItemListSource.Album("a", "x").key())
        assertEquals("artist:a", TvItemListSource.Artist("a", "x").key())
    }

    @Test
    fun theListsThatPlayAsOneProvideATitleToPlayAndAFilmographyDoesNot() {
        assertNull(person.playItem())
        assertEquals("MusicAlbum", TvItemListSource.Album("a", "x").playItem()?.type)
        assertEquals("MusicArtist", TvItemListSource.Artist("a", "x").playItem()?.type)
        assertEquals("BoxSet", saga.playItem()?.type)
    }

    @Test
    fun playButtonsAndTheTrackListFollowWhatTheListHolds() {
        fun ready(
            source: TvItemListSource,
            items: List<JellyfinItem>,
        ) = content(source, TvItemListState.Ready(items))
        val tracks = listOf(listItem("t1", "Audio"), listItem("t2", "Audio"))
        val album = TvItemListSource.Album("album-1", "First Light")
        val playlist = TvItemListSource.Container("pl-1", "Mix", isPlaylist = true)

        assertTrue(ready(album, tracks).showsTrackList)
        assertTrue(ready(album, tracks).hasShuffle)
        assertTrue(content(album, TvItemListState.Loading).showsTrackList)
        assertTrue(ready(playlist, tracks).showsTrackList)
        assertTrue(ready(playlist, tracks).hasShuffle)
        assertFalse(ready(playlist, tracks + listItem("film")).showsTrackList)
        assertFalse(ready(playlist, tracks + listItem("film")).hasShuffle)
        assertTrue(ready(playlist, tracks + listItem("film")).hasPlayAll)
        assertFalse(ready(saga, listOf(listItem("film"))).hasShuffle)
        val artist = TvItemListSource.Artist("artist-1", "Example Band")
        assertTrue(ready(artist, listOf(listItem("album-1", "MusicAlbum"))).hasShuffle)
        assertFalse(ready(artist, listOf(listItem("album-1", "MusicAlbum"))).showsTrackList)
        assertFalse(ready(artist, emptyList()).hasPlayAll)
        assertFalse(ready(person, listOf(listItem("film"))).hasPlayAll)
    }

    @Test
    fun routesMapToTheListTheyShow() {
        assertEquals(person, TvRoute.Person("person-1", "Ada Example").itemListSource())
        assertEquals(saga, TvRoute.Collection("box-1", "Example Saga").itemListSource())
        assertNull(TvRoute.Home.itemListSource())
        assertNull(TvRoute.JellyfinDetail("movie-1").itemListSource())
    }

    @Test
    fun aListLoadsOnceAndShowsItsTitlesWithoutDuplicates() =
        runTest {
            val coordinator = coordinator { listOf(listItem("a"), listItem("b"), listItem("a")) }

            coordinator.show(person)
            assertEquals(TvItemListState.Loading, coordinator.states.value[person.key()])
            runCurrent()

            val ready = coordinator.states.value[person.key()] as TvItemListState.Ready
            assertEquals(listOf("a", "b"), ready.items.map { it.id })
        }

    @Test
    fun aFailedLoadCanBeRetried() =
        runTest {
            var fail = true
            val coordinator =
                coordinator {
                    if (fail) error("offline")
                    listOf(listItem("a"))
                }

            coordinator.show(saga)
            runCurrent()
            assertEquals(TvItemListState.Failed, coordinator.states.value[saga.key()])

            fail = false
            coordinator.retry(saga)
            assertEquals(TvItemListState.Loading, coordinator.states.value[saga.key()])
            runCurrent()
            val ready = coordinator.states.value[saga.key()] as TvItemListState.Ready
            assertEquals(listOf("a"), ready.items.map { it.id })
        }

    @Test
    fun returningToAListKeepsItsTitlesVisibleWhileItRefreshesAndWhenTheRefreshFails() =
        runTest {
            var fail = false
            val gate = CompletableDeferred<Unit>()
            val coordinator =
                coordinator {
                    gate.await()
                    if (fail) error("offline")
                    listOf(listItem("a"))
                }
            gate.complete(Unit)
            coordinator.show(person)
            runCurrent()

            fail = true
            coordinator.show(person)
            val whileRefreshing = coordinator.states.value[person.key()]
            assertTrue("the shown titles stay while refreshing", whileRefreshing is TvItemListState.Ready)
            runCurrent()

            assertEquals(whileRefreshing, coordinator.states.value[person.key()])
        }

    @Test
    fun differentListsKeepTheirOwnState() =
        runTest {
            val coordinator = coordinator { source -> listOf(listItem(source.id)) }

            coordinator.show(person)
            coordinator.show(saga)
            runCurrent()

            val personItems = coordinator.states.value[person.key()] as TvItemListState.Ready
            val sagaItems = coordinator.states.value[saga.key()] as TvItemListState.Ready
            assertEquals(listOf("person-1"), personItems.items.map { it.id })
            assertEquals(listOf("box-1"), sagaItems.items.map { it.id })
        }

    private fun content(
        source: TvItemListSource,
        state: TvItemListState,
    ) = TvItemListContent(source, state, TvLibraryGridImages("https://example.com", null), emptySet(), "route")

    private fun TestScope.coordinator(load: suspend (TvItemListSource) -> List<JellyfinItem>) =
        TvItemListCoordinator(this, TvItemListLoader { load(it) })
}

internal fun listItem(
    id: String,
    type: String = "Movie",
    locationType: String? = null,
    positionTicks: Long? = null,
) = JellyfinItem(
    id = id,
    libraryId = null,
    name = id,
    sortName = null,
    overview = null,
    type = type,
    mediaType = "Video",
    locationType = locationType,
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
    positionTicks = positionTicks,
    playedPercentage = null,
    productionYear = null,
    premiereDate = null,
    communityRating = null,
    officialRating = null,
    indexNumber = null,
    parentIndexNumber = null,
    seriesName = null,
    seasonId = null,
    episodeTitle = null,
    lastPlayed = null,
)
