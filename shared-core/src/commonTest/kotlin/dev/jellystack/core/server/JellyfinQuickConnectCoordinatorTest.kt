package dev.jellystack.core.server

import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.testing.InMemorySettings
import dev.jellystack.network.generated.jellyfin.AuthenticateByNameResponse
import dev.jellystack.network.generated.jellyfin.AuthenticateByNameUser
import dev.jellystack.network.jellyfin.JellyfinQuickConnectHttpException
import dev.jellystack.network.jellyfin.JellyfinQuickConnectRemote
import dev.jellystack.network.jellyfin.JellyfinQuickConnectSessionDto
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JellyfinQuickConnectCoordinatorTest {
    @Test
    fun approvedCodeAuthenticatesAndPersistsExactlyOnce() =
        runTest {
            val remote =
                FakeQuickConnectRemote(
                    pollResults =
                        ArrayDeque(
                            listOf(
                                JellyfinQuickConnectSessionDto(
                                    authenticated = false,
                                    secret = SECRET,
                                    code = CODE,
                                ).asSuccess(),
                                JellyfinQuickConnectSessionDto(
                                    authenticated = true,
                                    secret = SECRET,
                                    code = CODE,
                                ).asSuccess(),
                            ),
                        ),
                )
            val fixture = repositoryFixture()
            val coordinator =
                JellyfinQuickConnectCoordinator(
                    serverRepository = fixture.repository,
                    remoteFactory =
                        JellyfinQuickConnectRemoteFactory { baseUrl, identity ->
                            assertEquals("https://media.example", baseUrl)
                            assertEquals("0.15.0", identity.appVersion)
                            assertEquals("jellystack-generated-id", identity.deviceId)
                            remote
                        },
                    idGenerator = { "generated-id" },
                )

            val states =
                coordinator
                    .connect(
                        JellyfinQuickConnectInput(
                            name = "Media",
                            baseUrl = "https://media.example/",
                            appVersion = "0.15.0",
                        ),
                    ).toList()

            assertIs<JellyfinQuickConnectState.Starting>(states.first())
            assertEquals(CODE, assertIs<JellyfinQuickConnectState.Waiting>(states[1]).session.code)
            val connected = assertIs<JellyfinQuickConnectState.Connected>(states.last())
            assertEquals("dummy-access-token", (connected.server.credentials as StoredCredential.Jellyfin).accessToken)
            assertEquals(1, remote.authenticateCalls)
            assertEquals(1, fixture.store.upsertCount)
            assertNull(fixture.vault.readJellyfinPassword(connected.server.id))
        }

    @Test
    fun pollsEveryFiveSecondsAndStopsAfterThreeTransportErrors() =
        runTest {
            val remote =
                FakeQuickConnectRemote(
                    pollResults =
                        ArrayDeque(
                            listOf(
                                Result.failure(IllegalStateException("offline-1")),
                                Result.failure(IllegalStateException("offline-2")),
                                Result.failure(IllegalStateException("offline-3")),
                            ),
                        ),
                )
            val coordinator = coordinator(remote)
            val states = mutableListOf<JellyfinQuickConnectState>()
            val job =
                launch {
                    coordinator
                        .connect(input())
                        .toList(states)
                }

            runCurrent()
            assertEquals(0, remote.pollCalls)
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(0, remote.pollCalls)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, remote.pollCalls)
            advanceTimeBy(10_000)
            job.join()

            assertEquals(3, remote.pollCalls)
            assertEquals(
                JellyfinQuickConnectError.TRANSPORT,
                assertIs<JellyfinQuickConnectState.Failed>(states.last()).error,
            )
            assertEquals(1, remote.closeCalls)
        }

    @Test
    fun successfulPollResetsTransportFailureCounter() =
        runTest {
            val waiting =
                JellyfinQuickConnectSessionDto(
                    authenticated = false,
                    secret = SECRET,
                    code = CODE,
                )
            val remote =
                FakeQuickConnectRemote(
                    pollResults =
                        ArrayDeque(
                            listOf(
                                Result.failure(IllegalStateException("offline-1")),
                                waiting.asSuccess(),
                                Result.failure(IllegalStateException("offline-2")),
                                waiting.asSuccess(),
                                JellyfinQuickConnectSessionDto(
                                    authenticated = true,
                                    secret = SECRET,
                                    code = CODE,
                                ).asSuccess(),
                            ),
                        ),
                )

            val states = coordinator(remote).connect(input()).toList()

            assertIs<JellyfinQuickConnectState.Connected>(states.last())
            assertEquals(5, remote.pollCalls)
            assertEquals(1, remote.authenticateCalls)
        }

    @Test
    fun waitingSessionExpiresAfterTenMinutes() =
        runTest {
            val remote = FakeQuickConnectRemote()
            val states = coordinator(remote).connect(input()).toList()

            assertEquals(120, remote.pollCalls)
            assertEquals(
                JellyfinQuickConnectError.EXPIRED,
                assertIs<JellyfinQuickConnectState.Failed>(states.last()).error,
            )
            assertEquals(600_000, states.waitingSession().expiresAt.toEpochMilliseconds())
        }

    @Test
    fun disabledAndExpiredServersHaveDistinctErrors() =
        runTest {
            val disabled = FakeQuickConnectRemote(enabled = false)
            val disabledStates = coordinator(disabled).connect(input()).toList()
            assertEquals(
                JellyfinQuickConnectError.DISABLED,
                assertIs<JellyfinQuickConnectState.Failed>(disabledStates.last()).error,
            )

            val unauthorized =
                FakeQuickConnectRemote(
                    enabledFailure = JellyfinQuickConnectHttpException(401),
                )
            val unauthorizedStates = coordinator(unauthorized).connect(input()).toList()
            assertEquals(
                JellyfinQuickConnectError.DISABLED,
                assertIs<JellyfinQuickConnectState.Failed>(unauthorizedStates.last()).error,
            )

            val expired =
                FakeQuickConnectRemote(
                    pollResults =
                        ArrayDeque(
                            listOf(Result.failure(JellyfinQuickConnectHttpException(404))),
                        ),
                )
            val expiredStates = coordinator(expired).connect(input()).toList()
            assertEquals(
                JellyfinQuickConnectError.EXPIRED,
                assertIs<JellyfinQuickConnectState.Failed>(expiredStates.last()).error,
            )
        }

    @Test
    fun cancellingFlowStopsPollingAndClosesRemote() =
        runTest {
            val remote = FakeQuickConnectRemote()
            val coordinator = coordinator(remote)
            val states = mutableListOf<JellyfinQuickConnectState>()
            val job = launch { coordinator.connect(input()).toList(states) }

            runCurrent()
            assertEquals(CODE, states.waitingSession().code)
            job.cancelAndJoin()

            assertEquals(0, remote.pollCalls)
            assertEquals(0, remote.authenticateCalls)
            assertEquals(1, remote.closeCalls)
        }

    private fun coordinator(remote: FakeQuickConnectRemote): JellyfinQuickConnectCoordinator =
        JellyfinQuickConnectCoordinator(
            serverRepository = repositoryFixture().repository,
            remoteFactory = JellyfinQuickConnectRemoteFactory { _, _ -> remote },
            clock = FixedZeroClock,
            idGenerator = { "generated-id" },
        )

    private fun input() =
        JellyfinQuickConnectInput(
            name = "Media",
            baseUrl = "https://media.example",
            appVersion = "0.15.0",
        )

    private fun List<JellyfinQuickConnectState>.waitingSession(): JellyfinQuickConnectSession =
        filterIsInstance<JellyfinQuickConnectState.Waiting>().single().session

    private fun repositoryFixture(): RepositoryFixture {
        val store = CountingServerStore()
        val vault = ServerCredentialVault(FakeSecureStore())
        val repository =
            ServerRepository(
                store = store,
                connectivity = ServerConnectivity { error("Password connectivity must not be used") },
                credentialVault = vault,
                activeServerPreferences = ActiveServerPreferenceRepository(InMemorySettings()),
                idGenerator = { "server-id" },
            )
        return RepositoryFixture(repository, store, vault)
    }

    private data class RepositoryFixture(
        val repository: ServerRepository,
        val store: CountingServerStore,
        val vault: ServerCredentialVault,
    )

    private class FakeQuickConnectRemote(
        private val enabled: Boolean = true,
        private val enabledFailure: Throwable? = null,
        private val pollResults: ArrayDeque<Result<JellyfinQuickConnectSessionDto>> = ArrayDeque(),
    ) : JellyfinQuickConnectRemote {
        var authenticateCalls = 0
        var pollCalls = 0
        var closeCalls = 0

        override suspend fun isEnabled(): Boolean {
            enabledFailure?.let { throw it }
            return enabled
        }

        override suspend fun initiate(): JellyfinQuickConnectSessionDto =
            JellyfinQuickConnectSessionDto(
                authenticated = false,
                secret = SECRET,
                code = CODE,
            )

        override suspend fun poll(secret: String): JellyfinQuickConnectSessionDto {
            assertEquals(SECRET, secret)
            pollCalls += 1
            return if (pollResults.isEmpty()) {
                JellyfinQuickConnectSessionDto(
                    authenticated = false,
                    secret = SECRET,
                    code = CODE,
                )
            } else {
                pollResults.removeFirst().getOrThrow()
            }
        }

        override suspend fun authenticate(secret: String): AuthenticateByNameResponse {
            assertEquals(SECRET, secret)
            authenticateCalls += 1
            return AuthenticateByNameResponse(
                accessToken = "dummy-access-token",
                user = AuthenticateByNameUser(id = "user-id", name = "Alice"),
                serverId = "jellyfin-server",
            )
        }

        override fun close() {
            closeCalls += 1
        }
    }

    private class CountingServerStore : ServerStore {
        private val records = linkedMapOf<String, ServerRecord>()
        var upsertCount = 0

        override suspend fun list(): List<ServerRecord> = records.values.toList()

        override suspend fun findByTypeAndUrl(
            type: ServerType,
            baseUrl: String,
        ): ServerRecord? = records.values.firstOrNull { it.type == type && it.baseUrl == baseUrl }

        override suspend fun get(id: String): ServerRecord? = records[id]

        override suspend fun upsert(record: ServerRecord) {
            upsertCount += 1
            records[record.id] = record
        }

        override suspend fun delete(id: String) {
            records.remove(id)
        }
    }

    private companion object {
        const val SECRET = "dummy-quick-connect-secret"
        const val CODE = "123456"
    }
}

private object FixedZeroClock : kotlinx.datetime.Clock {
    override fun now(): kotlinx.datetime.Instant = kotlinx.datetime.Instant.fromEpochMilliseconds(0)
}

private fun JellyfinQuickConnectSessionDto.asSuccess(): Result<JellyfinQuickConnectSessionDto> = Result.success(this)
