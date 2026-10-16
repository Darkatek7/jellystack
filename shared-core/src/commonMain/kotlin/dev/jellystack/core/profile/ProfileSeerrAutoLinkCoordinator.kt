package dev.jellystack.core.profile

import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.SeerrConnectionResult
import dev.jellystack.core.server.SeerrServerInput
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Adds a household member after they signed in to Jellyfin and signs them into the household's Seerr with their
 * own Jellyfin account.
 *
 * Seerr is linked only when the profile is created, never on activation: a profile whose owner later removed Seerr
 * must stay unlinked. A failed link is silent because the profile can still connect Seerr from settings.
 */
class ProfileSeerrAutoLinkCoordinator(
    private val profileRepository: HouseholdProfileRepository,
    private val serverRepository: ServerRepository,
    private val connectionCoordinator: ServerConnectionCoordinator,
) {
    /**
     * Returns the profile already bound to [jellyfin] (the account was added before), otherwise creates one for it
     * and links the household's Seerr.
     */
    suspend fun openOrCreateProfile(
        jellyfin: ManagedServer,
        appVersion: String,
    ): HouseholdProfile {
        profileRepository.profileForJellyfinConnection(jellyfin.id)?.let { return it }
        val username = (jellyfin.credentials as? StoredCredential.Jellyfin)?.username
        val profile =
            profileRepository.createProfile(
                displayName = username?.takeIf(String::isNotBlank) ?: jellyfin.name,
                jellyfinConnectionId = jellyfin.id,
            )
        linkSeerr(profile.id, appVersion)
        return profile
    }

    /** Returns the connection id now bound to the profile, or null when nothing was linked. */
    suspend fun linkSeerr(
        profileId: String,
        appVersion: String,
    ): String? {
        val binding = profileRepository.binding(profileId) ?: return null
        if (binding.seerrConnectionId != null) return null
        val connections =
            resolveProfileConnections(
                profileId = profileId,
                bindings = profileRepository.observeBindings().first(),
                servers = serverRepository.currentServers(),
            )
        val jellyfin = connections.jellyfin?.takeIf { it.type == ServerType.JELLYFIN } ?: return null
        val householdSeerr = connections.seerrPrefill ?: return null
        val result =
            try {
                connectionCoordinator.connectSeerrAutomatically(
                    SeerrServerInput(
                        name = householdSeerr.name,
                        baseUrl = householdSeerr.baseUrl,
                        appVersion = appVersion,
                    ),
                    jellyfin,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                return null
            }
        val connected = (result as? SeerrConnectionResult.Connected)?.server ?: return null
        profileRepository.bindSeerr(profileId, connected.id)
        return connected.id
    }
}
