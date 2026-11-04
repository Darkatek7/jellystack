@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator

/** The screens that show once no gate step is pending: connect, manage, pick, or the signed-in app. */
@Composable
internal fun TvProfileSelectionContent(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    app: TvProfileHostApp,
) {
    val state = actions.state
    val strings = app.environment.strings
    val profiles = inputs.profiles
    val selectedProfile = profiles.firstOrNull { it.id == state.selectedProfileId }
    when {
        selectedProfile != null && !state.pickerVisible && state.activatedProfileId != selectedProfile.id ->
            TvProfileStatusScreen(strings.profile.switchingProfile)
        profiles.isEmpty() -> TvProfileFirstConnectionContent(actions, app)
        (state.pickerVisible || selectedProfile == null) && state.profileManagementVisible ->
            TvProfileManagementContent(actions, inputs, strings)
        state.pickerVisible || selectedProfile == null -> TvProfilePickerContent(actions, inputs, strings)
        else -> TvProfileAuthenticatedContent(selectedProfile, actions, app)
    }
}

@Composable
private fun TvProfileFirstConnectionContent(
    actions: TvProfileHostActions,
    app: TvProfileHostApp,
) {
    TvConnectionScreen(
        coordinator = app.koin.get<ServerConnectionCoordinator>(),
        quickConnectCoordinator = app.koin.get<JellyfinQuickConnectCoordinator>(),
        appVersion = app.environment.appVersion,
        strings = app.environment.strings,
        onConnected = { actions.onFirstConnected() },
    )
}

@Composable
private fun TvProfileManagementContent(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    strings: TvStrings,
) {
    val state = actions.state
    val profiles = inputs.profiles
    TvProfileManagementScreen(
        profiles = tvProfilePresentations(profiles, state),
        selectedProfileId = state.managedProfileId,
        strings = strings,
        onSelectProfile = { state.managedProfileId = it },
        onManagePin = { profileId ->
            profiles.firstOrNull { it.id == profileId }?.let(actions::manageProfilePin)
        },
        onRemove = { profileId -> state.removeProfile = profiles.firstOrNull { it.id == profileId } },
        onBack = {
            if (state.managedProfileId != null) {
                state.managedProfileId = null
            } else {
                state.profileManagementVisible = false
            }
        },
    )
}

@Composable
private fun TvProfilePickerContent(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    strings: TvStrings,
) {
    val state = actions.state
    val profiles = inputs.profiles
    TvProfilePickerScreen(
        profiles = tvProfilePresentations(profiles, state),
        rememberedProfileId = profiles.maxByOrNull { it.lastActiveAt ?: it.createdAt }?.id,
        strings = strings,
        onSelect = { profileId -> profiles.firstOrNull { it.id == profileId }?.let(actions::selectProfile) },
        onAdd = { state.addProfile = true },
        onManage = {
            state.managedProfileId = null
            state.profileManagementVisible = true
        },
    )
}

@Composable
@OptIn(UnstableApi::class)
private fun TvProfileAuthenticatedContent(
    profile: HouseholdProfile,
    actions: TvProfileHostActions,
    app: TvProfileHostApp,
) {
    val handles = app.handles
    TvAuthenticatedApp(
        playbackController = handles.playbackController,
        playerEngine = handles.playerEngine,
        trailerPreviewPlaybackController = handles.trailerPreviewController,
        trailerPreviewEngine = handles.trailerPreviewEngine,
        appVersion = app.environment.appVersion,
        settingsRepository = app.settingsRepository,
        serverRepository = app.serverRepository,
        strings = app.environment.strings,
        stopPlayback = handles.stopPlayback,
        activeProfileName = profile.displayName,
        activeProfileId = profile.id,
        activeProfileGeneration = actions.state.generation,
        onOpenProfiles = { actions.openProfilePicker() },
        onAuthenticationExpired = { actions.onAuthenticationExpired(profile) },
        voiceSearch = app.environment.platform.voiceSearch,
        integrations = app.environment.platform.integrations,
        onExitConfirmed = app.environment.platform.onExitConfirmed,
    )
}

private fun tvProfilePresentations(
    profiles: List<HouseholdProfile>,
    state: TvProfileHostState,
): List<TvProfilePresentation> =
    profiles.map { profile ->
        TvProfilePresentation(
            id = profile.id,
            displayName = profile.displayName,
            avatarUrl = state.profileAvatarUrls[profile.id],
            pinRequired = state.profilePinRequired[profile.id] == true,
            lastActiveAt = profile.lastActiveAt,
        )
    }
