package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState

/** A row the home screen can show: its stable [id] and the [title] the settings list names it by. */
internal data class TvHomeRowOption(
    val id: String,
    val title: String,
)

internal const val TV_HOME_MY_LIST_ROW = "my-list"
internal const val TV_HOME_CONTINUE_ROW = "continue"
internal const val TV_HOME_NEXT_UP_ROW = "next"
internal const val TV_HOME_LIBRARIES_ROW = "libraries"

internal fun tvHomePluginRowId(sectionId: String): String = "plugin:$sectionId"

/**
 * The rows the home screen builds for this state, in the order it builds them without a user choice: My List, then
 * either the server's sections or Continue watching, Next up and My media. Rows without content are left out.
 */
internal fun tvHomeRowOptions(
    state: JellyfinHomeState,
    homeSections: HomeSectionsState,
    hasMyList: Boolean,
    strings: TvStrings,
): List<TvHomeRowOption> =
    buildList {
        if (hasMyList) add(TvHomeRowOption(TV_HOME_MY_LIST_ROW, strings.myList))
        if (homeSections is HomeSectionsState.Ready) {
            homeSections.sections
                .filter { it.items.isNotEmpty() }
                .distinctBy { it.id }
                .forEach { add(TvHomeRowOption(tvHomePluginRowId(it.id), it.title)) }
        } else {
            if (state.continueWatching.isNotEmpty()) {
                add(TvHomeRowOption(TV_HOME_CONTINUE_ROW, strings.continueWatching))
            }
            if (state.nextUp.isNotEmpty()) add(TvHomeRowOption(TV_HOME_NEXT_UP_ROW, strings.nextUp))
            if (state.libraries.isNotEmpty()) add(TvHomeRowOption(TV_HOME_LIBRARIES_ROW, strings.myMedia))
        }
    }

internal fun tvHomeRowToggleTargetId(rowId: String): String = tvSettingsControlTargetId("home-row:$rowId")
