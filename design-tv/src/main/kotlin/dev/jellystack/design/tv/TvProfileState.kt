package dev.jellystack.design.tv

import androidx.compose.runtime.Immutable
import dev.jellystack.core.profile.HouseholdProfile
import kotlinx.datetime.Instant

@Immutable
internal sealed interface TvProfileState {
    data object Loading : TvProfileState

    data object NeedsConnection : TvProfileState

    data class Picker(
        val profiles: List<HouseholdProfile>,
    ) : TvProfileState

    data class PinEntry(
        val profile: HouseholdProfile,
        val enteredDigits: Int = 0,
        val remainingAttempts: Int? = null,
        val lockedUntil: Instant? = null,
    ) : TvProfileState

    data class Reconnect(
        val profile: HouseholdProfile,
        val connectionId: String,
    ) : TvProfileState

    data class Switching(
        val profile: HouseholdProfile,
    ) : TvProfileState

    data class Content(
        val profile: HouseholdProfile,
        val generation: Long,
    ) : TvProfileState
}

internal fun initialTvProfileState(
    profiles: List<HouseholdProfile>,
    coldLaunch: Boolean,
    pickerWasVisible: Boolean,
    rememberedProfileId: String?,
    generation: Long = 0,
): TvProfileState {
    if (profiles.isEmpty()) return TvProfileState.NeedsConnection
    val pickerRequired = pickerWasVisible || (coldLaunch && profiles.size > 1)
    val remembered = profiles.firstOrNull { it.id == rememberedProfileId }
    val selected = remembered ?: profiles.singleOrNull()
    return when {
        pickerRequired -> TvProfileState.Picker(profiles)
        selected != null -> TvProfileState.Content(selected, generation)
        else -> TvProfileState.Picker(profiles)
    }
}

internal const val TV_PROFILE_AVATAR_TARGET = "rail:profile-avatar"
internal const val TV_TOP_LEVEL_DESTINATION_COUNT = 5
