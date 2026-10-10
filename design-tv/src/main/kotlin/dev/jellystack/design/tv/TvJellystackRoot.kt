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
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.core.jellyfin.LocalTrailerContext
import dev.jellystack.core.jellyfin.LocalTrailerResolver
import dev.jellystack.core.jellyfin.withBrowsableLibraries
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
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
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackStartPolicy
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
    val profiles by profileRepository.observeProfiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    var profileAvatarUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var profilePinRequired by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var profilePinRevision by rememberSaveable { mutableStateOf(0L) }
    var legacyChecked by remember { mutableStateOf(false) }
    var initialized by rememberSaveable { mutableStateOf(false) }
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
    var reconnectConnectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var recoverPinProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var addProfile by rememberSaveable { mutableStateOf(false) }
    var removeProfile by remember { mutableStateOf<HouseholdProfile?>(null) }
    var connectionsBeforeAdd by remember { mutableStateOf<Set<String>>(emptySet()) }

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
    LaunchedEffect(legacyChecked, profiles, initialized) {
        if (!legacyChecked || initialized) return@LaunchedEffect
        when (
            val initial =
                initialTvProfileState(
                    profiles = profiles,
                    coldLaunch = coldLaunch,
                    pickerWasVisible = pickerVisible,
                    rememberedProfileId = activeProfiles.profileId.value,
                    generation = generation,
                )
        ) {
            is TvProfileState.Content -> selectedProfileId = initial.profile.id
            is TvProfileState.Picker -> {
                activeProfiles.clear()
                pickerVisible = true
            }
            else -> Unit
        }
        initialized = true
    }
    LaunchedEffect(profiles.map { it.id }, selectedProfileId) {
        if (selectedProfileId != null && profiles.none { it.id == selectedProfileId }) {
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
            if (binding == null || jellyfin == null || jellyfin.type != ServerType.JELLYFIN) {
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

    fun selectProfile(profile: HouseholdProfile) {
        pinForManagement = false
        scope.launch {
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

    LaunchedEffect(initialized, pickerVisible, selectedProfileId, activatedProfileId) {
        val selected = profiles.firstOrNull { it.id == selectedProfileId }
        selected?.let { profile ->
            val activationNeeded = initialized && !pickerVisible
            val activationIdle = activatedProfileId != profile.id && switchingProfile == null
            if (activationNeeded && activationIdle) beginActivation(profile)
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

    Box(modifier.fillMaxSize().background(TvBackground)) {
        val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
        when {
            !legacyChecked || !initialized -> TvProfileStatusScreen(strings.loading)
            switchingProfile != null -> TvProfileStatusScreen(strings.switchingProfile)
            addProfile ->
                TvConnectionScreen(
                    coordinator = koin.get<ServerConnectionCoordinator>(),
                    quickConnectCoordinator = koin.get<JellyfinQuickConnectCoordinator>(),
                    appVersion = appVersion,
                    strings = strings,
                    onConnected = {
                        scope.launch {
                            val connected =
                                serverRepository
                                    .currentServers()
                                    .filter { it.type == ServerType.JELLYFIN && it.id !in connectionsBeforeAdd }
                                    .maxByOrNull { it.updatedAt }
                                    ?: return@launch
                            val username = (connected.credentials as? StoredCredential.Jellyfin)?.username
                            val created =
                                profileRepository.createProfile(
                                    displayName = username?.takeIf(String::isNotBlank) ?: connected.name,
                                    jellyfinConnectionId = connected.id,
                                )
                            addProfile = false
                            beginActivation(created)
                        }
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
                    onAdd = {
                        connectionsBeforeAdd = servers.map { it.id }.toSet()
                        addProfile = true
                    },
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
                    onOpenProfiles = {
                        stopPlayback()
                        trailerPreviewController.stop(saveProgress = false)
                        activeProfiles.clear()
                        activatedProfileId = null
                        pickerVisible = true
                    },
                    onAuthenticationExpired = {
                        stopPlayback()
                        trailerPreviewController.stop(saveProgress = false)
                        scope.launch {
                            reconnectConnectionId = profileRepository.binding(selectedProfile.id)?.jellyfinConnectionId
                            activeProfiles.clear()
                            activatedProfileId = null
                            reconnectProfile = selectedProfile
                        }
                    },
                    voiceSearch = voiceSearch,
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
    val syncPlay =
        remember(accountGeneration) {
            SyncPlayCoordinator(
                environmentProvider = environmentProvider,
                playbackController = playbackController,
                playItem = { itemId, positionMs ->
                    val item = browseRepository.cachedItem(itemId) ?: return@SyncPlayCoordinator
                    val detail = browseRepository.getItemDetail(itemId) ?: return@SyncPlayCoordinator
                    val environment = environmentProvider.current() ?: return@SyncPlayCoordinator
                    playbackController.play(
                        PlaybackRequest
                            .from(item, detail, startPolicy = PlaybackStartPolicy.RESUME)
                            .copy(resumePositionTicks = positionMs * 10_000L),
                        environment,
                    )
                },
                onAccessDenied = { sessionRepository.refresh() },
                scope = scope,
            )
        }
    val browseState by browseCoordinator.state.collectAsStateWithLifecycle()
    val homeState = remember(browseState) { browseState.withBrowsableLibraries() }
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
    val playbackState by playbackController.state.collectAsStateWithLifecycle()
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
            )
        }
    val playbackCoordinators =
        rememberTvJellyfinPlaybackCoordinators(
            identity = playbackIdentity,
            playbackState = playbackState,
            controller = playbackController,
            router = playbackCommandRouter,
            sources =
                TvPlaybackCoordinatorSources(
                    environmentProvider = environmentProvider,
                    browseRepository = browseRepository,
                    settings = currentSettings,
                    images = { TvPlayerImages(homeState.imageBaseUrl, homeState.imageAccessToken) },
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

    fun push(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.push(route)
    }

    val playbackLauncher =
        remember(accountGeneration, activeProfileId, playbackController) {
            TvPlaybackLauncher(
                scope = scope,
                loadDetail = browseRepository::getItemDetail,
                starter = controllerPlaybackStarter(playbackController, environmentProvider),
                currentSettings = currentSettings,
                onStarted = { push(TvRoute.Player) },
            )
        }

    fun openJellyfinDetail(item: JellyfinItem) {
        appStateHolder.rememberDetailSource(item)
        push(TvRoute.JellyfinDetail(item.id))
    }

    fun selectTopLevel(route: TvRoute) {
        trailerPreviewCoordinator.clearFocus()
        appStateHolder.selectTopLevel(route)
    }

    fun openSettingsConnections() {
        push(tvConnectionsSettingsRoute())
    }

    fun openSeerr(item: JellyseerrSearchItem) {
        recommendationsCoordinator.loadDetail(item)
        push(item.toTvRoute())
    }

    fun openSeerr(route: TvRoute.SeerrDetail) {
        val item = route.toSearchItem()
        recommendationsCoordinator.loadDetail(item)
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
    LaunchedEffect(playbackState) {
        if (playbackState is PlaybackState.Active || playbackState is PlaybackState.Preparing) {
            trailerPreviewCoordinator.clearFocus()
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

    val currentRoute = appUiState.currentRoute
    val jellyfinServerKey = serverRepository.activeServer(ServerType.JELLYFIN)?.id

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
            currentRoute is TvRoute.Settings
    val focusCoordinator =
        remember(appUiState.activeProfileGeneration) {
            TvFocusCoordinator<FocusRequester>(
                awaitFocusFrame = { withFrameNanos { } },
            )
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
    val focusRegistrationRevision = focusCoordinator.registrationRevision
    LaunchedEffect(
        showRail,
        appUiState.railExpanded,
        currentFocusRouteKey,
        appUiState.isForeground,
        focusContentAuthoritativelyLoaded,
        focusRegistrationRevision,
        semanticRestorationSession,
    ) {
        if (!showRail || !appUiState.isForeground) return@LaunchedEffect
        if (appUiState.railExpanded) {
            val railRestoration =
                railRestorationSession.restoreOnce(
                    coordinator = focusCoordinator,
                    routeKey = TV_FOCUS_RAIL_ROUTE,
                    preferredTargetId = tvRailTargetId(currentRoute),
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            if (railRestoration is TvFocusRestoration.Failed) {
                appStateHolder.closeRail()
                focusCoordinator.onUserMovement()
                focusCoordinator.restoreFocus(
                    routeKey = currentFocusRouteKey,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            }
        } else {
            // A missing content target must not turn route restoration (including Back) into a rail opener.
            semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
            if (!semanticRestorationSession.isPending) return@LaunchedEffect
            val semanticTargetId =
                semanticRestorationSession.preferredTargetId(
                    availableTargets = focusCoordinator.focusTargets(currentFocusRouteKey),
                    contentAuthoritativelyLoaded = focusContentAuthoritativelyLoaded,
                ) ?: return@LaunchedEffect
            val restoration =
                focusCoordinator.restoreFocus(
                    routeKey = currentFocusRouteKey,
                    preferredTargetId = semanticTargetId,
                    includeFallback = false,
                    requiredInteractionRevision = semanticRestorationSession.interactionRevision,
                    requestFocus = { requester -> runCatching { requester.requestFocus() }.getOrDefault(false) },
                )
            when (restoration) {
                is TvFocusRestoration.Focused -> semanticRestorationSession.complete()
                TvFocusRestoration.Cancelled ->
                    semanticRestorationSession.cancelAfterInteraction(focusCoordinator.currentInteractionRevision)
                TvFocusRestoration.Failed -> Unit
            }
        }
    }
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
            NavDisplay(
                backStack = appUiState.backStack,
                onBack = { backDispatcher.dispatch() },
                modifier = Modifier.fillMaxSize(),
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
                                            onHomeLibrary = { libraryId, title -> push(TvRoute.Library(libraryId, title)) },
                                            onLibrary = { push(TvRoute.Library(it.id, it.name)) },
                                            onSeerrItem = ::openSeerr,
                                            myList = myList,
                                            onMyListEntry = { entry ->
                                                entry.jellyfinItem?.let(::openJellyfinDetail)
                                                    ?: entry.savedMedia?.toTvRoute()?.let(::push)
                                            },
                                            spotlightAutoAdvance = settings.spotlightAutoCycle,
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
                                                    push(TvRoute.Library(id, library?.name))
                                                } else {
                                                    browseCoordinator.selectLibrary(id)
                                                }
                                            },
                                            onOpenItem = ::openJellyfinDetail,
                                            onOpenContainer = browseCoordinator::openContainer,
                                            onLoadMore = browseCoordinator::loadNextPage,
                                            onRetry = browseCoordinator::refreshSelectedLibrary,
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
                                            onToggleFavorite = { item -> scope.launch { browseCoordinator.toggleFavorite(item) } },
                                            onTogglePlayed = { item, played ->
                                                scope.launch {
                                                    browseRepository.setPlayedStatus(item.id, played)
                                                    browseCoordinator.refreshSelectedLibrary()
                                                }
                                            },
                                            cinematicModesEnabled = true,
                                            trailerPreviewState = trailerPreviewState,
                                            trailerPreviewEngine = trailerPreviewEngine,
                                            previewSoundEnabled = settings.trailerPreviewSoundEnabled,
                                            previewProgress = trailerPreviewProgress,
                                            onPreviewFocus = ::focusCinematicTrailer,
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
                                            onToggleSaved = { item ->
                                                scope.launch {
                                                    if (savedMedia.any { it.identity == item.mediaIdentity() }) {
                                                        myListRepository.removeSeerr(myListProfileId, item)
                                                    } else {
                                                        myListRepository.saveSeerr(myListProfileId, item)
                                                    }
                                                }
                                            },
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
                                            onSeerrConnected = { server ->
                                                activeProfileId?.let { profileId ->
                                                    profileRepository.binding(profileId)?.let { binding ->
                                                        profileRepository.bindConnections(
                                                            binding.copy(seerrConnectionId = server.id),
                                                        )
                                                    }
                                                }
                                            },
                                            profileId = activeProfileId,
                                            profilePreferencesRepository = profilePreferencesRepository,
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
                                            onPlaybackStarted = { push(TvRoute.Player) },
                                        )
                                    is TvRoute.SeerrDetail -> {
                                        val key = route.mediaType to route.tmdbId
                                        val routeItem = route.toSearchItem()
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
                                        )
                                    }
                                    TvRoute.Player ->
                                        TvPlaybackScreen(
                                            controller = playbackController,
                                            engine = playerEngine,
                                            syncPlay = syncPlay,
                                            playbackState = playbackState,
                                            syncState = syncPlayState,
                                            segmentState = segmentState,
                                            continuationState = continuationState,
                                            seekBackSeconds = settings.seekBackSeconds,
                                            seekForwardSeconds = settings.seekForwardSeconds,
                                            subtitleTextSize = settings.subtitleTextSize,
                                            subtitleBackground = settings.subtitleBackground,
                                            extrasState = extrasState,
                                            timelineSegments = timelineSegments,
                                            imageBaseUrl = homeState.imageBaseUrl,
                                            imageAccessToken = homeState.imageAccessToken,
                                            onSeekTo = playbackCommandRouter::seekTo,
                                            loadEpisodes = browseRepository::latestEpisodesForSeries,
                                            onPlayEpisode = playbackLauncher::play,
                                            onSkipSegment = segmentCoordinator::skip,
                                            onPlayNext = continuationCoordinator::playNext,
                                            onCancelAutoplay = continuationCoordinator::cancelAutoplay,
                                            strings = strings,
                                            stopPlayback = stopPlayback,
                                            onClose = {
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
    val cancelFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocusRequester.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(560.dp).background(TvSurfaceRaised, RoundedCornerShape(26.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings.exitAppTitle, color = TvText, fontSize = 28.sp)
            Text(strings.exitAppMessage, color = TvTextMuted, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    label = strings.exitApp,
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
