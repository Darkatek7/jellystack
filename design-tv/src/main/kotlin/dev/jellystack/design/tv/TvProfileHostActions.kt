package dev.jellystack.design.tv

import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.ProfilePinRepository
import dev.jellystack.core.profile.ProfilePinResult
import dev.jellystack.core.profile.ProfilePinState
import dev.jellystack.core.profile.ProfileRemovalCoordinator
import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** The repositories the profile gate reads and writes. */
internal class TvProfileHostServices(
    val profiles: HouseholdProfileRepository,
    val servers: ServerRepository,
    val pins: ProfilePinRepository,
    val activeProfiles: ActiveProfileRepository,
    val extras: TvProfileHostExtraServices,
)

/** The repositories only activation and removal touch. */
internal class TvProfileHostExtraServices(
    val activeServerPreferences: ActiveServerPreferenceRepository,
    val removal: ProfileRemovalCoordinator,
)

/** What stops when the active profile changes or the gate is raised. */
internal class TvProfileHostPlayback(
    val stopPlayback: () -> Unit,
    val stopTrailerPreview: () -> Unit,
)

/** Profile selection, activation and sign-in recovery of the profile gate. */
internal class TvProfileHostActions(
    val state: TvProfileHostState,
    val services: TvProfileHostServices,
    val scope: CoroutineScope,
    private val sessionRecovery: TvSessionRecovery,
    private val playback: TvProfileHostPlayback,
) {
    fun beginActivation(profile: HouseholdProfile) {
        state.switchingProfile = profile
        state.pickerReturnProfileId = null
        state.pinProfile = null
        state.pinValue = ""
        scope.launch {
            playback.stopPlayback()
            playback.stopTrailerPreview()
            services.activeProfiles.clear()
            val binding = services.profiles.binding(profile.id)
            val jellyfin = binding?.let { services.servers.findServer(it.jellyfinConnectionId) }
            val serverUsable = jellyfin?.type == ServerType.JELLYFIN && !sessionRecovery.requiresSignIn(profile.id)
            if (binding == null || !serverUsable) {
                state.reconnectProfile = profile
                state.reconnectConnectionId = binding?.jellyfinConnectionId
                state.switchingProfile = null
                return@launch
            }
            services.servers.setActiveServer(ServerType.JELLYFIN, binding.jellyfinConnectionId)
            val seerrId = binding.seerrConnectionId
            if (seerrId == null) {
                services.extras.activeServerPreferences.setActiveServerId(ServerType.JELLYSEERR, null)
            } else if (services.servers.findServer(seerrId)?.type == ServerType.JELLYSEERR) {
                services.servers.setActiveServer(ServerType.JELLYSEERR, seerrId)
            } else {
                services.extras.activeServerPreferences.setActiveServerId(ServerType.JELLYSEERR, null)
            }
            services.activeProfiles.activate(profile.id)
            services.profiles.updateProfile(profile.copy(lastActiveAt = Clock.System.now()))
            state.generation += 1L
            state.selectedProfileId = profile.id
            state.activatedProfileId = profile.id
            state.pickerVisible = false
            state.profileManagementVisible = false
            state.managedProfileId = null
            state.reconnectProfile = null
            state.reconnectConnectionId = null
            state.switchingProfile = null
        }
    }

    fun showReconnect(profile: HouseholdProfile) {
        scope.launch {
            state.reconnectConnectionId = services.profiles.binding(profile.id)?.jellyfinConnectionId
            state.reconnectProfile = profile
        }
    }

    suspend fun openProfile(profile: HouseholdProfile) {
        when (val pinState = services.pins.state(profile.id)) {
            ProfilePinState.NotConfigured -> beginActivation(profile)
            ProfilePinState.Ready -> {
                state.pinProfile = profile
                state.pinValue = ""
                state.pinRemainingAttempts = null
                state.pinLockedUntilMillis = null
            }
            is ProfilePinState.Locked -> {
                state.pinProfile = profile
                state.pinValue = ""
                state.pinLockedUntilMillis = pinState.until.toEpochMilliseconds()
            }
        }
    }

    fun selectProfile(profile: HouseholdProfile) {
        state.pinForManagement = false
        scope.launch { openProfile(profile) }
    }

    fun openProfilePicker(userOpened: Boolean = true) {
        state.pickerReturnProfileId = tvPickerReturnProfileId(userOpened, state.activatedProfileId)
        playback.stopPlayback()
        playback.stopTrailerPreview()
        services.activeProfiles.clear()
        state.activatedProfileId = null
        state.pickerVisible = true
    }

    fun manageProfilePin(profile: HouseholdProfile) {
        scope.launch {
            when (val pinState = services.pins.state(profile.id)) {
                ProfilePinState.NotConfigured -> {
                    state.configuringPinProfile = profile
                    state.firstConfiguredPin = null
                    state.configurationPin = ""
                    state.configurationPinError = false
                    state.configurationCanRemove = false
                }
                ProfilePinState.Ready -> {
                    state.pinForManagement = true
                    state.pinProfile = profile
                    state.pinValue = ""
                    state.pinRemainingAttempts = null
                    state.pinLockedUntilMillis = null
                }
                is ProfilePinState.Locked -> {
                    state.pinForManagement = true
                    state.pinProfile = profile
                    state.pinValue = ""
                    state.pinLockedUntilMillis = pinState.until.toEpochMilliseconds()
                }
            }
        }
    }

    /** The signed-in app reported a rejected session: send the profile through the sign-in screen. */
    fun onAuthenticationExpired(profile: HouseholdProfile) {
        // Parallel reports start the recovery once; only a successful sign-in lifts the mark.
        if (sessionRecovery.markExpired(profile.id)) {
            playback.stopPlayback()
            playback.stopTrailerPreview()
            scope.launch {
                state.reconnectConnectionId = services.profiles.binding(profile.id)?.jellyfinConnectionId
                services.activeProfiles.clear()
                state.activatedProfileId = null
                state.reconnectProfile = profile
            }
        }
    }

    /** The profile signed in again: lift the sign-in mark (and a PIN recovery) and open its session. */
    fun onReconnected(profile: HouseholdProfile) {
        scope.launch {
            if (state.recoverPinProfileId == profile.id) {
                services.pins.recoverAfterReauthentication(profile.id) { exactProfileId ->
                    exactProfileId == profile.id
                }
                state.recoverPinProfileId = null
            }
            sessionRecovery.markSignedIn(profile.id)
            beginActivation(profile)
        }
    }

    /** The first account was connected: create its default profile and open it. */
    fun onFirstConnected() {
        scope.launch {
            services.profiles.ensureLegacyDefaultProfile()?.let(::beginActivation)
        }
    }

    fun confirmRemoveProfile(profile: HouseholdProfile) {
        scope.launch {
            val wasActive = profile.id == state.selectedProfileId
            if (wasActive) {
                playback.stopPlayback()
                playback.stopTrailerPreview()
            }
            services.extras.removal.remove(profile.id)
            state.profilePinRevision += 1L
            if (wasActive) {
                services.activeProfiles.clear()
                state.selectedProfileId = null
                state.activatedProfileId = null
            }
            state.removeProfile = null
            if (state.managedProfileId == profile.id) state.managedProfileId = null
            state.pickerVisible = true
        }
    }
}

/** PIN entry and PIN setup of the profile gate. */
internal class TvProfilePinActions(
    private val state: TvProfileHostState,
    private val pins: ProfilePinRepository,
    private val scope: CoroutineScope,
    private val host: TvProfileHostActions,
) {
    fun submitPin(profile: HouseholdProfile) {
        scope.launch {
            when (val result = pins.verify(profile.id, state.pinValue)) {
                ProfilePinResult.Unlocked -> {
                    if (state.pinForManagement) {
                        state.pinProfile = null
                        state.pinForManagement = false
                        state.configuringPinProfile = profile
                        state.firstConfiguredPin = null
                        state.configurationPin = ""
                        state.configurationCanRemove = true
                    } else {
                        host.beginActivation(profile)
                    }
                }
                is ProfilePinResult.Rejected -> {
                    state.pinRemainingAttempts = result.remainingAttempts
                    state.pinValue = ""
                }
                is ProfilePinResult.Locked -> {
                    state.pinLockedUntilMillis = result.until.toEpochMilliseconds()
                    state.pinValue = ""
                }
            }
        }
    }

    /** A locked profile can recover its PIN by signing in again. */
    fun recoverThroughReconnect(profile: HouseholdProfile) {
        scope.launch {
            state.reconnectConnectionId =
                host.services.profiles
                    .binding(profile.id)
                    ?.jellyfinConnectionId
            state.recoverPinProfileId = profile.id
            state.reconnectProfile = profile
            state.pinProfile = null
        }
    }

    fun submitConfigurationPin(profile: HouseholdProfile) {
        val first = state.firstConfiguredPin
        if (first == null) {
            state.firstConfiguredPin = state.configurationPin
            state.configurationPin = ""
            state.configurationPinError = false
        } else if (first == state.configurationPin) {
            scope.launch {
                pins.configure(profile.id, state.configurationPin)
                state.profilePinRevision += 1L
                state.cancelPinConfiguration()
            }
        } else {
            state.firstConfiguredPin = null
            state.configurationPin = ""
            state.configurationPinError = true
        }
    }

    fun removeConfiguredPin(profile: HouseholdProfile) {
        scope.launch {
            pins.remove(profile.id)
            state.profilePinRevision += 1L
            state.configuringPinProfile = null
            state.configurationCanRemove = false
        }
    }
}
