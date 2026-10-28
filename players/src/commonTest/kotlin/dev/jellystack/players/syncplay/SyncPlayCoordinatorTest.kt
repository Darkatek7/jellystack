package dev.jellystack.players.syncplay

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinSyncPlayException
import dev.jellystack.network.jellyfin.JellyfinSyncPlayFailure
import dev.jellystack.players.PlaybackController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPlayCoordinatorTest {
    @Test
    fun disabledAccessDoesNotContactJellyfin() =
        runTest {
            var environmentCalls = 0
            val coordinator =
                SyncPlayCoordinator(
                    environmentProvider =
                        JellyfinEnvironmentProvider {
                            environmentCalls += 1
                            null
                        },
                    playbackController = PlaybackController(scope = backgroundScope),
                    playItem = { _, _ -> },
                    scope = this,
                )

            coordinator.updateAccess(JellyfinSyncPlayAccess.NONE)
            coordinator.refresh()
            advanceUntilIdle()

            assertEquals(0, environmentCalls)
            assertFalse(coordinator.state.value.canJoin)
            assertEquals(null, coordinator.state.value.error)
            coordinator.close()
        }

    @Test
    fun joinOnlyAccessCannotCreateGroups() =
        runTest {
            var environmentCalls = 0
            val coordinator =
                SyncPlayCoordinator(
                    environmentProvider =
                        JellyfinEnvironmentProvider {
                            environmentCalls += 1
                            null
                        },
                    playbackController = PlaybackController(scope = backgroundScope),
                    playItem = { _, _ -> },
                    scope = this,
                )

            coordinator.updateAccess(JellyfinSyncPlayAccess.JOIN_GROUPS)
            coordinator.createGroup("Not allowed")
            advanceUntilIdle()

            assertEquals(0, environmentCalls)
            assertFalse(coordinator.state.value.canCreate)
            assertEquals(SyncPlayErrorCode.ACCESS_DENIED, coordinator.state.value.error)
            coordinator.close()
        }

    @Test
    fun forbiddenRefreshPublishesTypedErrorAndRefreshesCapabilities() =
        runTest {
            var accessRefreshes = 0
            val coordinator =
                SyncPlayCoordinator(
                    environmentProvider =
                        JellyfinEnvironmentProvider {
                            throw JellyfinSyncPlayException(
                                failure = JellyfinSyncPlayFailure.ACCESS_DENIED,
                                statusCode = 403,
                            )
                        },
                    playbackController = PlaybackController(scope = backgroundScope),
                    playItem = { _, _ -> },
                    onAccessDenied = { accessRefreshes += 1 },
                    scope = this,
                )

            coordinator.updateAccess(JellyfinSyncPlayAccess.CREATE_AND_JOIN_GROUPS)
            coordinator.refresh()
            advanceUntilIdle()

            assertEquals(SyncPlayErrorCode.ACCESS_DENIED, coordinator.state.value.error)
            assertEquals(1, accessRefreshes)
            coordinator.close()
        }

    private fun testEnvironment(token: String = "token") =
        JellyfinEnvironment(
            serverKey = "server",
            baseUrl = "https://media.example",
            accessToken = token,
            userId = "user",
            deviceId = "device",
            deviceName = "TV",
        )

    private class SocketHarness {
        val connections = mutableListOf<Channel<JsonObject>>()
        val connectTimes = mutableListOf<Long>()

        fun open(now: Long): Flow<JsonObject> {
            connectTimes += now
            return Channel<JsonObject>(Channel.UNLIMITED).also(connections::add).receiveAsFlow()
        }
    }

    private fun TestScope.newCoordinator(
        sockets: SocketHarness,
        environment: () -> JellyfinEnvironment? = { testEnvironment() },
    ): Pair<SyncPlayCoordinator, HttpClient> {
        val engine =
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/SyncPlay/List" ->
                        respond(
                            content = ByteReadChannel(GROUPS_JSON),
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    else -> respondOk()
                }
            }
        val client = NetworkClientFactory.create(ClientConfig(engine = engine, maxRetries = 0))
        val coordinator =
            SyncPlayCoordinator(
                environmentProvider = JellyfinEnvironmentProvider { environment() },
                playbackController = PlaybackController(scope = backgroundScope),
                playItem = { _, _ -> },
                client = client,
                scope = this,
                socketEvents = { sockets.open(testScheduler.currentTime) },
            )
        coordinator.updateAccess(JellyfinSyncPlayAccess.CREATE_AND_JOIN_GROUPS)
        return coordinator to client
    }

    /**
     * The mock engine answers on a real thread, so virtual time alone does not finish a request. Lets the test
     * dispatcher run while real time passes, until [condition] holds; the backoff timers are not advanced.
     */
    private suspend fun TestScope.awaitRequests(condition: () -> Boolean) {
        repeat(400) {
            runCurrent()
            if (condition()) return
            withContext(Dispatchers.Default) { delay(5) }
        }
        error("request did not finish")
    }

    private val group = SyncPlayGroup(id = "group", name = "Movie night", state = "Idle", participants = emptyList())

    @Test
    fun droppedSocketReconnectsWithBackoffWhileAGroupIsJoined() =
        runTest {
            val sockets = SocketHarness()
            val (coordinator, client) = newCoordinator(sockets)
            coordinator.joinGroup(group)
            awaitRequests { coordinator.state.value.currentGroup != null }
            assertEquals(1, sockets.connections.size)

            sockets.connections[0].close(IllegalStateException("wifi lost"))
            runCurrent()
            assertFalse(coordinator.state.value.connected)
            assertEquals(1, sockets.connections.size)

            advanceTimeBy(999)
            runCurrent()
            assertEquals(1, sockets.connections.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, sockets.connections.size)

            // The second attempt fails too without ever delivering a message: the wait doubles.
            val failedAt = testScheduler.currentTime
            sockets.connections[1].close(IllegalStateException("still down"))
            runCurrent()
            advanceTimeBy(1_999)
            runCurrent()
            assertEquals(2, sockets.connections.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(3, sockets.connections.size)
            assertEquals(failedAt + 2_000, sockets.connectTimes[2])

            coordinator.close()
            client.close()
        }

    @Test
    fun aMessageOnTheNewSocketResetsTheBackoffAndRefreshesTheGroup() =
        runTest {
            val sockets = SocketHarness()
            val (coordinator, client) = newCoordinator(sockets)
            coordinator.joinGroup(group)
            awaitRequests { coordinator.state.value.currentGroup != null }
            sockets.connections[0].close(IllegalStateException("drop"))
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, sockets.connections.size)

            sockets.connections[1].trySend(buildJsonObject { put("MessageType", "ForceKeepAlive") })
            awaitRequests { coordinator.state.value.connected }
            assertTrue(coordinator.state.value.connected)
            assertEquals(null, coordinator.state.value.error)

            sockets.connections[1].close(IllegalStateException("drop again"))
            runCurrent()
            val droppedAt = testScheduler.currentTime
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(3, sockets.connections.size)
            assertEquals(droppedAt + 1_000, sockets.connectTimes[2])
            coordinator.close()
            client.close()
        }

    @Test
    fun leavingTheGroupStopsReconnecting() =
        runTest {
            val sockets = SocketHarness()
            val (coordinator, client) = newCoordinator(sockets)
            coordinator.joinGroup(group)
            awaitRequests { coordinator.state.value.currentGroup != null }
            coordinator.leaveGroup()
            awaitRequests {
                coordinator.state.value.groups
                    .isNotEmpty()
            }
            assertNull(coordinator.state.value.currentGroup)

            sockets.connections[0].close(IllegalStateException("drop"))
            runCurrent()
            advanceTimeBy(120_000)
            runCurrent()

            assertEquals(1, sockets.connections.size)
            coordinator.close()
            client.close()
        }

    @Test
    fun socketThatEndsWithoutAGroupIsNotReopenedUntilTheNextGroupAction() =
        runTest {
            val sockets = SocketHarness()
            val (coordinator, client) = newCoordinator(sockets)
            coordinator.refresh()
            awaitRequests { coordinator.state.value.connected }
            assertEquals(1, sockets.connections.size)

            sockets.connections[0].close(IllegalStateException("idle drop"))
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(1, sockets.connections.size)

            coordinator.joinGroup(group)
            awaitRequests { coordinator.state.value.currentGroup != null }
            assertEquals(2, sockets.connections.size)
            coordinator.close()
            client.close()
        }

    @Test
    fun reconnectStopsWhenTheEnvironmentChanged() =
        runTest {
            val sockets = SocketHarness()
            var current: JellyfinEnvironment? = testEnvironment()
            val (coordinator, client) = newCoordinator(sockets) { current }
            coordinator.joinGroup(group)
            awaitRequests { coordinator.state.value.currentGroup != null }
            sockets.connections[0].close(IllegalStateException("drop"))
            runCurrent()

            current = testEnvironment().copy(serverKey = "other-server")
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(1, sockets.connections.size)
            coordinator.close()
            client.close()
        }

    @Test
    fun backoffDoublesFromOneSecondAndCapsAtThirtySeconds() {
        val schedule = (0..8).map(SyncPlayReconnectBackoff::delayMillis)

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L), schedule)
    }

    private companion object {
        const val GROUPS_JSON = """[{"GroupId":"group","GroupName":"Movie night","State":"Idle","Participants":[]}]"""
    }
}
