package dev.jellystack.design.theme

import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.players.PlaybackMode
import dev.jellystack.players.PlaybackQualityOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceBackedCopyTest {
    @Test
    fun recommendationRailsExposeSemanticKeysInsteadOfEnglishTitles() {
        val state =
            JellyseerrRecommendationRailState(
                rail = JellyseerrRecommendationRail.TRENDS,
                items = emptyList(),
                isLoading = false,
                errorMessage = null,
                canLoadMore = false,
                nextPage = 1,
                lastUpdated = null,
                isStale = false,
            )

        assertEquals(JellyseerrRecommendationRail.TRENDS, state.rail)
        assertTrue(JellyseerrRecommendationRail.entries.all { it.name == it.name.uppercase() })
    }

    @Test
    fun playerQualityUsesModeAndFlagsForLocalizedUiCopy() {
        val option =
            PlaybackQualityOption(
                id = PlaybackQualityOption.AUTO_ID,
                label = "",
                mode = PlaybackMode.HLS,
                sourceId = null,
                maxBitrate = null,
                isAuto = true,
            )

        assertTrue(option.isAuto)
        assertEquals(PlaybackMode.HLS, option.mode)
        assertTrue(option.label.isEmpty())
    }
}
