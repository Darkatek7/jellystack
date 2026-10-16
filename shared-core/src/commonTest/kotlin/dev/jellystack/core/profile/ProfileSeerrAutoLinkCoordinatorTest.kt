package dev.jellystack.core.profile

import dev.jellystack.core.jellyseerr.JellyseerrAuthRequest
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticationResult
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.security.FakeSecureStore
import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.ConnectivityResult
import dev.jellystack.core.server.CredentialInput
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerConnectivity
import dev.jellystack.core.server.ServerCredentialVault
import dev.jellystack.core.server.ServerRecord
import dev.jellystack.core.server.ServerRegistration
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerStore
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.core.testing.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProfileSeerrAutoLinkCoordinatorTest {
    @Test
    fun newProfileIsSignedInToTheHouseholdSeerrWithItsOwnAccount() =
        runTest {
            val fixture = fixture()
            val alice = fixture.addHouseholdMemberWithSeerr("alice")
            val bob = fixture.registerJellyfin("bob")

            val profile = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            val seerrId = assertNotNull(fixture.profiles.binding(profile.id)?.seerrConnectionId)
            val seerr = assertNotNull(fixture.servers.findServer(seerrId))
            assertEquals("https://requests.example", seerr.baseUrl)
            assertEquals("bob-key", (seerr.credentials as StoredCredential.ApiKey).apiKey)
            assertNotEquals(fixture.profiles.binding(alice.id)?.seerrConnectionId, seerrId)
            assertEquals(listOf("bob"), fixture.authenticator.usernames)
        }

    @Test
    fun sameSeerrAccountRefreshesTheStoredRecordInsteadOfAddingOne() =
        runTest {
            val fixture = fixture(seerrUserIds = mapOf("bob" to "7"))
            val storedSeerr = fixture.addStoredSeerr(apiKey = "stale-key", userId = "7")
            val bob = fixture.registerJellyfin("bob")

            val profile = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            assertEquals(storedSeerr.id, fixture.profiles.binding(profile.id)?.seerrConnectionId)
            assertEquals(1, fixture.servers.currentServers().count { it.type == ServerType.JELLYSEERR })
            val refreshed = fixture.servers.findServer(storedSeerr.id)?.credentials as StoredCredential.ApiKey
            assertEquals("bob-key", refreshed.apiKey)
        }

    @Test
    fun seerrSignInWithoutAUserIdNeverOverwritesAStoredRecord() =
        runTest {
            val fixture = fixture(reportsSeerrUserId = false)
            val anonymous =
                fixture.servers.register(
                    ServerRegistration(
                        type = ServerType.JELLYSEERR,
                        name = "Requests",
                        baseUrl = "https://requests.example",
                        credentials = CredentialInput.ApiKey(apiKey = "other-key", userId = null, sessionCookie = null),
                    ),
                )
            val bob = fixture.registerJellyfin("bob")

            val profile = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            assertNull(fixture.profiles.binding(profile.id)?.seerrConnectionId)
            assertEquals("other-key", (fixture.servers.findServer(anonymous.id)?.credentials as StoredCredential.ApiKey).apiKey)
        }

    @Test
    fun failedSeerrSignInStillCreatesTheProfileUnlinked() =
        runTest {
            val fixture = fixture(authenticationFailure = IllegalStateException("seerr offline"))
            fixture.addHouseholdMemberWithSeerr("alice")
            val bob = fixture.registerJellyfin("bob")

            val profile = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            assertEquals(ProfileConnectionBinding(profile.id, bob.id, null), fixture.profiles.binding(profile.id))
            assertEquals(1, fixture.servers.currentServers().count { it.type == ServerType.JELLYSEERR })
        }

    @Test
    fun householdWithoutSeerrCreatesTheProfileWithoutSigningIn() =
        runTest {
            val fixture = fixture()
            val bob = fixture.registerJellyfin("bob")

            val profile = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            assertNull(fixture.profiles.binding(profile.id)?.seerrConnectionId)
            assertEquals(emptyList(), fixture.authenticator.usernames)
        }

    @Test
    fun accountThatAlreadyHasAProfileOpensThatProfileAndLeavesItsSeerrAlone() =
        runTest {
            val fixture = fixture()
            fixture.addHouseholdMemberWithSeerr("alice")
            val bob = fixture.registerJellyfin("bob")
            val bobProfile = fixture.profiles.createProfile("bob", bob.id)

            val opened = fixture.coordinator.openOrCreateProfile(bob, APP_VERSION)

            assertEquals(bobProfile, opened)
            assertEquals(2, fixture.profiles.listProfiles().size)
            assertNull(fixture.profiles.binding(bobProfile.id)?.seerrConnectionId)
            assertEquals(emptyList(), fixture.authenticator.usernames)
        }

    private suspend fun Fixture.addHouseholdMemberWithSeerr(username: String): HouseholdProfile {
        val jellyfin = registerJellyfin(username)
        val seerr = addStoredSeerr(apiKey = "$username-key", userId = seerrUserIds.getValue(username))
        val profile = profiles.createProfile(username, jellyfin.id)
        profiles.bindSeerr(profile.id, seerr.id)
        return profile
    }

    private suspend fun Fixture.addStoredSeerr(
        apiKey: String,
        userId: String,
    ): ManagedServer =
        servers.register(
            ServerRegistration(
                type = ServerType.JELLYSEERR,
                name = "Requests",
                baseUrl = "https://requests.example",
                credentials = CredentialInput.ApiKey(apiKey = apiKey, userId = userId, sessionCookie = null),
            ),
        )

    private suspend fun Fixture.registerJellyfin(username: String): ManagedServer =
        servers.register(
            ServerRegistration(
                type = ServerType.JELLYFIN,
                name = "Media",
                baseUrl = "https://media.example",
                credentials = CredentialInput.Jellyfin(username = username, password = "$username-password"),
            ),
        )

    private fun fixture(
        seerrUserIds: Map<String, String> = mapOf("alice" to "1", "bob" to "2"),
        authenticationFailure: Throwable? = null,
        reportsSeerrUserId: Boolean = true,
    ): Fixture {
        var serverIds = 0
        var profileIds = 0
        val servers =
            ServerRepository(
                store = ListServerStore(),
                connectivity =
                    ServerConnectivity { registration ->
                        when (val credentials = registration.credentials) {
                            is CredentialInput.Jellyfin ->
                                ConnectivityResult.Success(
                                    "ok",
                                    StoredCredential.Jellyfin(
                                        username = credentials.username,
                                        deviceId = credentials.deviceId,
                                        accessToken = "${credentials.username}-token",
                                        userId = "${credentials.username}-id",
                                    ),
                                )
                            is CredentialInput.ApiKey ->
                                ConnectivityResult.Success(
                                    "ok",
                                    StoredCredential.ApiKey(credentials.apiKey, credentials.userId, credentials.sessionCookie),
                                )
                        }
                    },
                credentialVault = ServerCredentialVault(FakeSecureStore()),
                activeServerPreferences = ActiveServerPreferenceRepository(InMemorySettings()),
                clock = FixedProfileClock,
                idGenerator = { "server-${serverIds++}" },
            )
        val profiles =
            HouseholdProfileRepository(
                store = InMemoryProfileStore(),
                activeServerPreferences = ActiveServerPreferenceRepository(InMemorySettings()),
                clock = FixedProfileClock,
                idGenerator = { "profile-${profileIds++}" },
            )
        val authenticator = ScriptedAuthenticator(seerrUserIds, authenticationFailure, reportsSeerrUserId)
        return Fixture(
            servers = servers,
            profiles = profiles,
            authenticator = authenticator,
            seerrUserIds = seerrUserIds,
            coordinator =
                ProfileSeerrAutoLinkCoordinator(
                    profileRepository = profiles,
                    serverRepository = servers,
                    connectionCoordinator = ServerConnectionCoordinator(servers, authenticator),
                ),
        )
    }

    private class Fixture(
        val servers: ServerRepository,
        val profiles: HouseholdProfileRepository,
        val authenticator: ScriptedAuthenticator,
        val seerrUserIds: Map<String, String>,
        val coordinator: ProfileSeerrAutoLinkCoordinator,
    )

    private class ScriptedAuthenticator(
        private val seerrUserIds: Map<String, String>,
        private val failure: Throwable?,
        private val reportsUserId: Boolean,
    ) : JellyseerrAuthenticator() {
        val usernames = mutableListOf<String>()

        override suspend fun authenticate(request: JellyseerrAuthRequest): JellyseerrAuthenticationResult {
            val username = requireNotNull(request.username)
            usernames += username
            failure?.let { throw it }
            return JellyseerrAuthenticationResult(
                apiKey = "$username-key",
                userId = seerrUserIds.getValue(username).toInt().takeIf { reportsUserId },
                sessionCookie = null,
                hasValidatedSession = true,
            )
        }
    }

    private class ListServerStore : ServerStore {
        private val records = linkedMapOf<String, ServerRecord>()

        override suspend fun list(): List<ServerRecord> = records.values.toList()

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

    private companion object {
        const val APP_VERSION = "test"
    }
}
