package dev.jellystack.core.profile

import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSwitchCoordinatorTest {
    @Test
    fun switchRunsHooksInRequiredOrderAndPublishesActiveOnlyAfterRefresh() =
        runTest {
            val events = mutableListOf<String>()
            val coordinator = coordinator(events = events)

            assertEquals(ProfileSwitchResult.Activated(PROFILE_A, 1), coordinator.switchTo(PROFILE_A))

            assertEquals(
                listOf(
                    "unlock:a",
                    "stop-playback",
                    "stop-trailers",
                    "stop-syncplay",
                    "cancel:1",
                    "activate:a",
                    "clear",
                    "bootstrap:a:1",
                    "refresh:a:1",
                ),
                events,
            )
            assertEquals(ActiveProfileState.Active(PROFILE_A, 1), coordinator.state.value)
        }

    @Test
    fun staleGenerationCannotPublishDuringConcurrentSwitch() =
        runTest {
            val bootstrapStarted = CompletableDeferred<Unit>()
            val finishBootstrap = CompletableDeferred<Unit>()
            val coordinator =
                coordinator(
                    bootstrap = { profileId, _ ->
                        if (profileId == PROFILE_B) {
                            bootstrapStarted.complete(Unit)
                            finishBootstrap.await()
                        }
                    },
                )
            coordinator.switchTo(PROFILE_A)
            val oldGeneration = coordinator.generation

            val switching = async { coordinator.switchTo(PROFILE_B) }
            bootstrapStarted.await()

            assertIs<ActiveProfileState.Bootstrapping>(coordinator.state.value)
            assertFalse(coordinator.publishIfCurrent(oldGeneration) {})
            assertTrue(coordinator.publishIfCurrent(coordinator.generation) {})
            finishBootstrap.complete(Unit)
            assertIs<ProfileSwitchResult.Activated>(switching.await())
        }

    @Test
    fun expiredTargetStopsAtReconnectWithoutFallingBack() =
        runTest {
            val events = mutableListOf<String>()
            val coordinator = coordinator(events = events, authenticated = { it.profileId != PROFILE_B })
            coordinator.switchTo(PROFILE_A)
            events.clear()

            assertEquals(ProfileSwitchResult.Reconnect(PROFILE_B, 2), coordinator.switchTo(PROFILE_B))

            assertEquals(
                listOf("unlock:b", "stop-playback", "stop-trailers", "stop-syncplay", "cancel:2", "activate:b", "clear"),
                events,
            )
            assertEquals(ActiveProfileState.Reconnect(PROFILE_B, 2), coordinator.state.value)
        }

    @Test
    fun lockedTargetDoesNotStopCurrentPlaybackOrAdvanceGeneration() =
        runTest {
            val events = mutableListOf<String>()
            val coordinator =
                coordinator(
                    events = events,
                    unlock = { profileId ->
                        if (profileId ==
                            PROFILE_B
                        ) {
                            ProfileUnlockResult.Locked(Instant.fromEpochMilliseconds(40_000))
                        } else {
                            ProfileUnlockResult.Unlocked
                        }
                    },
                )
            coordinator.switchTo(PROFILE_A)
            events.clear()

            assertIs<ProfileSwitchResult.Locked>(coordinator.switchTo(PROFILE_B))
            assertEquals(listOf("unlock:b"), events)
            assertEquals(1, coordinator.generation)
            assertEquals(
                ActiveProfileState.Locked(
                    profileId = PROFILE_B,
                    until = Instant.fromEpochMilliseconds(40_000),
                    previousProfileId = PROFILE_A,
                ),
                coordinator.state.value,
            )
            coordinator.cancelUnlock()
            assertEquals(ActiveProfileState.Active(PROFILE_A, 1), coordinator.state.value)
        }

    @Test
    fun environmentsResolveOnlyExactActiveProfileBindings() =
        runTest {
            val state = kotlinx.coroutines.flow.MutableStateFlow<ActiveProfileState>(ActiveProfileState.Active(PROFILE_A, 7))
            val bindings =
                mapOf(
                    PROFILE_A to ProfileConnectionBinding(PROFILE_A, "jf-a", null),
                    PROFILE_B to ProfileConnectionBinding(PROFILE_B, "jf-b", "seerr-b"),
                )
            val servers =
                mapOf(
                    "jf-a" to jellyfinServer("jf-a", "user-a"),
                    "jf-b" to jellyfinServer("jf-b", "user-b"),
                    "seerr-b" to seerrServer("seerr-b", "22"),
                )
            val provider =
                ProfileEnvironmentProvider(
                    activeState = state,
                    bindingResolver = { bindings[it] },
                    serverResolver = { servers[it] },
                )

            assertEquals("jf-a", provider.jellyfin()?.serverKey)
            assertNull(provider.seerr())
            state.value = ActiveProfileState.Switching(PROFILE_A, PROFILE_B, 8)
            assertNull(provider.jellyfin())
            assertNull(provider.seerr())
            state.value = ActiveProfileState.Active(PROFILE_B, 8)
            assertEquals("user-b", provider.jellyfin()?.userId)
            assertEquals(22, provider.seerr()?.apiUserId)
        }

    private fun coordinator(
        events: MutableList<String> = mutableListOf(),
        unlock: suspend (String) -> ProfileUnlockResult = { ProfileUnlockResult.Unlocked },
        authenticated: suspend (ProfileConnectionBinding) -> Boolean = { true },
        bootstrap: suspend (String, Long) -> Unit = { profileId, generation ->
            events +=
                "bootstrap:${profileId.removePrefix("profile-")}:$generation"
        },
    ): ProfileSwitchCoordinator =
        ProfileSwitchCoordinator(
            bindingResolver = { profileId ->
                when (profileId) {
                    PROFILE_A -> ProfileConnectionBinding(PROFILE_A, "jf-a", "seerr-a")
                    PROFILE_B -> ProfileConnectionBinding(PROFILE_B, "jf-b", null)
                    else -> null
                }
            },
            unlock = { profileId ->
                events += "unlock:${profileId.removePrefix("profile-")}"
                unlock(profileId)
            },
            stopPlayback = { events += "stop-playback" },
            stopTrailers = { events += "stop-trailers" },
            stopSyncPlay = { events += "stop-syncplay" },
            cancelPreviousGeneration = { events += "cancel:$it" },
            activateConnections = { events += "activate:${it.profileId.removePrefix("profile-")}" },
            clearPresentationState = { events += "clear" },
            isAuthenticated = authenticated,
            bootstrapCaches = bootstrap,
            refresh = { profileId, generation -> events += "refresh:${profileId.removePrefix("profile-")}:$generation" },
        )

    private fun jellyfinServer(
        id: String,
        userId: String,
    ) = ManagedServer(
        id = id,
        type = ServerType.JELLYFIN,
        name = id,
        baseUrl = "https://jellyfin.example",
        credentials =
            StoredCredential.Jellyfin(
                username = "user",
                deviceId = "device",
                accessToken = "token-$userId",
                userId = userId,
            ),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private fun seerrServer(
        id: String,
        userId: String,
    ) = ManagedServer(
        id = id,
        type = ServerType.JELLYSEERR,
        name = id,
        baseUrl = "https://seerr.example",
        credentials = StoredCredential.ApiKey(apiKey = "api-key", userId = userId, sessionCookie = null),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private companion object {
        const val PROFILE_A = "profile-a"
        const val PROFILE_B = "profile-b"
    }
}
