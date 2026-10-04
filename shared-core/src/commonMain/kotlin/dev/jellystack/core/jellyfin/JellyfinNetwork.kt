package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinBrowseApi
import io.ktor.client.HttpClient

fun defaultJellyfinBrowseApiFactory(
    acceptLanguageProvider: () -> String? = { null },
    clientProvider: (() -> HttpClient)? = null,
): JellyfinBrowseApiFactory {
    val sharedClient: HttpClient by lazy {
        clientProvider?.invoke()
            ?: NetworkClientFactory.create(
                ClientConfig(
                    installLogging = false,
                    acceptLanguageProvider = acceptLanguageProvider,
                ),
            )
    }
    return { environment ->
        JellyfinBrowseApi(
            client = sharedClient,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            deviceId = environment.deviceId,
            clientName = "Jellystack",
            deviceName = environment.deviceName,
            clientVersion = environment.clientVersion,
        )
    }
}
