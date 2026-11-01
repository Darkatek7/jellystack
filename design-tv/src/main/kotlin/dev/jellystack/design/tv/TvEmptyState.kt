@file:Suppress("FunctionName", "FunctionNaming", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** The one action of an empty state; [focusTargetId] is the target focus is restored to. */
internal data class TvEmptyStateAction(
    val label: String,
    val focusTargetId: String,
    val onClick: () -> Unit,
)

/**
 * What a list or grid shows when it has nothing to show: an icon, what is missing, a hint on what to do about it, and
 * optionally one action. With an [action], focus starts on it, since nothing else on the screen can hold focus. Without
 * one the text is only announced and the focus stays where it was, as with a search field above.
 */
@Composable
internal fun TvEmptyState(
    icon: ImageVector,
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    action: TvEmptyStateAction? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier.tvStatusSemantics(listOfNotNull(title, hint).joinToString(". ")),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = TvTextMuted, modifier = Modifier.size(44.dp))
            Text(
                title,
                color = TvText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            hint?.let {
                Text(
                    it,
                    color = TvTextMuted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
        action?.let {
            TvActionButton(
                label = it.label,
                onClick = it.onClick,
                primary = true,
                focusToNavigationRailOnLeft = true,
                focusTargetId = it.focusTargetId,
                modifier = Modifier.tvScreenEntryFocus(focusTargetId = it.focusTargetId),
            )
        }
    }
}
