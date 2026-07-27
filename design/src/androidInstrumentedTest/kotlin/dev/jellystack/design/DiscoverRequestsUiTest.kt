package dev.jellystack.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyseerr.JellyseerrDetailEnrichmentSection
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.jellyseerr.JellyseerrSeasonStatus
import dev.jellystack.core.jellyseerr.JellyseerrUser
import dev.jellystack.design.components.ModalFocusScope
import dev.jellystack.design.jellyseerr.DiscoverAction
import dev.jellystack.design.jellyseerr.DiscoverPendingOperation
import dev.jellystack.design.jellyseerr.DiscoverScreen
import dev.jellystack.design.jellyseerr.DiscoverSelectionContent
import dev.jellystack.design.jellyseerr.DiscoverUiState
import dev.jellystack.design.jellyseerr.SeerrImmersiveDetailTestTags
import dev.jellystack.design.jellyseerr.reduce
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscoverRequestsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()

    @Test
    fun requestsIsAChildPageAndBackRestoresDiscoverFeed() {
        composeRule.setContent { discoverRequestsHarness() }

        composeRule.onNodeWithText("Search & requests").performClick()
        composeRule.onAllNodesWithText("Requests").assertCountEquals(2)
        composeRule.onNodeWithTag("primary_destination_discover").assertIsSelected()

        composeRule.onNodeWithContentDescription("Back to Discover").performClick()
        composeRule.onAllNodesWithText("Trends").assertCountEquals(2)
        composeRule.onNodeWithTag("primary_destination_discover").assertIsSelected()
    }

    @Test
    fun noNamedProfilesStillOffersServerDefault() {
        composeRule.setContent {
            discoverRequestsHarness(
                initialState =
                    DiscoverUiState(
                        destination = DiscoverDestination.Requests,
                        requestQuery = "Dune",
                    ),
                requestsState = readyRequests(searchResults = listOf(dune())),
                detailStates = mapOf(dune().mediaType to dune().tmdbId to loadedDetail(dune())),
            )
        }

        composeRule.onNode(hasText("Dune") and hasText("View details") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Request").performClick()
        composeRule.onNodeWithText("Search request profiles").assertExists()
        composeRule.onNodeWithText("Server default").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Submit request").assertIsEnabled()
    }

    @Test
    fun expandedRequestsKeepsListAndConfigurationVisible() {
        composeRule.setContent {
            discoverRequestsHarness(
                width = 1_000.dp,
                initialState =
                    DiscoverUiState(destination = DiscoverDestination.Requests)
                        .reduce(DiscoverAction.SelectSearchResult(dune())),
                requestsState = readyRequests(searchResults = listOf(dune())),
                detailStates = mapOf(dune().mediaType to dune().tmdbId to loadedDetail(dune())),
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.PRIMARY_PANE).assertExists()
        composeRule.onNodeWithTag(ShellTestTags.SECONDARY_PANE).assertExists()
        composeRule.onAllNodesWithText("Requests").assertCountEquals(2)
        composeRule.onNodeWithText("Request").performClick()
        composeRule.onNodeWithText("Server default").assertIsEnabled()
    }

    @Test
    fun recommendationTapHidesFeedSemanticsUntilDetailCloses() {
        composeRule.setContent {
            discoverRequestsHarness(
                detailStates = mapOf(dune().mediaType to dune().tmdbId to loadedDetail(dune())),
            )
        }

        composeRule.onNodeWithText("Dune").performClick()

        composeRule.onNodeWithText("A mythic journey across Arrakis.").assertExists()
        composeRule.onNodeWithText("Request profile").assertDoesNotExist()
        composeRule.onAllNodesWithText("Trends").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onAllNodesWithText("Trends").assertCountEquals(2)
    }

    @Test
    fun compactRecommendationUsesFullWidthDetailActionsAndTopClose() {
        composeRule.setContent {
            discoverRequestsHarness(
                detailStates = mapOf(dune().mediaType to dune().tmdbId to loadedDetail(dune())),
            )
        }

        composeRule.onNodeWithText("Dune").performClick()

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.HERO).assertWidthIsAtLeast(360.dp)
        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithText("Close").assertDoesNotExist()
    }

    @Test
    fun recommendationDetailsShowThemeAwareSeerrRatings() {
        val item = dune()
        composeRule.setContent {
            discoverRequestsHarness(
                detailStates =
                    mapOf(
                        item.mediaType to item.tmdbId to
                            loadedDetail(
                                item,
                                ratings =
                                    JellyseerrMediaRatings(
                                        tmdb = 8.6,
                                        imdb = 8.3,
                                        rottenTomatoesCritics = 92.0,
                                        rottenTomatoesAudience = 89.0,
                                    ),
                            ),
                    ),
            )
        }

        composeRule.onNodeWithText("Dune").performClick()

        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT).performScrollToIndex(3)
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.RATINGS).assertExists()
        composeRule.onNodeWithText("TMDb").assertExists()
        composeRule.onNodeWithText("8.6").assertExists()
        composeRule.onNodeWithText("IMDb").assertExists()
        composeRule.onNodeWithText("8.3").assertExists()
        composeRule.onNodeWithText("RT critics").assertExists()
        composeRule.onNodeWithText("92%").assertExists()
        composeRule.onNodeWithText("RT audience").assertExists()
        composeRule.onNodeWithText("89%").assertExists()
    }

    @Test
    fun requestTapOpensMediaDetailsWithDeleteAndAdditionalSeasons() {
        val request = showRequest()
        val item = requestItem(request)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState(destination = DiscoverDestination.Requests),
                requestsState = readyRequests(requests = listOf(request)),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item, seasons = listOf(1, 2, 3))),
            )
        }

        composeRule.onNodeWithText("Sample Show").performClick()

        composeRule.onNodeWithText("Request more seasons").assertExists()
        composeRule.onNodeWithText("Partial").assertExists()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT).performScrollToIndex(3)
        composeRule.onNodeWithText("Detailed show overview.").assertExists()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Open in Seerr").assertExists()
        composeRule.onNodeWithText("Delete request").assertExists()
    }

    @Test
    fun detailUsesFilterIndependentCurrentRequestLookup() {
        val request = showRequest()
        val item =
            requestItem(request).copy(
                availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
                requests = emptyList(),
            )
        val requestsState =
            readyRequests(requests = emptyList()).copy(
                currentRequestsByMedia =
                    mapOf(request.mediaType to requireNotNull(request.tmdbId) to request),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState =
                    DiscoverUiState(destination = DiscoverDestination.Requests)
                        .reduce(DiscoverAction.SelectSearchResult(item)),
                requestsState = requestsState,
                detailStates =
                    mapOf(
                        item.mediaType to item.tmdbId to
                            loadedDetail(item, seasons = listOf(1, 2, 3)),
                    ),
            )
        }

        composeRule.onNodeWithText("Request more seasons").assertExists()
        composeRule.onNodeWithText("Partial").assertExists()
    }

    @Test
    fun requestsDetailUsesExactSelectedRequestWhenMediaHasMultipleRequests() {
        val selected =
            showRequest().copy(
                requestedBy =
                    JellyseerrUser(
                        id = 7,
                        displayName = "Selected owner",
                        username = "selected",
                        permissions = 0,
                    ),
            )
        val sameMedia =
            selected.copy(
                id = 73,
                requestStatus = JellyseerrRequestStatus.PENDING,
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PENDING,
                        `4k` = null,
                    ),
                requestedBy =
                    JellyseerrUser(
                        id = 8,
                        displayName = "Other owner",
                        username = "other",
                        permissions = 0,
                    ),
            )
        val item = requestItem(selected)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState =
                    DiscoverUiState(destination = DiscoverDestination.Requests)
                        .reduce(DiscoverAction.SelectExistingRequest(selected)),
                requestsState =
                    readyRequests(
                        requests = listOf(selected, sameMedia),
                        isAdmin = false,
                        currentUserId = 7,
                    ),
                detailStates =
                    mapOf(
                        item.mediaType to item.tmdbId to
                            loadedDetail(item, seasons = listOf(1, 2, 3)),
                    ),
            )
        }

        composeRule.onNodeWithText("Request more seasons").assertExists()
        composeRule.onNodeWithText("Partial").assertExists()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete request").assertExists()
    }

    @Test
    fun approvedRequestOutsidePendingFilterUsesLiveSummary() {
        val pending =
            showRequest().copy(
                requestStatus = JellyseerrRequestStatus.PENDING,
                availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
                canRemoveFromService = false,
            )
        val approved =
            pending.copy(
                requestStatus = JellyseerrRequestStatus.APPROVED,
            )
        val item = requestItem(pending)
        val requestsState =
            readyRequests(requests = emptyList(), isAdmin = true).copy(
                filter = JellyseerrRequestFilter.PENDING,
                currentRequestsByMedia =
                    mapOf(pending.mediaType to requireNotNull(pending.tmdbId) to approved),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState =
                    DiscoverUiState(destination = DiscoverDestination.Requests)
                        .reduce(DiscoverAction.SelectExistingRequest(pending)),
                requestsState = requestsState,
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Approved").assertExists()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Approve request").assertDoesNotExist()
    }

    @Test
    fun compactExistingRequestUsesCommandDeckAndFullWidthManagementActions() {
        val request = showRequest()
        val item = requestItem(request)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState(destination = DiscoverDestination.Requests),
                requestsState = readyRequests(requests = listOf(request)),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item, seasons = listOf(1, 2, 3))),
            )
        }

        composeRule.onNodeWithText("Sample Show").performClick()

        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete request").performClick()
        composeRule
            .onNodeWithText("Delete this request? The requested media files are not removed.")
            .assertExists()
    }

    @Test
    fun additionalSeasonRequestUsesSearchableBottomSheet() {
        val request = showRequest()
        val item = requestItem(request)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState(destination = DiscoverDestination.Requests),
                requestsState = readyRequests(requests = listOf(request)),
                detailStates =
                    mapOf(
                        item.mediaType to item.tmdbId to
                            loadedDetail(item, seasons = listOf(1, 2, 3)),
                    ),
            )
        }

        composeRule.onNodeWithText("Sample Show").performClick()
        composeRule.onNodeWithText("Request more seasons").performClick()

        composeRule.onNodeWithText("Search request profiles").assertExists()
        composeRule.onNodeWithText("Search seasons").assertExists()
        composeRule.onNodeWithText("Submit request").assertIsEnabled()
    }

    @Test
    fun pendingSubmitDisablesRequestConfigurationSubmit() {
        val item = dune()
        val state =
            DiscoverUiState()
                .reduce(DiscoverAction.SelectSearchResult(item))
                .reduce(DiscoverAction.OpenRequestConfiguration)
                .reduce(
                    DiscoverAction.OperationStarted(
                        DiscoverPendingOperation.Submit(item.mediaType, item.tmdbId),
                    ),
                )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = state,
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Submit request").assertIsNotEnabled()
    }

    @Test
    fun similarAndRecommendationTitlesPushDetailsAndBackReturnsToParent() {
        val parent = dune()
        val similar = dune().copy(tmdbId = 2, title = "Dune: Part Two")
        val recommended = dune().copy(tmdbId = 3, title = "Arrival")
        composeRule.setContent {
            discoverRequestsHarness(
                detailStates =
                    mapOf(
                        parent.mediaType to parent.tmdbId to
                            loadedDetail(
                                parent,
                                enrichment =
                                    JellyseerrMediaDetailEnrichment(
                                        similar = listOf(similar),
                                        recommendations = listOf(recommended),
                                    ),
                            ),
                        similar.mediaType to similar.tmdbId to loadedDetail(similar),
                        recommended.mediaType to recommended.tmdbId to loadedDetail(recommended),
                    ),
            )
        }

        composeRule.onNodeWithText("Dune").performClick()
        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT)
            .performScrollToNode(hasTestTag(SeerrImmersiveDetailTestTags.SIMILAR))
        composeRule.onNodeWithText("Dune: Part Two").performClick()
        composeRule.onNodeWithText("Dune: Part Two").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Dune").assertExists()

        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT)
            .performScrollToNode(hasTestTag(SeerrImmersiveDetailTestTags.RECOMMENDATIONS))
        composeRule.onNodeWithText("Arrival").performClick()
        composeRule.onNodeWithText("Arrival").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Dune").assertExists()
    }

    @Test
    fun similarRetryLoadingLeavesRecommendationsVisible() {
        val parent = dune()
        val recommended = dune().copy(tmdbId = 3, title = "Arrival")
        val loaded =
            loadedDetail(
                parent,
                enrichment =
                    JellyseerrMediaDetailEnrichment(
                        recommendations = listOf(recommended),
                        failedSections = setOf(JellyseerrDetailEnrichmentSection.SIMILAR),
                    ),
            ).copy(
                enrichmentLoadingSections =
                    setOf(JellyseerrDetailEnrichmentSection.SIMILAR),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState =
                    DiscoverUiState()
                        .reduce(DiscoverAction.SelectSearchResult(parent)),
                detailStates = mapOf(parent.mediaType to parent.tmdbId to loaded),
            )
        }

        composeRule
            .onNodeWithTag(SeerrImmersiveDetailTestTags.ROOT)
            .performScrollToNode(hasTestTag(SeerrImmersiveDetailTestTags.RECOMMENDATIONS))
        composeRule.onNodeWithText("Arrival").assertExists()
    }

    @Test
    fun requestOwnerCanDeleteWithoutAdminPermission() {
        val request =
            showRequest().copy(
                requestedBy =
                    JellyseerrUser(
                        id = 7,
                        displayName = "Owner",
                        username = "owner",
                        permissions = 0,
                    ),
            )
        val item = requestItem(request)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState(destination = DiscoverDestination.Requests),
                requestsState =
                    readyRequests(
                        requests = listOf(request),
                        isAdmin = false,
                        currentUserId = 7,
                    ),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Sample Show").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete request").assertExists()
    }

    @Test
    fun otherUserCannotDeleteRequestWithoutAdminPermission() {
        val request =
            showRequest().copy(
                requestedBy =
                    JellyseerrUser(
                        id = 7,
                        displayName = "Owner",
                        username = "owner",
                        permissions = 0,
                    ),
            )
        val item = requestItem(request)
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState(destination = DiscoverDestination.Requests),
                requestsState =
                    readyRequests(
                        requests = listOf(request),
                        isAdmin = false,
                        currentUserId = 8,
                    ),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Sample Show").performClick()
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Delete request").assertDoesNotExist()
    }

    @Test
    fun availableTitleShowsStatusWithoutFakeAction() {
        val item =
            dune().copy(
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.AVAILABLE,
                        `4k` = null,
                    ),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState().reduce(DiscoverAction.SelectSearchResult(item)),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Available").assertExists()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION).assertDoesNotExist()
    }

    @Test
    fun processingTitleUsesProminentNonInteractiveStatus() {
        val item =
            dune().copy(
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PROCESSING,
                        `4k` = null,
                    ),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState().reduce(DiscoverAction.SelectSearchResult(item)),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Processing").assertExists()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION).assertDoesNotExist()
    }

    @Test
    fun pendingTitleUsesProminentNonInteractiveStatus() {
        val item =
            dune().copy(
                availability =
                    JellyseerrMediaAvailability(
                        standard = JellyseerrMediaStatus.PENDING,
                        `4k` = null,
                    ),
            )
        composeRule.setContent {
            discoverRequestsHarness(
                initialState = DiscoverUiState().reduce(DiscoverAction.SelectSearchResult(item)),
                detailStates = mapOf(item.mediaType to item.tmdbId to loadedDetail(item)),
            )
        }

        composeRule.onNodeWithText("Pending").assertExists()
        composeRule.onNodeWithTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION).assertDoesNotExist()
    }
}

@Composable
private fun discoverRequestsHarness(
    width: androidx.compose.ui.unit.Dp = 411.dp,
    initialState: DiscoverUiState = DiscoverUiState(),
    requestsState: JellyseerrRequestsState.Ready = readyRequests(),
    detailStates: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState> = emptyMap(),
) {
    var state by remember { mutableStateOf(initialState) }

    JellystackTheme(isDarkTheme = false) {
        ProvideResponsiveProfile(Modifier.requiredSize(width = width, height = 891.dp)) {
            val isExpanded = LocalResponsiveProfile.current.isExpanded
            val hasSelection = state.selected != null
            val selectionContent: @Composable (Modifier, Modifier) -> Unit =
                { contentModifier, initialFocusModifier ->
                    DiscoverSelectionContent(
                        state = state,
                        detailStates = detailStates,
                        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
                        requests = requestsState.requests,
                        currentRequestsByMedia = requestsState.currentRequestsByMedia,
                        liveRequestStateAvailable = true,
                        onSelectProfile = { state = state.reduce(DiscoverAction.SelectProfile(it)) },
                        onSelectSeasons = { state = state.reduce(DiscoverAction.SelectSeasonSelection(it)) },
                        onSubmit = { _, _, _ -> },
                        onApprove = {},
                        onDelete = {},
                        onRemoveMedia = {},
                        onConfigureRequest = { state = state.reduce(DiscoverAction.OpenRequestConfiguration) },
                        onCloseRequestConfiguration = {
                            state = state.reduce(DiscoverAction.CloseRequestConfiguration)
                        },
                        onOpenRelatedDetail = { origin, item ->
                            state =
                                state.reduce(
                                    DiscoverAction.OpenRelatedDetail(
                                        item = item,
                                        origin = origin,
                                    ),
                                )
                        },
                        onDetailViewStateChange = { key, viewState ->
                            state =
                                state.reduce(
                                    DiscoverAction.UpdateDetailViewState(
                                        key = key,
                                        viewState = viewState,
                                    ),
                                )
                        },
                        onClose = { state = state.reduce(DiscoverAction.CloseSelection) },
                        isAdmin = requestsState.isAdmin,
                        currentUserId = requestsState.currentUserId,
                        initialFocusModifier = initialFocusModifier,
                        modifier = contentModifier,
                    )
                }
            JellystackShell(
                state =
                    JellystackShellState(
                        primary = PrimaryDestination.Discover,
                        discover = state.destination,
                        paneMode =
                            if (state.destination == DiscoverDestination.Requests) {
                                ShellPaneMode.ListDetail
                            } else {
                                ShellPaneMode.Single
                            },
                    ),
                onAction = {},
                topBar = {
                    Text(
                        if (state.destination == DiscoverDestination.Requests) "Requests" else "Discover",
                    )
                },
                primaryContent = { contentPadding ->
                    DiscoverScreen(
                        state = state,
                        recommendationsState = readyRecommendations(),
                        recommendationDetails = detailStates,
                        requestsState = requestsState,
                        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
                        contentPadding = contentPadding,
                        onAction = { state = state.reduce(it) },
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
                },
                secondaryContent =
                    if (isExpanded && hasSelection) {
                        { contentPadding ->
                            selectionContent(Modifier.fillMaxSize().padding(contentPadding), Modifier)
                        }
                    } else {
                        null
                    },
            )
            if (!isExpanded && hasSelection) {
                ModalFocusScope(
                    onDismissRequest = { state = state.reduce(DiscoverAction.CloseSelection) },
                    returnFocusRequester = null,
                    fullScreen = true,
                ) { initialFocusModifier ->
                    selectionContent(Modifier.fillMaxSize(), initialFocusModifier)
                }
            }
        }
    }
}

private fun readyRecommendations(): JellyseerrRecommendationsState =
    JellyseerrRecommendationsState.Ready(
        rails =
            mapOf(
                JellyseerrRecommendationRail.TRENDS to
                    JellyseerrRecommendationRailState(
                        rail = JellyseerrRecommendationRail.TRENDS,
                        items = listOf(dune()),
                        isLoading = false,
                        errorMessage = null,
                        canLoadMore = false,
                        nextPage = 2,
                        lastUpdated = null,
                        isStale = false,
                    ),
            ),
    )

private fun readyRequests(
    searchResults: List<JellyseerrSearchItem> = emptyList(),
    requests: List<JellyseerrRequestSummary> = emptyList(),
    isAdmin: Boolean = true,
    currentUserId: Int? = 1,
): JellyseerrRequestsState.Ready =
    JellyseerrRequestsState.Ready(
        filter = JellyseerrRequestFilter.ALL,
        requests = requests,
        counts = null,
        query = if (searchResults.isEmpty()) "" else "Dune",
        searchResults = searchResults,
        isSearching = false,
        isRefreshing = false,
        isPerformingAction = false,
        pendingApprovals = emptySet(),
        message = null,
        isAdmin = isAdmin,
        lastUpdated = null,
        languageProfiles = JellyseerrLanguageProfiles.EMPTY,
        currentRequestsByMedia =
            requests.associateBy { summary ->
                summary.mediaType to requireNotNull(summary.tmdbId)
            },
        currentUserId = currentUserId,
    )

private fun dune(): JellyseerrSearchItem =
    JellyseerrSearchItem(
        tmdbId = 438631,
        mediaType = JellyseerrMediaType.MOVIE,
        title = "Dune",
        overview = "A mythic journey across Arrakis.",
        releaseYear = "2021",
        posterPath = null,
        backdropPath = null,
        mediaInfoId = null,
        tvdbId = null,
        availability = JellyseerrMediaAvailability(standard = null, `4k` = null),
        requests = emptyList(),
    )

private fun showRequest() =
    JellyseerrRequestSummary(
        id = 72,
        mediaId = 91,
        tmdbId = 9001,
        tvdbId = 8001,
        title = "Sample Show",
        originalTitle = null,
        mediaType = JellyseerrMediaType.TV,
        requestStatus = JellyseerrRequestStatus.COMPLETED,
        availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.PARTIALLY_AVAILABLE,
                `4k` = null,
            ),
        is4k = false,
        canRemoveFromService = true,
        createdAt = null,
        updatedAt = null,
        requestedBy = null,
        profileName = null,
        seasons = listOf(JellyseerrSeasonStatus(1, JellyseerrRequestStatus.COMPLETED)),
        posterPath = null,
        backdropPath = null,
    )

private fun requestItem(request: JellyseerrRequestSummary) =
    JellyseerrSearchItem(
        tmdbId = requireNotNull(request.tmdbId),
        mediaType = request.mediaType,
        title = requireNotNull(request.title),
        overview = null,
        releaseYear = null,
        posterPath = request.posterPath,
        backdropPath = request.backdropPath,
        mediaInfoId = request.mediaId,
        tvdbId = request.tvdbId,
        availability = request.availability,
        requests = listOf(request),
    )

private fun loadedDetail(
    item: JellyseerrSearchItem,
    seasons: List<Int> = emptyList(),
    ratings: JellyseerrMediaRatings? = null,
    enrichment: JellyseerrMediaDetailEnrichment = JellyseerrMediaDetailEnrichment(ratings = ratings),
    jellyseerrUrl: String? = "https://seerr.test/title/${item.tmdbId}",
) = JellyseerrMediaDetailState.Loaded(
    JellyseerrMediaDetail(
        tmdbId = item.tmdbId,
        mediaType = item.mediaType,
        title = item.title,
        year = item.releaseYear,
        overview = if (item.mediaType == JellyseerrMediaType.TV) "Detailed show overview." else item.overview,
        runtimeMinutes = 120,
        genres = listOf("Drama"),
        releaseDate = null,
        revenue = null,
        originalLanguage = "en",
        productionCountries = emptyList(),
        studios = emptyList(),
        ratings = ratings,
        trailer = null,
        posterPath = item.posterPath,
        backdropPath = item.backdropPath,
        jellyseerrUrl = jellyseerrUrl,
        jellyfinUrl = null,
        imdbId = null,
        tvdbId = item.tvdbId,
        availableSeasons = seasons,
        enrichment = enrichment,
    ),
)
