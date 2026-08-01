package dev.jellystack.design.shell

import dev.jellystack.design.navigation.DiscoverDestination
import dev.jellystack.design.navigation.PrimaryDestination

internal data class ShellFeedback(
    val id: Long,
    val message: String,
    val onDismiss: () -> Unit,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

internal enum class ShellPaneMode {
    Single,
    ListDetail,
}

internal data class JellystackShellState(
    val primary: PrimaryDestination = PrimaryDestination.Home,
    val discover: DiscoverDestination = DiscoverDestination.Feed,
    val paneMode: ShellPaneMode = ShellPaneMode.Single,
    val dynamicTitle: String? = null,
    val showNavigation: Boolean = true,
    val showAdminDestination: Boolean = false,
    val feedback: ShellFeedback? = null,
) {
    val destinations: List<PrimaryDestination>
        get() =
            if (showAdminDestination) {
                PrimaryDestination.entries
            } else {
                PrimaryDestination.entries.filterNot { it == PrimaryDestination.Admin }
            }
}

internal sealed interface JellystackShellAction {
    data class SelectPrimary(
        val destination: PrimaryDestination,
    ) : JellystackShellAction

    data object OpenSettings : JellystackShellAction

    data object FeedbackShown : JellystackShellAction

    data object FeedbackAction : JellystackShellAction
}
