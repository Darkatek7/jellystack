@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.jellystack.core.profile.ProfilePinState
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/** Keeps the profile gate in step with the stores; every effect re-keys on exactly what it reads. */
@Composable
internal fun TvProfileHostEffects(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    TvLegacyProfileEffect(actions, inputs)
    TvProfileAvatarEffect(actions, inputs)
    TvProfilePinRequiredEffect(actions, inputs)
    TvProfileSelectionValidityEffect(actions.state, inputs)
    TvProfileInitialStateEffect(actions, inputs)
    TvProfilePinClockEffect(actions.state)
    TvProfileActivationEffect(actions, inputs)
    TvProfileHostBackHandler(actions.state)
}

@Composable
private fun TvLegacyProfileEffect(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    val profileRepository = actions.services.profiles
    val servers = inputs.servers
    LaunchedEffect(servers.map { it.id to it.updatedAt }) {
        if (servers.any { it.type == ServerType.JELLYFIN }) {
            profileRepository.ensureLegacyDefaultProfile()
            profileRepository.repairLegacySingleProfileSeerrBinding(
                servers.filter { it.type == ServerType.JELLYSEERR }.map { it.id },
            )
            val profile = profileRepository.listProfiles().singleOrNull()
            val binding = profile?.let { profileRepository.binding(it.id) }
            val jellyfin = binding?.let { exact -> servers.firstOrNull { it.id == exact.jellyfinConnectionId } }
            val credential = jellyfin?.credentials as? StoredCredential.Jellyfin
            if (jellyfin != null && credential != null) {
                profileRepository.repairLegacySingleProfileIdentity(
                    jellyfinConnectionId = jellyfin.id,
                    displayName = credential.username,
                    avatarSeed = credential.userId,
                )
            }
        }
        actions.state.legacyChecked = true
    }
}

@Composable
private fun TvProfileAvatarEffect(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    val profileRepository = actions.services.profiles
    val profiles = inputs.profiles
    val servers = inputs.servers
    LaunchedEffect(profiles.map { it.id to it.updatedAt }, servers.map { it.id to it.updatedAt }) {
        actions.state.profileAvatarUrls =
            buildMap {
                profiles.forEach { profile ->
                    val binding = profileRepository.binding(profile.id) ?: return@forEach
                    val server = servers.firstOrNull { it.id == binding.jellyfinConnectionId } ?: return@forEach
                    val credential = server.credentials as? StoredCredential.Jellyfin ?: return@forEach
                    jellyfinUserImageUrl(server.baseUrl, credential.accessToken, credential.userId)?.let { url ->
                        put(profile.id, url)
                    }
                }
            }
    }
}

@Composable
private fun TvProfilePinRequiredEffect(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    val profiles = inputs.profiles
    LaunchedEffect(profiles.map { it.id }, actions.state.profilePinRevision) {
        actions.state.profilePinRequired =
            profiles.associate { profile ->
                profile.id to (actions.services.pins.state(profile.id) !is ProfilePinState.NotConfigured)
            }
    }
}

@Composable
private fun TvProfileSelectionValidityEffect(
    state: TvProfileHostState,
    inputs: TvProfileHostInputs,
) {
    val loadedProfiles = inputs.loadedProfiles
    LaunchedEffect(loadedProfiles?.map { it.id }, state.selectedProfileId) {
        val selectedId = state.selectedProfileId
        if (loadedProfiles != null && selectedId != null && inputs.profiles.none { it.id == selectedId }) {
            state.selectedProfileId = null
            state.pickerVisible = true
        }
    }
}

@Composable
private fun TvProfileInitialStateEffect(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    val state = actions.state
    val pins = actions.services.pins
    LaunchedEffect(state.legacyChecked, inputs.loadedProfiles, state.initialized) {
        val loaded = inputs.loadedProfiles
        if (!state.legacyChecked || loaded == null || state.initialized) return@LaunchedEffect
        val pinProtected = loaded.filter { pins.state(it.id) !is ProfilePinState.NotConfigured }
        when (
            val initial =
                initialTvProfileState(
                    profiles = loaded,
                    coldLaunch = inputs.coldLaunch,
                    pickerWasVisible = state.pickerVisible,
                    rememberedProfileId = actions.services.activeProfiles.profileId.value,
                    generation = state.generation,
                ).lockedOnColdLaunch(inputs.coldLaunch, pinProtected.mapTo(HashSet()) { it.id })
        ) {
            is TvProfileState.Content -> state.selectedProfileId = initial.profile.id
            is TvProfileState.Picker -> {
                actions.services.activeProfiles.clear()
                state.pickerVisible = true
            }
            is TvProfileState.PinEntry -> {
                actions.services.activeProfiles.clear()
                actions.openProfile(initial.profile)
            }
            else -> Unit
        }
        state.initialized = true
    }
}

@Composable
private fun TvProfilePinClockEffect(state: TvProfileHostState) {
    LaunchedEffect(state.pinLockedUntilMillis, state.pinProfile?.id) {
        val deadline = state.pinLockedUntilMillis ?: return@LaunchedEffect
        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            delay(1_000)
            state.pinClockTick += 1L
        }
    }
}

@Composable
private fun TvProfileActivationEffect(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
) {
    val state = actions.state
    val sessionRecovery = inputs.sessionRecovery
    LaunchedEffect(
        state.initialized,
        inputs.loadedProfiles == null,
        state.pickerVisible,
        state.selectedProfileId,
        state.activatedProfileId,
        state.reconnectProfile?.id,
        sessionRecovery.requiresSignIn(state.selectedProfileId),
    ) {
        val profile = inputs.profiles.firstOrNull { it.id == state.selectedProfileId } ?: return@LaunchedEffect
        val decision =
            decideTvActivation(
                TvActivationInputs(
                    initialized = state.initialized,
                    pickerVisible = state.pickerVisible,
                    selectedProfileId = profile.id,
                    activatedProfileId = state.activatedProfileId,
                    switchingProfile = state.switchingProfile != null,
                    reconnectPending = state.reconnectProfile != null,
                    signInRequired = sessionRecovery.requiresSignIn(profile.id),
                ),
            )
        when (decision) {
            TvActivationDecision.ACTIVATE -> actions.beginActivation(profile)
            TvActivationDecision.SHOW_RECONNECT -> actions.showReconnect(profile)
            TvActivationDecision.WAIT -> Unit
        }
    }
}

/** Back steps out of the gate's secondary screens before the app's own back handling applies. */
@Composable
private fun TvProfileHostBackHandler(state: TvProfileHostState) {
    BackHandler(
        enabled =
            state.addProfile ||
                state.reconnectProfile != null ||
                state.pinProfile != null ||
                state.configuringPinProfile != null ||
                state.profileManagementVisible,
    ) {
        when {
            state.addProfile -> state.addProfile = false
            state.reconnectProfile != null -> state.dismissReconnect()
            state.configuringPinProfile != null -> {
                state.configuringPinProfile = null
                state.firstConfiguredPin = null
                state.configurationPin = ""
                state.configurationCanRemove = false
            }
            state.pinProfile != null -> state.cancelPinEntry()
            state.managedProfileId != null -> state.managedProfileId = null
            state.profileManagementVisible -> state.profileManagementVisible = false
        }
    }
}
