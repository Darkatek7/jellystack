package dev.jellystack.design

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.downloads.OfflineMediaKind
import dev.jellystack.core.downloads.OfflineMediaMetadata
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyfin.MediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.design.jellyfin.CompactTrackPicker
import dev.jellystack.design.jellyfin.DetailActionTestTags
import dev.jellystack.design.jellyfin.HomeSpotlight
import dev.jellystack.design.jellyfin.ImmersiveDetailTestTags
import dev.jellystack.design.jellyfin.ImmersiveMediaDetailContent
import dev.jellystack.design.jellyfin.InfoSection
import dev.jellystack.design.jellyfin.JellyfinBrowseScreen
import dev.jellystack.design.jellyfin.JellyfinDetailContent
import dev.jellystack.design.jellyfin.LibraryCardTestTags
import dev.jellystack.design.jellyfin.LibraryNavigationState
import dev.jellystack.design.jellyfin.SeasonEpisodes
import dev.jellystack.design.jellyfin.SpotlightCandidate
import dev.jellystack.design.jellyfin.SpotlightTestTags
import dev.jellystack.design.jellyfin.TrackPickerOption
import dev.jellystack.design.jellyfin.TrackPickerTestTags
import dev.jellystack.design.jellyseerr.JellyseerrRecommendationsScreen
import dev.jellystack.design.layout.ProvideResponsiveProfile
import dev.jellystack.design.navigation.LibraryDestination
import dev.jellystack.design.navigation.LibrarySection
import dev.jellystack.design.navigation.PrimaryDestination
import dev.jellystack.design.navigation.ShellModal
import dev.jellystack.design.navigation.ShellModalOwner
import dev.jellystack.design.navigation.dismissActiveShellModal
import dev.jellystack.design.preview.JellystackPreviewFixture
import dev.jellystack.design.shell.JellystackShell
import dev.jellystack.design.shell.JellystackShellState
import dev.jellystack.design.shell.ShellPaneMode
import dev.jellystack.design.theme.JellystackTheme
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleFormat
import dev.jellystack.players.SubtitleTrack
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class LibraryAndMediaUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<JellystackTestActivity>()
    private var usedHardwareInput = false

    @After
    fun resetTouchModeAfterHardwareInput() {
        if (!usedHardwareInput) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            "android.permission.MODIFY_TOUCH_MODE_STATE",
        )
        try {
            instrumentation.resetInTouchMode()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun rootNavigationUsesDiscoverLabel() {
        composeRule.setContent {
            JellystackPreviewFixture(fixtureName = "home")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discover").assertExists()
        composeRule.onNodeWithText("Media").assertMissing()
    }

    @Test
    fun libraryRootOmitsCategoryChipsAndKeepsRailActions() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-bonus",
                            name = "Bonus Library",
                            collectionType = "music",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
                libraryItems = emptyList(),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = true,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Movies").assertCountEquals(1)
        composeRule.onAllNodesWithText("Shows").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Open Movies").assertExists()
        composeRule.onNodeWithContentDescription("Open Shows").assertExists()
        composeRule.onNodeWithText("Bonus Library").assertMissing()
    }

    @Test
    fun typedLibraryDestinationDirectlySelectsTheRenderedSubview() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = JellyfinHomeState(),
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    libraryNavigationState =
                        LibraryNavigationState(
                            destination = LibraryDestination.Section(LibrarySection.Downloads),
                        ),
                )
            }
        }

        composeRule.onNodeWithText("No downloads yet").assertExists()
    }

    @Test
    fun librarySubviewDoesNotRenderASecondNavigationHeader() {
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = JellyfinHomeState(),
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    libraryNavigationState =
                        LibraryNavigationState(
                            destination = LibraryDestination.Section(LibrarySection.Favorites),
                        ),
                )
            }
        }

        composeRule.onAllNodesWithText("Favorites").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Back to library").assertDoesNotExist()
        composeRule.onNodeWithText("No favorites yet").assertExists()
    }

    @Test
    fun libraryNoMatchNamesQueryAndClearRestoresResults() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = 1,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
                libraryItems = listOf(movieItem("movie-dune", "Dune", "2026-06-28T12:00:00Z")),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    libraryNavigationState =
                        LibraryNavigationState(
                            destination = LibraryDestination.Section(LibrarySection.Movies),
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Search library").performTextInput("Alien")
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No results for \"Alien\"").assertExists()
        composeRule.onNodeWithText("Clear search").performClick()
        composeRule.onNodeWithText("Dune").assertExists()
    }

    @Test
    fun nestedLibraryNavigationOpensChildrenWithAnIndependentQuery() {
        val rootState =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-music",
                            name = "Music",
                            collectionType = "music",
                            itemCount = 2,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-music",
                libraryItems =
                    listOf(
                        movieItem("folder-albums", "Albums", "2026-06-28T12:00:00Z").copy(
                            type = "Folder",
                            mediaType = null,
                        ),
                        movieItem("parent-album", "Parent album", "2026-06-27T12:00:00Z"),
                    ),
            )
        val childState =
            rootState.copy(
                libraryItems = listOf(movieItem("child-album", "Child album", "2026-06-26T12:00:00Z")),
                totalLibraryItemCount = 1,
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                var navigationState by remember { mutableStateOf(LibraryNavigationState()) }
                var browseState by remember { mutableStateOf(rootState) }
                JellyfinBrowseScreen(
                    state = browseState,
                    onSelectLibrary = { browseState = rootState },
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onOpenContainer = { item ->
                        browseState = childState
                        navigationState =
                            navigationState.push(LibraryDestination.Children(item.id, item.name))
                    },
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    libraryNavigationState = navigationState,
                    onLibraryNavigationChange = { navigationState = it },
                )
            }
        }

        composeRule.onNode(hasText("Libraries") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Music") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Search library").performTextInput("Album")
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNode(hasText("Albums") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Child album").assertExists()
    }

    @Test
    fun expandedLibraryKeepsListBesideDetail() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = 1,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
                libraryItems = listOf(movieItem("movie-dune", "Dune", "2026-06-28T12:00:00Z")),
            )
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
                    var detailOpen by remember { mutableStateOf(false) }
                    JellystackShell(
                        state =
                            JellystackShellState(
                                primary = PrimaryDestination.Library,
                                paneMode = ShellPaneMode.ListDetail,
                            ),
                        onAction = {},
                        primaryContent = { padding ->
                            JellyfinBrowseScreen(
                                state = state,
                                onSelectLibrary = {},
                                onRefresh = {},
                                onLoadMore = {},
                                onOpenDetail = { detailOpen = true },
                                onConnectServer = {},
                                selectedSpotlightId = null,
                                onSelectedSpotlightIdChange = {},
                                contentPadding = padding,
                                libraryNavigationState =
                                    LibraryNavigationState(
                                        destination =
                                            LibraryDestination.Section(LibrarySection.Movies),
                                    ),
                            )
                        },
                        secondaryContent =
                            if (detailOpen) {
                                { padding ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                        Text("Play", modifier = Modifier)
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }
            }
        }

        composeRule.onNode(hasText("Dune") and hasClickAction()).performClick()
        composeRule
            .onNode(hasTestTag(ShellTestTags.PRIMARY_PANE) and hasAnyDescendant(hasText("Dune")))
            .assertExists()
        composeRule
            .onNode(hasTestTag(ShellTestTags.SECONDARY_PANE) and hasAnyDescendant(hasText("Play")))
            .assertExists()
    }

    @Test
    fun libraryTabSuppressesFalseEmptyStateWhileResolvingVisibleSelection() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-bonus",
                            name = "Bonus Library",
                            collectionType = "music",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-bonus",
                libraryItems = emptyList(),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = true,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Libraries").assertExists()
        composeRule.onNodeWithText("No shows available in this library yet").assertMissing()
    }

    @Test
    fun spotlightAdvancesEverySixSecondsAndWraps() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent { manualSpotlightHarness() }

            composeRule.onNodeWithText("First movie").assertExists()
            composeRule.mainClock.advanceTimeBy(5_999L)
            composeRule.onNodeWithText("First movie").assertExists()
            composeRule.mainClock.advanceTimeBy(500L)
            composeRule.onNodeWithText("Second movie").assertExists()
            composeRule.mainClock.advanceTimeBy(6_500L)
            composeRule.onNodeWithText("First movie").assertExists()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun configuredHomeSectionsKeepSpotlightAtTop() {
        composeRule.setContent {
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            JellystackTheme(isDarkTheme = false) {
                HomeContent(
                    hasServers = true,
                    browseState = spotlightHomeState(),
                    homeSectionsState =
                        HomeSectionsState.Ready(
                            sections = emptyList(),
                            imageBaseUrl = "https://example.com",
                            imageAccessToken = "token",
                        ),
                    selectedSpotlightId = selectedId,
                    onSelectedSpotlightIdChange = { selectedId = it },
                    onSelectLibrary = {},
                    onRefreshLibraries = {},
                    onLoadMore = {},
                    onOpenItemDetail = {},
                    onPlayItem = {},
                    onConnectJellyfin = {},
                    onConnectJellyseerr = {},
                    learnMoreUrl = "https://example.com",
                    downloadStatuses = emptyMap(),
                )
            }
        }

        composeRule.onNodeWithTag(SpotlightTestTags.PAGER).assertIsDisplayed()
    }

    @Test
    fun spotlightUsesClearLogoInsteadOfTitleTextWhenAvailable() {
        val state =
            spotlightHomeState().copy(
                recentMovies =
                    spotlightHomeState().recentMovies.map { item ->
                        item.copy(logoImageTag = "clear-logo")
                    },
            )
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = false,
                    spotlightLogoPainter = ColorPainter(Color.White),
                )
            }
        }

        composeRule
            .onNodeWithTag(SpotlightTestTags.TITLE_LOGO, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNode(
                hasText("First movie") and
                    hasAnyAncestor(hasTestTag(SpotlightTestTags.HERO)),
                useUnmergedTree = true,
            ).assertDoesNotExist()
    }

    @Test
    fun spotlightManualSwipeRestartsSixSecondInterval() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent { manualSpotlightHarness() }

            composeRule.mainClock.advanceTimeBy(3_000L)
            composeRule.onNodeWithTag(SpotlightTestTags.PAGER).performTouchInput { swipeLeft() }
            composeRule.mainClock.advanceTimeBy(300L)
            composeRule.onNodeWithText("Second movie").assertExists()

            composeRule.mainClock.advanceTimeBy(3_500L)
            composeRule.onNodeWithText("Second movie").assertExists()
            composeRule.mainClock.advanceTimeBy(3_000L)
            composeRule.onNodeWithText("First movie").assertExists()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun spotlightAutoCycleProgressTracksSixSecondInterval() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent { manualSpotlightHarness() }

            composeRule.mainClock.advanceTimeBy(3_000L)

            val progress = spotlightAutoCycleProgress()
            assertTrue("Expected progress near halfway, got $progress", progress in 0.35f..0.65f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun spotlightManualSelectionRestartsAutoCycleProgress() {
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.setContent { manualSpotlightHarness() }

            composeRule.mainClock.advanceTimeBy(3_000L)
            composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
            composeRule.mainClock.advanceTimeBy(500L)

            val progress = spotlightAutoCycleProgress()
            assertTrue("Expected restarted progress near zero, got $progress", progress < 0.15f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun rootOwnedSpotlightSelectionSurvivesPrimaryNavigationRoundTrip() {
        composeRule.setContent { rootOwnedSpotlightHarness() }

        composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
        composeRule.onNodeWithTag(SpotlightTestTags.HERO).assertTextContains("Second movie")
        composeRule.onNodeWithTag("test_leave_home").performClick()
        composeRule.onNodeWithText("Library surface").assertExists()
        composeRule.onNodeWithTag("test_return_home").performClick()

        composeRule.onNodeWithTag(SpotlightTestTags.HERO).assertTextContains("Second movie")
        composeRule
            .onNodeWithContentDescription("Spotlight item 2 of 2")
            .assertIsSelected()
    }

    private fun spotlightAutoCycleProgress(): Float =
        composeRule
            .onNodeWithTag("spotlight_auto_cycle_progress")
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    @Test
    fun spotlightIndicatorsAreTappable48DpAndExposeSelection() {
        composeRule.setContent { manualSpotlightHarness() }

        composeRule
            .onNodeWithContentDescription("Spotlight item 1 of 2")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .assertIsSelected()
        composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()

        composeRule.onNodeWithText("Second movie").assertExists()
        composeRule
            .onNodeWithContentDescription("Spotlight item 2 of 2")
            .assertIsSelected()
    }

    @Test
    fun spotlightPublishesInitialFallbackExactlyOnce() {
        val selections = mutableListOf<String?>()
        composeRule.setContent {
            var selectedId by remember { mutableStateOf<String?>(null) }

            JellystackTheme(isDarkTheme = false) {
                HomeSpotlight(
                    candidates = spotlightCandidates(),
                    selectedId = selectedId,
                    onSelected = { id ->
                        selections += id
                        selectedId = id
                    },
                ) { candidate, _, _ ->
                    Text(candidate.displayItem.name)
                }
            }
        }

        composeRule.runOnIdle {
            check(selections == listOf("movie-1")) {
                "Expected one initial fallback publication, got $selections"
            }
        }
    }

    @Test
    fun spotlightDoesNotRepublishAlreadySelectedId() {
        val selections = mutableListOf<String?>()
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                HomeSpotlight(
                    candidates = spotlightCandidates(),
                    selectedId = "movie-1",
                    onSelected = selections::add,
                ) { candidate, _, _ ->
                    Text(candidate.displayItem.name)
                }
            }
        }

        composeRule.runOnIdle {
            check(selections.isEmpty()) {
                "Expected no publication for an already selected ID, got $selections"
            }
        }
    }

    @Test
    fun spotlightRetargetsInFlightIndicatorByStableIdAfterReorder() {
        composeRule.mainClock.autoAdvance = false
        try {
            val selections = mutableListOf<String?>()
            var reorderCandidates: (() -> Unit)? = null
            composeRule.setContent {
                var candidates by remember { mutableStateOf(spotlightCandidates()) }
                var selectedId by remember { mutableStateOf<String?>("movie-1") }
                reorderCandidates = { candidates = candidates.reversed() }

                JellystackTheme(isDarkTheme = false) {
                    HomeSpotlight(
                        candidates = candidates,
                        selectedId = selectedId,
                        onSelected = { id ->
                            selections += id
                            selectedId = id
                        },
                    ) { candidate, _, _ ->
                        Text(candidate.displayItem.name)
                    }
                }
            }
            composeRule.runOnIdle { selections.clear() }

            composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
            composeRule.mainClock.advanceTimeBy(32L)
            composeRule.runOnIdle { checkNotNull(reorderCandidates).invoke() }
            composeRule.mainClock.advanceTimeBy(1_000L)

            composeRule.onNodeWithText("Second movie").assertExists()
            composeRule
                .onNodeWithContentDescription("Spotlight item 1 of 2")
                .assertIsSelected()
            composeRule.runOnIdle {
                check(selections == listOf("movie-2")) {
                    "Expected one stable-ID publication after reorder, got $selections"
                }
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun spotlightFallsBackOnceWhenInFlightTargetIsRemoved() {
        composeRule.mainClock.autoAdvance = false
        try {
            val selections = mutableListOf<String?>()
            var removeTarget: (() -> Unit)? = null
            var restoreCandidates: (() -> Unit)? = null
            composeRule.setContent {
                var candidates by remember { mutableStateOf(spotlightCandidates()) }
                var selectedId by remember { mutableStateOf<String?>("movie-1") }
                removeTarget = {
                    candidates = candidates.filterNot { it.displayItem.id == "movie-2" }
                }
                restoreCandidates = { candidates = spotlightCandidates() }

                JellystackTheme(isDarkTheme = false) {
                    HomeSpotlight(
                        candidates = candidates,
                        selectedId = selectedId,
                        onSelected = { id ->
                            selections += id
                            selectedId = id
                        },
                    ) { candidate, _, _ ->
                        Text(candidate.displayItem.name)
                    }
                }
            }
            composeRule.runOnIdle { selections.clear() }

            composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
            composeRule.mainClock.advanceTimeBy(32L)
            composeRule.runOnIdle { checkNotNull(removeTarget).invoke() }
            composeRule.mainClock.advanceTimeBy(1_000L)
            composeRule.runOnIdle { checkNotNull(restoreCandidates).invoke() }
            composeRule.mainClock.advanceTimeBy(1_000L)

            composeRule.onNodeWithText("First movie").assertExists()
            composeRule
                .onNodeWithContentDescription("Spotlight item 1 of 2")
                .assertIsSelected()
            composeRule.runOnIdle {
                check(selections == listOf("movie-2", "movie-1")) {
                    "Expected target then one fallback without resurrection, got $selections"
                }
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun productionSpotlightManualSelectionKeepsActionsAndFirstRailVisible() {
        val playedIds = mutableListOf<String>()
        val openedIds = mutableListOf<String>()
        val state =
            spotlightHomeState().copy(
                continueWatching =
                    listOf(
                        movieItem(
                            id = "continue-movie",
                            name = "Continue movie",
                            dateCreated = (Clock.System.now() - 3.days).toString(),
                        ),
                    ),
            )

        composeRule.setContent {
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = { openedIds += it.id },
                    onPlayItem = { playedIds += it.id },
                    onConnectServer = {},
                    selectedSpotlightId = selectedId,
                    onSelectedSpotlightIdChange = { selectedId = it },
                    showLibraryItems = false,
                )
            }
        }

        composeRule.onNodeWithText("Continue watching").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
        composeRule.onNodeWithTag(SpotlightTestTags.HERO).assertTextContains("Second movie")
        composeRule.onNodeWithTag(SpotlightTestTags.PLAY).performClick()
        composeRule.onNodeWithTag(SpotlightTestTags.DETAILS).performClick()

        composeRule.runOnIdle {
            check(playedIds == listOf("movie-2")) {
                "Expected the manually selected movie to play, got $playedIds"
            }
            check(openedIds == listOf("movie-2")) {
                "Expected the manually selected movie details, got $openedIds"
            }
        }
    }

    @Test
    fun spotlightReturnsToTopAfterRefreshTemporarilyRemovesCandidates() {
        val loadedState =
            spotlightHomeState().copy(
                continueWatching =
                    listOf(
                        movieItem(
                            id = "continue-movie",
                            name = "Continue movie",
                            dateCreated = (Clock.System.now() - 3.days).toString(),
                        ),
                    ),
            )
        var beginRefresh: (() -> Unit)? = null
        var finishRefresh: (() -> Unit)? = null

        composeRule.setContent {
            var state by remember { mutableStateOf(loadedState) }
            beginRefresh = {
                state =
                    state.copy(
                        isInitialLoading = true,
                        libraryItems = emptyList(),
                        recentShows = emptyList(),
                        recentMovies = emptyList(),
                    )
            }
            finishRefresh = {
                state = loadedState.copy(isInitialLoading = false)
            }

            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = false,
                    spotlightAutoAdvanceEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag(SpotlightTestTags.HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(SpotlightTestTags.HOME_LIST).performScrollToIndex(3)
        composeRule.onNodeWithText("Recently added movies").assertIsDisplayed()
        composeRule.runOnIdle { checkNotNull(beginRefresh).invoke() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { checkNotNull(finishRefresh).invoke() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SpotlightTestTags.HERO).assertIsDisplayed()
        val listTop =
            composeRule
                .onNodeWithTag(SpotlightTestTags.HOME_LIST)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val heroTop =
            composeRule
                .onNodeWithTag(SpotlightTestTags.HERO)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val tolerance = 32f * composeRule.activity.resources.displayMetrics.density
        assertTrue(
            "Expected refreshed Home to return to the Spotlight, listTop=$listTop heroTop=$heroTop",
            heroTop <= listTop + tolerance,
        )
    }

    @Test
    fun spotlightPageChangeDoesNotStealIndicatorFocus() {
        usedHardwareInput = true
        composeRule.setContent { manualSpotlightHarness() }
        val firstIndicator =
            composeRule.onNodeWithContentDescription("Spotlight item 1 of 2")

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.window.decorView
                .hasWindowFocus()
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        composeRule.waitForIdle()
        firstIndicator.assertIsFocused()
        composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()

        composeRule
            .onNodeWithContentDescription("Spotlight item 2 of 2")
            .assertIsSelected()
        firstIndicator.assertIsFocused()
    }

    @Test
    fun spotlightSelectionSurvivesCandidateReordering() {
        var reorderCandidates: (() -> Unit)? = null
        composeRule.setContent {
            var candidates by remember { mutableStateOf(spotlightCandidates()) }
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            reorderCandidates = { candidates = candidates.reversed() }

            JellystackTheme(isDarkTheme = false) {
                HomeSpotlight(
                    candidates = candidates,
                    selectedId = selectedId,
                    onSelected = { selectedId = it },
                ) { candidate, _, _ ->
                    Text(candidate.displayItem.name)
                }
            }
        }

        composeRule.onNodeWithContentDescription("Spotlight item 2 of 2").performClick()
        composeRule.onNodeWithText("Second movie").assertExists()
        composeRule.runOnIdle { checkNotNull(reorderCandidates).invoke() }

        composeRule.onNodeWithText("Second movie").assertExists()
        composeRule
            .onNodeWithContentDescription("Spotlight item 1 of 2")
            .assertIsSelected()
    }

    @Test
    fun spotlightSelectionPublishesFallbackWhenCurrentCandidateDisappears() {
        var removeSelectedCandidate: (() -> Unit)? = null
        var restoreCandidates: (() -> Unit)? = null
        var observedSelectedId: String? = null
        composeRule.setContent {
            var candidates by remember { mutableStateOf(spotlightCandidates()) }
            var selectedId by rememberSaveable { mutableStateOf<String?>("movie-1") }
            removeSelectedCandidate = {
                candidates = candidates.filterNot { candidate -> candidate.displayItem.id == "movie-1" }
            }
            restoreCandidates = { candidates = spotlightCandidates() }
            observedSelectedId = selectedId

            JellystackTheme(isDarkTheme = false) {
                HomeSpotlight(
                    candidates = candidates,
                    selectedId = selectedId,
                    onSelected = { selectedId = it },
                ) { candidate, _, _ ->
                    Text(candidate.displayItem.name)
                }
            }
        }

        composeRule.runOnIdle { check(observedSelectedId == "movie-1") }
        composeRule.runOnIdle { checkNotNull(removeSelectedCandidate).invoke() }

        composeRule.onNodeWithText("Second movie").assertExists()
        composeRule.runOnIdle { check(observedSelectedId == "movie-2") }
        composeRule.runOnIdle { checkNotNull(restoreCandidates).invoke() }

        composeRule.onNodeWithText("Second movie").assertExists()
        composeRule.runOnIdle { check(observedSelectedId == "movie-2") }
        composeRule
            .onNodeWithContentDescription("Spotlight item 2 of 2")
            .assertIsSelected()
    }

    @Test
    fun spotlightSelectionClearsAcrossEmptyCandidatesAndDoesNotResurrect() {
        var clearCandidates: (() -> Unit)? = null
        var restoreCandidates: (() -> Unit)? = null
        var observedSelectedId: String? = null
        composeRule.setContent {
            var candidates by remember { mutableStateOf(spotlightCandidates()) }
            var selectedId by rememberSaveable { mutableStateOf<String?>("movie-2") }
            clearCandidates = { candidates = emptyList() }
            restoreCandidates = { candidates = spotlightCandidates() }
            observedSelectedId = selectedId

            JellystackTheme(isDarkTheme = false) {
                HomeSpotlight(
                    candidates = candidates,
                    selectedId = selectedId,
                    onSelected = { selectedId = it },
                ) { candidate, _, _ ->
                    Text(candidate.displayItem.name)
                }
            }
        }

        composeRule.runOnIdle { check(observedSelectedId == "movie-2") }
        composeRule.runOnIdle { checkNotNull(clearCandidates).invoke() }
        composeRule.runOnIdle { check(observedSelectedId == null) }
        composeRule.runOnIdle { checkNotNull(restoreCandidates).invoke() }

        composeRule.onNodeWithText("First movie").assertExists()
        composeRule.runOnIdle { check(observedSelectedId == "movie-1") }
    }

    @Test
    fun spotlightEpisodeActionUsesBackingEpisodeSemantics() {
        val episode =
            movieItem(
                id = "episode-2",
                name = "The second episode",
                dateCreated = (Clock.System.now() - 1.days).toString(),
            ).copy(
                libraryId = "lib-shows",
                type = "Episode",
                parentId = "season-1",
                seriesId = "series-1",
                seriesName = "Sample Show",
                seasonId = "season-1",
                parentIndexNumber = 1,
                indexNumber = 2,
                positionTicks = 1L,
            )
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-shows",
                recentShows = listOf(episode),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onPlayItem = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = false,
                )
            }
        }

        composeRule.onNodeWithText("Continue · S1 · E2").assertExists()
    }

    @Test
    fun spotlightActionsRemainInsideHeroAtTwoHundredPercentFontScale() {
        val longTitle =
            "A very long localized spotlight title that wraps across several lines without hiding its actions"
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = null,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
                recentMovies =
                    listOf(
                        movieItem(
                            id = "movie-long-title",
                            name = longTitle,
                            dateCreated = (Clock.System.now() - 1.days).toString(),
                        ),
                    ),
            )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 2f),
            ) {
                JellystackTheme(isDarkTheme = false) {
                    JellyfinBrowseScreen(
                        state = state,
                        onSelectLibrary = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenDetail = {},
                        onPlayItem = {},
                        onConnectServer = {},
                        selectedSpotlightId = null,
                        onSelectedSpotlightIdChange = {},
                        showLibraryItems = false,
                    )
                }
            }
        }

        val heroBounds = composeRule.onNodeWithTag(SpotlightTestTags.HERO).fetchSemanticsNode().boundsInRoot
        val titleBounds =
            composeRule
                .onAllNodesWithText(longTitle, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .minBy { node -> node.boundsInRoot.top }
                .boundsInRoot
        val playBounds = composeRule.onNodeWithTag(SpotlightTestTags.PLAY).fetchSemanticsNode().boundsInRoot
        val detailsBounds = composeRule.onNodeWithTag(SpotlightTestTags.DETAILS).fetchSemanticsNode().boundsInRoot
        assertTrue("Title is clipped below $heroBounds: $titleBounds", titleBounds.bottom <= heroBounds.bottom)
        assertTrue("Play action is clipped outside $heroBounds: $playBounds", playBounds.bottom <= heroBounds.bottom)
        assertTrue("Details action is clipped outside $heroBounds: $detailsBounds", detailsBounds.bottom <= heroBounds.bottom)
        assertTrue("Title starts above $heroBounds: $titleBounds", titleBounds.top >= heroBounds.top)
        assertTrue("Play action starts above $heroBounds: $playBounds", playBounds.top >= heroBounds.top)
        assertTrue("Details action starts above $heroBounds: $detailsBounds", detailsBounds.top >= heroBounds.top)
    }

    @Test
    fun libraryQuickActionsNavigateToSubviewStates() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = 4,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = 8,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                var navigationState by remember { mutableStateOf(LibraryNavigationState()) }
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = true,
                    onNavigateUp = { navigationState = navigationState.pop() },
                    libraryNavigationState = navigationState,
                    onLibraryNavigationChange = { navigationState = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Downloads") and hasClickAction()).assertExists()
        composeRule.onNode(hasText("Favorites") and hasClickAction()).assertExists()
        composeRule.onNode(hasText("Libraries") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Shows") and hasClickAction()).assertExists()
    }

    @Test
    fun favoritesSubviewShowsPostersWhenFavoritesExist() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = 4,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = 8,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-movies",
                libraryItems =
                    listOf(
                        movieItem("fav-movie-1", "Favorite Alpha", "2026-06-28T12:00:00Z"),
                        movieItem("fav-movie-2", "Favorite Bravo", "2026-06-27T12:00:00Z"),
                        movieItem("fav-movie-3", "Favorite Gamma", "2026-06-26T12:00:00Z"),
                    ),
                favorites = setOf("fav-movie-1", "fav-movie-2", "fav-movie-3"),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                var navigationState by remember { mutableStateOf(LibraryNavigationState()) }
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    onSelectFavorites = {},
                    showLibraryItems = true,
                    onNavigateUp = { navigationState = navigationState.pop() },
                    libraryNavigationState = navigationState,
                    onLibraryNavigationChange = { navigationState = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Favorites") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        // LazyVerticalGrid only composes visible items, so scroll the grid itself to each poster
        // before asserting that the card exists. Favorites are now the first grid items because
        // subview navigation is rendered exclusively by the app shell.
        val titles = listOf("Favorite Alpha", "Favorite Bravo", "Favorite Gamma")
        titles.forEachIndexed { index, title ->
            composeRule.onNodeWithTag(LibraryCardTestTags.GRID).performScrollToIndex(index)
            composeRule.waitForIdle()
            composeRule.onNode(hasText(title) and hasClickAction()).assertExists()
        }
    }

    @Test
    fun libraryMovieRailOpensFullMovieViewAndSelectsLibrary() {
        val selectedLibraries = mutableListOf<String>()
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-movies",
                            name = "Movies",
                            collectionType = "movies",
                            itemCount = 4,
                            primaryImageTag = null,
                        ),
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = 8,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-shows",
                recentMovies = listOf(movieItem("movie-new", "New Movie", "2026-06-28T12:00:00Z")),
                totalLibraryItemCount = 142L,
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                var navigationState by remember { mutableStateOf(LibraryNavigationState()) }
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = { selectedLibraries += it },
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = true,
                    onNavigateUp = { navigationState = navigationState.pop() },
                    libraryNavigationState = navigationState,
                    onLibraryNavigationChange = { navigationState = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open Movies").performClick()
        composeRule.onNodeWithText("Search library").assertExists()
        composeRule.onNodeWithText("Search library").performTextInput("a")
        assertTrue("Expected movie library selection, got $selectedLibraries", "lib-movies" in selectedLibraries)
    }

    @Test
    fun downloadsSubviewRendersMetadataAndLegacyRows() {
        val state =
            JellyfinHomeState(
                libraries =
                    listOf(
                        JellyfinLibrary(
                            id = "lib-shows",
                            name = "Shows",
                            collectionType = "tvshows",
                            itemCount = 8,
                            primaryImageTag = null,
                        ),
                    ),
                selectedLibraryId = "lib-shows",
            )
        val offlineMedia =
            listOf(
                OfflineMedia(
                    mediaId = "episode-1",
                    filePath = "/offline/episode-1.mp4",
                    mimeType = "video/mp4",
                    checksumSha256 = null,
                    sizeBytes = 1024,
                    kind = OfflineMediaKind.VIDEO,
                    metadata =
                        OfflineMediaMetadata(
                            itemId = "episode-1",
                            libraryId = "lib-shows",
                            name = "Pilot",
                            type = "Episode",
                            mediaType = "Video",
                            seriesName = "Sample Show",
                            indexNumber = 1,
                            parentIndexNumber = 1,
                        ),
                ),
                OfflineMedia(
                    mediaId = "legacy-video",
                    filePath = "/offline/legacy.mp4",
                    mimeType = "video/mp4",
                    checksumSha256 = null,
                    sizeBytes = 2048,
                    kind = OfflineMediaKind.VIDEO,
                ),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                var navigationState by remember { mutableStateOf(LibraryNavigationState()) }
                JellyfinBrowseScreen(
                    state = state,
                    onSelectLibrary = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onOpenDetail = {},
                    onConnectServer = {},
                    selectedSpotlightId = null,
                    onSelectedSpotlightIdChange = {},
                    showLibraryItems = true,
                    offlineMedia = offlineMedia,
                    onNavigateUp = { navigationState = navigationState.pop() },
                    libraryNavigationState = navigationState,
                    onLibraryNavigationChange = { navigationState = it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Downloads") and hasClickAction()).performClick()
        composeRule.onNodeWithText("S1 · E1").assertExists()
        composeRule.onNodeWithText("legacy-video").assertExists()
        composeRule.onNodeWithText("Downloaded item").assertExists()
    }

    @Test
    fun compactTrackPickerOpensBoundedSelectionDialog() {
        val selected = mutableListOf<String>()

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                CompactTrackPicker(
                    label = "Audio",
                    selectedSummary = "German 5.1",
                    options =
                        listOf(
                            TrackPickerOption(
                                value = "deu",
                                fullLabel = "Surround - German - Dolby Digital - 5.1 - Default",
                                selected = true,
                            ),
                            TrackPickerOption(
                                value = "eng",
                                fullLabel = "English Stereo",
                                selected = false,
                            ),
                        ),
                    onSelect = { selected += it },
                    modifier = Modifier.testTag(TrackPickerTestTags.AUDIO),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(TrackPickerTestTags.AUDIO)
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains("Audio · German 5.1")
            .performClick()
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertExists()
        val firstOption =
            composeRule.onNode(
                hasText("Surround - German - Dolby Digital - 5.1 - Default") and hasClickAction(),
            )
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { firstOption.assertIsFocused() }.isSuccess
        }
        composeRule.onNodeWithText("English Stereo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertMissing()
        composeRule.onNodeWithTag(TrackPickerTestTags.AUDIO).assertIsFocused()
        assertTrue("Expected English selection, got $selected", selected == listOf("eng"))
    }

    @Test
    fun compactTrackPickerUsesOneActivatingFocusTargetPerControl() {
        usedHardwareInput = true
        val selected = mutableListOf<String>()
        val pickerLabel = "Audio · German 5.1"
        val firstOptionLabel = "Surround - German - Dolby Digital - 5.1 - Default"
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                CompactTrackPicker(
                    label = "Audio",
                    selectedSummary = "German 5.1",
                    options =
                        listOf(
                            TrackPickerOption(
                                value = "deu",
                                fullLabel = firstOptionLabel,
                                selected = true,
                            ),
                            TrackPickerOption(
                                value = "eng",
                                fullLabel = "English Stereo",
                                selected = false,
                            ),
                        ),
                    onSelect = { selected += it },
                    modifier = Modifier.testTag(TrackPickerTestTags.AUDIO),
                )
            }
        }
        composeRule.waitForIdle()

        fun awaitActivityWindowFocus() {
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                composeRule.activity.window.decorView
                    .hasWindowFocus()
            }
        }

        composeRule.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onAllNodes(
                isFocusable() and
                    (hasText(pickerLabel) or hasAnyDescendant(hasText(pickerLabel))),
                useUnmergedTree = true,
            ).assertCountEquals(1)
        composeRule.onNodeWithTag(TrackPickerTestTags.AUDIO).performClick()

        composeRule.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(3)
        val firstOption =
            composeRule.onNode(hasText(firstOptionLabel) and hasClickAction())
        val secondOption =
            composeRule.onNode(hasText("English Stereo") and hasClickAction())
        composeRule
            .onAllNodes(
                isFocusable() and
                    (hasText(firstOptionLabel) or hasAnyDescendant(hasText(firstOptionLabel))),
                useUnmergedTree = true,
            ).assertCountEquals(1)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { firstOption.assertIsFocused() }.isSuccess
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertMissing()
        assertTrue("Expected focused option activation, got $selected", selected == listOf("deu"))
        composeRule
            .onNodeWithTag(TrackPickerTestTags.AUDIO)
            .assertIsFocused()
        awaitActivityWindowFocus()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { firstOption.assertIsFocused() }.isSuccess
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        composeRule.waitForIdle()
        secondOption.assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertMissing()
        assertTrue(
            "Expected one Tab press to activate the second option, got $selected",
            selected == listOf("deu", "eng"),
        )
        composeRule
            .onNodeWithTag(TrackPickerTestTags.AUDIO)
            .assertIsFocused()
        awaitActivityWindowFocus()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertExists()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching { firstOption.assertIsFocused() }.isSuccess
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.waitForIdle()
        secondOption.assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertMissing()
        assertTrue(
            "Expected one Down press to activate the second option, got $selected",
            selected == listOf("deu", "eng", "eng"),
        )
        composeRule
            .onNodeWithTag(TrackPickerTestTags.AUDIO)
            .assertIsFocused()
        awaitActivityWindowFocus()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertExists()
    }

    @Test
    fun compactTrackPickerOwnerDismissesTheVisibleDialogSynchronously() {
        var activeOwner: ShellModalOwner? = null
        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                CompactTrackPicker(
                    label = "Audio",
                    selectedSummary = "English",
                    options =
                        listOf(
                            TrackPickerOption(
                                value = "eng",
                                fullLabel = "English Stereo",
                                selected = true,
                            ),
                        ),
                    onSelect = {},
                    onShellModalChange = { activeOwner = it },
                )
            }
        }

        composeRule.onNodeWithText("Audio · English").performClick()
        composeRule.runOnIdle {
            assertTrue(activeOwner?.modal == ShellModal.PlayerOptions)
        }
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertExists()

        composeRule.runOnIdle { dismissActiveShellModal(activeOwner) }

        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertMissing()
        composeRule.runOnIdle { assertTrue(activeOwner == null) }
    }

    @Test
    fun recommendationsScreenShowsTopSpacer() {
        val emptyRecommendations = JellyseerrRecommendationsState.Ready(emptyMap())

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyseerrRecommendationsScreen(
                    state = emptyRecommendations,
                    detailStates = emptyMap(),
                    onRefresh = {},
                    onRetryRail = {},
                    onLoadMore = {},
                    onOpenDetails = { _, _, _ -> },
                    onLoadDetail = {},
                    onRequestOpen = { _, _, _ -> },
                    onTrailer = { _, _, _, _ -> },
                    onImpression = { _, _, _ -> },
                    languageProfiles = JellyseerrLanguageProfiles.EMPTY,
                    onAddServer = {},
                    contentPadding = PaddingValues(),
                    onShellModalChange = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("recommendationsTopSpacer").assertExists()
    }

    @Test
    fun heartButtonTogglesFavoriteState() {
        var isFavorite by mutableStateOf(false)
        val detail =
            JellyfinItemDetail(
                id = "movie-1",
                name = "Sample Movie",
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = null,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                JellyfinDetailContent(
                    detail = detail,
                    baseUrl = null,
                    accessToken = null,
                    seasons = emptyList(),
                    isEpisode = false,
                    onPlay = {},
                    onQueueDownload = {},
                    isFavorite = isFavorite,
                    onToggleFavorite = { isFavorite = !isFavorite },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun playedActionSitsBesideDownloadAndTogglesItsActionLabel() {
        var isPlayed by mutableStateOf(false)
        val detail =
            JellyfinItemDetail(
                id = "episode-1",
                name = "Sample Episode",
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = null,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                JellyfinDetailContent(
                    detail = detail,
                    baseUrl = null,
                    accessToken = null,
                    seasons = emptyList(),
                    isEpisode = true,
                    onPlay = {},
                    onQueueDownload = {},
                    isPlayed = isPlayed,
                    onTogglePlayed = { isPlayed = !isPlayed },
                )
            }
        }

        val downloadBounds = composeRule.onNodeWithTag(DetailActionTestTags.DOWNLOAD).fetchSemanticsNode().boundsInRoot
        val playedBounds = composeRule.onNodeWithTag(DetailActionTestTags.PLAYED).fetchSemanticsNode().boundsInRoot
        assertTrue("Expected Seen beside Download", kotlin.math.abs(downloadBounds.top - playedBounds.top) < 1f)
        composeRule.onNodeWithText("Seen").performClick()
        composeRule.onNodeWithText("Unseen").assertIsDisplayed()
    }

    @Test
    fun detailActionsKeepPlayPrimaryAndTrailerSecondary() {
        var played = false
        var trailerPlayed = false
        val detail =
            JellyfinItemDetail(
                id = "movie-actions",
                name = "Action Movie",
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = null,
                communityRating = 8.4,
                officialRating = null,
                genres = listOf("Drama"),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources =
                    listOf(
                        JellyfinMediaSource(
                            id = "source",
                            name = "1080p",
                            runTimeTicks = null,
                            container = "mkv",
                            videoBitrate = null,
                            supportsDirectPlay = true,
                            supportsDirectStream = true,
                            supportsTranscoding = true,
                            streams = emptyList(),
                        ),
                    ),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                JellyfinDetailContent(
                    detail = detail,
                    baseUrl = null,
                    accessToken = null,
                    seasons = emptyList(),
                    isEpisode = false,
                    onPlay = { played = true },
                    onTrailer = { trailerPlayed = true },
                    onQueueDownload = {},
                )
            }
        }

        composeRule.onNodeWithTag(DetailActionTestTags.PRIMARY).performClick()
        composeRule.onNodeWithTag(DetailActionTestTags.TRAILER).performClick()
        composeRule.onNodeWithText("Drama").assertExists()
        assertTrue(played)
        assertTrue(trailerPlayed)
    }

    @Test
    fun immersiveMovieDetailUsesCommandDeckAndEnrichedSections() {
        var played = false
        var favorite by mutableStateOf(false)
        val item = movieItem("immersive-movie", "Enola Holmes 3", Clock.System.now().toString())
        val similar = movieItem("similar-movie", "The Housemaid", Clock.System.now().toString())
        val detail =
            JellyfinItemDetail(
                id = item.id,
                name = item.name,
                overview = "A detective adventure across Malta.",
                taglines = listOf("The next case begins."),
                runTimeTicks = 64_800_000_000L,
                productionYear = 2026,
                premiereDate = "2026-06-30",
                communityRating = 8.2,
                officialRating = "12+",
                genres = listOf("Adventure", "Crime", "Mystery"),
                studios = listOf("Legendary Pictures"),
                primaryImageTag = "primary",
                backdropImageTags = listOf("backdrop"),
                mediaSources =
                    listOf(
                        JellyfinMediaSource(
                            id = "source",
                            name = "4K",
                            runTimeTicks = 64_800_000_000L,
                            container = "mkv",
                            videoBitrate = 6_800_000,
                            supportsDirectPlay = true,
                            supportsDirectStream = true,
                            supportsTranscoding = true,
                            streams =
                                listOf(
                                    JellyfinMediaStream(
                                        type = JellyfinMediaStreamType.VIDEO,
                                        index = 0,
                                        displayTitle = "4K AV1 HDR",
                                        codec = "av1",
                                        language = null,
                                        isDefault = true,
                                        isForced = false,
                                        width = 3840,
                                        height = 2160,
                                        videoRangeType = "HDR10",
                                    ),
                                ),
                        ),
                    ),
                people =
                    listOf(
                        JellyfinPerson(
                            id = "person-1",
                            name = "Millie Bobby Brown",
                            role = "Enola Holmes",
                            type = "Actor",
                            primaryImageTag = "person",
                        ),
                    ),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                ImmersiveMediaDetailContent(
                    item = item,
                    detail = detail,
                    enrichment = MediaDetailEnrichment(similarItems = listOf(similar)),
                    playActionLabel = "Watch",
                    onPlay = { played = true },
                    isFavorite = favorite,
                    onToggleFavorite = { favorite = !favorite },
                )
            }
        }

        composeRule.onNodeWithTag(ImmersiveDetailTestTags.HERO).assertIsDisplayed()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.TITLE).assertExists()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.COMMAND_DECK).assertIsDisplayed()
        composeRule.onNodeWithTag(DetailActionTestTags.PRIMARY).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag(DetailActionTestTags.FAVORITE).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(DetailActionTestTags.DOWNLOAD).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(DetailActionTestTags.PLAYED).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertExists()
        assertTrue(played)

        composeRule.onNodeWithTag(ImmersiveDetailTestTags.ROOT).performScrollToIndex(5)
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.CAST).assertExists()
        composeRule.onNodeWithText("Millie Bobby Brown").assertExists()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.ROOT).performScrollToIndex(6)
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.SIMILAR).assertExists()
        composeRule.onNodeWithText("The Housemaid").assertExists()
    }

    @Test
    fun immersiveDetailPrefersClearlogoWhenAvailable() {
        val item = movieItem("logo-movie", "Logo Movie", Clock.System.now().toString())
        val detail =
            JellyfinItemDetail(
                id = item.id,
                name = item.name,
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
                logoImageTag = "logo",
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ImmersiveMediaDetailContent(item = item, detail = detail)
            }
        }

        composeRule.onNodeWithTag(ImmersiveDetailTestTags.LOGO).assertExists()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.TITLE).assertMissing()
    }

    @Test
    fun immersiveEpisodeFallsBackToTextAndExposesInformationTab() {
        val base = movieItem("episode-detail", "The Storm Dragon", Clock.System.now().toString())
        val episode =
            base.copy(
                type = "Episode",
                seriesId = "series-1",
                seriesName = "Parallel World",
                parentIndexNumber = 1,
                indexNumber = 1,
            )
        val detail =
            JellyfinItemDetail(
                id = episode.id,
                name = episode.name,
                overview = "The adventure begins.",
                taglines = emptyList(),
                runTimeTicks = 1_500_000_000L,
                productionYear = 2026,
                premiereDate = "2026-07-01",
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
                originalLanguage = "ja",
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = false) {
                ImmersiveMediaDetailContent(
                    item = episode,
                    detail = detail,
                    showPlayedAction = true,
                )
            }
        }

        composeRule.onNodeWithTag(ImmersiveDetailTestTags.TITLE).assertExists()
        composeRule.onNodeWithText("Parallel World · Season 1 · Episode 1").assertExists()
        composeRule.onNodeWithTag(DetailActionTestTags.PLAYED).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Info").performClick()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.ROOT).performScrollToIndex(4)
        composeRule.onNodeWithText("Original language").assertExists()
        composeRule.onNodeWithText("ja").assertExists()
    }

    @Test
    fun immersiveDetailRestoresSectionAndScrollStatePerItem() {
        val first = movieItem("stateful-first", "First Movie", Clock.System.now().toString())
        val second = movieItem("stateful-second", "Second Movie", Clock.System.now().toString())
        var current by mutableStateOf(first)

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ImmersiveMediaDetailContent(
                    item = current,
                    detail =
                        JellyfinItemDetail(
                            id = current.id,
                            name = current.name,
                            overview = "Overview for ${current.name}",
                            taglines = emptyList(),
                            runTimeTicks = null,
                            productionYear = 2026,
                            premiereDate = "2026-07-01",
                            communityRating = null,
                            officialRating = null,
                            genres = emptyList(),
                            studios = listOf("Jellystack Studios"),
                            primaryImageTag = null,
                            backdropImageTags = emptyList(),
                            mediaSources = emptyList(),
                            originalLanguage = "en",
                        ),
                )
            }
        }

        composeRule.onNodeWithTag("immersive_detail_tab_info").performClick().assertIsSelected()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.ROOT).performScrollToIndex(4)
        composeRule.onNodeWithText("Original language").assertIsDisplayed()

        composeRule.runOnUiThread { current = second }
        composeRule.onNodeWithTag("immersive_detail_tab_overview").assertIsSelected()

        composeRule.runOnUiThread { current = first }
        composeRule.onNodeWithTag("immersive_detail_tab_info").assertIsSelected()
        composeRule.onNodeWithText("Original language").assertIsDisplayed()
    }

    @Test
    fun immersiveDetailUsesExpandedPaneProfileWithoutDuplicateBackAction() {
        val item = movieItem("expanded-detail", "Expanded Movie", Clock.System.now().toString())
        val detail =
            JellyfinItemDetail(
                id = item.id,
                name = item.name,
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ProvideResponsiveProfile(Modifier.requiredSize(width = 1_000.dp, height = 800.dp)) {
                    ImmersiveMediaDetailContent(item = item, detail = detail)
                }
            }
        }

        composeRule.onNodeWithTag(ImmersiveDetailTestTags.HERO).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertMissing()
        composeRule.onNodeWithTag(ImmersiveDetailTestTags.COMMAND_DECK).assertIsDisplayed()
    }

    @Test
    fun immersiveDetailMoreMenuOpensTrackSelectors() {
        val item = movieItem("track-detail", "Track Movie", Clock.System.now().toString())
        val detail =
            JellyfinItemDetail(
                id = item.id,
                name = item.name,
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
            )
        val english = AudioTrack("audio-en", "en", "English AAC", "aac", true, 0)
        val japanese = AudioTrack("audio-ja", "ja", "Japanese AAC", "aac", false, 1)
        val subtitles =
            listOf(
                SubtitleTrack(
                    id = "subtitle-en",
                    language = "en",
                    title = "English subtitles",
                    format = SubtitleFormat.SRT,
                    isDefault = false,
                    isForced = false,
                    streamIndex = 2,
                ),
            )
        var selectedAudio by mutableStateOf(english)
        var selectedSubtitle by mutableStateOf<SubtitleTrack?>(null)

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ImmersiveMediaDetailContent(
                    item = item,
                    detail = detail,
                    audioTracks = listOf(english, japanese),
                    selectedAudioTrack = selectedAudio,
                    onSelectAudioTrack = { selectedAudio = it },
                    subtitleTracks = subtitles,
                    selectedSubtitleTrack = selectedSubtitle,
                    onSelectSubtitleTrack = { selectedSubtitle = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Audio · English AAC").performClick()
        composeRule.onNodeWithTag(TrackPickerTestTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Japanese AAC").performClick()
        assertEquals("audio-ja", selectedAudio.id)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Subtitles · Off").performClick()
        composeRule.onNodeWithText("English subtitles").performClick()
        assertEquals("subtitle-en", selectedSubtitle?.id)
    }

    @Test
    fun immersiveDetailKeepsJellyfinMetadataAheadOfSeerrFallbacks() {
        val item = movieItem("priority-detail", "Priority Movie", Clock.System.now().toString())
        val detail =
            JellyfinItemDetail(
                id = item.id,
                name = item.name,
                overview = "Primary overview",
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = "2026-07-01",
                communityRating = 8.1,
                officialRating = null,
                genres = emptyList(),
                studios = listOf("Jellyfin Studio"),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
                originalLanguage = "jellyfin-language",
                productionLocations = listOf("Jellyfin Country"),
            )
        val seerrDetail =
            JellyseerrMediaDetail(
                tmdbId = 42,
                mediaType = JellyseerrMediaType.MOVIE,
                title = item.name,
                year = "2030",
                overview = "Fallback overview",
                runtimeMinutes = 90,
                genres = emptyList(),
                releaseDate = "2030-01-01",
                revenue = null,
                originalLanguage = "seerr-language",
                productionCountries = listOf("Seerr Country"),
                studios = listOf("Seerr Studio"),
                ratings =
                    JellyseerrMediaRatings(
                        tmdb = 7.5,
                        imdb = null,
                        rottenTomatoesCritics = null,
                        rottenTomatoesAudience = null,
                    ),
                trailer = null,
                posterPath = null,
                backdropPath = null,
                jellyseerrUrl = null,
                jellyfinUrl = null,
                imdbId = null,
                tvdbId = null,
            )

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                InfoSection(
                    detail = detail,
                    enrichment = MediaDetailEnrichment(seerrDetail = seerrDetail),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composeRule.onNodeWithText("jellyfin-language").assertExists()
        composeRule.onNodeWithText("Jellyfin Country").assertExists()
        composeRule.onNodeWithText("Jellyfin Studio").assertExists()
        composeRule.onNodeWithText("seerr-language").assertMissing()
        composeRule.onNodeWithText("Seerr Country").assertMissing()
        composeRule.onNodeWithText("Seerr Studio").assertMissing()
    }

    @Test
    fun immersiveSeriesDownloadActionOffersScopeSelection() {
        val series = movieItem("download-series", "Download Series", Clock.System.now().toString()).copy(type = "Series")
        val episode =
            movieItem("download-episode", "Episode 1", Clock.System.now().toString())
                .copy(type = "Episode", seriesId = series.id, parentIndexNumber = 1, indexNumber = 1)
        val season = SeasonEpisodes(seasonNumber = 1, episodes = listOf(episode), sortKey = 1)
        val detail =
            JellyfinItemDetail(
                id = series.id,
                name = series.name,
                overview = null,
                taglines = emptyList(),
                runTimeTicks = null,
                productionYear = 2026,
                premiereDate = null,
                communityRating = null,
                officialRating = null,
                genres = emptyList(),
                studios = emptyList(),
                primaryImageTag = null,
                backdropImageTags = emptyList(),
                mediaSources = emptyList(),
            )
        var allSeasonsRequested = false
        var requestedSeason: Int? = null

        composeRule.setContent {
            JellystackTheme(isDarkTheme = true) {
                ImmersiveMediaDetailContent(
                    item = series,
                    detail = detail,
                    seasons = listOf(season),
                    onDownloadSeries = { allSeasonsRequested = true },
                    onDownloadSeason = { requestedSeason = it.seasonNumber },
                )
            }
        }

        composeRule.onNodeWithTag(DetailActionTestTags.DOWNLOAD).performClick()
        composeRule
            .onNode(
                hasText("Season 1") and
                    hasAnyAncestor(hasTestTag(TrackPickerTestTags.DIALOG)),
            ).performClick()
        assertEquals(1, requestedSeason)

        composeRule.onNodeWithTag(DetailActionTestTags.DOWNLOAD).performClick()
        composeRule
            .onNode(
                hasText("All seasons") and
                    hasAnyAncestor(hasTestTag(TrackPickerTestTags.DIALOG)),
            ).performClick()
        assertTrue(allSeasonsRequested)
    }
}

@Composable
private fun manualSpotlightHarness() {
    val candidates = remember { spotlightCandidates() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    JellystackTheme(isDarkTheme = false) {
        HomeSpotlight(
            candidates = candidates,
            selectedId = selectedId,
            onSelected = { selectedId = it },
        ) { candidate, _, _ ->
            Text(candidate.displayItem.name)
        }
    }
}

@Composable
private fun rootOwnedSpotlightHarness() {
    var primaryDestination by rememberSaveable { mutableStateOf(PrimaryDestination.Home) }
    var selectedSpotlightId by rememberSaveable { mutableStateOf<String?>(null) }

    JellystackTheme(isDarkTheme = false) {
        when (primaryDestination) {
            PrimaryDestination.Home ->
                Column {
                    Button(
                        onClick = { primaryDestination = PrimaryDestination.Library },
                        modifier = Modifier.testTag("test_leave_home"),
                    ) {
                        Text("Leave Home")
                    }
                    HomeContent(
                        hasServers = true,
                        browseState = spotlightHomeState(),
                        onSelectLibrary = {},
                        onRefreshLibraries = {},
                        onLoadMore = {},
                        onOpenItemDetail = {},
                        onPlayItem = {},
                        onConnectJellyfin = {},
                        onConnectJellyseerr = {},
                        learnMoreUrl = "https://example.com",
                        downloadStatuses = emptyMap(),
                        selectedSpotlightId = selectedSpotlightId,
                        onSelectedSpotlightIdChange = { selectedSpotlightId = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            PrimaryDestination.Library,
            PrimaryDestination.Discover,
            PrimaryDestination.Admin,
            ->
                Column {
                    Text("Library surface")
                    Button(
                        onClick = { primaryDestination = PrimaryDestination.Home },
                        modifier = Modifier.testTag("test_return_home"),
                    ) {
                        Text("Return Home")
                    }
                }
        }
    }
}

private fun spotlightHomeState(): JellyfinHomeState =
    JellyfinHomeState(
        libraries =
            listOf(
                JellyfinLibrary(
                    id = "lib-movies",
                    name = "Movies",
                    collectionType = "movies",
                    itemCount = null,
                    primaryImageTag = null,
                ),
            ),
        selectedLibraryId = "lib-movies",
        recentMovies = spotlightCandidates().map { candidate -> candidate.displayItem },
    )

private fun spotlightCandidates(): List<SpotlightCandidate> {
    val now = Clock.System.now()
    return listOf(
        movieItem("movie-1", "First movie", (now - 1.days).toString()),
        movieItem("movie-2", "Second movie", (now - 2.days).toString()),
    ).map { item ->
        SpotlightCandidate(
            displayItem = item,
            actionItem = item,
            addedAt = Instant.parse(item.dateCreated!!),
        )
    }
}

private fun movieItem(
    id: String,
    name: String,
    dateCreated: String,
): JellyfinItem =
    JellyfinItem(
        id = id,
        libraryId = "lib-movies",
        name = name,
        sortName = name,
        overview = null,
        type = "Movie",
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = "lib-movies",
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = null,
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
        dateCreated = dateCreated,
    )

private fun SemanticsNodeInteraction.assertExists(): SemanticsNodeInteraction {
    assertTrue("Expected node to exist.", runCatching { fetchSemanticsNode() }.isSuccess)
    return this
}

private fun SemanticsNodeInteraction.assertMissing(): SemanticsNodeInteraction {
    assertTrue("Expected node to not exist.", runCatching { fetchSemanticsNode() }.isFailure)
    return this
}
