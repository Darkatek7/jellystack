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
import dev.jellystack.core.preferences.AppLanguage

/**
 * Deterministic, credential-free TV states for the screenshot tests in `design-tv-screenshots`.
 * Every fixture renders production composables with fictional data and no network artwork.
 */
enum class TvGoldenFixture {
    MEDIA_CARDS,
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
                TvGoldenFixture.MEDIA_CARDS -> GoldenMediaCards(strings)
            }
        }
    }
}

@Composable
private fun GoldenMediaCards(strings: TvStrings) {
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
}
