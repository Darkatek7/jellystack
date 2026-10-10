@file:Suppress("FunctionName", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.preferences.AppLanguage
import dev.jellystack.players.PlaybackChapter
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentType
import dev.jellystack.players.playbackTimelineMarkers

/**
 * Deterministic, credential-free TV states for the screenshot tests in `design-tv-screenshots`.
 * Every fixture renders production composables with fictional data and no network artwork.
 */
enum class TvGoldenFixture {
    LANDSCAPE_CARDS,
    PORTRAIT_CARDS,
    PLAYER_CONTROLS,
    PLAYER_SCRUB,
    PLAYER_UP_NEXT,
    HOME_HERO,
    DETAIL_HERO,
}

/** Screenshot-test entry point; production code never calls it. */
@Composable
fun JellystackTvGoldenFixture(
    fixture: TvGoldenFixture,
    language: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier,
) {
    val strings = TvStrings.current(language)
    if (fixture in GOLDEN_PLAYER_FIXTURES) {
        JellystackTvTheme { GoldenPlayer(strings, fixture, modifier) }
        return
    }
    if (fixture == TvGoldenFixture.HOME_HERO || fixture == TvGoldenFixture.DETAIL_HERO) {
        JellystackTvTheme {
            if (fixture == TvGoldenFixture.HOME_HERO) {
                GoldenHomeHero(strings, modifier)
            } else {
                GoldenDetailHero(strings, modifier)
            }
        }
        return
    }
    JellystackTvTheme {
        Column(
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(
                    horizontal = TvLayoutTokens.SafeInsets.horizontal,
                    vertical = TvLayoutTokens.SafeInsets.vertical,
                ),
        ) {
            when (fixture) {
                TvGoldenFixture.LANDSCAPE_CARDS -> GoldenLandscapeCards(strings)
                TvGoldenFixture.PORTRAIT_CARDS -> GoldenPortraitCards()
                TvGoldenFixture.HOME_HERO,
                TvGoldenFixture.PLAYER_CONTROLS,
                TvGoldenFixture.PLAYER_SCRUB,
                TvGoldenFixture.PLAYER_UP_NEXT,
                TvGoldenFixture.DETAIL_HERO,
                -> Unit
            }
        }
    }
}

@Composable
private fun GoldenLandscapeCards(strings: TvStrings) {
    Column(verticalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TvLayoutTokens.CardSpacing)) {
            TvMediaCard(title = "The Last Horizon", imageUrl = null, onClick = {}, subtitle = "2024")
            TvMediaCard(
                title = "Northern Lights",
                imageUrl = null,
                onClick = {},
                subtitle = "2021",
                selected = true,
                watched = true,
            )
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
                TvMediaCard(
                    title = text.title,
                    subtitle = text.subtitle,
                    progress = text.progress,
                    imageUrl = null,
                    onClick = {},
                )
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
        TvMediaCard(
            title = "Paper Moon",
            imageUrl = null,
            onClick = {},
            subtitle = "1973",
            format = TvMediaCardFormat.POSTER,
        )
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
        goldenEpisode("The Quiet Signal", number = 6).copy(positionTicks = 26 * GOLDEN_TICKS_PER_MINUTE),
        goldenEpisode("Northbound", number = 7),
        goldenItem("Movie", "Paper Moon").copy(productionYear = 1973, positionTicks = 82 * GOLDEN_TICKS_PER_MINUTE),
    )

private fun goldenEpisode(
    name: String,
    number: Int,
) = goldenItem("Episode", name).copy(seriesName = "Harbor Lights", parentIndexNumber = 2, indexNumber = number)

internal fun goldenItem(
    type: String,
    name: String,
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
        positionTicks = null,
        playedPercentage = null,
        productionYear = null,
        premiereDate = null,
        communityRating = null,
        officialRating = null,
        indexNumber = null,
        parentIndexNumber = null,
        seriesName = null,
        seasonId = null,
        episodeTitle = null,
        lastPlayed = null,
    )

@Composable
private fun GoldenPlayer(
    strings: TvStrings,
    fixture: TvGoldenFixture,
    modifier: Modifier,
) {
    val scrubbing = fixture == TvGoldenFixture.PLAYER_SCRUB
    val upNext = fixture == TvGoldenFixture.PLAYER_UP_NEXT
    val duration = 49 * 60_000L
    val chapters =
        listOf(0L, 9L, 21L, 38L).mapIndexed { index, minute ->
            PlaybackChapter(
                index = index,
                name = "Chapter ${index + 1}",
                startPositionMs = minute * 60_000L,
                imageTag = null,
            )
        }
    val segments =
        listOf(
            PlaybackSegment("intro", PlaybackSegmentType.INTRO, 60_000L, 150_000L),
            PlaybackSegment("credits", PlaybackSegmentType.OUTRO, 46 * 60_000L, duration),
        )
    val model =
        TvPlayerOsdModel(
            positionMs = if (upNext) 46 * 60_000L + 20_000L else 754_000L,
            durationMs = duration,
            isPaused = false,
            markers = playbackTimelineMarkers(duration, chapters, segments),
            chapters = chapters,
            audioLabel = "English 5.1",
            subtitleLabel = "English",
            canShowEpisodes = true,
            canPlayNext = true,
            promptActions = if (upNext) goldenUpNextActions(strings) else goldenSkipIntroActions(strings),
        )
    Box(modifier.fillMaxSize().background(Color(0xFF1E2A36))) {
        if (scrubbing) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(start = 42.dp, end = 42.dp, top = 48.dp, bottom = 40.dp),
            ) {
                TvPlayerTimelineBody(
                    model = model,
                    displayPositionMs = 23 * 60_000L,
                    active = true,
                    thumbnail = { GoldenTrickplayFrame() },
                    strings = strings,
                )
            }
            return@Box
        }
        TvPlayerHeader(
            primaryTitle = "Harbor Lights",
            secondaryTitle = "S2 · E6 · The Quiet Signal",
            backDescription = strings.back,
            onBack = {},
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 36.dp, end = 36.dp, top = 24.dp),
            clock = TvPlayerClock(time = "20:41", endsAt = strings.player.endsAt.format("21:17")),
        )
        TvPlayerControls(
            model = model,
            actions =
                TvPlayerOsdActions(onSeekTo = {}, onTogglePlayPause = {}, onAudio = {}, onSubtitles = {}, onMore = {}),
            strings = strings,
            interaction =
                TvPlayerOsdInteraction(
                    controlsFocus = remember { FocusRequester() },
                    promptEntryFocus = remember { FocusRequester() },
                    scrub = remember { TvTimelineScrub(liveSeeks = false, nowMs = { 0L }) },
                    thumbnail = null,
                ),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Stand-in for a trickplay tile so the preview position is visible without network artwork. */
@Composable
private fun GoldenTrickplayFrame() {
    Box(
        Modifier
            .size(width = TRICKPLAY_WIDTH_DP.dp, height = (TRICKPLAY_WIDTH_DP * 9 / 16).dp)
            .background(Color(0xFF4A5A6E), TvShapes.Badge),
    )
}

private val GOLDEN_PLAYER_FIXTURES =
    setOf(TvGoldenFixture.PLAYER_CONTROLS, TvGoldenFixture.PLAYER_SCRUB, TvGoldenFixture.PLAYER_UP_NEXT)

private fun goldenSkipIntroActions(strings: TvStrings) =
    listOf(
        TvPlaybackActionModel(
            id = "golden-skip-intro",
            kind = TvPlaybackActionKind.SEGMENT_SKIP,
            label = strings.player.skipIntro,
        ),
    )

private fun goldenUpNextActions(strings: TvStrings) =
    listOf(
        TvPlaybackActionModel(
            id = "golden-play-next",
            kind = TvPlaybackActionKind.PLAY_NEXT,
            label = strings.player.playNextEpisode,
            kicker = strings.player.upNext,
            detail = "S2 E7 · Northbound",
        ),
        TvPlaybackActionModel(
            id = "golden-watch-credits",
            kind = TvPlaybackActionKind.WATCH_CREDITS,
            label = strings.player.watchCredits,
        ),
    )
