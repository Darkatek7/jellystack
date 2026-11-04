@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.core.preferences.HomeRowEntry
import dev.jellystack.core.preferences.HomeRowPreferences
import dev.jellystack.core.preferences.arrange
import dev.jellystack.core.preferences.move
import dev.jellystack.core.preferences.withVisibility

private fun tvHomeRowMoveTargetId(
    rowId: String,
    direction: String,
): String = tvSettingsControlTargetId("home-row:$rowId:$direction")

/** Settings > Home: one line per row with a toggle (OK) and buttons that move it up or down. */
@Composable
internal fun TvHomeSettings(
    options: List<TvHomeRowOption>,
    preferences: HomeRowPreferences,
    strings: TvStrings,
    onChange: (HomeRowPreferences) -> Unit,
) {
    val labels = strings.homeSettings
    val ids = options.map { it.id }
    val titles = options.associate { it.id to it.title }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(labels.intro, color = TvTextMuted, fontSize = TvTextSize.BodyLarge)
        if (options.isEmpty()) Text(labels.empty, color = TvTextMuted, fontSize = TvTextSize.BodyLarge)
        preferences.arrange(ids).forEachIndexed { index, entry ->
            key(entry.id) {
                TvHomeRowLine(
                    entry = entry,
                    title = titles.getValue(entry.id),
                    labels = labels,
                    screenEntry = index == 0,
                    actions =
                        TvHomeRowActions(
                            onToggle = { onChange(preferences.withVisibility(entry.id, !entry.visible)) },
                            onUp = { onChange(preferences.move(ids, entry.id, -1)) },
                            onDown = { onChange(preferences.move(ids, entry.id, 1)) },
                        ),
                )
            }
        }
    }
}

private class TvHomeRowActions(
    val onToggle: () -> Unit,
    val onUp: () -> Unit,
    val onDown: () -> Unit,
)

@Composable
private fun TvHomeRowLine(
    entry: HomeRowEntry,
    title: String,
    labels: TvHomeSettingsStrings,
    screenEntry: Boolean,
    actions: TvHomeRowActions,
) {
    val stateLabel = if (entry.visible) labels.shown else labels.hidden
    val toggleTargetId = tvHomeRowToggleTargetId(entry.id)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .width(640.dp)
                .height(64.dp)
                .tvScreenEntryFocus(screenEntry, toggleTargetId)
                .semantics(mergeDescendants = true) { stateDescription = stateLabel }
                .tvFocusable(
                    onClick = actions.onToggle,
                    shape = TvShapes.Control,
                    focusToNavigationRailOnLeft = true,
                    focusTargetId = toggleTargetId,
                    fill = TvSurface,
                ).padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = if (entry.visible) TvText else TvTextMuted,
                fontSize = TvTextSize.SectionTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(stateLabel, color = if (entry.visible) TvPurple else TvTextMuted, fontSize = TvTextSize.Subtitle)
        }
        val upTarget = tvHomeRowMoveTargetId(entry.id, "up")
        val downTarget = tvHomeRowMoveTargetId(entry.id, "down")
        TvHomeMoveButton(Icons.Default.KeyboardArrowUp, labels.moveUp, upTarget, actions.onUp)
        TvHomeMoveButton(Icons.Default.KeyboardArrowDown, labels.moveDown, downTarget, actions.onDown)
    }
}

@Composable
private fun TvHomeMoveButton(
    icon: ImageVector,
    label: String,
    targetId: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .size(64.dp)
            .semantics { contentDescription = label }
            .tvFocusable(
                onClick = onClick,
                shape = TvShapes.Control,
                focusTargetId = targetId,
                fill = TvSurface,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TvText, modifier = Modifier.size(32.dp))
    }
}
