package dev.jellystack.players.syncplay

import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.network.jellyfin.JellyfinSyncPlayException
import dev.jellystack.network.jellyfin.JellyfinSyncPlayFailure
import dev.jellystack.players.PlaybackController
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncPlayCoordinatorTest {
    @Test
    fun disabledAccessDoesNotContactJellyfin() =
        runTest {
            var environmentCalls = 0
            val coordinator =
                SyncPlayCoordinator(
                    environmentProvider = JellyfinEnvironmentProvider { environmentCalls += 1; null },
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
                    environmentProvider = JellyfinEnvironmentProvider { environmentCalls += 1; null },
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
}
