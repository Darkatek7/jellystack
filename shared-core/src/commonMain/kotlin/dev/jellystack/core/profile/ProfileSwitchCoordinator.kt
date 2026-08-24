package dev.jellystack.core.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

sealed interface ActiveProfileState {
    data object Picker : ActiveProfileState

    data class Locked(
        val profileId: String,
        val until: Instant? = null,
        val remainingAttempts: Int? = null,
        val previousProfileId: String? = null,
    ) : ActiveProfileState

    data class Switching(
        val fromProfileId: String?,
        val toProfileId: String,
        val generation: Long,
    ) : ActiveProfileState

    data class Bootstrapping(
        val profileId: String,
        val generation: Long,
    ) : ActiveProfileState

    data class Active(
        val profileId: String,
        val generation: Long,
    ) : ActiveProfileState

    data class Reconnect(
        val profileId: String,
        val generation: Long,
    ) : ActiveProfileState
}

sealed interface ProfileUnlockResult {
    data object Unlocked : ProfileUnlockResult

    data class Rejected(
        val remainingAttempts: Int,
    ) : ProfileUnlockResult

    data class Locked(
        val until: Instant,
    ) : ProfileUnlockResult
}

sealed interface ProfileSwitchResult {
    data class Activated(
        val profileId: String,
        val generation: Long,
    ) : ProfileSwitchResult

    data class Locked(
        val profileId: String,
        val until: Instant?,
        val remainingAttempts: Int?,
    ) : ProfileSwitchResult

    data class Reconnect(
        val profileId: String,
        val generation: Long,
    ) : ProfileSwitchResult

    data class MissingProfile(
        val profileId: String,
    ) : ProfileSwitchResult
}

class ProfileSwitchCoordinator(
    private val bindingResolver: suspend (profileId: String) -> ProfileConnectionBinding?,
    private val unlock: suspend (profileId: String) -> ProfileUnlockResult = { ProfileUnlockResult.Unlocked },
    private val stopPlayback: suspend () -> Unit,
    private val stopTrailers: suspend () -> Unit,
    private val stopSyncPlay: suspend () -> Unit,
    private val cancelPreviousGeneration: suspend (newGeneration: Long) -> Unit,
    private val activateConnections: suspend (binding: ProfileConnectionBinding) -> Unit,
    private val clearPresentationState: suspend () -> Unit,
    private val isAuthenticated: suspend (binding: ProfileConnectionBinding) -> Boolean,
    private val bootstrapCaches: suspend (profileId: String, generation: Long) -> Unit,
    private val refresh: suspend (profileId: String, generation: Long) -> Unit,
) {
    private val switchMutex = Mutex()
    private val mutableState = MutableStateFlow<ActiveProfileState>(ActiveProfileState.Picker)
    val state: StateFlow<ActiveProfileState> = mutableState.asStateFlow()
    var generation: Long = 0
        private set

    suspend fun switchTo(profileId: String): ProfileSwitchResult =
        switchMutex.withLock {
            require(profileId.isNotBlank())
            val binding = bindingResolver(profileId) ?: return@withLock ProfileSwitchResult.MissingProfile(profileId)
            val previous = activeProfileId()
            when (val unlockResult = unlock(profileId)) {
                is ProfileUnlockResult.Locked -> {
                    mutableState.value = ActiveProfileState.Locked(profileId, unlockResult.until, previousProfileId = previous)
                    return@withLock ProfileSwitchResult.Locked(profileId, unlockResult.until, null)
                }
                is ProfileUnlockResult.Rejected -> {
                    mutableState.value =
                        ActiveProfileState.Locked(
                            profileId = profileId,
                            remainingAttempts = unlockResult.remainingAttempts,
                            previousProfileId = previous,
                        )
                    return@withLock ProfileSwitchResult.Locked(profileId, null, unlockResult.remainingAttempts)
                }
                ProfileUnlockResult.Unlocked -> Unit
            }

            val nextGeneration = generation + 1
            generation = nextGeneration
            mutableState.value = ActiveProfileState.Switching(previous, profileId, nextGeneration)
            stopPlayback()
            stopTrailers()
            stopSyncPlay()
            cancelPreviousGeneration(nextGeneration)
            activateConnections(binding)
            clearPresentationState()
            if (!isAuthenticated(binding)) {
                mutableState.value = ActiveProfileState.Reconnect(profileId, nextGeneration)
                return@withLock ProfileSwitchResult.Reconnect(profileId, nextGeneration)
            }
            mutableState.value = ActiveProfileState.Bootstrapping(profileId, nextGeneration)
            bootstrapCaches(profileId, nextGeneration)
            refresh(profileId, nextGeneration)
            mutableState.value = ActiveProfileState.Active(profileId, nextGeneration)
            ProfileSwitchResult.Activated(profileId, nextGeneration)
        }

    fun showPicker() {
        mutableState.value = ActiveProfileState.Picker
    }

    fun cancelUnlock() {
        val locked = mutableState.value as? ActiveProfileState.Locked ?: return
        mutableState.value =
            locked.previousProfileId?.let { previous -> ActiveProfileState.Active(previous, generation) }
                ?: ActiveProfileState.Picker
    }

    fun publishIfCurrent(
        candidateGeneration: Long,
        publish: () -> Unit,
    ): Boolean {
        val currentGeneration = mutableState.value.generationOrNull()
        if (candidateGeneration != generation || currentGeneration != generation) return false
        publish()
        return true
    }

    private fun activeProfileId(): String? =
        when (val current = mutableState.value) {
            is ActiveProfileState.Active -> current.profileId
            is ActiveProfileState.Locked -> current.previousProfileId
            else -> null
        }
}

private fun ActiveProfileState.generationOrNull(): Long? =
    when (this) {
        is ActiveProfileState.Active -> generation
        is ActiveProfileState.Bootstrapping -> generation
        is ActiveProfileState.Switching -> generation
        is ActiveProfileState.Reconnect -> generation
        is ActiveProfileState.Locked,
        ActiveProfileState.Picker,
        -> null
    }
