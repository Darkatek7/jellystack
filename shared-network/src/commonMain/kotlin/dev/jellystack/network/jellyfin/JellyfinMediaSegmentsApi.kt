package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Playback-facing boundary for Jellyfin media-segment lookups. */
interface JellyfinMediaSegmentsService {
    suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult
}

/**
 * Authenticated Jellyfin client for the optional media-segments endpoint.
 *
 * Segment metadata is advisory: failures intentionally degrade to [JellyfinMediaSegmentsResult.Unavailable]
 * so playback can continue normally.
 */
class JellyfinMediaSegmentsApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
) : JellyfinMediaSegmentsService {
    override suspend fun fetchSegments(itemId: String): JellyfinMediaSegmentsResult =
        try {
            val response =
                client.get {
                    url {
                        takeFrom(baseUrl)
                        path("MediaSegments/$itemId")
                    }
                    header("X-Emby-Token", accessToken)
                }

            when {
                response.status == HttpStatusCode.NotFound -> JellyfinMediaSegmentsResult.Available(emptyList())
                !response.status.isSuccess() -> JellyfinMediaSegmentsResult.Unavailable
                else -> JellyfinMediaSegmentsResult.Available(response.body<JellyfinMediaSegmentsQueryResultDto>().items)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ClientRequestException) {
            if (failure.response.status == HttpStatusCode.NotFound) {
                JellyfinMediaSegmentsResult.Available(emptyList())
            } else {
                JellyfinMediaSegmentsResult.Unavailable
            }
        } catch (_: Throwable) {
            JellyfinMediaSegmentsResult.Unavailable
        }
}

sealed interface JellyfinMediaSegmentsResult {
    data class Available(
        val segments: List<JellyfinMediaSegmentDto>,
    ) : JellyfinMediaSegmentsResult

    data object Unavailable : JellyfinMediaSegmentsResult
}

@Serializable
data class JellyfinMediaSegmentsQueryResultDto(
    @SerialName("Items") val items: List<JellyfinMediaSegmentDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
data class JellyfinMediaSegmentDto(
    @SerialName("Id") val id: String,
    @SerialName("ItemId") val itemId: String,
    @SerialName("Type") val type: String,
    @SerialName("StartTicks") val startTicks: Long,
    @SerialName("EndTicks") val endTicks: Long,
)
