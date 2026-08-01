package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.HomeSectionsApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeSectionsRepositoryTest {
    @Test
    fun loadsConfiguredOrderAndSafeActions() =
        runTest {
            val repository =
                repository { path ->
                    when {
                        path.endsWith("/HomeScreen/Meta") ->
                            """{"Enabled":true,"PaginationEnabled":false,"NumResultsPerPage":10}"""
                        path.endsWith("/HomeScreen/Ready") -> ""
                        path.endsWith("/HomeScreen/Sections") ->
                            """{"Items":[
                            {"Section":"Discover","DisplayText":"Discover","ViewMode":"Portrait","OrderIndex":2},
                            {"Section":"ContinueWatching","DisplayText":"Continue watching","ViewMode":"Landscape","OrderIndex":1}
                        ]}"""
                        path.endsWith("/HomeScreen/Section/ContinueWatching") ->
                            """{"Items":[{"Id":"movie-1","Name":"Movie","Type":"Movie","ImageTags":{"Primary":"tag"}}]}"""
                        path.endsWith("/HomeScreen/Section/Discover") ->
                            """{"Items":[{"Name":"Upcoming","SourceType":"movie","ProviderIds":{"Jellyseerr":"42","JellyseerrPoster":"/HomeScreen/CachedImage/demo"}}]}"""
                        else -> error("Unexpected path $path")
                    }
                }

            repository.refresh(enabledByUser = true, language = "en")

            val ready = assertIs<HomeSectionsState.Ready>(repository.state.value)
            assertEquals(listOf("ContinueWatching:", "Discover:"), ready.sections.map(HomeSection::id))
            assertEquals(
                HomeSectionAction.JELLYFIN,
                ready.sections[0]
                    .items
                    .single()
                    .action,
            )
            assertEquals(
                HomeSectionAction.SEERR,
                ready.sections[1]
                    .items
                    .single()
                    .action,
            )
            assertEquals(
                42,
                ready.sections[1]
                    .items
                    .single()
                    .seerrTmdbId,
            )
        }

    @Test
    fun disabledPreferenceSkipsPlugin() =
        runTest {
            val repository = repository { error("Network must not be called") }

            repository.refresh(enabledByUser = false, language = null)

            assertIs<HomeSectionsState.Unavailable>(repository.state.value)
        }

    @Test
    fun externalItemsWithSharedPlaceholderIdReceiveUniqueStableIds() =
        runTest {
            val repository =
                repository { path ->
                    when {
                        path.endsWith("/HomeScreen/Meta") ->
                            """{"Enabled":true,"PaginationEnabled":false}"""
                        path.endsWith("/HomeScreen/Ready") -> ""
                        path.endsWith("/HomeScreen/Sections") ->
                            """{"Items":[{"Section":"Discover","DisplayText":"Discover"}]}"""
                        path.endsWith("/HomeScreen/Section/Discover") ->
                            """{"Items":[
                                {"Id":"00000000000000000000000000000000","Name":"Movie","SourceType":"movie","ProviderIds":{"Jellyseerr":"42"}},
                                {"Id":"00000000000000000000000000000000","Name":"Series","SourceType":"tv","ProviderIds":{"Jellyseerr":"84"}}
                            ]}"""
                        else -> error("Unexpected path $path")
                    }
                }

            repository.refresh(enabledByUser = true, language = "en")

            val items = assertIs<HomeSectionsState.Ready>(repository.state.value).sections.single().items
            assertEquals(listOf("seerr:movie:42", "seerr:tv:84"), items.map(HomeSectionItem::id))
        }

    @Test
    fun duplicatePluginItemsAreRemovedBeforeRendering() =
        runTest {
            val repository =
                repository { path ->
                    when {
                        path.endsWith("/HomeScreen/Meta") ->
                            """{"Enabled":true,"PaginationEnabled":false}"""
                        path.endsWith("/HomeScreen/Ready") -> ""
                        path.endsWith("/HomeScreen/Sections") ->
                            """{"Items":[{"Section":"Discover","DisplayText":"Discover"}]}"""
                        path.endsWith("/HomeScreen/Section/Discover") ->
                            """{"Items":[
                                {"Id":"00000000000000000000000000000000","Name":"Movie","SourceType":"movie","ProviderIds":{"Jellyseerr":"42"}},
                                {"Id":"00000000000000000000000000000000","Name":"Movie","SourceType":"movie","ProviderIds":{"Jellyseerr":"42"}}
                            ]}"""
                        else -> error("Unexpected path $path")
                    }
                }

            repository.refresh(enabledByUser = true, language = "en")

            val items = assertIs<HomeSectionsState.Ready>(repository.state.value).sections.single().items
            assertEquals(1, items.size)
        }

    @Test
    fun preservesSeriesArtworkOwnershipForEpisodeCards() =
        runTest {
            val repository =
                repository { path ->
                    when {
                        path.endsWith("/HomeScreen/Meta") ->
                            """{"Enabled":true,"PaginationEnabled":false}"""
                        path.endsWith("/HomeScreen/Ready") -> ""
                        path.endsWith("/HomeScreen/Sections") ->
                            """{"Items":[{"Section":"NextUp","DisplayText":"Next up","ViewMode":"Landscape"}]}"""
                        path.endsWith("/HomeScreen/Section/NextUp") ->
                            """{"Items":[{
                                "Id":"episode-1",
                                "Name":"Episode",
                                "Type":"Episode",
                                "SeriesId":"series-1",
                                "ImageTags":{"Primary":"episode-primary"},
                                "SeriesPrimaryImageTag":"series-primary",
                                "SeriesThumbImageTag":"series-thumb",
                                "ParentBackdropImageTags":["series-backdrop"]
                            }]}"""
                        else -> error("Unexpected path $path")
                    }
                }

            repository.refresh(enabledByUser = true, language = "en")

            val episode =
                assertIs<HomeSectionsState.Ready>(repository.state.value)
                    .sections
                    .single()
                    .items
                    .single()
                    .jellyfinItem
            requireNotNull(episode)
            assertEquals("series-1", episode.seriesId)
            assertEquals("series-primary", episode.seriesPrimaryImageTag)
            assertEquals("series-thumb", episode.seriesThumbImageTag)
            assertEquals("series-backdrop", episode.seriesBackdropImageTag)
        }

    private fun repository(responseFor: (String) -> String): HomeSectionsRepository {
        val engine =
            MockEngine { request ->
                respond(
                    content = responseFor(request.url.encodedPath),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = NetworkClientFactory.create(ClientConfig(engine = engine))
        val environment =
            JellyfinEnvironment(
                serverKey = "server",
                baseUrl = "https://jellyfin.example",
                accessToken = "dummy-token",
                userId = "user-1",
                deviceId = "device",
                deviceName = "test",
            )
        return HomeSectionsRepository(
            environmentProvider = JellyfinEnvironmentProvider { environment },
            apiFactory = { HomeSectionsApi(client, it.baseUrl, it.accessToken, it.deviceId) },
        )
    }
}
