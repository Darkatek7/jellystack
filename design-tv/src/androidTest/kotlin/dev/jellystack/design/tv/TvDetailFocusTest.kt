package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvDetailFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailOpensAtHeroAndUpFromPrimaryActionRestoresTop() {
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState =
                        TvDetailUiState(
                            routeKey = "movie-1",
                            sections = listOf(TvDetailSection.Overview(null, null)),
                        ),
                    heroContentDescription = "Movie details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        TvActionButton(
                            label = "Play",
                            onClick = {},
                            primary = true,
                            modifier =
                                primaryActionModifier
                                    .then(actionRowModifier)
                                    .align(Alignment.BottomStart)
                                    .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                        )
                    },
                ) { _, _ ->
                    item("body") {
                        Box(Modifier.height(900.dp).testTag("tv-detail-body"))
                    }
                }
            }
        }

        val hero = composeRule.onNodeWithTag("tv-detail-hero").assertIsFocused()
        composeRule.onNodeWithContentDescription("Movie details").assertIsFocused()
        assertEquals(0f, hero.getUnclippedBoundsInRoot().top.value, 0.01f)

        hero.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").assertIsFocused()

        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        hero.assertIsFocused()
        assertEquals(0f, hero.getUnclippedBoundsInRoot().top.value, 0.01f)
    }

    @Test
    fun focusChainOverviewCastSimilarScrollsAndCastCenterIsNoOp() {
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState =
                        TvDetailUiState(
                            routeKey = "episode-1",
                            sections =
                                listOf(
                                    TvDetailSection.Overview(null, null),
                                    TvDetailSection.Cast(
                                        listOf(
                                            TvDetailCastItem.Jellyfin(jellyfinPerson("person-0")),
                                            TvDetailCastItem.Jellyfin(jellyfinPerson("person-1")),
                                        ),
                                    ),
                                    TvDetailSection.Similar(
                                        listOf(TvDetailSimilarItem.Jellyfin(jellyfinItem("similar-0"))),
                                    ),
                                ),
                        ),
                    heroContentDescription = "Episode details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        Box(Modifier.fillMaxSize().then(actionRowModifier)) {
                            TvActionButton(
                                label = "Play",
                                onClick = {},
                                primary = true,
                                modifier =
                                    primaryActionModifier
                                        .then(actionRowModifier)
                                        .align(Alignment.BottomStart)
                                        .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                            )
                        }
                    },
                ) { bodyFocusModifier, sectionFocusModifiers ->
                    val castFocusModifiers = sectionFocusModifiers.getValue("cast")
                    val similarFocusModifiers = sectionFocusModifiers.getValue("similar")
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    item("cast") {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .then(castFocusModifiers.navigationModifier),
                        ) {
                            itemsIndexed(listOf("Actor 1", "Actor 2")) { index, actor ->
                                TvMediaCard(
                                    title = actor,
                                    imageUrl = null,
                                    onClick = null,
                                    modifier =
                                        castFocusModifiers
                                            .itemModifier("person-$index")
                                            .width(180.dp)
                                            .testTag("cast-person-$index"),
                                    providedFocusRequester = castFocusModifiers.itemFocusRequester("person-$index"),
                                )
                            }
                        }
                    }
                    item("similar") {
                        TvMediaCard(
                            title = "Similar",
                            imageUrl = null,
                            onClick = {},
                            modifier =
                                similarFocusModifiers
                                    .itemModifier("similar-0")
                                    .then(similarFocusModifiers.navigationModifier)
                                    .testTag("similar-item-0"),
                            providedFocusRequester = similarFocusModifiers.itemFocusRequester("similar-0"),
                        )
                    }
                }
            }
        }

        val hero = composeRule.onNodeWithTag("tv-detail-hero").assertIsFocused()
        hero.performKeyInput { pressKey(Key.DirectionDown) }
        val primary = composeRule.onNodeWithTag("tv-detail-primary-action").assertIsFocused()

        primary.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(2_000) {
            runCatching {
                composeRule.onNodeWithTag("tv-detail-body-focus").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        val body = composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        assertTrue(hero.getUnclippedBoundsInRoot().top.value < 0f)

        body.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        val firstCast = composeRule.onNodeWithContentDescription("Actor 1").assertIsFocused()

        firstCast.performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        firstCast.assertIsFocused()

        firstCast.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        val secondCast = composeRule.onNodeWithContentDescription("Actor 2").assertIsFocused()

        secondCast.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        val similar = composeRule.onNodeWithContentDescription("Similar").assertIsFocused()

        similar.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        firstCast.assertIsFocused()
    }

    @Test
    fun disappearingLowerSectionRestoresOverviewFocus() {
        var showCast by mutableStateOf(true)
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState =
                        TvDetailUiState(
                            routeKey = "episode-changing",
                            sections =
                                buildList {
                                    add(TvDetailSection.Overview(null, null))
                                    if (showCast) {
                                        add(
                                            TvDetailSection.Cast(
                                                listOf(TvDetailCastItem.Jellyfin(jellyfinPerson("temporary-actor"))),
                                            ),
                                        )
                                    }
                                },
                        ),
                    heroContentDescription = "Changing episode details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        TvActionButton(
                            label = "Play",
                            onClick = {},
                            primary = true,
                            modifier =
                                primaryActionModifier
                                    .then(actionRowModifier)
                                    .align(Alignment.BottomStart)
                                    .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                        )
                    },
                ) { bodyFocusModifier, sectionFocusModifiers ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    if (showCast) {
                        item("cast") {
                            LazyRow(
                                Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .then(sectionFocusModifiers.getValue("cast").navigationModifier),
                            ) {
                                item("actor") {
                                    TvMediaCard(
                                        title = "Temporary actor",
                                        imageUrl = null,
                                        onClick = null,
                                        modifier =
                                            sectionFocusModifiers
                                                .getValue("cast")
                                                .itemModifier("temporary-actor")
                                                .width(180.dp),
                                        providedFocusRequester =
                                            sectionFocusModifiers
                                                .getValue("cast")
                                                .itemFocusRequester("temporary-actor"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionDown) }
        val overview = composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        overview.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithContentDescription("Temporary actor").assertIsFocused()

        composeRule.runOnIdle { showCast = false }
        composeRule.waitForIdle()

        overview.assertIsFocused()
        composeRule.onNodeWithContentDescription("Temporary actor").assertDoesNotExist()
    }

    @Test
    fun appendingLowerSectionKeepsExistingCastFocus() {
        var showSimilar by mutableStateOf(false)
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState =
                        TvDetailUiState(
                            routeKey = "episode-appending",
                            sections =
                                buildList {
                                    add(TvDetailSection.Overview(null, null))
                                    add(
                                        TvDetailSection.Cast(
                                            listOf(TvDetailCastItem.Jellyfin(jellyfinPerson("persistent-actor"))),
                                        ),
                                    )
                                    if (showSimilar) {
                                        add(
                                            TvDetailSection.Similar(
                                                listOf(TvDetailSimilarItem.Jellyfin(jellyfinItem("new-similar"))),
                                            ),
                                        )
                                    }
                                },
                        ),
                    heroContentDescription = "Appending episode details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        TvActionButton(
                            label = "Play",
                            onClick = {},
                            primary = true,
                            modifier =
                                primaryActionModifier
                                    .then(actionRowModifier)
                                    .align(Alignment.BottomStart)
                                    .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                        )
                    },
                ) { bodyFocusModifier, sectionFocusModifiers ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                    item("cast") {
                        LazyRow(
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .then(sectionFocusModifiers.getValue("cast").navigationModifier),
                        ) {
                            item("actor") {
                                TvMediaCard(
                                    title = "Persistent actor",
                                    imageUrl = null,
                                    onClick = null,
                                    modifier =
                                        sectionFocusModifiers
                                            .getValue("cast")
                                            .itemModifier("persistent-actor")
                                            .width(180.dp),
                                    providedFocusRequester =
                                        sectionFocusModifiers
                                            .getValue("cast")
                                            .itemFocusRequester("persistent-actor"),
                                )
                            }
                        }
                    }
                    if (showSimilar) {
                        item("similar") {
                            TvMediaCard(
                                title = "New similar item",
                                imageUrl = null,
                                onClick = {},
                                modifier =
                                    sectionFocusModifiers
                                        .getValue("similar")
                                        .itemModifier("new-similar")
                                        .then(sectionFocusModifiers.getValue("similar").navigationModifier),
                                providedFocusRequester =
                                    sectionFocusModifiers
                                        .getValue("similar")
                                        .itemFocusRequester("new-similar"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-body-focus").performKeyInput { pressKey(Key.DirectionDown) }
        val cast = composeRule.onNodeWithContentDescription("Persistent actor").assertIsFocused()

        composeRule.runOnIdle { showSimilar = true }
        composeRule.waitForIdle()

        cast.assertIsFocused()
    }

    @Test
    fun detailWithoutHeroActionMovesDirectlyBetweenHeroAndOverview() {
        composeRule.setContent {
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState =
                        TvDetailUiState(
                            routeKey = "seerr-no-action",
                            sections = listOf(TvDetailSection.Overview(null, null)),
                        ),
                    heroContentDescription = "Seerr details",
                    hasPrimaryAction = false,
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                ) { bodyFocusModifier, _ ->
                    item("overview") {
                        Box(bodyFocusModifier.fillMaxWidth().height(900.dp))
                    }
                }
            }
        }

        val hero = composeRule.onNodeWithTag("tv-detail-hero").assertIsFocused()
        hero.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        val overview = composeRule.onNodeWithTag("tv-detail-body-focus").assertIsFocused()
        overview.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        hero.assertIsFocused()
    }

    @Test
    fun jellyfinProductionSectionsKeepAndRecoverFocusAcrossAsyncChanges() {
        var episodes by mutableStateOf(listOf(jellyfinItem("episode-1")))
        var cast by mutableStateOf(listOf(jellyfinPerson("person-1"), jellyfinPerson("person-2")))
        var similar by mutableStateOf(emptyList<JellyfinItem>())
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            val uiState =
                buildTvJellyfinDetailUiState(
                    routeKey = "series-changing",
                    facts = emptyList(),
                    overview = "Overview",
                    tagline = null,
                    seasonGroups = listOf(TvSeasonGroup(1, episodes)),
                    selectedSeasonIndex = 0,
                    episodes = episodes,
                    cast = cast,
                    similar = similar,
                )
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState = uiState,
                    heroContentDescription = "Changing Jellyfin details",
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { primaryActionModifier, actionRowModifier ->
                        TvActionButton(
                            label = "Play",
                            onClick = {},
                            primary = true,
                            modifier =
                                primaryActionModifier
                                    .then(actionRowModifier)
                                    .align(Alignment.BottomStart)
                                    .width(TV_DETAIL_PRIMARY_ACTION_WIDTH_DP.dp),
                        )
                    },
                ) { bodyFocusModifier, sectionFocusModifiers ->
                    tvJellyfinDetailSections(
                        uiState = uiState,
                        homeState = JellyfinHomeState(),
                        strings = strings,
                        bodyFocusModifier = bodyFocusModifier,
                        sectionFocusModifiers = sectionFocusModifiers,
                        onOpenItem = {},
                        onSelectSeason = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-primary-action").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-body-focus").performKeyInput { pressKey(Key.DirectionDown) }
        val episodeTag = "tv-detail-section-episodes-item-episode-1"
        waitUntilTagFocused(episodeTag)
        composeRule.onNodeWithTag(episodeTag).performKeyInput { pressKey(Key.DirectionDown) }
        val firstPersonTag = "tv-detail-section-cast-item-person-1"
        val secondPersonTag = "tv-detail-section-cast-item-person-2"
        waitUntilTagFocused(firstPersonTag)
        composeRule.onNodeWithTag(firstPersonTag).performKeyInput { pressKey(Key.DirectionRight) }
        val surviving = composeRule.onNodeWithTag(secondPersonTag).assertIsFocused()

        composeRule.runOnIdle {
            episodes = emptyList()
            cast = listOf(jellyfinPerson("person-2"), jellyfinPerson("person-1"))
            similar = listOf(jellyfinItem("similar-2"), jellyfinItem("similar-1"))
        }
        waitUntilTagFocused(secondPersonTag)
        surviving.assertIsFocused()

        composeRule.runOnIdle { cast = listOf(jellyfinPerson("person-1")) }
        waitUntilTagFocused(firstPersonTag)
        composeRule.onNodeWithTag(secondPersonTag).assertDoesNotExist()
        composeRule.onNodeWithTag(firstPersonTag).assertIsFocused()
    }

    @Test
    fun seerrProductionSectionsKeepAndRecoverFocusAcrossAsyncChanges() {
        var ratings by mutableStateOf<JellyseerrMediaRatings?>(null)
        var cast by mutableStateOf(listOf(seerrPerson(1), seerrPerson(2)))
        var similar by mutableStateOf(listOf(seerrItem(11), seerrItem(12)))
        val strings = TvStrings.current(AppLanguage.ENGLISH)
        composeRule.setContent {
            val uiState =
                buildTvSeerrDetailUiState(
                    routeKey = "tv:42",
                    overview = "Overview",
                    tagline = null,
                    ratings = ratings,
                    cast = cast,
                    similar = similar,
                )
            JellystackTvTheme {
                TvDetailFocusLayout(
                    uiState = uiState,
                    heroContentDescription = "Changing Seerr details",
                    hasPrimaryAction = false,
                    modifier = Modifier.fillMaxSize(),
                    heroContent = { _, _ -> Box(Modifier.fillMaxSize()) },
                ) { bodyFocusModifier, sectionFocusModifiers ->
                    tvSeerrDetailSections(
                        uiState = uiState,
                        strings = strings,
                        bodyFocusModifier = bodyFocusModifier,
                        sectionFocusModifiers = sectionFocusModifiers,
                        onOpenItem = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("tv-detail-hero").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("tv-detail-body-focus").performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("Person 1")
        composeRule.onNodeWithContentDescription("Person 1").performKeyInput { pressKey(Key.DirectionDown) }
        waitUntilFocused("Similar 11")
        composeRule.onNodeWithContentDescription("Similar 11").performKeyInput { pressKey(Key.DirectionRight) }
        val surviving = composeRule.onNodeWithContentDescription("Similar 12").assertIsFocused()

        composeRule.runOnIdle {
            ratings = JellyseerrMediaRatings(8.0, null, null, null)
            cast = emptyList()
            similar = listOf(seerrItem(12), seerrItem(11))
        }
        waitUntilFocused("Similar 12")
        surviving.assertIsFocused()

        composeRule.runOnIdle { similar = listOf(seerrItem(11)) }
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription("Similar 12").assertDoesNotExist()
                composeRule.onNodeWithContentDescription("Similar 11").assertIsFocused()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithContentDescription("Similar 12").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Similar 11").assertIsFocused()
    }

    private fun waitUntilFocused(contentDescription: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithContentDescription(contentDescription).assertIsFocused()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagFocused(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsFocused()
                true
            }.getOrDefault(false)
        }
    }

    private fun jellyfinPerson(id: String) =
        JellyfinPerson(id = id, name = id, role = "Role", type = "Actor", primaryImageTag = null)

    private fun jellyfinItem(id: String) =
        JellyfinItem(
            id = id,
            libraryId = null,
            name = id,
            sortName = null,
            overview = null,
            type = "Episode",
            mediaType = null,
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
            runTimeTicks = null,
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

    private fun seerrPerson(id: Int) = JellyseerrPerson(id = id, name = "Person $id")

    private fun seerrItem(id: Int) =
        JellyseerrSearchItem(
            tmdbId = id,
            mediaType = JellyseerrMediaType.TV,
            title = "Similar $id",
            overview = null,
            releaseYear = null,
            posterPath = null,
            backdropPath = null,
            mediaInfoId = null,
            tvdbId = null,
            availability = JellyseerrMediaAvailability(null, null),
            requests = emptyList(),
        )
}
