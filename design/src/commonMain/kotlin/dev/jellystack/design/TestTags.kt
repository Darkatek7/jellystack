package dev.jellystack.design

/**
 * Shared test tags used by both production code (for [androidx.compose.ui.platform.testTag]
 * annotations) and instrumented tests (for [androidx.compose.ui.test.onNodeWithTag] lookups).
 */
object TestTags {
    const val RECOMMENDATION_BACKDROP: String = "recommendation_backdrop"
    const val SEERR_RATINGS: String = "seerr_ratings"
    const val PRIMARY_HOME: String = "primary_destination_home"
    const val PRIMARY_LIBRARY: String = "primary_destination_library"
    const val PRIMARY_DISCOVER: String = "primary_destination_discover"
}

internal object ShellTestTags {
    const val BOTTOM_DOCK: String = "shell_bottom_dock"
    const val NAVIGATION_RAIL: String = "shell_navigation_rail"
    const val PRIMARY_PANE: String = "shell_primary_pane"
    const val SECONDARY_PANE: String = "shell_secondary_pane"
    const val OPEN_SETTINGS: String = "open_settings"
}
