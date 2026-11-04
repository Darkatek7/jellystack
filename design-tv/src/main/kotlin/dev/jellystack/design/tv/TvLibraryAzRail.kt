@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/**
 * The letters at the right edge of a name-sorted grid. D-pad right from the last column moves onto a letter and OK
 * jumps to the first title at or after it. Each letter draws its own focus, a filled chip, because the system ring
 * is wider than the letter.
 */
@Composable
internal fun TvLibraryAzRail(
    strings: TvLibraryStrings,
    onJump: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(TV_LIBRARY_RAIL_WIDTH_DP.dp).fillMaxHeight().testTag("tv-library-az-rail"),
    ) {
        TV_LIBRARY_JUMP_LETTERS.forEach { letter ->
            TvLibraryAzLetter(
                letter = letter,
                description = strings.jumpToLetter(letter),
                onClick = { onJump(letter) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TvLibraryAzLetter(
    letter: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = description }
                .tvFocusable(
                    onClick = onClick,
                    shape = shape,
                    scale = 1f,
                    focusIndication = TvFocusIndication.NONE,
                    onFocusChanged = { focused = it },
                ).background(if (focused) TvPurple else androidx.compose.ui.graphics.Color.Transparent, shape)
                .testTag("tv-library-az-$letter"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = if (focused) TvBackground else TvTextMuted,
            // Fixed size: the cells are as tall as the screen allows, a larger system font would overlap them.
            fontSize = with(LocalDensity.current) { TV_LIBRARY_RAIL_LETTER_DP.dp.toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private const val TV_LIBRARY_RAIL_LETTER_DP = 12
