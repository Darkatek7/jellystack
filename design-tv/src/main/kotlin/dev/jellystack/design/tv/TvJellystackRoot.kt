@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package dev.jellystack.design.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Text
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.jellyfin.AuthExpirySignal
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.core.jellyfin.LocalTrailerContext
import dev.jellystack.core.jellyfin.LocalTrailerResolver
import dev.jellystack.core.jellyfin.isLiveTv
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.livetv.LiveTvRepository
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.HouseholdProfile
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.MyListEntry
import dev.jellystack.core.profile.ProfileMyListRepository
import dev.jellystack.core.profile.ProfilePinRepository
import dev.jellystack.core.profile.ProfilePinResult
import dev.jellystack.core.profile.ProfilePinState
import dev.jellystack.core.profile.ProfilePreferencesRepository
import dev.jellystack.core.profile.ProfileRemovalCoordinator
import dev.jellystack.core.profile.mediaIdentity
import dev.jellystack.core.server.ActiveServerPreferenceRepository
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackQueue
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@Composable
internal fun TvRouteFocusScope(
    focusCoordinator: TvFocusCoordinator<FocusRequester>,
    routeKey: String,
    focusMemory: TvFocusMemory? = null,
    content: @Composable () -> Unit,
) {
    val entryFocusRequester = remember(focusCoordinator, routeKey) { FocusRequester() }
    CompositionLocalProvider(
        LocalTvScreenEntryFocusRequester provides entryFocusRequester,
        LocalTvFocusContext provides TvFocusContext(focusCoordinator, routeKey, focusMemory),
        content = content,
    )
}

@Composable
internal fun TvAppBackHandler(
    dispatcher: TvAppBackDispatcher,
    onExitRequested: () -> Unit = {},
) {
    BackHandler(enabled = dispatcher.rootHandlerEnabled) {
        if (!dispatcher.dispatch()) onExitRequested()
    }
}

@Composable
@OptIn(UnstableApi::class)
fun TvJellystackRoot(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    stopPlayback: () -> Unit,
    modifier: Modifier = Modifier,
    coldLaunch: Boolean = true,
    voiceSearch: TvVoiceSearchPort = UnsupportedTvVoiceSearch,
    integrations: TvPlatformIntegrations = TvPlatformIntegrations(),
    onExitConfirmed: () -> Unit = {},
) {
    val koin = remember { JellystackDI.koin }
    val serverRepository = remember(koin) { koin.get<ServerRepository>() }
    val settingsRepository = remember(koin) { koin.get<AppSettingsRepository>() }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    JellystackTvTheme(
        motionPreference = settings.motionPreference,
        highContrastFocus = settings.highContrastFocus,
    ) {
        val strings = remember(settings.appLanguage) { TvStrings.current(settings.appLanguage) }
        TvProfileHost(
            playbackController = playbackController,
            playerEngine = playerEngine,
            trailerPreviewController = trailerPreviewController,
            trailerPreviewEngine = trailerPreviewEngine,
            appVersion = appVersion,
            settingsRepository = settingsRepository,
            serverRepository = serverRepository,
            strings = strings,
            stopPlayback = stopPlayback,
            coldLaunch = coldLaunch,
            voiceSearch = voiceSearch,
            integrations = integrations,
            onExitConfirmed = onExitConfirmed,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun TvProfileHost(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    strings: TvStrings,
    stopPlayback: () -> Unit,
    coldLaunch: Boolean,
    voiceSearch: TvVoiceSearchPort,
    integrations: TvPlatformIntegrations,
    onExitConfirmed: () -> Unit,
    modifier: Modifier,
) {
    val koin = remember { JellystackDI.koin }
    val scope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
    val activeProfiles = remember(koin) { koin.get<ActiveProfileRepository>() }
    val pinRepository = remember(koin) { koin.get<ProfilePinRepository>() }
    val removalCoordinator = remember(koin) { koin.get<ProfileRemovalCoordinator>() }
    val activeServerPreferences = remember(koin) { koin.get<ActiveServerPreferenceRepository>() }
    // Null until the store has answered, so "not loaded yet" never routes to the connect screen.
    val loadedProfiles: List<HouseholdProfile>? by
        profileRepository.observeProfiles().collectAsStateWithLifecycle(initialValue = null)
    val profiles = loadedProfiles.orEmpty()
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    var profileAvatarUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var profilePinRequired by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var profilePinRevision by rememberSaveable { mutableStateOf(0L) }
    var legacyChecked by remember { mutableStateOf(false) }
    // Not saveable: a restored activity re-runs the profile gate (as a cold launch after process death).
    var initialized by remember { mutableStateOf(false) }
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    var profileManagementVisible by rememberSaveable { mutableStateOf(false) }
    var managedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var generation by rememberSaveable { mutableStateOf(0L) }
    var activatedProfileId by remember { mutableStateOf<String?>(null) }
    var switchingProfile by remember { mutableStateOf<HouseholdProfile?>(null) }
    var pinProfile by remember { mutableStateOf<HouseholdProfile?>(null) }
    var pinValue by rememberSaveable { mutableStateOf("") }
    var pinRemainingAttempts by rememberSaveable { mutableStateOf<Int?>(null) }
    var pinLockedUntilMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var pinClockTick by remember { mutableStateOf(0L) }
    var pinForManagement by rememberSaveable { mutableStateOf(false) }
    var configuringPinProfile by remember { mutableStateOf<HouseholdProfile?>(null) }
    var firstConfiguredPin by rememberSaveable { mutableStateOf<String?>(null) }
    var configurationPin by rememberSaveable { mutableStateOf("") }
    var configurationPinError by rememberSaveable { mutableStateOf(false) }
    var configurationCanRemove by rememberSaveable { mutableStateOf(false) }
    var reconnectProfile by remember { mutableStateOf<HouseholdProfile?>(null) }
    val sessionRecovery = rememberTvSessionRecovery()
    var reconnectConnectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var recoverPinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var addProfile by rememberSaveable { mutableStateOf(false) }
    var removeProfile by remember { mutableStateOf<HouseholdProfile?>(null) }

    LaunchedEffect(servers.map { it.id to it.updatedAt }) {
        if (servers.any { it.type == ServerType.JELLYFIN }) {
            profileRepository.ensureLegacyDefaultProfile()
            profileRepository.repairLegacySingleProfileSeerrBinding(
                servers.filter { it.type == ServerType.JELLYSEERR }.map { it.id },
            )
            val profile = profileRepository.listProfiles().singleOrNull()
            val binding = profile?.let { profileRepository.binding(it.id) }
            val jellyfin = binding?.let { exact -> servers.firstOrNull { it.id == exact.jellyfinConnectionId } }
            val credential = jellyfin?.credentials as? StoredCredential.Jellyfin
            if (jellyfin != null && credential != null) {
                profileRepository.repairLegacySingleProfileIdentity(
                    jellyfinConnectionId = jellyfin.id,
                    displayName = credential.username,
                    avatarSeed = credential.userId,
                )
            }
        }
        legacyChecked = true
    }
    LaunchedEffect(profiles.map { it.id to it.updatedAt }, servers.map { it.id to it.updatedAt }) {
        profileAvatarUrls =
            buildMap {
                profiles.forEach { profile ->
                    val binding = profileRepository.binding(profile.id) ?: return@forEach
                    val server = servers.firstOrNull { it.id == binding.jellyfinConnectionId } ?: return@forEach
                    val credential = server.credentials as? StoredCredential.Jellyfin ?: return@forEach
                    jellyfinUserImageUrl(server.baseUrl, credential.accessToken, credential.userId)?.let { url ->
                        put(profile.id, url)
                    }
                }
            }
    }
    LaunchedEffect(profiles.map { it.id }, profilePinRevision) {
        profilePinRequired =
            profiles.associate { profile ->
                profile.id to (pinRepository.state(profile.id) !is ProfilePinState.NotConfigured)
            }
    }
    LaunchedEffect(loadedProfiles?.map { it.id }, selectedProfileId) {
        if (loadedProfiles != null && selectedProfileId != null && profiles.none { it.id == selectedProfileId }) {
            selectedProfileId = null
            pickerVisible = true
        }
    }

    fun beginActivation(profile: HouseholdProfile) {
        switchingProfile = profile
        pinProfile = null
        pinValue = ""
        scope.launch {
            stopPlayback()
            trailerPreviewController.stop(saveProgress = false)
            activeProfiles.clear()
            val binding = profileRepository.binding(profile.id)
            val jellyfin = binding?.let { serverRepository.findServer(it.jellyfinConnectionId) }
            val serverUsable = jellyfin?.type == ServerType.JELLYFIN && !sessionRecovery.requiresSignIn(profile.id)
            if (binding == null || !serverUsable) {
                reconnectProfile = profile
                reconnectConnectionId = binding?.jellyfinConnectionId
                switchingProfile = null
                return@launch
            }
            serverRepository.setActiveServer(ServerType.JELLYFIN, binding.jellyfinConnectionId)
            val seerrId = binding.seerrConnectionId
            if (seerrId == null) {
                activeServerPreferences.setActiveServerId(ServerType.JELLYSEERR, null)
            } else if (serverRepository.findServer(seerrId)?.type == ServerType.JELLYSEERR) {
                serverRepository.setActiveServer(ServerType.JELLYSEERR, seerrId)
            } else {
                activeServerPreferences.setActiveServerId(ServerType.JELLYSEERR, null)
            }
            activeProfiles.activate(profile.id)
            profileRepository.updateProfile(profile.copy(lastActiveAt = Clock.System.now()))
            generation += 1L
            selectedProfileId = profile.id
            activatedProfileId = profile.id
            pickerVisible = false
            profileManagementVisible = false
            managedProfileId = null
            reconnectProfile = null
            reconnectConnectionId = null
            switchingProfile = null
        }
    }

    fun showReconnect(profile: HouseholdProfile) {
        scope.launch {
            reconnectConnectionId = profileRepository.binding(profile.id)?.jellyfinConnectionId
            reconnectProfile = profile
        }
    }

    suspend fun openProfile(profile: HouseholdProfile) {
        when (val state = pinRepository.state(profile.id)) {
            ProfilePinState.NotConfigured -> beginActivation(profile)
            ProfilePinState.Ready -> {
                pinProfile = profile
                pinValue = ""
                pinRemainingAttempts = null
                pinLockedUntilMillis = null
            }
            is ProfilePinState.Locked -> {
                pinProfile = profile
                pinValue = ""
                pinLockedUntilMillis = state.until.toEpochMilliseconds()
            }
        }
    }

    fun selectProfile(profile: HouseholdProfile) {
        pinForManagement = false
        scope.launch { openProfile(profile) }
    }

    fun openProfilePicker() {
        stopPlayback()
        trailerPreviewController.stop(saveProgress = false)
        activeProfiles.clear()
        activatedProfileId = null
        pickerVisible = true
    }
    LaunchedEffect(legacyChecked, loadedProfiles, initialized) {
        val loaded = loadedProfiles
        if (!legacyChecked || loaded == null || initialized) return@LaunchedEffect
        val pinProtected = loaded.filter { pinRepository.state(it.id) !is ProfilePinState.NotConfigured }
        when (
            val initial =
                initialTvProfileState(
                    profiles = loaded,
                    coldLaunch = coldLaunch,
                    pickerWasVisible = pickerVisible,
                    rememberedProfileId = activeProfiles.profileId.value,
                    generation = generation,
                ).lockedOnColdLaunch(coldLaunch, pinProtected.mapTo(HashSet()) { it.id })
        ) {
            is TvProfileState.Content -> selectedProfileId = initial.profile.id
            is TvProfileState.Picker -> {
                activeProfiles.clear()
                pickerVisible = true
            }
            is TvProfileState.PinEntry -> {
                activeProfiles.clear()
                openProfile(initial.profile)
            }
            else -> Unit
        }
        initialized = true
    }
    LaunchedEffect(pinLockedUntilMillis, pinProfile?.id) {
        val deadline = pinLockedUntilMillis ?: return@LaunchedEffect
        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            delay(1_000)
            pinClockTick += 1L
        }
    }

    fun manageProfilePin(profile: HouseholdProfile) {
        scope.launch {
            when (val state = pinRepository.state(profile.id)) {
                ProfilePinState.NotConfigured -> {
                    configuringPinProfile = profile
                    firstConfiguredPin = null
                    configurationPin = ""
                    configurationPinError = false
                    configurationCanRemove = false
                }
                ProfilePinState.Ready -> {
                    pinForManagement = true
                    pinProfile = profile
                    pinValue = ""
                    pinRemainingAttempts = null
                    pinLockedUntilMillis = null
                }
                is ProfilePinState.Locked -> {
                    pinForManagement = true
                    pinProfile = profile
                    pinValue = ""
                    pinLockedUntilMillis = state.until.toEpochMilliseconds()
                }
            }
        }
    }

    LaunchedEffect(
        initialized,
        loadedProfiles == null,
        pickerVisible,
        selectedProfileId,
        activatedProfileId,
        reconnectProfile?.id,
        sessionRecovery.requiresSignIn(selectedProfileId),
    ) {
        val profile = profiles.firstOrNull { it.id == selectedProfileId } ?: return@LaunchedEffect
        val decision =
            decideTvActivation(
                TvActivationInputs(
                    initialized = initialized,
                    pickerVisible = pickerVisible,
                    selectedProfileId = profile.id,
                    activatedProfileId = activatedProfileId,
                    switchingProfile = switchingProfile != null,
                    reconnectPending = reconnectProfile != null,
                    signInRequired = sessionRecovery.requiresSignIn(profile.id),
                ),
            )
        when (decision) {
            TvActivationDecision.ACTIVATE -> beginActivation(profile)
            TvActivationDecision.SHOW_RECONNECT -> showReconnect(profile)
            TvActivationDecision.WAIT -> Unit
        }
    }

    BackHandler(
        enabled =
            addProfile ||
                reconnectProfile != null ||
                pinProfile != null ||
                configuringPinProfile != null ||
                profileManagementVisible,
    ) {
        when {
            addProfile -> addProfile = false
            reconnectProfile != null -> {
                reconnectProfile = null
                recoverPinProfileId = null
                pickerVisible = true
            }
            configuringPinProfile != null -> {
                configuringPinProfile = null
                firstConfiguredPin = null
                configurationPin = ""
                configurationCanRemove = false
            }
            pinProfile != null -> {
                pinProfile = null
                pinValue = ""
                pinForManagement = false
            }
            managedProfileId != null -> managedProfileId = null
            profileManagementVisible -> profileManagementVisible = false
        }
    }

    TvDeepLinkSignedOutEffect(
        inbox = integrations.deepLinks,
        signedOut = loadedProfiles != null && (profiles.isEmpty() || reconnectProfile != null),
    )
    TvProfileGateEffect(
        enabled = activatedProfileId != null && !pickerVisible,
        profileCount = profiles.size,
        onGate = ::openProfilePicker,
    )
    Box(modifier.fillMaxSize().background(TvBackground)) {
        val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
        when {
            !legacyChecked || !initialized || loadedProfiles == null -> TvProfileStatusScreen(strings.loading)
            switchingProfile != null -> TvProfileStatusScreen(strings.switchingProfile)
            addProfile ->
                TvAddProfileScreen(
                    profiles = profiles,
                    appVersion = appVersion,
                    strings = strings,
                    onCreated = { created ->
                        addProfile = false
                        // An account that already had a profile keeps that profile's PIN; a new profile has none.
                        selectProfile(created)
                    },
                    onDismiss = { addProfile = false },
                )
            reconnectProfile != null -> {
                val profile = checkNotNull(reconnectProfile)
                val connection = reconnectConnectionId?.let { id -> servers.firstOrNull { it.id == id } }
                TvConnectionScreen(
                    coordinator = koin.get<ServerConnectionCoordinator>(),
                    quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                    appVersion = appVersion,
                    strings = strings,
                    onConnected = {
                        scope.launch {
                            if (recoverPinProfileId == profile.id) {
                                pinRepository.recoverAfterReauthentication(profile.id) { exactProfileId ->
                                    exactProfileId == profile.id
                                }
                                recoverPinProfileId = null
                            }
                            sessionRecovery.markSignedIn(profile.id)
                            beginActivation(profile)
                        }
                    },
                    onDismiss = {
                        reconnectProfile = null
                        recoverPinProfileId = null
                        pickerVisible = true
                    },
                    existingServerId = reconnectConnectionId,
                    initialDisplayName = connection?.name ?: profile.displayName,
                    initialBaseUrl = connection?.baseUrl.orEmpty(),
                )
            }
            pinProfile != null -> {
                val profile = checkNotNull(pinProfile)
                val currentPinTime = remember(pinClockTick) { Clock.System.now().toEpochMilliseconds() }
                val locked = pinLockedUntilMillis?.let { it > currentPinTime } == true
                TvProfilePinScreen(
                    profile = profile,
                    pin = pinValue,
                    strings = strings,
                    remainingAttempts = pinRemainingAttempts,
                    locked = locked,
                    onDigit = { if (pinValue.length < 4) pinValue += it },
                    onDelete = { if (pinValue.isNotEmpty()) pinValue = pinValue.dropLast(1) },
                    onSubmit = {
                        scope.launch {
                            when (val result = pinRepository.verify(profile.id, pinValue)) {
                                ProfilePinResult.Unlocked -> {
                                    if (pinForManagement) {
                                        pinProfile = null
                                        pinForManagement = false
                                        configuringPinProfile = profile
                                        firstConfiguredPin = null
                                        configurationPin = ""
                                        configurationCanRemove = true
                                    } else {
                                        beginActivation(profile)
                                    }
                                }
                                is ProfilePinResult.Rejected -> {
                                    pinRemainingAttempts = result.remainingAttempts
                                    pinValue = ""
                                }
                                is ProfilePinResult.Locked -> {
                                    pinLockedUntilMillis = result.until.toEpochMilliseconds()
                                    pinValue = ""
                                }
                            }
                        }
                    },
                    onCancel = {
                        pinProfile = null
                        pinValue = ""
                        pinForManagement = false
                    },
                    secondaryActionLabel = strings.reconnectProfile.takeIf { locked },
                    onSecondaryAction =
                        if (locked) {
                            {
                                scope.launch {
                                    reconnectConnectionId = profileRepository.binding(profile.id)?.jellyfinConnectionId
                                    recoverPinProfileId = profile.id
                                    reconnectProfile = profile
                                    pinProfile = null
                                }
                            }
                        } else {
                            null
                        },
                )
            }
            configuringPinProfile != null -> {
                val profile = checkNotNull(configuringPinProfile)
                TvProfilePinScreen(
                    profile = profile,
                    pin = configurationPin,
                    strings = strings,
                    remainingAttempts = null,
                    locked = false,
                    title =
                        when {
                            configurationPinError -> strings.pinMismatch
                            firstConfiguredPin == null -> strings.setProfilePin
                            else -> strings.confirmProfilePin
                        },
                    onDigit = { if (configurationPin.length < 4) configurationPin += it },
                    onDelete = { if (configurationPin.isNotEmpty()) configurationPin = configurationPin.dropLast(1) },
                    onSubmit = {
                        val first = firstConfiguredPin
                        if (first == null) {
                            firstConfiguredPin = configurationPin
                            configurationPin = ""
                            configurationPinError = false
                        } else if (first == configurationPin) {
                            scope.launch {
                                pinRepository.configure(profile.id, configurationPin)
                                profilePinRevision += 1L
                                configuringPinProfile = null
                                firstConfiguredPin = null
                                configurationPin = ""
                                configurationCanRemove = false
                            }
                        } else {
                            firstConfiguredPin = null
                            configurationPin = ""
                            configurationPinError = true
                        }
                    },
                    onCancel = {
                        configuringPinProfile = null
                        firstConfiguredPin = null
                        configurationPin = ""
                        configurationCanRemove = false
                    },
                    secondaryActionLabel = strings.removeProfilePin.takeIf { configurationCanRemove },
                    onSecondaryAction =
                        if (configurationCanRemove) {
                            {
                                scope.launch {
                                    pinRepository.remove(profile.id)
                                    profilePinRevision += 1L
                                    configuringPinProfile = null
                                    configurationCanRemove = false
                                }
                            }
                        } else {
                            null
                        },
                )
            }
            selectedProfile != null && !pickerVisible && activatedProfileId != selectedProfile.id ->
                TvProfileStatusScreen(strings.switchingProfile)
            profiles.isEmpty() ->
                TvConnectionScreen(
                    coordinator = koin.get<ServerConnectionCoordinator>(),
                    quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                    appVersion = appVersion,
                    strings = strings,
                    onConnected = {
                        scope.launch {
                            profileRepository.ensureLegacyDefaultProfile()?.let(::beginActivation)
                        }
                    },
                )
            (pickerVisible || selectedProfile == null) && profileManagementVisible ->
                TvProfileManagementScreen(
                    profiles =
                        profiles.map { profile ->
                            TvProfilePresentation(
                                id = profile.id,
                                displayName = profile.displayName,
                                avatarUrl = profileAvatarUrls[profile.id],
                                pinRequired = profilePinRequired[profile.id] == true,
                                lastActiveAt = profile.lastActiveAt,
                            )
                        },
                    selectedProfileId = managedProfileId,
                    strings = strings,
                    onSelectProfile = { managedProfileId = it },
                    onManagePin = { profileId ->
                        profiles.firstOrNull { it.id == profileId }?.let(::manageProfilePin)
                    },
                    onRemove = { profileId -> removeProfile = profiles.firstOrNull { it.id == profileId } },
                    onBack = {
                        if (managedProfileId != null) managedProfileId = null else profileManagementVisible = false
                    },
                )
            pickerVisible || selectedProfile == null ->
                TvProfilePickerScreen(
                    profiles =
                        profiles.map { profile ->
                            TvProfilePresentation(
                                id = profile.id,
                                displayName = profile.displayName,
                                avatarUrl = profileAvatarUrls[profile.id],
                                pinRequired = profilePinRequired[profile.id] == true,
                                lastActiveAt = profile.lastActiveAt,
                            )
                        },
                    rememberedProfileId = profiles.maxByOrNull { it.lastActiveAt ?: it.createdAt }?.id,
                    strings = strings,
                    onSelect = { profileId -> profiles.firstOrNull { it.id == profileId }?.let(::selectProfile) },
                    onAdd = { addProfile = true },
                    onManage = {
                        managedProfileId = null
                        profileManagementVisible = true
                    },
                )
            else ->
                TvAuthenticatedApp(
                    playbackController = playbackController,
                    playerEngine = playerEngine,
                    trailerPreviewPlaybackController = trailerPreviewController,
                    trailerPreviewEngine = trailerPreviewEngine,
                    appVersion = appVersion,
                    settingsRepository = settingsRepository,
                    serverRepository = serverRepository,
                    strings = strings,
                    stopPlayback = stopPlayback,
                    activeProfileName = selectedProfile.displayName,
                    activeProfileId = selectedProfile.id,
                    activeProfileGeneration = generation,
                    onOpenProfiles = ::openProfilePicker,
                    onAuthenticationExpired = {
                        // Parallel reports start the recovery once; only a successful sign-in lifts the mark.
                        if (sessionRecovery.markExpired(selectedProfile.id)) {
                            stopPlayback()
                            trailerPreviewController.stop(saveProgress = false)
                            scope.launch {
                                reconnectConnectionId =
                                    profileRepository.binding(selectedProfile.id)?.jellyfinConnectionId
                                activeProfiles.clear()
                                activatedProfileId = null
                                reconnectProfile = selectedProfile
                            }
                        }
                    },
                    voiceSearch = voiceSearch,
                    integrations = integrations,
                    onExitConfirmed = onExitConfirmed,
                )
        }
        removeProfile?.let { profile ->
            TvRemoveProfileDialog(
                profile = profile,
                strings = strings,
                onConfirm = {
                    scope.launch {
                        val wasActive = profile.id == selectedProfileId
                        if (wasActive) {
                            stopPlayback()
                            trailerPreviewController.stop(saveProgress = false)
                        }
                        removalCoordinator.remove(profile.id)
                        profilePinRevision += 1L
                        if (wasActive) {
                            activeProfiles.clear()
                            selectedProfileId = null
                            activatedProfileId = null
                        }
                        removeProfile = null
                        if (managedProfileId == profile.id) managedProfileId = null
                        pickerVisible = true
                    }
                },
                onDismiss = { removeProfile = null },
            )
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun TvAuthenticatedApp(
    playbackController: PlaybackController,
    playerEngine: AndroidPlayerEngine,
    trailerPreviewPlaybackController: PlaybackController,
    trailerPreviewEngine: AndroidPlayerEngine,
    appVersion: String,
    settingsRepository: AppSettingsRepository,
    serverRepository: ServerRepository,
    strings: TvStrings,
    stopPlayback: () -> Unit,
    activeProfileName: String = strings.profiles,
    activeProfileId: String? = null,
    activeProfileGeneration: Long = 0L,
    onOpenProfiles: () -> Unit = {},
    onAuthenticationExpired: () -> Unit = {},
    voiceSearch: TvVoiceSearchPort = UnsupportedTvVoiceSearch,
    integrations: TvPlatformIntegrations = TvPlatformIntegrations(),
    onExitConfirmed: () -> Unit = {},
) {
    val koin = remember { JellystackDI.koin }
    val rootScope = rememberCoroutineScope()
    val profileRepository = remember(koin) { koin.get<HouseholdProfileRepository>() }
    val activeJellyfinServer by
        serverRepository
            .observeActiveServer(ServerType.JELLYFIN)
            .collectAsStateWithLifecycle(initialValue = serverRepository.activeServer(ServerType.JELLYFIN))
    // Compose adapts the lifecycle-agnostic holder to process-death persistence at this root boundary.
    val appStateSaver =
        remember {
            Saver<TvAppStateHolder, String>(
                save = { TvAppStatePersistence.encode(it.snapshot()) },
                restore = { raw -> TvAppStatePersistence.decode(raw)?.let(::TvAppStateHolder) },
            )
        }
    val appStateHolder = rememberSaveable(saver = appStateSaver) { TvAppStateHolder() }
    LaunchedEffect(activeProfileGeneration) { appStateHolder.resetForGeneration(activeProfileGeneration) }
    val appUiState = appStateHolder.state
    val focusMemory = appStateHolder.focusMemory
    val authenticatedEnvironmentIdentity =
        activeJellyfinServer?.let { server ->
            (server.credentials as? StoredCredential.Jellyfin)?.let { credential ->
                TvAuthenticatedEnvironmentIdentity(
                    serverConnectionId = server.id,
                    principalId = credential.userId,
                )
            }
        }
    var lastBoundIdentity by remember { mutableStateOf<TvAuthenticatedEnvironmentIdentity?>(null) }
    LaunchedEffect(authenticatedEnvironmentIdentity) {
        if (lastBoundIdentity != null && lastBoundIdentity != authenticatedEnvironmentIdentity) stopPlayback()
        if (authenticatedEnvironmentIdentity == null) {
            appStateHolder.deactivateEnvironment()
        } else {
            appStateHolder.activateEnvironment(authenticatedEnvironmentIdentity)
        }
        lastBoundIdentity = authenticatedEnvironmentIdentity
    }
    // Never construct or compose account state while its restored owner is unvalidated. On a
    // principal change the old account group leaves composition before the clean generation enters.
    if (appUiState.environmentIdentity != authenticatedEnvironmentIdentity || authenticatedEnvironmentIdentity == null) {
        return
    }
    val accountGeneration =
        remember(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            TvAccountGeneration(authenticatedEnvironmentIdentity, rootScope)
        }
    val scope = accountGeneration.scope
    DisposableEffect(accountGeneration) {
        onDispose(accountGeneration::close)
    }
    val browseRepository = remember(accountGeneration) { koin.get<JellyfinBrowseRepository>() }
    val environmentProvider = remember(accountGeneration) { koin.get<JellyfinEnvironmentProvider>() }
    val seerrRepository = remember(accountGeneration) { koin.get<JellyseerrRepository>() }
    val seerrEnvironmentProvider = remember(accountGeneration) { koin.get<JellyseerrEnvironmentProvider>() }
    val sessionRepository =
        remember(accountGeneration) { koin.get<JellyfinSessionRepository>().isolatedSession() }
    val browseCoordinator =
        remember(accountGeneration) {
            JellyfinBrowseCoordinator(
                repository = browseRepository,
                scope = scope,
                favoritesStore =
                    koin
                        .get<JellyfinFavoritesStoreApi>()
                        .scoped(authenticatedEnvironmentIdentity.serverConnectionId),
            )
        }
    val homeSectionsRepository =
        remember(accountGeneration) { koin.get<HomeSectionsRepository>().isolatedSession() }
    val recommendationsCoordinator =
        remember(accountGeneration) {
            JellyseerrRecommendationsCoordinator(
                repository = seerrRepository,
                environmentProvider = seerrEnvironmentProvider,
                scope = scope,
            )
        }
    val requestsCoordinator =
        remember(accountGeneration) {
            JellyseerrRequestsCoordinator(
                repository = seerrRepository,
                environmentProvider = seerrEnvironmentProvider,
                scope = scope,
            )
        }
    val detailTrailerResolver =
        remember(accountGeneration) {
            DetailTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
                fetchSeerrTrailer = { tmdbId, isShow ->
                    koin.get<JellyseerrEnvironmentProvider>().current()?.let { environment ->
                        koin
                            .get<JellyseerrRepository>()
                            .fetchRecommendationDetail(
                                environment,
                                tmdbId,
                                if (isShow) {
                                    dev.jellystack.core.jellyseerr.JellyseerrMediaType.TV
                                } else {
                                    dev.jellystack.core.jellyseerr.JellyseerrMediaType.MOVIE
                                },
                            ).trailer
                    }
                },
            )
        }
    val localTrailerResolver =
        remember(accountGeneration) {
            LocalTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
            )
        }
    val trailerPreviewPlayer =
        remember(accountGeneration, trailerPreviewPlaybackController, trailerPreviewEngine, environmentProvider) {
            TvPlaybackTrailerPreviewPlayer(
                controller = trailerPreviewPlaybackController,
                engine = trailerPreviewEngine,
                environmentProvider = environmentProvider,
            )
        }
    val trailerPreviewCoordinator =
        remember(accountGeneration, localTrailerResolver, trailerPreviewPlayer) {
            TvTrailerPreviewController(
                scope = scope,
                resolve = { target ->
                    if (environmentProvider.current()?.serverKey != target.serverKey) {
                        null
                    } else {
                        localTrailerResolver.resolve(
                            LocalTrailerContext(
                                itemId = target.itemId,
                                isEpisode = target.isEpisode,
                                seriesId = target.seriesId,
                            ),
                        )
                    }
                },
                player = trailerPreviewPlayer,
            )
        }
    val deviceSettings by settingsRepository.settings.collectAsStateWithLifecycle()
    val profilePreferencesRepository = remember(koin) { koin.get<ProfilePreferencesRepository>() }
    val preferenceProfileId = activeProfileId ?: "legacy-tv-profile"
    val profilePreferences by
        profilePreferencesRepository
            .preferences(preferenceProfileId)
            .collectAsStateWithLifecycle()
    val settings =
        if (activeProfileId == null) {
            deviceSettings
        } else {
            profilePreferences.applyTo(deviceSettings)
        }
    val currentSettings = {
        val currentDeviceSettings = settingsRepository.settings.value
        activeProfileId?.let { profilePreferencesRepository.preferences(it).value.applyTo(currentDeviceSettings) }
            ?: currentDeviceSettings
    }

    fun push(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.push(route)
    }

    val playbackQueue = remember(accountGeneration) { PlaybackQueue() }
    val playbackLauncher =
        remember(accountGeneration, activeProfileId, playbackController) {
            TvPlaybackLauncher(
                scope = scope,
                sources =
                    TvPlaybackSources(
                        loadDetail = browseRepository::getItemDetail,
                        loadEpisodes = browseRepository::latestEpisodesToPlay,
                        loadListEntries = browseRepository::listEntriesOf,
                        queue = playbackQueue,
                    ),
                starter = controllerPlaybackStarter(playbackController, environmentProvider),
                currentSettings = currentSettings,
                openPlayer = { push(TvRoute.Player) },
            )
        }
    val syncPlay =
        remember(accountGeneration) {
            SyncPlayCoordinator(
                environmentProvider = environmentProvider,
                playbackController = playbackController,
                // A queue started by another group member opens the player route like a local play action.
                playItem = { itemId, positionMs ->
                    browseRepository.cachedItem(itemId)?.let { playbackLauncher.playForGroup(it, positionMs) }
                },
                onAccessDenied = { sessionRepository.refresh() },
                scope = scope,
            )
        }
    val browseState by browseCoordinator.state.collectAsStateWithLifecycle()
    val liveTvRepository = remember(accountGeneration) { koin.get<LiveTvRepository>() }
    val liveTv = rememberTvLiveTvAccess(liveTvRepository, scope, browseState.libraries)
    val homeState = remember(browseState, liveTv) { liveTv.libraries(browseState) }
    val homeSections by homeSectionsRepository.state.collectAsStateWithLifecycle()
    val recommendations by recommendationsCoordinator.state.collectAsStateWithLifecycle()
    val requests by requestsCoordinator.state.collectAsStateWithLifecycle()
    val details by recommendationsCoordinator.details.collectAsStateWithLifecycle()
    val myListRepository = remember(accountGeneration) { koin.get<ProfileMyListRepository>() }
    val myListProfileId = requiredProfileId(activeProfileId)
    val savedMedia by
        myListRepository
            .observeSavedMedia(myListProfileId)
            .collectAsStateWithLifecycle(initialValue = emptyList())
    var favoriteItems by remember(accountGeneration) { mutableStateOf<Map<String, JellyfinItem>>(emptyMap()) }
    var myList by remember(accountGeneration) { mutableStateOf<List<MyListEntry>>(emptyList()) }
    LaunchedEffect(accountGeneration, homeState.favorites) {
        favoriteItems =
            runCatching { browseRepository.refreshFavoriteItems().associateBy(JellyfinItem::id) }
                .getOrElse { emptyMap() }
    }
    LaunchedEffect(accountGeneration, homeState.favorites, favoriteItems, savedMedia) {
        myList =
            myListRepository.reconcile(
                profileId = myListProfileId,
                jellyfinFavoriteIds = homeState.favorites,
                resolveFavorite = { itemId -> favoriteItems[itemId] ?: browseRepository.cachedItem(itemId) },
                resolveIdentity = browseRepository::cachedItem,
            )
    }
    // Held without reading it here: it changes with every position tick, only the player route reads the value.
    val playbackStates = playbackController.state
    val playbackStateHolder = playbackStates.collectAsStateWithLifecycle()
    val syncPlayState by syncPlay.state.collectAsStateWithLifecycle()
    val playbackIdentity =
        activeJellyfinServer?.let { server ->
            (server.credentials as? StoredCredential.Jellyfin)?.let { credential ->
                TvJellyfinPlaybackIdentity(serverKey = server.id, userId = credential.userId)
            }
        }
    val trailerPreviewState by trailerPreviewCoordinator.state.collectAsStateWithLifecycle()
    val trailerPreviewProgress = remember(accountGeneration) { mutableStateOf(0f) }
    LaunchedEffect(accountGeneration, trailerPreviewPlaybackController) {
        trailerPreviewPlaybackController.state.collect { playbackState ->
            trailerPreviewProgress.value =
                (playbackState as? PlaybackState.Active)?.let { active ->
                    active.durationMs?.takeIf { it > 0L }?.let { active.positionMs.toFloat() / it.toFloat() }
                } ?: 0f
        }
    }
    val sessionState by sessionRepository.state.collectAsStateWithLifecycle()
    TvAuthExpiryEffect(
        signal = remember(koin) { koin.get<AuthExpirySignal>() },
        environmentProvider = environmentProvider,
        sessionRepository = sessionRepository,
        playbackStates = playbackStates,
        syncPlayError = syncPlayState.error,
    )
    LaunchedEffect(sessionState) {
        if ((sessionState as? JellyfinSessionState.Error)?.authenticationExpired == true) {
            onAuthenticationExpired()
        }
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    var savedSearchQuery by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) { mutableStateOf("") }
    var savedSearchSource by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            mutableStateOf(TvSearchSource.ALL.name)
        }
    var savedSearchMode by
        rememberSaveable(authenticatedEnvironmentIdentity, appUiState.activeProfileGeneration) {
            mutableStateOf(TvSearchMode.EDIT.name)
        }
    val searchCoordinator =
        remember(accountGeneration, scope, browseRepository, seerrRepository, seerrEnvironmentProvider, voiceSearch) {
            TvSearchCoordinator(
                scope = scope,
                initialSession =
                    TvSearchSessionState(
                        query = savedSearchQuery,
                        source = TvSearchSource.entries.firstOrNull { it.name == savedSearchSource } ?: TvSearchSource.ALL,
                        mode = TvSearchMode.entries.firstOrNull { it.name == savedSearchMode } ?: TvSearchMode.EDIT,
                    ),
                voiceSearch = voiceSearch,
                sources =
                    TvSearchSources(
                        jellyfin = browseRepository::searchItems,
                        seerr = { query ->
                            seerrEnvironmentProvider
                                .current()
                                ?.let { environment ->
                                    seerrRepository.search(environment, query)
                                }.orEmpty()
                        },
                    ),
            )
        }
    val searchState by searchCoordinator.state.collectAsStateWithLifecycle()
    val searchSession = searchState.session
    LaunchedEffect(searchSession) {
        savedSearchQuery = searchSession.query
        savedSearchSource = searchSession.source.name
        savedSearchMode = searchSession.mode.name
    }
    DisposableEffect(searchCoordinator) {
        onDispose(searchCoordinator::shutdown)
    }
    val playbackCommandRouter =
        remember(playbackController, syncPlay) {
            TvPlaybackCommandRouter(
                isSyncPlayActive = { syncPlay.state.value.currentGroup != null },
                requestSyncSeek = syncPlay::requestSeek,
                requestLocalSeek = playbackController::seekTo,
                requestSyncNext = syncPlay::requestNext,
                pauseCommands =
                    TvPlaybackPauseCommands(
                        requestSyncPause = syncPlay::requestPause,
                        requestSyncUnpause = syncPlay::requestUnpause,
                        pauseLocal = playbackController::pause,
                        resumeLocal = playbackController::resume,
                    ),
            )
        }
    val playbackCoordinators =
        rememberTvJellyfinPlaybackCoordinators(
            identity = playbackIdentity,
            playbackStates = playbackStates,
            controller = playbackController,
            router = playbackCommandRouter,
            sources =
                TvPlaybackCoordinatorSources(
                    environmentProvider = environmentProvider,
                    browseRepository = browseRepository,
                    settings = currentSettings,
                    images = { TvPlayerImages(homeState.imageBaseUrl, homeState.imageAccessToken) },
                    queue = playbackQueue,
                ),
        )
    val segmentCoordinator = playbackCoordinators.segment
    val continuationCoordinator = playbackCoordinators.continuation
    val segmentState by segmentCoordinator.state.collectAsStateWithLifecycle()
    val continuationState by continuationCoordinator.state.collectAsStateWithLifecycle()
    val extrasState by playbackCoordinators.extras.state.collectAsStateWithLifecycle()
    val timelineSegments by segmentCoordinator.timelineSegments.collectAsStateWithLifecycle()
    val syncPlayAccess =
        (sessionState as? JellyfinSessionState.Ready)?.capabilities?.syncPlayAccess
            ?: JellyfinSyncPlayAccess.NONE

    TvPlayerRoutePopEffect(
        onPlayerRoute = appUiState.currentRoute == TvRoute.Player,
        playback = { playbackStateHolder.value },
        start = playbackLauncher.startState,
        onPop = appStateHolder::popPlayerRoute,
    )

    fun openLibrary(
        libraryId: String,
        title: String?,
    ) {
        if (homeState.libraries.firstOrNull { it.id == libraryId }?.isLiveTv() == true) {
            push(TvRoute.LiveTv)
            return
        }
        val myListItems = myList.mapNotNull { it.jellyfinItem }
        val mode = tvLibraryEntryMode(libraryId, homeState, homeSections, myListItems, strings.libraryBrowseLabels())
        push(TvRoute.Library(libraryId, title, mode))
    }

    fun openJellyfinDetail(item: JellyfinItem) {
        // Collections and playlists list their entries; a detail page has nothing to show for them.
        val listRoute = item.itemListRoute()
        if (listRoute != null) {
            push(listRoute)
            return
        }
        appStateHolder.rememberDetailSource(item)
        push(TvRoute.JellyfinDetail(item.id))
    }

    fun openPerson(person: JellyfinPerson) {
        push(TvRoute.Person(person.id, person.name))
    }

    fun selectTopLevel(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.selectTopLevel(route)
    }

    fun openSettingsConnections() {
        push(tvConnectionsSettingsRoute())
    }

    // The Seerr detail route loads its own detail, so My List entries and a restored back stack get one too.
    fun openSeerr(item: JellyseerrSearchItem) {
        push(item.toTvRoute())
    }

    fun openSeerr(route: TvRoute.SeerrDetail) {
        push(route)
    }

    LaunchedEffect(
        accountGeneration,
        settings.useServerHomeSections,
        settings.appLanguage,
        serverRepository.currentServers(),
    ) {
        homeSectionsRepository.refresh(
            enabledByUser = settings.useServerHomeSections,
            language = settings.appLanguage.languageTag,
        )
    }
    LaunchedEffect(accountGeneration, serverRepository.currentServers()) {
        sessionRepository.refresh()
    }
    LaunchedEffect(syncPlayAccess) {
        syncPlay.updateAccess(syncPlayAccess)
    }
    LaunchedEffect(settings.trailerPreviewsEnabled) {
        trailerPreviewCoordinator.setEnabled(settings.trailerPreviewsEnabled)
    }
    LaunchedEffect(settings.trailerPreviewSoundEnabled) {
        trailerPreviewCoordinator.setSoundEnabled(settings.trailerPreviewSoundEnabled)
    }
    LaunchedEffect(settings.trailerPreviewDelayMillis) {
        trailerPreviewCoordinator.setFocusDelayMillis(settings.trailerPreviewDelayMillis.toLong())
    }
    LaunchedEffect(playbackStates) {
        playbackStates.collect { state ->
            if (state is PlaybackState.Active || state is PlaybackState.Preparing) trailerPreviewCoordinator.clearFocus()
        }
    }
    LaunchedEffect(accountGeneration, serverRepository.currentServers()) { trailerPreviewCoordinator.invalidateCache() }
    LaunchedEffect(lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.STARTED)) {
            appStateHolder.onForegrounded()
        } else {
            appStateHolder.onBackgrounded()
            trailerPreviewCoordinator.onBackgrounded()
        }
    }
    DisposableEffect(accountGeneration) {
        onDispose {
            browseCoordinator.shutdown()
            homeSectionsRepository.close()
            sessionRepository.close()
            recommendationsCoordinator.shutdown()
            requestsCoordinator.shutdown()
            syncPlay.close()
            trailerPreviewCoordinator.release()
        }
    }

    TvWatchNextSyncEffect(
        publisher = integrations.watchNext,
        enabled = deviceSettings.watchNextEnabled,
        account =
            TvWatchNextAccount(
                serverKey = authenticatedEnvironmentIdentity.serverConnectionId,
                principalId = authenticatedEnvironmentIdentity.principalId,
                profileId = activeProfileId,
            ),
        home = homeState,
    )
    TvDeepLinkOpenEffect(integrations.deepLinks, authenticatedEnvironmentIdentity.serverConnectionId) { itemId ->
        if (appStateHolder.state.currentRoute == TvRoute.Player) stopPlayback()
        selectTopLevel(TvRoute.Home)
        push(TvRoute.JellyfinDetail(itemId))
    }

    val itemLists = remember(accountGeneration) { TvItemListCoordinator(scope, browseRepository.itemListLoader()) }
    val itemListStates by itemLists.states.collectAsStateWithLifecycle()
    val currentRoute = appUiState.currentRoute
    val jellyfinServerKey = serverRepository.activeServer(ServerType.JELLYFIN)?.id
    // Progress and watched marks from other devices show up while Home stays open.
    TvLiveRefreshEffect(active = currentRoute is TvRoute.Home) {
        browseCoordinator.refreshHomeFeeds()
        homeSectionsRepository.refresh(
            enabledByUser = settings.useServerHomeSections,
            language = settings.appLanguage.languageTag,
            silent = true,
        )
    }

    fun focusCinematicTrailer(
        item: JellyfinItem,
        presentationId: String,
    ) {
        if (!currentRoute.allowsTrailerPreview() || jellyfinServerKey == null) return
        trailerPreviewCoordinator.focus(
            TvTrailerPreviewRequest(
                owner = TvTrailerPreviewOwner.CARD,
                presentationId = presentationId,
                target =
                    TvTrailerPreviewTarget(
                        serverKey = jellyfinServerKey,
                        itemId = item.id,
                        isEpisode = item.type.equals("Episode", true),
                        seriesId = item.seriesId,
                    ),
            ),
        )
    }
    val showRail =
        currentRoute is TvRoute.Home ||
            currentRoute is TvRoute.Library ||
            currentRoute is TvRoute.Search ||
            currentRoute is TvRoute.Discover ||
            currentRoute is TvRoute.Settings ||
            currentRoute is TvRoute.LiveTv ||
            currentRoute.itemListSource() != null
    val focusCoordinator =
        remember(appUiState.activeProfileGeneration) {
            TvFocusCoordinator<FocusRequester>(
                awaitFocusFrame = { withFrameNanos { } },
            )
        }
    DisposableEffect(appStateHolder, focusCoordinator) {
        appStateHolder.onRouteForgotten = focusCoordinator::forgetRemembered
        onDispose { appStateHolder.onRouteForgotten = {} }
    }
    val currentFocusRouteKey =
        currentRoute.focusRouteKey(
            if (currentRoute is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
        )
    val focusContentAuthoritativelyLoaded =
        when (currentRoute) {
            TvRoute.Home ->
                !homeState.isInitialLoading &&
                    !homeState.isHomeLoading &&
                    homeSections !is HomeSectionsState.Loading
            is TvRoute.Library -> !homeState.isLibraryLoading && !homeState.isPageLoading
            TvRoute.Search ->
                !searchState.jellyfin.isLoading && !searchState.seerr.isLoading
            TvRoute.Discover ->
                recommendations !is JellyseerrRecommendationsState.Loading &&
                    (recommendations as? JellyseerrRecommendationsState.Ready)
                        ?.rails
                        ?.values
                        ?.none { it.isLoading } != false
            is TvRoute.Person, is TvRoute.Collection, is TvRoute.Music ->
                itemListStates[requireNotNull(currentRoute.itemListSource()).key()] !is TvItemListState.Loading
            else -> true
        }
    val semanticRestorationSession =
        remember(focusCoordinator, currentFocusRouteKey) {
            TvSemanticFocusRestorationSession(
                snapshot = focusMemory.restore(currentFocusRouteKey),
                interactionRevision = focusCoordinator.currentInteractionRevision,
            )
        }
    val railRestorationSession =
        remember(focusCoordinator, appUiState.railExpanded, currentRoute) {
            TvSemanticFocusRestorationSession(
                snapshot = null,
                interactionRevision = focusCoordinator.currentInteractionRevision,
            )
        }
    val backDispatcher =
        TvAppBackDispatcher(
            holder = appStateHolder,
            libraryPathDepth = { homeState.browsePath.size },
            selectedLibraryId = { homeState.selectedLibraryId },
            popLibraryPath = browseCoordinator::navigateUp,
            cancelFocusRestoration = focusCoordinator::onUserMovement,
        )
    var showExitConfirmation by rememberSaveable(activeProfileGeneration) { mutableStateOf(false) }
    TvAppBackHandler(backDispatcher) { showExitConfirmation = true }
    TvRootFocusRestoration(
        focusCoordinator = focusCoordinator,
        appStateHolder = appStateHolder,
        focus =
            TvRootFocusState(
                currentRoute = currentRoute,
                routeKey = currentFocusRouteKey,
                showRail = showRail,
                railExpanded = appUiState.railExpanded,
                isForeground = appUiState.isForeground,
                contentAuthoritativelyLoaded = focusContentAuthoritativelyLoaded,
            ),
        sessions = TvRootFocusSessions(semanticRestorationSession, railRestorationSession),
    )
    LaunchedEffect(appUiState.railExpanded, currentRoute) {
        if (appUiState.railExpanded || !currentRoute.allowsTrailerPreview()) trailerPreviewCoordinator.clearFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    trailerPreviewCoordinator.onUserInteraction()
                }
                if (
                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode in
                    setOf(
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    )
                ) {
                    focusCoordinator.onUserMovement()
                }
                false
            },
    ) {
        CompositionLocalProvider(
            LocalTvNavigationRailOpener provides {
                if (!appStateHolder.state.railExpanded) {
                    focusCoordinator.onUserMovement()
                    appStateHolder.openRail()
                }
            },
        ) {
            val reducedMotion = LocalTvFocusAppearance.current.reducedMotion
            val screenTransition = remember(reducedMotion) { tvScreenTransition<TvRoute>(reducedMotion) }
            NavDisplay(
                backStack = appUiState.backStack,
                onBack = { backDispatcher.dispatch() },
                modifier = Modifier.fillMaxSize(),
                transitionSpec = screenTransition,
                popTransitionSpec = screenTransition,
                entryProvider =
                    rememberLatestNavEntryProvider { route ->
                        NavEntry(route) {
                            val entryRouteKey =
                                route.focusRouteKey(
                                    if (route is TvRoute.Library) homeState.browsePath.map { it.id } else emptyList(),
                                )
                            TvRouteFocusScope(focusCoordinator, entryRouteKey, focusMemory) {
                                when (route) {
                                    TvRoute.Home ->
                                        TvHomeScreen(
                                            state = homeState,
                                            homeSections = homeSections,
                                            strings = strings,
                                            trailerPreviewState = trailerPreviewState,
                                            focusMemory = focusMemory,
                                            onRefresh = {
                                                trailerPreviewCoordinator.invalidateCache()
                                                browseCoordinator.bootstrap(true)
                                            },
                                            onPreviewFocus = { owner, item, presentationId ->
                                                if (currentRoute.allowsTrailerPreview() && jellyfinServerKey != null) {
                                                    trailerPreviewCoordinator.focus(
                                                        TvTrailerPreviewRequest(
                                                            owner = owner,
                                                            presentationId = presentationId,
                                                            target =
                                                                TvTrailerPreviewTarget(
                                                                    serverKey = jellyfinServerKey,
                                                                    itemId = item.id,
                                                                    isEpisode = item.type.equals("Episode", true),
                                                                    seriesId = item.seriesId,
                                                                ),
                                                        ),
                                                    )
                                                }
                                            },
                                            onPreviewBlur = { owner, item, presentationId ->
                                                if (jellyfinServerKey != null) {
                                                    trailerPreviewCoordinator.clearFocus(
                                                        TvTrailerPreviewRequest(
                                                            owner = owner,
                                                            presentationId = presentationId,
                                                            target =
                                                                TvTrailerPreviewTarget(
                                                                    serverKey = jellyfinServerKey,
                                                                    itemId = item.id,
                                                                    isEpisode = item.type.equals("Episode", true),
                                                                    seriesId = item.seriesId,
                                                                ),
                                                        ),
                                                    )
                                                }
                                            },
                                            onCancelPreview = trailerPreviewCoordinator::clearFocus,
                                            trailerPreviewEngine = trailerPreviewEngine,
                                            previewSoundEnabled = settings.trailerPreviewSoundEnabled,
                                            previewProgress = trailerPreviewProgress,
                                            onPlayItem = { item ->
                                                trailerPreviewCoordinator.clearFocus()
                                                playbackLauncher.play(item)
                                            },
                                            onItem = {
                                                trailerPreviewCoordinator.clearFocus()
                                                openJellyfinDetail(it)
                                            },
                                            onHomeLibrary = ::openLibrary,
                                            onLibrary = { openLibrary(it.id, it.name) },
                                            onSeerrItem = ::openSeerr,
                                            myList = myList,
                                            onMyListEntry = { entry ->
                                                entry.jellyfinItem?.let(::openJellyfinDetail)
                                                    ?: entry.savedMedia?.toTvRoute()?.let(::push)
                                            },
                                            spotlightAutoAdvance = settings.spotlightAutoCycle,
                                            rowPreferences = settings.homeRows,
                                            heroHintsSeen = settings.heroHintsSeen,
                                            onHeroHintsUsed = { settingsRepository.setHeroHintsSeen(true) },
                                        )
                                    is TvRoute.Library -> {
                                        val library = homeState.libraries.firstOrNull { it.id == route.libraryId }
                                        val rememberedQuery =
                                            if (activeProfileId != null && route.libraryId != null) {
                                                profilePreferencesRepository
                                                    .libraryBrowseQuery(activeProfileId, route.libraryId)
                                                    .value
                                            } else {
                                                dev.jellystack.core.jellyfin.LibraryBrowseQuery.DEFAULT
                                            }
                                        TvLibraryScreen(
                                            route = route,
                                            state = homeState,
                                            strings = strings,
                                            focusMemory = focusMemory,
                                            onSelectLibrary = { id ->
                                                val library = homeState.libraries.firstOrNull { it.id == id }
                                                if (route.libraryId == null) {
                                                    openLibrary(id, library?.name)
                                                } else {
                                                    browseCoordinator.selectLibrary(id)
                                                }
                                            },
                                            onOpenItem = ::openJellyfinDetail,
                                            onOpenContainer = { container ->
                                                container.itemListRoute()?.let(::push) ?: browseCoordinator.openContainer(container)
                                            },
                                            onLoadMore = browseCoordinator::loadNextPage,
                                            onJumpToLetter = browseCoordinator::jumpToLibraryLetter,
                                            onLoadLatest = browseCoordinator::loadLibraryLatest,
                                            onLetterJumpHandled = browseCoordinator::consumeLibraryLetterJump,
                                            onRetry = {
                                                if (route.libraryId == null) {
                                                    browseCoordinator.refreshLibraries()
                                                } else {
                                                    browseCoordinator.refreshSelectedLibrary()
                                                }
                                            },
                                            homeSections = homeSections,
                                            myListItems = myList.mapNotNull { it.jellyfinItem },
                                            collectionType = library?.collectionType,
                                            rememberedQuery = rememberedQuery,
                                            onModeChanged = { mode ->
                                                when {
                                                    mode == route.mode -> Unit
                                                    mode == TvLibraryMode.ALL_TITLES -> push(route.copy(mode = mode))
                                                    route.mode == TvLibraryMode.ALL_TITLES -> appStateHolder.popRoute()
                                                    else -> Unit
                                                }
                                            },
                                            onQueryChanged = { query ->
                                                val profileId = activeProfileId
                                                val libraryId = route.libraryId
                                                if (profileId != null && libraryId != null) {
                                                    profilePreferencesRepository.setLibraryBrowseQuery(profileId, libraryId, query)
                                                }
                                                browseCoordinator.setLibraryBrowseQuery(query)
                                            },
                                            onPlayItem = { item ->
                                                trailerPreviewCoordinator.clearFocus()
                                                playbackLauncher.play(item)
                                            },
                                            cinematicModesEnabled = true,
                                            trailerPreviewState = trailerPreviewState,
                                            trailerPreviewEngine = trailerPreviewEngine,
                                            previewSoundEnabled = settings.trailerPreviewSoundEnabled,
                                            previewProgress = trailerPreviewProgress,
                                            onPreviewFocus = ::focusCinematicTrailer,
                                            onPreviewClear = { trailerPreviewCoordinator.clearFocus(TvTrailerPreviewOwner.CARD) },
                                        )
                                    }
                                    TvRoute.Search ->
                                        TvSearchScreen(
                                            searchState = searchState,
                                            homeState = homeState,
                                            strings = strings,
                                            focusMemory = focusMemory,
                                            actions = tvSearchActions(searchCoordinator, ::openJellyfinDetail, ::openSeerr),
                                        )
                                    TvRoute.Discover ->
                                        TvDiscoverScreen(
                                            recommendations,
                                            requests,
                                            strings,
                                            focusMemory,
                                            ::openSeerr,
                                            onConnectSeerr = ::openSettingsConnections,
                                            onRetry = recommendationsCoordinator::refreshAll,
                                            isSaved = { item ->
                                                savedMedia.any { it.identity == item.mediaIdentity() }
                                            },
                                        )
                                    is TvRoute.Settings ->
                                        TvSettingsScreen(
                                            section = route.section,
                                            settings = settings,
                                            repository = settingsRepository,
                                            serverRepository = serverRepository,
                                            connectionCoordinator = koin.get<ServerConnectionCoordinator>(),
                                            quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                                            appVersion = appVersion,
                                            strings = strings,
                                            onOpenCategory = { category -> push(tvSettingsRoute(category)) },
                                            onServersChanged = {
                                                browseCoordinator.bootstrap(true)
                                                recommendationsCoordinator.refreshAll()
                                            },
                                            profileId = activeProfileId,
                                            profilePreferencesRepository = profilePreferencesRepository,
                                            profileRepository = profileRepository,
                                            watchNextAvailable = integrations.watchNext.supported,
                                            homeRows = tvHomeRowOptions(homeState, homeSections, myList.isNotEmpty(), strings),
                                        )
                                    is TvRoute.JellyfinDetail ->
                                        TvJellyfinDetailScreen(
                                            route = route,
                                            initialItem = appStateHolder.detailSource(route.itemId),
                                            homeState = homeState,
                                            repository = browseRepository,
                                            browseCoordinator = browseCoordinator,
                                            environmentProvider = environmentProvider,
                                            playbackController = playbackController,
                                            playbackLauncher = playbackLauncher,
                                            trailerResolver = detailTrailerResolver,
                                            strings = strings,
                                            onOpenItem = ::openJellyfinDetail,
                                            onOpenPerson = ::openPerson,
                                            onPlaybackStarted = { push(TvRoute.Player) },
                                        )
                                    is TvRoute.Person, is TvRoute.Collection, is TvRoute.Music ->
                                        TvItemListRoute(
                                            route = route,
                                            data =
                                                TvItemListRouteData(
                                                    routeKey = entryRouteKey,
                                                    coordinator = itemLists,
                                                    states = itemListStates,
                                                    images = TvLibraryGridImages(homeState.imageBaseUrl, homeState.imageAccessToken),
                                                    favorites = homeState.favorites,
                                                ),
                                            playback = TvItemListRoutePlayback(playbackLauncher, ::openJellyfinDetail),
                                            strings = strings,
                                            focusMemory = focusMemory,
                                        )
                                    TvRoute.LiveTv ->
                                        TvLiveTvRoute(
                                            coordinator = liveTv.coordinator,
                                            context =
                                                TvLiveTvContext(
                                                    images = TvLibraryGridImages(homeState.imageBaseUrl, homeState.imageAccessToken),
                                                    routeKey = entryRouteKey,
                                                    strings = strings,
                                                    focusMemory = focusMemory,
                                                ),
                                            launcher = playbackLauncher,
                                        )
                                    is TvRoute.SeerrDetail -> {
                                        val key = route.mediaType to route.tmdbId
                                        val routeItem = route.toSearchItem()
                                        // Ready means Seerr's server is known; a load before that ends without a result.
                                        val seerrReady = recommendations is JellyseerrRecommendationsState.Ready
                                        LaunchedEffect(key, seerrReady) {
                                            if (seerrReady) recommendationsCoordinator.loadDetail(routeItem)
                                        }
                                        TvSeerrDetailScreen(
                                            route = route,
                                            detailState = details[key],
                                            requestsState = requests,
                                            requestsCoordinator = requestsCoordinator,
                                            strings = strings,
                                            onOpenItem = ::openSeerr,
                                            isInMyList = savedMedia.any { it.identity == routeItem.mediaIdentity() },
                                            onToggleMyList = { item, save ->
                                                scope.launch {
                                                    if (save) {
                                                        myListRepository.saveSeerr(myListProfileId, item)
                                                    } else {
                                                        myListRepository.removeSeerr(myListProfileId, item)
                                                    }
                                                }
                                            },
                                            onRetry = { recommendationsCoordinator.reloadDetail(routeItem) },
                                        )
                                    }
                                    TvRoute.Player ->
                                        TvPlaybackScreen(
                                            controller = playbackController,
                                            engine = playerEngine,
                                            syncPlay = syncPlay,
                                            playbackState = playbackStateHolder.value,
                                            startState = playbackLauncher.startState,
                                            syncState = syncPlayState,
                                            segmentState = segmentState,
                                            continuationState = continuationState,
                                            seekBackSeconds = settings.seekBackSeconds,
                                            seekForwardSeconds = settings.seekForwardSeconds,
                                            subtitleTextSize = settings.subtitleTextSize,
                                            subtitleBackground = settings.subtitleBackground,
                                            videoScale = settings.videoScale,
                                            onVideoScaleSelected = settingsRepository::setVideoScale,
                                            matchRefreshRate = settings.matchRefreshRate,
                                            extrasState = extrasState,
                                            timelineSegments = timelineSegments,
                                            imageBaseUrl = homeState.imageBaseUrl,
                                            imageAccessToken = homeState.imageAccessToken,
                                            onSeekTo = playbackCommandRouter::seekTo,
                                            onSetPaused = playbackCommandRouter::setPaused,
                                            loadEpisodes = browseRepository::latestEpisodesForSeries,
                                            onPlayEpisode = playbackLauncher::play,
                                            onSkipSegment = segmentCoordinator::skip,
                                            onPlayNext = continuationCoordinator::playNext,
                                            onCancelAutoplay = continuationCoordinator::cancelAutoplay,
                                            strings = strings,
                                            startActions =
                                                TvPlayerStartActions(
                                                    onRetry = playbackLauncher::retryStart,
                                                    onCancel = playbackLauncher::cancelStart,
                                                    onFailureSeen = playbackLauncher::cancelStart,
                                                ),
                                            audioQueue =
                                                TvAudioQueueControls(
                                                    hasPrevious = { playbackQueue.entryBefore(it) != null },
                                                    playPrevious = playbackLauncher::playPrevious,
                                                ),
                                            stopPlayback = stopPlayback,
                                            onClose = {
                                                playbackLauncher.cancelStart()
                                                // Closing the player leaves a SyncPlay group, as on the phone.
                                                if (syncPlay.state.value.currentGroup != null) syncPlay.leaveGroup()
                                                stopPlayback()
                                                appStateHolder.popRoute()
                                            },
                                        )
                                }
                            }
                        }
                    },
            )
        }
        if (showRail) {
            CompositionLocalProvider(
                LocalTvFocusContext provides TvFocusContext(focusCoordinator, TV_FOCUS_RAIL_ROUTE),
            ) {
                TvNavigationRail(
                    selected = currentRoute,
                    expanded = appUiState.railExpanded,
                    strings = strings,
                    profileName = activeProfileName,
                    callbacks =
                        TvNavigationRailCallbacks(
                            onProfile = onOpenProfiles,
                            onSelected = { route ->
                                selectTopLevel(route)
                                focusCoordinator.onUserMovement()
                                appStateHolder.closeRail()
                            },
                            onDismiss = {
                                focusCoordinator.onUserMovement()
                                appStateHolder.closeRail()
                            },
                        ),
                )
            }
        }
        playbackLauncher.pendingAsk?.let { ask ->
            TvResumeAskDialog(positionLabel = ask.positionLabel, strings = strings, onAnswer = playbackLauncher::answerAsk)
        }
        if (showExitConfirmation) {
            TvExitConfirmationDialog(
                strings = strings,
                onConfirm = {
                    showExitConfirmation = false
                    onExitConfirmed()
                },
                onDismiss = { showExitConfirmation = false },
            )
        }
    }
}

@Composable
internal fun TvExitConfirmationDialog(
    strings: TvStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocusRequester = rememberTvDialogInitialFocus()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(560.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.messages.exitAppTitle, color = TvText, fontSize = 28.sp)
            Text(strings.messages.exitAppMessage, color = TvTextMuted, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    label = strings.messages.exitApp,
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.width(180.dp).testTag("tv-exit-confirm"),
                )
                TvActionButton(
                    label = strings.cancel,
                    onClick = onDismiss,
                    focusRequester = cancelFocusRequester,
                    modifier = Modifier.testTag("tv-exit-cancel"),
                )
            }
        }
    }
}

internal fun TvRoute.allowsTrailerPreview(): Boolean = this is TvRoute.Home || this is TvRoute.Library || this is TvRoute.Search

private fun TvRoute.SeerrDetail.toSearchItem(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        overview = overview,
        releaseYear = releaseYear,
        posterPath = posterPath,
        backdropPath = backdropPath,
        mediaInfoId = null,
        tvdbId = tvdbId,
        availability = JellyseerrMediaAvailability(null, null),
        requests = emptyList(),
    )
