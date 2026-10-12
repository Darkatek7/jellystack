package dev.jellystack.core.profile

import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.ServerType

/** The connections one household profile uses, resolved from its binding instead of "first server of a type". */
data class ProfileConnections(
    val jellyfin: ManagedServer?,
    val seerr: ManagedServer?,
    /** Seerr record the profile may sign in to again in place; null when another profile shares it. */
    val seerrReconnectId: String?,
    /** Seerr server whose URL prefills the connect dialog: the profile's own, else the household's latest. */
    val seerrPrefill: ManagedServer?,
) {
    val servers: List<ManagedServer> = listOfNotNull(jellyfin, seerr)
}

fun resolveProfileConnections(
    profileId: String,
    bindings: List<ProfileConnectionBinding>,
    servers: List<ManagedServer>,
): ProfileConnections {
    val binding = bindings.firstOrNull { it.profileId == profileId }
    val jellyfin = servers.firstOrNull { it.id == binding?.jellyfinConnectionId && it.type == ServerType.JELLYFIN }
    val seerr = servers.firstOrNull { it.id == binding?.seerrConnectionId && it.type == ServerType.JELLYSEERR }
    val sharedSeerr = seerr != null && bindings.any { it.profileId != profileId && it.seerrConnectionId == seerr.id }
    return ProfileConnections(
        jellyfin = jellyfin,
        seerr = seerr,
        seerrReconnectId = seerr?.id?.takeUnless { sharedSeerr },
        seerrPrefill = seerr ?: servers.latestOf(ServerType.JELLYSEERR),
    )
}

/**
 * Jellyfin server that prefills a new profile's sign-in: the one the most recently active profile uses,
 * otherwise the most recently updated one, so a second household member does not retype the URL.
 */
fun newProfileJellyfinPrefill(
    profiles: List<HouseholdProfile>,
    bindings: List<ProfileConnectionBinding>,
    servers: List<ManagedServer>,
): ManagedServer? {
    val recentProfileId = profiles.maxByOrNull { it.lastActiveAt ?: it.createdAt }?.id
    val recentConnectionId = bindings.firstOrNull { it.profileId == recentProfileId }?.jellyfinConnectionId
    return servers.firstOrNull { it.id == recentConnectionId && it.type == ServerType.JELLYFIN }
        ?: servers.latestOf(ServerType.JELLYFIN)
}

private fun List<ManagedServer>.latestOf(type: ServerType): ManagedServer? = filter { it.type == type }.maxByOrNull { it.updatedAt }
