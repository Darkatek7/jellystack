package dev.jellystack.design.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.jellyfin.JellyfinDetailContent
import dev.jellystack.design.jellyfin.LibraryNavigationState
import dev.jellystack.design.jellyseerr.DiscoverAction
import dev.jellystack.design.jellyseerr.DiscoverScreen
import dev.jellystack.design.jellyseerr.DiscoverSelectionContent
import dev.jellystack.design.jellyseerr.DiscoverUiState
import dev.jellystack.design.jellyseerr.reduce
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.onboarding.OnboardingScreen
import dev.jellystack.design.settings.SettingsScreen
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellAction
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.shell.JellystackTopBar
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.players.cast.CastConnectionState
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.app_title
import jellystack_mobile.design.generated.resources.nav_discover
import jellystack_mobile.design.generated.resources.nav_library
import jellystack_mobile.design.generated.resources.requests_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun JellystackPreviewFixture(
    fixtureName: String,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scenario = JellystackPreviewData.scenario(fixtureName)
    JellystackTheme(isDarkTheme = darkTheme) {
        ProvideResponsiveProfile(modifier = modifier.fillMaxSize()) {
            PreviewScenarioShell(scenario)
        }
    }
}

@Composable
private fun PreviewScenarioShell(scenario: JellystackPreviewScenario) {
    val initialPrimary =
        when (scenario) {
            JellystackPreviewScenario.Home -> PrimaryDestination.Home
            JellystackPreviewScenario.Library,
            JellystackPreviewScenario.Detail,
            -> PrimaryDestination.Library
            JellystackPreviewScenario.Discover,
            JellystackPreviewScenario.Requests,
            -> PrimaryDestination.Discover
            JellystackPreviewScenario.Settings,
            JellystackPreviewScenario.Onboarding,
            -> PrimaryDestination.Home
        }
    var primary by remember(scenario) { mutableStateOf(initialPrimary) }
    var libraryNavigation by remember(scenario) { mutableStateOf(LibraryNavigationState()) }
    var discoverState by
        remember(scenario) {
            mutableStateOf(
                if (scenario == JellystackPreviewScenario.Requests) {
                    DiscoverUiState(destination = DiscoverDestination.Requests)
                        .reduce(
                            DiscoverAction.SelectSearchResult(
                                JellystackPreviewData.requestSelectionItem,
                            ),
                        )
                } else {
                    DiscoverUiState()
                },
            )
        }
    var selectedSpotlightId by remember(scenario) { mutableStateOf<String?>(null) }
    val standalone = scenario == JellystackPreviewScenario.Settings || scenario == JellystackPreviewScenario.Onboarding
    val detail = scenario == JellystackPreviewScenario.Detail
    val title =
        when (scenario) {
            JellystackPreviewScenario.Home -> stringResource(Res.string.app_title)
            JellystackPreviewScenario.Library -> stringResource(Res.string.nav_library)
            JellystackPreviewScenario.Discover -> stringResource(Res.string.nav_discover)
            JellystackPreviewScenario.Requests -> stringResource(Res.string.requests_title)
            JellystackPreviewScenario.Detail -> JellystackPreviewData.detail.name
            JellystackPreviewScenario.Settings,
            JellystackPreviewScenario.Onboarding,
            -> ""
        }

    JellystackShell(
        state =
            JellystackShellState(
                primary = primary,
                discover = discoverState.destination,
                paneMode =
                    if (scenario == JellystackPreviewScenario.Requests) {
                        ShellPaneMode.ListDetail
                    } else {
                        ShellPaneMode.Single
                    },
                showNavigation = !standalone && !detail,
            ),
        onAction = { action ->
            if (action is JellystackShellAction.SelectPrimary) primary = action.destination
        },
        topBar = {
            if (!standalone) {
                JellystackTopBar(
                    title = title,
                    showBack = detail || scenario == JellystackPreviewScenario.Requests,
                    onBack = {},
                    castState = CastConnectionState.Idle,
                    renderCastButton = {},
                    onOpenSettings = {},
                )
            }
        },
        primaryContent = { padding ->
            when (scenario) {
                JellystackPreviewScenario.Home ->
                    BrowseFixture(
                        contentPadding = padding,
                        showLibraryItems = false,
                        selectedSpotlightId = selectedSpotlightId,
                        onSelectedSpotlightIdChange = { selectedSpotlightId = it },
                        libraryNavigation = libraryNavigation,
                        onLibraryNavigationChange = { libraryNavigation = it },
                        spotlightPainter = SplitLuminanceArtworkPainter,
                    )
                JellystackPreviewScenario.Library ->
                    BrowseFixture(
                        contentPadding = padding,
                        showLibraryItems = true,
                        selectedSpotlightId = selectedSpotlightId,
                        onSelectedSpotlightIdChange = { selectedSpotlightId = it },
                        libraryNavigation = libraryNavigation,
                        onLibraryNavigationChange = { libraryNavigation = it },
                    )
                JellystackPreviewScenario.Discover,
                JellystackPreviewScenario.Requests,
                ->
                    DiscoverScreen(
                        state = discoverState,
                        recommendationsState = JellystackPreviewData.recommendationsState,
                        recommendationDetails = emptyMap(),
                        requestsState = JellystackPreviewData.requestsState,
                        languageProfiles = JellystackPreviewData.languageProfiles,
                        contentPadding = padding,
                        onAction = { discoverState = discoverState.reduce(it) },
                        onRecommendationsRefresh = {},
                        onRecommendationsRetry = {},
                        onRecommendationsLoadNext = {},
                        onRecommendationLoadDetail = {},
                        onRecommendationOpenDetails = { _, _, _ -> },
                        onRecommendationRequestOpen = { _, _, _ -> },
                        onRecommendationTrailer = { _, _, _, _ -> },
                        onRecommendationImpression = { _, _, _ -> },
                        onClearSearch = {},
                        onAddServer = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                JellystackPreviewScenario.Settings ->
                    SettingsScreen(
                        state = JellystackPreviewData.settingsState,
                        onAction = {},
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                JellystackPreviewScenario.Onboarding ->
                    OnboardingScreen(
                        state = JellystackPreviewData.onboardingState,
                        onAction = {},
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                JellystackPreviewScenario.Detail ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        JellyfinDetailContent(
                            detail = JellystackPreviewData.detail,
                            baseUrl = null,
                            accessToken = null,
                            seasons = emptyList(),
                            isEpisode = false,
                            onPlay = {},
                            onQueueDownload = {},
                            isFavorite = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
            }
        },
        secondaryContent =
            if (scenario == JellystackPreviewScenario.Requests) {
                { padding ->
                    DiscoverSelectionContent(
                        state = discoverState,
                        detailStates = emptyMap(),
                        languageProfiles = JellystackPreviewData.languageProfiles,
                        onSelectProfile = {
                            discoverState = discoverState.reduce(DiscoverAction.SelectProfile(it))
                        },
                        onSelectSeasons = {
                            discoverState = discoverState.reduce(DiscoverAction.SelectSeasonSelection(it))
                        },
                        onSubmit = { _, _, _ -> },
                        onApprove = {},
                        onDelete = {},
                        onRemoveMedia = {},
                        onConfigureRequest = {
                            discoverState =
                                discoverState.reduce(DiscoverAction.OpenRequestConfiguration)
                        },
                        onCloseRequestConfiguration = {
                            discoverState =
                                discoverState.reduce(DiscoverAction.CloseRequestConfiguration)
                        },
                        isAdmin = true,
                        onClose = {
                            discoverState = discoverState.reduce(DiscoverAction.CloseSelection)
                        },
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun BrowseFixture(
    contentPadding: PaddingValues,
    showLibraryItems: Boolean,
    selectedSpotlightId: String?,
    onSelectedSpotlightIdChange: (String?) -> Unit,
    libraryNavigation: LibraryNavigationState,
    onLibraryNavigationChange: (LibraryNavigationState) -> Unit,
    spotlightPainter: androidx.compose.ui.graphics.painter.Painter? = null,
) {
    JellyfinBrowseScreen(
        state = JellystackPreviewData.homeState,
        onSelectLibrary = {},
        onRefresh = {},
        onLoadMore = {},
        onOpenDetail = {},
        onPlayItem = {},
        onConnectServer = {},
        selectedSpotlightId = selectedSpotlightId,
        onSelectedSpotlightIdChange = onSelectedSpotlightIdChange,
        showLibraryItems = showLibraryItems,
        contentPadding = contentPadding,
        libraryNavigationState = libraryNavigation,
        onLibraryNavigationChange = onLibraryNavigationChange,
        spotlightPainter = spotlightPainter,
        spotlightEligibilityNow = JellystackPreviewData.fixedClock,
        modifier = Modifier.fillMaxSize(),
    )
}
