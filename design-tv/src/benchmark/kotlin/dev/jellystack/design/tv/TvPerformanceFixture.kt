@file:Suppress("FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import dev.jellystack.core.preferences.MotionPreference

/** Credential-free cinematic surface used by the external TV frame-time gate. */
@Composable
fun JellystackTvPerformanceFixture() {
    val rows =
        remember {
            List(3) { rowIndex ->
                TvCinematicRow(
                    id = "benchmark-$rowIndex",
                    title = "Benchmark row ${rowIndex + 1}",
                    cards =
                        List(8) { cardIndex ->
                            TvCinematicCard(
                                id = "item-$rowIndex-$cardIndex",
                                title = "Title ${rowIndex * 8 + cardIndex + 1}",
                                subtitle = "2026",
                            )
                        },
                )
            }
        }
    var focusedAnchor by remember {
        mutableStateOf(
            TvFocusAnchor(
                rows.first().id,
                rows
                    .first()
                    .cards
                    .first()
                    .id,
                TvFocusDestination.SECTION_ITEM,
            ),
        )
    }
    var activationCount by remember { mutableIntStateOf(0) }
    val state =
        TvCinematicBrowseState(
            hero = TvCinematicHero(title = "Cinematic traversal"),
            rows = rows,
            focusedAnchor = focusedAnchor,
        )
    JellystackTvTheme(motionPreference = MotionPreference.REDUCED, highContrastFocus = true) {
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    testTagsAsResourceId = true
                    contentDescription = "activations=$activationCount;queuedBackdrops=0"
                }.testTag("cinematic-benchmark-root"),
        ) {
            TvCinematicBrowse(
                state = state,
                actionLabels =
                    TvSelectedItemActionLabels(
                        play = "Play",
                        resume = "Resume",
                        details = "Details",
                        addToList = "Add to My List",
                        removeFromList = "Remove from My List",
                        markPlayed = "Mark played",
                        markUnplayed = "Mark unplayed",
                    ),
                onCardFocused = { anchor, _ -> focusedAnchor = anchor },
                onCardClick = { activationCount += 1 },
                selectedItemActions = null,
            )
        }
    }
}
