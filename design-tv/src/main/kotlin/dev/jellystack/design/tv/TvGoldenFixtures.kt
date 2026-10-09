@file:Suppress("FunctionName", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage

/**
 * Deterministic, credential-free TV states for the screenshot tests in `design-tv-screenshots`.
 * Every fixture renders production composables with fictional data and no network artwork.
 */
enum class TvGoldenFixture {
    LANDSCAPE_CARDS,
    PORTRAIT_CARDS,
}

/** Screenshot-test entry point; production code never calls it. */
@Composable
fun JellystackTvGoldenFixture(
    fixture: TvGoldenFixture,
    language: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier,
) {
    val strings = TvStrings.current(language)
    JellystackTvTheme {
        Column(
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(horizontal = TvLayoutTokens.SafeInsets.horizontal, vertical = TvLayoutTokens.SafeInsets.vertical),
        ) {
            when (fixture) {
                TvGoldenFixture.LANDSCAPE_CARDS -> GoldenLandscapeCards(strings)
                TvGoldenFixture.PORTRAIT_CARDS -> GoldenPortraitCards()
            }
        }
    }
}

@Composable
private fun GoldenLandscapeCards(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
            TvMediaCard(title = "The Last Horizon", imageUrl = null, onClick = {}, subtitle = "2024")
            TvMediaCard(title = "Northern Lights", imageUrl = null, onClick = {}, subtitle = "2021", selected = true)
            TvMediaCard(
                title = "A Remarkably Long Title That Cannot Possibly Fit On One Line",
                imageUrl = null,
                onClick = {},
                subtitle = "A subtitle that is also far too long for the metadata band",
            )
            TvMediaCard(title = strings.home, imageUrl = null, onClick = null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
            GOLDEN_PROGRESS_ITEMS.forEach { item ->
                val text = item.tvCardText(strings)
                TvMediaCard(title = text.title, subtitle = text.subtitle, progress = text.progress, imageUrl = null, onClick = {})
            }
        }
    }
}

@Composable
private fun GoldenPortraitCards() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing),
        verticalAlignment = Alignment.Top,
    ) {
        TvMediaCard(title = "Paper Moon", imageUrl = null, onClick = {}, subtitle = "1973", format = TvMediaCardFormat.POSTER)
        TvMediaCard(
            title = "Alex Example",
            imageUrl = null,
            onClick = {},
            subtitle = "Detective / Narrator / Older Self",
            format = TvMediaCardFormat.CAST_PORTRAIT,
        )
        TvMediaCard(
            title = "Maximiliane Beispielname-Langform",
            imageUrl = null,
            onClick = {},
            subtitle = "Sam",
            format = TvMediaCardFormat.CAST_PORTRAIT,
        )
    }
}

private const val GOLDEN_TICKS_PER_MINUTE = 600_000_000L

private val GOLDEN_PROGRESS_ITEMS =
    listOf(
        goldenItem("Episode", "The Quiet Signal", seriesName = "Harbor Lights", season = 2, episode = 6, positionMinutes = 26),
        goldenItem("Episode", "Northbound", seriesName = "Harbor Lights", season = 2, episode = 7),
        goldenItem("Movie", "Paper Moon", year = 1973, positionMinutes = 82),
    )

private fun goldenItem(
    type: String,
    name: String,
    seriesName: String? = null,
    season: Int? = null,
    episode: Int? = null,
    year: Int? = null,
    positionMinutes: Long? = null,
): JellyfinItem =
    JellyfinItem(
        id = name,
        libraryId = null,
        name = name,
        sortName = null,
        overview = null,
        type = type,
        mediaType = "Video",
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = null,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = 100 * GOLDEN_TICKS_PER_MINUTE,
        positionTicks = positionMinutes?.times(GOLDEN_TICKS_PER_MINUTE),
        playedPercentage = null,
        productionYear = year,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = episode,
        parentIndexNumber = season,
        seriesName = seriesName,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )
