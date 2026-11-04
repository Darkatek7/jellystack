package dev.jellystack.network.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinBrowseApiTest {
    @Test
    fun supportedBrowseRoutesPreserveServerBasePathAndUserQuery() =
        runTest {
            listOf("", "/", "/jellyfin", "/jellyfin/").forEach { basePath ->
                val requests = mutableListOf<HttpRequestData>()
                val engine =
                    MockEngine { request ->
                        requests += request
                        val body =
                            when {
                                request.url.encodedPath.endsWith("/Items/Latest") -> "[]"
                                request.url.encodedPath.endsWith("/Items/movie-42") -> """{"Id":"movie-42","Name":"Movie"}"""
                                else -> """{"Items":[],"TotalRecordCount":0}"""
                            }
                        respond(
                            body,
                            HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
                try {
                    val api = JellyfinBrowseApi(client, "https://example.test$basePath", "dummy-access-token")

                    api.fetchLibraries("user-id")
                    api.fetchLibraryItems("user-id", "library-id", 20, 10, recursive = false, searchTerm = "München & Wien")
                    api.fetchLatestItems("user-id", "library-id", 8, "Movie")
                    api.fetchContinueWatching("user-id", 6)
                    api.fetchNextUp("user-id", 12, parentId = "library-id")
                    api.fetchEpisodesForSeries("user-id", "series-id")
                    api.fetchItemDetail("user-id", "movie-42")

                    val prefix = basePath.trimEnd('/')
                    assertEquals(
                        listOf("/UserViews", "/Items", "/Items/Latest", "/UserItems/Resume", "/Shows/NextUp", "/Items", "/Items/movie-42")
                            .map { "$prefix$it" },
                        requests.map { it.url.encodedPath },
                    )
                    requests.forEach { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals("user-id", request.url.parameters["UserId"])
                        assertTrue(requireNotNull(request.headers[HttpHeaders.Authorization]).contains("Token=\"dummy-access-token\""))
                        assertNull(request.headers["X-Emby-Token"])
                        assertNull(request.headers["X-Emby-Authorization"])
                    }
                    assertEquals("München & Wien", requests[1].url.parameters["SearchTerm"])
                    assertEquals("false", requests[1].url.parameters["Recursive"])
                    assertEquals("20", requests[1].url.parameters["StartIndex"])
                    assertEquals("10", requests[1].url.parameters["Limit"])
                    assertEquals("library-id", requests[4].url.parameters["ParentId"])
                    assertEquals("series-id", requests[5].url.parameters["ParentId"])
                } finally {
                    client.close()
                }
            }
        }

    @Test
    fun offlineProgressUpdatesPositionWithoutOverwritingOtherUserData() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        """{"PlaybackPositionTicks":123450000,"Played":true}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            try {
                val api = JellyfinBrowseApi(client, "https://example.test/jellyfin", "dummy-access-token")

                api.reportPlaybackProgress("user-id", "movie-42", 123_450_000)
                api.markPlaybackCompleted("user-id", "movie-42")

                assertEquals(
                    listOf("/jellyfin/UserItems/movie-42/UserData", "/jellyfin/UserPlayedItems/movie-42"),
                    requests.map { it.url.encodedPath },
                )
                requests.forEach { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("user-id", request.url.parameters["UserId"])
                }
                assertEquals("""{"PlaybackPositionTicks":123450000}""", requests[0].bodyText())
                assertEquals("", requests[1].bodyText())
            } finally {
                client.close()
            }
        }

    @Test
    fun rejectedOfflineUpdatesFailSoTheyCanBeRetried() =
        runTest {
            val engine = MockEngine { respond("expired", HttpStatusCode.Unauthorized) }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            try {
                val api = JellyfinBrowseApi(client, "https://example.test", "dummy-access-token")

                assertFailsWith<IllegalStateException> { api.reportPlaybackProgress("user-id", "movie-42", 123_450_000) }
                assertFailsWith<IllegalStateException> { api.markPlaybackCompleted("user-id", "movie-42") }
            } finally {
                client.close()
            }
        }

    @Test
    fun fetchLatestItemsRequestsDateCreatedMetadata() =
        runTest {
            var requestedFields = ""
            val engine =
                MockEngine { request ->
                    requestedFields = request.url.parameters["Fields"].orEmpty()
                    respond(
                        "[]",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLatestItems(
                userId = "u-1",
                libraryId = "library-1",
                limit = 12,
                includeItemTypes = "Movie",
            )

            assertTrue("DateCreated" in requestedFields, "Expected DateCreated in $requestedFields")
            client.close()
        }

    @Test
    fun searchTermIsForwardedToLibraryItemsEndpoint() =
        runTest {
            var searchTerm: String? = null
            var parentId: String? = "not-set"
            val engine =
                MockEngine { request ->
                    searchTerm = request.url.parameters["SearchTerm"]
                    parentId = request.url.parameters["ParentId"]
                    respond(
                        """{"Items":[],"TotalRecordCount":0}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLibraryItems(
                userId = "u-1",
                libraryId = "",
                startIndex = 0,
                limit = 40,
                searchTerm = "Arrival",
            )

            assertEquals("Arrival", searchTerm)
            assertEquals(null, parentId)
            client.close()
        }

    @Test
    fun browseQueryParametersAreForwardedWithoutImplicitFilters() =
        runTest {
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[],"TotalRecordCount":0}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLibraryItems(
                userId = "u-1",
                libraryId = "mixed",
                startIndex = 30,
                limit = 30,
                includeItemTypes = "Movie,Series",
                filters = "IsFavorite",
                sortBy = "ProductionYear",
                sortOrder = "Descending",
                isPlayed = false,
                genres = listOf("Action", "Science Fiction"),
                years = listOf(1999, 2025),
            )

            assertEquals(listOf("ProductionYear"), parameters["SortBy"])
            assertEquals(listOf("Descending"), parameters["SortOrder"])
            assertEquals(listOf("false"), parameters["IsPlayed"])
            assertEquals(listOf("IsFavorite"), parameters["Filters"])
            assertEquals(listOf("Action|Science Fiction"), parameters["Genres"])
            assertEquals(listOf("1999,2025"), parameters["Years"])
            assertEquals(listOf("Movie,Series"), parameters["IncludeItemTypes"])
            client.close()
        }

    @Test
    fun latestItemsWithoutATypeLeaveTheFilterOut() =
        runTest {
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        "[]",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLatestItems("u-1", "books", 16, includeItemTypes = null)

            assertEquals(listOf("books"), parameters["ParentId"])
            assertNull(parameters["IncludeItemTypes"])
            client.close()
        }

    @Test
    fun nameLessThanCountsTheItemsBeforeALetter() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    requests += request
                    respond(
                        """{"Items":[],"TotalRecordCount":412}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val response =
                api.fetchLibraryItems(
                    userId = "u-1",
                    libraryId = "movies",
                    startIndex = 0,
                    limit = 1,
                    includeItemTypes = "Movie",
                    genres = listOf("Drama"),
                    nameLessThan = "M",
                )
            api.fetchLibraryItems(userId = "u-1", libraryId = "movies", startIndex = 0, limit = 1, nameLessThan = " ")

            assertEquals(412L, response.totalRecordCount)
            assertEquals("M", requests[0].url.parameters["NameLessThan"])
            assertEquals("Drama", requests[0].url.parameters["Genres"])
            assertNull(requests[1].url.parameters["NameLessThan"])
            client.close()
        }

    @Test
    fun personFilmographyQueryFiltersByPersonAcrossTheWholeLibrary() =
        runTest {
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[{"Id":"film-1","Name":"Film","Type":"Movie"}],"TotalRecordCount":1}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val page =
                api.fetchLibraryItems(
                    userId = "u-1",
                    libraryId = "",
                    startIndex = 0,
                    limit = 50,
                    includeItemTypes = "Movie,Series",
                    recursive = true,
                    personIds = listOf("person-1", "person-2"),
                )

            assertEquals(listOf("person-1,person-2"), parameters["PersonIds"])
            assertEquals(listOf("Movie,Series"), parameters["IncludeItemTypes"])
            assertEquals(listOf("true"), parameters["Recursive"])
            assertNull(parameters["ParentId"])
            assertEquals(listOf("film-1"), page.items.map { it.id })
            client.close()
        }

    @Test
    fun albumTracksQuerySortsByDiscThenTrackNumberBelowTheAlbum() =
        runTest {
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[{"Id":"t1","Name":"Opening Theme","Type":"Audio","IndexNumber":1,"ParentIndexNumber":1}],"TotalRecordCount":1}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val page =
                api.fetchLibraryItems(
                    userId = "u-1",
                    libraryId = "album-1",
                    startIndex = 0,
                    limit = 500,
                    includeItemTypes = "Audio",
                    recursive = true,
                    sortBy = "ParentIndexNumber,IndexNumber,SortName",
                )

            assertEquals(listOf("album-1"), parameters["ParentId"])
            assertEquals(listOf("ParentIndexNumber,IndexNumber,SortName"), parameters["SortBy"])
            assertEquals(listOf("Ascending"), parameters["SortOrder"])
            assertEquals(listOf("Audio"), parameters["IncludeItemTypes"])
            assertEquals(1, page.items.single().indexNumber)
            assertEquals(1, page.items.single().parentIndexNumber)
            client.close()
        }

    @Test
    fun albumArtistFilterSendsTheArtistIdsAndIsOmittedOtherwise() =
        runTest {
            val seen = mutableListOf<Map<String, List<String>>>()
            val engine =
                MockEngine { request ->
                    seen +=
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[],"TotalRecordCount":0}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLibraryItems("u-1", "", 0, 50, includeItemTypes = "MusicAlbum", albumArtistIds = listOf("artist-1"))
            api.fetchLibraryItems("u-1", "lib-1", 0, 50)

            assertEquals(listOf("artist-1"), seen[0]["AlbumArtistIds"])
            assertNull(seen[1]["AlbumArtistIds"])
            client.close()
        }

    @Test
    fun trackDetailDecodesArtistsAlbumAndAlbumCover() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"Id":"t1","Name":"Opening Theme","Album":"First Light","AlbumId":"album-1","AlbumPrimaryImageTag":"tag","AlbumArtist":"Example Band","Artists":["Example Band"]}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val detail = api.fetchItemDetail("u-1", "t1")

            assertEquals("First Light", detail.album)
            assertEquals("album-1", detail.albumId)
            assertEquals("tag", detail.albumPrimaryImageTag)
            assertEquals(listOf("Example Band"), detail.artists)
            client.close()
        }

    @Test
    fun queriesWithoutPersonsNeverSendThePersonFilter() =
        runTest {
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[],"TotalRecordCount":0}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.fetchLibraryItems(userId = "u-1", libraryId = "library-1", startIndex = 0, limit = 30)

            assertNull(parameters["PersonIds"])
            client.close()
        }

    @Test
    fun playlistItemsUseThePlaylistEndpointWithUserAndPaging() =
        runTest {
            var requestedPath = ""
            var parameters: Map<String, List<String>> = emptyMap()
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.encodedPath
                    parameters =
                        request.url.parameters
                            .entries()
                            .associate { it.key to it.value }
                    respond(
                        """{"Items":[{"Id":"b","Name":"B","Type":"Movie"},{"Id":"a","Name":"A","Type":"Episode"}],"TotalRecordCount":2}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test/jellyfin", accessToken = "dummy-access-token")

            val response = api.fetchPlaylistItems(userId = "u-1", playlistId = "playlist-9", startIndex = 20, limit = 100)

            assertEquals("/jellyfin/Playlists/playlist-9/Items", requestedPath)
            assertEquals(listOf("u-1"), parameters["UserId"])
            assertEquals(listOf("20"), parameters["StartIndex"])
            assertEquals(listOf("100"), parameters["Limit"])
            assertEquals(listOf("b", "a"), response.items.map { it.id })
            client.close()
        }

    @Test
    fun fetchItemDetailRequestsRichMetadataFields() =
        runTest {
            var requestedFields = ""
            val engine =
                MockEngine { request ->
                    requestedFields = request.url.parameters["Fields"].orEmpty()
                    respond(
                        """
                        {
                          "Id": "episode-42",
                          "Name": "Episode",
                          "BackdropImageTags": ["episode-backdrop"],
                          "ParentBackdropImageTags": ["series-backdrop"]
                        }
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val detail = api.fetchItemDetail("u-1", "episode-42")

            listOf(
                "People",
                "OriginalTitle",
                "OriginalLanguage",
                "ProductionLocations",
                "Tags",
                "ParentBackdropImageTags",
            ).forEach { field ->
                assertTrue(field in requestedFields, "Expected $field in $requestedFields")
            }
            assertEquals(listOf("episode-backdrop"), detail.backdropImageTags)
            assertEquals(listOf("series-backdrop"), detail.parentBackdropImageTags)
            client.close()
        }

    @Test
    fun aPageStillDecodesWhenOneItemHasNoName() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """
                        {"Items":[
                          {"Id":"a","Name":"A","Type":"Movie"},
                          {"Id":"nameless","Type":"Movie","SortName":"sorted"},
                          {"Id":"c","Name":"C","Type":"Movie"}
                        ],"TotalRecordCount":3}
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val page = api.fetchLibraryItems("u-1", "library-1", startIndex = 0, limit = 3)

            assertEquals(listOf("a", "nameless", "c"), page.items.map { it.id })
            assertEquals(listOf("A", null, "C"), page.items.map { it.name })
            assertEquals("sorted", page.items[1].sortName)
            client.close()
        }

    @Test
    fun fetchSimilarItemsUsesItemEndpointAndUserContext() =
        runTest {
            var requestedPath = ""
            var requestedUserId = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.encodedPath
                    requestedUserId = request.url.parameters["UserId"].orEmpty()
                    respond(
                        """{"Items":[{"Id":"related-1","Name":"Related","Type":"Movie"}],"TotalRecordCount":1}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val response = api.fetchSimilarItems("u-1", "movie-42", limit = 12)

            assertEquals("/Items/movie-42/Similar", requestedPath)
            assertEquals("u-1", requestedUserId)
            assertEquals(listOf("related-1"), response.items.map { it.id })
            client.close()
        }

    @Test
    fun setPlayedStatusPostsToPlayedItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Post &&
                            request.url.encodedPath.endsWith("/UserPlayedItems/movie-42") &&
                            request.url.parameters["UserId"] == "u-1" -> {
                            respond(
                                """{"Played":true,"PlaybackPositionTicks":0}""",
                                HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val userData = api.setPlayedStatus("u-1", "movie-42", played = true)

            assertEquals(true, userData.played)
            client.close()
        }

    @Test
    fun setPlayedStatusDeletesPlayedItemsEndpointForUnplayed() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Delete &&
                            request.url.encodedPath.endsWith("/UserPlayedItems/movie-42") &&
                            request.url.parameters["UserId"] == "u-1" -> {
                            respond(
                                """{"Played":false,"PlaybackPositionTicks":0}""",
                                HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val userData = api.setPlayedStatus("u-1", "movie-42", played = false)

            assertEquals(false, userData.played)
            client.close()
        }

    @Test
    fun addFavoritePostsToFavoriteItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Post &&
                            request.url.encodedPath.endsWith("/UserFavoriteItems/movie-42") &&
                            request.url.parameters["UserId"] == "u-1" -> {
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.addFavorite("u-1", "movie-42") // expect success, no throw

            client.close()
        }

    @Test
    fun removeFavoriteDeletesFavoriteItemsEndpoint() =
        runTest {
            val engine =
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Delete &&
                            request.url.encodedPath.endsWith("/UserFavoriteItems/movie-42") &&
                            request.url.parameters["UserId"] == "u-1" -> {
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> respond("not found", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            api.removeFavorite("u-1", "movie-42") // expect success, no throw

            client.close()
        }

    @Test
    fun fetchFavoriteIdsReturnsIds() =
        runTest {
            val engine =
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/Items") && request.url.parameters["UserId"] == "u-1") {
                        respond(
                            """{"Items":[{"Id":"a","Name":"A","Type":"Movie"},{"Id":"b","Name":"B","Type":"Movie"},{"Id":"c","Name":"C","Type":"Movie"}],"TotalRecordCount":3}""",
                            HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val ids = api.fetchFavoriteIds("u-1")

            assertEquals(setOf("a", "b", "c"), ids)

            client.close()
        }

    @Test
    fun searchPersonsAsksThePersonsEndpointWithTheNameAndAUserLimit() =
        runTest {
            var request: HttpRequestData? = null
            val engine =
                MockEngine {
                    request = it
                    respond(
                        """{"Items":[{"Id":"p-1","Name":"Ada Example","Type":"Person"}],"TotalRecordCount":1}""",
                        HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = NetworkClientFactory.create(ClientConfig(engine = engine, installLogging = false))
            val api = JellyfinBrowseApi(client, baseUrl = "https://example.test", accessToken = "dummy-access-token")

            val response = api.searchPersons("u-1", "Ada Example", limit = 5)

            val sent = request!!
            assertEquals(HttpMethod.Get, sent.method)
            assertEquals("/Persons", sent.url.encodedPath)
            assertEquals("Ada Example", sent.url.parameters["SearchTerm"])
            assertEquals("u-1", sent.url.parameters["UserId"])
            assertEquals("5", sent.url.parameters["Limit"])
            assertEquals(listOf("p-1"), response.items.map { it.id })

            client.close()
        }

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
}
