package dev.jellystack.design.tv

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyseerr.RequestProviderType
import dev.jellystack.core.server.SeerrConnectionResult
import dev.jellystack.core.server.SeerrLoginCredentials
import dev.jellystack.core.server.SeerrServerInput
import dev.jellystack.core.server.ServerConnectionCoordinator

internal data class TvRequestConnectionAttempt(
    val provider: RequestProviderType,
    val server: SeerrServerInput,
    val apiKey: String,
    val needsCredentials: Boolean,
    val credentials: SeerrLoginCredentials,
)

internal suspend fun connectTvRequestProvider(
    coordinator: ServerConnectionCoordinator,
    attempt: TvRequestConnectionAttempt,
): SeerrConnectionResult =
    runSuspendCatching {
        when {
            attempt.provider == RequestProviderType.MEDIARIUM ->
                SeerrConnectionResult.Connected(coordinator.connectMediarium(attempt.server, attempt.apiKey))
            !attempt.needsCredentials -> coordinator.connectSeerrAutomatically(attempt.server)
            else -> {
                val server = coordinator.connectSeerrManually(attempt.server, attempt.credentials)
                SeerrConnectionResult.Connected(server)
            }
        }
    }.getOrElse { SeerrConnectionResult.ConnectionFailed(it.message.orEmpty()) }
