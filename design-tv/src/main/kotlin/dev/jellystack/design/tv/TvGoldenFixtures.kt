@file:Suppress("FunctionName", "MatchingDeclarationName", "MaxLineLength", "TooManyFunctions")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.jellystack.core.preferences.AppLanguage

/** Deterministic, credential-free TV states used by screenshot and contrast regression tests. */
enum class TvGoldenFixture {
    BROWSE,
    ALL_TITLES,
    SEARCH,
    LOADING,
    MISSING_ART,
    SEARCH_PARTIAL_ERROR,
    DETAIL,
    DISCOVER,
    FOCUS_CONTRAST,
}

@Composable
fun JellystackTvGoldenFixture(
    fixture: TvGoldenFixture,
    language: AppLanguage = AppLanguage.ENGLISH,
    darkArtwork: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val strings = TvStrings.current(language)
    JellystackTvTheme {
        Box(modifier.fillMaxSize().background(TvBackground)) {
            GoldenBackdrop(fixture)
            GoldenRail(strings)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 92.dp,
                        top = TvLayoutTokens.SafeInsets.vertical,
                        end = TvLayoutTokens.SafeInsets.horizontal,
                        bottom = TvLayoutTokens.SafeInsets.vertical,
                    ),
            ) {
                when (fixture) {
                    TvGoldenFixture.BROWSE -> GoldenBrowse(strings)
                    TvGoldenFixture.ALL_TITLES -> GoldenAllTitles(strings)
                    TvGoldenFixture.SEARCH -> GoldenSearch(strings, partialError = false)
                    TvGoldenFixture.LOADING -> GoldenLoading(strings)
                    TvGoldenFixture.MISSING_ART -> GoldenMissingArt(strings)
                    TvGoldenFixture.SEARCH_PARTIAL_ERROR -> GoldenSearch(strings, partialError = true)
                    TvGoldenFixture.DETAIL -> GoldenDetail(strings)
                    TvGoldenFixture.DISCOVER -> GoldenDiscover(strings)
                    TvGoldenFixture.FOCUS_CONTRAST -> GoldenFocusContrast(darkArtwork)
                }
            }
        }
    }
}

@Composable
private fun GoldenBackdrop(fixture: TvGoldenFixture) {
    val accent =
        when (fixture) {
            TvGoldenFixture.DETAIL -> Color(0xFF49316E)
            TvGoldenFixture.DISCOVER -> Color(0xFF173D45)
            TvGoldenFixture.ALL_TITLES, TvGoldenFixture.SEARCH -> Color(0xFF243047)
            else -> Color(0xFF251A43)
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.82f), TvBackground),
                    radius = 900f,
                ),
            ),
    )
}

@Composable
private fun GoldenRail(strings: TvStrings) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(TvLayoutTokens.CollapsedRailWidth)
            .background(Color.Black.copy(alpha = 0.54f))
            .padding(vertical = TvLayoutTokens.SafeInsets.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("J", color = TvPurple, fontSize = 26.sp, fontWeight = FontWeight.Black)
        listOf(strings.home, strings.library, strings.search, strings.discover, strings.settings).forEachIndexed { index, label ->
            Box(
                Modifier
                    .size(46.dp)
                    .background(if (index == 0) TvSurfaceRaised else Color.Transparent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.take(1), color = if (index == 0) TvPurple else TvTextMuted, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GoldenBrowse(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(strings.home, color = TvText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Continue watching", color = TvTextMuted, fontSize = 18.sp)
        GoldenCardRow(strings)
        Text(strings.recentlyAdded, color = TvText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        GoldenCardRow(strings, secondRow = true)
    }
}

@Composable
private fun GoldenAllTitles(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("${strings.library} · ${strings.allTitles}", color = TvText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvActionButton(strings.sort, {}, enabled = false)
            TvActionButton(strings.filters, {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(5) { index ->
                Column(Modifier.width(136.dp).background(TvSurface, RoundedCornerShape(14.dp))) {
                    Box(Modifier.fillMaxWidth().height(190.dp).background(Color(0xFF252638)))
                    Text("Title ${index + 1}", color = TvText, modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun GoldenLoading(strings: TvStrings) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(strings.library, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        TvStatusAnchor(strings.loading, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .width(TvLayoutTokens.LandscapeArtworkWidth)
                        .height(TvLayoutTokens.LandscapeArtworkHeight + TvLayoutTokens.LandscapeMetadataBandHeight)
                        .background(TvSurface, RoundedCornerShape(18.dp)),
                )
            }
        }
    }
}

@Composable
private fun GoldenMissingArt(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(strings.library, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Designed missing-art fallback", color = TvTextMuted, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
            TvMediaCard("Unknown movie", null, {}, subtitle = "2026", focusable = false)
            TvMediaCard("Untitled episode", null, {}, subtitle = "S01 E04", focusable = false)
            TvMediaCard("Audio library", null, {}, subtitle = strings.itemCount(42), focusable = false)
        }
    }
}

@Composable
private fun GoldenSearch(
    strings: TvStrings,
    partialError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(strings.search, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .fillMaxWidth(0.78f)
                .height(58.dp)
                .background(TvSurfaceRaised, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Dune", color = TvText, fontSize = 20.sp)
        }
        if (partialError) TvStatusAnchor(strings.seerrSearchFailed, Modifier.fillMaxWidth())
        GoldenCardRow(strings)
    }
}

@Composable
private fun GoldenDetail(strings: TvStrings) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Text("The Last Horizon", color = TvText, fontSize = 45.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text("2026  •  2h 14m  •  8.4", color = TvTextMuted, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "A small crew crosses a silent frontier and finds a signal that should not exist.",
            color = TvTextMuted,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvActionButton(strings.play, {}, primary = true)
            TvActionButton(strings.details, {})
        }
        Spacer(Modifier.height(18.dp))
        Text(strings.similar, color = TvText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        GoldenCardRow(strings, compact = true)
    }
}

@Composable
private fun GoldenDiscover(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(strings.discover, color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Trending this week", color = TvTextMuted, fontSize = 18.sp)
        GoldenCardRow(strings)
        TvStatusAnchor(strings.discoverLoadFailed, Modifier.fillMaxWidth())
    }
}

@Composable
private fun GoldenFocusContrast(darkArtwork: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("High-contrast focus", color = TvText, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Dual-tone focus remains visible on light and dark artwork.", color = TvTextMuted, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            GoldenFocusedCard(if (darkArtwork) Color.Black else Color.White, "Focused title")
            GoldenFocusedCard(if (darkArtwork) Color(0xFF0E2642) else Color(0xFFFFF2C4), "Selected title", selected = true)
        }
    }
}

@Composable
private fun GoldenFocusedCard(
    artworkColor: Color,
    title: String,
    selected: Boolean = false,
) {
    Column(
        Modifier
            .width(TvLayoutTokens.LandscapeArtworkWidth)
            .border(5.dp, TvLayoutTokens.FocusDarkRing, RoundedCornerShape(20.dp))
            .border(2.dp, TvLayoutTokens.FocusLightRing, RoundedCornerShape(18.dp))
            .border(1.dp, TvLayoutTokens.FocusAccentRing, RoundedCornerShape(16.dp))
            .background(TvSurface, RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TvLayoutTokens.LandscapeArtworkHeight)
                .background(artworkColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(TvLayoutTokens.LandscapeMetadataBandHeight)
                .background(Color(0xFF11121B))
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (selected) Box(Modifier.width(4.dp).height(30.dp).background(TvPurple, RoundedCornerShape(2.dp)))
            Text(title, color = TvText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GoldenCardRow(
    strings: TvStrings,
    secondRow: Boolean = false,
    compact: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
        val titles = if (secondRow) listOf("Northern Lights", "Atlas", "Afterglow") else listOf("Signal", "Europa", "Night Train")
        titles.take(if (compact) 2 else 3).forEachIndexed { index, title ->
            TvMediaCard(
                title = title,
                imageUrl = null,
                onClick = {},
                subtitle = if (index == 0) "72%" else strings.itemCount((index + 4).toLong()),
                focusable = false,
            )
        }
    }
}
