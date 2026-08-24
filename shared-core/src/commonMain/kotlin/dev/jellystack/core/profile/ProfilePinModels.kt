package dev.jellystack.core.profile

import kotlinx.datetime.Instant

sealed interface ProfilePinState {
    data object NotConfigured : ProfilePinState

    data object Ready : ProfilePinState

    data class Locked(
        val until: Instant,
    ) : ProfilePinState
}

sealed interface ProfilePinResult {
    data object Unlocked : ProfilePinResult

    data class Rejected(
        val remainingAttempts: Int,
    ) : ProfilePinResult

    data class Locked(
        val until: Instant,
    ) : ProfilePinResult
}
