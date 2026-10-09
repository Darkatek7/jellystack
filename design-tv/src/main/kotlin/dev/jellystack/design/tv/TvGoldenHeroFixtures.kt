@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinChannelLayout
import dev.jellystack.core.jellyfin.JellyfinDynamicRange
import dev.jellystack.core.jellyfin.JellyfinImmersiveAudio
import dev.jellystack.core.jellyfin.JellyfinMediaFeatures
import dev.jellystack.core.jellyfin.JellyfinVideoResolution

// Hero states for TvGoldenFixture.HOME_HERO and DETAIL_HERO: fictional titles, no network artwork, no trailer.

/**
 * The top of the home screen: backdrop, hero, clock and the first row. The artwork is a bright stand-in,
 * so the screenshot shows whether hero text and row titles stay readable on light images.
 */
@Composable
internal fun GoldenHomeHero(
    strings: TvStrings,
    modifier: Modifier,
) {
    val item =
        goldenItem("Movie", "The Last Horizon").copy(
            overview = "A lighthouse keeper picks up a signal from a ship that sank forty years ago.",
            productionYear = 2024,
            communityRating = 7.8,
            officialRating = "PG-13",
        )
    val model =
        TvHomeHeroModel(
            stageItem = item,
            stagePresentationId = null,
            mode = TvHomeHeroMode.RECENT,
            page = TvHomeHeroPage(index = 1, count = 6),
            showCarouselContext = true,
            imageBaseUrl = null,
            imageAccessToken = null,
        )
    val trailer =
        TvHomeHeroTrailer(
            state = TvTrailerPreviewState.Idle,
            soundEnabled = false,
            progress = remember { mutableFloatStateOf(0f) },
            surface = {},
        )
    Box(modifier.fillMaxSize().background(TvBackground)) {
        TvHomeBackdrop(model, trailer, image = { GoldenBrightArtwork() })
        Box(Modifier.fillMaxWidth()) {
            TvHeroCarousel(
                model = model,
                trailer = trailer,
                callbacks =
                    TvHomeHeroCallbacks(
                        onPlay = {},
                        onDetails = {},
                        onVerticalMove = {},
                        onHeroFocused = {},
                        onCarouselMove = {},
                    ),
                strings = strings,
                primaryFocusRequester = remember { FocusRequester() },
            )
        }
        TvHomeClock("20:41", Modifier.align(Alignment.TopEnd))
        Column(
            Modifier.padding(start = TvLayoutTokens.ContentStart, top = TV_CINEMATIC_ROWS_TOP),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvSectionTitle(strings.continueWatching)
            Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
                listOf("Harbor Lights", "Northern Lights", "Paper Moon", "Quiet Signal").forEach { title ->
                    TvMediaCard(title = title, imageUrl = null, onClick = {})
                }
            }
        }
    }
}

/** Light, high-contrast stand-in artwork: the worst case for text over a backdrop. */
@Composable
private fun GoldenBrightArtwork() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF5EFE0), Color(0xFFF2C14E), Color(0xFF8AB6D6), Color(0xFFFFFFFF)),
                ),
            ),
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
