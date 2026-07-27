@file:Suppress("FunctionName")
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.jellystack.design.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.downloads.DownloadStatus
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyfin.MediaDetailEnrichment
import dev.jellystack.design.components.CinematicCommandDeckSurface
import dev.jellystack.design.components.CinematicDetailColors
import dev.jellystack.design.components.CinematicDetailTab
import dev.jellystack.design.components.CinematicDetailTabs
import dev.jellystack.design.components.CinematicDetailTheme
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleTrack
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.add_to_favorites
import jellystack_mobile.design.generated.resources.all_seasons
import jellystack_mobile.design.generated.resources.audio
import jellystack_mobile.design.generated.resources.default_label
import jellystack_mobile.design.generated.resources.detail_accessibility
import jellystack_mobile.design.generated.resources.detail_audience_rating
import jellystack_mobile.design.generated.resources.detail_audio_and_subtitles
import jellystack_mobile.design.generated.resources.detail_back
import jellystack_mobile.design.generated.resources.detail_cast
import jellystack_mobile.design.generated.resources.detail_countries
import jellystack_mobile.design.generated.resources.detail_critic_rating
import jellystack_mobile.design.generated.resources.detail_episode_label
import jellystack_mobile.design.generated.resources.detail_external
import jellystack_mobile.design.generated.resources.detail_extras
import jellystack_mobile.design.generated.resources.detail_forced_subtitles
import jellystack_mobile.design.generated.resources.detail_info
import jellystack_mobile.design.generated.resources.detail_jellyfin_rating
import jellystack_mobile.design.generated.resources.detail_loading_more
import jellystack_mobile.design.generated.resources.detail_manage_download
import jellystack_mobile.design.generated.resources.detail_more_options
import jellystack_mobile.design.generated.resources.detail_no_extras
import jellystack_mobile.design.generated.resources.detail_open_trailer
import jellystack_mobile.design.generated.resources.detail_original_language
import jellystack_mobile.design.generated.resources.detail_original_title
import jellystack_mobile.design.generated.resources.detail_ratings
import jellystack_mobile.design.generated.resources.detail_release
import jellystack_mobile.design.generated.resources.detail_similar
import jellystack_mobile.design.generated.resources.detail_video
import jellystack_mobile.design.generated.resources.download
import jellystack_mobile.design.generated.resources.favorite
import jellystack_mobile.design.generated.resources.forced_label
import jellystack_mobile.design.generated.resources.mark_as_seen
import jellystack_mobile.design.generated.resources.mark_as_unseen
import jellystack_mobile.design.generated.resources.off
import jellystack_mobile.design.generated.resources.overview
import jellystack_mobile.design.generated.resources.remove_from_favorites
import jellystack_mobile.design.generated.resources.season_number
import jellystack_mobile.design.generated.resources.seen
import jellystack_mobile.design.generated.resources.specials
import jellystack_mobile.design.generated.resources.studios
import jellystack_mobile.design.generated.resources.subtitles
import jellystack_mobile.design.generated.resources.unseen
import jellystack_mobile.design.generated.resources.view_series
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal object ImmersiveDetailTestTags {
    const val ROOT = "immersive_detail_root"
    const val HERO = "immersive_detail_hero"
    const val LOGO = "immersive_detail_logo"
    const val TITLE = "immersive_detail_title"
    const val COMMAND_DECK = "immersive_detail_command_deck"
    const val TABS = "immersive_detail_tabs"
    const val RATINGS = "immersive_detail_ratings"
    const val CAST = "immersive_detail_cast"
    const val SIMILAR = "immersive_detail_similar"
}

private enum class DetailSection {
    Overview,
    Extras,
    Info,
}

private enum class DetailTrackPicker {
    Audio,
    Subtitles,
}

private sealed interface SeriesDownloadScope {
    data object AllSeasons : SeriesDownloadScope

    data class Season(
        val group: SeasonEpisodes,
    ) : SeriesDownloadScope
}

private val CinematicBackground = CinematicDetailColors.background
private val CinematicSurface = CinematicDetailColors.surface
private val CinematicSurfaceHigh = CinematicDetailColors.surfaceHigh
private val CinematicPrimary = CinematicDetailColors.primary
private val CinematicOnSurface = CinematicDetailColors.onSurface
private val CinematicMuted = CinematicDetailColors.muted

@Composable
internal fun ImmersiveMediaDetailContent(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    enrichment: MediaDetailEnrichment = MediaDetailEnrichment(),
    enrichmentLoading: Boolean = false,
    baseUrl: String? = null,
    accessToken: String? = null,
    seasons: List<SeasonEpisodes> = emptyList(),
    onBack: () -> Unit = {},
    onPlay: () -> Unit = {},
    onTrailer: (() -> Unit)? = null,
    showPlayAction: Boolean = true,
    playActionLabel: String = "Play",
    playActionEnabled: Boolean = true,
    playActionLoading: Boolean = false,
    emptyPlaybackMessage: String? = null,
    downloadStatus: DownloadStatus? = null,
    episodeDownloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onQueueDownload: () -> Unit = {},
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onDownloadSeries: (() -> Unit)? = null,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)? = null,
    onViewSeries: (() -> Unit)? = null,
    onOpenEpisode: ((JellyfinItem) -> Unit)? = null,
    onOpenItemDetail: (JellyfinItem) -> Unit = {},
    audioTracks: List<AudioTrack> = emptyList(),
    selectedAudioTrack: AudioTrack? = null,
    onSelectAudioTrack: (AudioTrack) -> Unit = {},
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleTrack: SubtitleTrack? = null,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    favoriteError: String? = null,
    showPlayedAction: Boolean = true,
    isPlayed: Boolean = detail.isPlayed,
    playedPending: Boolean = false,
    onTogglePlayed: () -> Unit = {},
    playedError: String? = null,
    modifier: Modifier = Modifier,
) {
    val stateHolder = rememberSaveableStateHolder()
    CinematicDetailTheme {
        stateHolder.SaveableStateProvider(item.id) {
            var sectionName by rememberSaveable { mutableStateOf(DetailSection.Overview.name) }
            val requestedSection =
                runCatching { DetailSection.valueOf(sectionName) }
                    .getOrDefault(DetailSection.Overview)
            val sections =
                remember(onTrailer) {
                    buildList {
                        add(DetailSection.Overview)
                        if (onTrailer != null) add(DetailSection.Extras)
                        add(DetailSection.Info)
                    }
                }
            val selectedSection =
                requestedSection.takeIf { it in sections } ?: DetailSection.Overview
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(CinematicBackground)
                        .testTag(ImmersiveDetailTestTags.ROOT),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item(key = "hero") {
                    DetailHeroAndCommandDeck(
                        item = item,
                        detail = detail,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        onBack = onBack,
                        onPlay = onPlay,
                        onTrailer = onTrailer,
                        showPlayAction = showPlayAction,
                        playActionLabel = playActionLabel,
                        playActionEnabled = playActionEnabled,
                        playActionLoading = playActionLoading,
                        downloadStatus = downloadStatus,
                        onQueueDownload = onQueueDownload,
                        onPauseDownload = onPauseDownload,
                        onResumeDownload = onResumeDownload,
                        onRemoveDownload = onRemoveDownload,
                        onDownloadSeries = onDownloadSeries,
                        seriesSeasons = seasons,
                        onDownloadSeason = onDownloadSeason,
                        audioTracks = audioTracks,
                        selectedAudioTrack = selectedAudioTrack,
                        onSelectAudioTrack = onSelectAudioTrack,
                        subtitleTracks = subtitleTracks,
                        selectedSubtitleTrack = selectedSubtitleTrack,
                        onSelectSubtitleTrack = onSelectSubtitleTrack,
                        onViewSeries = onViewSeries,
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        showPlayedAction = showPlayedAction,
                        isPlayed = isPlayed,
                        playedPending = playedPending,
                        onTogglePlayed = onTogglePlayed,
                    )
                }
                item(key = "messages") {
                    DetailMessages(
                        emptyPlaybackMessage = emptyPlaybackMessage,
                        favoriteError = favoriteError,
                        playedError = playedError,
                        downloadStatus = downloadStatus,
                    )
                }
                item(key = "facts") {
                    DetailFactStrip(
                        item = item,
                        detail = detail,
                        enrichment = enrichment,
                        modifier = Modifier.detailHorizontalPadding(),
                    )
                }
                item(key = "tabs") {
                    DetailSectionTabs(
                        sections = sections,
                        selected = selectedSection,
                        onSelect = { sectionName = it.name },
                        modifier = Modifier.detailHorizontalPadding(),
                    )
                }
                when (selectedSection) {
                    DetailSection.Overview -> {
                        item(key = "overview") {
                            OverviewSection(
                                detail = detail,
                                enrichment = enrichment,
                                enrichmentLoading = enrichmentLoading,
                                modifier = Modifier.detailHorizontalPadding(),
                            )
                        }
                        if (detail.people.isNotEmpty()) {
                            item(key = "cast") {
                                PeopleSection(
                                    detail = detail,
                                    baseUrl = baseUrl,
                                    accessToken = accessToken,
                                )
                            }
                        }
                        if (seasons.isNotEmpty()) {
                            item(key = "seasons") {
                                SeasonEpisodeSelector(
                                    seasons = seasons,
                                    baseUrl = baseUrl,
                                    accessToken = accessToken,
                                    modifier = Modifier.detailHorizontalPadding(),
                                    episodeDownloadStatuses = episodeDownloadStatuses,
                                    onDownloadSeason = onDownloadSeason,
                                    onOpenEpisode = onOpenEpisode,
                                )
                            }
                        }
                        if (enrichment.similarItems.isNotEmpty()) {
                            item(key = "similar") {
                                SimilarSection(
                                    items = enrichment.similarItems,
                                    baseUrl = baseUrl,
                                    accessToken = accessToken,
                                    onOpenItemDetail = onOpenItemDetail,
                                )
                            }
                        }
                    }

                    DetailSection.Extras ->
                        item(key = "extras") {
                            ExtrasSection(
                                item = item,
                                detail = detail,
                                baseUrl = baseUrl,
                                accessToken = accessToken,
                                onTrailer = onTrailer,
                                modifier = Modifier.detailHorizontalPadding(),
                            )
                        }

                    DetailSection.Info ->
                        item(key = "info") {
                            InfoSection(
                                detail = detail,
                                enrichment = enrichment,
                                modifier = Modifier.detailHorizontalPadding(),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun DetailHeroAndCommandDeck(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onTrailer: (() -> Unit)?,
    showPlayAction: Boolean,
    playActionLabel: String,
    playActionEnabled: Boolean,
    playActionLoading: Boolean,
    downloadStatus: DownloadStatus?,
    onQueueDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownloadSeries: (() -> Unit)?,
    seriesSeasons: List<SeasonEpisodes>,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)?,
    audioTracks: List<AudioTrack>,
    selectedAudioTrack: AudioTrack?,
    onSelectAudioTrack: (AudioTrack) -> Unit,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleTrack: SubtitleTrack?,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit,
    onViewSeries: (() -> Unit)?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    showPlayedAction: Boolean,
    isPlayed: Boolean,
    playedPending: Boolean,
    onTogglePlayed: () -> Unit,
) {
    val profile = LocalResponsiveProfile.current
    val heroHeight =
        when {
            profile.isShortHeight -> 280.dp
            profile.isExpanded -> 470.dp
            else -> 330.dp
        }
    val totalHeight = heroHeight + 76.dp
    val heroArtwork = remember(item, detail) { resolveHeroArtwork(item, detail) }
    val logoArtwork = remember(item, detail) { resolveLogoArtwork(item, detail) }
    var logoLoadFailed by remember(logoArtwork) { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(totalHeight),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .testTag(ImmersiveDetailTestTags.HERO),
        ) {
            DetailRemoteImage(
                baseUrl = baseUrl,
                itemId = heroArtwork.itemId,
                imageType = heroArtwork.imageType,
                tag = heroArtwork.tag,
                accessToken = accessToken,
                contentDescription = detail.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.05f),
                                0.48f to Color.Black.copy(alpha = 0.12f),
                                0.78f to CinematicBackground.copy(alpha = 0.62f),
                                1f to CinematicBackground,
                            ),
                        ),
            )
            if (!profile.isExpanded) {
                FloatingHeroIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.detail_back),
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 16.dp),
                )
            }
            MoreActions(
                onTrailer = onTrailer,
                downloadStatus = downloadStatus,
                onRemoveDownload = onRemoveDownload,
                audioTracks = audioTracks,
                selectedAudioTrack = selectedAudioTrack,
                onSelectAudioTrack = onSelectAudioTrack,
                subtitleTracks = subtitleTracks,
                selectedSubtitleTrack = selectedSubtitleTrack,
                onSelectSubtitleTrack = onSelectSubtitleTrack,
                onViewSeries = onViewSeries,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 16.dp),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = profile.horizontalContentPadding)
                        .padding(bottom = 66.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (logoArtwork != null && !logoLoadFailed) {
                    DetailRemoteImage(
                        baseUrl = baseUrl,
                        itemId = logoArtwork.itemId,
                        imageType = "Logo",
                        tag = logoArtwork.tag,
                        accessToken = accessToken,
                        contentDescription = detail.name,
                        contentScale = ContentScale.Fit,
                        onError = { logoLoadFailed = true },
                        modifier =
                            Modifier
                                .fillMaxWidth(0.66f)
                                .heightIn(min = 72.dp, max = 118.dp)
                                .testTag(ImmersiveDetailTestTags.LOGO),
                    )
                } else {
                    Text(
                        text = detail.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = CinematicOnSurface,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .testTag(ImmersiveDetailTestTags.TITLE)
                                .semantics { heading() },
                    )
                }
                if (item.type.equals("Episode", ignoreCase = true)) {
                    val seasonNumber = item.parentIndexNumber
                    val episodeNumber = item.indexNumber
                    Text(
                        text =
                            buildList {
                                item.seriesName?.takeIf { it.isNotBlank() }?.let(::add)
                                if (seasonNumber != null && episodeNumber != null) {
                                    add(
                                        stringResource(
                                            Res.string.detail_episode_label,
                                            seasonNumber,
                                            episodeNumber,
                                        ),
                                    )
                                }
                            }.joinToString(" · "),
                        color = CinematicMuted,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                } else if (detail.genres.isNotEmpty()) {
                    Text(
                        text = detail.genres.take(3).joinToString(" · "),
                        color = CinematicMuted,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        CommandDeck(
            showPlayAction = showPlayAction,
            playActionLabel = playActionLabel,
            playActionEnabled = playActionEnabled,
            playActionLoading = playActionLoading,
            onPlay = onPlay,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            downloadStatus = downloadStatus,
            onQueueDownload = onQueueDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onDownloadSeries = onDownloadSeries,
            seriesSeasons = seriesSeasons,
            onDownloadSeason = onDownloadSeason,
            showPlayedAction = showPlayedAction,
            isPlayed = isPlayed,
            playedPending = playedPending,
            onTogglePlayed = onTogglePlayed,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = profile.horizontalContentPadding),
        )
    }
}

@Composable
private fun CommandDeck(
    showPlayAction: Boolean,
    playActionLabel: String,
    playActionEnabled: Boolean,
    playActionLoading: Boolean,
    onPlay: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    downloadStatus: DownloadStatus?,
    onQueueDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    onDownloadSeries: (() -> Unit)?,
    seriesSeasons: List<SeasonEpisodes>,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)?,
    showPlayedAction: Boolean,
    isPlayed: Boolean,
    playedPending: Boolean,
    onTogglePlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var downloadScopeOpen by remember { mutableStateOf(false) }
    val downloadCommand =
        when (downloadStatus) {
            is DownloadStatus.InProgress -> Triple(Icons.Filled.Pause, true, onPauseDownload)
            is DownloadStatus.Paused -> Triple(Icons.Filled.Refresh, true, onResumeDownload)
            is DownloadStatus.Completed -> Triple(Icons.Filled.CloudDone, false, onQueueDownload)
            is DownloadStatus.Queued,
            is DownloadStatus.WaitingForNetwork,
            -> Triple(Icons.Filled.Download, false, onQueueDownload)
            else -> Triple(Icons.Filled.Download, true, onQueueDownload)
        }
    val canChooseSeriesScope =
        onDownloadSeries != null &&
            downloadStatus !is DownloadStatus.InProgress &&
            downloadStatus !is DownloadStatus.Paused &&
            downloadStatus !is DownloadStatus.Completed
    CinematicCommandDeckSurface(
        modifier = modifier,
        testTag = ImmersiveDetailTestTags.COMMAND_DECK,
    ) {
        if (showPlayAction) {
            Button(
                onClick = onPlay,
                enabled = playActionEnabled && !playActionLoading,
                modifier =
                    Modifier
                        .weight(1.65f)
                        .height(60.dp)
                        .testTag(DetailActionTestTags.PRIMARY),
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = CinematicPrimary,
                        contentColor = Color(0xFF21113E),
                    ),
            ) {
                if (playActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = playActionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CommandAction(
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = stringResource(Res.string.favorite),
            contentDescription =
                stringResource(
                    if (isFavorite) {
                        Res.string.remove_from_favorites
                    } else {
                        Res.string.add_to_favorites
                    },
                ),
            onClick = onToggleFavorite,
            modifier = Modifier.weight(0.58f).testTag(DetailActionTestTags.FAVORITE),
        )
        CommandAction(
            icon = downloadCommand.first,
            label = stringResource(Res.string.download),
            contentDescription = stringResource(Res.string.download),
            enabled = downloadCommand.second,
            onClick = {
                if (canChooseSeriesScope) {
                    downloadScopeOpen = true
                } else {
                    downloadCommand.third()
                }
            },
            modifier = Modifier.weight(0.58f).testTag(DetailActionTestTags.DOWNLOAD),
        )
        if (showPlayedAction) {
            CommandAction(
                icon = if (isPlayed) Icons.Filled.VisibilityOff else Icons.Filled.CheckCircle,
                label =
                    stringResource(
                        if (isPlayed) {
                            Res.string.unseen
                        } else {
                            Res.string.seen
                        },
                    ),
                contentDescription =
                    stringResource(
                        if (isPlayed) {
                            Res.string.mark_as_unseen
                        } else {
                            Res.string.mark_as_seen
                        },
                    ),
                enabled = !playedPending,
                onClick = onTogglePlayed,
                modifier = Modifier.weight(0.58f).testTag(DetailActionTestTags.PLAYED),
            )
        }
    }
    if (downloadScopeOpen && onDownloadSeries != null) {
        TrackPickerDialog(
            label = stringResource(Res.string.download),
            options =
                listOf(
                    TrackPickerOption<SeriesDownloadScope>(
                        value = SeriesDownloadScope.AllSeasons,
                        fullLabel = stringResource(Res.string.all_seasons),
                        selected = false,
                    ),
                ) +
                    seriesSeasons.takeIf { onDownloadSeason != null }.orEmpty().map { season ->
                        TrackPickerOption<SeriesDownloadScope>(
                            value = SeriesDownloadScope.Season(season),
                            fullLabel =
                                season.seasonNumber?.let { number ->
                                    stringResource(Res.string.season_number, number)
                                } ?: stringResource(Res.string.specials),
                            selected = false,
                        )
                    },
            onSelect = { scope ->
                when (scope) {
                    SeriesDownloadScope.AllSeasons -> onDownloadSeries()
                    is SeriesDownloadScope.Season -> onDownloadSeason?.invoke(scope.group)
                }
            },
            onDismissRequest = { downloadScopeOpen = false },
        )
    }
}

@Composable
private fun CommandAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .heightIn(min = 60.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            color = CinematicSurface,
            contentColor = if (enabled) CinematicPrimary else CinematicMuted.copy(alpha = 0.45f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Text(
            text = label,
            color = if (enabled) CinematicOnSurface else CinematicMuted.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreActions(
    onTrailer: (() -> Unit)?,
    downloadStatus: DownloadStatus?,
    onRemoveDownload: () -> Unit,
    audioTracks: List<AudioTrack>,
    selectedAudioTrack: AudioTrack?,
    onSelectAudioTrack: (AudioTrack) -> Unit,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleTrack: SubtitleTrack?,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit,
    onViewSeries: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPicker by remember { mutableStateOf<DetailTrackPicker?>(null) }
    Box(modifier = modifier) {
        FloatingHeroIcon(
            icon = Icons.Filled.MoreHoriz,
            contentDescription = stringResource(Res.string.detail_more_options),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CinematicSurfaceHigh,
        ) {
            if (audioTracks.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "${stringResource(Res.string.audio)} · " +
                                (selectedAudioTrack?.displayName() ?: audioTracks.first().displayName()),
                        )
                    },
                    onClick = {
                        expanded = false
                        selectedPicker = DetailTrackPicker.Audio
                    },
                )
            }
            if (subtitleTracks.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "${stringResource(Res.string.subtitles)} · " +
                                (selectedSubtitleTrack?.displayName() ?: stringResource(Res.string.off)),
                        )
                    },
                    onClick = {
                        expanded = false
                        selectedPicker = DetailTrackPicker.Subtitles
                    },
                )
            }
            onTrailer?.let { playTrailer ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.detail_open_trailer)) },
                    onClick = {
                        expanded = false
                        playTrailer()
                    },
                )
            }
            onViewSeries?.let { viewSeries ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.view_series)) },
                    onClick = {
                        expanded = false
                        viewSeries()
                    },
                )
            }
            if (downloadStatus != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.detail_manage_download)) },
                    onClick = {
                        expanded = false
                        onRemoveDownload()
                    },
                )
            }
        }
        when (selectedPicker) {
            DetailTrackPicker.Audio ->
                TrackPickerDialog(
                    label = stringResource(Res.string.audio),
                    options =
                        audioTracks.map { track ->
                            TrackPickerOption<AudioTrack>(
                                value = track,
                                fullLabel = track.displayName(),
                                selected = track.id == selectedAudioTrack?.id,
                            )
                        },
                    onSelect = onSelectAudioTrack,
                    onDismissRequest = { selectedPicker = null },
                )

            DetailTrackPicker.Subtitles ->
                TrackPickerDialog(
                    label = stringResource(Res.string.subtitles),
                    options =
                        listOf(
                            TrackPickerOption<SubtitleSelection>(
                                value = SubtitleSelection.Off,
                                fullLabel = stringResource(Res.string.off),
                                selected = selectedSubtitleTrack == null,
                            ),
                        ) +
                            subtitleTracks.map { track ->
                                TrackPickerOption<SubtitleSelection>(
                                    value = SubtitleSelection.Track(track),
                                    fullLabel = track.displayName(),
                                    selected = track.id == selectedSubtitleTrack?.id,
                                )
                            },
                    onSelect = { selection ->
                        onSelectSubtitleTrack((selection as? SubtitleSelection.Track)?.track)
                    },
                    onDismissRequest = { selectedPicker = null },
                )

            null -> Unit
        }
    }
}

@Composable
private fun FloatingHeroIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(52.dp),
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        shape = CircleShape,
        shadowElevation = 8.dp,
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun DetailMessages(
    emptyPlaybackMessage: String?,
    favoriteError: String?,
    playedError: String?,
    downloadStatus: DownloadStatus?,
) {
    val messages =
        buildList {
            emptyPlaybackMessage?.takeIf { it.isNotBlank() }?.let(::add)
            favoriteError?.takeIf { it.isNotBlank() }?.let(::add)
            playedError?.takeIf { it.isNotBlank() }?.let(::add)
            when (downloadStatus) {
                is DownloadStatus.Failed -> downloadStatus.cause.message?.let(::add)
                else -> Unit
            }
        }
    if (messages.isEmpty()) return
    Column(
        modifier = Modifier.detailHorizontalPadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        messages.forEach { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CinematicSurface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(14.dp),
                    color = CinematicMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DetailFactStrip(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    enrichment: MediaDetailEnrichment,
    modifier: Modifier = Modifier,
) {
    val video = detail.mediaSources.flatMap { it.streams }.firstOrNull { it.type == JellyfinMediaStreamType.VIDEO }
    val audio = detail.mediaSources.flatMap { it.streams }.firstOrNull { it.type == JellyfinMediaStreamType.AUDIO }
    val facts =
        buildList {
            (detail.productionYear?.toString() ?: enrichment.seerrDetail?.year)
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            (
                detail.runTimeTicks?.let(::detailRuntime)
                    ?: enrichment.seerrDetail
                        ?.runtimeMinutes
                        ?.takeIf { it > 0 }
                        ?.let(::detailRuntimeMinutes)
            )?.takeIf { it.isNotBlank() }?.let(::add)
            detail.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
            video?.height?.takeIf { it > 0 }?.let { add("${it}p") }
            video
                ?.videoRangeType
                ?.takeUnless { it.equals("SDR", ignoreCase = true) }
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            if (
                listOf(video?.codec, video?.profile, audio?.codec, audio?.profile)
                    .filterNotNull()
                    .any { it.contains("dolby", true) || it.contains("dovi", true) || it.contains("atmos", true) }
            ) {
                add("Dolby")
            }
            if (item.type.equals("Episode", true) && item.parentIndexNumber != null && item.indexNumber != null) {
                add("S${item.parentIndexNumber} · E${item.indexNumber}")
            }
        }
    if (facts.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        itemsIndexed(facts) { index, fact ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fact,
                    color = CinematicOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (index < facts.lastIndex) {
                    Text(
                        text = "•",
                        color = CinematicPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSectionTabs(
    sections: List<DetailSection>,
    selected: DetailSection,
    onSelect: (DetailSection) -> Unit,
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
                                DetailSection.Overview -> Res.string.overview
                                DetailSection.Extras -> Res.string.detail_extras
                                DetailSection.Info -> Res.string.detail_info
                            },
                        ),
                    testTag = "immersive_detail_tab_${section.name.lowercase()}",
                )
            },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        testTag = ImmersiveDetailTestTags.TABS,
    )
}

@Composable
private fun OverviewSection(
    detail: JellyfinItemDetail,
    enrichment: MediaDetailEnrichment,
    enrichmentLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        detail.taglines.firstOrNull()?.takeIf { it.isNotBlank() }?.let { tagline ->
            Text(
                text = tagline,
                color = CinematicPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        detail.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyLarge,
                color = CinematicOnSurface,
            )
        }
        RatingSection(detail = detail, enrichment = enrichment)
        if (enrichmentLoading && enrichment.seerrDetail == null && enrichment.similarItems.isEmpty()) {
            Text(
                text = stringResource(Res.string.detail_loading_more),
                color = CinematicMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RatingSection(
    detail: JellyfinItemDetail,
    enrichment: MediaDetailEnrichment,
) {
    val ratings =
        buildList {
            detail.communityRating?.takeIf { it > 0 }?.let {
                add(
                    DetailRating(
                        stringResource(Res.string.detail_jellyfin_rating),
                        decimalScore(it),
                    ),
                )
            }
            detail.criticRating?.takeIf { it > 0 }?.let {
                add(DetailRating(stringResource(Res.string.detail_critic_rating), percentScore(it)))
            }
            enrichment.seerrDetail?.ratings?.tmdb?.takeIf { it > 0 }?.let {
                add(DetailRating("TMDB", decimalScore(it)))
            }
            enrichment.seerrDetail?.ratings?.imdb?.takeIf { it > 0 }?.let {
                add(DetailRating("IMDb", decimalScore(it)))
            }
            enrichment.seerrDetail?.ratings?.rottenTomatoesCritics?.takeIf { it > 0 }?.let {
                add(DetailRating("Rotten Tomatoes", percentScore(it)))
            }
            enrichment.seerrDetail?.ratings?.rottenTomatoesAudience?.takeIf { it > 0 }?.let {
                add(DetailRating(stringResource(Res.string.detail_audience_rating), percentScore(it)))
            }
        }.distinctBy { it.label }
    if (ratings.isEmpty()) return
    Column(
        modifier = Modifier.testTag(ImmersiveDetailTestTags.RATINGS),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeading(Res.string.detail_ratings)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CinematicSurfaceHigh,
            contentColor = CinematicOnSurface,
            shape = RoundedCornerShape(22.dp),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(ratings) { index, rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = CinematicPrimary)
                        Column(modifier = Modifier.widthIn(min = 72.dp)) {
                            Text(
                                rating.value,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(rating.label, style = MaterialTheme.typography.labelSmall, color = CinematicMuted)
                        }
                        if (index < ratings.lastIndex) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(CinematicMuted.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleSection(
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ImmersiveDetailTestTags.CAST),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeading(
            resource = Res.string.detail_cast,
            modifier = Modifier.detailHorizontalPadding(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding =
                PaddingValues(
                    horizontal = LocalResponsiveProfile.current.horizontalContentPadding,
                ),
        ) {
            items(detail.people.take(24), key = { "${it.id}:${it.role}" }) { person ->
                Column(
                    modifier = Modifier.width(104.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    PosterImage(
                        modifier = Modifier.size(92.dp).clip(CircleShape),
                        baseUrl = baseUrl,
                        itemId = person.id,
                        primaryTag = person.primaryImageTag,
                        thumbTag = null,
                        backdropTag = null,
                        accessToken = accessToken,
                        contentDescription = person.name,
                    )
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = CinematicOnSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    person.role?.takeIf { it.isNotBlank() }?.let { role ->
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = CinematicMuted,
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
private fun SimilarSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    onOpenItemDetail: (JellyfinItem) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ImmersiveDetailTestTags.SIMILAR),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeading(
            resource = Res.string.detail_similar,
            modifier = Modifier.detailHorizontalPadding(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding =
                PaddingValues(
                    horizontal = LocalResponsiveProfile.current.horizontalContentPadding,
                ),
        ) {
            items(items, key = { it.id }) { similar ->
                Surface(
                    modifier = Modifier.width(148.dp).clickable { onOpenItemDetail(similar) },
                    color = CinematicSurface,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column {
                        PosterImage(
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                            baseUrl = baseUrl,
                            itemId = similar.id,
                            primaryTag = similar.primaryImageTag,
                            thumbTag = similar.thumbImageTag,
                            backdropTag = similar.backdropImageTag,
                            accessToken = accessToken,
                            contentDescription = similar.name,
                        )
                        Text(
                            text = similar.name,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelLarge,
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
private fun ExtrasSection(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
    onTrailer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeading(Res.string.detail_extras)
        if (onTrailer == null) {
            Text(stringResource(Res.string.detail_no_extras), color = CinematicMuted)
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onTrailer),
                color = CinematicSurface,
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    PosterImage(
                        modifier = Modifier.fillMaxSize(),
                        baseUrl = baseUrl,
                        itemId = detail.id,
                        primaryTag = detail.primaryImageTag,
                        thumbTag = item.thumbImageTag,
                        backdropTag = detail.backdropImageTags.firstOrNull(),
                        accessToken = accessToken,
                        contentDescription = detail.name,
                        preferLandscapeArtwork = true,
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                    ),
                                ).padding(18.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(Res.string.detail_open_trailer),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoSection(
    detail: JellyfinItemDetail,
    enrichment: MediaDetailEnrichment,
    modifier: Modifier = Modifier,
) {
    val seerr = enrichment.seerrDetail
    val defaultLabel = stringResource(Res.string.default_label)
    val forcedLabel = stringResource(Res.string.forced_label)
    val externalLabel = stringResource(Res.string.detail_external)
    val forcedSubtitlesLabel = stringResource(Res.string.detail_forced_subtitles)
    val videoStreams =
        detail.mediaSources.flatMap { it.streams }.filter { it.type == JellyfinMediaStreamType.VIDEO }
    val audioStreams =
        detail.mediaSources.flatMap { it.streams }.filter { it.type == JellyfinMediaStreamType.AUDIO }
    val subtitleStreams =
        detail.mediaSources.flatMap { it.streams }.filter { it.type == JellyfinMediaStreamType.SUBTITLE }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SectionHeading(Res.string.detail_info)
        InfoRows(
            rows =
                listOfNotNull(
                    detail.originalTitle?.takeIf { it.isNotBlank() }?.let {
                        stringResource(Res.string.detail_original_title) to it
                    },
                    detail.premiereDate
                        ?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(Res.string.detail_release) to it }
                        ?: seerr?.releaseDate?.takeIf { it.isNotBlank() }?.let {
                            stringResource(Res.string.detail_release) to it
                        },
                    detail.originalLanguage
                        ?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(Res.string.detail_original_language) to it }
                        ?: seerr?.originalLanguage?.takeIf { it.isNotBlank() }?.let {
                            stringResource(Res.string.detail_original_language) to it
                        },
                    (detail.productionLocations.ifEmpty { seerr?.productionCountries.orEmpty() })
                        .takeIf { it.isNotEmpty() }
                        ?.let { stringResource(Res.string.detail_countries) to it.joinToString(" · ") },
                    detail.studios
                        .ifEmpty { seerr?.studios.orEmpty() }
                        .takeIf { it.isNotEmpty() }
                        ?.let { stringResource(Res.string.studios) to it.joinToString(" · ") },
                ),
        )
        if (detail.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.tags.take(18).forEach { tag ->
                    Surface(
                        color = CinematicSurfaceHigh,
                        contentColor = CinematicMuted,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "#$tag",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        if (videoStreams.isNotEmpty()) {
            TechnicalGroup(
                heading = Res.string.detail_video,
                values = videoStreams.distinctBy(::streamIdentity).map(::videoStreamSummary),
            )
        }
        if (audioStreams.isNotEmpty() || subtitleStreams.isNotEmpty()) {
            TechnicalGroup(
                heading = Res.string.detail_audio_and_subtitles,
                values =
                    audioStreams.distinctBy(::streamIdentity).map {
                        audioStreamSummary(it, defaultLabel)
                    } +
                        subtitleStreams.distinctBy(::streamIdentity).map {
                            subtitleStreamSummary(
                                stream = it,
                                defaultLabel = defaultLabel,
                                forcedLabel = forcedLabel,
                                externalLabel = externalLabel,
                            )
                        },
            )
        }
        val accessibility =
            subtitleStreams
                .filter { it.isHearingImpaired || it.isForced }
                .distinctBy(::streamIdentity)
                .map { accessibilitySummary(it, forcedSubtitlesLabel) }
        if (accessibility.isNotEmpty()) {
            TechnicalGroup(
                heading = Res.string.detail_accessibility,
                values = accessibility,
            )
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = CinematicOnSurface)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = CinematicMuted)
            }
        }
    }
}

@Composable
private fun TechnicalGroup(
    heading: StringResource,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(heading)
        values.forEach { value ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CinematicSurface,
                contentColor = CinematicMuted,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    resource: StringResource,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(resource),
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Black,
        color = CinematicOnSurface,
    )
}

@Composable
private fun DetailRemoteImage(
    baseUrl: String?,
    itemId: String,
    imageType: String,
    tag: String?,
    accessToken: String?,
    contentDescription: String,
    contentScale: ContentScale,
    alignment: Alignment = Alignment.Center,
    onError: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val url =
        remember(baseUrl, itemId, imageType, tag, accessToken) {
            buildImageUrl(baseUrl, itemId, tag, imageType, accessToken)
        }
    val context = LocalPlatformContext.current
    if (url == null) {
        Box(
            modifier =
                modifier.background(
                    Brush.verticalGradient(
                        listOf(CinematicSurfaceHigh, CinematicBackground),
                    ),
                ),
        )
    } else {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            onError = { onError?.invoke() },
            modifier = modifier,
        )
    }
}

internal data class DetailArtwork(
    val itemId: String,
    val imageType: String,
    val tag: String?,
)

internal fun resolveHeroArtwork(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
): DetailArtwork {
    val isEpisode = item.type.equals("Episode", ignoreCase = true)
    val seriesId = item.seriesId?.takeIf { it.isNotBlank() }
    return when {
        isEpisode && seriesId != null && !item.seriesBackdropImageTag.isNullOrBlank() ->
            DetailArtwork(seriesId, "Backdrop", item.seriesBackdropImageTag)
        isEpisode && seriesId != null && detail.parentBackdropImageTags.firstOrNull() != null ->
            DetailArtwork(seriesId, "Backdrop", detail.parentBackdropImageTags.first())
        detail.backdropImageTags.firstOrNull() != null ->
            DetailArtwork(detail.id, "Backdrop", detail.backdropImageTags.first())
        item.backdropImageTag != null -> DetailArtwork(item.id, "Backdrop", item.backdropImageTag)
        isEpisode && seriesId != null && item.seriesPrimaryImageTag != null ->
            DetailArtwork(seriesId, "Primary", item.seriesPrimaryImageTag)
        detail.primaryImageTag != null -> DetailArtwork(detail.id, "Primary", detail.primaryImageTag)
        else -> DetailArtwork(item.id, "Primary", item.primaryImageTag)
    }
}

internal fun resolveLogoArtwork(
    item: JellyfinItem,
    detail: JellyfinItemDetail,
): DetailArtwork? {
    val detailLogo = detail.logoImageTag
    if (!detailLogo.isNullOrBlank()) return DetailArtwork(detail.id, "Logo", detailLogo)
    val seriesLogo = item.seriesLogoImageTag ?: item.parentLogoImageTag
    if (item.type.equals("Episode", true) && !seriesLogo.isNullOrBlank()) {
        return DetailArtwork(item.seriesId ?: item.id, "Logo", seriesLogo)
    }
    return item.logoImageTag?.takeIf { it.isNotBlank() }?.let {
        DetailArtwork(item.id, "Logo", it)
    }
}

private data class DetailRating(
    val label: String,
    val value: String,
)

private fun decimalScore(value: Double): String = "${(value * 10).roundToInt() / 10.0}"

private fun percentScore(value: Double): String = "${value.roundToInt()}%"

private fun detailRuntime(ticks: Long): String {
    val totalMinutes = (ticks / 600_000_000L).toInt()
    return detailRuntimeMinutes(totalMinutes)
}

private fun detailRuntimeMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return ""
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "$minutes min"
}

private fun streamIdentity(stream: JellyfinMediaStream): String =
    listOf(
        stream.type.name,
        stream.index,
        stream.codec,
        stream.language,
        stream.displayTitle,
    ).joinToString(":")

private fun videoStreamSummary(stream: JellyfinMediaStream): String =
    buildList {
        stream.height?.let { add("${it}p") }
        stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        stream.profile?.takeIf { it.isNotBlank() }?.let(::add)
        stream.bitrate?.takeIf { it > 0 }?.let { add(detailBitrate(it)) }
        stream.videoRangeType
            ?.takeIf { it.isNotBlank() && !it.equals("SDR", true) }
            ?.let(::add)
        stream.averageFrameRate?.takeIf { it > 0 }?.let { add("${decimalScore(it)} fps") }
        stream.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
    }.joinToString(" · ")

private fun audioStreamSummary(
    stream: JellyfinMediaStream,
    defaultLabel: String,
): String =
    buildList {
        stream.language?.takeIf { it.isNotBlank() }?.let(::add)
        stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        stream.profile?.takeIf { it.isNotBlank() }?.let(::add)
        stream.channels?.takeIf { it > 0 }?.let { add("$it ch") }
        stream.channelLayout?.takeIf { it.isNotBlank() }?.let(::add)
        stream.bitrate?.takeIf { it > 0 }?.let { add(detailBitrate(it)) }
        if (stream.isDefault) add(defaultLabel)
    }.joinToString(" · ")

private fun subtitleStreamSummary(
    stream: JellyfinMediaStream,
    defaultLabel: String,
    forcedLabel: String,
    externalLabel: String,
): String =
    buildList {
        stream.language?.takeIf { it.isNotBlank() }?.let(::add)
        stream.codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        if (stream.isForced) add(forcedLabel)
        if (stream.isHearingImpaired) add("SDH")
        if (stream.isExternal) add(externalLabel)
        if (stream.isDefault) add(defaultLabel)
    }.joinToString(" · ")

private fun accessibilitySummary(
    stream: JellyfinMediaStream,
    forcedSubtitlesLabel: String,
): String =
    buildList {
        stream.language?.takeIf { it.isNotBlank() }?.let(::add)
        if (stream.isHearingImpaired) add("SDH")
        if (stream.isForced) add(forcedSubtitlesLabel)
        stream.displayTitle?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")

private fun detailBitrate(bitrate: Int): String =
    if (bitrate >= 1_000_000) {
        "${(bitrate / 100_000).toDouble() / 10.0} Mbps"
    } else {
        "${bitrate / 1000} Kbps"
    }

@Composable
private fun Modifier.detailHorizontalPadding(): Modifier =
    padding(
        horizontal = LocalResponsiveProfile.current.horizontalContentPadding,
        vertical = 10.dp,
    )

private fun AudioTrack.displayName(): String =
    title?.takeIf { it.isNotBlank() }
        ?: language?.takeIf { it.isNotBlank() }
        ?: codec?.takeIf { it.isNotBlank() }
        ?: id

private fun SubtitleTrack.displayName(): String =
    title?.takeIf { it.isNotBlank() }
        ?: language?.takeIf { it.isNotBlank() }
        ?: id
