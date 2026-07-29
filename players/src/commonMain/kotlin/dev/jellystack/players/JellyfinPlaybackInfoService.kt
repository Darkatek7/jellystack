package dev.jellystack.players

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinPlaybackApi
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoRequestDto
import dev.jellystack.network.jellyfin.JellyfinPlaybackInfoResponseDto
import io.ktor.client.HttpClient

fun interface JellyfinPlaybackInfoService {
    suspend fun fetch(
        environment: JellyfinEnvironment,
        itemId: String,
        userId: String,
        request: JellyfinPlaybackInfoRequestDto,
    ): JellyfinPlaybackInfoResponseDto
}

class NetworkJellyfinPlaybackInfoService(
    private val client: HttpClient = NetworkClientFactory.create(ClientConfig(installLogging = false)),
) : JellyfinPlaybackInfoService {
    override suspend fun fetch(
        environment: JellyfinEnvironment,
        itemId: String,
        userId: String,
        request: JellyfinPlaybackInfoRequestDto,
    ): JellyfinPlaybackInfoResponseDto =
        JellyfinPlaybackApi(client, environment.baseUrl, environment.accessToken)
            .fetchPlaybackInfo(itemId, userId, request)
}
