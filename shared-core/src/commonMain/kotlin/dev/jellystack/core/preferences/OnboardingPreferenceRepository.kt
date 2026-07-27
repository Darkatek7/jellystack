package dev.jellystack.core.preferences

import com.russhwolf.settings.Settings

private const val KEY_LAST_WHATS_NEW_VERSION = "onboarding.last_whats_new_version"
private const val KEY_TUTORIAL_STEP = "onboarding.tutorial.step"
private const val KEY_TUTORIAL_COMPLETED = "onboarding.tutorial.completed"

class OnboardingPreferenceRepository(
    private val settings: Settings,
) {
    fun lastSeenWhatsNewVersion(): String? = readString(KEY_LAST_WHATS_NEW_VERSION)

    fun setLastSeenWhatsNewVersion(version: String) {
        settings.putString(KEY_LAST_WHATS_NEW_VERSION, version)
    }

    fun tutorialState(): TutorialState {
        val stepName = readString(KEY_TUTORIAL_STEP)
        val storedStep = stepName?.let { name -> enumValues<TutorialStep>().firstOrNull { it.name == name } }
        val step = storedStep ?: TutorialStep.Welcome
        val isCompleted = settings.getBoolean(KEY_TUTORIAL_COMPLETED, defaultValue = false)
        return TutorialState(step = step, isCompleted = isCompleted)
    }

    fun setTutorialStep(step: TutorialStep): TutorialState {
        val state = TutorialState(step = step, isCompleted = false)
        persist(state)
        return state
    }

    fun markTutorialCompleted(): TutorialState {
        val current = tutorialState()
        val completed = current.copy(isCompleted = true)
        persist(completed)
        return completed
    }

    fun resetTutorial(): TutorialState {
        val state = TutorialState(step = TutorialStep.Welcome, isCompleted = false)
        persist(state)
        return state
    }

    private fun persist(state: TutorialState) {
        settings.putString(KEY_TUTORIAL_STEP, state.step.name)
        settings.putBoolean(KEY_TUTORIAL_COMPLETED, state.isCompleted)
    }

    private fun readString(key: String): String? = if (settings.hasKey(key)) settings.getString(key, defaultValue = "") else null
}

data class TutorialState(
    val step: TutorialStep,
    val isCompleted: Boolean,
)

enum class TutorialStep {
    Welcome,
    ConnectJellyfin,
    ConnectJellyseerr,
    Explore,
}
