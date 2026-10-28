package dev.jellystack.design.tv

import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlayerErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSessionRecoveryTest {
    private val idle =
        TvActivationInputs(
            initialized = true,
            pickerVisible = false,
            selectedProfileId = "profile",
            activatedProfileId = null,
            switchingProfile = false,
            reconnectPending = false,
            signInRequired = false,
        )

    @Test
    fun aSelectedProfileThatIsNotActiveYetIsActivated() {
        assertEquals(TvActivationDecision.ACTIVATE, decideTvActivation(idle))
    }

    @Test
    fun aRejectedTokenGoesToSignInInsteadOfBeingActivatedAgain() {
        val expired = idle.copy(signInRequired = true)

        assertEquals(TvActivationDecision.SHOW_RECONNECT, decideTvActivation(expired))
    }

    @Test
    fun whileTheSignInScreenIsPendingNothingActivates() {
        // This is the loop from the bug: expiry clears the active profile, the gate saw an idle profile and
        // activated the stale token again every second.
        val reconnecting = idle.copy(reconnectPending = true, signInRequired = true)

        assertEquals(TvActivationDecision.WAIT, decideTvActivation(reconnecting))
        assertEquals(TvActivationDecision.WAIT, decideTvActivation(idle.copy(reconnectPending = true)))
    }

    @Test
    fun nothingHappensWhileNotReadyAlreadyActiveOrSwitching() {
        listOf(
            idle.copy(initialized = false),
            idle.copy(pickerVisible = true),
            idle.copy(selectedProfileId = null),
            idle.copy(activatedProfileId = "profile"),
            idle.copy(switchingProfile = true),
        ).forEach { assertEquals(TvActivationDecision.WAIT, decideTvActivation(it)) }
    }

    @Test
    fun aDifferentActiveProfileDoesNotBlockActivatingTheSelectedOne() {
        assertEquals(TvActivationDecision.ACTIVATE, decideTvActivation(idle.copy(activatedProfileId = "other")))
    }

    @Test
    fun onlyTheFirstExpiryReportOfAProfileStartsTheRecoveryAndOnlySignInLiftsIt() {
        val recovery = TvSessionRecovery()

        assertTrue(recovery.markExpired("profile"))
        assertFalse(recovery.markExpired("profile"))
        assertTrue(recovery.requiresSignIn("profile"))
        assertFalse(recovery.requiresSignIn("other"))
        assertFalse(recovery.requiresSignIn(null))

        recovery.markSignedIn("profile")

        assertFalse(recovery.requiresSignIn("profile"))
        assertTrue(recovery.markExpired("profile"))
    }

    @Test
    fun onlyUnauthorizedPlaybackFailuresCountAsRejectedCredentials() {
        val unauthorized = PlaybackState.PlaybackError("401", category = PlayerErrorCategory.UNAUTHORIZED)

        assertEquals(unauthorized, unauthorized.unauthorizedPlaybackFailure())
        listOf(PlayerErrorCategory.NETWORK, PlayerErrorCategory.MEDIA).forEach { category ->
            assertNull(PlaybackState.PlaybackError("failure", category = category).unauthorizedPlaybackFailure())
        }
        assertNull(PlaybackState.Stopped.unauthorizedPlaybackFailure())
    }
}
