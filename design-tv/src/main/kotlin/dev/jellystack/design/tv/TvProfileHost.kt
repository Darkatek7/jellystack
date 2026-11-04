@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.ProfilePinRepository
import dev.jellystack.core.profile.ProfileRemovalCoordinator
import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import kotlinx.datetime.Clock
import org.koin.core.Koin

/** The player objects the profile gate stops when the active profile changes, and hands on to the signed-in app. */
@OptIn(UnstableApi::class)
internal class TvAppPlaybackHandles(
    val playbackController: PlaybackController,
    val playerEngine: AndroidPlayerEngine,
    val trailerPreviewController: PlaybackController,
    val trailerPreviewEngine: AndroidPlayerEngine,
    val stopPlayback: () -> Unit,
)

/** App-level inputs of the TV root that the profile gate passes through to the signed-in app. */
internal class TvAppEnvironment(
    val appVersion: String,
    val strings: TvStrings,
    val coldLaunch: Boolean,
    val platform: TvAppPlatform,
)

/** Platform hooks of the TV host that the signed-in app uses. */
internal class TvAppPlatform(
    val voiceSearch: TvVoiceSearchPort,
    val integrations: TvPlatformIntegrations,
    val onExitConfirmed: () -> Unit,
)

/** Everything the gate screens need besides the gate state itself. */
internal class TvProfileHostApp(
    val handles: TvAppPlaybackHandles,
    val environment: TvAppEnvironment,
    val settingsRepository: AppSettingsRepository,
    val serverRepository: ServerRepository,
    val koin: Koin,
)

/** Decides between the connect screen, the profile picker, PIN entry and the signed-in app. */
@Composable
@OptIn(UnstableApi::class)
internal fun TvProfileHost(
    handles: TvAppPlaybackHandles,
    environment: TvAppEnvironment,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    modifier: Modifier,
) {
    val koin = remember { JellystackDI.koin }
    val scope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
    val pinRepository = remember(koin) { koin.get<ProfilePinRepository>() }
    val activeProfiles = remember(koin) { koin.get<ActiveProfileRepository>() }
    // Null until the store has answered, so "not loaded yet" never routes to the connect screen.
    val loadedProfiles: List<HouseholdProfile>? by
        profileRepository.observeProfiles().collectAsStateWithLifecycle(initialValue = null)
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    val state = rememberTvProfileHostState()
    val sessionRecovery = rememberTvSessionRecovery()
    val actions =
        TvProfileHostActions(
            state = state,
            services =
                TvProfileHostServices(
                    profiles = profileRepository,
                    servers = serverRepository,
                    pins = pinRepository,
                    activeProfiles = activeProfiles,
                    extras =
                        TvProfileHostExtraServices(
                            activeServerPreferences = remember(koin) { koin.get<ActiveServerPreferenceRepository>() },
                            removal = remember(koin) { koin.get<ProfileRemovalCoordinator>() },
                        ),
                ),
            scope = scope,
            sessionRecovery = sessionRecovery,
            playback =
                TvProfileHostPlayback(
                    stopPlayback = handles.stopPlayback,
                    stopTrailerPreview = { handles.trailerPreviewController.stop(saveProgress = false) },
                ),
        )
    val inputs = TvProfileHostInputs(loadedProfiles, servers, environment.coldLaunch, sessionRecovery)
    TvProfileHostEffects(actions, inputs)
    TvDeepLinkSignedOutEffect(
        inbox = environment.platform.integrations.deepLinks,
        signedOut = loadedProfiles != null && (inputs.profiles.isEmpty() || state.reconnectProfile != null),
    )
    TvProfileGateEffect(
        enabled = state.activatedProfileId != null && !state.pickerVisible,
        profileCount = inputs.profiles.size,
        onGate = actions::openProfilePicker,
    )
    val app = TvProfileHostApp(handles, environment, settingsRepository, serverRepository, koin)
    Box(modifier.fillMaxSize().background(TvBackground)) {
        TvProfileHostScreen(actions, inputs, app)
        state.removeProfile?.let { profile ->
            TvRemoveProfileDialog(
                profile = profile,
                strings = environment.strings,
                onConfirm = { actions.confirmRemoveProfile(profile) },
                onDismiss = { state.removeProfile = null },
            )
        }
    }
}

@Composable
private fun TvProfileHostScreen(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    app: TvProfileHostApp,
) {
    val state = actions.state
    val strings = app.environment.strings
    when {
        !state.legacyChecked || !state.initialized || inputs.loadedProfiles == null ->
            TvProfileStatusScreen(strings.loading)
        state.switchingProfile != null -> TvProfileStatusScreen(strings.profile.switchingProfile)
        state.addProfile -> TvProfileAddScreenContent(actions, inputs, app)
        state.reconnectProfile != null -> TvProfileReconnectContent(actions, inputs, app)
        state.pinProfile != null -> TvProfilePinEntryContent(actions, strings)
        state.configuringPinProfile != null -> TvProfilePinConfigurationContent(actions, strings)
        else -> TvProfileSelectionContent(actions, inputs, app)
    }
}

@Composable
private fun TvProfileAddScreenContent(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    app: TvProfileHostApp,
) {
    val state = actions.state
    TvAddProfileScreen(
        profiles = inputs.profiles,
        appVersion = app.environment.appVersion,
        strings = app.environment.strings,
        onCreated = { created ->
            state.addProfile = false
            // An account that already had a profile keeps that profile's PIN; a new profile has none.
            actions.selectProfile(created)
        },
        onDismiss = { state.addProfile = false },
    )
}

@Composable
private fun TvProfileReconnectContent(
    actions: TvProfileHostActions,
    inputs: TvProfileHostInputs,
    app: TvProfileHostApp,
) {
    val state = actions.state
    val profile = checkNotNull(state.reconnectProfile)
    val connection = state.reconnectConnectionId?.let { id -> inputs.servers.firstOrNull { it.id == id } }
    TvConnectionScreen(
        coordinator = app.koin.get<ServerConnectionCoordinator>(),
        quickConnectCoordinator = app.koin.get<JellyfinQuickConnectCoordinator>(),
        appVersion = app.environment.appVersion,
        strings = app.environment.strings,
        onConnected = { actions.onReconnected(profile) },
        onDismiss = state::dismissReconnect,
        existingServerId = state.reconnectConnectionId,
        initialDisplayName = connection?.name ?: profile.displayName,
        initialBaseUrl = connection?.baseUrl.orEmpty(),
    )
}

@Composable
private fun TvProfilePinEntryContent(
    actions: TvProfileHostActions,
    strings: TvStrings,
) {
    val state = actions.state
    val profile = checkNotNull(state.pinProfile)
    val pinActions = TvProfilePinActions(state, actions.services.pins, actions.scope, actions)
    val currentPinTime = remember(state.pinClockTick) { Clock.System.now().toEpochMilliseconds() }
    val locked = state.pinLockedUntilMillis?.let { it > currentPinTime } == true
    TvProfilePinScreen(
        profile = profile,
        pin = state.pinValue,
        strings = strings,
        remainingAttempts = state.pinRemainingAttempts,
        locked = locked,
        onDigit = { if (state.pinValue.length < 4) state.pinValue += it },
        onDelete = { if (state.pinValue.isNotEmpty()) state.pinValue = state.pinValue.dropLast(1) },
        onSubmit = { pinActions.submitPin(profile) },
        onCancel = state::cancelPinEntry,
        secondaryActionLabel = strings.profile.reconnectProfile.takeIf { locked },
        onSecondaryAction = if (locked) ({ pinActions.recoverThroughReconnect(profile) }) else null,
    )
}

@Composable
private fun TvProfilePinConfigurationContent(
    actions: TvProfileHostActions,
    strings: TvStrings,
) {
    val state = actions.state
    val profile = checkNotNull(state.configuringPinProfile)
    val pinActions = TvProfilePinActions(state, actions.services.pins, actions.scope, actions)
    TvProfilePinScreen(
        profile = profile,
        pin = state.configurationPin,
        strings = strings,
        remainingAttempts = null,
        locked = false,
        title =
            when {
                state.configurationPinError -> strings.profile.pinMismatch
                state.firstConfiguredPin == null -> strings.profile.setProfilePin
                else -> strings.profile.confirmProfilePin
            },
        onDigit = { if (state.configurationPin.length < 4) state.configurationPin += it },
        onDelete = {
            if (state.configurationPin.isNotEmpty()) state.configurationPin = state.configurationPin.dropLast(1)
        },
        onSubmit = { pinActions.submitConfigurationPin(profile) },
        onCancel = state::cancelPinConfiguration,
        secondaryActionLabel = strings.profile.removeProfilePin.takeIf { state.configurationCanRemove },
        onSecondaryAction = if (state.configurationCanRemove) ({ pinActions.removeConfiguredPin(profile) }) else null,
    )
}
