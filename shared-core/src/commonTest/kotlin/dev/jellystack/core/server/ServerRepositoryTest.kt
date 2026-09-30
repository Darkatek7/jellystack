package dev.jellystack.core.server

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.testing.InMemorySettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerRepositoryTest {
    @Test
    fun authenticatedJellyfinRegistrationPersistsTokenAndRemovesOldPassword() =
        runTest {
            val firstCredential =
                StoredCredential.Jellyfin(
                    username = "dummy-user",
                    deviceId = "old-device",
                    accessToken = "dummy-old-token",
                    userId = "user42",
                )
            val secureStore = FakeSecureStore()
            val repo = repository(secureStore) { ConnectivityResult.Success("ok", firstCredential) }
            val existing =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-old-password"),
                    ),
                )
            val quickConnectCredential =
                StoredCredential.Jellyfin(
                    username = "Alice",
                    deviceId = "quick-device",
                    accessToken = "dummy-quick-connect-token",
                    userId = "quick-user",
                )

            val updated =
                repo.registerAuthenticatedJellyfin(
                    AuthenticatedJellyfinRegistration(
                        id = existing.id,
                        name = "Media",
                        baseUrl = "https://media.example/",
                        credentials = quickConnectCredential,
                    ),
                )

            assertEquals(existing.id, updated.id)
            assertEquals("https://media.example", updated.baseUrl)
            assertEquals(quickConnectCredential, updated.credentials)
            assertNull(secureStore.peek("servers.${existing.id}.jellyfin.password"))
        }

    @Test
    fun authenticatedJellyfinRegistrationRejectsDuplicateUrl() =
        runTest {
            val repo = repository { error("Connectivity must not be used") }
            val credential =
                StoredCredential.Jellyfin(
                    username = "Alice",
                    deviceId = "quick-device",
                    accessToken = "dummy-quick-connect-token",
                    userId = "quick-user",
                )
            repo.registerAuthenticatedJellyfin(
                AuthenticatedJellyfinRegistration(
                    name = "First",
                    baseUrl = "https://media.example",
                    credentials = credential,
                ),
            )

            assertFailsWith<DuplicateServerException> {
                repo.registerAuthenticatedJellyfin(
                    AuthenticatedJellyfinRegistration(
                        name = "Duplicate",
                        baseUrl = "https://media.example/",
                        credentials = credential.copy(accessToken = "dummy-other-token"),
                    ),
                )
            }
        }

    @Test
    fun authenticatedJellyfinRegistrationAllowsDistinctPrincipalsOnOneUrl() =
        runTest {
            val repo = repository { error("Connectivity must not be used") }

            val first =
                repo.registerAuthenticatedJellyfin(
                    AuthenticatedJellyfinRegistration(
                        name = "Alice",
                        baseUrl = "https://media.example",
                        credentials = jellyfinCredential("alice"),
                    ),
                )
            val second =
                repo.registerAuthenticatedJellyfin(
                    AuthenticatedJellyfinRegistration(
                        name = "Bob",
                        baseUrl = "https://media.example/",
                        credentials = jellyfinCredential("bob"),
                    ),
                )

            assertTrue(first.id != second.id)
            assertEquals(setOf("alice", "bob"), repo.currentServers().map { it.credentials.principal }.toSet())
        }

    @Test
    fun passwordRegistrationChecksDuplicatesAfterAuthentication() =
        runTest {
            var authenticatedPrincipal = "alice"
            val repo =
                repository {
                    ConnectivityResult.Success("ok", jellyfinCredential(authenticatedPrincipal))
                }

            val first =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Alice",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin("Alice", "password"),
                    ),
                )
            authenticatedPrincipal = "bob"
            val second =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Bob",
                        baseUrl = "https://media.example/",
                        credentials = CredentialInput.Jellyfin("Bob", "password"),
                    ),
                )

            assertTrue(first.id != second.id)
            authenticatedPrincipal = "alice"
            assertFailsWith<DuplicateServerException> {
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Alice duplicate",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin("Alice", "password"),
                    ),
                )
            }
        }

    @Test
    fun registersJellyfinServerAndPersistsCredentials() =
        runTest {
            val storedCredential =
                StoredCredential.Jellyfin(
                    username = "dummy-user",
                    deviceId = "device-1",
                    accessToken = "dummy-token",
                    userId = "user42",
                )
            val secureStore = FakeSecureStore()
            val repo = repository(secureStore) { ConnectivityResult.Success("ok", storedCredential) }

            val managed =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example/",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-password"),
                    ),
                )

            assertEquals("https://media.example", managed.baseUrl)
            assertEquals(storedCredential, managed.credentials)
            assertTrue(repo.currentServers().isNotEmpty())
            assertEquals("dummy-password", secureStore.peek("servers.${managed.id}.jellyfin.password")?.reveal())
        }

    @Test
    fun registersPasswordlessJellyfinServerAndPreservesEmptyPasswordCredential() =
        runTest {
            val storedCredential =
                StoredCredential.Jellyfin(
                    username = "passwordless-user",
                    deviceId = "device-1",
                    accessToken = "dummy-token",
                    userId = "user42",
                )
            val secureStore = FakeSecureStore()
            val repo = repository(secureStore) { ConnectivityResult.Success("ok", storedCredential) }

            val managed =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "passwordless-user", password = ""),
                    ),
                )

            assertEquals("", repo.jellyfinPassword(managed.id)?.reveal())
            assertEquals("", secureStore.peek("servers.${managed.id}.jellyfin.password")?.reveal())
        }

    @Test
    fun duplicateBaseUrlRejected() =
        runTest {
            val repo = repository { successApiKey() }

            repo.register(
                ServerRegistration(
                    type = ServerType.SONARR,
                    name = "Shows",
                    baseUrl = "https://sonarr.example",
                    credentials = CredentialInput.ApiKey("dummy-api-key"),
                ),
            )

            assertFailsWith<DuplicateServerException> {
                repo.register(
                    ServerRegistration(
                        type = ServerType.SONARR,
                        name = "Shows 2",
                        baseUrl = "https://sonarr.example/",
                        credentials = CredentialInput.ApiKey("xyz"),
                    ),
                )
            }
        }

    @Test
    fun invalidUrlThrows() =
        runTest {
            val repo = repository { successApiKey() }

            assertFailsWith<InvalidServerConfiguration> {
                repo.register(
                    ServerRegistration(
                        type = ServerType.RADARR,
                        name = "Movies",
                        baseUrl = "ftp://invalid", // unsupported scheme
                        credentials = CredentialInput.ApiKey("dummy-api-key"),
                    ),
                )
            }
        }

    @Test
    fun connectivityFailureBubblesUp() =
        runTest {
            val repo = repository { ConnectivityResult.Failure("nope") }

            assertFailsWith<ConnectivityException> {
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = "https://requests.example",
                        credentials = CredentialInput.ApiKey("dummy-api-key"),
                    ),
                )
            }
        }

    @Test
    fun removeDeletesServer() =
        runTest {
            val repo = repository { successApiKey() }
            val managed =
                repo.register(
                    ServerRegistration(
                        type = ServerType.RADARR,
                        name = "Movies",
                        baseUrl = "https://radarr.example",
                        credentials = CredentialInput.ApiKey("dummy-api-key"),
                    ),
                )

            repo.remove(managed.id)
            assertTrue(repo.currentServers().isEmpty())
        }

    @Test
    fun findServerReturnsManagedInstance() =
        runTest {
            val storedCredential =
                StoredCredential.Jellyfin(
                    username = "dummy-user",
                    deviceId = "device-1",
                    accessToken = "dummy-token",
                    userId = "user42",
                )
            val repo = repository { ConnectivityResult.Success("ok", storedCredential) }

            val managed =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-password"),
                    ),
                )

            val fetched = repo.findServer(managed.id)
            assertNotNull(fetched)
            assertEquals(managed, fetched)
        }

    @Test
    fun jellyfinPasswordReturnsSecretOnlyForJellyfinServers() =
        runTest {
            val storedCredential =
                StoredCredential.Jellyfin(
                    username = "dummy-user",
                    deviceId = "device-1",
                    accessToken = "dummy-token",
                    userId = "user42",
                )
            val repo =
                repository { registration ->
                    when (registration.type) {
                        ServerType.JELLYFIN -> ConnectivityResult.Success("ok", storedCredential)
                        else -> ConnectivityResult.Success("ok", StoredCredential.ApiKey(apiKey = "dummy-api-key"))
                    }
                }

            val jellyfin =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYFIN,
                        name = "Media",
                        baseUrl = "https://media.example",
                        credentials = CredentialInput.Jellyfin(username = "dummy-user", password = "dummy-password"),
                    ),
                )
            val radarr =
                repo.register(
                    ServerRegistration(
                        type = ServerType.RADARR,
                        name = "Movies",
                        baseUrl = "https://radarr.example",
                        credentials = CredentialInput.ApiKey("dummy-api-key"),
                    ),
                )

            assertEquals("dummy-password", repo.jellyfinPassword(jellyfin.id)?.reveal())
            assertNull(repo.jellyfinPassword(radarr.id))
        }

    @Test
    fun firstServerOfATypeBecomesActiveAndAdditionalRegistrationKeepsSelection() =
        runTest {
            val repo = repository { successApiKey() }
            val first =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Alpha",
                        baseUrl = "https://alpha.example",
                        credentials = CredentialInput.ApiKey("a"),
                    ),
                )
            repo.register(
                ServerRegistration(
                    type = ServerType.JELLYSEERR,
                    name = "Beta",
                    baseUrl = "https://beta.example",
                    credentials = CredentialInput.ApiKey("b"),
                ),
            )

            assertEquals(first.id, repo.activeServer(ServerType.JELLYSEERR)?.id)
            assertEquals(first.id, repo.observeActiveServer(ServerType.JELLYSEERR).first()?.id)
        }

    @Test
    fun activeSelectionCanSwitchAndFallsBackAlphabeticallyAfterRemoval() =
        runTest {
            val repo = repository { successApiKey() }
            val beta =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Beta",
                        baseUrl = "https://beta.example",
                        credentials = CredentialInput.ApiKey("b"),
                    ),
                )
            val alpha =
                repo.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Alpha",
                        baseUrl = "https://alpha.example",
                        credentials = CredentialInput.ApiKey("a"),
                    ),
                )

            repo.setActiveServer(ServerType.JELLYSEERR, alpha.id)
            assertEquals(alpha.id, repo.activeServer(ServerType.JELLYSEERR)?.id)

            repo.remove(alpha.id)

            assertEquals(beta.id, repo.activeServer(ServerType.JELLYSEERR)?.id)
        }

    @Test
    fun invalidOrWrongTypeActiveSelectionIsRejected() =
        runTest {
            val repo = repository { successApiKey() }
            val radarr =
                repo.register(
                    ServerRegistration(
                        type = ServerType.RADARR,
                        name = "Radarr",
                        baseUrl = "https://radarr.example",
                        credentials = CredentialInput.ApiKey("r"),
                    ),
                )

            assertFailsWith<InvalidServerConfiguration> {
                repo.setActiveServer(ServerType.JELLYSEERR, radarr.id)
            }
        }

    private fun repository(
        secureStore: FakeSecureStore = FakeSecureStore(),
        resultProvider: (ServerRegistration) -> ConnectivityResult,
    ): ServerRepository {
        val store = InMemoryServerStore()
        val connectivity = ServerConnectivity { registration -> resultProvider(registration) }
        return ServerRepository(
            store,
            connectivity,
            ServerCredentialVault(secureStore),
            ActiveServerPreferenceRepository(InMemorySettings()),
            clock = FixedClock,
        )
    }

    private fun successApiKey(): ConnectivityResult = ConnectivityResult.Success("ok", StoredCredential.ApiKey(apiKey = "dummy-api-key"))

    private fun jellyfinCredential(userId: String) =
        StoredCredential.Jellyfin(
            username = userId,
            deviceId = "device-$userId",
            accessToken = "token-$userId",
            userId = userId,
        )
}

private val StoredCredential.principal: String?
    get() =
        when (this) {
            is StoredCredential.Jellyfin -> userId
            is StoredCredential.ApiKey -> userId
        }

private object FixedClock : kotlinx.datetime.Clock {
    private val instant = Instant.fromEpochMilliseconds(1_700_000_000_000)

    override fun now(): Instant = instant
}

private class InMemoryServerStore : ServerStore {
    private val items = linkedMapOf<String, ServerRecord>()

    override suspend fun list(): List<ServerRecord> = items.values.sortedBy { it.name }

    override suspend fun findByTypeAndUrl(
        type: ServerType,
        baseUrl: String,
    ): ServerRecord? = items.values.firstOrNull { it.type == type && it.baseUrl == baseUrl }

    override suspend fun get(id: String): ServerRecord? = items[id]

    override suspend fun upsert(record: ServerRecord) {
        items[record.id] = record
    }

    override suspend fun delete(id: String) {
        items.remove(id)
    }
}
