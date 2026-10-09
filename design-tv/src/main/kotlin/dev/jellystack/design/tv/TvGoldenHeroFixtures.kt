@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinChannelLayout
import dev.jellystack.core.jellyfin.JellyfinDynamicRange
import dev.jellystack.core.jellyfin.JellyfinImmersiveAudio
import dev.jellystack.core.jellyfin.JellyfinMediaFeatures
import dev.jellystack.core.jellyfin.JellyfinVideoResolution

// Hero states for TvGoldenFixture.HOME_HERO and DETAIL_HERO: fictional titles, no artwork, no trailer.

@Composable
internal fun GoldenHomeHero(strings: TvStrings) {
    val item =
        goldenItem("Movie", "The Last Horizon").copy(
            overview = "A lighthouse keeper picks up a signal from a ship that sank forty years ago.",
            productionYear = 2024,
            communityRating = 7.8,
            officialRating = "PG-13",
        )
    TvHeroCarousel(
        model =
            TvHomeHeroModel(
                stageItem = item,
                stagePresentationId = null,
                mode = TvHomeHeroMode.RECENT,
                page = TvHomeHeroPage(index = 1, count = 6),
                showCarouselContext = true,
                imageBaseUrl = null,
                imageAccessToken = null,
            ),
        trailer =
            TvHomeHeroTrailer(
                state = TvTrailerPreviewState.Idle,
                soundEnabled = false,
                progress = remember { mutableFloatStateOf(0f) },
                surface = {},
            ),
        callbacks =
            TvHomeHeroCallbacks(
                onPlay = {},
                onDetails = {},
                onActionVerticalMove = {},
                onHeroFocused = {},
                onCarouselMove = {},
                onIndicatorVerticalMove = {},
            ),
        strings = strings,
        primaryFocusRequester = remember { FocusRequester() },
    )
}

@Composable
internal fun GoldenDetailHero(
    strings: TvStrings,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().background(TvBackground)) {
        Box(Modifier.fillMaxWidth().height(TV_DETAIL_HERO_HEIGHT_DP.dp)) {
            TvDetailHero(
                TvDetailHeroModel(
                    title = "The Last Horizon",
                    backdropUrl = null,
                    logoUrl = null,
                    titleColor = TvText,
                    subtitle = "Drama  •  Mystery  •  Thriller",
                    badges =
                        JellyfinMediaFeatures(
                            resolution = JellyfinVideoResolution.UHD_4K,
                            dynamicRange = JellyfinDynamicRange.DOLBY_VISION,
                            immersiveAudio = JellyfinImmersiveAudio.DOLBY_ATMOS,
                            surround = JellyfinChannelLayout.SURROUND_7_1,
                            hearingImpairedSubtitles = true,
                        ).badgeLabels(strings),
                ),
            ) {
                TvDetailHeroActions(
                    state =
                        TvDetailHeroActionsState(
                            resume = true,
                            favorite = true,
                            played = false,
                            trailerError = false,
                        ),
                    callbacks =
                        TvDetailHeroActionCallbacks(
                            onPlay = {},
                            onToggleFavorite = {},
                            onTogglePlayed = {},
                            onTrailer = {},
                        ),
                    strings = strings,
                    primaryActionModifier = Modifier,
                    actionRowModifier = Modifier,
                )
            }
        }
    }
}
