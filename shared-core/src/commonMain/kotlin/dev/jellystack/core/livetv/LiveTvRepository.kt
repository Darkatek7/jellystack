package dev.jellystack.core.livetv

import dev.jellystack.core.jellyfin.AuthExpirySignal
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinClientIdentity
import dev.jellystack.network.jellyfin.JellyfinLiveTvApi
import dev.jellystack.network.jellyfin.observeJellyfinUnauthorized
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

typealias LiveTvApiFactory = (JellyfinEnvironment) -> JellyfinLiveTvApi

/**
 * Channels and guide entries of the server's Live TV. A server without Live TV, or a user without access to it, has no
 * channels: [channels] is empty then, which callers read as "hide Live TV".
 */
class LiveTvRepository(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val apiFactory: LiveTvApiFactory,
    private val clock: Clock = Clock.System,
) {
    suspend fun channels(limit: Int = JellyfinLiveTvApi.CHANNEL_LIMIT): List<LiveTvChannel> {
        val environment = environmentProvider.current() ?: return emptyList()
        return apiFactory(environment)
            .fetchChannels(environment.userId, limit)
            .items
            .map { it.toDomain() }
    }

    /** Whether the server has at least one channel for this user; one small request. */
    suspend fun hasChannels(): Boolean = channels(limit = 1).isNotEmpty()

    /**
     * The programs of [channelIds] from [at] until [window] later: what runs at [at] and what starts within the window,
     * sorted by start, grouped by channel. Channels without entries are absent from the map.
     */
    suspend fun programs(
        channelIds: List<String>,
        at: Instant = clock.now(),
        window: Duration = DEFAULT_PROGRAM_WINDOW,
    ): Map<String, List<LiveTvProgram>> {
        if (channelIds.isEmpty()) return emptyMap()
        val environment = environmentProvider.current() ?: return emptyMap()
        return apiFactory(environment)
            .fetchPrograms(
                userId = environment.userId,
                channelIds = channelIds,
                minEndDate = at.toString(),
                maxStartDate = (at + window).toString(),
            ).items
            .mapNotNull { it.toDomain() }
            .sortedBy(LiveTvProgram::start)
            .groupBy(LiveTvProgram::channelId)
    }

    companion object {
        /** Long enough for "next" and a little beyond; short enough to keep the answer small. */
        val DEFAULT_PROGRAM_WINDOW: Duration = 6.hours
    }
}

fun defaultLiveTvApiFactory(
    acceptLanguageProvider: () -> String? = { null },
    authExpirySignal: AuthExpirySignal? = null,
): LiveTvApiFactory {
    val client by lazy {
        NetworkClientFactory.create(
            ClientConfig(
                installLogging = false,
                acceptLanguageProvider = acceptLanguageProvider,
                configure = { authExpirySignal?.let { signal -> observeJellyfinUnauthorized(signal::report) } },
            ),
        )
    }
    return { environment ->
        JellyfinLiveTvApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            identity =
                JellyfinClientIdentity(
                    appVersion = environment.clientVersion,
                    deviceName = environment.deviceName,
                    deviceId = environment.deviceId ?: "unknown",
                ),
        )
    }
}
