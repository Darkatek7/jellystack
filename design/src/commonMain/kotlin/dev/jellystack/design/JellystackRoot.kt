@file:Suppress("FunctionName")

package dev.jellystack.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jellystack.core.di.JellystackDI
import dev.jellystack.core.downloads.DownloadRequest
import dev.jellystack.core.downloads.DownloadStatus
import dev.jellystack.core.downloads.OfflineDownloadManager
import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.downloads.OfflineMediaKind
import dev.jellystack.core.downloads.OfflineMediaMetadata
import dev.jellystack.core.jellyfin.DetailTrailerContext
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.DetailTrailerSource
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinFavoritesStoreApi
import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.HomeSectionsRepository
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinSessionRepository
import dev.jellystack.core.jellyfin.JellyfinSessionState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.MediaDetailEnrichment
import dev.jellystack.core.jellyfin.MediaDetailEnrichmentLoader
import dev.jellystack.core.jellyfin.SeriesPlaybackReason
import dev.jellystack.core.jellyfin.SeriesPlaybackTarget
import dev.jellystack.core.jellyfin.SeriesPlaybackTargetResolver
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.core.jellyseerr.JellyseerrAuthenticator
import dev.jellystack.core.jellyseerr.JellyseerrEnvironmentProvider
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrMessage
import dev.jellystack.core.jellyseerr.JellyseerrMessageCode
import dev.jellystack.core.jellyseerr.JellyseerrMessageRecovery
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRepository
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.logging.TelemetryTracker
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.core.preferences.AppPlatformCapabilities
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.preferences.OnboardingPreferenceRepository
import dev.jellystack.core.preferences.ResumeMode
import dev.jellystack.core.preferences.ThemePreferenceRepository
import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.privacy.AppPrivacyStatus
import dev.jellystack.core.security.BiometricAuthGate
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricLockState
import dev.jellystack.core.server.ConnectivityException
import dev.jellystack.core.server.JellyfinConnectionInput
import dev.jellystack.core.server.JellyfinQuickConnectCoordinator
import dev.jellystack.core.server.JellyfinQuickConnectInput
import dev.jellystack.core.server.JellyfinQuickConnectState
import dev.jellystack.core.server.JellyfinSignInMethod
import dev.jellystack.core.server.ManagedServer
import dev.jellystack.core.server.SeerrConnectionResult
import dev.jellystack.core.server.SeerrLoginCredentials
import dev.jellystack.core.server.SeerrServerInput
import dev.jellystack.core.server.ServerAddressValidation
import dev.jellystack.core.server.ServerConnectionCoordinator
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.core.server.ServerType
import dev.jellystack.core.server.StoredCredential
import dev.jellystack.core.server.validateServerAddress
import dev.jellystack.design.biometric.rememberBiometricPlatformState
import dev.jellystack.design.cast.BindCastSnapshotProvider
import dev.jellystack.design.cast.CastRoutePickerButton
import dev.jellystack.design.cast.rememberPlatformCastSessionManager
import dev.jellystack.design.components.InsecureHttpWarning
import dev.jellystack.design.components.JellyfinQuickConnectStatus
import dev.jellystack.design.components.JellyfinSignInMethodSelector
import dev.jellystack.design.components.ModalFocusScope
import dev.jellystack.design.jellyfin.ImmersiveMediaDetailContent
import dev.jellystack.design.jellyfin.HomeSectionsScreen
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.jellyfin.JellyfinDetailLoadingSkeleton
import dev.jellystack.design.jellyfin.LibraryNavigationState
import dev.jellystack.design.jellyfin.LibraryRefreshTarget
import dev.jellystack.design.jellyfin.SeasonEpisodes
import dev.jellystack.design.jellyfin.buildSeasonEpisodes
import dev.jellystack.design.jellyfin.hasLocalMedia
import dev.jellystack.design.jellyfin.refreshTarget
import dev.jellystack.design.jellyfin.supportsPlayedStatus
import dev.jellystack.design.jellyseerr.DiscoverAction
import dev.jellystack.design.jellyseerr.DiscoverPendingOperation
import dev.jellystack.design.jellyseerr.DiscoverScreen
import dev.jellystack.design.jellyseerr.DiscoverSelectionContent
import dev.jellystack.design.jellyseerr.DiscoverUiState
import dev.jellystack.design.jellyseerr.reduce
import dev.jellystack.design.jellyseerr.requiresDestinationDispatch
import dev.jellystack.design.jellyseerr.toSearchItemOrNull
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.lifecycle.rememberAppForegroundActive
import dev.jellystack.design.navigation.BackStackSnapshot
import dev.jellystack.design.navigation.DetailOrigin
import dev.jellystack.design.navigation.DetailStackEntry
import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.LibraryDestination
import dev.jellystack.design.navigation.LibrarySection
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.navigation.ShellBackAction
import dev.jellystack.design.navigation.ShellModal
import dev.jellystack.design.navigation.ShellModalOwner
import dev.jellystack.design.navigation.dismissActiveShellModal
import dev.jellystack.design.navigation.nextBackAction
import dev.jellystack.design.navigation.publishIfCurrentDetailRequest
import dev.jellystack.design.navigation.rememberDestinationChangeDispatcher
import dev.jellystack.design.navigation.resolveLibraryDestinationTitle
import dev.jellystack.design.onboarding.OnboardingAction
import dev.jellystack.design.onboarding.OnboardingField
import dev.jellystack.design.onboarding.OnboardingScreen
import dev.jellystack.design.onboarding.OnboardingUiState
import dev.jellystack.design.onboarding.OnboardingValidationError
import dev.jellystack.design.onboarding.onboardingProgress
import dev.jellystack.design.onboarding.validateOnboarding
import dev.jellystack.design.settings.SettingsAction
import dev.jellystack.design.settings.SettingsConnectionHealth
import dev.jellystack.design.settings.SettingsScreen
import dev.jellystack.design.settings.SettingsSection
import dev.jellystack.design.settings.SettingsUiState
import dev.jellystack.design.settings.toSettingsConnectionUi
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellAction
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.shell.JellystackTopBar
import dev.jellystack.design.shell.ShellFeedback
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.design.theme.LocalThemeController
import dev.jellystack.design.theme.ThemeController
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.JellyfinDirectDownloadSourceResolver
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackNotice
import dev.jellystack.players.PlaybackRequest
import dev.jellystack.players.PlaybackSourceOptions
import dev.jellystack.players.PlaybackStartPolicy
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.PlaybackStreamSelection
import dev.jellystack.players.PlaybackStreamSelector
import dev.jellystack.players.ResolvedPlaybackSource
import dev.jellystack.players.ResolvedSubtitle
import dev.jellystack.players.SubtitleTrack
import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.NoopCastSessionManager
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.app_lock_authentication_unavailable
import jellystack_mobile.design.generated.resources.app_lock_enroll_device
import jellystack_mobile.design.generated.resources.app_lock_locked_heading
import jellystack_mobile.design.generated.resources.app_lock_prompt
import jellystack_mobile.design.generated.resources.app_lock_unlock_before_disable
import jellystack_mobile.design.generated.resources.app_lock_waiting
import jellystack_mobile.design.generated.resources.app_title
import jellystack_mobile.design.generated.resources.audio_track_switch_failed
import jellystack_mobile.design.generated.resources.base_url
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.cast_connection_failed
import jellystack_mobile.design.generated.resources.cast_connection_lost
import jellystack_mobile.design.generated.resources.cast_reconnect
import jellystack_mobile.design.generated.resources.cast_stopped
import jellystack_mobile.design.generated.resources.close
import jellystack_mobile.design.generated.resources.complete_required_fields
import jellystack_mobile.design.generated.resources.connect_jellyfin
import jellystack_mobile.design.generated.resources.connect_seerr
import jellystack_mobile.design.generated.resources.connect_server
import jellystack_mobile.design.generated.resources.continue_episode
import jellystack_mobile.design.generated.resources.download_connect_server
import jellystack_mobile.design.generated.resources.download_direct_required
import jellystack_mobile.design.generated.resources.download_episodes_already_queued
import jellystack_mobile.design.generated.resources.download_item_already_offline
import jellystack_mobile.design.generated.resources.download_no_episodes
import jellystack_mobile.design.generated.resources.download_no_source
import jellystack_mobile.design.generated.resources.download_season_already_queued
import jellystack_mobile.design.generated.resources.download_series_already_queued
import jellystack_mobile.design.generated.resources.download_source_unresolved
import jellystack_mobile.design.generated.resources.download_unavailable_device
import jellystack_mobile.design.generated.resources.downloads
import jellystack_mobile.design.generated.resources.email
import jellystack_mobile.design.generated.resources.favorite_update_failed
import jellystack_mobile.design.generated.resources.favorites
import jellystack_mobile.design.generated.resources.hide_password
import jellystack_mobile.design.generated.resources.item_detail_unavailable
import jellystack_mobile.design.generated.resources.jellyfin_account
import jellystack_mobile.design.generated.resources.libraries
import jellystack_mobile.design.generated.resources.library_connect_server_status
import jellystack_mobile.design.generated.resources.loading_episodes
import jellystack_mobile.design.generated.resources.movies
import jellystack_mobile.design.generated.resources.nav_discover
import jellystack_mobile.design.generated.resources.nav_admin
import jellystack_mobile.design.generated.resources.nav_library
import jellystack_mobile.design.generated.resources.no_playable_episode
import jellystack_mobile.design.generated.resources.onboarding_saving
import jellystack_mobile.design.generated.resources.onboarding_url_error
import jellystack_mobile.design.generated.resources.password
import jellystack_mobile.design.generated.resources.play
import jellystack_mobile.design.generated.resources.play_episode
import jellystack_mobile.design.generated.resources.playback_failed
import jellystack_mobile.design.generated.resources.playback_no_source
import jellystack_mobile.design.generated.resources.played_status_update_failed
import jellystack_mobile.design.generated.resources.quick_connect_description
import jellystack_mobile.design.generated.resources.quick_connect_seerr_manual
import jellystack_mobile.design.generated.resources.refresh_status
import jellystack_mobile.design.generated.resources.remove_server_failed
import jellystack_mobile.design.generated.resources.request_approval_failed
import jellystack_mobile.design.generated.resources.request_approved
import jellystack_mobile.design.generated.resources.request_delete_failed
import jellystack_mobile.design.generated.resources.request_duplicate
import jellystack_mobile.design.generated.resources.request_failed
import jellystack_mobile.design.generated.resources.request_media_id_missing
import jellystack_mobile.design.generated.resources.request_media_requeue_failed
import jellystack_mobile.design.generated.resources.request_media_requeued
import jellystack_mobile.design.generated.resources.request_permission_denied
import jellystack_mobile.design.generated.resources.request_refresh_failed
import jellystack_mobile.design.generated.resources.request_remove_media_failed
import jellystack_mobile.design.generated.resources.request_removed
import jellystack_mobile.design.generated.resources.request_search_failed
import jellystack_mobile.design.generated.resources.request_submitted
import jellystack_mobile.design.generated.resources.requests_title
import jellystack_mobile.design.generated.resources.retry
import jellystack_mobile.design.generated.resources.seerr_account
import jellystack_mobile.design.generated.resources.seerr_automatic_login
import jellystack_mobile.design.generated.resources.seerr_connect_jellyfin_first
import jellystack_mobile.design.generated.resources.select_cast_device
import jellystack_mobile.design.generated.resources.server_name
import jellystack_mobile.design.generated.resources.server_url_missing_protocol
import jellystack_mobile.design.generated.resources.show_password
import jellystack_mobile.design.generated.resources.shows
import jellystack_mobile.design.generated.resources.sign_in_with
import jellystack_mobile.design.generated.resources.use_different_account
import jellystack_mobile.design.generated.resources.username
import jellystack_mobile.design.generated.resources.version_label
import jellystack_mobile.design.generated.resources.view_changelog
import jellystack_mobile.design.generated.resources.whats_new_0143_audio
import jellystack_mobile.design.generated.resources.whats_new_0143_playback_sessions
import jellystack_mobile.design.generated.resources.whats_new_0143_search
import jellystack_mobile.design.generated.resources.whats_new_0143_seerr_permissions
import jellystack_mobile.design.generated.resources.whats_new_0143_server_addresses
import jellystack_mobile.design.generated.resources.whats_new_dialog_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.collections.buildList
import kotlin.collections.buildMap
import androidx.compose.ui.semantics.password as passwordSemantics

enum class JellystackOrientation {
    Portrait,
    Landscape,
    Unknown,
}

val LocalJellystackOrientation = staticCompositionLocalOf { JellystackOrientation.Unknown }

private const val DEFAULT_LEARN_MORE_URL = "https://discord.gg/8P73XVVtAf"
private const val DEFAULT_CHANGELOG_URL = "https://github.com/Darkatek7/jellystack/releases"
private const val OFF_SUBTITLE_TRACK_ID = "__off_subtitle__"

@Suppress("FunctionName")
@Composable
private fun DefaultWhatsNewHighlights(): List<String> =
    listOf(
        stringResource(Res.string.whats_new_0143_seerr_permissions),
        stringResource(Res.string.whats_new_0143_server_addresses),
        stringResource(Res.string.whats_new_0143_audio),
        stringResource(Res.string.whats_new_0143_playback_sessions),
        stringResource(Res.string.whats_new_0143_search),
    )

internal enum class ServerFormType {
    JELLYFIN,
    SEERR,
}

private sealed interface JellyfinDetailUiState {
    data object Hidden : JellyfinDetailUiState

    data class Loading(
        val item: JellyfinItem,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState

    data class Loaded(
        val item: JellyfinItem,
        val detail: JellyfinItemDetail,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState

    data class Error(
        val item: JellyfinItem,
        val message: String,
        val imageBaseUrl: String?,
        val imageAccessToken: String?,
    ) : JellyfinDetailUiState
}

private fun JellyfinDetailUiState.withImageInfo(
    imageBaseUrl: String?,
    imageAccessToken: String?,
): JellyfinDetailUiState =
    when (this) {
        JellyfinDetailUiState.Hidden -> this
        is JellyfinDetailUiState.Error -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
        is JellyfinDetailUiState.Loaded -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
        is JellyfinDetailUiState.Loading -> copy(imageBaseUrl = imageBaseUrl, imageAccessToken = imageAccessToken)
    }

internal data class ServerFormState(
    val serverId: String? = null,
    val type: ServerFormType = ServerFormType.JELLYFIN,
    val name: String = "",
    val baseUrl: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val jellyfinSignInMethod: JellyfinSignInMethod = JellyfinSignInMethod.QUICK_CONNECT,
    val useJellyfinLogin: Boolean = false,
    val automaticSeerrLogin: Boolean = false,
    val allowInsecureHttp: Boolean = false,
) {
    val serverAddressValidation: ServerAddressValidation
        get() = validateServerAddress(baseUrl)

    val requiresInsecureHttpConfirmation: Boolean
        get() =
            serverId == null &&
                baseUrl.trim().startsWith("http://", ignoreCase = true)

    val isValid: Boolean
        get() =
            (!requiresInsecureHttpConfirmation || allowInsecureHttp) &&
                serverAddressValidation is ServerAddressValidation.Valid &&
                when (type) {
                    ServerFormType.JELLYFIN ->
                        name.isNotBlank() &&
                            baseUrl.isNotBlank() &&
                            (
                                jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT ||
                                    (
                                        username.isNotBlank() &&
                                            (password.isNotBlank() || serverId != null)
                                    )
                            )
                    ServerFormType.SEERR ->
                        baseUrl.isNotBlank() &&
                            (
                                automaticSeerrLogin ||
                                    (
                                        password.isNotBlank() &&
                                            (
                                                (!useJellyfinLogin && email.isNotBlank()) ||
                                                    (useJellyfinLogin && username.isNotBlank())
                                            )
                                    )
                            )
                }
}

private data class ServerManagementUiState(
    val servers: List<ManagedServer> = emptyList(),
    val isDialogOpen: Boolean = false,
    val form: ServerFormState = ServerFormState(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

private val TutorialSequence =
    listOf(
        TutorialStep.Welcome,
        TutorialStep.ConnectJellyfin,
        TutorialStep.ConnectJellyseerr,
        TutorialStep.Explore,
    )

private data class PendingResumePlayback(
    val item: JellyfinItem,
    val detail: JellyfinItemDetail,
)

@Composable
private fun shellTitle(
    primary: PrimaryDestination,
    discover: DiscoverDestination,
    library: LibraryDestination,
): String =
    when (primary) {
        PrimaryDestination.Home -> stringResource(Res.string.app_title)
        PrimaryDestination.Library ->
            resolveLibraryDestinationTitle(
                destination = library,
                rootTitle = stringResource(Res.string.nav_library),
                sectionTitles =
                    mapOf(
                        LibrarySection.Downloads to stringResource(Res.string.downloads),
                        LibrarySection.Favorites to stringResource(Res.string.favorites),
                        LibrarySection.Libraries to stringResource(Res.string.libraries),
                        LibrarySection.Movies to stringResource(Res.string.movies),
                        LibrarySection.Series to stringResource(Res.string.shows),
                    ),
            )
        PrimaryDestination.Discover ->
            if (discover == DiscoverDestination.Requests) {
                stringResource(Res.string.requests_title)
            } else {
                stringResource(Res.string.nav_discover)
            }
        PrimaryDestination.Admin -> stringResource(Res.string.nav_admin)
    }

@Suppress("FunctionName", "ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellystackRoot(
    defaultDarkTheme: Boolean = true,
    controller: PlaybackController? = null,
    downloadManager: OfflineDownloadManager? = null,
    castSessionManager: CastSessionManager? = null,
    castConnectionStateFlow: Flow<CastConnectionState>? = null,
    castProgressFlow: Flow<Long>? = null,
    castRouteButton: (@Composable (CastConnectionState) -> Unit)? = null,
    orientation: JellystackOrientation = JellystackOrientation.Unknown,
    appVersion: String = "0.0.0",
    whatsNewHighlights: List<String>? = null,
    learnMoreUrl: String = DEFAULT_LEARN_MORE_URL,
    changelogUrl: String = DEFAULT_CHANGELOG_URL,
    onResolvedDarkThemeChanged: (Boolean) -> Unit = {},
    onAppLanguageChanged: (AppLanguage) -> Unit = {},
    platformCapabilities: AppPlatformCapabilities = AppPlatformCapabilities(),
    privacyStatus: AppPrivacyStatus = AppPrivacyStatus(),
) {
    var selectedSpotlightId by rememberSaveable { mutableStateOf<String?>(null) }
    check(JellystackDI.isStarted()) {
        "JellystackRoot requires application DI. Use JellystackPreviewFixture for previews and screenshots."
    }

    val resolvedWhatsNewHighlights = whatsNewHighlights ?: DefaultWhatsNewHighlights()
    val koin = remember { JellystackDI.koin }
    val platformCastManager = rememberPlatformCastSessionManager()
    val playbackController =
        remember(controller, koin, platformCastManager) {
            controller ?: PlaybackController(castSessionManager = platformCastManager ?: NoopCastSessionManager)
        }
    val ownsPlaybackController = controller == null
    val appForegroundActive = rememberAppForegroundActive()
    LaunchedEffect(appForegroundActive, playbackController) {
        if (appForegroundActive) {
            playbackController.flushOfflineProgress()
        }
    }
    DisposableEffect(playbackController, ownsPlaybackController) {
        onDispose {
            if (ownsPlaybackController) {
                playbackController.release()
            }
        }
    }
    val offlineDownloadManager = downloadManager
    val downloadStatusesFlow =
        remember(offlineDownloadManager) {
            offlineDownloadManager?.statuses
                ?: MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        }
    val downloadStatuses by downloadStatusesFlow.collectAsState()
    val offlineMediaFlow =
        remember(offlineDownloadManager) {
            offlineDownloadManager?.offlineMedia
                ?: MutableStateFlow<List<OfflineMedia>>(emptyList())
        }
    val offlineMedia by offlineMediaFlow.collectAsState()
    val themePreferences = remember(koin) { koin.get<ThemePreferenceRepository>() }
    val appSettingsRepository = remember(koin) { koin.get<AppSettingsRepository>() }
    val appSettings by appSettingsRepository.settings.collectAsState()
    val homeSectionsRepository = remember(koin) { koin.get<HomeSectionsRepository>() }
    val homeSectionsState by homeSectionsRepository.state.collectAsState()
    val environmentProvider = remember(koin) { koin.get<JellyfinEnvironmentProvider>() }
    val sessionRepository = remember(koin) { koin.get<JellyfinSessionRepository>() }
    val sessionState by sessionRepository.state.collectAsState()
    val sessionCapabilities = (sessionState as? JellyfinSessionState.Ready)?.capabilities
    val biometricGate = remember(koin) { koin.get<BiometricAuthGate>() }
    val biometricLockState by biometricGate.lockState.collectAsState()
    val biometricEnabled by biometricGate.isEnabled.collectAsState()
    val biometricCapability by biometricGate.capability.collectAsState()
    val biometricAutoPrompt by biometricGate.autoPrompt.collectAsState()
    val biometricPlatformState = rememberBiometricPlatformState()
    val biometricUnlocker = biometricPlatformState.unlocker
    LaunchedEffect(biometricGate, biometricPlatformState.capability) {
        biometricPlatformState.capability.collect { capability ->
            biometricGate.updateCapability(capability)
        }
    }
    LaunchedEffect(
        biometricEnabled,
        biometricAutoPrompt,
        biometricLockState,
        biometricUnlocker,
        biometricCapability,
    ) {
        if (
            biometricEnabled &&
            biometricAutoPrompt &&
            biometricCapability.isAuthenticationReady &&
            biometricUnlocker != null &&
            biometricLockState is BiometricLockState.Locked
        ) {
            biometricGate.unlock(biometricUnlocker)
        }
    }
    val isAppLockActive =
        biometricEnabled &&
            biometricLockState !is BiometricLockState.Disabled &&
            biometricLockState !is BiometricLockState.Unlocked
    val themeController =
        remember(themePreferences, defaultDarkTheme) {
            ThemeController(
                initialMode = themePreferences.currentMode(),
                initialSystemDark = defaultDarkTheme,
                onModeChanged = themePreferences::setMode,
            )
        }
    LaunchedEffect(defaultDarkTheme) {
        themeController.updateSystemDark(defaultDarkTheme)
    }
    val themeMode by themeController.mode.collectAsState()
    val isDarkTheme by themeController.isDark.collectAsState()
    LaunchedEffect(isDarkTheme) {
        onResolvedDarkThemeChanged(isDarkTheme)
    }
    val playbackState by playbackController.state.collectAsState()
    val resolvedCastSessionManager =
        when {
            castSessionManager != null -> castSessionManager
            playbackController.castManager !== NoopCastSessionManager -> playbackController.castManager
            platformCastManager != null -> platformCastManager
            else -> NoopCastSessionManager
        }
    val resolvedCastConnectionStateFlow =
        castConnectionStateFlow ?: resolvedCastSessionManager.connectionState
    val resolvedCastProgressFlow =
        castProgressFlow ?: resolvedCastSessionManager.remoteProgress
    BindCastSnapshotProvider(
        controller = playbackController,
        castSessionManager = resolvedCastSessionManager,
    )
    val castState by resolvedCastConnectionStateFlow.collectAsState(initial = CastConnectionState.Idle)

    @Suppress("UnusedVariable")
    val remoteProgress by resolvedCastProgressFlow.collectAsState(initial = 0L)
    var primaryDestination by rememberSaveable { mutableStateOf(PrimaryDestination.Home) }
    var discoverUiState by remember { mutableStateOf(DiscoverUiState()) }
    val discoverDestination = discoverUiState.destination
    var libraryNavigationState by remember { mutableStateOf(LibraryNavigationState()) }
    val detailRouteBackStack = remember { mutableStateListOf<DetailStackEntry>() }
    val detailUiBackStack = remember { mutableStateListOf<JellyfinDetailUiState>() }
    var detailRequestGeneration by remember { mutableStateOf(0L) }
    val detailState = detailUiBackStack.lastOrNull() ?: JellyfinDetailUiState.Hidden
    var detailEnrichmentById by
        remember { mutableStateOf<Map<String, MediaDetailEnrichment>>(emptyMap()) }
    var detailEnrichmentLoadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playedMutationPendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playedMutationError by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeShellModal by remember { mutableStateOf<ShellModalOwner?>(null) }
    var shellFeedback by remember { mutableStateOf<ShellFeedback?>(null) }
    var shellFeedbackId by remember { mutableStateOf(0L) }
    val destinationDispatcher = rememberDestinationChangeDispatcher()
    var detailEpisodeCache by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isDetailEpisodesLoading by remember { mutableStateOf(false) }
    var detailJob by remember { mutableStateOf<Job?>(null) }

    fun assertDetailStacksSynchronized() {
        check(detailRouteBackStack.size == detailUiBackStack.size) {
            "Detail route/UI stacks diverged: ${detailRouteBackStack.size} != ${detailUiBackStack.size}"
        }
    }

    fun clearDetailStacks() {
        detailJob?.cancel()
        detailRouteBackStack.clear()
        detailUiBackStack.clear()
        detailEnrichmentById = emptyMap()
        detailEnrichmentLoadingIds = emptySet()
        assertDetailStacksSynchronized()
    }

    fun updateLoadedPlayedStatus(
        itemId: String,
        isPlayed: Boolean,
    ) {
        detailUiBackStack.indices.forEach { index ->
            val state = detailUiBackStack[index]
            if (state is JellyfinDetailUiState.Loaded && state.item.id == itemId) {
                detailUiBackStack[index] = state.copy(detail = state.detail.copy(isPlayed = isPlayed))
            }
        }
    }

    fun selectPrimary(destination: PrimaryDestination) {
        destinationDispatcher.dispatch {
            if (destination != primaryDestination) {
                clearDetailStacks()
            }
            primaryDestination = destination
        }
    }
    var bulkDownloadJob by remember { mutableStateOf<Job?>(null) }
    var serverErrorMessage by remember { mutableStateOf<String?>(null) }
    var lastCastState by remember { mutableStateOf<CastConnectionState>(CastConnectionState.Idle) }
    val coroutineExceptionHandler =
        remember {
            CoroutineExceptionHandler { _, throwable ->
                JellystackLog.e("Unhandled coroutine failure", throwable)
                serverErrorMessage = throwable.connectivityErrorMessage()
            }
        }
    val coroutineScope =
        remember(coroutineExceptionHandler) {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineExceptionHandler)
        }
    DisposableEffect(coroutineScope) {
        onDispose { coroutineScope.cancel() }
    }

    fun showShellFeedback(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        shellFeedbackId += 1
        val feedbackId = shellFeedbackId
        shellFeedback =
            ShellFeedback(
                id = feedbackId,
                message = message,
                actionLabel = actionLabel,
                onAction = onAction,
                onDismiss = {
                    if (shellFeedback?.id == feedbackId) shellFeedback = null
                },
            )
    }

    LaunchedEffect(playbackController) {
        playbackController.notices.collect { notice ->
            when (notice) {
                PlaybackNotice.AudioTrackSelectionFailed ->
                    showShellFeedback(getString(Res.string.audio_track_switch_failed))
            }
        }
    }

    LaunchedEffect(castState) {
        val previous = lastCastState
        val current = castState
        val (message, actionLabel) =
            when {
                previous is CastConnectionState.Connected && current is CastConnectionState.Idle ->
                    getString(Res.string.cast_stopped, previous.deviceName) to getString(Res.string.cast_reconnect)
                previous is CastConnectionState.Connected && current is CastConnectionState.Error -> {
                    val reason = current.cause?.message?.takeIf { it.isNotBlank() }
                    val baseMessage = reason ?: getString(Res.string.cast_connection_lost, previous.deviceName)
                    baseMessage to getString(Res.string.cast_reconnect)
                }
                current is CastConnectionState.Error && previous !is CastConnectionState.Error -> {
                    val reason = current.cause?.message?.takeIf { it.isNotBlank() }
                    val baseMessage = reason ?: getString(Res.string.cast_connection_failed)
                    baseMessage to getString(Res.string.cast_reconnect)
                }
                else -> null to null
            }
        if (message != null) {
            showShellFeedback(
                message = message,
                actionLabel = actionLabel,
                onAction = { coroutineScope.launch { resolvedCastSessionManager.play() } },
            )
        }
        lastCastState = current
    }
    val streamSelector = remember { PlaybackStreamSelector() }
    val downloadSourceResolver = remember { JellyfinDirectDownloadSourceResolver() }

    val telemetry = remember { koin.get<TelemetryTracker>() }
    val browseRepository = remember { koin.get<JellyfinBrowseRepository>() }
    val favoritesStore = remember { koin.get<JellyfinFavoritesStoreApi>() }
    val jellyseerrRepository = remember { koin.get<JellyseerrRepository>() }
    val jellyseerrAuthenticator = remember { koin.get<JellyseerrAuthenticator>() }
    val serverConnectionCoordinator = remember { koin.get<ServerConnectionCoordinator>() }
    val jellyfinQuickConnectCoordinator = remember { koin.get<JellyfinQuickConnectCoordinator>() }
    val jellyseerrEnvironmentProvider = remember { koin.get<JellyseerrEnvironmentProvider>() }
    val jellyseerrCoordinator =
        remember(jellyseerrRepository, jellyseerrEnvironmentProvider, coroutineScope) {
            JellyseerrRequestsCoordinator(
                repository = jellyseerrRepository,
                environmentProvider = jellyseerrEnvironmentProvider,
                scope = coroutineScope,
                autoStart = false,
            )
        }
    val jellyseerrState by jellyseerrCoordinator.state.collectAsState()
    val recommendationsCoordinator =
        remember(jellyseerrRepository, jellyseerrEnvironmentProvider, coroutineScope) {
            JellyseerrRecommendationsCoordinator(
                repository = jellyseerrRepository,
                environmentProvider = jellyseerrEnvironmentProvider,
                scope = coroutineScope,
                autoStart = false,
            )
        }
    val recommendationsState by recommendationsCoordinator.state.collectAsState()
    val recommendationDetails by recommendationsCoordinator.details.collectAsState()
    val readyRequestsState = jellyseerrState as? JellyseerrRequestsState.Ready
    val languageProfiles = readyRequestsState?.languageProfiles ?: JellyseerrLanguageProfiles.EMPTY
    LaunchedEffect(readyRequestsState?.message?.id) {
        readyRequestsState?.message?.let { message ->
            val selectedItem = discoverUiState.selected?.item
            discoverUiState =
                discoverUiState.reduce(
                    DiscoverAction.OperationFinished(
                        code = message.code,
                        operationKey = message.operationKey,
                    ),
                )
            if (
                message.code in
                setOf(
                    JellyseerrMessageCode.RequestSubmitted,
                    JellyseerrMessageCode.RequestApproved,
                    JellyseerrMessageCode.RequestRemoved,
                    JellyseerrMessageCode.MediaRequeued,
                )
            ) {
                jellyseerrCoordinator.refresh()
                selectedItem?.let(recommendationsCoordinator::reloadDetail)
            }
        }
    }
    val serverRepository = remember { koin.get<ServerRepository>() }
    val managedServers by serverRepository.observeServers().collectAsState()
    val activeJellyfinServer by
        remember(serverRepository) { serverRepository.observeActiveServer(ServerType.JELLYFIN) }
            .collectAsState(initial = serverRepository.activeServer(ServerType.JELLYFIN))
    val activeSeerrServer by
        remember(serverRepository) { serverRepository.observeActiveServer(ServerType.JELLYSEERR) }
            .collectAsState(initial = serverRepository.activeServer(ServerType.JELLYSEERR))
    LaunchedEffect(activeJellyfinServer?.id) {
        sessionRepository.refresh()
    }
    LaunchedEffect(
        activeJellyfinServer?.id,
        appSettings.useServerHomeSections,
        appSettings.appLanguage,
    ) {
        homeSectionsRepository.refresh(
            enabledByUser = appSettings.useServerHomeSections,
            language = appSettings.appLanguage.languageTag,
        )
    }
    LaunchedEffect(sessionCapabilities?.isAdministrator) {
        if (primaryDestination == PrimaryDestination.Admin && sessionCapabilities?.isAdministrator != true) {
            primaryDestination = PrimaryDestination.Home
        }
    }
    val onboardingPreferences = remember { koin.get<OnboardingPreferenceRepository>() }
    val tutorialSteps = remember { TutorialSequence }
    val tutorialState = remember { onboardingPreferences.tutorialState() }
    var activeTutorialStep by remember { mutableStateOf(tutorialState.step) }
    var isTutorialVisible by remember { mutableStateOf(!tutorialState.isCompleted) }
    var onboardingIsFirstRun by remember { mutableStateOf(!tutorialState.isCompleted) }
    var automaticTutorialAdvanceAllowed by rememberSaveable { mutableStateOf(true) }
    var onboardingFieldErrors by
        remember { mutableStateOf<Map<OnboardingField, OnboardingValidationError>>(emptyMap()) }
    var tutorialFormHistory by
        remember { mutableStateOf<Map<TutorialStep, ServerFormState>>(emptyMap()) }
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    var pendingWhatsNew by remember { mutableStateOf(false) }
    val browseCoordinator =
        remember(browseRepository, favoritesStore, coroutineScope) {
            JellyfinBrowseCoordinator(
                repository = browseRepository,
                scope = coroutineScope,
                favoritesStore = favoritesStore,
                autoBootstrap = false,
            )
        }
    val browseState by browseCoordinator.state.collectAsState()
    val favorites by browseCoordinator.favorites.collectAsState()
    val favoriteError by browseCoordinator.favoriteError.collectAsState()
    val favoriteErrorText =
        favoriteError?.ifBlank { stringResource(Res.string.favorite_update_failed) }
    val playedStatusUpdateFailedMessage = stringResource(Res.string.played_status_update_failed)

    LaunchedEffect(favoriteError) {
        if (favoriteError != null) {
            delay(4_000L)
            browseCoordinator.clearFavoriteError()
        }
    }

    var hasBootstrappedBrowse by remember { mutableStateOf(false) }
    var hasStartedRequests by remember { mutableStateOf(false) }
    var hasStartedRecommendations by remember { mutableStateOf(false) }

    val ensureBrowseBootstrapped = {
        if (!hasBootstrappedBrowse) {
            browseCoordinator.bootstrap(forceRefresh = false)
            hasBootstrappedBrowse = true
        }
    }
    val ensureRequestsStarted = {
        if (!hasStartedRequests) {
            jellyseerrCoordinator.start()
            hasStartedRequests = true
        }
    }
    val ensureRecommendationsStarted = {
        if (!hasStartedRecommendations) {
            recommendationsCoordinator.start()
            hasStartedRecommendations = true
        }
    }

    val stopBrowse = {
        if (hasBootstrappedBrowse) {
            browseCoordinator.shutdown()
            hasBootstrappedBrowse = false
        }
    }
    val stopRequests = {
        if (hasStartedRequests) {
            jellyseerrCoordinator.shutdown()
            hasStartedRequests = false
        }
    }
    val stopRecommendations = {
        if (hasStartedRecommendations) {
            recommendationsCoordinator.shutdown()
            hasStartedRecommendations = false
        }
    }

    LaunchedEffect(isAppLockActive, biometricEnabled) {
        if (biometricEnabled && isAppLockActive) {
            stopBrowse()
            stopRequests()
            stopRecommendations()
        } else {
            ensureBrowseBootstrapped()
            ensureRequestsStarted()
            ensureRecommendationsStarted()
        }
    }

    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverFormState by remember { mutableStateOf(ServerFormState()) }
    var tutorialServerFormState by remember { mutableStateOf(ServerFormState()) }
    var isSavingServer by remember { mutableStateOf(false) }
    var jellyfinQuickConnectState by remember { mutableStateOf<JellyfinQuickConnectState?>(null) }
    var jellyfinQuickConnectJob by remember { mutableStateOf<Job?>(null) }
    var jellyfinQuickConnectGeneration by remember { mutableStateOf(0L) }
    var showQuickConnectSeerrExplanation by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var selectedSettingsSection by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var settingsConnectionHealth by
        remember { mutableStateOf<Map<String, SettingsConnectionHealth>>(emptyMap()) }
    val activePlaybackForDetail =
        (playbackState as? PlaybackState.Active)?.takeIf {
            val loaded = detailState as? JellyfinDetailUiState.Loaded
            loaded != null && it.mediaId == loaded.detail.id
        }

    fun presentWhatsNew() {
        showWhatsNewDialog = true
        activeShellModal =
            ShellModalOwner(ShellModal.WhatsNew) {
                showWhatsNewDialog = false
                activeShellModal = null
            }
    }

    fun dismissWhatsNew() {
        showWhatsNewDialog = false
        if (activeShellModal?.modal == ShellModal.WhatsNew) activeShellModal = null
    }

    LaunchedEffect(appVersion) {
        if (appVersion.isBlank()) return@LaunchedEffect
        val lastSeen = onboardingPreferences.lastSeenWhatsNewVersion()
        if (lastSeen != appVersion) {
            onboardingPreferences.setLastSeenWhatsNewVersion(appVersion)
            if (isTutorialVisible) {
                pendingWhatsNew = true
            } else {
                presentWhatsNew()
            }
        }
    }

    LaunchedEffect(isTutorialVisible) {
        if (!isTutorialVisible && pendingWhatsNew) {
            presentWhatsNew()
            pendingWhatsNew = false
        }
    }

    val showTutorialStep: (TutorialStep) -> Unit = { step ->
        val sanitizedCurrentForm = tutorialServerFormState.withoutSecrets()
        tutorialFormHistory =
            tutorialFormHistory + (activeTutorialStep to sanitizedCurrentForm)
        if (onboardingIsFirstRun) {
            onboardingPreferences.setTutorialStep(step)
        }
        activeTutorialStep = step
        isTutorialVisible = true
        serverErrorMessage = null
        onboardingFieldErrors = emptyMap()
        tutorialServerFormState =
            onboardingServerForm(
                step,
                tutorialFormHistory,
                activeJellyfinServer,
                activeSeerrServer,
            )
        tutorialFormHistory = tutorialFormHistory + (step to tutorialServerFormState)
    }

    val completeTutorial: () -> Unit = {
        if (onboardingIsFirstRun) {
            val completed = onboardingPreferences.markTutorialCompleted()
            activeTutorialStep = completed.step
        }
        isTutorialVisible = false
        onboardingIsFirstRun = false
        tutorialServerFormState = ServerFormState()
        tutorialFormHistory = emptyMap()
    }

    val restartTutorial: () -> Unit = {
        activeTutorialStep = TutorialStep.Welcome
        tutorialServerFormState = ServerFormState()
        tutorialFormHistory = emptyMap()
        onboardingFieldErrors = emptyMap()
        serverErrorMessage = null
        isTutorialVisible = true
        automaticTutorialAdvanceAllowed = true
        showQuickConnectSeerrExplanation = false
    }

    val closeTutorialToSettings: () -> Unit = {
        tutorialServerFormState = tutorialServerFormState.withoutSecrets()
        tutorialFormHistory = tutorialFormHistory.mapValues { (_, form) -> form.withoutSecrets() }
        isTutorialVisible = false
    }

    val goToNextTutorialStep: () -> Unit = {
        automaticTutorialAdvanceAllowed = true
        val index = tutorialSteps.indexOf(activeTutorialStep)
        if (index == -1 || index == tutorialSteps.lastIndex) {
            completeTutorial()
        } else {
            showTutorialStep(tutorialSteps[index + 1])
        }
    }

    val goToPreviousTutorialStep: () -> Unit = {
        val index = tutorialSteps.indexOf(activeTutorialStep)
        if (index > 0) {
            automaticTutorialAdvanceAllowed = false
            showTutorialStep(tutorialSteps[index - 1])
        }
    }
    val detailTrailerResolver =
        remember(browseRepository, jellyseerrRepository, jellyseerrEnvironmentProvider) {
            DetailTrailerResolver(
                fetchLocalTrailers = browseRepository::fetchLocalTrailers,
                fetchItemDetail = { browseRepository.getItemDetail(it, forceRefresh = false) },
                fetchSeerrTrailer = { tmdbId, isShow ->
                    jellyseerrEnvironmentProvider.current()?.let { environment ->
                        jellyseerrRepository
                            .fetchRecommendationDetail(
                                environment,
                                tmdbId,
                                if (isShow) JellyseerrMediaType.TV else JellyseerrMediaType.MOVIE,
                            ).trailer
                    }
                },
            )
        }
    val detailEnrichmentLoader =
        remember(browseRepository, jellyseerrRepository, jellyseerrEnvironmentProvider) {
            MediaDetailEnrichmentLoader(
                fetchSimilarItems = browseRepository::fetchSimilarItems,
                fetchSeerrDetail = { tmdbId, mediaType ->
                    jellyseerrEnvironmentProvider.current()?.let { environment ->
                        jellyseerrRepository.fetchRecommendationDetail(
                            environment = environment,
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                        )
                    }
                },
            )
        }
    var detailTrailerSource by remember { mutableStateOf<DetailTrailerSource?>(null) }

    LaunchedEffect(detailState) {
        detailTrailerSource = null
        val loaded = detailState as? JellyfinDetailUiState.Loaded ?: return@LaunchedEffect
        detailTrailerSource =
            detailTrailerResolver.resolve(
                DetailTrailerContext(
                    itemId = loaded.item.id,
                    isEpisode = loaded.item.type.equals("Episode", ignoreCase = true),
                    isSeries = loaded.item.type.equals("Series", ignoreCase = true),
                    seriesId = loaded.item.seriesId,
                    detail = loaded.detail,
                ),
            )
    }
    LaunchedEffect((detailState as? JellyfinDetailUiState.Loaded)?.item?.id) {
        val loaded = detailState as? JellyfinDetailUiState.Loaded ?: return@LaunchedEffect
        val itemId = loaded.item.id
        if (detailEnrichmentById.containsKey(itemId)) return@LaunchedEffect
        detailEnrichmentLoadingIds = detailEnrichmentLoadingIds + itemId
        try {
            val enrichment =
                detailEnrichmentLoader.load(
                    item = loaded.item,
                    detail = loaded.detail,
                )
            detailEnrichmentById = detailEnrichmentById + (itemId to enrichment)
        } finally {
            detailEnrichmentLoadingIds = detailEnrichmentLoadingIds - itemId
        }
    }
    LaunchedEffect(isTutorialVisible, activeTutorialStep, activeJellyfinServer, activeSeerrServer) {
        if (!isTutorialVisible) return@LaunchedEffect
        serverErrorMessage = null
        tutorialServerFormState =
            onboardingServerForm(
                activeTutorialStep,
                tutorialFormHistory,
                activeJellyfinServer,
                activeSeerrServer,
            )
        tutorialFormHistory =
            tutorialFormHistory + (activeTutorialStep to tutorialServerFormState)
    }

    LaunchedEffect(
        isTutorialVisible,
        activeTutorialStep,
        activeJellyfinServer?.id,
        activeSeerrServer?.id,
        isSavingServer,
        automaticTutorialAdvanceAllowed,
    ) {
        if (!isTutorialVisible || isSavingServer) return@LaunchedEffect
        tutorialAutoAdvanceDestination(
            step = activeTutorialStep,
            jellyfinConnected = activeJellyfinServer != null,
            seerrConnected = activeSeerrServer != null,
            automaticAdvanceAllowed = automaticTutorialAdvanceAllowed,
        )?.let { nextStep ->
            destinationDispatcher.dispatch { showTutorialStep(nextStep) }
        }
    }

    LaunchedEffect(jellyseerrState) {
        val ready = jellyseerrState as? JellyseerrRequestsState.Ready
        val requests =
            ready
                ?.currentRequestsByMedia
                ?.values
                ?.toList()
                ?.takeIf { it.isNotEmpty() }
                ?: ready?.requests.orEmpty()
        recommendationsCoordinator.updateRequests(requests)
    }

    val trackRecommendationEvent =
        remember(telemetry) {
            {
                    event: String,
                    rail: JellyseerrRecommendationRail,
                    item: JellyseerrSearchItem,
                    position: Int,
                    extras: Map<String, Any?>,
                ->
                val payload =
                    buildMap<String, Any?> {
                        put("rail", rail.name)
                        put("tmdbId", item.tmdbId)
                        put("mediaType", item.mediaType.name)
                        put("position", position)
                        put("availabilityStandard", item.availability.standard?.name)
                        put("availability4k", item.availability.`4k`?.name)
                        put("requested", item.isRequested)
                        putAll(extras)
                    }
                telemetry.track(event, payload)
            }
        }

    val onRecommendationOpenDetails:
        (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit =
        { rail, item, position ->
            trackRecommendationEvent("rec_card_tap", rail, item, position, emptyMap())
        }

    val onRecommendationImpression:
        (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit =
        { rail, item, position ->
            trackRecommendationEvent("rec_card_impression", rail, item, position, emptyMap())
        }

    val onRecommendationRequestOpen:
        (JellyseerrRecommendationRail, JellyseerrSearchItem, Int) -> Unit =
        { rail, item, position ->
            trackRecommendationEvent("rec_request_open", rail, item, position, emptyMap())
        }

    val onRecommendationTrailer:
        (JellyseerrRecommendationRail, JellyseerrSearchItem, Int, JellyseerrMediaTrailer?) -> Unit =
        { rail, item, position, trailer ->
            trackRecommendationEvent(
                "rec_details_trailer",
                rail,
                item,
                position,
                buildMap {
                    put("trailerSite", trailer?.site)
                    put("trailerKey", trailer?.key)
                },
            )
        }
    val loadedDetail = detailState as? JellyfinDetailUiState.Loaded
    val derivedSelection =
        remember(detailState, streamSelector) {
            val loaded = detailState as? JellyfinDetailUiState.Loaded ?: return@remember null
            if (loaded.detail.mediaSources.isEmpty()) {
                null
            } else {
                runCatching { streamSelector.select(loaded.detail.mediaSources) }.getOrNull()
            }
        }

    var preferredAudioTrackId by remember { mutableStateOf<String?>(null) }
    var preferredSubtitleTrackId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(derivedSelection?.sourceId, loadedDetail?.item?.id) {
        val audioTracks = derivedSelection?.audioTracks.orEmpty()
        preferredAudioTrackId =
            loadedDetail
                ?.item
                ?.let { item -> playbackController.resolveAudioPreference(item, audioTracks)?.id }
                ?: derivedSelection?.audioTracks?.defaultAudioTrackId()
        val subtitleTracks = derivedSelection?.subtitleTracks.orEmpty()
        val savedSubtitle =
            loadedDetail?.item?.let { item ->
                playbackController.resolveSubtitlePreference(item, subtitleTracks)
            }
        preferredSubtitleTrackId =
            when {
                savedSubtitle?.disabled == true -> OFF_SUBTITLE_TRACK_ID
                savedSubtitle?.trackId != null -> savedSubtitle.trackId
                else -> derivedSelection?.subtitleTracks?.defaultSubtitleTrackId()
            }
    }

    LaunchedEffect(activePlaybackForDetail) {
        val playback = activePlaybackForDetail ?: return@LaunchedEffect
        preferredAudioTrackId = playback.audioTrack?.id
        preferredSubtitleTrackId = playback.subtitleTrack?.id ?: OFF_SUBTITLE_TRACK_ID
    }

    val availableAudioTracks =
        activePlaybackForDetail?.stream?.audioTracks
            ?: derivedSelection?.audioTracks
            ?: emptyList()

    val availableSubtitleTracks =
        activePlaybackForDetail?.stream?.subtitleTracks
            ?: derivedSelection?.subtitleTracks
            ?: emptyList()

    val selectedAudioTrack =
        activePlaybackForDetail?.audioTrack
            ?: availableAudioTracks.firstOrNull { it.id == preferredAudioTrackId }

    val selectedSubtitleTrack =
        activePlaybackForDetail?.subtitleTrack
            ?: when (preferredSubtitleTrackId) {
                OFF_SUBTITLE_TRACK_ID -> null
                else -> availableSubtitleTracks.firstOrNull { it.id == preferredSubtitleTrackId }
            }

    val detailDownloadStatus = loadedDetail?.let { downloadStatuses[it.item.id] }
    val detailEpisodes =
        if (loadedDetail != null) {
            findEpisodesForDetail(
                state = loadedDetail,
                libraryItems = browseState.libraryItems,
                knownEpisodes = detailEpisodeCache,
            )
        } else {
            emptyList()
        }
    val detailSeasonGroups =
        if (loadedDetail != null) {
            buildSeasonEpisodes(detailEpisodes)
        } else {
            emptyList()
        }
    val detailAllEpisodes =
        if (loadedDetail != null) {
            detailSeasonGroups.flatMap { it.episodes }
        } else {
            emptyList()
        }
    val isEpisodeDetail =
        loadedDetail?.item?.type?.equals("Episode", ignoreCase = true) == true
    val isSeriesDetail =
        loadedDetail?.item?.type?.equals("Series", ignoreCase = true) == true
    val seriesPlaybackResolver = remember { SeriesPlaybackTargetResolver() }
    val completedDownloadIds =
        remember(downloadStatuses) {
            downloadStatuses.filterValues { it is DownloadStatus.Completed }.keys
        }
    val seriesPlaybackCandidates =
        remember(isSeriesDetail, detailEpisodes, completedDownloadIds) {
            if (isSeriesDetail) {
                seriesPlaybackResolver.orderedCandidates(detailEpisodes, completedDownloadIds)
            } else {
                emptyList()
            }
        }
    val seriesPlaybackTarget = seriesPlaybackCandidates.firstOrNull()
    val noPlayableSeriesMessage = stringResource(Res.string.no_playable_episode)
    val playbackNoSourceMessage = stringResource(Res.string.playback_no_source)
    val playbackFailedMessage = stringResource(Res.string.playback_failed)
    val connectPlaybackServerMessage = stringResource(Res.string.library_connect_server_status)
    val downloadNoSourceMessage = stringResource(Res.string.download_no_source)
    val downloadSourceUnresolvedMessage = stringResource(Res.string.download_source_unresolved)
    val downloadDirectRequiredMessage = stringResource(Res.string.download_direct_required)
    val downloadNoEpisodesMessage = stringResource(Res.string.download_no_episodes)
    val downloadEpisodesAlreadyQueuedMessage = stringResource(Res.string.download_episodes_already_queued)
    val downloadItemAlreadyOfflineMessage = stringResource(Res.string.download_item_already_offline)
    val downloadUnavailableDeviceMessage = stringResource(Res.string.download_unavailable_device)
    val downloadConnectServerMessage = stringResource(Res.string.download_connect_server)
    val downloadSeriesAlreadyQueuedMessage = stringResource(Res.string.download_series_already_queued)
    val downloadSeasonAlreadyQueuedMessage = stringResource(Res.string.download_season_already_queued)
    val itemDetailUnavailableMessage = stringResource(Res.string.item_detail_unavailable)
    val completeRequiredFieldsMessage = stringResource(Res.string.complete_required_fields)
    val removeServerFailedMessage = stringResource(Res.string.remove_server_failed)
    val quickConnectSeerrManualMessage = stringResource(Res.string.quick_connect_seerr_manual)
    val hasAnyServer = managedServers.isNotEmpty()
    val serverUiState =
        ServerManagementUiState(
            servers = managedServers,
            isDialogOpen = showAddServerDialog,
            form = serverFormState,
            isSaving = isSavingServer,
            errorMessage = serverErrorMessage,
        )

    var pendingResumePlayback by remember { mutableStateOf<PendingResumePlayback?>(null) }
    val launchPlayback: (JellyfinItem, JellyfinItemDetail, PlaybackStartPolicy) -> Unit = playback@{ item, detail, startPolicy ->
        val pendingAudio = preferredAudioTrackId
        val pendingSubtitle = preferredSubtitleTrackId
        val status = downloadStatuses[item.id]
        val hasOfflineSource = status is DownloadStatus.Completed
        val hasRemoteSource = detail.mediaSources.isNotEmpty()
        if (!hasOfflineSource && !hasRemoteSource) {
            serverErrorMessage = playbackNoSourceMessage
            return@playback
        }
        coroutineScope.launch {
            val environment = environmentProvider.current()
            if (environment != null) {
                val playbackRequest =
                    PlaybackRequest.from(
                        item = item,
                        detail = detail,
                        preferredAudioTrackId = pendingAudio,
                        preferredSubtitleTrackId = pendingSubtitle,
                        startPolicy = startPolicy,
                    )
                runCatching {
                    playbackController.play(playbackRequest, environment)
                }.onSuccess {
                    serverErrorMessage = null
                }.onFailure { error ->
                    JellystackLog.e("Playback failed for ${item.id}: ${error.message}", error)
                    serverErrorMessage = error.message ?: playbackFailedMessage
                }
            } else {
                serverErrorMessage = connectPlaybackServerMessage
                destinationDispatcher.dispatch { isSettingsOpen = true }
            }
        }
    }
    val playbackAction: (JellyfinItem, JellyfinItemDetail) -> Unit = { item, detail ->
        if (appSettings.resumeMode == ResumeMode.ASK && (item.positionTicks ?: 0L) > 0L) {
            pendingResumePlayback = PendingResumePlayback(item, detail)
        } else {
            launchPlayback(
                item,
                detail,
                if (appSettings.resumeMode == ResumeMode.RESTART) {
                    PlaybackStartPolicy.RESTART
                } else {
                    PlaybackStartPolicy.RESUME
                },
            )
        }
    }

    val pauseDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.pause(mediaId)
    }
    val resumeDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.resume(mediaId)
    }
    val removeDownload: (String) -> Unit = { mediaId ->
        offlineDownloadManager?.remove(mediaId)
    }

    suspend fun enqueueDownloadRequests(
        manager: OfflineDownloadManager,
        requests: List<DownloadRequest>,
    ): Int {
        if (requests.isEmpty()) return 0
        val snapshot = downloadStatuses
        var enqueued = 0
        requests.forEach { request ->
            val status = snapshot[request.mediaId]
            if (status !is DownloadStatus.InProgress && status !is DownloadStatus.Queued && status !is DownloadStatus.Completed) {
                manager.enqueue(request)
                enqueued += 1
                JellystackLog.d("Queued offline download for ${request.mediaId}")
            }
        }
        return enqueued
    }

    suspend fun enqueueDirectDownload(
        item: JellyfinItem,
        detail: JellyfinItemDetail,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
        showErrors: Boolean = true,
    ): Boolean {
        val playbackRequest = PlaybackRequest.from(item, detail)
        if (playbackRequest.mediaSources.isEmpty()) {
            if (showErrors) {
                serverErrorMessage = downloadNoSourceMessage
            }
            return false
        }
        val selection =
            runCatching { streamSelector.select(playbackRequest.mediaSources) }
                .getOrElse {
                    if (showErrors) {
                        serverErrorMessage = downloadSourceUnresolvedMessage
                    }
                    return false
                }
        if (selection.mode != PlaybackMode.DIRECT) {
            if (showErrors) {
                serverErrorMessage = downloadDirectRequiredMessage
            }
            return false
        }
        val resolved =
            downloadSourceResolver.resolve(
                request = playbackRequest,
                selection = selection,
                environment = environment,
                startPositionMs = 0L,
                options = PlaybackSourceOptions(),
            )
        val requests = buildDownloadRequests(item, playbackRequest, selection, resolved)
        val enqueued = enqueueDownloadRequests(manager, requests)
        return enqueued > 0
    }

    suspend fun queueDownloadsForEpisodes(
        episodes: List<JellyfinItem>,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
    ): Int {
        if (episodes.isEmpty()) return 0
        val uniqueEpisodes = episodes.distinctBy { it.id }
        var queuedCount = 0
        for (episode in uniqueEpisodes) {
            val status = downloadStatuses[episode.id]
            if (status is DownloadStatus.InProgress || status is DownloadStatus.Queued || status is DownloadStatus.Completed) {
                continue
            }
            val detail =
                try {
                    browseRepository.getItemDetail(episode.id, forceRefresh = true)
                } catch (t: Throwable) {
                    JellystackLog.e("Failed to load detail for ${episode.id}", t)
                    null
                } ?: continue
            if (detail.mediaSources.isEmpty()) {
                continue
            }
            val queued = enqueueDirectDownload(episode, detail, environment, manager, showErrors = false)
            if (queued) {
                queuedCount += 1
            }
        }
        return queuedCount
    }

    suspend fun queueDownloadFor(
        item: JellyfinItem,
        detail: JellyfinItemDetail,
        environment: JellyfinEnvironment,
        manager: OfflineDownloadManager,
    ): Boolean {
        val playbackRequest = PlaybackRequest.from(item, detail)
        if (playbackRequest.mediaSources.isEmpty()) {
            val episodeCandidates =
                when {
                    item.type.equals("Series", ignoreCase = true) -> browseRepository.episodesForSeries(item.id)
                    item.type.equals("Season", ignoreCase = true) -> browseRepository.episodesForSeason(item.id)
                    else -> emptyList()
                }
            if (episodeCandidates.isEmpty()) {
                serverErrorMessage = downloadNoEpisodesMessage
                return false
            }
            val queued = queueDownloadsForEpisodes(episodeCandidates, environment, manager)
            if (queued == 0) {
                serverErrorMessage = downloadEpisodesAlreadyQueuedMessage
            }
            return queued > 0
        }
        return enqueueDirectDownload(item, detail, environment, manager, showErrors = true)
    }
    val queueDownloadAction: (JellyfinItem, JellyfinItemDetail) -> Unit = { item, detail ->
        val manager = offlineDownloadManager
        val existingStatus = downloadStatuses[item.id]
        if (existingStatus is DownloadStatus.Completed) {
            serverErrorMessage = downloadItemAlreadyOfflineMessage
        } else if (manager == null) {
            serverErrorMessage = downloadUnavailableDeviceMessage
        } else {
            serverErrorMessage = null
            coroutineScope.launch {
                val environment = environmentProvider.current()
                if (environment == null) {
                    serverErrorMessage = downloadConnectServerMessage
                    destinationDispatcher.dispatch { isSettingsOpen = true }
                    return@launch
                }
                queueDownloadFor(item, detail, environment, manager)
            }
        }
    }

    val downloadSeriesAction: (() -> Unit)? =
        if (loadedDetail != null && detailSeasonGroups.isNotEmpty() && !isEpisodeDetail) {
            {
                val manager = offlineDownloadManager
                when {
                    manager == null -> serverErrorMessage = downloadUnavailableDeviceMessage
                    detailAllEpisodes.isEmpty() -> serverErrorMessage = downloadNoEpisodesMessage
                    bulkDownloadJob?.isActive == true -> Unit
                    else -> {
                        serverErrorMessage = null
                        bulkDownloadJob =
                            coroutineScope.launch {
                                try {
                                    val environment = environmentProvider.current()
                                    if (environment == null) {
                                        serverErrorMessage = downloadConnectServerMessage
                                        destinationDispatcher.dispatch { isSettingsOpen = true }
                                        return@launch
                                    }
                                    val queued = queueDownloadsForEpisodes(detailAllEpisodes, environment, manager)
                                    if (queued == 0) {
                                        serverErrorMessage = downloadSeriesAlreadyQueuedMessage
                                    }
                                } finally {
                                    bulkDownloadJob = null
                                }
                            }
                    }
                }
            }
        } else {
            null
        }
    val downloadSeasonAction: ((SeasonEpisodes) -> Unit)? =
        if (loadedDetail != null && detailSeasonGroups.isNotEmpty()) {
            { season ->
                val manager = offlineDownloadManager
                when {
                    manager == null -> serverErrorMessage = downloadUnavailableDeviceMessage
                    season.episodes.isEmpty() -> serverErrorMessage = downloadNoEpisodesMessage
                    bulkDownloadJob?.isActive == true -> Unit
                    else -> {
                        serverErrorMessage = null
                        bulkDownloadJob =
                            coroutineScope.launch {
                                try {
                                    val environment = environmentProvider.current()
                                    if (environment == null) {
                                        serverErrorMessage = downloadConnectServerMessage
                                        destinationDispatcher.dispatch { isSettingsOpen = true }
                                        return@launch
                                    }
                                    val queued = queueDownloadsForEpisodes(season.episodes, environment, manager)
                                    if (queued == 0) {
                                        serverErrorMessage = downloadSeasonAlreadyQueuedMessage
                                    }
                                } finally {
                                    bulkDownloadJob = null
                                }
                            }
                    }
                }
            }
        } else {
            null
        }

    val onSelectLibrary: (String) -> Unit = { libraryId ->
        destinationDispatcher.dispatch { browseCoordinator.selectLibrary(libraryId) }
    }
    val onRefreshHome: () -> Unit = {
        browseCoordinator.bootstrap(forceRefresh = true)
        coroutineScope.launch {
            homeSectionsRepository.refresh(
                enabledByUser = appSettings.useServerHomeSections,
                language = appSettings.appLanguage.languageTag,
            )
        }
    }
    val onRefreshLibrary: () -> Unit = {
        when (libraryNavigationState.destination.refreshTarget()) {
            LibraryRefreshTarget.CurrentLevel -> browseCoordinator.refreshSelectedLibrary()
            LibraryRefreshTarget.Favorites -> browseCoordinator.refreshFavorites()
            LibraryRefreshTarget.Libraries -> browseCoordinator.refreshLibraries()
            LibraryRefreshTarget.None -> Unit
        }
    }
    val onLoadMore: () -> Unit = browseCoordinator::loadNextPage
    val onSelectFavorites: () -> Unit = {
        browseCoordinator.selectFavorites()
    }
    val onRefreshJellyseerr: () -> Unit = {
        jellyseerrCoordinator.refresh()
        recommendationsCoordinator.refreshAll()
    }

    val cancelJellyfinQuickConnect: () -> Unit = {
        jellyfinQuickConnectGeneration += 1
        jellyfinQuickConnectJob?.cancel()
        jellyfinQuickConnectJob = null
        jellyfinQuickConnectState = null
        isSavingServer = false
    }

    val dismissAddServerDialog = dismissAddServerDialog@{
        val quickConnectActive = jellyfinQuickConnectState != null
        if (isSavingServer && !quickConnectActive) return@dismissAddServerDialog
        cancelJellyfinQuickConnect()
        showAddServerDialog = false
        if (activeShellModal?.modal == ShellModal.ServerEditor) activeShellModal = null
        serverFormState = ServerFormState()
        serverErrorMessage = null
    }

    val openAddServerDialog: (ServerFormType) -> Unit = { defaultType ->
        cancelJellyfinQuickConnect()
        serverErrorMessage = null
        serverFormState =
            when (defaultType) {
                ServerFormType.JELLYFIN -> ServerFormState(type = ServerFormType.JELLYFIN)
                ServerFormType.SEERR -> {
                    ServerFormState(
                        type = ServerFormType.SEERR,
                        name = "Seerr",
                        useJellyfinLogin = true,
                        automaticSeerrLogin = true,
                    )
                }
            }
        showAddServerDialog = true
        activeShellModal =
            ShellModalOwner(
                modal = ShellModal.ServerEditor,
                dismiss = dismissAddServerDialog,
            )
    }

    fun currentDetailOrigin(): DetailOrigin =
        detailRouteBackStack.lastOrNull()?.origin
            ?: when (primaryDestination) {
                PrimaryDestination.Home -> DetailOrigin.Home
                PrimaryDestination.Library -> DetailOrigin.Library
                PrimaryDestination.Discover ->
                    if (discoverDestination == DiscoverDestination.Requests) {
                        DetailOrigin.Requests
                    } else {
                        DetailOrigin.Discover
                    }
                PrimaryDestination.Admin -> DetailOrigin.Home
            }

    fun loadDetail(
        item: JellyfinItem,
        origin: DetailOrigin,
        forceRefresh: Boolean,
        replaceTop: Boolean = false,
    ) {
        val baseUrl = browseCoordinator.state.value.imageBaseUrl
        val accessToken = browseCoordinator.state.value.imageAccessToken
        val loadingState = JellyfinDetailUiState.Loading(item, baseUrl, accessToken)
        detailRequestGeneration += 1
        val expectedEntry =
            DetailStackEntry(
                mediaId = item.id,
                origin = origin,
                generation = detailRequestGeneration,
            )
        val entryIndex =
            if (replaceTop && detailRouteBackStack.isNotEmpty()) {
                val index = detailRouteBackStack.lastIndex
                detailRouteBackStack[index] = expectedEntry
                detailUiBackStack[index] = loadingState
                index
            } else {
                detailRouteBackStack.add(expectedEntry)
                detailUiBackStack.add(loadingState)
                detailRouteBackStack.lastIndex
            }
        assertDetailStacksSynchronized()
        detailEpisodeCache = emptyList()
        detailJob?.cancel()
        val job =
            coroutineScope.launch {
                try {
                    val offlineStatus = downloadStatuses[item.id]
                    val preferOfflineDetail = !forceRefresh && offlineStatus is DownloadStatus.Completed
                    val cachedDetail = browseRepository.cachedItemDetail(item.id)
                    val fallbackDetail = cachedDetail ?: item.toOfflineDetail()
                    val isOfflineMode = browseState.errorMessage?.isNotBlank() == true
                    var remoteFailure: Throwable? = null
                    var usedFallbackForEmptyRemoteDetail = false
                    val detail: JellyfinItemDetail? =
                        when {
                            preferOfflineDetail -> {
                                if (fallbackDetail != null) {
                                    JellystackLog.d("Using offline cached detail for ${item.id} (download available).")
                                } else {
                                    JellystackLog.d("Offline detail requested for ${item.id}, but no cached copy exists.")
                                }
                                fallbackDetail
                            }
                            isOfflineMode -> {
                                if (fallbackDetail != null) {
                                    JellystackLog.d("Offline mode active; using cached detail for ${item.id}.")
                                } else {
                                    JellystackLog.d("Offline mode active but no cached detail for ${item.id}.")
                                }
                                fallbackDetail
                            }
                            else ->
                                try {
                                    browseRepository.getItemDetail(item.id, forceRefresh)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (t: Throwable) {
                                    remoteFailure = t
                                    if (fallbackDetail != null) {
                                        fallbackDetail
                                    } else {
                                        null
                                    }
                                } ?: fallbackDetail.also { fallback ->
                                    if (fallback != null) {
                                        usedFallbackForEmptyRemoteDetail = true
                                    }
                                }
                        }
                    val updatedState =
                        if (detail != null) {
                            JellyfinDetailUiState.Loaded(
                                item = item,
                                detail = detail,
                                imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                                imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                            )
                        } else {
                            JellyfinDetailUiState.Error(
                                item = item,
                                message = itemDetailUnavailableMessage,
                                imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                                imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                            )
                        }
                    publishIfCurrentDetailRequest(detailRouteBackStack, entryIndex, expectedEntry) {
                        remoteFailure?.let { failure ->
                            if (detail != null) {
                                JellystackLog.d(
                                    "Falling back to cached detail for ${item.id}: ${failure.message}",
                                )
                            } else {
                                serverErrorMessage = failure.connectivityErrorMessage()
                                JellystackLog.e(
                                    "Failed to load detail for ${item.id}: ${failure.message}",
                                    failure,
                                )
                            }
                        }
                        if (usedFallbackForEmptyRemoteDetail) {
                            JellystackLog.d(
                                "Remote detail for ${item.id} was empty; using cached fallback.",
                            )
                        }
                        detailUiBackStack[entryIndex] = updatedState
                        assertDetailStacksSynchronized()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    publishIfCurrentDetailRequest(detailRouteBackStack, entryIndex, expectedEntry) {
                        detailUiBackStack[entryIndex] =
                            JellyfinDetailUiState.Error(
                                item = item,
                                message = t.message ?: itemDetailUnavailableMessage,
                                imageBaseUrl = browseCoordinator.state.value.imageBaseUrl,
                                imageAccessToken = browseCoordinator.state.value.imageAccessToken,
                            )
                        assertDetailStacksSynchronized()
                    }
                }
            }
        detailJob = job
        job.invokeOnCompletion {
            if (detailJob == job) detailJob = null
        }
    }

    val onOpenItemDetail: (JellyfinItem) -> Unit = { item ->
        destinationDispatcher.dispatch {
            loadDetail(
                item = item,
                origin = currentDetailOrigin(),
                forceRefresh = false,
            )
        }
    }
    val onPlayItem: (JellyfinItem) -> Unit = playItem@{ item ->
        val isDirectlyPlayable =
            item.type.equals("Episode", ignoreCase = true) ||
                item.type.equals("Movie", ignoreCase = true) ||
                item.type.equals("Video", ignoreCase = true) ||
                item.type.equals("MusicVideo", ignoreCase = true) ||
                item.type.equals("Audio", ignoreCase = true) ||
                item.type.equals("AudioBook", ignoreCase = true)
        if (!isDirectlyPlayable) {
            onOpenItemDetail(item)
            return@playItem
        }
        coroutineScope.launch {
            val fallbackDetail = browseRepository.cachedItemDetail(item.id) ?: item.toOfflineDetail()
            val detail =
                runCatching { browseRepository.getItemDetail(item.id, false) }
                    .getOrNull()
                    ?: fallbackDetail
            if (detail != null) {
                playbackAction(item, detail)
            } else {
                onOpenItemDetail(item)
            }
        }
    }

    val playSeriesAction: (() -> Unit)? =
        if (loadedDetail != null && isSeriesDetail && seriesPlaybackCandidates.isNotEmpty()) {
            {
                coroutineScope.launch {
                    for (candidate in seriesPlaybackCandidates) {
                        val episode = candidate.episode
                        val detail =
                            runCatching { browseRepository.getItemDetail(episode.id, forceRefresh = false) }
                                .getOrNull()
                                ?: browseRepository.cachedItemDetail(episode.id)
                                ?: episode.toOfflineDetail()
                        val hasOfflineSource = downloadStatuses[episode.id] is DownloadStatus.Completed
                        if (hasOfflineSource || detail.mediaSources.isNotEmpty()) {
                            playbackAction(episode, detail)
                            return@launch
                        }
                    }
                    serverErrorMessage = noPlayableSeriesMessage
                }
            }
        } else {
            null
        }

    val viewSeriesAction: (() -> Unit)? =
        if (loadedDetail != null && isEpisodeDetail) {
            destinationDispatcher.action {
                val target = loadedDetail.item.toSeriesNavigationTarget()
                loadDetail(
                    item = target,
                    origin = currentDetailOrigin(),
                    forceRefresh = false,
                )
            }
        } else {
            null
        }

    val submitServer = submitServer@{
        if (isSavingServer) return@submitServer
        val tutorialSubmission = isTutorialVisible
        val form = if (tutorialSubmission) tutorialServerFormState else serverFormState
        if (!form.isValid) {
            serverErrorMessage = completeRequiredFieldsMessage
            return@submitServer
        }
        val submissionQuickConnectGeneration =
            if (form.type == ServerFormType.JELLYFIN &&
                form.jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT
            ) {
                jellyfinQuickConnectGeneration += 1
                jellyfinQuickConnectGeneration
            } else {
                null
            }
        val submissionJob =
            coroutineScope.launch {
                isSavingServer = true
                serverErrorMessage = null
                var connectedServerId = form.serverId
                var connectedViaQuickConnect = false
                var connectedJellyfinUsername: String? = null
                try {
                    when (form.type) {
                        ServerFormType.JELLYFIN -> {
                            val connectedServer =
                                when (form.jellyfinSignInMethod) {
                                    JellyfinSignInMethod.QUICK_CONNECT -> {
                                        var result: ManagedServer? = null
                                        jellyfinQuickConnectCoordinator
                                            .connect(
                                                JellyfinQuickConnectInput(
                                                    serverId = form.serverId,
                                                    name = form.name,
                                                    baseUrl = form.baseUrl,
                                                    appVersion = appVersion,
                                                ),
                                            ).collect { state ->
                                                jellyfinQuickConnectState = state
                                                if (state is JellyfinQuickConnectState.Connected) {
                                                    result = state.server
                                                }
                                            }
                                        val authenticated = result ?: return@launch
                                        connectedViaQuickConnect = true
                                        authenticated
                                    }
                                    JellyfinSignInMethod.PASSWORD -> {
                                        val password =
                                            form.password.ifBlank {
                                                form.serverId
                                                    ?.let { serverRepository.jellyfinPassword(it)?.reveal() }
                                                    .orEmpty()
                                            }
                                        serverConnectionCoordinator.connectJellyfin(
                                            JellyfinConnectionInput(
                                                serverId = form.serverId,
                                                name = form.name,
                                                baseUrl = form.baseUrl,
                                                username = form.username,
                                                password = password,
                                            ),
                                        )
                                    }
                                }
                            connectedServerId = connectedServer.id
                            connectedJellyfinUsername =
                                (connectedServer.credentials as? StoredCredential.Jellyfin)?.username
                            showQuickConnectSeerrExplanation = false
                            settingsConnectionHealth =
                                settingsConnectionHealth +
                                (connectedServer.id to SettingsConnectionHealth.Ready)
                            browseCoordinator.bootstrap(forceRefresh = true)
                        }
                        ServerFormType.SEERR -> {
                            val input =
                                SeerrServerInput(
                                    name = form.name,
                                    baseUrl = form.baseUrl,
                                    serverId = form.serverId,
                                    appVersion = appVersion,
                                )
                            if (form.automaticSeerrLogin) {
                                when (val result = serverConnectionCoordinator.connectSeerrAutomatically(input)) {
                                    is SeerrConnectionResult.Connected -> {
                                        connectedServerId = result.server.id
                                        settingsConnectionHealth =
                                            settingsConnectionHealth +
                                            (result.server.id to SettingsConnectionHealth.Ready)
                                    }
                                    is SeerrConnectionResult.ConnectionFailed -> {
                                        connectedServerId?.let { id ->
                                            settingsConnectionHealth =
                                                settingsConnectionHealth +
                                                (id to SettingsConnectionHealth.NeedsAttention)
                                        }
                                        serverErrorMessage = result.reason
                                        return@launch
                                    }
                                    is SeerrConnectionResult.CredentialsRequired -> {
                                        connectedServerId?.let { id ->
                                            settingsConnectionHealth =
                                                settingsConnectionHealth +
                                                (id to SettingsConnectionHealth.NeedsAttention)
                                        }
                                        val updatedForm =
                                            form.copy(
                                                automaticSeerrLogin = false,
                                                useJellyfinLogin = true,
                                                username = result.suggestedUsername.orEmpty(),
                                                password = "",
                                            )
                                        if (tutorialSubmission) {
                                            tutorialServerFormState = updatedForm
                                            showQuickConnectSeerrExplanation = true
                                        } else {
                                            serverFormState = updatedForm
                                        }
                                        serverErrorMessage = result.reason
                                        return@launch
                                    }
                                }
                            } else {
                                val credentials =
                                    if (form.useJellyfinLogin) {
                                        SeerrLoginCredentials.Jellyfin(form.username, form.password)
                                    } else {
                                        SeerrLoginCredentials.Local(form.email, form.password)
                                    }
                                val connectedServer =
                                    serverConnectionCoordinator.connectSeerrManually(input, credentials)
                                connectedServerId = connectedServer.id
                                settingsConnectionHealth =
                                    settingsConnectionHealth +
                                    (connectedServer.id to SettingsConnectionHealth.Ready)
                            }
                            jellyseerrCoordinator.refresh()
                            recommendationsCoordinator.refreshAll()
                            showQuickConnectSeerrExplanation = false
                        }
                    }
                    if (tutorialSubmission) {
                        tutorialServerFormState = tutorialServerFormState.withoutSecrets()
                        tutorialFormHistory =
                            tutorialFormHistory.mapValues { (_, remembered) -> remembered.withoutSecrets() }
                        if (isTutorialVisible) {
                            automaticTutorialAdvanceAllowed = true
                            destinationDispatcher.dispatch {
                                val nextStep =
                                    when (form.type) {
                                        ServerFormType.JELLYFIN -> TutorialStep.ConnectJellyseerr
                                        ServerFormType.SEERR -> TutorialStep.Explore
                                    }
                                showTutorialStep(nextStep)
                                if (connectedViaQuickConnect && nextStep == TutorialStep.ConnectJellyseerr) {
                                    val seerrForm =
                                        ServerFormState(
                                            type = ServerFormType.SEERR,
                                            name = "Seerr",
                                            username = connectedJellyfinUsername.orEmpty(),
                                            useJellyfinLogin = true,
                                            automaticSeerrLogin = true,
                                        )
                                    tutorialServerFormState = seerrForm
                                    tutorialFormHistory = tutorialFormHistory + (nextStep to seerrForm)
                                }
                            }
                        } else {
                            tutorialServerFormState = ServerFormState()
                        }
                    } else {
                        serverFormState = ServerFormState()
                        showAddServerDialog = false
                        if (activeShellModal?.modal == ShellModal.ServerEditor) activeShellModal = null
                    }
                    jellyfinQuickConnectState = null
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    connectedServerId?.let { id ->
                        settingsConnectionHealth =
                            settingsConnectionHealth +
                            (id to SettingsConnectionHealth.NeedsAttention)
                    }
                    serverErrorMessage = t.connectivityErrorMessage()
                    JellystackLog.e("Failed to connect server: $serverErrorMessage", t)
                } finally {
                    if (
                        submissionQuickConnectGeneration == null ||
                        submissionQuickConnectGeneration == jellyfinQuickConnectGeneration
                    ) {
                        isSavingServer = false
                        if (submissionQuickConnectGeneration != null) {
                            jellyfinQuickConnectJob = null
                        }
                    }
                }
            }
        if (
            submissionQuickConnectGeneration != null &&
            submissionQuickConnectGeneration == jellyfinQuickConnectGeneration
        ) {
            jellyfinQuickConnectJob = submissionJob
        }
    }

    val removeServer: (ManagedServer) -> Unit = { server ->
        coroutineScope.launch {
            try {
                serverRepository.remove(server.id)
                settingsConnectionHealth = settingsConnectionHealth - server.id
                if (server.type == ServerType.JELLYFIN) {
                    clearDetailStacks()
                    browseCoordinator.bootstrap(forceRefresh = true)
                }
                serverErrorMessage = null
            } catch (t: Throwable) {
                settingsConnectionHealth =
                    settingsConnectionHealth +
                    (server.id to SettingsConnectionHealth.NeedsAttention)
                serverErrorMessage = t.message ?: removeServerFailedMessage
            }
        }
    }

    val onRetryDetail: () -> Unit = {
        when (val state = detailState) {
            is JellyfinDetailUiState.Error ->
                loadDetail(
                    item = state.item,
                    origin = currentDetailOrigin(),
                    forceRefresh = true,
                    replaceTop = true,
                )
            is JellyfinDetailUiState.Loaded ->
                loadDetail(
                    item = state.item,
                    origin = currentDetailOrigin(),
                    forceRefresh = true,
                    replaceTop = true,
                )
            JellyfinDetailUiState.Hidden,
            is JellyfinDetailUiState.Loading,
            -> Unit
        }
    }

    val onBackFromDetail: () -> Unit = {
        detailJob?.cancel()
        if (detailRouteBackStack.isNotEmpty()) {
            detailRouteBackStack.removeAt(detailRouteBackStack.lastIndex)
            detailUiBackStack.removeAt(detailUiBackStack.lastIndex)
            assertDetailStacksSynchronized()
        }
    }

    fun openSettings() {
        destinationDispatcher.dispatch {
            isSettingsOpen = true
            serverErrorMessage = null
        }
    }

    fun closeSettings() {
        showAddServerDialog = false
        serverFormState = ServerFormState()
        serverErrorMessage = null
        if (activeShellModal?.modal == ShellModal.ServerEditor) activeShellModal = null
        isSettingsOpen = false
    }

    fun dismissTopModal() {
        dismissActiveShellModal(activeShellModal)
    }

    fun popLibraryLevel() {
        when {
            libraryNavigationState.destination ==
                LibraryDestination.Section(LibrarySection.Favorites) -> {
                browseCoordinator.leaveFavorites()
                libraryNavigationState = libraryNavigationState.pop()
            }
            browseState.browsePath.isNotEmpty() -> {
                if (browseCoordinator.navigateUp()) {
                    libraryNavigationState = libraryNavigationState.pop()
                }
            }
            libraryNavigationState.destination != LibraryDestination.Root -> {
                libraryNavigationState = libraryNavigationState.pop()
            }
        }
    }

    val backAction =
        nextBackAction(
            BackStackSnapshot(
                primary = primaryDestination,
                discover = discoverDestination,
                libraryDepth = libraryNavigationState.depth,
                detailDepth = detailRouteBackStack.size,
                settingsOpen = isSettingsOpen,
                onboardingStep = activeTutorialStep.takeIf { isTutorialVisible },
                onboardingIsFirstRun = onboardingIsFirstRun,
                modal = activeShellModal?.modal,
                appLocked = isAppLockActive,
                discoverSelectionVisible = discoverUiState.selected != null,
            ),
        )

    fun dispatchBack() {
        if (backAction == ShellBackAction.DismissModal) {
            dismissTopModal()
            return
        }
        destinationDispatcher.dispatch {
            when (backAction) {
                ShellBackAction.DismissModal -> Unit
                ShellBackAction.PreviousOnboardingStep -> goToPreviousTutorialStep()
                ShellBackAction.CloseOnboarding -> closeTutorialToSettings()
                ShellBackAction.CloseSettings -> closeSettings()
                ShellBackAction.PopDetail -> onBackFromDetail()
                ShellBackAction.CloseDiscoverSelection ->
                    discoverUiState = discoverUiState.reduce(DiscoverAction.CloseSelection)
                ShellBackAction.CloseRequests -> discoverUiState = discoverUiState.reduce(DiscoverAction.BackToFeed)
                ShellBackAction.PopLibrary -> popLibraryLevel()
                ShellBackAction.SelectHome -> primaryDestination = PrimaryDestination.Home
                ShellBackAction.ExitPlatform -> Unit
            }
        }
    }

    platformBackHandler(enabled = backAction != ShellBackAction.ExitPlatform) { dispatchBack() }

    LaunchedEffect(serverRepository) {
        serverRepository
            .observeServers()
            .map { servers -> servers.firstOrNull { it.type == ServerType.JELLYFIN }?.id }
            .distinctUntilChanged()
            .collect { serverId ->
                libraryNavigationState = LibraryNavigationState()
                if (serverId != null) {
                    browseCoordinator.bootstrap(forceRefresh = true)
                } else {
                    clearDetailStacks()
                    browseCoordinator.bootstrap(forceRefresh = false)
                }
            }
    }

    LaunchedEffect(detailState, browseState.errorMessage) {
        when (val state = detailState) {
            is JellyfinDetailUiState.Loaded -> {
                val seriesId =
                    when {
                        state.item.type.equals("Series", ignoreCase = true) -> state.item.id
                        !state.item.seriesId.isNullOrBlank() -> state.item.seriesId
                        else -> null
                    }
                if (seriesId == null) {
                    detailEpisodeCache = emptyList()
                    isDetailEpisodesLoading = false
                    return@LaunchedEffect
                }
                isDetailEpisodesLoading = true
                val offlineMode = browseState.errorMessage?.isNotBlank() == true
                val cachedEpisodes = browseRepository.episodesForSeries(seriesId)
                detailEpisodeCache = cachedEpisodes
                if (cachedEpisodes.isNotEmpty()) {
                    JellystackLog.d("Loaded ${cachedEpisodes.size} cached episodes for series $seriesId.")
                } else {
                    JellystackLog.d("No cached episodes available for series $seriesId.")
                }
                if (offlineMode) {
                    JellystackLog.d("Offline mode active; skipping episode refresh for series $seriesId.")
                    isDetailEpisodesLoading = false
                    return@LaunchedEffect
                }
                val refreshResult = runCatching { browseRepository.refreshEpisodesForSeries(seriesId) }
                val refreshedEpisodes = refreshResult.getOrNull()
                if (refreshedEpisodes != null) {
                    JellystackLog.d("Refreshed ${refreshedEpisodes.size} episodes for series $seriesId.")
                    detailEpisodeCache = refreshedEpisodes
                } else {
                    refreshResult.exceptionOrNull()?.let { error ->
                        JellystackLog.e("Failed to refresh episodes for series $seriesId: ${error.message}", error)
                    }
                    if (cachedEpisodes.isNotEmpty()) {
                        JellystackLog.d("Retaining ${cachedEpisodes.size} cached episodes for series $seriesId after refresh failure.")
                    }
                }
                isDetailEpisodesLoading = false
            }
            else -> {
                detailEpisodeCache = emptyList()
                isDetailEpisodesLoading = false
            }
        }
    }

    LaunchedEffect(browseState.imageBaseUrl, browseState.imageAccessToken) {
        if (detailUiBackStack.isEmpty()) return@LaunchedEffect
        val baseUrl = browseState.imageBaseUrl
        val accessToken = browseState.imageAccessToken
        for (index in detailUiBackStack.indices) {
            val updated = detailUiBackStack[index].withImageInfo(baseUrl, accessToken)
            detailUiBackStack[index] = updated
        }
        assertDetailStacksSynchronized()
    }
    CompositionLocalProvider(
        LocalThemeController provides themeController,
        LocalJellystackOrientation provides orientation,
    ) {
        JellystackTheme(isDarkTheme = isDarkTheme) {
            ProvideResponsiveProfile(modifier = Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val uriHandler = LocalUriHandler.current
                    val openJellyfinSettings = {
                        openSettings()
                        openAddServerDialog(ServerFormType.JELLYFIN)
                    }
                    val openJellyseerrSettings = {
                        openSettings()
                        openAddServerDialog(ServerFormType.SEERR)
                    }
                    val openWhatsNewFromSettings = {
                        presentWhatsNew()
                    }
                    val showTutorialFromSettings = {
                        destinationDispatcher.dispatch {
                            onboardingIsFirstRun = false
                            restartTutorial()
                        }
                    }
                    val onCastDisconnect: () -> Unit = {
                        coroutineScope.launch { resolvedCastSessionManager.disconnect() }
                    }
                    val onCastReconnect: () -> Unit = {
                        coroutineScope.launch { resolvedCastSessionManager.play() }
                    }
                    val selectCastDeviceDescription = stringResource(Res.string.select_cast_device)
                    val renderRouteButton: @Composable (CastConnectionState) -> Unit =
                        castRouteButton ?: { state: CastConnectionState ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .testTag(CastTestTags.ROUTE_PICKER_TOP_BAR)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = selectCastDeviceDescription
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                CastRoutePickerButton(
                                    state = state,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                    val shouldShowBiometricOverlay = isAppLockActive
                    val canAttemptBiometricUnlock =
                        biometricUnlocker != null && biometricCapability.isAuthenticationReady
                    val unlockBeforeDisableMessage =
                        stringResource(Res.string.app_lock_unlock_before_disable)
                    val appLockUnavailableMessage =
                        stringResource(Res.string.app_lock_authentication_unavailable)
                    val onBiometricUnlock: () -> Unit = {
                        biometricGate.unlock(biometricUnlocker)
                    }
                    val notifyUnlockRequired: () -> Unit = {
                        showShellFeedback(unlockBeforeDisableMessage)
                    }
                    val toggleBiometricProtection: (Boolean) -> Unit = { enabled ->
                        val applied =
                            if (enabled) {
                                biometricGate.enable()
                            } else {
                                val unlocked =
                                    biometricLockState == BiometricLockState.Unlocked ||
                                        biometricLockState == BiometricLockState.Disabled
                                if (!unlocked) {
                                    notifyUnlockRequired()
                                    false
                                } else {
                                    biometricGate.disable()
                                }
                            }
                        if (!applied) {
                            showShellFeedback(
                                biometricCapability.description
                                    ?: appLockUnavailableMessage,
                            )
                        }
                    }
                    val openLibraryContainer: (JellyfinItem) -> Unit = { item ->
                        if (item.isBrowseContainer()) {
                            destinationDispatcher.dispatch {
                                browseCoordinator.openContainer(item)
                                libraryNavigationState =
                                    libraryNavigationState.push(
                                        LibraryDestination.Children(
                                            parentId = item.id,
                                            title = item.name,
                                        ),
                                    )
                            }
                        }
                    }
                    val navigateUpLibrary: () -> Unit = {
                        destinationDispatcher.dispatch { popLibraryLevel() }
                    }
                    val onRequestModalChange: (ShellModalOwner?) -> Unit = { owner ->
                        activeShellModal = owner
                    }
                    val onSettingsModalChange: (ShellModalOwner?) -> Unit = { owner ->
                        activeShellModal = owner
                    }
                    val onDiscoverAction: (DiscoverAction) -> Unit = { action ->
                        val applyAction: () -> Unit = {
                            discoverUiState = discoverUiState.reduce(action)
                            when (action) {
                                is DiscoverAction.RequestQueryChanged -> jellyseerrCoordinator.search(action.query)
                                is DiscoverAction.RequestFilterChanged -> jellyseerrCoordinator.selectFilter(action.filter)
                                DiscoverAction.RefreshRequestStatus -> jellyseerrCoordinator.refresh()
                                is DiscoverAction.SelectRecommendation ->
                                    recommendationsCoordinator.loadDetail(action.item)
                                is DiscoverAction.SelectSearchResult ->
                                    recommendationsCoordinator.loadDetail(action.item)
                                is DiscoverAction.SelectExistingRequest ->
                                    action.summary.toSearchItemOrNull()?.let(recommendationsCoordinator::loadDetail)
                                is DiscoverAction.OpenRelatedDetail ->
                                    recommendationsCoordinator.loadDetail(action.item)
                                else -> Unit
                            }
                            Unit
                        }
                        if (action.requiresDestinationDispatch()) {
                            destinationDispatcher.dispatch(applyAction)
                        } else {
                            applyAction()
                        }
                    }
                    val closeDiscoverSelection: () -> Unit = {
                        destinationDispatcher.dispatch {
                            discoverUiState = discoverUiState.reduce(DiscoverAction.CloseSelection)
                        }
                    }
                    val closeDiscoverRequestConfiguration: () -> Unit = {
                        destinationDispatcher.dispatch {
                            discoverUiState =
                                discoverUiState.reduce(DiscoverAction.CloseRequestConfiguration)
                        }
                    }
                    val dismissDiscoverSelectionLayer: () -> Unit = {
                        if (discoverUiState.isRequestConfigurationOpen) {
                            closeDiscoverRequestConfiguration()
                        } else {
                            closeDiscoverSelection()
                        }
                    }
                    val onOpenHomeSeerrItem: (HomeSectionItem) -> Unit = { homeItem ->
                        homeItem.seerrTmdbId?.let { tmdbId ->
                            val item =
                                JellyseerrSearchItem(
                                    tmdbId = tmdbId,
                                    mediaType = JellyseerrMediaType.from(homeItem.seerrMediaType),
                                    title = homeItem.name,
                                    overview = homeItem.overview,
                                    releaseYear = homeItem.productionYear?.toString(),
                                    posterPath = homeItem.imageUrl,
                                    backdropPath = null,
                                    mediaInfoId = null,
                                    tvdbId = null,
                                    availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
                                    requests = emptyList(),
                                )
                            destinationDispatcher.dispatch {
                                clearDetailStacks()
                                primaryDestination = PrimaryDestination.Discover
                                discoverUiState = discoverUiState.reduce(DiscoverAction.SelectSearchResult(item))
                                recommendationsCoordinator.loadDetail(item)
                            }
                        }
                    }

                    val homeContent: @Composable (PaddingValues) -> Unit = { measuredPadding ->
                        HomeContent(
                            hasServers = hasAnyServer,
                            browseState = browseState,
                            homeSectionsState = homeSectionsState,
                            onSelectLibrary = onSelectLibrary,
                            onRefreshLibraries = onRefreshHome,
                            onLoadMore = onLoadMore,
                            onOpenItemDetail = onOpenItemDetail,
                            onPlayItem = onPlayItem,
                            onOpenSeerrItem = onOpenHomeSeerrItem,
                            onConnectJellyfin = openJellyfinSettings,
                            onConnectJellyseerr = openJellyseerrSettings,
                            learnMoreUrl = learnMoreUrl,
                            downloadStatuses = downloadStatuses,
                            selectedSpotlightId = selectedSpotlightId,
                            onSelectedSpotlightIdChange = { selectedSpotlightId = it },
                            spotlightAutoAdvanceEnabled = appSettings.spotlightAutoCycle,
                            spotlightAutoAdvanceIntervalMillis = appSettings.spotlightIntervalSeconds * 1_000L,
                            contentPadding = measuredPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val libraryContent: @Composable (PaddingValues) -> Unit = { measuredPadding ->
                        LibraryContent(
                            browseState = browseState,
                            onSelectLibrary = onSelectLibrary,
                            onRefreshLibraries = onRefreshLibrary,
                            onLoadMore = onLoadMore,
                            onOpenItemDetail = onOpenItemDetail,
                            onOpenContainer = openLibraryContainer,
                            onNavigateUp = navigateUpLibrary,
                            onAddServer = openJellyfinSettings,
                            onSelectFavorites = onSelectFavorites,
                            downloadStatuses = downloadStatuses,
                            offlineMedia = offlineMedia,
                            selectedSpotlightId = selectedSpotlightId,
                            onSelectedSpotlightIdChange = { selectedSpotlightId = it },
                            contentPadding = measuredPadding,
                            libraryNavigationState = libraryNavigationState,
                            onLibraryNavigationChange =
                                destinationDispatcher.callback { navigationState ->
                                    libraryNavigationState = navigationState
                                },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val discoverContent: @Composable (PaddingValues) -> Unit = { measuredPadding ->
                        DiscoverScreen(
                            state = discoverUiState,
                            contentPadding = measuredPadding,
                            recommendationsState = recommendationsState,
                            recommendationDetails = recommendationDetails,
                            requestsState = jellyseerrState,
                            languageProfiles = languageProfiles,
                            onAction = onDiscoverAction,
                            onRecommendationsRefresh = onRefreshJellyseerr,
                            onRecommendationsRetry = recommendationsCoordinator::retry,
                            onRecommendationsLoadNext = recommendationsCoordinator::loadNextPage,
                            onRecommendationOpenDetails = onRecommendationOpenDetails,
                            onRecommendationLoadDetail = recommendationsCoordinator::loadDetail,
                            onRecommendationRequestOpen = onRecommendationRequestOpen,
                            onRecommendationTrailer = onRecommendationTrailer,
                            onRecommendationImpression = onRecommendationImpression,
                            onClearSearch = {
                                discoverUiState = discoverUiState.reduce(DiscoverAction.RequestQueryChanged(""))
                                jellyseerrCoordinator.clearSearch()
                            },
                            onAddServer = openJellyseerrSettings,
                            onShellModalChange = onRequestModalChange,
                        )
                    }
                    val detailContent: @Composable (Modifier) -> Unit = { contentModifier ->
                        DetailContent(
                            state = detailState,
                            enrichment =
                                loadedDetail
                                    ?.item
                                    ?.id
                                    ?.let { detailEnrichmentById[it] }
                                    ?: MediaDetailEnrichment(),
                            enrichmentLoading =
                                loadedDetail
                                    ?.item
                                    ?.id
                                    ?.let { it in detailEnrichmentLoadingIds }
                                    ?: false,
                            libraryItems = browseState.libraryItems,
                            knownEpisodes = detailEpisodeCache,
                            onBack = onBackFromDetail,
                            onViewSeries = viewSeriesAction,
                            onRetry = onRetryDetail,
                            onPlay = playbackAction,
                            seriesPlaybackTarget = seriesPlaybackTarget,
                            seriesPlaybackLoading = isSeriesDetail && isDetailEpisodesLoading,
                            noPlayableSeriesMessage = noPlayableSeriesMessage,
                            onPlaySeries = playSeriesAction,
                            onTrailer =
                                detailTrailerSource?.let { trailer ->
                                    {
                                        when (trailer) {
                                            is DetailTrailerSource.Local ->
                                                playbackAction(trailer.item, trailer.detail)
                                            is DetailTrailerSource.YouTube ->
                                                trailer.trailer.url
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?.let { runCatching { uriHandler.openUri(it) } }
                                        }
                                    }
                                },
                            downloadStatus = detailDownloadStatus,
                            downloadStatuses = downloadStatuses,
                            onQueueDownload = queueDownloadAction,
                            onPauseDownload = pauseDownload,
                            onResumeDownload = resumeDownload,
                            onRemoveDownload = removeDownload,
                            onDownloadSeries = downloadSeriesAction,
                            onDownloadSeason = downloadSeasonAction,
                            onOpenItemDetail = onOpenItemDetail,
                            audioTracks = availableAudioTracks,
                            selectedAudioTrack = selectedAudioTrack,
                            onSelectAudioTrack = { track ->
                                preferredAudioTrackId = track.id
                                loadedDetail?.item?.let { item ->
                                    playbackController.saveAudioPreference(item, track)
                                }
                                if (activePlaybackForDetail != null) {
                                    playbackController.selectAudioTrack(track.id)
                                }
                            },
                            subtitleTracks = availableSubtitleTracks,
                            selectedSubtitleTrack = selectedSubtitleTrack,
                            onSelectSubtitleTrack = { track ->
                                preferredSubtitleTrackId = track?.id ?: OFF_SUBTITLE_TRACK_ID
                                loadedDetail?.item?.let { item ->
                                    playbackController.saveSubtitlePreference(item, track)
                                }
                                if (activePlaybackForDetail != null) {
                                    playbackController.selectSubtitle(track?.id)
                                }
                            },
                            isFavorite =
                                loadedDetail?.item?.id?.let { id -> id in favorites }
                                    ?: false,
                            onToggleFavorite = {
                                loadedDetail?.item?.let { item ->
                                    coroutineScope.launch {
                                        browseCoordinator.toggleFavorite(item)
                                    }
                                }
                            },
                            favoriteError = favoriteErrorText,
                            playedPending =
                                loadedDetail?.item?.id?.let { it in playedMutationPendingIds }
                                    ?: false,
                            onTogglePlayed = {
                                val current = detailState as? JellyfinDetailUiState.Loaded
                                if (
                                    current != null &&
                                    supportsPlayedStatus(current.item.type) &&
                                    current.item.id !in playedMutationPendingIds
                                ) {
                                    val itemId = current.item.id
                                    val previousPlayed = current.detail.isPlayed
                                    val requestedPlayed = !previousPlayed
                                    playedMutationPendingIds = playedMutationPendingIds + itemId
                                    playedMutationError = null
                                    updateLoadedPlayedStatus(itemId, requestedPlayed)
                                    coroutineScope.launch {
                                        try {
                                            val updated = browseRepository.setPlayedStatus(itemId, requestedPlayed)
                                            checkNotNull(updated) { "No active Jellyfin server" }
                                            updateLoadedPlayedStatus(itemId, updated.isPlayed)
                                        } catch (cancelled: CancellationException) {
                                            updateLoadedPlayedStatus(itemId, previousPlayed)
                                            throw cancelled
                                        } catch (failure: Throwable) {
                                            updateLoadedPlayedStatus(itemId, previousPlayed)
                                            playedMutationError = itemId to playedStatusUpdateFailedMessage
                                            JellystackLog.e(
                                                "Failed to update played status for $itemId",
                                                failure,
                                            )
                                        } finally {
                                            playedMutationPendingIds = playedMutationPendingIds - itemId
                                        }
                                    }
                                }
                            },
                            playedError =
                                loadedDetail?.item?.id?.let { itemId ->
                                    playedMutationError?.takeIf { it.first == itemId }?.second
                                },
                            modifier = contentModifier,
                        )
                    }

                    val primaryContent: @Composable (PaddingValues) -> Unit =
                        when (primaryDestination) {
                            PrimaryDestination.Home -> homeContent
                            PrimaryDestination.Library -> libraryContent
                            PrimaryDestination.Discover -> discoverContent
                            PrimaryDestination.Admin -> { measuredPadding ->
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(measuredPadding),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(stringResource(Res.string.nav_admin))
                                }
                            }
                        }
                    val detailPane: (@Composable (PaddingValues) -> Unit)? =
                        if (detailState == JellyfinDetailUiState.Hidden) {
                            null
                        } else {
                            { measuredPadding ->
                                detailContent(Modifier.fillMaxSize().padding(measuredPadding))
                            }
                        }
                    val responsiveProfile = LocalResponsiveProfile.current
                    val isDiscoverSelectionVisible =
                        primaryDestination == PrimaryDestination.Discover &&
                            discoverUiState.selected != null
                    val compactImmersiveDetail =
                        !responsiveProfile.isExpanded &&
                            (
                                detailState != JellyfinDetailUiState.Hidden ||
                                    isDiscoverSelectionVisible
                            )
                    val renderDiscoverSelection:
                        @Composable (contentModifier: Modifier, initialFocusModifier: Modifier) -> Unit =
                        { contentModifier, initialFocusModifier ->
                            DiscoverSelectionContent(
                                state = discoverUiState,
                                detailStates = recommendationDetails,
                                languageProfiles = languageProfiles,
                                requests = readyRequestsState?.requests.orEmpty(),
                                currentRequestsByMedia =
                                    readyRequestsState?.currentRequestsByMedia.orEmpty(),
                                liveRequestStateAvailable = readyRequestsState != null,
                                capabilities =
                                    readyRequestsState?.capabilities
                                        ?: dev.jellystack.core.jellyseerr.JellyseerrRequestCapabilities.NONE,
                                onSelectProfile = {
                                    onDiscoverAction(DiscoverAction.SelectProfile(it))
                                },
                                onSelectVariant = {
                                    onDiscoverAction(DiscoverAction.SelectRequestVariant(it))
                                },
                                onSelectSeasons = {
                                    onDiscoverAction(DiscoverAction.SelectSeasonSelection(it))
                                },
                                onSubmit = { item, profileSelection, seasons ->
                                    jellyseerrCoordinator.submitRequest(item, profileSelection, seasons)
                                },
                                onSubmitVariant = { item, profileSelection, seasons, variant ->
                                    onDiscoverAction(
                                        DiscoverAction.OperationStarted(
                                            DiscoverPendingOperation.Submit(
                                                mediaType = item.mediaType,
                                                tmdbId = item.tmdbId,
                                            ),
                                        ),
                                    )
                                    jellyseerrCoordinator.submitRequest(
                                        item = item,
                                        profileSelection = profileSelection,
                                        seasons = seasons,
                                        variant = variant,
                                    )
                                },
                                onApprove = { summary ->
                                    onDiscoverAction(
                                        DiscoverAction.OperationStarted(
                                            DiscoverPendingOperation.Approve(summary.id),
                                        ),
                                    )
                                    jellyseerrCoordinator.approveRequest(summary)
                                },
                                onDelete = { summary ->
                                    onDiscoverAction(
                                        DiscoverAction.OperationStarted(
                                            DiscoverPendingOperation.Delete(summary.id),
                                        ),
                                    )
                                    jellyseerrCoordinator.deleteRequest(summary.id)
                                },
                                onRemoveMedia = { summary ->
                                    onDiscoverAction(
                                        DiscoverAction.OperationStarted(
                                            DiscoverPendingOperation.RemoveMedia(summary.id),
                                        ),
                                    )
                                    jellyseerrCoordinator.removeMedia(summary)
                                },
                                onConfigureRequest = {
                                    onDiscoverAction(DiscoverAction.OpenRequestConfiguration)
                                },
                                onCloseRequestConfiguration = closeDiscoverRequestConfiguration,
                                onRetryDetail = recommendationsCoordinator::reloadDetail,
                                onRetryEnrichment = { item, section ->
                                    recommendationsCoordinator.retryDetailEnrichment(item, section)
                                },
                                onOpenRelatedDetail = { origin, item ->
                                    onDiscoverAction(
                                        DiscoverAction.OpenRelatedDetail(
                                            item = item,
                                            origin = origin,
                                        ),
                                    )
                                },
                                onDetailViewStateChange = { key, viewState ->
                                    onDiscoverAction(
                                        DiscoverAction.UpdateDetailViewState(
                                            key = key,
                                            viewState = viewState,
                                        ),
                                    )
                                },
                                onTrailer = { selection, trailer ->
                                    val rail = selection.recommendationRail
                                    val position = selection.recommendationPosition
                                    if (rail != null && position != null) {
                                        onRecommendationTrailer(
                                            rail,
                                            selection.item,
                                            position,
                                            trailer,
                                        )
                                    }
                                },
                                isAdmin = readyRequestsState?.isAdmin == true,
                                currentUserId = readyRequestsState?.currentUserId,
                                pendingApprovals = readyRequestsState?.pendingApprovals.orEmpty(),
                                onClose = closeDiscoverSelection,
                                initialFocusModifier = initialFocusModifier,
                                modifier = contentModifier,
                            )
                        }
                    val expandedDiscoverSelectionPane: (@Composable (PaddingValues) -> Unit)? =
                        if (responsiveProfile.isExpanded && isDiscoverSelectionVisible) {
                            { measuredPadding ->
                                renderDiscoverSelection(
                                    Modifier.fillMaxSize().padding(measuredPadding),
                                    Modifier,
                                )
                            }
                        } else {
                            null
                        }
                    val compactRequestSelectionVisible =
                        !responsiveProfile.isExpanded && isDiscoverSelectionVisible
                    val compactSelectionModal =
                        when {
                            discoverUiState.selected == null -> null
                            discoverUiState.isRequestConfigurationOpen -> ShellModal.RequestConfiguration
                            else -> ShellModal.SeerrMediaDetail
                        }
                    LaunchedEffect(compactRequestSelectionVisible, compactSelectionModal) {
                        if (compactRequestSelectionVisible && compactSelectionModal != null) {
                            activeShellModal =
                                ShellModalOwner(
                                    modal = compactSelectionModal,
                                    dismiss = dismissDiscoverSelectionLayer,
                                )
                        } else if (
                            activeShellModal?.modal == ShellModal.RequestConfiguration ||
                            activeShellModal?.modal == ShellModal.RequestManagement ||
                            activeShellModal?.modal == ShellModal.SeerrMediaDetail
                        ) {
                            activeShellModal = null
                        }
                    }
                    val shellPaneMode =
                        if (
                            detailPane != null ||
                            expandedDiscoverSelectionPane != null ||
                            primaryDestination == PrimaryDestination.Library ||
                            (
                                primaryDestination == PrimaryDestination.Discover &&
                                    discoverDestination == DiscoverDestination.Requests
                            )
                        ) {
                            ShellPaneMode.ListDetail
                        } else {
                            ShellPaneMode.Single
                        }
                    val requestShellFeedback =
                        readyRequestsState?.message?.let { message ->
                            val canRefresh =
                                message.recovery == JellyseerrMessageRecovery.RefreshRequests
                            ShellFeedback(
                                id = message.id,
                                message = message.localizedText(),
                                onDismiss = jellyseerrCoordinator::acknowledgeMessage,
                                actionLabel =
                                    stringResource(Res.string.refresh_status).takeIf { canRefresh },
                                onAction =
                                    if (canRefresh) {
                                        jellyseerrCoordinator::refresh
                                    } else {
                                        null
                                    },
                            )
                        }
                    val activeShellFeedback = requestShellFeedback ?: shellFeedback

                    fun handleShellAction(action: JellystackShellAction) {
                        when (action) {
                            is JellystackShellAction.SelectPrimary ->
                                selectPrimary(action.destination)
                            JellystackShellAction.OpenSettings -> openSettings()
                            JellystackShellAction.FeedbackShown ->
                                activeShellFeedback?.onDismiss?.invoke()
                            JellystackShellAction.FeedbackAction -> {
                                val feedback = activeShellFeedback
                                feedback?.onAction?.invoke()
                                feedback?.onDismiss?.invoke()
                            }
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    brush =
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.background,
                                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                                                MaterialTheme.colorScheme.background,
                                            ),
                                        ),
                                ),
                    ) {
                        JellystackShell(
                            modifier =
                                if (compactRequestSelectionVisible) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                },
                            state =
                                JellystackShellState(
                                    primary = primaryDestination,
                                    discover = discoverDestination,
                                    paneMode = shellPaneMode,
                                    dynamicTitle =
                                        shellTitle(
                                            primaryDestination,
                                            discoverDestination,
                                            libraryNavigationState.destination,
                                        ),
                                    showNavigation =
                                        !isSettingsOpen &&
                                            !isTutorialVisible &&
                                            !isAppLockActive &&
                                            !compactImmersiveDetail,
                                    showAdminDestination = sessionCapabilities?.isAdministrator == true,
                                    feedback = activeShellFeedback,
                                ),
                            onAction = ::handleShellAction,
                            topBar = {
                                if (!compactImmersiveDetail) {
                                    Column {
                                        JellystackTopBar(
                                            title =
                                                shellTitle(
                                                    primaryDestination,
                                                    discoverDestination,
                                                    libraryNavigationState.destination,
                                                ),
                                            showBack =
                                                primaryDestination != PrimaryDestination.Home ||
                                                    detailRouteBackStack.isNotEmpty(),
                                            onBack = ::dispatchBack,
                                            castState = castState,
                                            renderCastButton = renderRouteButton,
                                            onOpenSettings = ::openSettings,
                                        )
                                        CastStatusBanner(
                                            state = castState,
                                            onDisconnect = onCastDisconnect,
                                            onReconnect = onCastReconnect,
                                        )
                                    }
                                }
                            },
                            primaryContent = primaryContent,
                            secondaryContent = detailPane ?: expandedDiscoverSelectionPane,
                        )

                        if (compactRequestSelectionVisible) {
                            ModalFocusScope(
                                onDismissRequest = dismissDiscoverSelectionLayer,
                                returnFocusRequester = null,
                                fullScreen = true,
                            ) { initialFocusModifier ->
                                renderDiscoverSelection(
                                    Modifier.fillMaxSize(),
                                    initialFocusModifier,
                                )
                            }
                        }

                        if (shouldShowBiometricOverlay) {
                            BiometricLockOverlay(
                                state = biometricLockState,
                                capability = biometricCapability,
                                canRetry = canAttemptBiometricUnlock,
                                onUnlock = onBiometricUnlock,
                            )
                        }

                        if (!shouldShowBiometricOverlay && isSettingsOpen) {
                            val settingsConnections =
                                managedServers
                                    .filter { server ->
                                        server.type == ServerType.JELLYFIN ||
                                            server.type == ServerType.JELLYSEERR
                                    }.map { server ->
                                        server.toSettingsConnectionUi(
                                            isActive =
                                                server.id ==
                                                    when (server.type) {
                                                        ServerType.JELLYFIN -> activeJellyfinServer?.id
                                                        ServerType.JELLYSEERR -> activeSeerrServer?.id
                                                        ServerType.SONARR,
                                                        ServerType.RADARR,
                                                        -> null
                                                    },
                                            health =
                                                settingsConnectionHealth[server.id]
                                                    ?: SettingsConnectionHealth.Ready,
                                        )
                                    }
                            val settingsUiState =
                                SettingsUiState(
                                    selectedSection = selectedSettingsSection,
                                    themeMode = themeMode,
                                    appSettings = appSettings,
                                    platformCapabilities = platformCapabilities,
                                    appLockEnabled = biometricEnabled,
                                    appLockState = biometricLockState,
                                    appLockCapability = biometricCapability,
                                    connections = settingsConnections,
                                    appVersion = appVersion,
                                    downloadCount = offlineMedia.count { it.kind == OfflineMediaKind.VIDEO },
                                    downloadedBytes = offlineMedia.sumOf { it.sizeBytes ?: 0L },
                                    privacyStatus = privacyStatus,
                                )
                            val handleSettingsAction: (SettingsAction) -> Unit = { action ->
                                when (action) {
                                    is SettingsAction.SelectSection ->
                                        selectedSettingsSection = action.section
                                    is SettingsAction.SetTheme -> themeController.setMode(action.mode)
                                    is SettingsAction.SetAppLanguage -> {
                                        appSettingsRepository.setAppLanguage(action.language)
                                        onAppLanguageChanged(action.language)
                                    }
                                    is SettingsAction.SetWifiQuality ->
                                        appSettingsRepository.setWifiStreamingQuality(action.quality)
                                    is SettingsAction.SetMobileQuality ->
                                        appSettingsRepository.setMobileStreamingQuality(action.quality)
                                    is SettingsAction.SetAutoplayNextMode ->
                                        appSettingsRepository.setAutoplayNextMode(action.mode)
                                    is SettingsAction.SetResumeMode -> appSettingsRepository.setResumeMode(action.mode)
                                    is SettingsAction.SetSeekBackSeconds ->
                                        appSettingsRepository.setSeekBackSeconds(action.seconds)
                                    is SettingsAction.SetSeekForwardSeconds ->
                                        appSettingsRepository.setSeekForwardSeconds(action.seconds)
                                    is SettingsAction.SetPreferredAudioLanguage ->
                                        appSettingsRepository.setPreferredAudioLanguage(action.languageCode)
                                    is SettingsAction.SetPreferredSubtitleLanguage ->
                                        appSettingsRepository.setPreferredSubtitleLanguage(action.languageCode)
                                    is SettingsAction.SetSubtitleMode -> appSettingsRepository.setSubtitleMode(action.mode)
                                    is SettingsAction.SetRememberSeriesTracks ->
                                        appSettingsRepository.setRememberSeriesTracks(action.enabled)
                                    SettingsAction.ClearRememberedTracks ->
                                        playbackController.clearRememberedTrackPreferences()
                                    is SettingsAction.SetSubtitleTextSize ->
                                        appSettingsRepository.setSubtitleTextSize(action.size)
                                    is SettingsAction.SetSubtitleBackground ->
                                        appSettingsRepository.setSubtitleBackground(action.background)
                                    is SettingsAction.SetSpotlightAutoCycle ->
                                        appSettingsRepository.setSpotlightAutoCycle(action.enabled)
                                    is SettingsAction.SetUseServerHomeSections ->
                                        appSettingsRepository.setUseServerHomeSections(action.enabled)
                                    is SettingsAction.SetSpotlightIntervalSeconds ->
                                        appSettingsRepository.setSpotlightIntervalSeconds(action.seconds)
                                    is SettingsAction.SetDownloadsWifiOnly ->
                                        appSettingsRepository.setDownloadsWifiOnly(action.enabled)
                                    SettingsAction.ClearAllDownloads -> offlineDownloadManager?.clearAll()
                                    is SettingsAction.SetAppLock ->
                                        toggleBiometricProtection(action.enabled)
                                    is SettingsAction.ActivateConnection ->
                                        managedServers
                                            .firstOrNull { it.id == action.id }
                                            ?.let { server ->
                                                coroutineScope.launch {
                                                    try {
                                                        serverRepository.setActiveServer(server.type, server.id)
                                                        when (server.type) {
                                                            ServerType.JELLYFIN ->
                                                                browseCoordinator.bootstrap(forceRefresh = true)
                                                            ServerType.JELLYSEERR -> {
                                                                jellyseerrCoordinator.refresh()
                                                                recommendationsCoordinator.refreshAll()
                                                            }
                                                            ServerType.SONARR,
                                                            ServerType.RADARR,
                                                            -> Unit
                                                        }
                                                        settingsConnectionHealth =
                                                            settingsConnectionHealth +
                                                            (server.id to SettingsConnectionHealth.Ready)
                                                    } catch (error: Throwable) {
                                                        settingsConnectionHealth =
                                                            settingsConnectionHealth +
                                                            (server.id to SettingsConnectionHealth.NeedsAttention)
                                                        showShellFeedback(
                                                            error.connectivityErrorMessage(),
                                                        )
                                                    }
                                                }
                                            }
                                    is SettingsAction.AddConnection ->
                                        openAddServerDialog(
                                            if (action.type == ServerType.JELLYFIN) {
                                                ServerFormType.JELLYFIN
                                            } else {
                                                ServerFormType.SEERR
                                            },
                                        )
                                    is SettingsAction.EditConnection ->
                                        managedServers
                                            .firstOrNull { it.id == action.id }
                                            ?.let { server ->
                                                coroutineScope.launch {
                                                    cancelJellyfinQuickConnect()
                                                    serverErrorMessage = null
                                                    serverFormState =
                                                        when (val credential = server.credentials) {
                                                            is StoredCredential.Jellyfin -> {
                                                                val hasStoredPassword =
                                                                    serverRepository.jellyfinPassword(server.id) != null
                                                                ServerFormState(
                                                                    serverId = server.id,
                                                                    type = ServerFormType.JELLYFIN,
                                                                    name = server.name,
                                                                    baseUrl = server.baseUrl,
                                                                    username = credential.username,
                                                                    jellyfinSignInMethod =
                                                                        if (hasStoredPassword) {
                                                                            JellyfinSignInMethod.PASSWORD
                                                                        } else {
                                                                            JellyfinSignInMethod.QUICK_CONNECT
                                                                        },
                                                                )
                                                            }
                                                            is StoredCredential.ApiKey ->
                                                                ServerFormState(
                                                                    serverId = server.id,
                                                                    type = ServerFormType.SEERR,
                                                                    name = server.name,
                                                                    baseUrl = server.baseUrl,
                                                                    useJellyfinLogin = true,
                                                                    automaticSeerrLogin = true,
                                                                )
                                                        }
                                                    showAddServerDialog = true
                                                    activeShellModal =
                                                        ShellModalOwner(
                                                            modal = ShellModal.ServerEditor,
                                                            dismiss = dismissAddServerDialog,
                                                        )
                                                }
                                            }
                                    is SettingsAction.RemoveConnection ->
                                        managedServers
                                            .firstOrNull { it.id == action.id }
                                            ?.let(removeServer)
                                    SettingsAction.RunSetup -> showTutorialFromSettings()
                                    SettingsAction.ShowWhatsNew -> openWhatsNewFromSettings()
                                    SettingsAction.Close ->
                                        destinationDispatcher.dispatch { closeSettings() }
                                }
                            }
                            SettingsScreen(
                                state = settingsUiState,
                                onAction = handleSettingsAction,
                                onShellModalChange = onSettingsModalChange,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (showAddServerDialog) {
                                AddServerDialog(
                                    state = serverFormState,
                                    isSaving = isSavingServer,
                                    quickConnectState = jellyfinQuickConnectState,
                                    errorMessage = serverErrorMessage,
                                    availableJellyfinServers =
                                        managedServers.filter { it.type == ServerType.JELLYFIN },
                                    onValueChange = { updated ->
                                        if (updated.jellyfinSignInMethod != serverFormState.jellyfinSignInMethod) {
                                            cancelJellyfinQuickConnect()
                                        }
                                        serverFormState = updated
                                    },
                                    onClearError = { serverErrorMessage = null },
                                    onDismiss = dismissAddServerDialog,
                                    onSubmit = submitServer,
                                    onRestartQuickConnect = {
                                        cancelJellyfinQuickConnect()
                                        submitServer()
                                    },
                                )
                            }
                        }
                        pendingResumePlayback?.let { pending ->
                            AlertDialog(
                                onDismissRequest = { pendingResumePlayback = null },
                                title = { Text("Resume playback?") },
                                text = { Text("Continue where you stopped, or start this title from the beginning.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            pendingResumePlayback = null
                                            launchPlayback(pending.item, pending.detail, PlaybackStartPolicy.RESUME)
                                        },
                                    ) {
                                        Text("Resume")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            pendingResumePlayback = null
                                            launchPlayback(pending.item, pending.detail, PlaybackStartPolicy.RESTART)
                                        },
                                    ) {
                                        Text("Start over")
                                    }
                                },
                            )
                        }
                        if (!shouldShowBiometricOverlay && showWhatsNewDialog) {
                            WhatsNewDialog(
                                version = appVersion,
                                highlights = resolvedWhatsNewHighlights,
                                onClose = ::dismissWhatsNew,
                                onViewChangelog = {
                                    runCatching { uriHandler.openUri(changelogUrl) }
                                    dismissWhatsNew()
                                },
                            )
                        }
                        if (!shouldShowBiometricOverlay && isTutorialVisible) {
                            val manualSeerrCredentialsRequired =
                                activeTutorialStep == TutorialStep.ConnectJellyseerr &&
                                    !tutorialServerFormState.automaticSeerrLogin
                            val onboardingUiState =
                                OnboardingUiState(
                                    step = activeTutorialStep,
                                    progress = onboardingProgress(activeTutorialStep),
                                    form = tutorialServerFormState,
                                    fieldErrors = onboardingFieldErrors,
                                    manualSeerrCredentialsRequired = manualSeerrCredentialsRequired,
                                    isSaving = isSavingServer,
                                    serviceErrorDetail = serverErrorMessage,
                                    canStartExploring = activeJellyfinServer != null,
                                    quickConnectState = jellyfinQuickConnectState,
                                    seerrQuickConnectExplanation =
                                        quickConnectSeerrManualMessage.takeIf {
                                            activeTutorialStep == TutorialStep.ConnectJellyseerr &&
                                                showQuickConnectSeerrExplanation
                                        },
                                )
                            val handleOnboardingAction: (OnboardingAction) -> Unit = { action ->
                                when (action) {
                                    is OnboardingAction.FormChanged -> {
                                        if (
                                            action.form.jellyfinSignInMethod !=
                                            tutorialServerFormState.jellyfinSignInMethod
                                        ) {
                                            cancelJellyfinQuickConnect()
                                        }
                                        tutorialServerFormState = action.form
                                        tutorialFormHistory =
                                            tutorialFormHistory + (activeTutorialStep to action.form)
                                        onboardingFieldErrors = emptyMap()
                                        serverErrorMessage = null
                                    }
                                    is OnboardingAction.SignInMethodChanged -> {
                                        cancelJellyfinQuickConnect()
                                        val updated =
                                            tutorialServerFormState.copy(
                                                jellyfinSignInMethod = action.method,
                                                password = "",
                                            )
                                        tutorialServerFormState = updated
                                        tutorialFormHistory =
                                            tutorialFormHistory + (activeTutorialStep to updated)
                                        onboardingFieldErrors = emptyMap()
                                        serverErrorMessage = null
                                    }
                                    OnboardingAction.RestartQuickConnect -> {
                                        cancelJellyfinQuickConnect()
                                        submitServer()
                                    }
                                    OnboardingAction.CancelQuickConnect -> cancelJellyfinQuickConnect()
                                    OnboardingAction.Continue -> {
                                        if (activeTutorialStep == TutorialStep.Welcome) {
                                            destinationDispatcher.dispatch { goToNextTutorialStep() }
                                        } else {
                                            val errors =
                                                validateOnboarding(
                                                    step = activeTutorialStep,
                                                    form = tutorialServerFormState,
                                                    manualSeerrCredentialsRequired =
                                                    manualSeerrCredentialsRequired,
                                                )
                                            onboardingFieldErrors = errors
                                            if (errors.isEmpty()) submitServer()
                                        }
                                    }
                                    OnboardingAction.Back -> {
                                        cancelJellyfinQuickConnect()
                                        destinationDispatcher.dispatch { goToPreviousTutorialStep() }
                                    }
                                    OnboardingAction.SkipSeerr ->
                                        destinationDispatcher.dispatch {
                                            showTutorialStep(TutorialStep.Explore)
                                        }
                                    OnboardingAction.StartExploring ->
                                        destinationDispatcher.dispatch {
                                            if (activeJellyfinServer != null) completeTutorial()
                                        }
                                }
                            }
                            OnboardingScreen(
                                state = onboardingUiState,
                                onAction = handleOnboardingAction,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun ServerFormState.withoutSecrets(): ServerFormState = copy(password = "")

@Composable
private fun JellyseerrMessage.localizedText(): String {
    val base =
        when (code) {
            JellyseerrMessageCode.SearchFailed -> stringResource(Res.string.request_search_failed)
            JellyseerrMessageCode.RequestSubmitted ->
                stringResource(Res.string.request_submitted, subject.orEmpty())
            JellyseerrMessageCode.RequestPermissionDenied ->
                stringResource(Res.string.request_permission_denied)
            JellyseerrMessageCode.RequestDuplicate ->
                stringResource(Res.string.request_duplicate, subject.orEmpty())
            JellyseerrMessageCode.RequestFailed ->
                stringResource(Res.string.request_failed, subject.orEmpty())
            JellyseerrMessageCode.RequestRemoved -> stringResource(Res.string.request_removed)
            JellyseerrMessageCode.DeleteFailed -> stringResource(Res.string.request_delete_failed)
            JellyseerrMessageCode.RequestApproved -> stringResource(Res.string.request_approved)
            JellyseerrMessageCode.ApprovalFailed -> stringResource(Res.string.request_approval_failed)
            JellyseerrMessageCode.MediaIdMissing ->
                stringResource(Res.string.request_media_id_missing, subject.orEmpty())
            JellyseerrMessageCode.MediaRequeued ->
                stringResource(Res.string.request_media_requeued, subject.orEmpty())
            JellyseerrMessageCode.MediaRequeueFailed ->
                stringResource(Res.string.request_media_requeue_failed)
            JellyseerrMessageCode.RemoveMediaFailed ->
                stringResource(Res.string.request_remove_media_failed)
            JellyseerrMessageCode.RefreshFailed -> stringResource(Res.string.request_refresh_failed)
        }
    return detail?.takeIf(String::isNotBlank)?.let { "$base\n$it" } ?: base
}

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    browseState: JellyfinHomeState,
    selectedSpotlightId: String?,
    onSelectedSpotlightIdChange: (String?) -> Unit,
    onSelectLibrary: (String) -> Unit,
    onRefreshLibraries: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onPlayItem: ((JellyfinItem) -> Unit)? = null,
    onAddServer: () -> Unit,
    onSelectFavorites: () -> Unit = {},
    showLibraryItems: Boolean = true,
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    offlineMedia: List<OfflineMedia> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    libraryNavigationState: LibraryNavigationState = LibraryNavigationState(),
    onLibraryNavigationChange: (LibraryNavigationState) -> Unit = {},
    modifier: Modifier = Modifier,
    spotlightAutoAdvanceEnabled: Boolean = true,
    spotlightAutoAdvanceIntervalMillis: Long = 6_000L,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JellyfinBrowseScreen(
            state = browseState,
            onSelectLibrary = onSelectLibrary,
            onRefresh = onRefreshLibraries,
            onLoadMore = onLoadMore,
            onOpenDetail = onOpenItemDetail,
            onOpenContainer = onOpenContainer,
            onNavigateUp = onNavigateUp,
            onPlayItem = onPlayItem,
            onConnectServer = onAddServer,
            selectedSpotlightId = selectedSpotlightId,
            onSelectedSpotlightIdChange = onSelectedSpotlightIdChange,
            onSelectFavorites = onSelectFavorites,
            showLibraryItems = showLibraryItems,
            downloadStatuses = downloadStatuses,
            offlineMedia = offlineMedia,
            contentPadding = contentPadding,
            libraryNavigationState = libraryNavigationState,
            onLibraryNavigationChange = onLibraryNavigationChange,
            spotlightAutoAdvanceEnabled = spotlightAutoAdvanceEnabled,
            spotlightAutoAdvanceIntervalMillis = spotlightAutoAdvanceIntervalMillis,
            modifier = Modifier.weight(1f, fill = true),
        )
    }
}

@Suppress("FunctionName")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    hasServers: Boolean,
    browseState: JellyfinHomeState,
    homeSectionsState: HomeSectionsState = HomeSectionsState.Unavailable,
    selectedSpotlightId: String?,
    onSelectedSpotlightIdChange: (String?) -> Unit,
    onSelectLibrary: (String) -> Unit,
    onRefreshLibraries: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenItemDetail: (JellyfinItem) -> Unit,
    onPlayItem: (JellyfinItem) -> Unit,
    onOpenSeerrItem: (HomeSectionItem) -> Unit = {},
    onConnectJellyfin: () -> Unit,
    onConnectJellyseerr: () -> Unit,
    learnMoreUrl: String,
    downloadStatuses: Map<String, DownloadStatus>,
    offlineMedia: List<OfflineMedia> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
    spotlightAutoAdvanceEnabled: Boolean = true,
    spotlightAutoAdvanceIntervalMillis: Long = 6_000L,
) {
    val configuredHome = homeSectionsState as? HomeSectionsState.Ready
    if (hasServers && configuredHome != null) {
        HomeSectionsScreen(
            state = configuredHome,
            contentPadding = contentPadding,
            onOpenJellyfinItem = onOpenItemDetail,
            onOpenSeerrItem = onOpenSeerrItem,
            modifier = modifier,
        )
    } else if (hasServers) {
        LibraryContent(
            browseState = browseState,
            selectedSpotlightId = selectedSpotlightId,
            onSelectedSpotlightIdChange = onSelectedSpotlightIdChange,
            onSelectLibrary = onSelectLibrary,
            onRefreshLibraries = onRefreshLibraries,
            onLoadMore = onLoadMore,
            onOpenItemDetail = onOpenItemDetail,
            onPlayItem = onPlayItem,
            onAddServer = onConnectJellyfin,
            showLibraryItems = false,
            downloadStatuses = downloadStatuses,
            offlineMedia = offlineMedia,
            contentPadding = contentPadding,
            modifier = modifier,
            spotlightAutoAdvanceEnabled = spotlightAutoAdvanceEnabled,
            spotlightAutoAdvanceIntervalMillis = spotlightAutoAdvanceIntervalMillis,
        )
    } else {
        WelcomeHomeContent(
            onConnectJellyfin = onConnectJellyfin,
            onConnectJellyseerr = onConnectJellyseerr,
            learnMoreUrl = learnMoreUrl,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Suppress("FunctionName")
@Composable
private fun WelcomeHomeContent(
    onConnectJellyfin: () -> Unit,
    onConnectJellyseerr: () -> Unit,
    learnMoreUrl: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
                                ),
                            ),
                    ).padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Welcome to Jellystack",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Build a proper streaming cockpit: connect Jellyfin for playback and Seerr for requests.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(onClick = onConnectJellyfin) {
                Text("Connect Jellyfin Server")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onConnectJellyseerr) {
                Text("Connect Seerr Server")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = {
                    runCatching { uriHandler.openUri(learnMoreUrl) }
                },
            ) {
                Text("Learn more")
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun WhatsNewDialog(
    version: String,
    highlights: List<String>,
    onClose: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.whats_new_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (version.isNotBlank()) {
                    Text(
                        text = stringResource(Res.string.version_label, version),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                highlights.filter { it.isNotBlank() }.forEach { highlight ->
                    Text("- $highlight", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onViewChangelog) {
                Text(stringResource(Res.string.view_changelog))
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

@Suppress("FunctionName")
@Composable
private fun DetailContent(
    state: JellyfinDetailUiState,
    enrichment: MediaDetailEnrichment = MediaDetailEnrichment(),
    enrichmentLoading: Boolean = false,
    libraryItems: List<JellyfinItem>,
    knownEpisodes: List<JellyfinItem>,
    onBack: () -> Unit,
    onViewSeries: (() -> Unit)? = null,
    onRetry: () -> Unit,
    onPlay: (JellyfinItem, JellyfinItemDetail) -> Unit,
    seriesPlaybackTarget: SeriesPlaybackTarget? = null,
    seriesPlaybackLoading: Boolean = false,
    noPlayableSeriesMessage: String? = null,
    onPlaySeries: (() -> Unit)? = null,
    onTrailer: (() -> Unit)? = null,
    downloadStatus: DownloadStatus? = null,
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onQueueDownload: (JellyfinItem, JellyfinItemDetail) -> Unit = { _, _ -> },
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onDownloadSeries: (() -> Unit)? = null,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)? = null,
    onOpenItemDetail: (JellyfinItem) -> Unit,
    audioTracks: List<AudioTrack> = emptyList(),
    selectedAudioTrack: AudioTrack? = null,
    onSelectAudioTrack: (AudioTrack) -> Unit = {},
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleTrack: SubtitleTrack? = null,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    favoriteError: String? = null,
    playedPending: Boolean = false,
    onTogglePlayed: () -> Unit = {},
    playedError: String? = null,
    modifier: Modifier = Modifier,
) {
    val detailStateHolder = rememberSaveableStateHolder()
    when (state) {
        JellyfinDetailUiState.Hidden ->
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Select an item to view details",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

        is JellyfinDetailUiState.Loading ->
            JellyfinDetailLoadingSkeleton(modifier = modifier.fillMaxSize())

        is JellyfinDetailUiState.Error ->
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }

        is JellyfinDetailUiState.Loaded ->
            detailStateHolder.SaveableStateProvider(state.item.id) {
                val episodes =
                    remember(state.detail.id, libraryItems, knownEpisodes) {
                        findEpisodesForDetail(
                            state = state,
                            libraryItems = libraryItems,
                            knownEpisodes = knownEpisodes,
                        )
                    }
                val availableEpisodes =
                    remember(episodes, downloadStatuses) {
                        episodes.availableEpisodes(downloadStatuses)
                    }
                val seasonGroups = remember(availableEpisodes) { buildSeasonEpisodes(availableEpisodes) }
                val isEpisode = state.item.type.equals("Episode", ignoreCase = true)
                val isSeries = state.item.type.equals("Series", ignoreCase = true)
                val seriesActionLabel =
                    seriesPlaybackTarget?.let { target ->
                        val episodeLabel = seriesEpisodeLabel(target.episode)
                        if (target.reason == SeriesPlaybackReason.CONTINUE) {
                            stringResource(Res.string.continue_episode, episodeLabel)
                        } else {
                            stringResource(Res.string.play_episode, episodeLabel)
                        }
                    }
                val hasOfflineSource = downloadStatus is DownloadStatus.Completed
                val hasRemoteSource = state.detail.mediaSources.isNotEmpty()
                val playEnabled =
                    if (isSeries) {
                        seriesPlaybackTarget != null
                    } else {
                        hasOfflineSource || hasRemoteSource
                    }
                ImmersiveMediaDetailContent(
                    item = state.item,
                    detail = state.detail,
                    enrichment = enrichment,
                    enrichmentLoading = enrichmentLoading,
                    baseUrl = state.imageBaseUrl,
                    accessToken = state.imageAccessToken,
                    seasons = seasonGroups,
                    onBack = onBack,
                    onPlay = onPlaySeries.takeIf { isSeries } ?: { onPlay(state.item, state.detail) },
                    onTrailer = onTrailer,
                    showPlayAction =
                        state.item.type.lowercase() in
                            setOf("movie", "episode", "video", "musicvideo", "audio", "audiobook", "trailer") ||
                            (isSeries && (seriesActionLabel != null || seriesPlaybackLoading)),
                    playActionLabel =
                        when {
                            seriesActionLabel != null -> seriesActionLabel
                            isSeries && seriesPlaybackLoading -> stringResource(Res.string.loading_episodes)
                            else -> stringResource(Res.string.play)
                        },
                    playActionEnabled = playEnabled,
                    playActionLoading = isSeries && seriesPlaybackLoading,
                    emptyPlaybackMessage =
                        noPlayableSeriesMessage.takeIf {
                            isSeries && !seriesPlaybackLoading && seriesPlaybackTarget == null
                        },
                    downloadStatus = downloadStatus,
                    episodeDownloadStatuses = downloadStatuses,
                    onQueueDownload = { onQueueDownload(state.item, state.detail) },
                    onPauseDownload = { onPauseDownload(state.item.id) },
                    onResumeDownload = { onResumeDownload(state.item.id) },
                    onRemoveDownload = { onRemoveDownload(state.item.id) },
                    onDownloadSeries = onDownloadSeries,
                    onDownloadSeason = onDownloadSeason,
                    onViewSeries = if (isEpisode) onViewSeries else null,
                    onOpenEpisode = onOpenItemDetail,
                    onOpenItemDetail = onOpenItemDetail,
                    audioTracks = audioTracks,
                    selectedAudioTrack = selectedAudioTrack,
                    onSelectAudioTrack = onSelectAudioTrack,
                    subtitleTracks = subtitleTracks,
                    selectedSubtitleTrack = selectedSubtitleTrack,
                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    favoriteError = favoriteError,
                    showPlayedAction = supportsPlayedStatus(state.item.type),
                    isPlayed = state.detail.isPlayed,
                    playedPending = playedPending,
                    onTogglePlayed = onTogglePlayed,
                    playedError = playedError,
                    modifier = modifier.fillMaxSize(),
                )
            }
    }
}

private fun seriesEpisodeLabel(episode: JellyfinItem): String =
    when {
        episode.parentIndexNumber != null && episode.indexNumber != null ->
            "S${episode.parentIndexNumber} E${episode.indexNumber}"
        else -> episode.name
    }

private fun JellyfinDetailUiState.itemOrNull(): JellyfinItem? =
    when (this) {
        JellyfinDetailUiState.Hidden -> null
        is JellyfinDetailUiState.Error -> item
        is JellyfinDetailUiState.Loaded -> item
        is JellyfinDetailUiState.Loading -> item
    }

private fun findEpisodesForDetail(
    state: JellyfinDetailUiState.Loaded,
    libraryItems: List<JellyfinItem>,
    knownEpisodes: List<JellyfinItem>,
): List<JellyfinItem> {
    val targetNames =
        buildSet {
            add(state.detail.name.lowercase())
            add(state.item.name.lowercase())
            state.item.seriesName
                ?.lowercase()
                ?.let { add(it) }
        }
    val targetIds =
        buildSet {
            if (state.item.type.equals("Series", ignoreCase = true)) {
                add(state.item.id)
            }
            state.item.parentId?.let { add(it) }
            state.item.seasonId?.let { add(it) }
            state.item.seriesId?.let { add(it) }
        }
    return (if (knownEpisodes.isNotEmpty()) knownEpisodes else libraryItems)
        .asSequence()
        .filter { it.type.equals("Episode", ignoreCase = true) }
        .filter { episode ->
            val matchesName = episode.seriesName?.lowercase()?.let { it in targetNames } ?: false
            val matchesId =
                episode.parentId?.let { it in targetIds } == true ||
                    episode.seasonId?.let { it in targetIds } == true ||
                    episode.seriesId?.let { it in targetIds } == true
            matchesName || matchesId
        }.toList()
}

private fun List<JellyfinItem>.availableEpisodes(downloadStatuses: Map<String, DownloadStatus>): List<JellyfinItem> =
    filter { episode ->
        val isDownloaded = downloadStatuses[episode.id] is DownloadStatus.Completed
        isDownloaded || episode.hasLocalMedia()
    }

private fun JellyfinItem.toSeriesNavigationTarget(): JellyfinItem {
    val targetId = seriesId ?: parentId ?: id
    return copy(
        id = targetId,
        type = "Series",
        name = seriesName ?: name,
        sortName = sortName ?: seriesName,
        seriesId = targetId,
        parentId = null,
        seasonId = null,
        indexNumber = null,
        parentIndexNumber = null,
        episodeTitle = null,
    )
}

private fun JellyfinItem.toOfflineDetail(): JellyfinItemDetail =
    JellyfinItemDetail(
        id = id,
        name = name,
        overview = overview,
        taglines = taglines,
        runTimeTicks = runTimeTicks,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        genres = emptyList(),
        studios = emptyList(),
        primaryImageTag = primaryImageTag,
        backdropImageTags =
            buildList {
                backdropImageTag?.let(::add)
                seriesBackdropImageTag?.let(::add)
            },
        mediaSources = emptyList(),
        isPlayed = (playedPercentage ?: 0.0) >= 90.0,
    )

private fun List<AudioTrack>.defaultAudioTrackId(): String? = firstOrNull { it.isDefault }?.id ?: firstOrNull()?.id

private fun List<SubtitleTrack>.defaultSubtitleTrackId(): String? = firstOrNull { it.isDefault }?.id ?: firstOrNull { !it.isForced }?.id

private fun durationMillisFromTicks(ticks: Long?): Long? = ticks?.div(10_000L)

@Suppress("FunctionName")
@Composable
private fun BiometricLockOverlay(
    state: BiometricLockState,
    capability: BiometricCapability,
    canRetry: Boolean,
    onUnlock: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val overlayTint = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
    val enrollmentGuidance = stringResource(Res.string.app_lock_enroll_device)
    val supportingText =
        when {
            !capability.secureCredentialAvailable -> enrollmentGuidance
            state == BiometricLockState.Locked ->
                capability.description ?: stringResource(Res.string.app_lock_prompt)
            state == BiometricLockState.Unlocking ->
                stringResource(Res.string.app_lock_waiting)
            state is BiometricLockState.Error ->
                state.reason.ifBlank { stringResource(Res.string.app_lock_authentication_unavailable) }
            else -> ""
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = true,
                    onClick = {},
                ).background(overlayTint),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.app_lock_locked_heading),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state == BiometricLockState.Unlocking) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onUnlock,
                            enabled = canRetry,
                        ) {
                            Text(stringResource(Res.string.retry))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("FunctionName", "UNUSED_PARAMETER")
@Composable
private fun AddServerDialog(
    state: ServerFormState,
    isSaving: Boolean,
    quickConnectState: JellyfinQuickConnectState?,
    errorMessage: String?,
    availableJellyfinServers: List<ManagedServer>,
    onValueChange: (ServerFormState) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onRestartQuickConnect: () -> Unit,
) {
    var passwordVisible by remember(state.type) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isSaving || quickConnectState != null) onDismiss() },
        title = { Text(stringResource(Res.string.connect_server)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == ServerFormType.JELLYFIN,
                        onClick = {
                            if (!isSaving) {
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.JELLYFIN,
                                        email = "",
                                        password = "",
                                        useJellyfinLogin = false,
                                        automaticSeerrLogin = false,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving,
                        label = { Text("Jellyfin") },
                    )
                    FilterChip(
                        selected = state.type == ServerFormType.SEERR,
                        onClick = {
                            if (!isSaving) {
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        type = ServerFormType.SEERR,
                                        username = "",
                                        password = "",
                                        useJellyfinLogin = true,
                                        automaticSeerrLogin = true,
                                    ),
                                )
                                onClearError()
                            }
                        },
                        enabled = !isSaving,
                        label = { Text("Seerr") },
                    )
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = {
                        onValueChange(state.copy(name = it))
                        onClearError()
                    },
                    label = { Text(stringResource(Res.string.server_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = {
                        onValueChange(state.copy(baseUrl = it, allowInsecureHttp = false))
                        onClearError()
                    },
                    label = { Text(stringResource(Res.string.base_url)) },
                    placeholder = { Text("https://jellyfin.example.com") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError =
                        state.serverAddressValidation is ServerAddressValidation.MissingProtocol ||
                            state.serverAddressValidation is ServerAddressValidation.Invalid,
                    supportingText =
                        when (state.serverAddressValidation) {
                            ServerAddressValidation.MissingProtocol ->
                                {
                                    {
                                        Text(stringResource(Res.string.server_url_missing_protocol))
                                    }
                                }
                            ServerAddressValidation.Invalid ->
                                {
                                    {
                                        Text(stringResource(Res.string.onboarding_url_error))
                                    }
                                }
                            else -> null
                        },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Uri),
                )
                if (state.requiresInsecureHttpConfirmation) {
                    InsecureHttpWarning(
                        confirmed = state.allowInsecureHttp,
                        onConfirmedChange = { confirmed ->
                            onValueChange(state.copy(allowInsecureHttp = confirmed))
                            onClearError()
                        },
                        enabled = !isSaving,
                    )
                }
                when (state.type) {
                    ServerFormType.JELLYFIN -> {
                        JellyfinSignInMethodSelector(
                            selected = state.jellyfinSignInMethod,
                            onSelected = { method ->
                                passwordVisible = false
                                onValueChange(
                                    state.copy(
                                        jellyfinSignInMethod = method,
                                        password = "",
                                    ),
                                )
                                onClearError()
                            },
                            enabled = !isSaving || quickConnectState != null,
                        )
                        if (quickConnectState != null) {
                            JellyfinQuickConnectStatus(
                                state = quickConnectState,
                                onUsePassword = {
                                    onValueChange(
                                        state.copy(
                                            jellyfinSignInMethod = JellyfinSignInMethod.PASSWORD,
                                        ),
                                    )
                                    onClearError()
                                },
                                onNewCode = onRestartQuickConnect,
                                onCancel = onDismiss,
                            )
                        } else if (state.jellyfinSignInMethod == JellyfinSignInMethod.QUICK_CONNECT) {
                            Text(
                                text = stringResource(Res.string.quick_connect_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = {
                                    onValueChange(state.copy(username = it))
                                    onClearError()
                                },
                                label = { Text(stringResource(Res.string.username)) },
                                placeholder = { Text("user") },
                                singleLine = true,
                                enabled = !isSaving,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = {
                                    onValueChange(state.copy(password = it))
                                    onClearError()
                                },
                                label = { Text(stringResource(Res.string.password)) },
                                singleLine = true,
                                enabled = !isSaving,
                                visualTransformation =
                                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions =
                                    KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val icon =
                                        if (passwordVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        }
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription =
                                                stringResource(
                                                    if (passwordVisible) {
                                                        Res.string.hide_password
                                                    } else {
                                                        Res.string.show_password
                                                    },
                                                ),
                                        )
                                    }
                                },
                                modifier = Modifier.semantics { passwordSemantics() },
                            )
                        }
                    }
                    ServerFormType.SEERR -> {
                        if (state.automaticSeerrLogin) {
                            Text(
                                text =
                                    if (availableJellyfinServers.isNotEmpty()) {
                                        stringResource(Res.string.seerr_automatic_login)
                                    } else {
                                        stringResource(Res.string.seerr_connect_jellyfin_first)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    onValueChange(
                                        state.copy(
                                            automaticSeerrLogin = false,
                                            useJellyfinLogin = availableJellyfinServers.isNotEmpty(),
                                        ),
                                    )
                                    onClearError()
                                },
                                enabled = !isSaving,
                            ) {
                                Text(stringResource(Res.string.use_different_account))
                            }
                        } else {
                            Text(stringResource(Res.string.sign_in_with), style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !state.useJellyfinLogin,
                                    onClick = {
                                        if (!isSaving) {
                                            onValueChange(
                                                state.copy(
                                                    useJellyfinLogin = false,
                                                    username = "",
                                                ),
                                            )
                                            onClearError()
                                        }
                                    },
                                    enabled = !isSaving,
                                    label = { Text(stringResource(Res.string.seerr_account)) },
                                )
                                FilterChip(
                                    selected = state.useJellyfinLogin,
                                    onClick = {
                                        if (!isSaving) {
                                            onValueChange(
                                                state.copy(
                                                    useJellyfinLogin = true,
                                                    email = "",
                                                ),
                                            )
                                            onClearError()
                                        }
                                    },
                                    enabled = !isSaving,
                                    label = { Text(stringResource(Res.string.jellyfin_account)) },
                                )
                            }
                            if (state.useJellyfinLogin) {
                                OutlinedTextField(
                                    value = state.username,
                                    onValueChange = {
                                        onValueChange(state.copy(username = it))
                                        onClearError()
                                    },
                                    label = { Text(stringResource(Res.string.username)) },
                                    placeholder = { Text("user") },
                                    singleLine = true,
                                    enabled = !isSaving,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                )
                            } else {
                                OutlinedTextField(
                                    value = state.email,
                                    onValueChange = {
                                        onValueChange(state.copy(email = it))
                                        onClearError()
                                    },
                                    label = { Text(stringResource(Res.string.email)) },
                                    placeholder = { Text("user@example.com") },
                                    singleLine = true,
                                    enabled = !isSaving,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Email),
                                )
                            }
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = {
                                    onValueChange(state.copy(password = it))
                                    onClearError()
                                },
                                label = { Text(stringResource(Res.string.password)) },
                                singleLine = true,
                                enabled = !isSaving,
                                visualTransformation =
                                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions =
                                    KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription =
                                                stringResource(
                                                    if (passwordVisible) Res.string.hide_password else Res.string.show_password,
                                                ),
                                        )
                                    }
                                },
                                modifier = Modifier.semantics { passwordSemantics() },
                            )
                        }
                    }
                }
                if (errorMessage != null && quickConnectState == null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (quickConnectState == null) {
                Button(
                    onClick = onSubmit,
                    enabled = state.isValid && !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.onboarding_saving))
                    } else {
                        val label =
                            when (state.type) {
                                ServerFormType.JELLYFIN -> stringResource(Res.string.connect_jellyfin)
                                ServerFormType.SEERR -> stringResource(Res.string.connect_seerr)
                            }
                        Text(label)
                    }
                }
            }
        },
        dismissButton = {
            if (quickConnectState == null) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

private object JellystackTags {
    const val THEME_SWITCH = "theme_switch"
    const val SECURITY_SWITCH = "security_switch"
}

private object CastTestTags {
    const val ROUTE_PICKER_TOP_BAR = "cast_route_picker_top_bar"
    const val STATUS_IDLE_ICON = "cast_status_idle_icon"
    const val STATUS_CONNECTING = "cast_status_connecting"
    const val STATUS_CONNECTING_SPINNER = "cast_status_connecting_spinner"
    const val DISCONNECT_BUTTON = "cast_disconnect_button"
    const val RECONNECT_BUTTON = "cast_reconnect_button"
    const val STATUS_BANNER = "cast_status_banner"
    const val STOP_CASTING_BUTTON = "cast_stop_casting_button"
    const val RECONNECT_BANNER_BUTTON = "cast_reconnect_banner_button"
    const val DISMISS_ERROR_BUTTON = "cast_dismiss_error_button"
}

private fun buildDownloadRequests(
    item: JellyfinItem,
    request: PlaybackRequest,
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
): List<DownloadRequest> {
    val requests = mutableListOf<DownloadRequest>()
    val metadata = item.toOfflineMediaMetadata()
    val durationMs = durationMillisFromTicks(request.durationTicks)
    val videoBitrate = selection.videoBitrate
    val expectedSize =
        if (durationMs != null && durationMs > 0 && videoBitrate != null && videoBitrate > 0) {
            val seconds = durationMs / 1_000.0
            ((videoBitrate.toLong() * seconds) / 8.0).toLong()
        } else {
            null
        }
    requests +=
        DownloadRequest(
            mediaId = item.id,
            downloadUrl = source.url,
            headers = source.headers,
            mimeType = source.mimeType,
            expectedSizeBytes = expectedSize,
            checksumSha256 = null,
            kind = OfflineMediaKind.VIDEO,
            language = null,
            relativePath = buildVideoRelativePath(item, selection, source),
            metadata = metadata,
        )
    source.subtitles.forEach { subtitle ->
        val trackId = subtitle.trackId.ifBlank { "unknown" }
        val ext = subtitleExtensionFromMime(subtitle.mimeType)
        val subtitleUrl = subtitle.url
        requests +=
            DownloadRequest(
                mediaId = "${item.id}::sub::$trackId",
                downloadUrl = subtitleUrl,
                headers = source.headers,
                mimeType = subtitle.mimeType,
                expectedSizeBytes = null,
                checksumSha256 = null,
                kind = OfflineMediaKind.SUBTITLE,
                language = subtitle.language,
                relativePath = subtitleRelativePath(item, subtitle, ext, trackId),
                metadata = metadata,
            )
    }
    return requests
}

private fun JellyfinItem.toOfflineMediaMetadata(): OfflineMediaMetadata =
    OfflineMediaMetadata(
        itemId = id,
        libraryId = libraryId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        primaryImageTag = primaryImageTag,
        thumbImageTag = thumbImageTag,
        backdropImageTag = backdropImageTag,
        seriesId = seriesId,
        seriesName = seriesName,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag,
        parentLogoImageTag = parentLogoImageTag,
        runTimeTicks = runTimeTicks,
        positionTicks = positionTicks,
        playedPercentage = playedPercentage,
        productionYear = productionYear,
        premiereDate = premiereDate,
        officialRating = officialRating,
        indexNumber = indexNumber,
        parentIndexNumber = parentIndexNumber,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        dateCreated = dateCreated,
        logoImageTag = logoImageTag,
        artImageTag = artImageTag,
        bannerImageTag = bannerImageTag,
        seriesLogoImageTag = seriesLogoImageTag,
        seriesArtImageTag = seriesArtImageTag,
        seriesBannerImageTag = seriesBannerImageTag,
    )

private fun buildVideoRelativePath(
    item: JellyfinItem,
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
): String {
    val extension = determineVideoExtension(selection, source)
    return "${item.id}/${item.id}.$extension"
}

private fun determineVideoExtension(
    selection: PlaybackStreamSelection,
    source: ResolvedPlaybackSource,
): String {
    val container = selection.container?.lowercase()
    if (!container.isNullOrBlank()) return container
    return when (source.mimeType) {
        "video/mp4", "application/mp4" -> "mp4"
        "video/x-matroska" -> "mkv"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> "mp4"
    }
}

@Suppress("FunctionName")
@Composable
private fun CastToolbarAction(
    state: CastConnectionState,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        CastConnectionState.Idle ->
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .testTag(CastTestTags.STATUS_IDLE_ICON)
                        .semantics {
                            contentDescription = "No cast device connected"
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cast,
                    contentDescription = null,
                    tint = tint,
                )
            }

        is CastConnectionState.Connecting ->
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .testTag(CastTestTags.STATUS_CONNECTING)
                        .semantics {
                            contentDescription = "Connecting to cast device"
                        },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }

        is CastConnectionState.Connected ->
            IconButton(
                modifier =
                    Modifier
                        .testTag(CastTestTags.DISCONNECT_BUTTON)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Disconnect from ${state.deviceName}"
                        },
                onClick = onDisconnect,
            ) {
                Icon(
                    imageVector = Icons.Filled.CastConnected,
                    contentDescription = null,
                )
            }

        is CastConnectionState.Error ->
            IconButton(
                modifier =
                    Modifier
                        .testTag(CastTestTags.RECONNECT_BUTTON)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Reconnect cast session"
                        },
                onClick = onReconnect,
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
    }
}

@Composable
private fun CastStatusBanner(
    state: CastConnectionState,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    val showBanner =
        state is CastConnectionState.Connecting ||
            state is CastConnectionState.Connected ||
            state is CastConnectionState.Error
    AnimatedVisibility(visible = showBanner) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(CastTestTags.STATUS_BANNER),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    CastConnectionState.Idle -> Unit

                    is CastConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .testTag(CastTestTags.STATUS_CONNECTING_SPINNER),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Connecting to ${state.deviceName ?: "device"}")
                        Spacer(modifier = Modifier.weight(1f))
                        return@Row
                    }

                    is CastConnectionState.Connected -> {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = "Casting to ${state.deviceName}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val detail =
                                state.snapshot.title
                                    ?: state.snapshot.episodeName
                                    ?: state.snapshot.seriesName
                            detail?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(
                            modifier = Modifier.testTag(CastTestTags.STOP_CASTING_BUTTON),
                            onClick = onDisconnect,
                        ) {
                            Text("Stop casting")
                        }
                        return@Row
                    }

                    is CastConnectionState.Error -> {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = "Cast connection lost",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            state.cause
                                ?.message
                                ?.takeIf { it.isNotBlank() }
                                ?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                modifier = Modifier.testTag(CastTestTags.RECONNECT_BANNER_BUTTON),
                                onClick = onReconnect,
                            ) {
                                Text("Reconnect")
                            }
                            TextButton(
                                modifier = Modifier.testTag(CastTestTags.DISMISS_ERROR_BUTTON),
                                onClick = onDisconnect,
                            ) {
                                Text("Dismiss")
                            }
                        }
                        return@Row
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun subtitleRelativePath(
    item: JellyfinItem,
    subtitle: ResolvedSubtitle,
    extension: String,
    trackId: String,
): String {
    val sanitizedTrackId = sanitizeFileSegment(trackId)
    val base =
        subtitle.language?.let(::sanitizeFileSegment)
            ?: subtitle.label?.let(::sanitizeFileSegment)
            ?: "track"
    return "${item.id}/subtitles/${sanitizedTrackId}_$base.$extension"
}

private fun subtitleExtensionFromMime(mimeType: String?): String =
    when (mimeType?.lowercase()) {
        "application/x-subrip", "text/srt" -> "srt"
        "text/x-ass" -> "ass"
        "text/x-ssa" -> "ssa"
        else -> "vtt"
    }

private fun sanitizeFileSegment(value: String): String {
    val sanitized = value.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    val trimmed = sanitized.trim('_')
    return if (trimmed.isBlank()) "file" else trimmed
}

private fun Throwable.connectivityErrorMessage(): String =
    when (this) {
        is ConnectivityException ->
            listOfNotNull(message, cause?.message)
                .joinToString(separator = ": ")
                .ifBlank { "Unable to connect to server" }
        else -> message ?: "Unable to connect to server"
    }
