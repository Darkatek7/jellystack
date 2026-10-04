package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvSubtitlePaddingPolicyTest {
    @Test
    fun hiddenControlsWithoutStandaloneActionsUseNormalPadding() {
        assertEquals(
            0.08f,
            tvSubtitleBottomPaddingFraction(
                controlsVisible = false,
                standaloneActionsVisible = false,
                panelOpen = false,
            ),
        )
    }

    @Test
    fun standaloneActionsRaiseSubtitlesAboveThePrompt() {
        assertEquals(
            0.20f,
            tvSubtitleBottomPaddingFraction(
                controlsVisible = false,
                standaloneActionsVisible = true,
                panelOpen = false,
            ),
        )
    }

    @Test
    fun visibleControlsUseTheHighestPadding() {
        assertEquals(
            0.38f,
            tvSubtitleBottomPaddingFraction(
                controlsVisible = true,
                standaloneActionsVisible = true,
                panelOpen = false,
            ),
        )
    }

    @Test
    fun optionsPanelWinsWhileControlsAreHidden() {
        assertEquals(
            0.38f,
            tvSubtitleBottomPaddingFraction(
                controlsVisible = false,
                standaloneActionsVisible = true,
                panelOpen = true,
            ),
        )
    }

    @Test
    fun everyPresentationCombinationReturnsAValidMedia3Fraction() {
        listOf(false, true).forEach { controlsVisible ->
            listOf(false, true).forEach { standaloneActionsVisible ->
                listOf(false, true).forEach { panelOpen ->
                    assertTrue(
                        tvSubtitleBottomPaddingFraction(
                            controlsVisible = controlsVisible,
                            standaloneActionsVisible = standaloneActionsVisible,
                            panelOpen = panelOpen,
                        ) in 0f..1f,
                    )
                }
            }
        }
    }
}
