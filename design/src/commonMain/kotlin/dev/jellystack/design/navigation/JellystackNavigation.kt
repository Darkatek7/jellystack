package dev.jellystack.design.navigation

import dev.jellystack.core.preferences.TutorialStep

internal enum class PrimaryDestination { Home, Library, Discover }

internal enum class DiscoverDestination { Feed, Requests }

internal enum class LibrarySection { Downloads, Favorites, Libraries, Movies, Series }

internal sealed interface LibraryDestination {
    data object Root : LibraryDestination

    data class Section(
        val section: LibrarySection,
    ) : LibraryDestination

    data class Library(
        val libraryId: String,
        val title: String,
    ) : LibraryDestination

    data class Children(
        val parentId: String,
        val title: String,
    ) : LibraryDestination
}

internal fun resolveLibraryDestinationTitle(
    destination: LibraryDestination,
    rootTitle: String,
    sectionTitles: Map<LibrarySection, String>,
): String =
    when (destination) {
        LibraryDestination.Root -> rootTitle
        is LibraryDestination.Section -> sectionTitles[destination.section] ?: rootTitle
        is LibraryDestination.Library -> destination.title
        is LibraryDestination.Children -> destination.title
    }

internal enum class DetailOrigin { Home, Library, Discover, Requests }

internal data class DetailStackEntry(
    val mediaId: String,
    val origin: DetailOrigin,
    val generation: Long,
)

internal fun isCurrentDetailRequest(
    stack: List<DetailStackEntry>,
    index: Int,
    expected: DetailStackEntry,
): Boolean = stack.getOrNull(index) == expected

internal inline fun publishIfCurrentDetailRequest(
    stack: List<DetailStackEntry>,
    index: Int,
    expected: DetailStackEntry,
    publish: () -> Unit,
): Boolean {
    if (!isCurrentDetailRequest(stack, index, expected)) return false
    publish()
    return true
}

internal enum class ShellModal {
    WhatsNew,
    ServerEditor,
    ServerRemoval,
    AppLockChoice,
    RequestConfiguration,
    RequestManagement,
    SeerrMediaDetail,
    PlayerOptions,
}

internal class ShellModalOwner(
    val modal: ShellModal,
    val dismiss: () -> Unit,
)

internal fun dismissActiveShellModal(owner: ShellModalOwner?) {
    owner?.dismiss?.invoke()
}

internal data class BackStackSnapshot(
    val primary: PrimaryDestination = PrimaryDestination.Home,
    val discover: DiscoverDestination = DiscoverDestination.Feed,
    val libraryDepth: Int = 0,
    val detailDepth: Int = 0,
    val settingsOpen: Boolean = false,
    val onboardingStep: TutorialStep? = null,
    val onboardingIsFirstRun: Boolean = false,
    val modal: ShellModal? = null,
    val appLocked: Boolean = false,
    val discoverSelectionVisible: Boolean = false,
)

internal enum class ShellBackAction {
    DismissModal,
    PreviousOnboardingStep,
    CloseOnboarding,
    CloseSettings,
    PopDetail,
    CloseDiscoverSelection,
    CloseRequests,
    PopLibrary,
    SelectHome,
    ExitPlatform,
}

internal fun nextBackAction(state: BackStackSnapshot): ShellBackAction =
    when {
        state.appLocked -> ShellBackAction.ExitPlatform
        state.modal != null -> ShellBackAction.DismissModal
        state.onboardingStep == TutorialStep.Welcome && state.onboardingIsFirstRun ->
            ShellBackAction.ExitPlatform
        state.onboardingStep == TutorialStep.Welcome -> ShellBackAction.CloseOnboarding
        state.onboardingStep != null -> ShellBackAction.PreviousOnboardingStep
        state.settingsOpen -> ShellBackAction.CloseSettings
        state.detailDepth > 0 -> ShellBackAction.PopDetail
        state.primary == PrimaryDestination.Discover &&
            state.discoverSelectionVisible -> ShellBackAction.CloseDiscoverSelection
        state.primary == PrimaryDestination.Discover &&
            state.discover == DiscoverDestination.Requests -> ShellBackAction.CloseRequests
        state.primary == PrimaryDestination.Library && state.libraryDepth > 0 -> ShellBackAction.PopLibrary
        state.primary != PrimaryDestination.Home -> ShellBackAction.SelectHome
        else -> ShellBackAction.ExitPlatform
    }
