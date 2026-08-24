@file:Suppress("FunctionName")

package dev.jellystack.design.screenshots

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.design.tv.TvGoldenFixture

private const val TV_720P = "spec:width=960dp,height=540dp,dpi=213"
private const val TV_1080P = "spec:width=960dp,height=540dp,dpi=320"
private const val TV_4K = "spec:width=960dp,height=540dp,dpi=640"

@Preview(name = "720p", device = TV_720P)
@Preview(name = "1080p", device = TV_1080P)
@Preview(name = "4K", device = TV_4K)
private annotation class TvResolutionMatrix

@Composable
private fun fixture(
    fixture: TvGoldenFixture,
    language: AppLanguage = AppLanguage.ENGLISH,
    darkArtwork: Boolean = true,
) {
    val labels =
        if (language == AppLanguage.GERMAN) {
            listOf("Start", "Bibliothek", "Suche", "Entdecken", "Einstellungen")
        } else {
            listOf("Home", "Library", "Search", "Discover", "Settings")
        }
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF38245F), Color(0xFF080910)),
                        radius = 900f,
                    ),
                ),
        ) {
            TvFixtureRail(labels)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 92.dp, top = 27.dp, end = 48.dp, bottom = 27.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                TvFixtureContent(fixture, labels, darkArtwork)
            }
        }
    }
}

@Composable
private fun TvFixtureRail(labels: List<String>) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(top = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("J", color = Color(0xFFB9A2FF), fontSize = 26.sp, fontWeight = FontWeight.Black)
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .width(46.dp)
                    .height(46.dp)
                    .background(if (index == 0) Color(0xFF222334) else Color.Transparent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.take(1), color = if (index == 0) Color(0xFFB9A2FF) else Color(0xFFB8B4C6))
            }
        }
    }
}

@Composable
private fun TvFixtureContent(
    fixture: TvGoldenFixture,
    labels: List<String>,
    darkArtwork: Boolean,
) {
    val title =
        when (fixture) {
            TvGoldenFixture.BROWSE -> labels[0]
            TvGoldenFixture.LOADING, TvGoldenFixture.MISSING_ART -> labels[1]
            TvGoldenFixture.SEARCH_PARTIAL_ERROR -> labels[2]
            TvGoldenFixture.DETAIL -> "The Last Horizon"
            TvGoldenFixture.DISCOVER -> labels[3]
            TvGoldenFixture.FOCUS_CONTRAST -> "High-contrast focus"
        }
    Text(title, color = Color(0xFFF4F1FF), fontSize = 38.sp, fontWeight = FontWeight.Bold)
    when (fixture) {
        TvGoldenFixture.LOADING -> {
            TvFixtureStatus(if (labels[0] == "Start") "Wird geladen" else "Loading")
            TvFixtureCards(empty = false)
        }
        TvGoldenFixture.MISSING_ART -> {
            Text("Designed missing-art fallback", color = Color(0xFFB8B4C6), fontSize = 17.sp)
            TvFixtureCards(empty = true)
        }
        TvGoldenFixture.SEARCH_PARTIAL_ERROR -> {
            Box(
                Modifier
                    .fillMaxWidth(0.76f)
                    .height(58.dp)
                    .background(Color(0xFF222334), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Dune", color = Color.White, fontSize = 20.sp)
            }
            TvFixtureStatus("Seerr results could not be loaded")
            TvFixtureCards(empty = false)
        }
        TvGoldenFixture.DETAIL -> {
            Spacer(Modifier.height(78.dp))
            Text("2026  •  2h 14m  •  8.4", color = Color(0xFFB8B4C6), fontSize = 17.sp)
            Text(
                "A small crew crosses a silent frontier and finds a signal that should not exist.",
                color = Color(0xFFB8B4C6),
                fontSize = 18.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvFixtureAction("Play", primary = true)
                TvFixtureAction("Details", primary = false)
            }
            Text("Similar", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            TvFixtureCards(empty = false, count = 2)
        }
        TvGoldenFixture.DISCOVER -> {
            Text("Trending this week", color = Color(0xFFB8B4C6), fontSize = 18.sp)
            TvFixtureCards(empty = false)
            TvFixtureStatus("Some rows could not be loaded")
        }
        TvGoldenFixture.FOCUS_CONTRAST -> {
            Text("Dual-tone focus on light and dark artwork", color = Color(0xFFB8B4C6), fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                TvFixtureCard("Focused title", if (darkArtwork) Color.Black else Color.White, focused = true)
                TvFixtureCard(
                    "Selected title",
                    if (darkArtwork) Color(0xFF0E2642) else Color(0xFFFFF2C4),
                    focused = true,
                    selected = true,
                )
            }
        }
        TvGoldenFixture.BROWSE -> {
            Text(if (labels[0] == "Start") "Weiterschauen" else "Continue watching", color = Color(0xFFB8B4C6))
            TvFixtureCards(empty = false)
            Text(if (labels[0] == "Start") "Kürzlich hinzugefügt" else "Recently added", color = Color.White, fontSize = 21.sp)
            TvFixtureCards(empty = false)
        }
    }
}

@Composable
private fun TvFixtureCards(
    empty: Boolean,
    count: Int = 3,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { index ->
            TvFixtureCard(
                title = listOf("Signal", "Europa", "Night Train")[index],
                artwork = if (empty) Color(0xFF252638) else listOf(Color(0xFF394B79), Color(0xFF6A3D55), Color(0xFF31594D))[index],
            )
        }
    }
}

@Composable
private fun TvFixtureCard(
    title: String,
    artwork: Color,
    focused: Boolean = false,
    selected: Boolean = false,
) {
    val focusModifier =
        if (focused) {
            Modifier
                .border(5.dp, Color.Black, RoundedCornerShape(20.dp))
                .border(2.dp, Color.White, RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFFB9A2FF), RoundedCornerShape(16.dp))
        } else {
            Modifier
        }
    Column(
        Modifier
            .width(232.dp)
            .then(focusModifier)
            .background(Color(0xFF11121B), RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(131.dp)
                .background(
                    if (artwork == Color(0xFF252638)) {
                        Brush.linearGradient(listOf(Color(0xFF292A3D), Color(0xFF151622), Color(0xFF30234A)))
                    } else {
                        Brush.linearGradient(listOf(artwork, artwork.copy(alpha = 0.55f)))
                    },
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork == Color(0xFF252638)) Text("◇", color = Color(0xFFB8B4C6), fontSize = 34.sp)
        }
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (selected) Box(Modifier.width(4.dp).height(30.dp).background(Color(0xFFB9A2FF)))
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TvFixtureStatus(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF222334), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Color(0xFFB8B4C6), fontSize = 16.sp)
    }
}

@Composable
private fun TvFixtureAction(
    label: String,
    primary: Boolean,
) {
    Box(
        Modifier
            .height(58.dp)
            .background(if (primary) Color(0xFFB9A2FF) else Color(0xFF222334), RoundedCornerShape(29.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) Color(0xFF251450) else Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@PreviewTest @TvResolutionMatrix @Composable
fun TvBrowseMatrix() = fixture(TvGoldenFixture.BROWSE)

@PreviewTest
@Preview(name = "Loading 1080p", device = TV_1080P)
@Composable
fun TvLoading() = fixture(TvGoldenFixture.LOADING)

@PreviewTest
@Preview(name = "Missing art 1080p", device = TV_1080P)
@Composable
fun TvMissingArt() = fixture(TvGoldenFixture.MISSING_ART)

@PreviewTest
@Preview(name = "Partial error 1080p", device = TV_1080P)
@Composable
fun TvSearchPartialError() = fixture(TvGoldenFixture.SEARCH_PARTIAL_ERROR)

@PreviewTest
@Preview(name = "Detail 1080p", device = TV_1080P)
@Composable
fun TvDetail() = fixture(TvGoldenFixture.DETAIL)

@PreviewTest
@Preview(name = "Discover 1080p", device = TV_1080P)
@Composable
fun TvDiscover() = fixture(TvGoldenFixture.DISCOVER)

@PreviewTest
@Preview(name = "German 150% 1080p", device = TV_1080P, locale = "de", fontScale = 1.5f)
@Composable
fun TvGermanLarge() = fixture(TvGoldenFixture.BROWSE, AppLanguage.GERMAN)

@PreviewTest
@Preview(name = "White art focus 1080p", device = TV_1080P)
@Composable
fun TvWhiteArtworkFocus() = fixture(TvGoldenFixture.FOCUS_CONTRAST, darkArtwork = false)

@PreviewTest
@Preview(name = "Black art focus 1080p", device = TV_1080P)
@Composable
fun TvBlackArtworkFocus() = fixture(TvGoldenFixture.FOCUS_CONTRAST, darkArtwork = true)
