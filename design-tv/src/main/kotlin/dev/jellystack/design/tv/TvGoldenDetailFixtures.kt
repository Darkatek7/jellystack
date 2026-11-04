@file:Suppress("FunctionName", "LongMethod", "LongParameterList", "TooManyFunctions", "MaxLineLength")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import dev.jellystack.core.jellyfin.JellyfinChannelLayout
import dev.jellystack.core.jellyfin.JellyfinDynamicRange
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinImmersiveAudio
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaFeatures
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyfin.JellyfinVideoResolution
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrPerson

// Detail screens for the screenshot tests: fictional titles, no network artwork, the production sections and hero.

internal val GOLDEN_DETAIL_FIXTURES =
    setOf(
        TvGoldenFixture.DETAIL_HERO,
        TvGoldenFixture.DETAIL_SERIES,
        TvGoldenFixture.DETAIL_EPISODE,
        TvGoldenFixture.DETAIL_SEERR,
    )

private const val TICKS_PER_MINUTE = 600_000_000L

@Composable
internal fun GoldenDetailScreen(
    fixture: TvGoldenFixture,
    strings: TvStrings,
    modifier: Modifier,
) {
    when (fixture) {
        TvGoldenFixture.DETAIL_SERIES -> GoldenSeriesDetail(strings, modifier)
        TvGoldenFixture.DETAIL_EPISODE -> GoldenEpisodeDetail(strings, modifier)
        TvGoldenFixture.DETAIL_SEERR -> GoldenSeerrDetail(strings, modifier)
        else -> GoldenMovieDetail(strings, modifier)
    }
}

/** Focus modifiers that do nothing, for sections shown without the focus layout. */
@Composable
private fun goldenSectionModifiers(uiState: TvDetailUiState): Map<String, TvDetailSectionFocusModifiers> =
    uiState.sections.filter { it.itemIds.isNotEmpty() }.associate { section ->
        section.id to
            key(section.id) {
                TvDetailSectionFocusModifiers(Modifier, emptyMap(), emptyMap(), rememberLazyListState())
            }
    }

@Composable
private fun GoldenDetailPage(
    heroHeightDp: Int,
    modifier: Modifier,
    hero: @Composable BoxScope.() -> Unit,
    sections: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().background(TvBackground),
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item { Box(Modifier.fillMaxWidth().heightIn(min = heroHeightDp.dp), content = hero) }
        sections()
    }
}

private fun goldenEndsAt(strings: TvStrings): String =
    strings.player.endsAt.format(if (strings.locale.language == "de") "22:41" else "10:41 PM")

private fun goldenStream(
    type: JellyfinMediaStreamType,
    index: Int,
    language: String?,
    codec: String,
    isDefault: Boolean = false,
    channels: Int? = null,
    height: Int? = null,
) = JellyfinMediaStream(
    type = type,
    index = index,
    displayTitle = null,
    codec = codec,
    language = language,
    isDefault = isDefault,
    isForced = false,
    channels = channels,
    height = height,
)

private fun goldenMediaSource(name: String?): JellyfinMediaSource =
    JellyfinMediaSource(
        id = "source",
        name = name,
        runTimeTicks = null,
        container = "mkv",
        videoBitrate = null,
        supportsDirectPlay = true,
        supportsDirectStream = true,
        supportsTranscoding = true,
        streams =
            listOf(
                goldenStream(JellyfinMediaStreamType.VIDEO, 0, null, "hevc", height = 2160),
                goldenStream(JellyfinMediaStreamType.AUDIO, 1, "de", "eac3", isDefault = true, channels = 6),
                goldenStream(JellyfinMediaStreamType.AUDIO, 2, "en", "eac3", channels = 6),
                goldenStream(JellyfinMediaStreamType.SUBTITLE, 3, "de", "subrip"),
                goldenStream(JellyfinMediaStreamType.SUBTITLE, 4, "en", "subrip"),
            ),
    )

private fun goldenPeople(): List<JellyfinPerson> =
    listOf(
        JellyfinPerson("p1", "Robin Example", "Director", "Director", null),
        JellyfinPerson("p2", "Alex Example", "Lena Hart", "Actor", null),
        JellyfinPerson("p3", "Sam Sample", "Captain Voss", "Actor", null),
        JellyfinPerson("p4", "Maximiliane Beispielname-Langform", "Keeper", "Actor", null),
        JellyfinPerson("p5", "Jo Placeholder", "Narrator", "Actor", null),
        JellyfinPerson("p6", "Kim Fixture", "Harbor Master", "Actor", null),
    )

private fun goldenDetail(
    name: String,
    minutes: Long,
    overview: String,
): JellyfinItemDetail =
    JellyfinItemDetail(
        id = name,
        name = name,
        overview = overview,
        taglines = listOf("Some signals never fade."),
        runTimeTicks = minutes * TICKS_PER_MINUTE,
        productionYear = 2024,
        premiereDate = null,
        communityRating = 7.8,
        officialRating = "PG-13",
        genres = listOf("Drama", "Mystery", "Thriller"),
        studios = listOf("Northern Light Pictures"),
        primaryImageTag = null,
        backdropImageTags = emptyList(),
        mediaSources = listOf(goldenMediaSource(null)),
        people = goldenPeople(),
    )

private const val GOLDEN_OVERVIEW =
    "A lighthouse keeper picks up a radio signal from a ship that sank forty years ago. As the storms close in, " +
        "she has to decide whether to answer it, and what the voice on the other end wants from her. The longer she " +
        "listens, the less the coast she thought she knew looks like home."

private val goldenBadges
    @Composable get() =
        JellyfinMediaFeatures(
            resolution = JellyfinVideoResolution.UHD_4K,
            dynamicRange = JellyfinDynamicRange.DOLBY_VISION,
            immersiveAudio = JellyfinImmersiveAudio.DOLBY_ATMOS,
            surround = JellyfinChannelLayout.SURROUND_7_1,
            hearingImpairedSubtitles = true,
        )

@Composable
private fun GoldenMovieDetail(
    strings: TvStrings,
    modifier: Modifier,
) {
    val detail = goldenDetail("The Last Horizon", minutes = 166, overview = GOLDEN_OVERVIEW)
    val uiState =
        buildTvJellyfinDetailUiState(
            routeKey = "golden-movie",
            overview = detail.overview,
            tagline = detail.taglines.first(),
            detailRows = tvJellyfinDetailRows(detail, strings),
            seasonGroups = emptyList(),
            selectedSeasonIndex = 0,
            episodes = emptyList(),
            cast = detail.tvCastPeople(),
            similar = listOf(goldenItem("Movie", "Paper Moon"), goldenItem("Movie", "Northern Lights")),
        )
    val model =
        TvDetailHeroModel(
            title = detail.name,
            backdropUrl = null,
            logoUrl = null,
            titleColor = TvText,
            metaLine =
                tvDetailMetaLine("2024", 166, "PG-13", 7.8, goldenEndsAt(strings), strings),
            subtitle = detail.genres.joinToString(TV_DETAIL_META_SEPARATOR),
            badges = goldenBadges.badgeLabels(strings),
            tagline = detail.taglines.first(),
            overview = detail.overview,
        )
    val summary = tvTrackLineSummary(tvPlaybackOptions(detail, TvDetailSelection(), strings), strings)
    val sectionModifiers = goldenSectionModifiers(uiState)
    GoldenDetailPage(
        TV_DETAIL_HERO_HEIGHT_DP,
        modifier,
        hero = {
            TvDetailHero(model) {
                GoldenActions(
                    strings,
                    label = tvDetailPrimaryLabel(38, true, null, strings),
                    startOver = true,
                    trailer = true,
                    summary = summary,
                )
            }
        },
        sections = {
            tvJellyfinDetailSections(
                uiState = uiState,
                homeState = JellyfinHomeState(),
                strings = strings,
                bodyFocusModifier = Modifier,
                sectionFocusModifiers = sectionModifiers,
                onOpenItem = {},
                onSelectSeason = {},
            )
        },
    )
}

@Composable
private fun GoldenActions(
    strings: TvStrings,
    label: String,
    startOver: Boolean,
    trailer: Boolean,
    summary: String?,
    more: Boolean = false,
) {
    TvDetailHeroActions(
        state = TvDetailHeroActionsState(label, startOver, favorite = true, played = false, trailerError = false),
        callbacks =
            TvDetailHeroActionCallbacks(
                onPlay = {},
                onStartOver = {},
                onToggleFavorite = {},
                onTogglePlayed = {},
                onTrailer = {}.takeIf { trailer },
                onMore = {}.takeIf { more },
            ),
        strings = strings,
        primaryActionModifier = Modifier,
        actionRowModifier = Modifier,
    )
    summary?.let { TvDetailTrackLine(it, onClick = {}) }
}

private fun goldenEpisodes(): List<JellyfinItem> {
    val titles = listOf("Low Tide", "The Quiet Signal", "Northbound", "Salt and Iron", "Lanterns", "The Long Dark")
    return titles.mapIndexed { index, title ->
        val season = if (index < 3) 1 else 2
        val number = if (index < 3) index + 1 else index - 2
        goldenItem("Episode", title).copy(
            id = "$season-$number",
            seriesId = "series",
            seriesName = "Harbor Lights",
            parentIndexNumber = season,
            indexNumber = number,
            runTimeTicks = 52 * TICKS_PER_MINUTE,
            locationType = "FileSystem",
            isPlayed = index < 3,
            playedPercentage =
                if (index < 3) {
                    100.0
                } else if (index == 3) {
                    38.0
                } else {
                    null
                },
            positionTicks = (20 * TICKS_PER_MINUTE).takeIf { index == 3 },
            overview = "Mara follows the radio trail to the old cannery, where the crew finds a logbook nobody was meant to read.",
        )
    }
}

@Composable
private fun GoldenSeriesDetail(
    strings: TvStrings,
    modifier: Modifier,
) {
    val detail = goldenDetail("Harbor Lights", minutes = 52, overview = GOLDEN_OVERVIEW)
    val episodes = goldenEpisodes()
    val groups = buildTvSeasonGroups(episodes)
    val next = tvNextEpisodeOf(episodes)
    val selected = defaultTvSeasonIndex(groups)
    val uiState =
        buildTvJellyfinDetailUiState(
            routeKey = "golden-series",
            overview = detail.overview,
            tagline = null,
            detailRows = tvJellyfinDetailRows(detail, strings),
            seasonGroups = groups,
            selectedSeasonIndex = selected,
            episodes = groups[selected].episodes,
            cast = detail.tvCastPeople(),
            similar = emptyList(),
        )
    val model =
        TvDetailHeroModel(
            title = detail.name,
            backdropUrl = null,
            logoUrl = null,
            titleColor = TvText,
            metaLine = tvDetailMetaLine("2022", 52, "TV-14", 8.3, null, strings, tvUnplayedEpisodesLabel(episodes, strings)),
            subtitle = detail.genres.joinToString(TV_DETAIL_META_SEPARATOR),
            overview = detail.overview,
        )
    val sectionModifiers = goldenSectionModifiers(uiState)
    GoldenDetailPage(
        TV_DETAIL_SERIES_HERO_HEIGHT_DP,
        modifier,
        hero = {
            TvDetailHero(model, bottomPadding = TV_DETAIL_SERIES_HERO_BOTTOM_DP.dp) {
                GoldenActions(strings, tvDetailPrimaryLabel(null, true, next, strings), startOver = true, trailer = true, summary = null)
            }
        },
        sections = {
            tvJellyfinDetailSections(
                uiState = uiState,
                homeState = JellyfinHomeState(),
                strings = strings,
                bodyFocusModifier = Modifier,
                sectionFocusModifiers = sectionModifiers,
                onOpenItem = {},
                onSelectSeason = {},
            )
        },
    )
}

@Composable
private fun GoldenEpisodeDetail(
    strings: TvStrings,
    modifier: Modifier,
) {
    val episodes = goldenEpisodes()
    val current = episodes[4]
    val detail =
        goldenDetail(
            "Lanterns",
            minutes = 52,
            overview = "Mara follows the radio trail to the old cannery, where the crew finds a logbook nobody was meant to read.",
        ).copy(taglines = emptyList(), people = goldenPeople().drop(1))
    val siblings = tvSeasonSiblings(current, episodes)
    val uiState =
        buildTvJellyfinDetailUiState(
            routeKey = "golden-episode",
            overview = detail.overview,
            tagline = null,
            detailRows = tvJellyfinDetailRows(detail, strings),
            seasonGroups = emptyList(),
            selectedSeasonIndex = 0,
            episodes = siblings,
            cast = detail.tvCastPeople(),
            similar = emptyList(),
            moreFromSeason = current.parentIndexNumber,
            currentEpisodeId = current.id,
        )
    val model =
        TvDetailHeroModel(
            title = detail.name,
            backdropUrl = null,
            logoUrl = null,
            titleColor = TvText,
            metaLine = tvDetailMetaLine("2024", 52, "TV-14", 8.1, goldenEndsAt(strings), strings),
            subtitle = listOf("Harbor Lights", tvEpisodeCode(2, 2)).joinToString(TV_DETAIL_META_SEPARATOR),
            badges = goldenBadges.badgeLabels(strings),
            overview = detail.overview,
        )
    val summary = tvTrackLineSummary(tvPlaybackOptions(detail, TvDetailSelection(subtitleStreamIndex = 4), strings), strings)
    val sectionModifiers = goldenSectionModifiers(uiState)
    GoldenDetailPage(
        TV_DETAIL_HERO_HEIGHT_DP,
        modifier,
        hero = {
            TvDetailHero(model) {
                GoldenActions(strings, strings.play, startOver = false, trailer = false, summary = summary, more = true)
            }
        },
        sections = {
            tvJellyfinDetailSections(
                uiState = uiState,
                homeState = JellyfinHomeState(),
                strings = strings,
                bodyFocusModifier = Modifier,
                sectionFocusModifiers = sectionModifiers,
                onOpenItem = {},
                onSelectSeason = {},
            )
        },
    )
}

@Composable
private fun GoldenSeerrDetail(
    strings: TvStrings,
    modifier: Modifier,
) {
    val route =
        TvRoute.SeerrDetail(
            tmdbId = 42,
            mediaType = JellyseerrMediaType.MOVIE,
            title = "The Last Horizon",
            overview = GOLDEN_OVERVIEW,
            releaseYear = "2024",
        )
    val uiState =
        buildTvSeerrDetailUiState(
            routeKey = "golden-seerr",
            overview = route.overview,
            tagline = null,
            ratings = JellyseerrMediaRatings(tmdb = 7.9, imdb = 8.1, rottenTomatoesCritics = 92.0, rottenTomatoesAudience = 88.0),
            cast =
                listOf(
                    JellyseerrPerson(1, "Alex Example", character = "Lena Hart"),
                    JellyseerrPerson(2, "Sam Sample", character = "Captain Voss"),
                    JellyseerrPerson(3, "Jo Placeholder", character = "Narrator"),
                ),
            similar = emptyList(),
            detailRows = listOf(TvDetailRow(strings.detailScreen.studios, "Northern Light Pictures")),
        )
    val model =
        tvSeerrHeroModel(
            route,
            detail = null,
            strings = strings,
        ).copy(metaLine = tvDetailMetaLine("2024", 166, "PG-13", 7.9, null, strings))
    val myListFocus = remember { FocusRequester() }
    val sectionModifiers = goldenSectionModifiers(uiState)
    GoldenDetailPage(
        TV_DETAIL_HERO_HEIGHT_DP,
        modifier,
        hero = {
            TvDetailHero(model) {
                TvDetailStatusChip(strings.availabilityPartiallyAvailable)
                TvSeerrHeroActions(
                    state =
                        TvSeerrHeroActionsState(
                            primaryAction = TvSeerrPrimaryAction.REQUEST,
                            hasRequestAction = true,
                            requestMoreSeasons = false,
                            isInMyList = false,
                            hasTrailer = true,
                        ),
                    callbacks = TvSeerrHeroActionCallbacks({}, {}, {}, {}),
                    strings = strings,
                    myListFocusRequester = myListFocus,
                    primaryActionModifier = Modifier,
                    actionRowModifier = Modifier,
                )
            }
        },
        sections = {
            tvSeerrDetailSections(
                uiState = uiState,
                strings = strings,
                bodyFocusModifier = Modifier,
                sectionFocusModifiers = sectionModifiers,
                onOpenItem = {},
            )
        },
    )
}
