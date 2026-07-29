package dev.jellystack.design

import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.StoredCredential

internal fun tutorialAutoAdvanceDestination(
    step: TutorialStep,
    jellyfinConnected: Boolean,
    seerrConnected: Boolean,
    automaticAdvanceAllowed: Boolean,
): TutorialStep? {
    if (!automaticAdvanceAllowed) return null
    return when {
        step == TutorialStep.ConnectJellyfin && jellyfinConnected -> TutorialStep.ConnectJellyseerr
        step == TutorialStep.ConnectJellyseerr && seerrConnected -> TutorialStep.Explore
        else -> null
    }
}

internal fun onboardingServerForm(
    step: TutorialStep,
    rememberedForms: Map<TutorialStep, ServerFormState>,
    activeJellyfin: ManagedServer?,
    activeSeerr: ManagedServer?,
): ServerFormState =
    onboardingServerForm(
        step = step,
        current = rememberedForms[step] ?: ServerFormState(),
        activeJellyfin = activeJellyfin,
        activeSeerr = activeSeerr,
    )

internal fun onboardingServerForm(
    step: TutorialStep,
    current: ServerFormState,
    activeJellyfin: ManagedServer?,
    activeSeerr: ManagedServer?,
): ServerFormState =
    when (step) {
        TutorialStep.ConnectJellyfin -> {
            val credential = activeJellyfin?.credentials as? StoredCredential.Jellyfin
            when {
                activeJellyfin != null &&
                    credential != null &&
                    (current.type != ServerFormType.JELLYFIN || current.serverId == null) ->
                    ServerFormState(
                        serverId = activeJellyfin.id,
                        type = ServerFormType.JELLYFIN,
                        name = activeJellyfin.name,
                        baseUrl = activeJellyfin.baseUrl,
                        username = credential.username,
                    )
                current.type == ServerFormType.JELLYFIN && current.name.isNotBlank() -> current
                else -> ServerFormState(type = ServerFormType.JELLYFIN, name = "Jellyfin")
            }
        }
        TutorialStep.ConnectJellyseerr ->
            when {
                activeSeerr != null &&
                    (current.type != ServerFormType.SEERR || current.serverId == null) ->
                    ServerFormState(
                        serverId = activeSeerr.id,
                        type = ServerFormType.SEERR,
                        name = activeSeerr.name,
                        baseUrl = activeSeerr.baseUrl,
                        automaticSeerrLogin = true,
                        useJellyfinLogin = true,
                    )
                current.type == ServerFormType.SEERR && current.name.isNotBlank() -> current
                else ->
                    ServerFormState(
                        type = ServerFormType.SEERR,
                        name = "Seerr",
                        automaticSeerrLogin = true,
                        useJellyfinLogin = true,
                    )
            }
        TutorialStep.Welcome,
        TutorialStep.Explore,
        -> ServerFormState()
    }
