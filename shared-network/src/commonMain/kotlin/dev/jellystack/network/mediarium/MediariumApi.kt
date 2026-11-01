package dev.jellystack.network.mediarium

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Native API only. Paths and bodies verified against Mediarium 3615787f. */
class MediariumApi(
    private val client: HttpClient,
    baseUrl: String,
    private val apiKey: String,
) {
    private val apiBase = baseUrl.trimEnd('/') + "/api"

    init {
        val url = Url(baseUrl)
        require(url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS)
        require(url.host.isNotBlank() && url.user == null && url.password == null)
        require(url.parameters.isEmpty() && url.fragment.isEmpty())
        require(apiKey.isNotBlank())
    }

    suspend fun account(): MediariumAccountDto = get("/auth/me")

    suspend fun search(query: String): List<MediariumTitleDto> = get("/discover/search") { parameter("q", query) }

    suspend fun trending(tv: Boolean): List<MediariumTitleDto> = get(if (tv) "/discover/tv/trending" else "/discover/trending")

    suspend fun popular(tv: Boolean): List<MediariumTitleDto> = get(if (tv) "/discover/tv/popular" else "/discover/popular")

    suspend fun detail(
        tmdbId: Int,
        tv: Boolean,
    ): MediariumDetailDto {
        require(tmdbId > 0)
        return get("/tmdb/${if (tv) "tv" else "movies"}/$tmdbId")
    }

    suspend fun requests(): MediariumRequestsDto = get("/requests")

    /**
     * An explicit acquisition request: search immediately and monitor the whole series.
     * Defaults for quality/folder/sources stay with Mediarium. See docs/mediarium.md.
     * 201 is a direct add; 202 is a request pending administrator approval.
     */
    suspend fun add(
        tmdbId: Int,
        tv: Boolean,
    ): MediariumAddResponseDto {
        require(tmdbId > 0)
        val response =
            client.post("$apiBase/${if (tv) "series" else "movies"}") {
                authenticate()
                contentType(ContentType.Application.Json)
                if (tv) {
                    setBody(MediariumAddSeriesDto(tmdbId, monitor = "all", searchNow = true))
                } else {
                    setBody(MediariumAddMovieDto(tmdbId, searchNow = true))
                }
            }
        response.checkStatus()
        return when (response.status) {
            HttpStatusCode.Accepted -> MediariumAddResponseDto.Pending(response.body<MediariumPendingDto>().request)
            HttpStatusCode.Created -> MediariumAddResponseDto.Added(response.body<MediariumLibraryItemDto>())
            else -> throw MediariumHttpException(response.status)
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        crossinline parameters: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response =
            client.get("$apiBase$path") {
                authenticate()
                parameters()
            }
        response.checkStatus()
        return response.body()
    }

    private fun HttpRequestBuilder.authenticate() {
        expectSuccess = false
        header("X-API-Key", apiKey)
    }

    private fun HttpResponse.checkStatus() {
        if (!status.isSuccess()) throw MediariumHttpException(status)
    }
}

/** Deliberately excludes URLs, API keys and server response bodies. */
class MediariumHttpException(
    val status: HttpStatusCode,
) : Exception("Mediarium HTTP ${status.value}")
