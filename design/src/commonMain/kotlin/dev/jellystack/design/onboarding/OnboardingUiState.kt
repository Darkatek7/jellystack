package dev.jellystack.design.onboarding

import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.design.ServerFormState

internal data class OnboardingProgress(
    val current: Int,
    val total: Int = 4,
) {
    val fraction: Float
        get() = current.toFloat() / total.coerceAtLeast(1).toFloat()
}

internal enum class OnboardingField {
    Name,
    Url,
    Username,
    Email,
    Password,
}

internal enum class OnboardingValidationError {
    Required,
    InvalidUrl,
    InsecureTransportNotConfirmed,
}

internal data class OnboardingUiState(
    val step: TutorialStep,
    val progress: OnboardingProgress,
    val form: ServerFormState,
    val fieldErrors: Map<OnboardingField, OnboardingValidationError>,
    val manualSeerrCredentialsRequired: Boolean,
    val isSaving: Boolean,
    val serviceErrorDetail: String?,
    val canStartExploring: Boolean,
    val quickConnectState: JellyfinQuickConnectState? = null,
    val seerrQuickConnectExplanation: String? = null,
)

internal sealed interface OnboardingAction {
    data class FormChanged(
        val form: ServerFormState,
    ) : OnboardingAction

    data class SignInMethodChanged(
        val method: JellyfinSignInMethod,
    ) : OnboardingAction

    data object RestartQuickConnect : OnboardingAction

    data object CancelQuickConnect : OnboardingAction

    data object Continue : OnboardingAction

    data object Back : OnboardingAction

    data object SkipSeerr : OnboardingAction

    data object StartExploring : OnboardingAction
}

internal fun onboardingProgress(step: TutorialStep): OnboardingProgress =
    OnboardingProgress(
        current =
            when (step) {
                TutorialStep.Welcome -> 1
                TutorialStep.ConnectJellyfin -> 2
                TutorialStep.ConnectJellyseerr -> 3
                TutorialStep.Explore -> 4
            },
    )

internal fun validateOnboarding(
    step: TutorialStep,
    form: ServerFormState,
    manualSeerrCredentialsRequired: Boolean,
): Map<OnboardingField, OnboardingValidationError> =
    buildMap {
        when (step) {
            TutorialStep.Welcome,
            TutorialStep.Explore,
            -> Unit
            TutorialStep.ConnectJellyfin -> {
                requireText(OnboardingField.Name, form.name)
                requireUrl(form.baseUrl)
                if (form.requiresInsecureHttpConfirmation && !form.allowInsecureHttp) {
                    put(OnboardingField.Url, OnboardingValidationError.InsecureTransportNotConfirmed)
                }
                if (form.jellyfinSignInMethod == JellyfinSignInMethod.PASSWORD) {
                    requireText(OnboardingField.Username, form.username)
                    if (form.serverId == null) requireText(OnboardingField.Password, form.password)
                }
            }
            TutorialStep.ConnectJellyseerr -> {
                requireText(OnboardingField.Name, form.name)
                requireUrl(form.baseUrl)
                if (form.requiresInsecureHttpConfirmation && !form.allowInsecureHttp) {
                    put(OnboardingField.Url, OnboardingValidationError.InsecureTransportNotConfirmed)
                }
                if (manualSeerrCredentialsRequired) {
                    if (form.useJellyfinLogin) {
                        requireText(OnboardingField.Username, form.username)
                    } else {
                        requireText(OnboardingField.Email, form.email)
                    }
                    requireText(OnboardingField.Password, form.password)
                }
            }
        }
    }

private fun MutableMap<OnboardingField, OnboardingValidationError>.requireText(
    field: OnboardingField,
    value: String,
) {
    if (value.isBlank()) put(field, OnboardingValidationError.Required)
}

private fun MutableMap<OnboardingField, OnboardingValidationError>.requireUrl(value: String) {
    when {
        value.isBlank() -> put(OnboardingField.Url, OnboardingValidationError.Required)
        !isValidOnboardingUrl(value) -> put(OnboardingField.Url, OnboardingValidationError.InvalidUrl)
    }
}

private fun isValidOnboardingUrl(value: String): Boolean {
    val normalized = value.trim()
    val remainder =
        when {
            normalized.startsWith("https://", ignoreCase = true) -> normalized.drop(8)
            normalized.startsWith("http://", ignoreCase = true) -> normalized.drop(7)
            else -> return false
        }
    val authority = remainder.substringBefore('/').substringBefore('?').substringBefore('#')
    val hostAndPort = authority.substringAfterLast('@')
    val host =
        if (hostAndPort.startsWith('[')) {
            hostAndPort.substringBefore(']').takeIf { hostAndPort.contains(']') }.orEmpty()
        } else {
            hostAndPort.substringBefore(':')
        }
    return host.isNotBlank() && host != "[" && authority.none(Char::isWhitespace)
}
