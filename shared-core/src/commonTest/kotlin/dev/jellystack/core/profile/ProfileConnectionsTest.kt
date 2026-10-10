package dev.jellystack.core.profile

import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileConnectionsTest {
    private val firstJellyfin = server("jellyfin-a", ServerType.JELLYFIN, updatedAt = 1)
    private val secondJellyfin = server("jellyfin-b", ServerType.JELLYFIN, updatedAt = 2)
    private val firstSeerr = server("seerr-a", ServerType.JELLYSEERR, updatedAt = 3)
    private val secondSeerr = server("seerr-b", ServerType.JELLYSEERR, updatedAt = 4)
    private val servers = listOf(firstJellyfin, secondJellyfin, firstSeerr, secondSeerr)

    @Test
    fun resolvesTheProfilesOwnServersInsteadOfTheFirstOfEachType() {
        val bindings =
            listOf(
                ProfileConnectionBinding("first", "jellyfin-a", "seerr-a"),
                ProfileConnectionBinding("second", "jellyfin-b", "seerr-b"),
            )

        val connections = resolveProfileConnections("second", bindings, servers)

        assertEquals(secondJellyfin, connections.jellyfin)
        assertEquals(secondSeerr, connections.seerr)
        assertEquals(listOf(secondJellyfin, secondSeerr), connections.servers)
        assertEquals("seerr-b", connections.seerrReconnectId)
    }

    @Test
    fun profileWithoutSeerrPrefillsTheHouseholdsLatestSeerrAndCreatesANewRecord() {
        val bindings =
            listOf(
                ProfileConnectionBinding("first", "jellyfin-a", "seerr-a"),
                ProfileConnectionBinding("second", "jellyfin-b"),
            )

        val connections = resolveProfileConnections("second", bindings, servers)

        assertNull(connections.seerr)
        assertNull(connections.seerrReconnectId)
        assertEquals(secondSeerr, connections.seerrPrefill)
        assertEquals(listOf(secondJellyfin), connections.servers)
    }

    @Test
    fun sharedSeerrRecordIsNotReauthenticatedInPlace() {
        val bindings =
            listOf(
                ProfileConnectionBinding("first", "jellyfin-a", "seerr-a"),
                ProfileConnectionBinding("second", "jellyfin-b", "seerr-a"),
            )

        val connections = resolveProfileConnections("first", bindings, servers)

        assertEquals(firstSeerr, connections.seerr)
        assertNull(connections.seerrReconnectId)
        assertEquals(firstSeerr, connections.seerrPrefill)
    }

    @Test
    fun bindingToAMissingOrMistypedServerResolvesToNothing() {
        val bindings = listOf(ProfileConnectionBinding("first", "seerr-a", "missing"))

        val connections = resolveProfileConnections("first", bindings, servers)

        assertNull(connections.jellyfin)
        assertNull(connections.seerr)
        assertEquals(emptyList(), connections.servers)
    }

    @Test
    fun newProfilePrefillUsesTheMostRecentlyActiveProfilesJellyfin() {
        val profiles =
            listOf(
                profile("first", lastActiveAt = 10),
                profile("second", lastActiveAt = 5),
            )
        val bindings =
            listOf(
                ProfileConnectionBinding("first", "jellyfin-a"),
                ProfileConnectionBinding("second", "jellyfin-b"),
            )

        assertEquals(firstJellyfin, newProfileJellyfinPrefill(profiles, bindings, servers))
    }

    @Test
    fun newProfilePrefillFallsBackToTheLatestJellyfinServer() {
        assertEquals(secondJellyfin, newProfileJellyfinPrefill(emptyList(), emptyList(), servers))
        assertNull(newProfileJellyfinPrefill(emptyList(), emptyList(), listOf(firstSeerr)))
    }

    private fun server(
        id: String,
        type: ServerType,
        updatedAt: Long,
    ) = ManagedServer(
        id = id,
        type = type,
        name = id,
        baseUrl = "https://$id.example.com",
        credentials =
            if (type == ServerType.JELLYFIN) {
                StoredCredential.Jellyfin("user", null, "token", "user-id")
            } else {
                StoredCredential.ApiKey("key", "1")
            },
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )

    private fun profile(
        id: String,
        lastActiveAt: Long,
    ) = HouseholdProfile(
        id = id,
        displayName = id,
        avatarSeed = id,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        lastActiveAt = Instant.fromEpochMilliseconds(lastActiveAt),
    )
}
