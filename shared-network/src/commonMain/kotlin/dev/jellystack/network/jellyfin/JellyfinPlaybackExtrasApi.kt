package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Playback-facing boundary for chapters and trickplay thumbnails of an item. */
fun interface JellyfinPlaybackExtrasService {
    suspend fun fetchExtras(itemId: String): JellyfinPlaybackExtrasResult
}

/**
 * Loads chapters and trickplay manifests when playback starts. They are fetched apart from the cached
 * item detail because trickplay images are generated later by a server task.
 *
 * Like media segments the data is advisory: failures degrade to [JellyfinPlaybackExtrasResult.Unavailable].
 */
class JellyfinPlaybackExtrasApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val userId: String,
    private val identity: JellyfinClientIdentity? = null,
) : JellyfinPlaybackExtrasService {
    override suspend fun fetchExtras(itemId: String): JellyfinPlaybackExtrasResult =
        try {
            val response =
                client.get {
                    url {
                        takeFrom(baseUrl)
                        path("Items/$itemId")
                    }
                    parameter("UserId", userId)
                    parameter("Fields", "Chapters,Trickplay")
                    parameter("EnableImages", false)
                    parameter("EnableUserData", false)
                    headers.appendJellyfinAuthorization(identity, accessToken)
                }
            if (response.status.isSuccess()) {
                JellyfinPlaybackExtrasResult.Available(response.body<JellyfinPlaybackExtrasDto>())
            } else {
                JellyfinPlaybackExtrasResult.Unavailable
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            JellyfinPlaybackExtrasResult.Unavailable
        }
}

sealed interface JellyfinPlaybackExtrasResult {
    data class Available(
        val extras: JellyfinPlaybackExtrasDto,
    ) : JellyfinPlaybackExtrasResult

    data object Unavailable : JellyfinPlaybackExtrasResult
}

@Serializable
data class JellyfinPlaybackExtrasDto(
    @SerialName("Id") val id: String,
    @SerialName("Chapters") val chapters: List<JellyfinChapterDto>? = null,
    /** Media source ID, then tile width (as a JSON key), to the trickplay manifest of that width. */
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, JellyfinTrickplayInfoDto>>? = null,
)

@Serializable
data class JellyfinChapterDto(
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0L,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
)

@Serializable
data class JellyfinTrickplayInfoDto(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("TileWidth") val tileWidth: Int = 0,
    @SerialName("TileHeight") val tileHeight: Int = 0,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    @SerialName("Interval") val interval: Int = 0,
)
