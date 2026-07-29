package dev.jellystack.network.jellyfin

import dev.jellystack.network.generated.jellyfin.AuthenticateByNameResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class JellyfinClientIdentity(
    val appName: String,
    val appVersion: String,
    val deviceName: String,
    val deviceId: String,
)

@Serializable
data class JellyfinQuickConnectSessionDto(
    @SerialName("Authenticated")
    val authenticated: Boolean = false,
    @SerialName("Secret")
    val secret: String = "",
    @SerialName("Code")
    val code: String = "",
)

@Serializable
data class JellyfinQuickConnectSecretDto(
    @SerialName("Secret")
    val secret: String,
)

interface JellyfinQuickConnectRemote {
    suspend fun isEnabled(): Boolean

    suspend fun initiate(): JellyfinQuickConnectSessionDto

    suspend fun poll(secret: String): JellyfinQuickConnectSessionDto

    suspend fun authenticate(secret: String): AuthenticateByNameResponse

    fun close()
}

class JellyfinQuickConnectApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val identity: JellyfinClientIdentity,
) : JellyfinQuickConnectRemote {
    override suspend fun isEnabled(): Boolean =
        client
            .request {
                method = HttpMethod.Get
                configure("/QuickConnect/Enabled")
            }.consumeAsJson()

    override suspend fun initiate(): JellyfinQuickConnectSessionDto =
        client
            .request {
                method = HttpMethod.Post
                configure("/QuickConnect/Initiate")
            }.consumeAsJson()

    override suspend fun poll(secret: String): JellyfinQuickConnectSessionDto =
        client
            .request {
                method = HttpMethod.Get
                configure("/QuickConnect/Connect")
                parameter("secret", secret)
            }.consumeAsJson()

    override suspend fun authenticate(secret: String): AuthenticateByNameResponse =
        client
            .request {
                method = HttpMethod.Post
                configure("/Users/AuthenticateWithQuickConnect")
                contentType(ContentType.Application.Json)
                setBody(JellyfinQuickConnectSecretDto(secret))
            }.consumeAsJson()

    override fun close() {
        client.close()
    }

    private fun HttpRequestBuilder.configure(pathSuffix: String) {
        url {
            takeFrom(baseUrl)
            path(pathSuffix.trimStart('/'))
        }
        headers.append(
            "X-Emby-Authorization",
            buildString {
                append("MediaBrowser Client=\"")
                append(identity.appName.escapeHeaderValue())
                append("\", Device=\"")
                append(identity.deviceName.escapeHeaderValue())
                append("\", DeviceId=\"")
                append(identity.deviceId.escapeHeaderValue())
                append("\", Version=\"")
                append(identity.appVersion.escapeHeaderValue())
                append("\"")
            },
        )
    }

    private suspend inline fun <reified T> HttpResponse.consumeAsJson(): T {
        if (!status.isSuccess()) {
            throw JellyfinQuickConnectHttpException(status.value)
        }
        return try {
            body()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw JellyfinQuickConnectInvalidResponseException()
        }
    }
}

class JellyfinQuickConnectAuthorizationApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val identity: JellyfinClientIdentity,
    private val accessToken: String,
) {
    suspend fun authorize(
        code: String,
        userId: String,
    ): Boolean =
        client
            .request {
                method = HttpMethod.Post
                url {
                    takeFrom(baseUrl)
                    path("QuickConnect/Authorize")
                }
                parameter("Code", code)
                parameter("UserId", userId)
                headers.append("X-Emby-Token", accessToken)
                headers.append(
                    "X-Emby-Authorization",
                    buildString {
                        append("MediaBrowser Client=\"")
                        append(identity.appName.escapeHeaderValue())
                        append("\", Device=\"")
                        append(identity.deviceName.escapeHeaderValue())
                        append("\", DeviceId=\"")
                        append(identity.deviceId.escapeHeaderValue())
                        append("\", Version=\"")
                        append(identity.appVersion.escapeHeaderValue())
                        append("\", Token=\"")
                        append(accessToken.escapeHeaderValue())
                        append("\"")
                    },
                )
            }.consumeAuthorizationResponse()

    private suspend fun HttpResponse.consumeAuthorizationResponse(): Boolean {
        if (!status.isSuccess()) {
            throw JellyfinQuickConnectHttpException(status.value)
        }
        return try {
            body()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            throw JellyfinQuickConnectInvalidResponseException()
        }
    }
}

class JellyfinQuickConnectHttpException(
    val code: Int,
) : RuntimeException("Jellyfin Quick Connect failed with status $code")

class JellyfinQuickConnectInvalidResponseException : RuntimeException("Jellyfin Quick Connect returned an invalid response")

private fun String.escapeHeaderValue(): String = replace("\\", "\\\\").replace("\"", "\\\"")
