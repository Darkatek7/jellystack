package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.server.ManagedServer

/**
 * The state of the profile gate: which profile is selected, which gate screen is showing and the PIN entry in
 * progress. Only the fields listed in [TvProfileHostStateSaver] survive recreation; the rest is rebuilt, so a restored
 * activity re-runs the gate as a cold launch after process death does.
 */
internal class TvProfileHostState {
    var profileAvatarUrls by mutableStateOf<Map<String, String>>(emptyMap())
    var profilePinRequired by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var profilePinRevision by mutableStateOf(0L)
    var legacyChecked by mutableStateOf(false)

    /** Not saveable: a restored activity re-runs the profile gate (as a cold launch after process death). */
    var initialized by mutableStateOf(false)
    var pickerVisible by mutableStateOf(false)
    var profileManagementVisible by mutableStateOf(false)
    var managedProfileId by mutableStateOf<String?>(null)
    var selectedProfileId by mutableStateOf<String?>(null)
    var generation by mutableStateOf(0L)
    var activatedProfileId by mutableStateOf<String?>(null)
    var switchingProfile by mutableStateOf<HouseholdProfile?>(null)
    var pinProfile by mutableStateOf<HouseholdProfile?>(null)
    var pinValue by mutableStateOf("")
    var pinRemainingAttempts by mutableStateOf<Int?>(null)
    var pinLockedUntilMillis by mutableStateOf<Long?>(null)
    var pinClockTick by mutableStateOf(0L)
    var pinForManagement by mutableStateOf(false)
    var configuringPinProfile by mutableStateOf<HouseholdProfile?>(null)
    var firstConfiguredPin by mutableStateOf<String?>(null)
    var configurationPin by mutableStateOf("")
    var configurationPinError by mutableStateOf(false)
    var configurationCanRemove by mutableStateOf(false)
    var reconnectProfile by mutableStateOf<HouseholdProfile?>(null)
    var reconnectConnectionId by mutableStateOf<String?>(null)
    var recoverPinProfileId by mutableStateOf<String?>(null)
    var addProfile by mutableStateOf(false)
    var removeProfile by mutableStateOf<HouseholdProfile?>(null)

    /** Forgets the PIN configuration in progress, as cancelling the PIN setup screen does. */
    fun cancelPinConfiguration() {
        configuringPinProfile = null
        firstConfiguredPin = null
        configurationPin = ""
        configurationCanRemove = false
    }

    /** Leaves the sign-in screen of a profile and returns to the picker. */
    fun dismissReconnect() {
        reconnectProfile = null
        recoverPinProfileId = null
        pickerVisible = true
    }

    /** Leaves the PIN prompt without a result. */
    fun cancelPinEntry() {
        pinProfile = null
        pinValue = ""
        pinForManagement = false
    }
}

/** Saves exactly the fields the gate kept in `rememberSaveable` before it had a state holder. */
private val TvProfileHostStateSaver =
    Saver<TvProfileHostState, List<Any?>>(
        save = { state ->
            listOf(
                state.profilePinRevision,
                state.pickerVisible,
                state.profileManagementVisible,
                state.managedProfileId,
                state.selectedProfileId,
                state.generation,
                state.pinValue,
                state.pinRemainingAttempts,
                state.pinLockedUntilMillis,
                state.pinForManagement,
                state.firstConfiguredPin,
                state.configurationPin,
                state.configurationPinError,
                state.configurationCanRemove,
                state.reconnectConnectionId,
                state.recoverPinProfileId,
                state.addProfile,
            )
        },
        restore = { saved ->
            TvProfileHostState().apply {
                profilePinRevision = saved[0] as Long
                pickerVisible = saved[1] as Boolean
                profileManagementVisible = saved[2] as Boolean
                managedProfileId = saved[3] as String?
                selectedProfileId = saved[4] as String?
                generation = saved[5] as Long
                pinValue = saved[6] as String
                pinRemainingAttempts = saved[7] as Int?
                pinLockedUntilMillis = saved[8] as Long?
                pinForManagement = saved[9] as Boolean
                firstConfiguredPin = saved[10] as String?
                configurationPin = saved[11] as String
                configurationPinError = saved[12] as Boolean
                configurationCanRemove = saved[13] as Boolean
                reconnectConnectionId = saved[14] as String?
                recoverPinProfileId = saved[15] as String?
                addProfile = saved[16] as Boolean
            }
        },
    )

@Composable
internal fun rememberTvProfileHostState(): TvProfileHostState {
    val saver = TvProfileHostStateSaver
    return rememberSaveable(saver = saver) { TvProfileHostState() }
}

/** The profiles and servers the gate effects react to; [loadedProfiles] is null until the store has answered. */
internal class TvProfileHostInputs(
    val loadedProfiles: List<HouseholdProfile>?,
    val servers: List<ManagedServer>,
    val coldLaunch: Boolean,
    val sessionRecovery: TvSessionRecovery,
) {
    val profiles: List<HouseholdProfile> get() = loadedProfiles.orEmpty()
}
