package dev.jellystack.network.jellyfin

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Jellyfin Live TV client: the channel list and a bounded window of programs.
 *
 * A server without Live TV answers 404, and a user without Live TV access 403. Both read as "no Live TV": the calls
 * return an empty result instead of failing, so a caller can hide the feature. Every other failure throws.
 */
class JellyfinLiveTvApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String,
    private val identity: JellyfinClientIdentity? = null,
) {
    /** The channels in channel order. `SortName` of a channel starts with its zero-padded number. */
    suspend fun fetchChannels(
        userId: String,
        limit: Int = CHANNEL_LIMIT,
    ): JellyfinLiveTvChannelsResponse =
        client
            .get {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("LiveTv", "Channels")
                }
                headers.appendJellyfinAuthorization(identity, accessToken)
                parameter("UserId", userId)
                parameter("EnableUserData", true)
                parameter("SortBy", "SortName")
                parameter("SortOrder", "Ascending")
                parameter("StartIndex", 0)
                parameter("Limit", limit)
                parameter("ImageTypeLimit", 1)
                parameter("EnableImageTypes", "Primary")
                // The current program is loaded with [fetchPrograms] for the visible channels only.
                parameter("AddCurrentProgram", false)
            }.orEmptyWhenUnavailable { JellyfinLiveTvChannelsResponse() }

    /**
     * The programs of [channelIds] that end after [minEndDate] and start before [maxStartDate] (ISO 8601, UTC). The
     * running program started before "now", so the lower bound is on the end, not on the start.
     */
    suspend fun fetchPrograms(
        userId: String,
        channelIds: List<String>,
        minEndDate: String,
        maxStartDate: String,
    ): JellyfinLiveTvProgramsResponse {
        if (channelIds.isEmpty()) return JellyfinLiveTvProgramsResponse()
        return client
            .get {
                url {
                    takeFrom(baseUrl)
                    appendPathSegments("LiveTv", "Programs")
                }
                headers.appendJellyfinAuthorization(identity, accessToken)
                parameter("UserId", userId)
                parameter("ChannelIds", channelIds.joinToString(","))
                parameter("MinEndDate", minEndDate)
                parameter("MaxStartDate", maxStartDate)
                parameter("SortBy", "StartDate")
                parameter("SortOrder", "Ascending")
                parameter("EnableTotalRecordCount", false)
                parameter("EnableImages", false)
                parameter("EnableUserData", false)
            }.orEmptyWhenUnavailable { JellyfinLiveTvProgramsResponse() }
    }

    private suspend inline fun <reified T> HttpResponse.orEmptyWhenUnavailable(empty: () -> T): T =
        when {
            status == HttpStatusCode.NotFound || status == HttpStatusCode.Forbidden -> empty()
            status.isSuccess() -> body()
            else -> error("Live TV request failed: ${status.value}")
        }

    companion object {
        /** A guide longer than this is not a list a remote can reasonably browse; the request stays bounded. */
        const val CHANNEL_LIMIT = 500
    }
}

@Serializable
data class JellyfinLiveTvChannelsResponse(
    @SerialName("Items") val items: List<JellyfinLiveTvChannelDto> = emptyList(),
)

@Serializable
data class JellyfinLiveTvChannelDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    /** The number as the tuner reports it; may be "12" or "12.1". */
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("Number") val number: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
)

@Serializable
data class JellyfinLiveTvProgramsResponse(
    @SerialName("Items") val items: List<JellyfinLiveTvProgramDto> = emptyList(),
)

@Serializable
data class JellyfinLiveTvProgramDto(
    @SerialName("Id") val id: String,
    @SerialName("ChannelId") val channelId: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("EpisodeTitle") val episodeTitle: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
)
