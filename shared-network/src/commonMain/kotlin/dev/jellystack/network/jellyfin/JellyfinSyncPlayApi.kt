package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class JellyfinSyncPlayApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val deviceId: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.append("X-Emby-Token", accessToken)
    }

    suspend fun groups(): List<JellyfinSyncPlayGroupDto> =
        client.request {
            method = HttpMethod.Get
            configure("/SyncPlay/List")
        }.body()

    suspend fun createGroup(name: String): JellyfinSyncPlayGroupDto =
        client.request {
            method = HttpMethod.Post
            configure("/SyncPlay/New")
            contentType(ContentType.Application.Json)
            setBody(NewSyncPlayGroupRequest(name.trim().take(MAX_GROUP_NAME_LENGTH)))
        }.body()

    suspend fun joinGroup(groupId: String) = post("/SyncPlay/Join", JoinSyncPlayGroupRequest(groupId))

    suspend fun leaveGroup() = postWithoutBody("/SyncPlay/Leave")

    suspend fun pause() = postWithoutBody("/SyncPlay/Pause")

    suspend fun unpause() = postWithoutBody("/SyncPlay/Unpause")

    suspend fun stop() = postWithoutBody("/SyncPlay/Stop")

    suspend fun seek(positionTicks: Long) = post("/SyncPlay/Seek", SyncPlaySeekRequest(positionTicks))

    suspend fun next(playlistItemId: String) = post("/SyncPlay/NextItem", SyncPlayPlaylistItemRequest(playlistItemId))

    suspend fun previous(playlistItemId: String) = post("/SyncPlay/PreviousItem", SyncPlayPlaylistItemRequest(playlistItemId))

    suspend fun setNewQueue(
        itemIds: List<String>,
        playingItemPosition: Int,
        startPositionTicks: Long,
    ) = post(
        "/SyncPlay/SetNewQueue",
        SyncPlayQueueRequest(itemIds, playingItemPosition, startPositionTicks),
    )

    suspend fun ready(
        whenUtc: String,
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String,
    ) = post("/SyncPlay/Ready", SyncPlayReadyRequest(whenUtc, positionTicks, isPlaying, playlistItemId))

    suspend fun buffering(
        whenUtc: String,
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String,
    ) = post("/SyncPlay/Buffering", SyncPlayReadyRequest(whenUtc, positionTicks, isPlaying, playlistItemId))

    fun events(): Flow<JsonObject> =
        flow {
            client.webSocket(urlString = socketUrl()) {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val payload = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: continue
                    emit(payload)
                }
            }
        }

    private suspend inline fun <reified T : Any> post(
        path: String,
        body: T,
    ) {
        client.request {
            method = HttpMethod.Post
            configure(path)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private suspend fun postWithoutBody(path: String) {
        client.request {
            method = HttpMethod.Post
            configure(path)
        }
    }

    private fun socketUrl(): String =
        URLBuilder().apply {
            takeFrom(baseUrl)
            protocol = if (protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
            path("socket")
            parameters.append("api_key", accessToken)
            parameters.append("deviceId", deviceId)
        }.buildString()

    private companion object {
        const val MAX_GROUP_NAME_LENGTH = 80
    }
}

@Serializable
data class JellyfinSyncPlayGroupDto(
    @SerialName("GroupId") val groupId: String,
    @SerialName("GroupName") val groupName: String,
    @SerialName("State") val state: String,
    @SerialName("Participants") val participants: List<String> = emptyList(),
    @SerialName("LastUpdatedAt") val lastUpdatedAt: String? = null,
)

@Serializable
private data class NewSyncPlayGroupRequest(@SerialName("GroupName") val groupName: String)

@Serializable
private data class JoinSyncPlayGroupRequest(@SerialName("GroupId") val groupId: String)

@Serializable
private data class SyncPlaySeekRequest(@SerialName("PositionTicks") val positionTicks: Long)

@Serializable
private data class SyncPlayPlaylistItemRequest(@SerialName("PlaylistItemId") val playlistItemId: String)

@Serializable
private data class SyncPlayQueueRequest(
    @SerialName("PlayingQueue") val playingQueue: List<String>,
    @SerialName("PlayingItemPosition") val playingItemPosition: Int,
    @SerialName("StartPositionTicks") val startPositionTicks: Long,
)

@Serializable
private data class SyncPlayReadyRequest(
    @SerialName("When") val whenUtc: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPlaying") val isPlaying: Boolean,
    @SerialName("PlaylistItemId") val playlistItemId: String,
)
