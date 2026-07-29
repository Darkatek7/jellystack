package dev.jellystack.design.onboarding

import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.design.ServerFormState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingUiStateTest {
    @Test
    fun fourStagesExposeOneBasedProgress() {
        assertEquals(OnboardingProgress(current = 1, total = 4), onboardingProgress(TutorialStep.Welcome))
        assertEquals(OnboardingProgress(current = 4, total = 4), onboardingProgress(TutorialStep.Explore))
    }

    @Test
    fun jellyfinErrorsAreLinkedToIndividualFields() {
        val errors =
            validateOnboarding(
                step = TutorialStep.ConnectJellyfin,
                form =
                    ServerFormState(
                        name = "",
                        baseUrl = "not-a-url",
                        username = "",
                        password = "",
                        jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
                    ),
                manualSeerrCredentialsRequired = false,
            )

        assertEquals(OnboardingValidationError.Required, errors[OnboardingField.Name])
        assertEquals(OnboardingValidationError.InvalidUrl, errors[OnboardingField.Url])
        assertEquals(OnboardingValidationError.Required, errors[OnboardingField.Username])
        assertEquals(OnboardingValidationError.Required, errors[OnboardingField.Password])
    }

    @Test
    fun existingJellyfinAndAutomaticSeerrDoNotRequirePasswords() {
        val jellyfinErrors =
            validateOnboarding(
                step = TutorialStep.ConnectJellyfin,
                form =
                    ServerFormState(
                        serverId = "jellyfin-1",
                        name = "Home",
                        baseUrl = "https://media.example",
                        username = "owner",
                        jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
                    ),
                manualSeerrCredentialsRequired = false,
            )
        val seerrErrors =
            validateOnboarding(
                step = TutorialStep.ConnectJellyseerr,
                form = ServerFormState(name = "Seerr", baseUrl = "http://seerr.example"),
                manualSeerrCredentialsRequired = false,
            )

        assertFalse(jellyfinErrors.containsKey(OnboardingField.Password))
        assertFalse(seerrErrors.containsKey(OnboardingField.Password))
    }

    @Test
    fun unencryptedServerRequiresAcknowledgementBeforeOnboardingContinues() {
        val form =
            ServerFormState(
                name = "Local demo",
                baseUrl = "http://media.example",
            )

        val errors =
            validateOnboarding(
                step = TutorialStep.ConnectJellyfin,
                form = form,
                manualSeerrCredentialsRequired = false,
            )

        assertEquals(
            OnboardingValidationError.InsecureTransportNotConfirmed,
            errors[OnboardingField.Url],
        )
        assertTrue(
            validateOnboarding(
                step = TutorialStep.ConnectJellyfin,
                form = form.copy(allowInsecureHttp = true),
                manualSeerrCredentialsRequired = false,
            ).isEmpty(),
        )
    }
}
