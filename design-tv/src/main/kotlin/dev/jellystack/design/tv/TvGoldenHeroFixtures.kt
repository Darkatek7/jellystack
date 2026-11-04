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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jellystack.core.preferences.HomeRowPreferences

// Hero states for TvGoldenFixture.HOME_HERO and DETAIL_HERO: fictional titles, no network artwork, no trailer.

/**
 * The top of the home screen: backdrop, hero, clock and the first row. The artwork is a bright stand-in,
 * so the screenshot shows whether hero text and row titles stay readable on light images.
 */
@Composable
internal fun GoldenHomeHero(
    strings: TvStrings,
    modifier: Modifier,
    showKeyHints: Boolean,
    pageIndex: Int,
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
            page = TvHomeHeroPage(index = pageIndex, count = 10),
            showCarouselContext = true,
            imageBaseUrl = null,
            imageAccessToken = null,
            showKeyHints = false,
        )
    val trailer =
        TvHomeHeroTrailer(
            state = TvTrailerPreviewState.Idle,
            soundEnabled = false,
            progress = remember { mutableFloatStateOf(0f) },
            video = TvTrailerVideo(surface = {}, firstFrameShown = remember { mutableStateOf(false) }),
            homeUiAlpha = remember { mutableFloatStateOf(1f) },
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
            // The screenshot has no focus, so the hint row is placed where the focused spotlight draws it.
            if (showKeyHints) {
                Box(Modifier.fillMaxWidth().height(TV_HOME_SPOTLIGHT_HEIGHT)) {
                    TvPlayKeyHints(
                        playLabel = strings.play,
                        detailsLabel = strings.details,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = TvLayoutTokens.ContentStart, bottom = 26.dp),
                    )
                }
            }
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

/** Settings > Home: Continue watching hidden, the rest in a custom order. */
@Composable
internal fun GoldenHomeSettings(
    strings: TvStrings,
    modifier: Modifier,
) {
    val rows =
        listOf(
            TvHomeRowOption("continue", strings.continueWatching),
            TvHomeRowOption("next", strings.nextUp),
            TvHomeRowOption("plugin:recent-movies", "Recently added movies"),
            TvHomeRowOption("plugin:recent-shows", "Recently added shows"),
        )
    Column(
        modifier.fillMaxSize().background(TvBackground).padding(TvScreenPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        androidx.tv.material3.Text(
            strings.homeSettings.categoryTitle,
            color = TvText,
            fontSize = TvTextSize.Headline,
            fontWeight = FontWeight.Bold,
        )
        TvHomeSettings(
            options = rows,
            preferences =
                HomeRowPreferences(
                    order = listOf("next", "plugin:recent-movies", "plugin:recent-shows", "continue"),
                    hidden = setOf("continue"),
                ),
            strings = strings,
            onChange = {},
        )
    }
}
