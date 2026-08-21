@file:Suppress("TooManyFunctions", "MaxLineLength", "LongMethod")

package app.jellystack.mobile

import android.Manifest
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import app.jellystack.mobile.cast.AndroidCastDiagnosticsStore
import app.jellystack.mobile.cast.CastConfig
import app.jellystack.mobile.cast.CastPermissionCoordinator
import app.jellystack.mobile.cast.CastPermissionUiState
import app.jellystack.mobile.cast.CastPickerHost
import app.jellystack.mobile.cast.requiredCastRuntimePermissions
import app.jellystack.mobile.playback.AndroidAutoplayPromptModel
import app.jellystack.mobile.playback.AndroidJellyfinMediaSegmentsService
import app.jellystack.mobile.playback.AndroidJellyfinPlaybackIdentity
import app.jellystack.mobile.playback.AndroidNetworkClassifier
import app.jellystack.mobile.playback.AndroidPlaybackCommandRouter
import app.jellystack.mobile.playback.androidAutoplayPromptModel
import app.jellystack.mobile.playback.rememberAndroidPlaybackCoordinators
import app.jellystack.mobile.playback.segmentSkipMode
import app.jellystack.mobile.playback.selectNextEpisode
import app.jellystack.mobile.ui.AndroidPlaybackSurface
import app.jellystack.mobile.ui.PermissionAwareCastRouteButton
import app.jellystack.mobile.ui.PlayerSystemUiController
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastStateListener
import com.russhwolf.settings.SharedPreferencesSettings
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.downloads.AndroidOfflineDownloadManager
import dev.jellystack.core.downloads.SettingsOfflineDownloadQueueStore
import dev.jellystack.core.downloads.SettingsOfflineMediaStore
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.playback.JellyfinOfflineProgressSyncer
import dev.jellystack.core.playback.JellyfinStreamingProgressReporter
import dev.jellystack.core.playback.SettingsOfflinePlaybackEventStore
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppPlatformCapabilities
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.privacy.AppPrivacyStatus
import dev.jellystack.core.privacy.RuntimePermissionStatus
import dev.jellystack.core.security.BiometricAuthGate
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.design.JellystackOrientation
import dev.jellystack.design.JellystackRoot
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.players.AndroidOfflinePlaybackSourceResolver
import dev.jellystack.players.AndroidPlaybackDeviceProfileProvider
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.JellyfinPlaybackSourceResolver
import dev.jellystack.players.NetworkJellyfinPlaybackInfoService
import dev.jellystack.players.PlaybackContinuationCoordinator
import dev.jellystack.players.PlaybackContinuationTarget
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackNetworkClass
import dev.jellystack.players.PlaybackPreferencesProvider
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSeekAdapter
import dev.jellystack.players.PlaybackSegmentCoordinator
import dev.jellystack.players.PlaybackSegmentModeProvider
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.SettingsPlaybackProgressStore
import dev.jellystack.players.SettingsSubtitlePreferenceStore
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.GoogleCastSessionManager
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val CAST_PERMISSION_PREFERENCES = "jellystack_cast_permissions"
private const val CAST_PERMISSION_REQUESTED = "requested_before"
private const val CAST_NOTIFICATION_PERMISSION_HANDLED = "notification_permission_handled"
private const val SHELL_CAST_ROUTE_TAG = "cast_route_picker_top_bar"

class MainActivity : AppCompatActivity() {
    private val orientationEvents = MutableStateFlow(Configuration.ORIENTATION_UNDEFINED)
    private val privacyStatusEvents = MutableStateFlow(AppPrivacyStatus())
    private val showCastNotificationRationale = MutableStateFlow(false)
    private var environmentRef: AndroidPlaybackEnvironment? = null
    private val biometricGate by lazy { JellystackDI.koin.get<BiometricAuthGate>() }
    private val castPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = hasCastPermissions()
            Napier.d(tag = "Cast", message = "Permission result: $result (granted=$granted)")
            if (::castPermissionCoordinator.isInitialized) castPermissionCoordinator.onPermissionResult()
            refreshPrivacyStatus()
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            showCastNotificationRationale.value = false
            refreshPrivacyStatus()
        }
    private lateinit var castPermissionCoordinator: CastPermissionCoordinator
    private lateinit var castContext: CastContext
    private val castDiagnosticsStore = AndroidCastDiagnosticsStore()
    private val discoveredRoutes = mutableSetOf<String>()
    private val castStateListener =
        CastStateListener { state ->
            Napier.d(tag = "Cast", message = "Cast state changed: $state")
        }
    private var locationWarningShown = false
    private val mediaRouter by lazy { MediaRouter.getInstance(this) }
    private val mediaRouteSelector by lazy {
        MediaRouteSelector
            .Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastConfig.RECEIVER_APP_ID,
                ),
            ).build()
    }
    private val mediaRouterCallback =
        object : MediaRouter.Callback() {
            override fun onRouteAdded(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                discoveredRoutes.add(routeKey(route))
                updateDeviceCount()
                Napier.d(
                    tag = "Cast",
                    message = "Route discovered: ${route.name} (${route.description ?: "no description"}) (count=${discoveredRoutes.size})",
                )
            }

            override fun onRouteRemoved(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                discoveredRoutes.remove(routeKey(route))
                updateDeviceCount()
                Napier.d(tag = "Cast", message = "Route removed: ${route.name} (count=${discoveredRoutes.size})")
            }
        }
    private var isCastDiscoveryActive = false

    internal val playbackEnvironment: AndroidPlaybackEnvironment?
        get() = environmentRef

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        applyAppLanguage(
            JellystackDI.koin
                .get<AppSettingsRepository>()
                .settings.value.appLanguage,
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        PlayerSystemUiController(this).restoreApplicationBars()
        orientationEvents.value = resources.configuration.orientation
        val startOrientation = orientationEvents.value
        initCastContext()
        initCastPermissionCoordinator()
        refreshPrivacyStatus()
        setContent {
            JellystackApp(
                appContext = applicationContext,
                orientationFlow = orientationEvents,
                initialOrientation = startOrientation,
                onEnvironmentChanged = { environmentRef = it },
                castDiagnosticsStore = castDiagnosticsStore,
                castPermissionState = castPermissionCoordinator.state,
                privacyStatusFlow = privacyStatusEvents,
                showCastNotificationRationaleFlow = showCastNotificationRationale,
                onCastAction = castPermissionCoordinator::onCastAction,
                onRequestCastPermissions = castPermissionCoordinator::requestPermissions,
                onOpenCastSettings = castPermissionCoordinator::openSettings,
                onCastPickerConsumed = castPermissionCoordinator::onPickerConsumed,
                onAppLanguageChanged = ::applyAppLanguage,
                onCastSessionStarted = ::onCastSessionStarted,
                onRequestCastNotifications = ::requestCastNotificationPermission,
                onDismissCastNotifications = ::dismissCastNotificationPermission,
            )
        }
        sanitizeIntent()
    }

    private fun applyAppLanguage(language: AppLanguage) {
        val locales = LocaleListCompat.forLanguageTags(language.languageTag.orEmpty())
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        orientationEvents.value = newConfig.orientation
        sanitizeIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sanitizeIntent()
    }

    private fun sanitizeIntent() {
        this.intent = this.intent?.cloneFilter()
    }

    override fun onResume() {
        super.onResume()
        biometricGate.onAppForegrounded()
        if (::castPermissionCoordinator.isInitialized) {
            castPermissionCoordinator.refreshFromSystem()
        }
        refreshPrivacyStatus()
        if (::castPermissionCoordinator.isInitialized && castPermissionCoordinator.shouldRunDiscovery) {
            startCastDiscoveryIfReady()
        }
    }

    override fun onStart() {
        super.onStart()
        biometricGate.onAppForegrounded()
        initCastContext()
        if (::castContext.isInitialized) {
            castContext.addCastStateListener(castStateListener)
        }
        if (::castPermissionCoordinator.isInitialized) {
            castPermissionCoordinator.refreshFromSystem()
        }
        if (::castPermissionCoordinator.isInitialized && castPermissionCoordinator.shouldRunDiscovery) {
            startCastDiscoveryIfReady()
        }
    }

    override fun onPause() {
        biometricGate.onAppBackgrounded()
        stopCastDiscovery()
        super.onPause()
    }

    override fun onStop() {
        if (::castContext.isInitialized) {
            castContext.removeCastStateListener(castStateListener)
        }
        biometricGate.onAppBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            biometricGate.onAppBackgrounded()
        }
        super.onDestroy()
    }

    private fun requestCastDiscoveryPermissions() {
        val missing = pendingCastPermissions()
        if (missing.isEmpty()) {
            castPermissionCoordinator.onPermissionResult()
        } else {
            Napier.i(tag = "Cast", message = "Requesting Cast permissions: $missing")
            castPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun onCastSessionStarted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) return
        val preferences = getSharedPreferences(CAST_PERMISSION_PREFERENCES, MODE_PRIVATE)
        if (!preferences.getBoolean(CAST_NOTIFICATION_PERMISSION_HANDLED, false)) {
            showCastNotificationRationale.value = true
        }
    }

    private fun requestCastNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSharedPreferences(CAST_PERMISSION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(CAST_NOTIFICATION_PERMISSION_HANDLED, true)
            .apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun dismissCastNotificationPermission() {
        getSharedPreferences(CAST_PERMISSION_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putBoolean(CAST_NOTIFICATION_PERMISSION_HANDLED, true)
            .apply()
        showCastNotificationRationale.value = false
        refreshPrivacyStatus()
    }

    private fun refreshPrivacyStatus() {
        privacyStatusEvents.value =
            AppPrivacyStatus(
                nearbyDevices =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionStatus(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        RuntimePermissionStatus.NotApplicable
                    },
                legacyLocation =
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        if (
                            isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
                        ) {
                            RuntimePermissionStatus.Granted
                        } else {
                            RuntimePermissionStatus.NotGranted
                        }
                    } else {
                        RuntimePermissionStatus.NotApplicable
                    },
                notifications =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionStatus(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        RuntimePermissionStatus.NotApplicable
                    },
            )
    }

    private fun permissionStatus(permission: String): RuntimePermissionStatus =
        if (isPermissionGranted(permission)) {
            RuntimePermissionStatus.Granted
        } else {
            RuntimePermissionStatus.NotGranted
        }

    private fun initCastPermissionCoordinator() {
        val preferences = getSharedPreferences(CAST_PERMISSION_PREFERENCES, MODE_PRIVATE)
        castPermissionCoordinator =
            CastPermissionCoordinator(
                requestedBefore = preferences.getBoolean(CAST_PERMISSION_REQUESTED, false),
                permissionsGranted = ::hasCastPermissions,
                shouldShowRationale = {
                    pendingCastPermissions().any(::shouldShowRequestPermissionRationale)
                },
                launchPermissions = ::requestCastDiscoveryPermissions,
                startDiscovery = ::startCastDiscoveryIfReady,
                openAppSettings = {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                },
                persistRequestedBefore = { requested ->
                    preferences.edit().putBoolean(CAST_PERMISSION_REQUESTED, requested).apply()
                },
            )
    }

    private fun pendingCastPermissions(): List<String> =
        requiredCastRuntimePermissions(Build.VERSION.SDK_INT)
            .filterNot(::isPermissionGranted)

    private fun hasCastPermissions(): Boolean = pendingCastPermissions().isEmpty()

    private fun initCastContext() {
        if (::castContext.isInitialized) return
        runCatching { CastContext.getSharedInstance(this) }
            .onSuccess {
                castContext = it
                Napier.d(tag = "Cast", message = "CastContext initialised")
            }.onFailure { throwable ->
                Napier.e(tag = "Cast", throwable = throwable, message = "Failed to initialise CastContext")
            }
    }

    @Suppress("ReturnCount")
    private fun startCastDiscoveryIfReady() {
        initCastContext()
        if (!::castContext.isInitialized) return
        if (!hasCastPermissions()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !isLocationServicesEnabled()) {
            if (!locationWarningShown) {
                Toast
                    .makeText(
                        this,
                        getString(R.string.cast_location_services_required),
                        Toast.LENGTH_LONG,
                    ).show()
                locationWarningShown = true
            }
            Napier.w(tag = "Cast", message = "Location services disabled; discovery paused")
            return
        }

        if (isCastDiscoveryActive) return

        runCatching {
            val flags =
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                flags,
            )
            isCastDiscoveryActive = true
            locationWarningShown = false
            castDiagnosticsStore.onActiveScanChanged(true)
            Napier.d(tag = "Cast", message = "Cast discovery started (MediaRouter)")
        }.onFailure { throwable ->
            Napier.e(tag = "Cast", throwable = throwable, message = "Failed to start Cast discovery")
        }
    }

    private fun stopCastDiscovery() {
        if (!isCastDiscoveryActive) return
        runCatching {
            mediaRouter.removeCallback(mediaRouterCallback)
            isCastDiscoveryActive = false
            discoveredRoutes.clear()
            updateDeviceCount()
            Napier.d(tag = "Cast", message = "Cast discovery stopped")
        }.onFailure { throwable ->
            Napier.e(tag = "Cast", throwable = throwable, message = "Failed to stop Cast discovery")
        }
    }

    private fun updateDeviceCount() {
        castDiagnosticsStore.onDeviceCount(discoveredRoutes.size)
    }

    private fun routeKey(route: MediaRouter.RouteInfo): String = route.id ?: route.name.toString()

    private fun isLocationServicesEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            runCatching {
                Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF
            }.getOrDefault(false)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@OptIn(UnstableApi::class)
@Suppress("CyclomaticComplexMethod", "FunctionName", "LongParameterList")
@Composable
private fun JellystackApp(
    appContext: Context,
    orientationFlow: StateFlow<Int>,
    initialOrientation: Int,
    onEnvironmentChanged: (AndroidPlaybackEnvironment?) -> Unit,
    castDiagnosticsStore: AndroidCastDiagnosticsStore,
    castPermissionState: StateFlow<CastPermissionUiState>,
    privacyStatusFlow: StateFlow<AppPrivacyStatus>,
    showCastNotificationRationaleFlow: StateFlow<Boolean>,
    onCastAction: (CastPickerHost) -> Unit,
    onRequestCastPermissions: () -> Unit,
    onOpenCastSettings: () -> Unit,
    onCastPickerConsumed: (Long) -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onCastSessionStarted: () -> Unit,
    onRequestCastNotifications: () -> Unit,
    onDismissCastNotifications: () -> Unit,
) {
    val environment = rememberAndroidPlaybackEnvironment(appContext, castDiagnosticsStore)
    val appSettingsRepository = remember { JellystackDI.koin.get<AppSettingsRepository>() }
    val appSettings by appSettingsRepository.settings.collectAsStateWithLifecycle()
    val playbackState by environment.controller.state.collectAsStateWithLifecycle()
    val browseRepository = remember { JellystackDI.koin.get<JellyfinBrowseRepository>() }
    val environmentProvider = remember { JellystackDI.koin.get<JellyfinEnvironmentProvider>() }
    val sessionRepository = remember { JellystackDI.koin.get<JellyfinSessionRepository>() }
    val sessionState by sessionRepository.state.collectAsStateWithLifecycle()
    val serverRepository = remember { JellystackDI.koin.get<ServerRepository>() }
    val servers by serverRepository.observeServers().collectAsStateWithLifecycle()
    val activeJellyfinServer by
        serverRepository
            .observeActiveServer(ServerType.JELLYFIN)
            .collectAsStateWithLifecycle(initialValue = serverRepository.activeServer(ServerType.JELLYFIN))
    val playbackIdentity =
        activeJellyfinServer?.let { server ->
            (server.credentials as? StoredCredential.Jellyfin)?.let { credential ->
                AndroidJellyfinPlaybackIdentity(serverKey = server.id, userId = credential.userId)
            }
        }
    val syncPlayCoordinator =
        remember(environment.controller, environmentProvider, browseRepository) {
            SyncPlayCoordinator(
                environmentProvider = environmentProvider,
                playbackController = environment.controller,
                playItem = playItem@{ itemId, startPositionMs ->
                    val detail = browseRepository.getItemDetail(itemId) ?: return@playItem
                    val item =
                        browseRepository.cachedItem(itemId)
                            ?: JellyfinItem(
                                id = itemId,
                                libraryId = null,
                                name = detail.name,
                                sortName = detail.name,
                                overview = detail.overview,
                                type = "Video",
                                mediaType = "Video",
                                locationType = null,
                                taglines = detail.taglines,
                                parentId = null,
                                primaryImageTag = detail.primaryImageTag,
                                thumbImageTag = null,
                                backdropImageTag = detail.backdropImageTags.firstOrNull(),
                                seriesId = null,
                                seriesPrimaryImageTag = null,
                                seriesThumbImageTag = null,
                                seriesBackdropImageTag = null,
                                parentLogoImageTag = null,
                                runTimeTicks = detail.runTimeTicks,
                                positionTicks = startPositionMs * 10_000L,
                                playedPercentage = null,
                                productionYear = detail.productionYear,
                                premiereDate = detail.premiereDate,
                                communityRating = detail.communityRating,
                                officialRating = detail.officialRating,
                                indexNumber = null,
                                parentIndexNumber = null,
                                seriesName = null,
                                seasonId = null,
                                episodeTitle = null,
                                lastPlayed = null,
                            )
                    val playbackEnvironment = environmentProvider.current() ?: return@playItem
                    environment.controller.play(
                        PlaybackRequest.from(item, detail, startPolicy = PlaybackStartPolicy.RESUME),
                        playbackEnvironment,
                    )
                    if (startPositionMs > 0L) environment.controller.seekTo(startPositionMs)
                },
                onAccessDenied = { sessionRepository.refresh() },
            )
        }
    val syncPlayAccess =
        (sessionState as? JellyfinSessionState.Ready)?.capabilities?.syncPlayAccess
            ?: dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess.NONE
    LaunchedEffect(syncPlayAccess) {
        syncPlayCoordinator.updateAccess(syncPlayAccess)
    }
    LaunchedEffect(servers) {
        sessionRepository.refresh()
    }
    DisposableEffect(syncPlayCoordinator) {
        onDispose(syncPlayCoordinator::close)
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsStateWithLifecycle()
    val segmentHttpClient = remember { NetworkClientFactory.create(ClientConfig(installLogging = false)) }
    DisposableEffect(segmentHttpClient) {
        onDispose(segmentHttpClient::close)
    }
    val playbackCommandRouter =
        remember(environment.controller, syncPlayCoordinator) {
            AndroidPlaybackCommandRouter(
                isSyncPlayActive = { syncPlayCoordinator.state.value.currentGroup != null },
                requestSyncSeek = syncPlayCoordinator::requestSeek,
                requestPlaybackSeek = environment.controller::seekTo,
                requestSyncNext = syncPlayCoordinator::requestNext,
            )
        }
    val playbackCoordinators =
        rememberAndroidPlaybackCoordinators(
            identity = playbackIdentity,
            playbackState = playbackState,
            isForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED),
            createSegmentCoordinator = { coordinatorScope ->
                PlaybackSegmentCoordinator(
                    scope = coordinatorScope,
                    segmentService = AndroidJellyfinMediaSegmentsService(environmentProvider, segmentHttpClient),
                    modeProvider =
                        PlaybackSegmentModeProvider { type ->
                            appSettingsRepository.settings.value.segmentSkipMode(type)
                        },
                    seekAdapter = PlaybackSeekAdapter(playbackCommandRouter::seekTo),
                )
            },
            createContinuationCoordinator = { coordinatorScope ->
                PlaybackContinuationCoordinator(
                    scope = coordinatorScope,
                    modeProvider = { appSettingsRepository.settings.value.autoplayNextMode },
                    resolveNext = resolve@{ mediaId, seriesId ->
                        val cached = browseRepository.episodesForSeries(seriesId)
                        val episodes = if (cached.isEmpty()) browseRepository.refreshEpisodesForSeries(seriesId) else cached
                        val next = selectNextEpisode(episodes, mediaId) ?: return@resolve null
                        val detail = browseRepository.getItemDetail(next.id) ?: return@resolve null
                        val playbackEnvironment = environmentProvider.current() ?: return@resolve null
                        PlaybackContinuationTarget(next.id, next.episodeTitle ?: next.name) {
                            playbackCommandRouter.playNext {
                                environment.controller.play(
                                    PlaybackRequest.from(
                                        item = next,
                                        detail = detail,
                                        startPolicy = PlaybackStartPolicy.RESTART,
                                    ),
                                    playbackEnvironment,
                                )
                            }
                        }
                    },
                )
            },
        )
    val segmentCoordinator = playbackCoordinators.segment
    val continuationCoordinator = playbackCoordinators.continuation
    val segmentState by segmentCoordinator.state.collectAsStateWithLifecycle()
    val continuationState by continuationCoordinator.state.collectAsStateWithLifecycle()
    DisposableEffect(environment.controller, environment.downloadManager, environment.castManager) {
        onEnvironmentChanged(environment)
        onDispose {
            onEnvironmentChanged(null)
            environment.controller.release()
            environment.downloadManager.release()
            environment.castManager.release()
            environment.networkClassifier.release()
        }
    }
    val orientation by orientationFlow.collectAsStateWithLifecycle(initialValue = initialOrientation)
    val systemDarkTheme = isSystemInDarkTheme()
    var resolvedDarkTheme by remember { mutableStateOf(systemDarkTheme) }
    val permissionState by castPermissionState.collectAsStateWithLifecycle()
    val privacyStatus by privacyStatusFlow.collectAsStateWithLifecycle()
    val showNotificationRationale by showCastNotificationRationaleFlow.collectAsStateWithLifecycle()
    LaunchedEffect(environment.castManager) {
        environment.castManager.connectionState.collect { state ->
            if (state is CastConnectionState.Connected) {
                onCastSessionStarted()
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        JellystackRoot(
            defaultDarkTheme = systemDarkTheme,
            controller = environment.controller,
            downloadManager = environment.downloadManager,
            castSessionManager = environment.castManager,
            orientation =
                when (orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> JellystackOrientation.Landscape
                    Configuration.ORIENTATION_PORTRAIT -> JellystackOrientation.Portrait
                    else -> JellystackOrientation.Unknown
                },
            appVersion = BuildConfig.VERSION_NAME,
            onResolvedDarkThemeChanged = { resolvedDarkTheme = it },
            onAppLanguageChanged = onAppLanguageChanged,
            platformCapabilities = AppPlatformCapabilities.Android,
            privacyStatus = privacyStatus,
            castRouteButton = { castState ->
                PermissionAwareCastRouteButton(
                    castState = castState,
                    permissionState = permissionState,
                    host = CastPickerHost.Shell,
                    onAction = onCastAction,
                    onRequestPermissions = onRequestCastPermissions,
                    onOpenSettings = onOpenCastSettings,
                    onPickerConsumed = onCastPickerConsumed,
                    modifier = Modifier.testTag(SHELL_CAST_ROUTE_TAG),
                )
            },
        )
        JellystackTheme(isDarkTheme = resolvedDarkTheme) {
            AndroidPlaybackSurface(
                controller = environment.controller,
                playerEngine = environment.playerEngine,
                castSessionManager = environment.castManager,
                castRouteButton = { castState ->
                    PermissionAwareCastRouteButton(
                        castState = castState,
                        permissionState = permissionState,
                        host = CastPickerHost.Player,
                        onAction = onCastAction,
                        onRequestPermissions = onRequestCastPermissions,
                        onOpenSettings = onOpenCastSettings,
                        onPickerConsumed = onCastPickerConsumed,
                        iconTint = Color.White,
                    )
                },
                modifier = Modifier.fillMaxSize(),
                orientation = orientation,
                isDarkTheme = resolvedDarkTheme,
                seekBackSeconds = appSettings.seekBackSeconds,
                seekForwardSeconds = appSettings.seekForwardSeconds,
                subtitleTextSize = appSettings.subtitleTextSize,
                subtitleBackground = appSettings.subtitleBackground,
                segmentState = segmentState,
                continuationState = continuationState,
                onSkipSegment = segmentCoordinator::skip,
                onPlayNext = continuationCoordinator::playNext,
                syncPlayCoordinator = syncPlayCoordinator,
                canCreateSyncPlay =
                    (sessionState as? JellyfinSessionState.Ready)?.capabilities?.canCreateSyncPlay == true,
                canJoinSyncPlay =
                    (sessionState as? JellyfinSessionState.Ready)?.capabilities?.canJoinSyncPlay == true,
            )
        }
        androidAutoplayPromptModel(continuationState)?.let { prompt ->
            JellystackTheme(isDarkTheme = resolvedDarkTheme) {
                AutoplayNextPrompt(
                    pending = prompt,
                    onCancel = continuationCoordinator::cancelAutoplay,
                    onPlayNow = continuationCoordinator::playNext,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (showNotificationRationale) {
            JellystackTheme(isDarkTheme = resolvedDarkTheme) {
                AlertDialog(
                    onDismissRequest = onDismissCastNotifications,
                    title = { Text(stringResource(R.string.cast_notification_permission_title)) },
                    text = { Text(stringResource(R.string.cast_notification_permission_message)) },
                    confirmButton = {
                        Button(onClick = onRequestCastNotifications) {
                            Text(stringResource(R.string.cast_notification_permission_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissCastNotifications) {
                            Text(stringResource(R.string.cast_permission_not_now))
                        }
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
internal fun AutoplayNextPrompt(
    pending: AndroidAutoplayPromptModel,
    onCancel: () -> Unit,
    onPlayNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.player_up_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(pending.title, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.player_playing_in_seconds, pending.secondsRemaining), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPlayNow) { Text(stringResource(R.string.player_play_now)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.player_cancel)) }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberAndroidPlaybackEnvironment(
    appContext: Context,
    castDiagnosticsStore: AndroidCastDiagnosticsStore,
): AndroidPlaybackEnvironment {
    val playerEngine = remember { AndroidPlayerEngine(appContext) }
    val progressStore = rememberPlaybackProgressStore(appContext)
    val subtitlePreferenceStore = rememberSubtitlePreferenceStore(appContext)
    val offlineStores = rememberOfflineStores(appContext)
    val koin = remember { JellystackDI.koin }
    val appSettingsRepository = remember(koin) { koin.get<AppSettingsRepository>() }
    val appSettings by appSettingsRepository.settings.collectAsStateWithLifecycle()
    val networkClassifier = remember { AndroidNetworkClassifier(appContext) }
    val networkClass by networkClassifier.networkClass.collectAsStateWithLifecycle()
    val downloadNetworkAllowed = remember { MutableStateFlow(true) }
    LaunchedEffect(appSettings.downloadsWifiOnly, networkClass) {
        downloadNetworkAllowed.value =
            !appSettings.downloadsWifiOnly ||
            networkClass == PlaybackNetworkClass.UNMETERED
    }
    val castContext = remember { CastContext.getSharedInstance(appContext) }
    val castManager =
        remember(castContext, castDiagnosticsStore) {
            GoogleCastSessionManager(appContext, castContext, castDiagnosticsStore)
        }
    val browseRepository = remember(koin) { koin.get<JellyfinBrowseRepository>() }
    val progressSyncer =
        remember(browseRepository, offlineStores.progress) {
            JellyfinOfflineProgressSyncer(
                repository = browseRepository,
                store = offlineStores.progress,
            )
        }
    val streamingReporter = remember(browseRepository) { JellyfinStreamingProgressReporter(browseRepository) }
    val downloadManager =
        remember(offlineStores, downloadNetworkAllowed) {
            AndroidOfflineDownloadManager(
                context = appContext,
                mediaStore = offlineStores.media,
                queueStore = offlineStores.queue,
                networkAllowed = downloadNetworkAllowed,
            )
        }
    val playbackSourceResolver =
        remember {
            JellyfinPlaybackSourceResolver(
                playbackInfoService = NetworkJellyfinPlaybackInfoService(),
                deviceProfileProvider = AndroidPlaybackDeviceProfileProvider(),
            )
        }
    val controller =
        remember(
            playerEngine,
            progressStore,
            playbackSourceResolver,
            subtitlePreferenceStore,
            offlineStores,
            progressSyncer,
            streamingReporter,
            appSettingsRepository,
            networkClassifier,
        ) {
            PlaybackController(
                progressStore = progressStore,
                playbackSourceResolver = playbackSourceResolver,
                playerEngine = playerEngine,
                offlineMediaStore = offlineStores.media,
                offlineSourceResolver = AndroidOfflinePlaybackSourceResolver(offlineStores.media),
                offlineProgressSyncer = progressSyncer,
                streamingProgressReporter = streamingReporter,
                subtitlePreferenceStore = subtitlePreferenceStore,
                castSessionManager = castManager,
                playbackPreferencesProvider = PlaybackPreferencesProvider { appSettingsRepository.settings.value },
                playbackNetworkClassifier = networkClassifier,
            )
        }
    DisposableEffect(castManager, controller) {
        castManager.setSnapshotProvider { controller.currentCastSnapshot() }
        onDispose {
            castManager.setSnapshotProvider(null)
        }
    }
    return remember(playerEngine, controller, downloadManager, castManager, networkClassifier) {
        AndroidPlaybackEnvironment(
            playerEngine = playerEngine,
            controller = controller,
            downloadManager = downloadManager,
            castManager = castManager,
            castDiagnosticsStore = castDiagnosticsStore,
            networkClassifier = networkClassifier,
        )
    }
}

@Composable
private fun rememberPlaybackProgressStore(appContext: Context): SettingsPlaybackProgressStore =
    remember {
        val preferences =
            appContext.getSharedPreferences(
                "jellystack_playback",
                MODE_PRIVATE,
            )
        SettingsPlaybackProgressStore(
            settings = SharedPreferencesSettings(preferences),
        )
    }

@Composable
private fun rememberSubtitlePreferenceStore(appContext: Context): SettingsSubtitlePreferenceStore =
    remember {
        val preferences =
            appContext.getSharedPreferences(
                "jellystack_playback",
                MODE_PRIVATE,
            )
        SettingsSubtitlePreferenceStore(
            settings = SharedPreferencesSettings(preferences),
        )
    }

@Composable
private fun rememberOfflineStores(appContext: Context): OfflineStores =
    remember {
        val preferences =
            appContext.getSharedPreferences(
                "jellystack_downloads",
                MODE_PRIVATE,
            )
        val settings = SharedPreferencesSettings(preferences)
        OfflineStores(
            media = SettingsOfflineMediaStore(settings),
            queue = SettingsOfflineDownloadQueueStore(settings),
            progress = SettingsOfflinePlaybackEventStore(settings),
        )
    }

@OptIn(UnstableApi::class)
internal data class AndroidPlaybackEnvironment(
    val playerEngine: AndroidPlayerEngine,
    val controller: PlaybackController,
    val downloadManager: AndroidOfflineDownloadManager,
    val castManager: GoogleCastSessionManager,
    val castDiagnosticsStore: AndroidCastDiagnosticsStore,
    val networkClassifier: AndroidNetworkClassifier,
)

private data class OfflineStores(
    val media: SettingsOfflineMediaStore,
    val queue: SettingsOfflineDownloadQueueStore,
    val progress: SettingsOfflinePlaybackEventStore,
)
