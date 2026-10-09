@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * The top-level navigation. It is hidden while the content has focus and slides in when Left at the content's
 * edge or Back opens it ([expanded]). The focus materializer stays composed, so it is in place before the items
 * appear; items are focusable only while the rail is open, also while it slides out.
 */
@Composable
internal fun TvNavigationRail(
    selected: TvRoute,
    expanded: Boolean,
    strings: TvStrings,
    profileName: String,
    callbacks: TvNavigationRailCallbacks,
) {
    val entries =
        listOf(
            TvNavigationRailEntry(TvRoute.Home, strings.home, Icons.Default.Home),
            TvNavigationRailEntry(TvRoute.Library(), strings.library, Icons.Default.VideoLibrary),
            TvNavigationRailEntry(TvRoute.Search, strings.search, Icons.Default.Search),
            TvNavigationRailEntry(TvRoute.Discover, strings.discover, Icons.Default.Explore),
            TvNavigationRailEntry(TvRoute.Settings(), strings.settings, Icons.Default.Settings),
        )
    TvRouteFocusMaterializer(
        ownerId = "navigation-rail",
        targetIds = entries.map { tvRailTargetId(it.route) }.toSet() + TV_PROFILE_AVATAR_TARGET,
        fallbackTargetIds = setOf(tvRailTargetId(TvRoute.Home)),
    ) { true }
    val motionMs = if (LocalTvFocusAppearance.current.reducedMotion) 0 else TV_RAIL_SLIDE_MS
    AnimatedVisibility(
        visible = expanded,
        enter = slideInHorizontally(tween(motionMs)) { -it } + fadeIn(tween(motionMs)),
        exit = slideOutHorizontally(tween(motionMs)) { -it } + fadeOut(tween(motionMs)),
    ) {
        TvNavigationRailPanel {
            TvNavigationRailProfile(
                label = "${strings.profiles}: $profileName",
                profileName = profileName,
                expanded = expanded,
                onProfile = callbacks.onProfile,
            )
            entries.forEach { entry ->
                TvNavigationRailItem(entry, isSelected = selected.sameTopLevel(entry.route), expanded, callbacks)
            }
        }
    }
}

internal class TvNavigationRailCallbacks(
    val onProfile: () -> Unit,
    val onSelected: (TvRoute) -> Unit,
    val onDismiss: () -> Unit,
)

internal fun tvNavigationRailItemsFocusable(expanded: Boolean): Boolean = expanded

private class TvNavigationRailEntry(
    val route: TvRoute,
    val label: String,
    val icon: ImageVector,
)

private const val TV_RAIL_SLIDE_MS = 180

private val TV_RAIL_ITEM_SHAPE = RoundedCornerShape(18.dp)

private val TV_RAIL_ITEM_WIDTH = TvLayoutTokens.ExpandedRailWidth - 20.dp

/** The open rail over a scrim that fades into the content. */
@Composable
private fun TvNavigationRailPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(TvLayoutTokens.ExpandedRailWidth + 56.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xFF080910), Color(0xF20E0F18), Color.Transparent))),
    ) {
        Column(
            modifier =
                Modifier
                    .width(TvLayoutTokens.ExpandedRailWidth)
                    .fillMaxHeight()
                    .background(Color(0xE60B0C14))
                    .padding(horizontal = 10.dp, vertical = TvLayoutTokens.SafeInsets.vertical),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TvNavigationRailProfile(
    label: String,
    profileName: String,
    expanded: Boolean,
    onProfile: () -> Unit,
) {
    Row(
        Modifier
            .width(TV_RAIL_ITEM_WIDTH)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .tvFocusable(
                onClick = onProfile,
                enabled = tvNavigationRailItemsFocusable(expanded),
                shape = TV_RAIL_ITEM_SHAPE,
                focusTargetId = TV_PROFILE_AVATAR_TARGET.takeIf { expanded },
            ).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Default.AccountCircle, null, tint = TvPurple)
        Text(profileName, color = TvText, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun TvNavigationRailItem(
    entry: TvNavigationRailEntry,
    isSelected: Boolean,
    expanded: Boolean,
    callbacks: TvNavigationRailCallbacks,
) {
    Row(
        Modifier
            .width(TV_RAIL_ITEM_WIDTH)
            .onPreviewKeyEvent { event ->
                // Right on the current section closes the rail and returns to that section's content.
                if (
                    isSelected &&
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    callbacks.onDismiss()
                    true
                } else {
                    false
                }
            }.background(if (isSelected) TvPurpleStrong.copy(alpha = 0.48f) else Color.Transparent, TV_RAIL_ITEM_SHAPE)
            .semantics(mergeDescendants = true) {
                contentDescription = entry.label
                this.selected = isSelected
            }.tvFocusable(
                onClick = { callbacks.onSelected(entry.route) },
                enabled = tvNavigationRailItemsFocusable(expanded),
                shape = TV_RAIL_ITEM_SHAPE,
                focusTargetId = tvRailTargetId(entry.route).takeIf { expanded },
            ).padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(entry.icon, null, tint = if (isSelected) TvPurple else TvTextMuted)
        Text(entry.label, color = if (isSelected) TvText else TvTextMuted, fontSize = 18.sp)
    }
}

private fun TvRoute.sameTopLevel(other: TvRoute): Boolean =
    (this is TvRoute.Home && other is TvRoute.Home) ||
        (this is TvRoute.Library && other is TvRoute.Library) ||
        (this is TvRoute.Search && other is TvRoute.Search) ||
        (this is TvRoute.Discover && other is TvRoute.Discover) ||
        (this is TvRoute.Settings && other is TvRoute.Settings)
