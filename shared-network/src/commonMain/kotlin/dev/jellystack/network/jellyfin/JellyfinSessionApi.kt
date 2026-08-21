package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

internal const val DEFAULT_JELLYSTACK_CLIENT_VERSION = "0.15.1"

/** Authenticated Jellyfin session and administrator endpoints used by capability-gated features. */
class JellyfinSessionApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val deviceId: String? = null,
    private val clientVersion: String = DEFAULT_JELLYSTACK_CLIENT_VERSION,
) {
    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.apply {
            appendIfAbsent("X-Emby-Token", accessToken)
            appendIfAbsent(
                "X-Emby-Authorization",
                "MediaBrowser Client=\"Jellystack\", Device=\"Android\", " +
                    "DeviceId=\"${deviceId ?: "unknown"}\", Version=\"$clientVersion\"",
            )
        }
    }

    private fun HeadersBuilder.appendIfAbsent(
        name: String,
        value: String,
    ) {
        if (!contains(name)) append(name, value)
    }

    suspend fun currentUser(): JellyfinUserDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users/Me")
            }.body()

    suspend fun users(): List<JellyfinUserDto> =
        client
            .request {
                method = HttpMethod.Get
                configure("/Users")
            }.body()

    suspend fun systemInfo(): JellyfinSystemInfoDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/System/Info")
            }.body()

    suspend fun itemCounts(): JellyfinItemCountsDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/Items/Counts")
            }.body()

    suspend fun activity(
        startIndex: Int,
        limit: Int,
        hasUserId: Boolean? = null,
    ): JellyfinActivityResponseDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/System/ActivityLog/Entries")
                parameter("startIndex", startIndex)
                parameter("limit", limit)
                hasUserId?.let { parameter("hasUserId", it) }
            }.body()

    suspend fun refreshLibrary() {
        client.request {
            method = HttpMethod.Post
            configure("/Library/Refresh")
        }
    }

    suspend fun restartServer() {
        client.request {
            method = HttpMethod.Post
            configure("/System/Restart")
        }
    }

    suspend fun createUser(
        name: String,
        password: String?,
    ): JellyfinUserDto =
        client
            .request {
                method = HttpMethod.Post
                configure("/Users/New")
                contentType(ContentType.Application.Json)
                setBody(JellyfinCreateUserRequestDto(name = name, password = password))
            }.body()

    suspend fun deleteUser(userId: String) {
        client.request {
            method = HttpMethod.Delete
            configure("/Users/$userId")
        }
    }

    /** Updates only the disabled flag while preserving every server-defined policy field. */
    suspend fun setUserDisabled(
        userId: String,
        disabled: Boolean,
    ) {
        val user =
            client
                .request {
                    method = HttpMethod.Get
                    configure("/Users/$userId")
                }.body<JsonObject>()
        val existingPolicy =
            user["Policy"]?.let { runCatching { it.jsonObject }.getOrNull() }
                ?: JsonObject(emptyMap())
        val updatedPolicy =
            buildJsonObject {
                existingPolicy.forEach { (key, value) -> put(key, value) }
                put("IsDisabled", JsonPrimitive(disabled))
            }
        client.request {
            method = HttpMethod.Post
            configure("/Users/$userId/Policy")
            contentType(ContentType.Application.Json)
            setBody(updatedPolicy)
        }
    }

    suspend fun resetUserPassword(
        userId: String,
        newPassword: String,
    ) {
        client.request {
            method = HttpMethod.Post
            configure("/Users/$userId/Password")
            contentType(ContentType.Application.Json)
            setBody(JellyfinPasswordRequestDto(newPassword = newPassword, resetPassword = true))
        }
    }
}

@Serializable
data class JellyfinUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Policy") val policy: JellyfinUserPolicyDto = JellyfinUserPolicyDto(),
    @SerialName("LastLoginDate") val lastLoginDate: String? = null,
    @SerialName("LastActivityDate") val lastActivityDate: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class JellyfinUserPolicyDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerialName("IsDisabled") val isDisabled: Boolean = false,
    @SerialName("SyncPlayAccess") val syncPlayAccess: String = "None",
)

@Serializable
data class JellyfinSystemInfoDto(
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
    @SerialName("Id") val id: String? = null,
)

@Serializable
data class JellyfinItemCountsDto(
    @SerialName("MovieCount") val movieCount: Int = 0,
    @SerialName("SeriesCount") val seriesCount: Int = 0,
    @SerialName("EpisodeCount") val episodeCount: Int = 0,
    @SerialName("AlbumCount") val albumCount: Int = 0,
    @SerialName("SongCount") val songCount: Int = 0,
    @SerialName("ArtistCount") val artistCount: Int = 0,
    @SerialName("BookCount") val bookCount: Int = 0,
)

@Serializable
data class JellyfinActivityResponseDto(
    @SerialName("Items") val items: List<JellyfinActivityEntryDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

@Serializable
data class JellyfinActivityEntryDto(
    @SerialName("Id") val id: Long = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("ShortOverview") val shortOverview: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Date") val date: String? = null,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserPrimaryImageTag") val userPrimaryImageTag: String? = null,
    @SerialName("Severity") val severity: String? = null,
)

@Serializable
private data class JellyfinCreateUserRequestDto(
    @SerialName("Name") val name: String,
    @SerialName("Password") val password: String? = null,
)

@Serializable
private data class JellyfinPasswordRequestDto(
    @SerialName("NewPw") val newPassword: String,
    @SerialName("ResetPassword") val resetPassword: Boolean,
)
