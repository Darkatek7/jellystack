package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/** What the remote's Menu key does on the authenticated app shell. */
internal enum class TvMenuKeyAction { OPEN_PROFILE_PICKER, NONE }

/**
 * Menu opens the profile picker on the main screens (the rail's routes) only. Detail pages, lists, the player
 * and anything shown over a screen keep their own Menu behaviour.
 */
internal fun tvMenuKeyAction(
    route: TvRoute,
    dialogOpen: Boolean,
): TvMenuKeyAction =
    when {
        dialogOpen -> TvMenuKeyAction.NONE
        route is TvRoute.Home ||
            route is TvRoute.Library ||
            route is TvRoute.Search ||
            route is TvRoute.Discover ||
            route is TvRoute.LiveTv ||
            route is TvRoute.Settings -> TvMenuKeyAction.OPEN_PROFILE_PICKER
        else -> TvMenuKeyAction.NONE
    }

/** Opens the profile picker on Menu key down when [tvMenuKeyAction] allows it for [route]. */
internal fun Modifier.tvMenuKeyOpensProfilePicker(
    route: TvRoute,
    dialogOpen: Boolean,
    onOpenProfilePicker: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_MENU ||
            tvMenuKeyAction(route, dialogOpen) != TvMenuKeyAction.OPEN_PROFILE_PICKER
        ) {
            return@onPreviewKeyEvent false
        }
        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0) {
            onOpenProfilePicker()
        }
        true
    }
