package dev.jellystack.design.navigation

import dev.jellystack.core.preferences.TutorialStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JellystackNavigationTest {
    @Test
    fun homeOwnedDiscoverSelectionReturnsToHomeWhenClosed() {
        assertEquals(
            PrimaryDestination.Home,
            destinationAfterClosingDiscoverSelection(
                current = PrimaryDestination.Discover,
                returnDestination = PrimaryDestination.Home,
            ),
        )
    }

    @Test
    fun discoverOwnedSelectionRemainsOnDiscoverWhenClosed() {
        assertEquals(
            PrimaryDestination.Discover,
            destinationAfterClosingDiscoverSelection(
                current = PrimaryDestination.Discover,
                returnDestination = null,
            ),
        )
    }

    @Test
    fun detailCompletionRequiresTheExactRequestGeneration() {
        val original =
            DetailStackEntry(
                mediaId = "same-id",
                origin = DetailOrigin.Home,
                generation = 41,
            )
        val replacement = original.copy(generation = 42)

        assertTrue(isCurrentDetailRequest(listOf(original), index = 0, expected = original))
        assertFalse(isCurrentDetailRequest(listOf(replacement), index = 0, expected = original))
    }

    @Test
    fun supersededSameSlotDetailFailureCannotPublishGlobalError() {
        val failedRequest =
            DetailStackEntry(
                mediaId = "same-id",
                origin = DetailOrigin.Home,
                generation = 41,
            )
        val replacement = failedRequest.copy(generation = 42)
        var globalError: String? = null

        val published =
            publishIfCurrentDetailRequest(
                stack = listOf(replacement),
                index = 0,
                expected = failedRequest,
            ) {
                globalError = "Connection failed"
            }

        assertFalse(published)
        assertNull(globalError)

        assertTrue(
            publishIfCurrentDetailRequest(
                stack = listOf(failedRequest),
                index = 0,
                expected = failedRequest,
            ) {
                globalError = "Current failure"
            },
        )
        assertEquals("Current failure", globalError)
    }

    @Test
    fun modalOwnerIsDismissedBeforeTheShellClearsItsSnapshot() {
        val events = mutableListOf<String>()
        lateinit var owner: ShellModalOwner
        var activeOwner: ShellModalOwner? = null
        owner =
            ShellModalOwner(ShellModal.PlayerOptions) {
                assertSame(owner, activeOwner)
                events += "owner-dismissed"
                activeOwner = null
                events += "snapshot-cleared"
            }
        activeOwner = owner

        dismissActiveShellModal(activeOwner)

        assertEquals(listOf("owner-dismissed", "snapshot-cleared"), events)
        assertEquals(null, activeOwner)
    }

    @Test
    fun modalOwnerCanDeclineDismissalWithoutLosingTheShellSnapshot() {
        lateinit var owner: ShellModalOwner
        var activeOwner: ShellModalOwner? = null
        owner = ShellModalOwner(ShellModal.ServerEditor) {}
        activeOwner = owner

        dismissActiveShellModal(activeOwner)

        assertSame(owner, activeOwner)
    }

    @Test
    fun backUsesTheApprovedPriority() {
        val base =
            BackStackSnapshot(
                primary = PrimaryDestination.Discover,
                discover = DiscoverDestination.Requests,
                libraryDepth = 2,
                detailDepth = 1,
                settingsOpen = true,
                onboardingStep = null,
                modal = ShellModal.RequestConfiguration,
                appLocked = false,
            )

        assertEquals(ShellBackAction.DismissModal, nextBackAction(base))
        assertEquals(ShellBackAction.CloseSettings, nextBackAction(base.copy(modal = null)))
        assertEquals(
            ShellBackAction.PopDetail,
            nextBackAction(base.copy(modal = null, settingsOpen = false)),
        )
        assertEquals(
            ShellBackAction.CloseRequests,
            nextBackAction(base.copy(modal = null, settingsOpen = false, detailDepth = 0)),
        )
        assertEquals(
            ShellBackAction.PopLibrary,
            nextBackAction(
                base.copy(
                    primary = PrimaryDestination.Library,
                    modal = null,
                    settingsOpen = false,
                    detailDepth = 0,
                    discover = DiscoverDestination.Feed,
                ),
            ),
        )
        assertEquals(
            ShellBackAction.SelectHome,
            nextBackAction(
                base.copy(
                    primary = PrimaryDestination.Discover,
                    modal = null,
                    settingsOpen = false,
                    detailDepth = 0,
                    discover = DiscoverDestination.Feed,
                ),
            ),
        )
    }

    @Test
    fun expandedDiscoverSelectionClosesBeforeRequestsPage() {
        val snapshot =
            BackStackSnapshot(
                primary = PrimaryDestination.Discover,
                discover = DiscoverDestination.Requests,
                discoverSelectionVisible = true,
            )

        assertEquals(ShellBackAction.CloseDiscoverSelection, nextBackAction(snapshot))
        assertEquals(
            ShellBackAction.CloseRequests,
            nextBackAction(snapshot.copy(discoverSelectionVisible = false)),
        )
    }

    @Test
    fun adminSubpageClosesBeforeLeavingAdmin() {
        val snapshot =
            BackStackSnapshot(
                primary = PrimaryDestination.Admin,
                adminDepth = 1,
            )

        assertEquals(ShellBackAction.PopAdmin, nextBackAction(snapshot))
        assertEquals(ShellBackAction.SelectHome, nextBackAction(snapshot.copy(adminDepth = 0)))
    }

    @Test
    fun discoverFeedDetailClosesBeforeLeavingDiscover() {
        val snapshot =
            BackStackSnapshot(
                primary = PrimaryDestination.Discover,
                discover = DiscoverDestination.Feed,
                discoverSelectionVisible = true,
            )

        assertEquals(ShellBackAction.CloseDiscoverSelection, nextBackAction(snapshot))
        assertEquals(
            ShellBackAction.SelectHome,
            nextBackAction(snapshot.copy(discoverSelectionVisible = false)),
        )
    }

    @Test
    fun homeAndFirstRunWelcomeExitButSettingsGuideCloses() {
        assertEquals(ShellBackAction.ExitPlatform, nextBackAction(BackStackSnapshot()))
        assertEquals(
            ShellBackAction.ExitPlatform,
            nextBackAction(
                BackStackSnapshot(
                    onboardingStep = TutorialStep.Welcome,
                    onboardingIsFirstRun = true,
                ),
            ),
        )
        assertEquals(
            ShellBackAction.CloseOnboarding,
            nextBackAction(BackStackSnapshot(onboardingStep = TutorialStep.Welcome)),
        )
    }

    @Test
    fun appLockNeverMutatesHiddenNavigation() {
        val locked =
            BackStackSnapshot(
                primary = PrimaryDestination.Library,
                libraryDepth = 3,
                detailDepth = 2,
                modal = ShellModal.RequestConfiguration,
                appLocked = true,
            )
        assertEquals(ShellBackAction.ExitPlatform, nextBackAction(locked))
    }

    @Test
    fun libraryShellTitleUsesTheVisibleDestinationName() {
        val sectionTitles = LibrarySection.entries.associateWith { "section:${it.name}" }

        assertEquals(
            "Library",
            resolveLibraryDestinationTitle(
                destination = LibraryDestination.Root,
                rootTitle = "Library",
                sectionTitles = sectionTitles,
            ),
        )
        assertEquals(
            "section:Favorites",
            resolveLibraryDestinationTitle(
                destination = LibraryDestination.Section(LibrarySection.Favorites),
                rootTitle = "Library",
                sectionTitles = sectionTitles,
            ),
        )
        assertEquals(
            "Anime",
            resolveLibraryDestinationTitle(
                destination = LibraryDestination.Library("anime", "Anime"),
                rootTitle = "Library",
                sectionTitles = sectionTitles,
            ),
        )
        assertEquals(
            "Season 2",
            resolveLibraryDestinationTitle(
                destination = LibraryDestination.Children("season-2", "Season 2"),
                rootTitle = "Library",
                sectionTitles = sectionTitles,
            ),
        )
    }
}
