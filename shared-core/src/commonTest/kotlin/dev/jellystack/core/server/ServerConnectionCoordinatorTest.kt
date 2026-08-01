package dev.jellystack.core.server

import dev.jellystack.core.jellyseerr.JellyseerrAuthRequest
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationException
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationResult
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.jellyseerr.JellyseerrQuickConnectAuthRequest
import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.testing.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerConnectionCoordinatorTest {
    @Test
    fun automaticSeerrConnectionReusesActiveJellyfinCredentials() =
        runTest {
            val authenticator = RecordingAuthenticator()
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            val jellyfin =
                coordinator.connectJellyfin(
                    JellyfinConnectionInput(
                        name = "Media",
                        baseUrl = "https://media.example",
                        username = "dummy-linked-user",
                        password = "dummy-linked-password",
                    ),
                )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput(name = "Requests", baseUrl = "https://requests.example/"),
                )

            val connected = assertIs<SeerrConnectionResult.Connected>(result)
            assertEquals(ServerType.JELLYSEERR, connected.server.type)
            assertEquals(jellyfin.id, repository.activeServer(ServerType.JELLYFIN)?.id)
            assertEquals(
                JellyseerrAuthRequest(
                    baseUrl = "https://requests.example",
                    method = JellyseerrAuthRequest.Method.JELLYFIN,
                    username = "dummy-linked-user",
                    password = "dummy-linked-password",
                ),
                authenticator.lastRequest,
            )
        }

    @Test
    fun automaticSeerrConnectionUsesStoredPasswordlessJellyfinCredentials() =
        runTest {
            val authenticator = RecordingAuthenticator()
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            coordinator.connectJellyfin(
                JellyfinConnectionInput(
                    name = "Media",
                    baseUrl = "https://media.example",
                    username = "passwordless-user",
                    password = "",
                ),
            )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput(name = "Requests", baseUrl = "https://requests.example"),
                )

            assertIs<SeerrConnectionResult.Connected>(result)
            assertEquals(
                JellyseerrAuthRequest(
                    baseUrl = "https://requests.example",
                    method = JellyseerrAuthRequest.Method.JELLYFIN,
                    username = "passwordless-user",
                    password = "",
                ),
                authenticator.lastRequest,
            )
            assertNull(authenticator.lastQuickConnectRequest)
        }

    @Test
    fun authenticationFailureRequestsCredentialsAndSuggestsLinkedUsername() =
        runTest {
            val authenticator =
                RecordingAuthenticator(
                    failure = JellyseerrAuthenticationException("Invalid Jellyfin credentials"),
                )
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            coordinator.connectJellyfin(
                JellyfinConnectionInput("Media", "https://media.example", "dummy-linked-user", "dummy-linked-password"),
            )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput("Requests", "https://requests.example"),
                )

            val required = assertIs<SeerrConnectionResult.CredentialsRequired>(result)
            assertEquals("dummy-linked-user", required.suggestedUsername)
            assertEquals("Invalid Jellyfin credentials", required.reason)
        }

    @Test
    fun automaticSeerrConnectionUsesQuickConnectForTokenOnlyJellyfin() =
        runTest {
            val authenticator = RecordingAuthenticator()
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            repository.registerAuthenticatedJellyfin(
                AuthenticatedJellyfinRegistration(
                    name = "Media",
                    baseUrl = "https://media.example",
                    credentials =
                        StoredCredential.Jellyfin(
                            username = "quick-user",
                            deviceId = "quick-device",
                            accessToken = "dummy-quick-connect-token",
                            userId = "quick-user-id",
                        ),
                ),
            )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput(
                        name = "Requests",
                        baseUrl = "https://requests.example/",
                        appVersion = "0.15.0",
                    ),
                )

            assertIs<SeerrConnectionResult.Connected>(result)
            assertEquals(
                JellyseerrQuickConnectAuthRequest(
                    baseUrl = "https://requests.example",
                    jellyfinBaseUrl = "https://media.example",
                    jellyfinAccessToken = "dummy-quick-connect-token",
                    jellyfinUserId = "quick-user-id",
                    jellyfinDeviceId = "quick-device",
                    appVersion = "0.15.0",
                ),
                authenticator.lastQuickConnectRequest,
            )
            assertNull(authenticator.lastRequest)
        }

    @Test
    fun unavailableSeerrQuickConnectFallsBackToCredentials() =
        runTest {
            val authenticator =
                RecordingAuthenticator(
                    failure =
                        JellyseerrAuthenticationException(
                            message = "Automatic Seerr sign-in is unavailable. Use your Jellyfin or Seerr password.",
                            reason = JellyseerrAuthenticationException.Reason.QUICK_CONNECT_UNAVAILABLE,
                        ),
                )
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            repository.registerAuthenticatedJellyfin(
                AuthenticatedJellyfinRegistration(
                    name = "Media",
                    baseUrl = "https://media.example",
                    credentials =
                        StoredCredential.Jellyfin(
                            username = "quick-user",
                            deviceId = "quick-device",
                            accessToken = "dummy-quick-connect-token",
                            userId = "quick-user-id",
                        ),
                ),
            )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput("Requests", "https://requests.example"),
                )

            val required = assertIs<SeerrConnectionResult.CredentialsRequired>(result)
            assertEquals("quick-user", required.suggestedUsername)
            assertEquals(
                "Automatic Seerr sign-in is unavailable. Use your Jellyfin or Seerr password.",
                required.reason,
            )
        }

    @Test
    fun transportFailureKeepsAutomaticLoginAndReturnsSafeConnectionMessage() =
        runTest {
            val authenticator =
                RecordingAuthenticator(
                    failure = IllegalStateException("TLSV1_ALERT_UNRECOGNIZED_NAME secret transport details"),
                )
            val repository = repository()
            val coordinator = ServerConnectionCoordinator(repository, authenticator)
            coordinator.connectJellyfin(
                JellyfinConnectionInput("Media", "https://media.example", "dummy-linked-user", "dummy-linked-password"),
            )

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput("Requests", "https://requests.example"),
                )

            val failed = assertIs<SeerrConnectionResult.ConnectionFailed>(result)
            assertEquals(
                "Could not connect to Seerr. Check the server URL and its HTTPS configuration.",
                failed.reason,
            )
        }

    @Test
    fun missingJellyfinRequestsCredentialsWithoutAttemptingAuthentication() =
        runTest {
            val authenticator = RecordingAuthenticator()
            val coordinator = ServerConnectionCoordinator(repository(), authenticator)

            val result =
                coordinator.connectSeerrAutomatically(
                    SeerrServerInput("Requests", "https://requests.example"),
                )

            val required = assertIs<SeerrConnectionResult.CredentialsRequired>(result)
            assertNull(required.suggestedUsername)
            assertNull(authenticator.lastRequest)
        }

    private fun repository(): ServerRepository {
        val secureStore = FakeSecureStore()
        val connectivity =
            ServerConnectivity { registration ->
                when (val credentials = registration.credentials) {
                    is CredentialInput.Jellyfin ->
                        ConnectivityResult.Success(
                            "ok",
                            StoredCredential.Jellyfin(
                                username = credentials.username,
                                deviceId = credentials.deviceId,
                                accessToken = "dummy-jellyfin-token",
                                userId = "jellyfin-user-id",
                            ),
                        )
                    is CredentialInput.ApiKey ->
                        ConnectivityResult.Success(
                            "ok",
                            StoredCredential.ApiKey(
                                apiKey = credentials.apiKey,
                                userId = credentials.userId,
                                sessionCookie = credentials.sessionCookie,
                            ),
                        )
                }
            }
        return ServerRepository(
            store = ConnectionTestServerStore(),
            connectivity = connectivity,
            credentialVault = ServerCredentialVault(secureStore),
            activeServerPreferences = ActiveServerPreferenceRepository(InMemorySettings()),
            clock = ConnectionTestClock,
            idGenerator = { "server-${nextId++}" },
        )
    }

    private class RecordingAuthenticator(
        private val failure: Throwable? = null,
    ) : JellyseerrAuthenticator() {
        var lastRequest: JellyseerrAuthRequest? = null
        var lastQuickConnectRequest: JellyseerrQuickConnectAuthRequest? = null

        override suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult {
            lastRequest = request
            failure?.let { throw it }
            return JellyseerrAuthenticationResult(
                apiKey = "dummy-seerr-api-key",
                userId = 7,
                sessionCookie = "connect.sid=session",
                hasValidatedSession = true,
            )
        }

        override suspend fun authenticateWithQuickConnect(request: JellyseerrQuickConnectAuthRequest): JellyseerrAuthenticationResult {
            lastQuickConnectRequest = request
            failure?.let { throw it }
            return JellyseerrAuthenticationResult(
                apiKey = "dummy-seerr-api-key",
                userId = 7,
                sessionCookie = "connect.sid=session",
                hasValidatedSession = true,
            )
        }
    }

    private companion object {
        var nextId = 1
    }
}

private object ConnectionTestClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

private class ConnectionTestServerStore : ServerStore {
    private val records = linkedMapOf<String, ServerRecord>()

    override suspend fun list(): List<ServerRecord> = records.values.sortedBy { it.name }

    override suspend fun findByTypeAndUrl(
        type: ServerType,
        baseUrl: String,
    ): ServerRecord? = records.values.firstOrNull { it.type == type && it.baseUrl == baseUrl }

    override suspend fun get(id: String): ServerRecord? = records[id]

    override suspend fun upsert(record: ServerRecord) {
        records[record.id] = record
    }

    override suspend fun delete(id: String) {
        records.remove(id)
    }
}
