@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.jellystack.design.jellyseerr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.jellyseerr.JellyseerrCollection
import dev.jellystack.core.jellyseerr.JellyseerrDetailEnrichmentSection
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetail
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailEnrichment
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaRatings
import dev.jellystack.core.jellyseerr.JellyseerrMediaTrailer
import dev.jellystack.core.jellyseerr.JellyseerrMediaVideo
import dev.jellystack.core.jellyseerr.JellyseerrPerson
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.jellyseerr.JellyseerrSeason
import dev.jellystack.core.tmdb.TmdbPosterSize
import dev.jellystack.core.tmdb.tmdbPosterUrl
import dev.jellystack.design.components.CinematicCommandDeckSurface
import dev.jellystack.design.components.CinematicDetailColors
import dev.jellystack.design.components.CinematicDetailTab
import dev.jellystack.design.components.CinematicDetailTabs
import dev.jellystack.design.components.CinematicDetailTheme
import dev.jellystack.design.components.CinematicHeroStage
import dev.jellystack.design.components.ShimmerPlaceholder
import dev.jellystack.design.components.cinematicHeroHeight
import dev.jellystack.design.layout.LocalResponsiveProfile
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.detail_back
import jellystack_mobile.design.generated.resources.detail_budget
import jellystack_mobile.design.generated.resources.detail_cast_members
import jellystack_mobile.design.generated.resources.detail_certification
import jellystack_mobile.design.generated.resources.detail_collection
import jellystack_mobile.design.generated.resources.detail_countries
import jellystack_mobile.design.generated.resources.detail_empty_body
import jellystack_mobile.design.generated.resources.detail_empty_title
import jellystack_mobile.design.generated.resources.detail_enrichment_failed
import jellystack_mobile.design.generated.resources.detail_episode_count
import jellystack_mobile.design.generated.resources.detail_extras
import jellystack_mobile.design.generated.resources.detail_info
import jellystack_mobile.design.generated.resources.detail_key_crew
import jellystack_mobile.design.generated.resources.detail_keywords
import jellystack_mobile.design.generated.resources.detail_languages
import jellystack_mobile.design.generated.resources.detail_load_failed
import jellystack_mobile.design.generated.resources.detail_loading_more
import jellystack_mobile.design.generated.resources.detail_more_options
import jellystack_mobile.design.generated.resources.detail_original_language
import jellystack_mobile.design.generated.resources.detail_original_title
import jellystack_mobile.design.generated.resources.detail_ratings
import jellystack_mobile.design.generated.resources.detail_recommendations
import jellystack_mobile.design.generated.resources.detail_release
import jellystack_mobile.design.generated.resources.detail_similar
import jellystack_mobile.design.generated.resources.detail_status
import jellystack_mobile.design.generated.resources.detail_videos
import jellystack_mobile.design.generated.resources.overview
import jellystack_mobile.design.generated.resources.rating_imdb
import jellystack_mobile.design.generated.resources.rating_rt_audience
import jellystack_mobile.design.generated.resources.rating_rt_critics
import jellystack_mobile.design.generated.resources.rating_tmdb
import jellystack_mobile.design.generated.resources.retry
import jellystack_mobile.design.generated.resources.revenue
import jellystack_mobile.design.generated.resources.runtime
import jellystack_mobile.design.generated.resources.season_number
import jellystack_mobile.design.generated.resources.seasons
import jellystack_mobile.design.generated.resources.show_less
import jellystack_mobile.design.generated.resources.show_more
import jellystack_mobile.design.generated.resources.studios
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal object SeerrImmersiveDetailTestTags {
    const val ROOT = "seerr_immersive_detail_root"
    const val HERO = "seerr_immersive_detail_hero"
    const val TITLE = "seerr_immersive_detail_title"
    const val COMMAND_DECK = "seerr_immersive_detail_command_deck"
    const val PRIMARY_ACTION = "seerr_immersive_detail_primary_action"
    const val STATUS = "seerr_immersive_detail_status"
    const val TABS = "seerr_immersive_detail_tabs"
    const val RATINGS = "seerr_immersive_detail_ratings"
    const val CAST = "seerr_immersive_detail_cast"
    const val CREW = "seerr_immersive_detail_crew"
    const val SEASONS = "seerr_immersive_detail_seasons"
    const val COLLECTION = "seerr_immersive_detail_collection"
    const val SIMILAR = "seerr_immersive_detail_similar"
    const val RECOMMENDATIONS = "seerr_immersive_detail_recommendations"
    const val VIDEOS = "seerr_immersive_detail_videos"
    const val INFO = "seerr_immersive_detail_info"
    const val EMPTY = "seerr_immersive_detail_empty"
    const val ERROR = "seerr_immersive_detail_error"
}

internal enum class JellyseerrDetailSection {
    Overview,
    Extras,
    Info,
}

internal data class JellyseerrDetailCommandState(
    val primaryActionLabel: String? = null,
    val primaryActionEnabled: Boolean = true,
    val primaryActionLoading: Boolean = false,
    val statusLabel: String? = null,
    val showOverflow: Boolean = false,
)

@Composable
internal fun JellyseerrImmersiveDetailPageContent(
    item: JellyseerrSearchItem,
    detailState: JellyseerrMediaDetailState?,
    selectedSection: JellyseerrDetailSection,
    commandState: JellyseerrDetailCommandState,
    onSectionSelected: (JellyseerrDetailSection) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOverflow: () -> Unit,
    onOpenRelatedTitle: (SeerrDetailOrigin, JellyseerrSearchItem) -> Unit,
    onOpenCollection: ((JellyseerrCollection) -> Unit)?,
    onTrailer: (JellyseerrMediaTrailer) -> Unit,
    onVideo: (JellyseerrMediaVideo) -> Unit,
    modifier: Modifier = Modifier,
    enrichment: JellyseerrMediaDetailEnrichment? = null,
    enrichmentLoading: Boolean = false,
    enrichmentLoadingSections: Set<JellyseerrDetailEnrichmentSection> = emptySet(),
    onRetryEnrichment: ((JellyseerrDetailEnrichmentSection) -> Unit)? = null,
    listState: LazyListState? = null,
    supportingActions: @Composable () -> Unit = {},
) {
    val resolvedState = detailState ?: JellyseerrMediaDetailState.Loading
    CinematicDetailTheme {
        when (resolvedState) {
            JellyseerrMediaDetailState.Loading ->
                SeerrDetailLoading(
                    item = item,
                    onBack = onBack,
                    modifier = modifier,
                )

            is JellyseerrMediaDetailState.Error ->
                SeerrDetailError(
                    item = item,
                    message = resolvedState.message,
                    onBack = onBack,
                    onRetry = onRetry,
                    modifier = modifier,
                )

            is JellyseerrMediaDetailState.Loaded -> {
                val detail = resolvedState.detail
                val resolvedEnrichment = enrichment ?: detail.enrichment
                val resolvedLoadingSections =
                    enrichmentLoadingSections +
                        resolvedState.enrichmentLoadingSections +
                        if (enrichmentLoading) {
                            JellyseerrDetailEnrichmentSection.entries
                        } else {
                            emptyList()
                        }
                SeerrLoadedDetail(
                    item = item,
                    detail = detail,
                    enrichment = resolvedEnrichment,
                    enrichmentLoadingSections = resolvedLoadingSections,
                    selectedSection = selectedSection,
                    commandState = commandState,
                    onSectionSelected = onSectionSelected,
                    onBack = onBack,
                    onPrimaryAction = onPrimaryAction,
                    onOverflow = onOverflow,
                    onOpenRelatedTitle = onOpenRelatedTitle,
                    onOpenCollection = onOpenCollection,
                    onTrailer = onTrailer,
                    onVideo = onVideo,
                    onRetryEnrichment = onRetryEnrichment,
                    listState = listState,
                    modifier = modifier,
                    supportingActions = supportingActions,
                )
            }
        }
    }
}

@Composable
private fun SeerrLoadedDetail(
    item: JellyseerrSearchItem,
    detail: JellyseerrMediaDetail,
    enrichment: JellyseerrMediaDetailEnrichment,
    enrichmentLoadingSections: Set<JellyseerrDetailEnrichmentSection>,
    selectedSection: JellyseerrDetailSection,
    commandState: JellyseerrDetailCommandState,
    onSectionSelected: (JellyseerrDetailSection) -> Unit,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOverflow: () -> Unit,
    onOpenRelatedTitle: (SeerrDetailOrigin, JellyseerrSearchItem) -> Unit,
    onOpenCollection: ((JellyseerrCollection) -> Unit)?,
    onTrailer: (JellyseerrMediaTrailer) -> Unit,
    onVideo: (JellyseerrMediaVideo) -> Unit,
    onRetryEnrichment: ((JellyseerrDetailEnrichmentSection) -> Unit)?,
    listState: LazyListState?,
    modifier: Modifier,
    supportingActions: @Composable () -> Unit,
) {
    val enrichmentLoading = enrichmentLoadingSections.isNotEmpty()
    val sections = visibleSeerrDetailSections(detail, enrichment, enrichmentLoading)
    val resolvedSection =
        selectedSection.takeIf { it in sections }
            ?: sections.firstOrNull()
            ?: JellyseerrDetailSection.Overview
    val profile = LocalResponsiveProfile.current
    val resolvedListState = listState ?: rememberLazyListState()
    LazyColumn(
        state = resolvedListState,
        modifier =
            modifier
                .fillMaxSize()
                .background(CinematicDetailColors.background)
                .testTag(SeerrImmersiveDetailTestTags.ROOT),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        item(key = "hero") {
            SeerrHeroAndCommandDeck(
                item = item,
                detail = detail,
                commandState = commandState,
                onBack = onBack,
                onPrimaryAction = onPrimaryAction,
                onOverflow = onOverflow,
            )
        }
        val facts = seerrDetailFacts(detail)
        if (facts.isNotEmpty()) {
            item(key = "facts") {
                SeerrFactStrip(
                    facts = facts,
                    modifier =
                        Modifier.padding(
                            horizontal = profile.horizontalContentPadding,
                            vertical = 10.dp,
                        ),
                )
            }
        }
        if (sections.size > 1) {
            item(key = "tabs") {
                SeerrDetailTabs(
                    sections = sections,
                    selected = resolvedSection,
                    onSelect = onSectionSelected,
                    modifier =
                        Modifier.padding(
                            horizontal = profile.horizontalContentPadding,
                            vertical = 10.dp,
                        ),
                )
            }
        }
        if (sections.isEmpty()) {
            item(key = "empty") {
                SeerrDetailEmptyState(
                    modifier =
                        Modifier.padding(
                            horizontal = profile.horizontalContentPadding,
                            vertical = 10.dp,
                        ),
                )
            }
        } else {
            when (resolvedSection) {
                JellyseerrDetailSection.Overview -> {
                    item(key = "overview") {
                        SeerrOverviewSection(
                            detail = detail,
                            enrichment = enrichment,
                            ratingsLoading =
                                JellyseerrDetailEnrichmentSection.RATINGS in
                                    enrichmentLoadingSections,
                            onRetryEnrichment = onRetryEnrichment,
                            modifier =
                                Modifier.padding(
                                    horizontal = profile.horizontalContentPadding,
                                    vertical = 10.dp,
                                ),
                        )
                    }
                    if (detail.cast.isNotEmpty()) {
                        item(key = "cast") {
                            SeerrPeopleSection(
                                people = seerrVisibleCast(detail.cast),
                                heading = stringResource(Res.string.detail_cast_members),
                                testTag = SeerrImmersiveDetailTestTags.CAST,
                            )
                        }
                    }
                    val crew = keySeerrCrew(detail.crew)
                    if (crew.isNotEmpty()) {
                        item(key = "crew") {
                            SeerrCrewSection(
                                people = crew,
                                modifier =
                                    Modifier.padding(
                                        horizontal = profile.horizontalContentPadding,
                                        vertical = 10.dp,
                                    ),
                            )
                        }
                    }
                    if (detail.seasons.isNotEmpty()) {
                        item(key = "seasons") {
                            SeerrSeasonsSection(detail.seasons)
                        }
                    }
                    detail.collection?.let { collection ->
                        item(key = "collection") {
                            SeerrCollectionSection(
                                collection = collection,
                                onOpen = onOpenCollection,
                                modifier =
                                    Modifier.padding(
                                        horizontal = profile.horizontalContentPadding,
                                        vertical = 10.dp,
                                    ),
                            )
                        }
                    }
                    val similarLoading =
                        JellyseerrDetailEnrichmentSection.SIMILAR in
                            enrichmentLoadingSections
                    if (
                        enrichment.similar.isNotEmpty() ||
                        JellyseerrDetailEnrichmentSection.SIMILAR in enrichment.failedSections ||
                        similarLoading
                    ) {
                        item(key = "similar") {
                            SeerrRelatedSection(
                                heading = stringResource(Res.string.detail_similar),
                                items = seerrVisibleRelatedItems(enrichment.similar),
                                loading = similarLoading,
                                failed =
                                    JellyseerrDetailEnrichmentSection.SIMILAR in
                                        enrichment.failedSections,
                                failureSection = JellyseerrDetailEnrichmentSection.SIMILAR,
                                onRetry = onRetryEnrichment,
                                onOpen = {
                                    onOpenRelatedTitle(SeerrDetailOrigin.Similar, it)
                                },
                                testTag = SeerrImmersiveDetailTestTags.SIMILAR,
                            )
                        }
                    }
                    val recommendationsLoading =
                        JellyseerrDetailEnrichmentSection.RECOMMENDATIONS in
                            enrichmentLoadingSections
                    if (
                        enrichment.recommendations.isNotEmpty() ||
                        recommendationsLoading ||
                        JellyseerrDetailEnrichmentSection.RECOMMENDATIONS in
                        enrichment.failedSections
                    ) {
                        item(key = "recommendations") {
                            SeerrRelatedSection(
                                heading = stringResource(Res.string.detail_recommendations),
                                items = seerrVisibleRelatedItems(enrichment.recommendations),
                                loading = recommendationsLoading,
                                failed =
                                    JellyseerrDetailEnrichmentSection.RECOMMENDATIONS in
                                        enrichment.failedSections,
                                failureSection = JellyseerrDetailEnrichmentSection.RECOMMENDATIONS,
                                onRetry = onRetryEnrichment,
                                onOpen = {
                                    onOpenRelatedTitle(SeerrDetailOrigin.Recommendations, it)
                                },
                                testTag = SeerrImmersiveDetailTestTags.RECOMMENDATIONS,
                            )
                        }
                    }
                }

                JellyseerrDetailSection.Extras ->
                    item(key = "videos") {
                        SeerrVideosSection(
                            detail = detail,
                            onTrailer = onTrailer,
                            onVideo = onVideo,
                            modifier =
                                Modifier.padding(
                                    horizontal = profile.horizontalContentPadding,
                                    vertical = 10.dp,
                                ),
                        )
                    }

                JellyseerrDetailSection.Info ->
                    item(key = "info") {
                        SeerrInfoSection(
                            detail = detail,
                            modifier =
                                Modifier.padding(
                                    horizontal = profile.horizontalContentPadding,
                                    vertical = 10.dp,
                                ),
                        )
                    }
            }
        }
        item(key = "supporting_actions") {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = profile.horizontalContentPadding,
                            vertical = 10.dp,
                        ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                supportingActions()
            }
        }
    }
}

@Composable
private fun SeerrHeroAndCommandDeck(
    item: JellyseerrSearchItem,
    detail: JellyseerrMediaDetail,
    commandState: JellyseerrDetailCommandState,
    onBack: () -> Unit,
    onPrimaryAction: () -> Unit,
    onOverflow: () -> Unit,
) {
    val profile = LocalResponsiveProfile.current
    val title = detail.title.ifBlank { item.title }
    CinematicHeroStage(
        heroHeight = cinematicHeroHeight(),
        heroModifier = Modifier.testTag(SeerrImmersiveDetailTestTags.HERO),
        artwork = {
            SeerrHeroArtwork(
                backdropPath = detail.backdropPath ?: item.backdropPath,
                posterPath = detail.posterPath ?: item.posterPath,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        },
        topStart =
            if (profile.isExpanded) {
                null
            } else {
                {
                    SeerrHeroIcon(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.detail_back),
                        onClick = onBack,
                    )
                }
            },
        topEnd =
            if (commandState.showOverflow) {
                {
                    SeerrHeroIcon(
                        icon = Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(Res.string.detail_more_options),
                        onClick = onOverflow,
                    )
                }
            } else {
                null
            },
        identity = {
            Text(
                text = title,
                modifier =
                    Modifier
                        .testTag(SeerrImmersiveDetailTestTags.TITLE)
                        .semantics { heading() },
                style = MaterialTheme.typography.headlineLarge,
                color = CinematicDetailColors.onSurface,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.genres.isNotEmpty()) {
                Text(
                    text = detail.genres.take(3).joinToString(" · "),
                    color = CinematicDetailColors.muted,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        commandDeck = {
            SeerrCommandDeck(
                state = commandState,
                onPrimaryAction = onPrimaryAction,
            )
        },
    )
}

@Composable
private fun SeerrCommandDeck(
    state: JellyseerrDetailCommandState,
    onPrimaryAction: () -> Unit,
) {
    CinematicCommandDeckSurface(testTag = SeerrImmersiveDetailTestTags.COMMAND_DECK) {
        state.primaryActionLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Button(
                onClick = onPrimaryAction,
                enabled = state.primaryActionEnabled && !state.primaryActionLoading,
                modifier =
                    Modifier
                        .weight(if (state.statusLabel == null) 1f else 1.45f)
                        .height(60.dp)
                        .testTag(SeerrImmersiveDetailTestTags.PRIMARY_ACTION),
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = CinematicDetailColors.primary,
                        contentColor = Color(0xFF21113E),
                    ),
            ) {
                if (state.primaryActionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.statusLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Surface(
                modifier =
                    Modifier
                        .weight(if (state.primaryActionLabel == null) 1f else 0.75f)
                        .height(60.dp)
                        .testTag(SeerrImmersiveDetailTestTags.STATUS)
                        .semantics { contentDescription = label },
                color = CinematicDetailColors.surface,
                contentColor = CinematicDetailColors.primary,
                shape = RoundedCornerShape(24.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrFactStrip(
    facts: List<String>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(facts.size) { index ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = facts[index],
                    color = CinematicDetailColors.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (index < facts.lastIndex) {
                    Text(
                        text = "•",
                        color = CinematicDetailColors.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrDetailTabs(
    sections: List<JellyseerrDetailSection>,
    selected: JellyseerrDetailSection,
    onSelect: (JellyseerrDetailSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    CinematicDetailTabs(
        tabs =
            sections.map { section ->
                CinematicDetailTab(
                    key = section,
                    label =
                        stringResource(
                            when (section) {
                                JellyseerrDetailSection.Overview -> Res.string.overview
                                JellyseerrDetailSection.Extras -> Res.string.detail_extras
                                JellyseerrDetailSection.Info -> Res.string.detail_info
                            },
                        ),
                    testTag = "seerr_immersive_detail_tab_${section.name.lowercase()}",
                )
            },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        testTag = SeerrImmersiveDetailTestTags.TABS,
    )
}

@Composable
private fun SeerrOverviewSection(
    detail: JellyseerrMediaDetail,
    enrichment: JellyseerrMediaDetailEnrichment,
    ratingsLoading: Boolean,
    onRetryEnrichment: ((JellyseerrDetailEnrichmentSection) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        detail.tagline?.takeIf { it.isNotBlank() }?.let { tagline ->
            Text(
                text = tagline,
                color = CinematicDetailColors.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            ExpandableOverview(overview)
        }
        val ratings = enrichment.ratings ?: detail.ratings
        if (
            ratings != null ||
            ratingsLoading ||
            JellyseerrDetailEnrichmentSection.RATINGS in enrichment.failedSections
        ) {
            SeerrRatingsSection(
                ratings = ratings,
                loading = ratingsLoading,
                failed =
                    JellyseerrDetailEnrichmentSection.RATINGS in
                        enrichment.failedSections,
                onRetry = onRetryEnrichment,
            )
        }
    }
}

@Composable
private fun ExpandableOverview(overview: String) {
    var expanded by remember(overview) { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyLarge,
            color = CinematicDetailColors.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 7,
            overflow = TextOverflow.Ellipsis,
        )
        if (overview.length > 320) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) Res.string.show_less else Res.string.show_more,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun SeerrRatingsSection(
    ratings: JellyseerrMediaRatings?,
    loading: Boolean,
    failed: Boolean,
    onRetry: ((JellyseerrDetailEnrichmentSection) -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(SeerrImmersiveDetailTestTags.RATINGS),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(stringResource(Res.string.detail_ratings))
        when {
            loading ->
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    shape = RoundedCornerShape(24.dp),
                )

            ratings != null -> {
                val entries = ratingEntries(ratings)
                if (entries.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CinematicDetailColors.surfaceHigh,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(entries) { rating ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = CinematicDetailColors.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Column {
                                        Text(
                                            rating.second,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                        )
                                        Text(
                                            rating.first,
                                            color = CinematicDetailColors.muted,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (failed) {
            EnrichmentFailure(
                label = stringResource(Res.string.detail_ratings),
                section = JellyseerrDetailEnrichmentSection.RATINGS,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun SeerrPeopleSection(
    people: List<JellyseerrPerson>,
    heading: String,
    testTag: String,
) {
    val profile = LocalResponsiveProfile.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(
            text = heading,
            modifier = Modifier.padding(horizontal = profile.horizontalContentPadding),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = profile.horizontalContentPadding),
        ) {
            items(people, key = { "${it.id}:${it.character}:${it.job}" }) { person ->
                Column(
                    modifier = Modifier.width(104.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SeerrTmdbArtwork(
                        path = person.profilePath,
                        contentDescription = person.name,
                        size = TmdbPosterSize.W342,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(92.dp).clip(CircleShape),
                    )
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = CinematicDetailColors.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    person.character?.takeIf { it.isNotBlank() }?.let { character ->
                        Text(
                            text = character,
                            style = MaterialTheme.typography.labelSmall,
                            color = CinematicDetailColors.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrCrewSection(
    people: List<JellyseerrPerson>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(SeerrImmersiveDetailTestTags.CREW),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(stringResource(Res.string.detail_key_crew))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CinematicDetailColors.surface,
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                people.forEach { person ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = person.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val role = person.job ?: person.department
                        role?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                modifier = Modifier.weight(1f),
                                color = CinematicDetailColors.muted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrSeasonsSection(seasons: List<JellyseerrSeason>) {
    val profile = LocalResponsiveProfile.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SeerrImmersiveDetailTestTags.SEASONS),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(
            text = stringResource(Res.string.seasons),
            modifier = Modifier.padding(horizontal = profile.horizontalContentPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = profile.horizontalContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(seasons, key = { "${it.id}:${it.seasonNumber}" }) { season ->
                Surface(
                    modifier = Modifier.width(148.dp),
                    color = CinematicDetailColors.surface,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column {
                        SeerrTmdbArtwork(
                            path = season.posterPath,
                            contentDescription =
                                season.name
                                    ?: stringResource(
                                        Res.string.season_number,
                                        season.seasonNumber,
                                    ),
                            size = TmdbPosterSize.W342,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        )
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    season.name
                                        ?: stringResource(
                                            Res.string.season_number,
                                            season.seasonNumber,
                                        ),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            season.episodeCount?.takeIf { it > 0 }?.let { count ->
                                Text(
                                    text = stringResource(Res.string.detail_episode_count, count),
                                    color = CinematicDetailColors.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrCollectionSection(
    collection: JellyseerrCollection,
    onOpen: ((JellyseerrCollection) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SeerrImmersiveDetailTestTags.COLLECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(stringResource(Res.string.detail_collection))
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onOpen == null) {
                            Modifier
                        } else {
                            Modifier
                                .clickable(role = Role.Button) { onOpen(collection) }
                                .semantics { role = Role.Button }
                        },
                    ),
            color = CinematicDetailColors.surface,
            shape = RoundedCornerShape(22.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 8f)) {
                SeerrTmdbArtwork(
                    path = collection.backdropPath ?: collection.posterPath,
                    contentDescription = collection.name,
                    size = TmdbPosterSize.W500,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                                ),
                            ).padding(18.dp),
                ) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrRelatedSection(
    heading: String,
    items: List<JellyseerrSearchItem>,
    loading: Boolean,
    failed: Boolean,
    failureSection: JellyseerrDetailEnrichmentSection,
    onRetry: ((JellyseerrDetailEnrichmentSection) -> Unit)?,
    onOpen: (JellyseerrSearchItem) -> Unit,
    testTag: String,
) {
    val profile = LocalResponsiveProfile.current
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeerrSectionHeading(
            text = heading,
            modifier = Modifier.padding(horizontal = profile.horizontalContentPadding),
        )
        if (items.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = profile.horizontalContentPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { "${it.mediaType}:${it.tmdbId}" }) { related ->
                    SeerrRelatedCard(
                        item = related,
                        onOpen = onOpen,
                    )
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .padding(horizontal = profile.horizontalContentPadding)
                        .size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        if (failed) {
            EnrichmentFailure(
                label = heading,
                section = failureSection,
                onRetry = onRetry,
                modifier = Modifier.padding(horizontal = profile.horizontalContentPadding),
            )
        }
    }
}

@Composable
private fun SeerrRelatedCard(
    item: JellyseerrSearchItem,
    onOpen: (JellyseerrSearchItem) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .width(148.dp)
                .clickable(role = Role.Button) { onOpen(item) }
                .semantics {
                    role = Role.Button
                    contentDescription =
                        listOfNotNull(item.title, item.releaseYear)
                            .joinToString(", ")
                },
        color = CinematicDetailColors.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            SeerrTmdbArtwork(
                path = item.posterPath ?: item.backdropPath,
                contentDescription = item.title,
                size = TmdbPosterSize.W342,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.releaseYear?.takeIf { it.isNotBlank() }?.let { year ->
                    Text(
                        text = year,
                        color = CinematicDetailColors.muted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeerrVideosSection(
    detail: JellyseerrMediaDetail,
    onTrailer: (JellyseerrMediaTrailer) -> Unit,
    onVideo: (JellyseerrMediaVideo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val videos = seerrDetailVideos(detail)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SeerrImmersiveDetailTestTags.VIDEOS),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SeerrSectionHeading(stringResource(Res.string.detail_videos))
        videos.forEach { video ->
            val matchingTrailer =
                detail.trailer?.takeIf { trailer ->
                    trailer.key == video.key &&
                        trailer.site.equals(video.site, ignoreCase = true)
                }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            if (matchingTrailer != null) {
                                onTrailer(matchingTrailer)
                            } else {
                                onVideo(video)
                            }
                        },
                color = CinematicDetailColors.surface,
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f)) {
                    SeerrTmdbArtwork(
                        path = detail.backdropPath ?: detail.posterPath,
                        contentDescription = video.name ?: stringResource(Res.string.detail_videos),
                        size = TmdbPosterSize.W500,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                                    ),
                                ).padding(18.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Column {
                                Text(
                                    text =
                                        video.name
                                            ?.takeIf { it.isNotBlank() }
                                            ?: stringResource(Res.string.detail_videos),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                video.type?.takeIf { it.isNotBlank() }?.let { type ->
                                    Text(
                                        text = type,
                                        color = CinematicDetailColors.muted,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrInfoSection(
    detail: JellyseerrMediaDetail,
    modifier: Modifier = Modifier,
) {
    val rows =
        listOfNotNull(
            detail.originalTitle
                ?.takeIf { it.isNotBlank() && it != detail.title }
                ?.let { stringResource(Res.string.detail_original_title) to it },
            detail.releaseDate
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.detail_release) to it },
            detail.status
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.detail_status) to it },
            detail.runtimeMinutes
                ?.takeIf { it > 0 }
                ?.let { stringResource(Res.string.runtime) to seerrRuntime(it) },
            detail.certification
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.detail_certification) to it },
            detail.originalLanguage
                ?.takeIf { it.isNotBlank() }
                ?.let { stringResource(Res.string.detail_original_language) to it.uppercase() },
            detail.languages
                .takeIf { it.isNotEmpty() }
                ?.let { stringResource(Res.string.detail_languages) to it.joinToString(" · ") },
            detail.productionCountries
                .takeIf { it.isNotEmpty() }
                ?.let { stringResource(Res.string.detail_countries) to it.joinToString(" · ") },
            detail.studios
                .takeIf { it.isNotEmpty() }
                ?.let { stringResource(Res.string.studios) to it.joinToString(" · ") },
            detail.budget
                ?.takeIf { it > 0L }
                ?.let { stringResource(Res.string.detail_budget) to formatMoney(it) },
            detail.revenue
                ?.takeIf { it > 0L }
                ?.let { stringResource(Res.string.revenue) to formatMoney(it) },
        )
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SeerrImmersiveDetailTestTags.INFO),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (rows.isNotEmpty()) {
            SeerrSectionHeading(stringResource(Res.string.detail_info))
            rows.forEach { (label, value) ->
                SeerrInfoRow(label, value)
            }
        }
        if (detail.keywords.isNotEmpty()) {
            SeerrSectionHeading(stringResource(Res.string.detail_keywords))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.keywords.take(24).forEach { keyword ->
                    Surface(
                        color = CinematicDetailColors.surfaceHigh,
                        contentColor = CinematicDetailColors.muted,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "#$keyword",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrInfoRow(
    label: String,
    value: String,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = CinematicDetailColors.onSurface,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinematicDetailColors.muted,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = label,
                    modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = CinematicDetailColors.onSurface,
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinematicDetailColors.muted,
                )
            }
        }
    }
}

@Composable
private fun SeerrEnrichmentLoading(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(Res.string.detail_loading_more)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CinematicDetailColors.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = loadingLabel
                    },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                text = loadingLabel,
                color = CinematicDetailColors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EnrichmentFailure(
    label: String,
    section: JellyseerrDetailEnrichmentSection,
    onRetry: ((JellyseerrDetailEnrichmentSection) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        color = CinematicDetailColors.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.detail_enrichment_failed, label),
                modifier = Modifier.weight(1f),
                color = CinematicDetailColors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            onRetry?.let { retryAction ->
                TextButton(onClick = { retryAction(section) }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

@Composable
private fun SeerrSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = CinematicDetailColors.onSurface,
    )
}

@Composable
private fun SeerrHeroArtwork(
    backdropPath: String?,
    posterPath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val candidates =
        remember(backdropPath, posterPath) {
            listOfNotNull(
                backdropPath?.takeIf { it.isNotBlank() },
                posterPath?.takeIf { it.isNotBlank() && it != backdropPath },
            )
        }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val activePath = candidates.getOrNull(index)
    if (activePath == null) {
        NeutralArtwork(modifier)
    } else {
        val context = LocalPlatformContext.current
        val request =
            remember(activePath, context) {
                ImageRequest
                    .Builder(context)
                    .data(tmdbPosterUrl(activePath, TmdbPosterSize.ORIGINAL))
                    .crossfade(true)
                    .build()
            }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            onError = { index += 1 },
            modifier = modifier,
        )
    }
}

@Composable
private fun SeerrTmdbArtwork(
    path: String?,
    contentDescription: String,
    size: TmdbPosterSize,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val url = remember(path, size) { tmdbPosterUrl(path, size) }
    if (url == null) {
        NeutralArtwork(modifier)
        return
    }
    val context = LocalPlatformContext.current
    val background = CinematicDetailColors.surfaceHigh
    val painter = remember(background) { ColorPainter(background) }
    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = painter,
        error = painter,
        modifier = modifier,
    )
}

@Composable
private fun NeutralArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    listOf(
                        CinematicDetailColors.surfaceHigh,
                        CinematicDetailColors.background,
                    ),
                ),
            ),
    )
}

@Composable
private fun SeerrHeroIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun SeerrDetailLoading(
    item: JellyseerrSearchItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = LocalResponsiveProfile.current
    val loadingLabel = stringResource(Res.string.detail_loading_more)
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(CinematicDetailColors.background)
                .testTag(SeerrImmersiveDetailTestTags.ROOT),
    ) {
        item {
            CinematicHeroStage(
                heroHeight = cinematicHeroHeight(),
                heroModifier = Modifier.testTag(SeerrImmersiveDetailTestTags.HERO),
                artwork = {
                    SeerrHeroArtwork(
                        backdropPath = item.backdropPath,
                        posterPath = item.posterPath,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                topStart =
                    if (profile.isExpanded) {
                        null
                    } else {
                        {
                            SeerrHeroIcon(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.detail_back),
                                onClick = onBack,
                            )
                        }
                    },
                identity = {
                    Text(
                        text = item.title,
                        modifier =
                            Modifier
                                .testTag(SeerrImmersiveDetailTestTags.TITLE)
                                .semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                },
                commandDeck = {
                    CinematicCommandDeckSurface(
                        testTag = SeerrImmersiveDetailTestTags.COMMAND_DECK,
                    ) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = loadingLabel
                                    },
                            color = CinematicDetailColors.primary,
                        )
                    }
                },
            )
        }
        item {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = profile.horizontalContentPadding,
                        vertical = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(22.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SeerrDetailEmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SeerrImmersiveDetailTestTags.EMPTY)
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
        color = CinematicDetailColors.surface,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.detail_empty_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(Res.string.detail_empty_body),
                color = CinematicDetailColors.muted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SeerrDetailError(
    item: JellyseerrSearchItem,
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = LocalResponsiveProfile.current
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(CinematicDetailColors.background)
                .testTag(SeerrImmersiveDetailTestTags.ERROR)
                .padding(profile.horizontalContentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!profile.isExpanded) {
                SeerrHeroIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.detail_back),
                    onClick = onBack,
                )
            }
            Text(
                text = item.title,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = if (profile.isExpanded) 0.dp else 12.dp)
                        .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Assertive },
            color = CinematicDetailColors.surface,
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.detail_load_failed),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = message.ifBlank { stringResource(Res.string.detail_load_failed) },
                    color = CinematicDetailColors.muted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

internal fun visibleSeerrDetailSections(
    detail: JellyseerrMediaDetail,
    enrichment: JellyseerrMediaDetailEnrichment,
    enrichmentLoading: Boolean,
): List<JellyseerrDetailSection> =
    buildList {
        if (hasSeerrOverviewContent(detail, enrichment, enrichmentLoading)) {
            add(JellyseerrDetailSection.Overview)
        }
        if (seerrDetailVideos(detail).isNotEmpty()) {
            add(JellyseerrDetailSection.Extras)
        }
        if (hasSeerrInfoContent(detail)) {
            add(JellyseerrDetailSection.Info)
        }
    }

internal fun keySeerrCrew(crew: List<JellyseerrPerson>): List<JellyseerrPerson> =
    crew
        .filter { person ->
            val role = "${person.job.orEmpty()} ${person.department.orEmpty()}"
            KEY_CREW_ROLES.any { role.contains(it, ignoreCase = true) }
        }.distinctBy { it.id to (it.job ?: it.department) }
        .sortedBy { person ->
            val role = "${person.job.orEmpty()} ${person.department.orEmpty()}"
            KEY_CREW_ROLES
                .indexOfFirst { role.contains(it, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?: Int.MAX_VALUE
        }.take(10)

internal fun seerrVisibleRelatedItems(items: List<JellyseerrSearchItem>): List<JellyseerrSearchItem> = items.take(12)

internal fun seerrVisibleCast(people: List<JellyseerrPerson>): List<JellyseerrPerson> =
    people.sortedBy { it.order ?: Int.MAX_VALUE }.take(16)

private fun hasSeerrOverviewContent(
    detail: JellyseerrMediaDetail,
    enrichment: JellyseerrMediaDetailEnrichment,
    enrichmentLoading: Boolean,
): Boolean =
    !detail.overview.isNullOrBlank() ||
        !detail.tagline.isNullOrBlank() ||
        detail.ratings != null ||
        enrichment.ratings != null ||
        detail.cast.isNotEmpty() ||
        keySeerrCrew(detail.crew).isNotEmpty() ||
        detail.seasons.isNotEmpty() ||
        detail.collection != null ||
        enrichment.similar.isNotEmpty() ||
        enrichment.recommendations.isNotEmpty() ||
        enrichment.failedSections.isNotEmpty() ||
        enrichmentLoading

private fun hasSeerrInfoContent(detail: JellyseerrMediaDetail): Boolean =
    detail.originalTitle?.takeIf { it.isNotBlank() && it != detail.title } != null ||
        !detail.releaseDate.isNullOrBlank() ||
        !detail.status.isNullOrBlank() ||
        !detail.certification.isNullOrBlank() ||
        detail.runtimeMinutes?.let { it > 0 } == true ||
        !detail.originalLanguage.isNullOrBlank() ||
        detail.languages.isNotEmpty() ||
        detail.productionCountries.isNotEmpty() ||
        detail.studios.isNotEmpty() ||
        detail.budget?.let { it > 0L } == true ||
        detail.revenue?.let { it > 0L } == true ||
        detail.keywords.isNotEmpty()

private fun seerrDetailVideos(detail: JellyseerrMediaDetail): List<JellyseerrMediaVideo> =
    detail.videos
        .filter { it.key.isNotBlank() }
        .ifEmpty {
            detail.trailer
                ?.takeIf { !it.key.isNullOrBlank() }
                ?.let { trailer ->
                    listOf(
                        JellyseerrMediaVideo(
                            id = null,
                            name = trailer.name,
                            site = trailer.site,
                            type = trailer.type,
                            key = trailer.key.orEmpty(),
                            url = trailer.url,
                            official = true,
                            publishedAt = null,
                        ),
                    )
                }.orEmpty()
        }.distinctBy { it.site to it.key }

private fun seerrDetailFacts(detail: JellyseerrMediaDetail): List<String> =
    listOfNotNull(
        detail.year?.takeIf { it.isNotBlank() },
        detail.certification?.takeIf { it.isNotBlank() },
        detail.runtimeMinutes?.takeIf { it > 0 }?.let(::seerrRuntime),
    )

@Composable
private fun ratingEntries(ratings: JellyseerrMediaRatings): List<Pair<String, String>> =
    listOfNotNull(
        ratings.tmdb?.let {
            stringResource(Res.string.rating_tmdb, formatScore(it)).substringBefore(" ") to
                formatScore(it)
        },
        ratings.imdb?.let {
            stringResource(Res.string.rating_imdb, formatScore(it)).substringBefore(" ") to
                formatScore(it)
        },
        ratings.rottenTomatoesCritics?.let {
            stringResource(Res.string.rating_rt_critics, formatPercent(it)).substringBeforeLast(" ") to
                formatPercent(it)
        },
        ratings.rottenTomatoesAudience?.let {
            stringResource(Res.string.rating_rt_audience, formatPercent(it)).substringBeforeLast(" ") to
                formatPercent(it)
        },
    )

private fun formatScore(value: Double): String = "${(value * 10).roundToInt() / 10.0}"

private fun formatPercent(value: Double): String = "${value.roundToInt()}%"

private fun seerrRuntime(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "$minutes min"
}

private fun formatMoney(value: Long): String =
    when {
        value >= 1_000_000_000L -> "$${value / 100_000_000L / 10.0}B"
        value >= 1_000_000L -> "$${value / 100_000L / 10.0}M"
        value >= 1_000L -> "$${value / 1_000L}K"
        else -> "$$value"
    }

private val KEY_CREW_ROLES =
    listOf(
        "director",
        "creator",
        "showrunner",
        "executive producer",
        "writer",
        "screenplay",
    )
