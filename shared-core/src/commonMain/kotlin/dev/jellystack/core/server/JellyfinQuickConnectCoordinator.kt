package dev.jellystack.core.server

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinClientIdentity
import dev.jellystack.network.jellyfin.JellyfinQuickConnectApi
import dev.jellystack.network.jellyfin.JellyfinQuickConnectHttpException
import dev.jellystack.network.jellyfin.JellyfinQuickConnectInvalidResponseException
import dev.jellystack.network.jellyfin.JellyfinQuickConnectRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class JellyfinSignInMethod {
    QUICK_CONNECT,
    PASSWORD,
}

data class JellyfinQuickConnectInput(
    val name: String,
    val baseUrl: String,
    val serverId: String? = null,
    val appVersion: String,
    val deviceName: String = "Jellystack",
)

data class JellyfinQuickConnectSession(
    val code: String,
    val expiresAt: Instant,
)

enum class JellyfinQuickConnectError {
    DISABLED,
    EXPIRED,
    TRANSPORT,
    INVALID_RESPONSE,
    AUTHENTICATION_FAILED,
}

sealed interface JellyfinQuickConnectState {
    data object Starting : JellyfinQuickConnectState

    data class Waiting(
        val session: JellyfinQuickConnectSession,
    ) : JellyfinQuickConnectState

    data object Registering : JellyfinQuickConnectState

    data class Connected(
        val server: ManagedServer,
    ) : JellyfinQuickConnectState

    data class Failed(
        val error: JellyfinQuickConnectError,
    ) : JellyfinQuickConnectState
}

fun interface JellyfinQuickConnectRemoteFactory {
    fun create(
        baseUrl: String,
        identity: JellyfinClientIdentity,
    ): JellyfinQuickConnectRemote
}

class JellyfinQuickConnectCoordinator(
    private val serverRepository: ServerRepository,
    private val remoteFactory: JellyfinQuickConnectRemoteFactory =
        JellyfinQuickConnectRemoteFactory { baseUrl, identity ->
            JellyfinQuickConnectApi(
                client =
                    NetworkClientFactory.create(
                        ClientConfig(
                            maxRetries = 0,
                            installLogging = false,
                            userAgent = "${identity.appName}/${identity.appVersion}",
                        ),
                    ),
                baseUrl = baseUrl,
                identity = identity,
            )
        },
    private val clock: Clock = Clock.System,
    private val idGenerator: () -> String = { randomId() },
    private val pollInterval: Duration = 5.seconds,
    private val sessionDuration: Duration = 10.minutes,
    private val maxConsecutiveTransportErrors: Int = 3,
) {
    fun connect(input: JellyfinQuickConnectInput): Flow<JellyfinQuickConnectState> =
        flow {
            emit(JellyfinQuickConnectState.Starting)
            val normalizedUrl =
                try {
                    normalizeBaseUrl(input.baseUrl)
                } catch (_: Throwable) {
                    emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.INVALID_RESPONSE))
                    return@flow
                }
            val deviceId = "jellystack-${idGenerator()}"
            val remote =
                remoteFactory.create(
                    normalizedUrl,
                    JellyfinClientIdentity(
                        appName = APP_NAME,
                        appVersion = input.appVersion,
                        deviceName = input.deviceName,
                        deviceId = deviceId,
                    ),
                )
            try {
                val enabled =
                    try {
                        remote.isEnabled()
                    } catch (error: Throwable) {
                        emit(JellyfinQuickConnectState.Failed(error.toQuickConnectError()))
                        return@flow
                    }
                if (!enabled) {
                    emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.DISABLED))
                    return@flow
                }

                val initiated =
                    try {
                        remote.initiate()
                    } catch (error: Throwable) {
                        emit(JellyfinQuickConnectState.Failed(error.toQuickConnectError()))
                        return@flow
                    }
                if (
                    initiated.authenticated ||
                    initiated.secret.isBlank() ||
                    !initiated.code.isSixDigitCode()
                ) {
                    emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.INVALID_RESPONSE))
                    return@flow
                }

                val startedAt = clock.now()
                val expiresAt =
                    Instant.fromEpochMilliseconds(
                        startedAt.toEpochMilliseconds() + sessionDuration.inWholeMilliseconds,
                    )
                emit(
                    JellyfinQuickConnectState.Waiting(
                        JellyfinQuickConnectSession(
                            code = initiated.code,
                            expiresAt = expiresAt,
                        ),
                    ),
                )

                var consecutiveTransportErrors = 0
                val maximumPolls = (sessionDuration.inWholeMilliseconds / pollInterval.inWholeMilliseconds).toInt()
                repeat(maximumPolls) {
                    delay(pollInterval)
                    val polled =
                        try {
                            remote.poll(initiated.secret)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: JellyfinQuickConnectHttpException) {
                            when (error.code) {
                                401 -> {
                                    emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.DISABLED))
                                    return@flow
                                }
                                404 -> {
                                    emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.EXPIRED))
                                    return@flow
                                }
                                else -> {
                                    consecutiveTransportErrors += 1
                                    null
                                }
                            }
                        } catch (_: JellyfinQuickConnectInvalidResponseException) {
                            emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.INVALID_RESPONSE))
                            return@flow
                        } catch (_: Throwable) {
                            consecutiveTransportErrors += 1
                            null
                        }

                    if (polled == null) {
                        if (consecutiveTransportErrors >= maxConsecutiveTransportErrors) {
                            emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.TRANSPORT))
                            return@flow
                        }
                        return@repeat
                    }

                    consecutiveTransportErrors = 0
                    if (!polled.authenticated) {
                        return@repeat
                    }
                    if (polled.secret.isNotBlank() && polled.secret != initiated.secret) {
                        emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.INVALID_RESPONSE))
                        return@flow
                    }

                    emit(JellyfinQuickConnectState.Registering)
                    val authentication =
                        try {
                            remote.authenticate(initiated.secret)
                        } catch (error: Throwable) {
                            emit(JellyfinQuickConnectState.Failed(error.toQuickConnectError(authentication = true)))
                            return@flow
                        }
                    if (
                        authentication.accessToken.isBlank() ||
                        authentication.user.id.isBlank()
                    ) {
                        emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.INVALID_RESPONSE))
                        return@flow
                    }
                    val server =
                        try {
                            serverRepository.registerAuthenticatedJellyfin(
                                AuthenticatedJellyfinRegistration(
                                    id = input.serverId,
                                    name = input.name.trim().ifBlank { defaultName(normalizedUrl) },
                                    baseUrl = normalizedUrl,
                                    credentials =
                                        StoredCredential.Jellyfin(
                                            username =
                                                authentication.user.name
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?: authentication.user.id,
                                            deviceId = deviceId,
                                            accessToken = authentication.accessToken,
                                            userId = authentication.user.id,
                                        ),
                                ),
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            emit(
                                JellyfinQuickConnectState.Failed(
                                    JellyfinQuickConnectError.AUTHENTICATION_FAILED,
                                ),
                            )
                            return@flow
                        }
                    emit(JellyfinQuickConnectState.Connected(server))
                    return@flow
                }

                emit(JellyfinQuickConnectState.Failed(JellyfinQuickConnectError.EXPIRED))
            } finally {
                remote.close()
            }
        }

    private fun Throwable.toQuickConnectError(authentication: Boolean = false): JellyfinQuickConnectError =
        when (this) {
            is CancellationException -> throw this
            is JellyfinQuickConnectHttpException ->
                when (code) {
                    401 -> JellyfinQuickConnectError.DISABLED
                    404 -> JellyfinQuickConnectError.EXPIRED
                    else ->
                        if (authentication) {
                            JellyfinQuickConnectError.AUTHENTICATION_FAILED
                        } else {
                            JellyfinQuickConnectError.TRANSPORT
                        }
                }
            is JellyfinQuickConnectInvalidResponseException -> JellyfinQuickConnectError.INVALID_RESPONSE
            else ->
                if (authentication) {
                    JellyfinQuickConnectError.AUTHENTICATION_FAILED
                } else {
                    JellyfinQuickConnectError.TRANSPORT
                }
        }

    private fun String.isSixDigitCode(): Boolean = length == 6 && all(Char::isDigit)

    private fun defaultName(baseUrl: String): String = baseUrl.substringAfter("://").substringBefore('/').ifBlank { "Jellyfin" }

    private companion object {
        const val APP_NAME = "Jellystack"
    }
}
