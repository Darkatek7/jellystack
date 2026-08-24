package dev.jellystack.core.profile

class ProfileRemovalCoordinator(
    private val store: ProfileStore,
    private val preferences: ProfilePreferencesRepository,
    private val pins: ProfilePinRepository,
    private val removeLocalConnection: suspend (connectionId: String) -> Unit,
) {
    suspend fun remove(profileId: String) {
        require(profileId.isNotBlank())
        val removedBinding = store.getBinding(profileId)
        store.deleteProfile(profileId)
        preferences.delete(profileId)
        pins.remove(profileId)
        if (removedBinding == null) return

        val referencedConnectionIds =
            store
                .listProfiles()
                .mapNotNull { profile -> store.getBinding(profile.id) }
                .flatMap { binding -> listOfNotNull(binding.jellyfinConnectionId, binding.seerrConnectionId) }
                .toSet()
        listOfNotNull(removedBinding.jellyfinConnectionId, removedBinding.seerrConnectionId)
            .distinct()
            .filterNot(referencedConnectionIds::contains)
            .forEach { connectionId -> removeLocalConnection(connectionId) }
    }
}
