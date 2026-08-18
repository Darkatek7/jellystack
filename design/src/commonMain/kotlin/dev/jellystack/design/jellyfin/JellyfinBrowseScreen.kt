@file:Suppress("FunctionName")

package dev.jellystack.design.jellyfin

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jellystack.core.downloads.DownloadStatus
import dev.jellystack.core.downloads.OfflineMedia
import dev.jellystack.core.downloads.OfflineMediaKind
import dev.jellystack.core.downloads.OfflineMediaMetadata
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.LibraryLoadErrorKind
import dev.jellystack.core.jellyfin.SpotlightCandidate
import dev.jellystack.core.jellyfin.buildSpotlightCandidates
import dev.jellystack.core.jellyfin.isBrowseContainer
import dev.jellystack.design.components.ImageTextScrim
import dev.jellystack.design.components.ShimmerPlaceholder
import dev.jellystack.design.layout.JellystackWidthClass
import dev.jellystack.design.layout.LocalResponsiveProfile
import dev.jellystack.design.navigation.ShellModalOwner
import dev.jellystack.design.theme.JellystackDimens
import dev.jellystack.design.theme.JellystackLayoutTokens
import dev.jellystack.players.AudioTrack
import dev.jellystack.players.SubtitleTrack
import jellystack_mobile.design.generated.resources.Res
import jellystack_mobile.design.generated.resources.add_to_favorites
import jellystack_mobile.design.generated.resources.audio
import jellystack_mobile.design.generated.resources.audio_track
import jellystack_mobile.design.generated.resources.cancel
import jellystack_mobile.design.generated.resources.clear
import jellystack_mobile.design.generated.resources.clear_search
import jellystack_mobile.design.generated.resources.continue_episode
import jellystack_mobile.design.generated.resources.continue_playback
import jellystack_mobile.design.generated.resources.default_label
import jellystack_mobile.design.generated.resources.download
import jellystack_mobile.design.generated.resources.download_failed
import jellystack_mobile.design.generated.resources.download_queued
import jellystack_mobile.design.generated.resources.download_season
import jellystack_mobile.design.generated.resources.download_waiting
import jellystack_mobile.design.generated.resources.downloaded
import jellystack_mobile.design.generated.resources.downloaded_bytes
import jellystack_mobile.design.generated.resources.downloaded_item
import jellystack_mobile.design.generated.resources.downloads
import jellystack_mobile.design.generated.resources.episode_number
import jellystack_mobile.design.generated.resources.episodes
import jellystack_mobile.design.generated.resources.favorite
import jellystack_mobile.design.generated.resources.favorites
import jellystack_mobile.design.generated.resources.forced_label
import jellystack_mobile.design.generated.resources.home_continue_watching
import jellystack_mobile.design.generated.resources.home_next_up
import jellystack_mobile.design.generated.resources.home_no_upcoming_episodes
import jellystack_mobile.design.generated.resources.home_recent_movies
import jellystack_mobile.design.generated.resources.home_recent_shows
import jellystack_mobile.design.generated.resources.items_count
import jellystack_mobile.design.generated.resources.libraries
import jellystack_mobile.design.generated.resources.library_connect_server_status
import jellystack_mobile.design.generated.resources.library_downloads_empty_body
import jellystack_mobile.design.generated.resources.library_downloads_empty_title
import jellystack_mobile.design.generated.resources.library_empty_body
import jellystack_mobile.design.generated.resources.library_empty_title
import jellystack_mobile.design.generated.resources.library_favorites_empty_body
import jellystack_mobile.design.generated.resources.library_favorites_empty_title
import jellystack_mobile.design.generated.resources.library_libraries_empty_body
import jellystack_mobile.design.generated.resources.library_libraries_empty_title
import jellystack_mobile.design.generated.resources.library_no_matches_body
import jellystack_mobile.design.generated.resources.library_no_matches_title
import jellystack_mobile.design.generated.resources.library_no_media_body
import jellystack_mobile.design.generated.resources.library_no_media_title
import jellystack_mobile.design.generated.resources.library_no_movies
import jellystack_mobile.design.generated.resources.library_no_shows
import jellystack_mobile.design.generated.resources.library_offline_empty_body
import jellystack_mobile.design.generated.resources.library_offline_empty_title
import jellystack_mobile.design.generated.resources.library_select_library_status
import jellystack_mobile.design.generated.resources.library_title
import jellystack_mobile.design.generated.resources.mark_as_seen
import jellystack_mobile.design.generated.resources.mark_as_unseen
import jellystack_mobile.design.generated.resources.media_source
import jellystack_mobile.design.generated.resources.minutes
import jellystack_mobile.design.generated.resources.movies
import jellystack_mobile.design.generated.resources.no_episodes_for_season
import jellystack_mobile.design.generated.resources.off
import jellystack_mobile.design.generated.resources.offline_ready
import jellystack_mobile.design.generated.resources.open_details
import jellystack_mobile.design.generated.resources.open_library_section
import jellystack_mobile.design.generated.resources.other
import jellystack_mobile.design.generated.resources.pause
import jellystack_mobile.design.generated.resources.paused_at
import jellystack_mobile.design.generated.resources.play
import jellystack_mobile.design.generated.resources.play_episode
import jellystack_mobile.design.generated.resources.playback_select_episode
import jellystack_mobile.design.generated.resources.playback_unavailable_episode
import jellystack_mobile.design.generated.resources.profile
import jellystack_mobile.design.generated.resources.ready_to_play
import jellystack_mobile.design.generated.resources.remove_from_favorites
import jellystack_mobile.design.generated.resources.remove_server
import jellystack_mobile.design.generated.resources.resume
import jellystack_mobile.design.generated.resources.retry
import jellystack_mobile.design.generated.resources.retry_download
import jellystack_mobile.design.generated.resources.search_library
import jellystack_mobile.design.generated.resources.season
import jellystack_mobile.design.generated.resources.season_episode_number
import jellystack_mobile.design.generated.resources.season_number
import jellystack_mobile.design.generated.resources.seasons
import jellystack_mobile.design.generated.resources.seen
import jellystack_mobile.design.generated.resources.select_season
import jellystack_mobile.design.generated.resources.series
import jellystack_mobile.design.generated.resources.shows
import jellystack_mobile.design.generated.resources.something_went_wrong
import jellystack_mobile.design.generated.resources.specials
import jellystack_mobile.design.generated.resources.stored_bytes
import jellystack_mobile.design.generated.resources.subtitle_count
import jellystack_mobile.design.generated.resources.subtitles
import jellystack_mobile.design.generated.resources.technical_details
import jellystack_mobile.design.generated.resources.trailer
import jellystack_mobile.design.generated.resources.unseen
import jellystack_mobile.design.generated.resources.video
import jellystack_mobile.design.generated.resources.view_series
import jellystack_mobile.design.generated.resources.watch_trailer
import jellystack_mobile.design.generated.resources.watched_percent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import dev.jellystack.design.navigation.LibraryDestination as ShellLibraryDestination
import dev.jellystack.design.navigation.LibrarySection as ShellLibrarySection

internal object SpotlightTestTags {
    const val HERO = "spotlight_hero"
    const val PAGER = "spotlight_pager"
    const val HOME_LIST = "home_list"
    const val AUTO_CYCLE_PROGRESS = "spotlight_auto_cycle_progress"
    const val TITLE_LOGO = "spotlight_title_logo"
    const val PLAY = "spotlight_play"
    const val DETAILS = "spotlight_details"
}

internal object LibraryCardTestTags {
    const val GRID = "library_grid"
    const val POSTER_CARD = "library_poster_card"
}

internal object DetailActionTestTags {
    const val PRIMARY = "detail_primary_action"
    const val FAVORITE = "detail_favorite_action"
    const val TRAILER = "detail_trailer_action"
    const val DOWNLOAD = "detail_download_action"
    const val PLAYED = "detail_played_action"
}

internal fun supportsPlayedStatus(itemType: String): Boolean = itemType.lowercase() in setOf("episode", "movie", "series")

@OptIn(ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun JellyfinBrowseScreen(
    state: JellyfinHomeState,
    onSelectLibrary: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenDetail: (JellyfinItem) -> Unit,
    onOpenContainer: (JellyfinItem) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onPlayItem: ((JellyfinItem) -> Unit)? = null,
    onConnectServer: () -> Unit,
    selectedSpotlightId: String?,
    onSelectedSpotlightIdChange: (String?) -> Unit,
    onSelectFavorites: () -> Unit = {},
    showLibraryItems: Boolean = true,
    downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    offlineMedia: List<OfflineMedia> = emptyList(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    libraryNavigationState: LibraryNavigationState = LibraryNavigationState(),
    onLibraryNavigationChange: (LibraryNavigationState) -> Unit = {},
    spotlightPainter: Painter? = null,
    spotlightLogoPainter: Painter? = null,
    spotlightAutoAdvanceEnabled: Boolean = true,
    spotlightAutoAdvanceIntervalMillis: Long = 6_000L,
    spotlightEligibilityNow: Instant? = null,
) {
    val shellDestination = libraryNavigationState.destination
    val onRetryLibraryError =
        if (state.libraryErrorKind == LibraryLoadErrorKind.NEXT_PAGE) {
            onLoadMore
        } else {
            onRefresh
        }
    val responsiveProfile = LocalResponsiveProfile.current
    val layoutDirection = LocalLayoutDirection.current
    val listPadding =
        PaddingValues(
            start =
                contentPadding.calculateStartPadding(layoutDirection) +
                    responsiveProfile.horizontalContentPadding,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end =
                contentPadding.calculateEndPadding(layoutDirection) +
                    responsiveProfile.horizontalContentPadding,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )
    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = if (showLibraryItems) state.isLibraryLoading else state.isInitialLoading,
            onRefresh = onRefresh,
        )
    val showsLibrary =
        remember(state.libraries) {
            state.libraries.firstOrNull { library ->
                library.collectionType?.equals("tvshows", ignoreCase = true) == true ||
                    library.collectionType?.equals("series", ignoreCase = true) == true
            }
        }
    val moviesLibrary =
        remember(state.libraries) {
            state.libraries.firstOrNull { library ->
                library.collectionType?.equals("movies", ignoreCase = true) == true
            }
        }
    val selectedLibrary =
        remember(state.libraries, state.selectedLibraryId) {
            state.libraries.firstOrNull { it.id == state.selectedLibraryId }
        }
    val resolvedLibraryId =
        remember(state.selectedLibraryId, showsLibrary, moviesLibrary, state.libraries) {
            val visibleLibraryIds =
                buildSet {
                    showsLibrary?.id?.let(::add)
                    moviesLibrary?.id?.let(::add)
                }
            when {
                visibleLibraryIds.isEmpty() -> state.selectedLibraryId
                state.selectedLibraryId in visibleLibraryIds -> state.selectedLibraryId
                showsLibrary != null -> showsLibrary.id
                moviesLibrary != null -> moviesLibrary.id
                else -> state.selectedLibraryId ?: state.libraries.firstOrNull()?.id
            }
        }
    val resolvedSelectedLibrary =
        remember(state.libraries, resolvedLibraryId) {
            state.libraries.firstOrNull { it.id == resolvedLibraryId }
        }
    val isResolvingVisibleLibrarySelection =
        showLibraryItems &&
            resolvedLibraryId != null &&
            resolvedLibraryId != state.selectedLibraryId
    LaunchedEffect(showLibraryItems, shellDestination, resolvedLibraryId, state.selectedLibraryId) {
        if (
            showLibraryItems &&
            shellDestination == ShellLibraryDestination.Root &&
            resolvedLibraryId != null &&
            resolvedLibraryId != state.selectedLibraryId
        ) {
            onSelectLibrary(resolvedLibraryId)
        }
    }
    val isTvLibrary =
        remember(state.libraryItems, resolvedSelectedLibrary, selectedLibrary) {
            (resolvedSelectedLibrary ?: selectedLibrary)
                ?.collectionType
                ?.equals("tvshows", ignoreCase = true) == true ||
                (resolvedSelectedLibrary ?: selectedLibrary)
                    ?.collectionType
                    ?.equals("series", ignoreCase = true) == true ||
                state.libraryItems.any { item ->
                    item.type.equals("Series", ignoreCase = true) ||
                        item.type.equals("Episode", ignoreCase = true)
                }
        }
    val tvPosterEntries =
        remember(state.libraryItems, isTvLibrary) {
            if (!isTvLibrary) {
                emptyList()
            } else {
                groupTvSeries(state.libraryItems).mapNotNull { group ->
                    val posterItem = group.series ?: group.fallbackEpisode?.toSeriesPlaceholder()
                    val openItem = group.openItem
                    if (posterItem != null && openItem != null) {
                        TvSeriesPosterEntry(id = group.id, poster = posterItem, openItem = openItem)
                    } else {
                        null
                    }
                }
            }
        }

    if (showLibraryItems) {
        val libraryStateHolder = rememberSaveableStateHolder()
        libraryStateHolder.SaveableStateProvider(libraryNavigationState.scrollKey) {
            var searchQuery by
                rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue(libraryNavigationState.searchQuery))
                }
            var debouncedQuery by remember { mutableStateOf(libraryNavigationState.searchQuery.trim()) }
            LaunchedEffect(searchQuery.text) {
                val normalizedQuery = searchQuery.text.trim()
                if (normalizedQuery == debouncedQuery) {
                    return@LaunchedEffect
                }
                if (normalizedQuery.isEmpty()) {
                    debouncedQuery = ""
                } else {
                    delay(250)
                    if (searchQuery.text.trim() == normalizedQuery) {
                        debouncedQuery = normalizedQuery
                    }
                }
            }
            val trimmedQuery = debouncedQuery
            val filteredTvPosterEntries =
                remember(tvPosterEntries, trimmedQuery) {
                    if (trimmedQuery.isEmpty()) {
                        tvPosterEntries
                    } else {
                        tvPosterEntries.filter { entry ->
                            entry.poster.name.contains(trimmedQuery, ignoreCase = true) ||
                                (entry.poster.seriesName?.contains(trimmedQuery, ignoreCase = true) == true)
                        }
                    }
                }
            val filteredLibraryItems =
                remember(state.libraryItems, trimmedQuery) {
                    if (trimmedQuery.isEmpty()) {
                        state.libraryItems
                    } else {
                        state.libraryItems.filter { item ->
                            item.name.contains(trimmedQuery, ignoreCase = true) ||
                                (item.seriesName?.contains(trimmedQuery, ignoreCase = true) == true)
                        }
                    }
                }
            val gridState =
                rememberSaveable(saver = LazyGridState.Saver) {
                    LazyGridState()
                }
            val downloadedMedia =
                remember(offlineMedia) {
                    offlineMedia
                        .asSequence()
                        .filter { media -> media.kind == OfflineMediaKind.VIDEO }
                        .distinctBy { media -> media.mediaId }
                        .sortedBy { media -> media.metadata?.sortName ?: media.metadata?.name ?: media.mediaId }
                        .toList()
                }
            val hasDownloads =
                remember(downloadedMedia) {
                    downloadedMedia.isNotEmpty()
                }
            val isOffline = state.homeErrorMessage?.isNotBlank() == true
            val showSkeleton = state.isLibraryLoading && state.libraryItems.isEmpty()
            val librarySeriesGroups =
                remember(state.libraryItems, state.recentShows, isTvLibrary) {
                    val sourceItems = if (isTvLibrary && state.libraryItems.isNotEmpty()) state.libraryItems else state.recentShows
                    groupTvSeries(sourceItems).take(16)
                }
            val libraryMovieItems =
                remember(state.libraryItems, state.recentMovies, isTvLibrary) {
                    val sourceItems = if (!isTvLibrary && state.libraryItems.isNotEmpty()) state.libraryItems else state.recentMovies
                    sourceItems
                        .asSequence()
                        .filter { item ->
                            item.type.equals("Movie", ignoreCase = true) ||
                                item.mediaType.equals("Video", ignoreCase = true)
                        }.distinctBy { it.id }
                        .take(16)
                        .toList()
                }
            val isPagedLibraryDestination =
                shellDestination == ShellLibraryDestination.Section(ShellLibrarySection.Movies) ||
                    shellDestination == ShellLibraryDestination.Section(ShellLibrarySection.Series) ||
                    shellDestination is ShellLibraryDestination.Library ||
                    shellDestination is ShellLibraryDestination.Children
            LoadMoreListener(
                gridState = gridState,
                shouldLoadMore =
                    isPagedLibraryDestination &&
                        !state.endReached &&
                        !state.isPageLoading &&
                        !state.isLibraryLoading &&
                        state.libraryItems.isNotEmpty(),
                onLoadMore = onLoadMore,
            )
            val setLibraryDestination: (ShellLibraryDestination) -> Unit = { destination ->
                onLibraryNavigationChange(libraryNavigationState.push(destination))
            }
            val openLibraryDestination: (ShellLibrarySection) -> Unit = { section ->
                setLibraryDestination(ShellLibraryDestination.Section(section))
                when (section) {
                    ShellLibrarySection.Movies -> moviesLibrary?.id?.let(onSelectLibrary)
                    ShellLibrarySection.Series -> showsLibrary?.id?.let(onSelectLibrary)
                    ShellLibrarySection.Favorites -> onSelectFavorites()
                    else -> Unit
                }
            }
            val openLibrary: (JellyfinLibrary) -> Unit = { library ->
                onSelectLibrary(library.id)
                val destination: ShellLibraryDestination =
                    when {
                        library.collectionType?.equals("movies", ignoreCase = true) == true ->
                            ShellLibraryDestination.Section(ShellLibrarySection.Movies)
                        library.collectionType?.equals("tvshows", ignoreCase = true) == true ||
                            library.collectionType?.equals("series", ignoreCase = true) == true ->
                            ShellLibraryDestination.Section(ShellLibrarySection.Series)
                        else ->
                            ShellLibraryDestination.Library(
                                libraryId = library.id,
                                title = library.name,
                            )
                    }
                setLibraryDestination(destination)
            }
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState),
            ) {
                Crossfade(
                    targetState = showSkeleton,
                    modifier = Modifier.fillMaxSize(),
                    label = "libraryGrid",
                ) { loading ->
                    if (loading) {
                        LibraryGridSkeleton(contentPadding = listPadding)
                    } else {
                        LazyVerticalGrid(
                            columns =
                                GridCells.Adaptive(
                                    minSize = if (shellDestination == ShellLibraryDestination.Root) 148.dp else 220.dp,
                                ),
                            modifier = Modifier.testTag(LibraryCardTestTags.GRID),
                            state = gridState,
                            contentPadding = listPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            when (shellDestination) {
                                ShellLibraryDestination.Root -> {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibraryLandingHeader()
                                    }
                                    if (
                                        state.homeErrorMessage != null ||
                                        (state.selectedLibraryId == null && state.libraries.isNotEmpty()) ||
                                        state.libraries.isEmpty()
                                    ) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            StatusBanner(
                                                state = state,
                                                errorMessage = state.homeErrorMessage,
                                                onRetry = onRefresh,
                                                onConnect = onConnectServer,
                                            )
                                        }
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibraryQuickActions(
                                            hasDownloads = hasDownloads,
                                            libraryCount = state.libraries.size,
                                            onDownloads = { openLibraryDestination(ShellLibrarySection.Downloads) },
                                            onFavorites = { openLibraryDestination(ShellLibrarySection.Favorites) },
                                            onLibraries = { openLibraryDestination(ShellLibrarySection.Libraries) },
                                        )
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibraryMovieRail(
                                            title = stringResource(Res.string.movies),
                                            items = libraryMovieItems,
                                            baseUrl = state.imageBaseUrl,
                                            accessToken = state.imageAccessToken,
                                            downloadStatuses = downloadStatuses,
                                            onOpenItem = onOpenDetail,
                                            onOpenLibrary = moviesLibrary?.let { { openLibraryDestination(ShellLibrarySection.Movies) } },
                                        )
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibrarySeriesRail(
                                            title = stringResource(Res.string.shows),
                                            groups = librarySeriesGroups,
                                            baseUrl = state.imageBaseUrl,
                                            accessToken = state.imageAccessToken,
                                            downloadStatuses = downloadStatuses,
                                            onOpenItem = onOpenDetail,
                                            onOpenLibrary = showsLibrary?.let { { openLibraryDestination(ShellLibrarySection.Series) } },
                                        )
                                    }
                                    if (
                                        !state.isLibraryLoading &&
                                        !isResolvingVisibleLibrarySelection &&
                                        libraryMovieItems.isEmpty() &&
                                        librarySeriesGroups.isEmpty() &&
                                        !hasDownloads
                                    ) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LibraryEmptyState(
                                                title =
                                                    if (isOffline) {
                                                        stringResource(Res.string.library_offline_empty_title)
                                                    } else {
                                                        stringResource(Res.string.library_empty_title)
                                                    },
                                                subtitle =
                                                    if (isOffline) {
                                                        stringResource(Res.string.library_offline_empty_body)
                                                    } else {
                                                        stringResource(Res.string.library_empty_body)
                                                    },
                                                icon = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.SearchOff,
                                            )
                                        }
                                    }
                                }
                                ShellLibraryDestination.Section(ShellLibrarySection.Downloads) -> {
                                    if (downloadedMedia.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LibraryEmptyState(
                                                title = stringResource(Res.string.library_downloads_empty_title),
                                                subtitle = stringResource(Res.string.library_downloads_empty_body),
                                                icon = Icons.Filled.Download,
                                            )
                                        }
                                    } else {
                                        items(downloadedMedia, key = { it.mediaId }) { media ->
                                            val metadata = media.metadata
                                            if (metadata != null) {
                                                val item = metadata.toJellyfinItem()
                                                LibraryLandscapeGridCard(
                                                    item = item,
                                                    baseUrl = state.imageBaseUrl,
                                                    accessToken = state.imageAccessToken,
                                                    isDownloaded = true,
                                                    onClick = { onOpenDetail(item) },
                                                )
                                            } else {
                                                LegacyDownloadCard(media = media)
                                            }
                                        }
                                    }
                                }
                                ShellLibraryDestination.Section(ShellLibrarySection.Favorites) -> {
                                    val favoriteItems = state.libraryItems
                                    val isFavoritesLoading = state.isLibraryLoading || state.isPageLoading
                                    if (!isFavoritesLoading && favoriteItems.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LibraryEmptyState(
                                                title = stringResource(Res.string.library_favorites_empty_title),
                                                subtitle = stringResource(Res.string.library_favorites_empty_body),
                                                icon = Icons.Filled.FavoriteBorder,
                                            )
                                        }
                                    } else {
                                        items(favoriteItems, key = { it.id }) { item ->
                                            LibraryPosterCard(
                                                item = item,
                                                baseUrl = state.imageBaseUrl,
                                                accessToken = state.imageAccessToken,
                                                isDownloaded =
                                                    downloadStatuses[item.id] is DownloadStatus.Completed,
                                                onClick = { onOpenDetail(item) },
                                            )
                                        }
                                    }
                                }
                                ShellLibraryDestination.Section(ShellLibrarySection.Libraries) -> {
                                    if (state.libraries.isEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LibraryEmptyState(
                                                title = stringResource(Res.string.library_libraries_empty_title),
                                                subtitle = stringResource(Res.string.library_libraries_empty_body),
                                                icon = Icons.Filled.Folder,
                                            )
                                        }
                                    } else {
                                        items(state.libraries, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { library ->
                                            LibraryCollectionRow(
                                                library = library,
                                                selected = library.id == state.selectedLibraryId,
                                                onClick = { openLibrary(library) },
                                            )
                                        }
                                    }
                                }
                                ShellLibraryDestination.Section(ShellLibrarySection.Movies),
                                ShellLibraryDestination.Section(ShellLibrarySection.Series),
                                is ShellLibraryDestination.Library,
                                is ShellLibraryDestination.Children,
                                -> {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibrarySearchField(
                                            query = searchQuery,
                                            onQueryChange = {
                                                searchQuery = it
                                                onLibraryNavigationChange(
                                                    libraryNavigationState.copy(searchQuery = it.text),
                                                )
                                            },
                                            onClear = {
                                                searchQuery = TextFieldValue("")
                                                debouncedQuery = ""
                                                onLibraryNavigationChange(
                                                    libraryNavigationState.copy(searchQuery = ""),
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    val backingItemsExist =
                                        if (isTvLibrary) tvPosterEntries.isNotEmpty() else state.libraryItems.isNotEmpty()
                                    val noMatches =
                                        trimmedQuery.isNotBlank() &&
                                            backingItemsExist &&
                                            if (isTvLibrary) {
                                                filteredTvPosterEntries.isEmpty()
                                            } else {
                                                filteredLibraryItems.isEmpty()
                                            }
                                    val libraryIsEmpty = trimmedQuery.isBlank() && !backingItemsExist
                                    if (!state.isLibraryLoading && (libraryIsEmpty || noMatches)) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            LibraryEmptyState(
                                                title =
                                                    if (noMatches) {
                                                        stringResource(Res.string.library_no_matches_title, trimmedQuery)
                                                    } else {
                                                        stringResource(Res.string.library_no_media_title)
                                                    },
                                                subtitle =
                                                    if (noMatches) {
                                                        stringResource(Res.string.library_no_matches_body)
                                                    } else {
                                                        stringResource(Res.string.library_no_media_body)
                                                    },
                                                icon = Icons.Filled.SearchOff,
                                                action =
                                                    if (noMatches) {
                                                        {
                                                            TextButton(
                                                                onClick = {
                                                                    searchQuery = TextFieldValue("")
                                                                    debouncedQuery = ""
                                                                    onLibraryNavigationChange(
                                                                        libraryNavigationState.copy(searchQuery = ""),
                                                                    )
                                                                },
                                                            ) {
                                                                Text(stringResource(Res.string.clear_search))
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    },
                                            )
                                        }
                                    } else if (isTvLibrary) {
                                        items(filteredTvPosterEntries, key = { it.id }) { entry ->
                                            LibraryLandscapeGridCard(
                                                item = entry.poster,
                                                baseUrl = state.imageBaseUrl,
                                                accessToken = state.imageAccessToken,
                                                isDownloaded =
                                                    downloadStatuses[entry.poster.id] is DownloadStatus.Completed ||
                                                        downloadStatuses[entry.openItem.id] is DownloadStatus.Completed,
                                                onClick = { onOpenDetail(entry.openItem) },
                                            )
                                        }
                                    } else {
                                        items(filteredLibraryItems, key = { it.id }) { item ->
                                            LibraryLandscapeGridCard(
                                                item = item,
                                                baseUrl = state.imageBaseUrl,
                                                accessToken = state.imageAccessToken,
                                                isDownloaded = downloadStatuses[item.id] is DownloadStatus.Completed,
                                                onClick = {
                                                    if (
                                                        (
                                                            shellDestination is ShellLibraryDestination.Library ||
                                                                shellDestination is ShellLibraryDestination.Children
                                                        ) &&
                                                        item.isBrowseContainer()
                                                    ) {
                                                        onOpenContainer(item)
                                                    } else {
                                                        onOpenDetail(item)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    if (state.isPageLoading) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }
                                }
                                is ShellLibraryDestination.Section ->
                                    error("Unsupported library section: ${shellDestination.section}")
                            }
                            if (shellDestination != ShellLibraryDestination.Root) {
                                state.libraryErrorMessage?.let { message ->
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LibraryErrorBanner(
                                            message = message,
                                            onRetry = onRetryLibraryError,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!showSkeleton) {
                    PullRefreshIndicator(
                        refreshing = state.isLibraryLoading,
                        state = pullRefreshState,
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp),
                    )
                }
            }
        }
    } else {
        val listState =
            rememberSaveable(saver = LazyListState.Saver) {
                LazyListState()
            }
        val hasDownloads =
            remember(downloadStatuses) {
                downloadStatuses.values.any { it is DownloadStatus.Completed }
            }
        val showSkeleton =
            state.isInitialLoading &&
                state.libraryItems.isEmpty() &&
                state.continueWatching.isEmpty() &&
                state.recentShows.isEmpty() &&
                state.recentMovies.isEmpty()
        var refreshWasActive by remember { mutableStateOf(false) }
        LaunchedEffect(state.isInitialLoading, showSkeleton) {
            when {
                state.isInitialLoading && !showSkeleton -> {
                    refreshWasActive = true
                    listState.scrollToItem(0)
                }
                refreshWasActive -> {
                    refreshWasActive = false
                    listState.scrollToItem(0)
                }
            }
        }
        val seriesGroups =
            remember(state.libraryItems, isTvLibrary) {
                if (isTvLibrary) groupTvSeries(state.libraryItems) else emptyList()
            }
        val nextUpItems =
            remember(state.nextUp, state.continueWatching, state.libraryItems) {
                if (state.nextUp.isNotEmpty()) {
                    state.nextUp
                } else {
                    val continueEpisodes = state.continueWatching.filter { it.type.equals("Episode", ignoreCase = true) }
                    val upcomingEpisodes =
                        state.libraryItems
                            .filter { it.type.equals("Episode", ignoreCase = true) }
                            .filter { (it.playedPercentage ?: 0.0) < 90.0 }
                    (continueEpisodes + upcomingEpisodes)
                        .distinctBy { it.id }
                        .take(12)
                }
            }
        val recentShowGroups =
            remember(state.recentShows) {
                if (state.recentShows.isEmpty()) {
                    emptyList()
                } else {
                    val groups = linkedMapOf<String, MutableTvSeriesGroup>()
                    state.recentShows.forEach { item ->
                        when {
                            item.type.equals("Series", ignoreCase = true) -> {
                                val key = "series:${item.id}"
                                val group = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }
                                group.series = item
                            }
                            item.type.equals("Episode", ignoreCase = true) -> {
                                val key =
                                    item.seriesId?.let { seriesId -> "series:$seriesId" }
                                        ?: item.parentId?.let { parentId -> "parent:$parentId" }
                                        ?: "episode:${item.id}"
                                val group = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }
                                group.episodes += item
                                if (group.series == null) {
                                    group.series = item.toSeriesPlaceholder()
                                }
                            }
                        }
                    }
                    groups.values.map { it.toImmutable() }.take(12)
                }
            }
        val recentMovieItems =
            remember(state.recentMovies) {
                state.recentMovies
                    .asSequence()
                    .filter { item ->
                        item.type.equals("Movie", ignoreCase = true) ||
                            item.mediaType.equals("Video", ignoreCase = true)
                    }.distinctBy { it.id }
                    .take(12)
                    .toList()
            }
        val resolvedSpotlightEligibilityNow =
            remember(spotlightEligibilityNow) {
                spotlightEligibilityNow ?: Clock.System.now()
            }
        val spotlightCandidates =
            remember(state.recentShows, state.recentMovies, state.libraryItems, resolvedSpotlightEligibilityNow) {
                buildSpotlightCandidates(
                    recentShows = state.recentShows,
                    recentMovies = state.recentMovies,
                    now = resolvedSpotlightEligibilityNow,
                    libraryItems = state.libraryItems,
                )
            }
        val spotlightCandidateIds =
            remember(spotlightCandidates) {
                spotlightCandidates.map { candidate -> candidate.displayItem.id }
            }
        LaunchedEffect(spotlightCandidateIds.isEmpty(), selectedSpotlightId) {
            if (spotlightCandidateIds.isEmpty() && selectedSpotlightId != null) {
                onSelectedSpotlightIdChange(null)
            }
        }
        val spotlightContent: @Composable (SpotlightCandidate, Int, Int) -> Unit =
            { candidate, _, _ ->
                HomeSpotlightCard(
                    item = candidate.displayItem,
                    actionItem = candidate.actionItem,
                    baseUrl = state.imageBaseUrl,
                    accessToken = state.imageAccessToken,
                    onOpenItem = onOpenDetail,
                    onPlayItem = onPlayItem,
                    artworkPainter = spotlightPainter,
                    logoPainter = spotlightLogoPainter,
                )
            }

        LoadMoreListener(
            listState = listState,
            shouldLoadMore = !state.endReached && !state.isPageLoading && !state.isLibraryLoading && state.libraryItems.isNotEmpty(),
            onLoadMore = onLoadMore,
        )

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState),
        ) {
            Crossfade(
                targetState = showSkeleton,
                modifier = Modifier.fillMaxSize(),
                label = "homeContent",
            ) { loading ->
                if (loading) {
                    HomeSkeleton(contentPadding = listPadding)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag(SpotlightTestTags.HOME_LIST),
                        state = listState,
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (
                            state.homeErrorMessage != null ||
                            (state.selectedLibraryId == null && state.libraries.isNotEmpty()) ||
                            state.libraries.isEmpty()
                        ) {
                            item(key = "status") {
                                StatusBanner(
                                    state = state,
                                    errorMessage = state.homeErrorMessage,
                                    onRetry = onRefresh,
                                    onConnect = onConnectServer,
                                )
                            }
                        }
                        if (spotlightCandidates.isNotEmpty()) {
                            item(key = "spotlight") {
                                key(spotlightCandidateIds) {
                                    HomeSpotlight(
                                        candidates = spotlightCandidates,
                                        selectedId = selectedSpotlightId,
                                        onSelected = onSelectedSpotlightIdChange,
                                        content = spotlightContent,
                                        autoAdvanceEnabled = spotlightAutoAdvanceEnabled,
                                        autoAdvanceIntervalMillis = spotlightAutoAdvanceIntervalMillis,
                                    )
                                }
                            }
                        }
                        if (state.continueWatching.isNotEmpty()) {
                            item(key = "continueWatching") {
                                ContinueWatchingSection(
                                    items = state.continueWatching,
                                    baseUrl = state.imageBaseUrl,
                                    accessToken = state.imageAccessToken,
                                    downloadStatuses = downloadStatuses,
                                    onItemSelected = onOpenDetail,
                                )
                            }
                        }
                        if (nextUpItems.isNotEmpty()) {
                            item(key = "nextUp") {
                                NextUpSection(
                                    items = nextUpItems,
                                    baseUrl = state.imageBaseUrl,
                                    accessToken = state.imageAccessToken,
                                    downloadStatuses = downloadStatuses,
                                    onOpenItem = onOpenDetail,
                                )
                            }
                        }
                        item(key = "recentShows") {
                            RecentlyAddedShowsSection(
                                groups = recentShowGroups,
                                baseUrl = state.imageBaseUrl,
                                accessToken = state.imageAccessToken,
                                downloadStatuses = downloadStatuses,
                                onOpenItem = onOpenDetail,
                            )
                        }
                        item(key = "recentMovies") {
                            RecentlyAddedMoviesSection(
                                items = recentMovieItems,
                                baseUrl = state.imageBaseUrl,
                                accessToken = state.imageAccessToken,
                                downloadStatuses = downloadStatuses,
                                onOpenItem = onOpenDetail,
                            )
                        }
                        if (state.libraryItems.isNotEmpty()) {
                            item(key = "spacerAfterRecent") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        if (state.isPageLoading) {
                            item(key = "pagingLoader") {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }

            if (!showSkeleton) {
                PullRefreshIndicator(
                    refreshing = state.isInitialLoading,
                    state = pullRefreshState,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun LibrarySearchField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon =
            if (query.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = {
                            onClear()
                            focusManager.clearFocus()
                        },
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(Res.string.clear_search))
                    }
                }
            } else {
                null
            },
        placeholder = { Text(stringResource(Res.string.search_library)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun LibraryLandingHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.library_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = stringResource(Res.string.profile),
            modifier = Modifier.size(58.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LibraryQuickActions(
    hasDownloads: Boolean,
    libraryCount: Int,
    onDownloads: () -> Unit,
    onFavorites: () -> Unit,
    onLibraries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibraryActionRow(
            icon = Icons.Filled.Download,
            label = stringResource(Res.string.downloads),
            supportingText = if (hasDownloads) stringResource(Res.string.offline_ready) else null,
            onClick = onDownloads,
        )
        LibraryActionRow(
            icon = Icons.Filled.FavoriteBorder,
            label = stringResource(Res.string.favorites),
            onClick = onFavorites,
        )
        LibraryActionRow(
            icon = Icons.Filled.Folder,
            label = stringResource(Res.string.libraries),
            supportingText = libraryCount.takeIf { it > 0 }?.toString(),
            onClick = onLibraries,
        )
    }
}

@Composable
private fun LibraryActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    ).padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun LibraryCollectionRow(
    library: JellyfinLibrary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            },
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                    ).padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val localizedItemCount =
                    library.itemCount?.let { itemCount ->
                        stringResource(Res.string.items_count, itemCount)
                    }
                val detail =
                    buildList {
                        library.collectionType?.takeIf { it.isNotBlank() }?.let { add(it) }
                        localizedItemCount?.let(::add)
                    }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryMovieRail(
    title: String,
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenLibrary: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LibraryRailContainer(title = title, onOpenLibrary = onOpenLibrary, modifier = modifier) {
        if (items.isEmpty()) {
            EmptySectionMessage(stringResource(Res.string.library_no_movies))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(items, key = { it.id }) { item ->
                    ImagePosterCard(
                        item = item,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        isDownloaded = downloadStatuses[item.id] is DownloadStatus.Completed,
                        onClick = { onOpenItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySeriesRail(
    title: String,
    groups: List<TvSeriesGroup>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onOpenItem: (JellyfinItem) -> Unit,
    onOpenLibrary: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LibraryRailContainer(title = title, onOpenLibrary = onOpenLibrary, modifier = modifier) {
        if (groups.isEmpty()) {
            EmptySectionMessage(stringResource(Res.string.library_no_shows))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(groups, key = { it.id }) { group ->
                    val targetItem = group.openItem
                    val isDownloaded =
                        group.episodes.any { episode -> downloadStatuses[episode.id] is DownloadStatus.Completed } ||
                            (group.series?.let { downloadStatuses[it.id] is DownloadStatus.Completed } == true)
                    SeriesImagePosterCard(
                        group = group,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        isDownloaded = isDownloaded,
                        enabled = targetItem != null,
                        onClick = { targetItem?.let(onOpenItem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRailContainer(
    title: String,
    onOpenLibrary: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { onOpenLibrary?.invoke() },
                enabled = onOpenLibrary != null,
                modifier = Modifier.size(JellystackLayoutTokens.minimumTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(Res.string.open_library_section, title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun ImagePosterCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    220.dp
                } else {
                    260.dp
                },
            ),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = item.id,
                primaryTag = item.primaryImageTag,
                thumbTag = item.thumbImageTag,
                backdropTag = item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
                artTag = item.artImageTag,
                bannerTag = item.bannerImageTag,
                contentScale = ContentScale.Fit,
                preferLandscapeArtwork = true,
            )
            if (isDownloaded) {
                DownloadedIconBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SeriesImagePosterCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extraCandidates =
        remember(group) {
            buildList<ImageCandidate> {
                val hasSeriesArtwork =
                    listOf(group.artTag, group.bannerTag, group.thumbTag, group.backdropTag, group.logoTag)
                        .any { !it.isNullOrBlank() }
                if (!hasSeriesArtwork) {
                    group.episodes.forEach { episode ->
                        addCandidate(episode.id, episode.artImageTag, "Art")
                        addCandidate(episode.id, episode.bannerImageTag, "Banner")
                        addCandidate(episode.id, episode.thumbImageTag, "Thumb")
                        addCandidate(episode.id, episode.backdropImageTag, "Backdrop")
                        addCandidate(episode.id, episode.primaryImageTag, "Primary")
                    }
                }
            }
        }
    Card(
        modifier =
            modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    220.dp
                } else {
                    260.dp
                },
            ),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = group.posterItemId,
                primaryTag = group.primaryImageTag,
                thumbTag = group.thumbTag,
                backdropTag = group.backdropTag,
                accessToken = accessToken,
                contentDescription = group.title,
                primaryImageItemId = group.primaryImageItemId,
                thumbImageItemId = group.primaryImageItemId,
                artImageItemId = group.primaryImageItemId,
                bannerImageItemId = group.primaryImageItemId,
                backdropImageItemId = group.primaryImageItemId,
                logoImageItemId = group.primaryImageItemId,
                logoTag = group.logoTag,
                artTag = group.artTag,
                bannerTag = group.bannerTag,
                contentScale = ContentScale.Fit,
                preferLandscapeArtwork = true,
                extraCandidates = extraCandidates,
            )
            if (isDownloaded) {
                DownloadedIconBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryLandscapeGridCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            val seriesItemId = item.seriesId ?: item.parentId
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = seriesItemId ?: item.id,
                primaryTag = item.seriesPrimaryImageTag ?: item.primaryImageTag,
                thumbTag = item.seriesThumbImageTag ?: item.thumbImageTag,
                backdropTag = item.seriesBackdropImageTag ?: item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
                primaryImageItemId = seriesItemId,
                thumbImageItemId = seriesItemId,
                artImageItemId = seriesItemId,
                bannerImageItemId = seriesItemId,
                backdropImageItemId = seriesItemId,
                logoImageItemId = seriesItemId,
                logoTag = item.seriesLogoImageTag ?: item.parentLogoImageTag ?: item.logoImageTag,
                artTag = item.seriesArtImageTag ?: item.artImageTag,
                bannerTag = item.seriesBannerImageTag ?: item.bannerImageTag,
                contentScale = ContentScale.Fit,
                preferLandscapeArtwork = true,
                extraCandidates =
                    buildList {
                        addCandidate(item.id, item.artImageTag, "Art")
                        addCandidate(item.id, item.bannerImageTag, "Banner")
                        addCandidate(item.id, item.thumbImageTag, "Thumb")
                        addCandidate(item.id, item.backdropImageTag, "Backdrop")
                        addCandidate(item.id, item.primaryImageTag, "Primary")
                    },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                ),
                            ),
                        ),
            )
            val label =
                if (item.type.equals("Episode", ignoreCase = true)) {
                    formatEpisodeLabel(item.parentIndexNumber, item.indexNumber) ?: item.name
                } else {
                    item.runTimeTicks?.let { stringResource(Res.string.minutes, ticksToMinutes(it)) }
                        ?: item.productionYear?.takeIf { it > 0 }?.toString()
                        ?: item.name
                }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
            )
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.82f),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(24.dp),
            )
            if (isDownloaded) {
                DownloadedIconBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun LegacyDownloadCard(
    media: OfflineMedia,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.mediaId,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.downloaded_item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(LibraryCardTestTags.POSTER_CARD),
        onClick = onClick,
        shape = RoundedCornerShape(JellystackDimens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
            ) {
                PosterImage(
                    modifier = Modifier.fillMaxSize(),
                    baseUrl = baseUrl,
                    itemId = item.id,
                    primaryTag = item.primaryImageTag,
                    thumbTag = item.thumbImageTag,
                    backdropTag = item.backdropImageTag,
                    accessToken = accessToken,
                    contentDescription = item.name,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                        ),
                                    ),
                            ),
                )
                if (isDownloaded) {
                    DownloadedBadge(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadataLine =
                    buildList {
                        item.productionYear?.takeIf { it > 0 }?.let { add(it.toString()) }
                        item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString(" • ")
                if (metadataLine.isNotBlank()) {
                    Text(
                        text = metadataLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onItemSelected: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.home_continue_watching))
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val isDownloaded = downloadStatuses[item.id] is DownloadStatus.Completed
                LandscapeMediaCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    isDownloaded = isDownloaded,
                    showPlayProgress = true,
                    onClick = { onItemSelected(item) },
                )
            }
        }
    }
}

@Composable
private fun LandscapeMediaCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    showPlayProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width =
        if (LocalResponsiveProfile.current.isCompact) {
            300.dp
        } else {
            360.dp
        }
    val seriesItemId = item.seriesId ?: item.parentId
    val isEpisode = item.type.equals("Episode", ignoreCase = true)
    val label =
        if (isEpisode) {
            formatEpisodeLabel(item.parentIndexNumber, item.indexNumber) ?: item.name
        } else {
            item.runTimeTicks?.let { stringResource(Res.string.minutes, ticksToMinutes(it)) }
                ?: item.productionYear?.takeIf { it > 0 }?.toString()
                ?: item.name
        }
    val progress = progressFraction(item)
    Card(
        modifier = modifier.width(width),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = seriesItemId ?: item.id,
                primaryTag = item.seriesPrimaryImageTag ?: item.primaryImageTag,
                thumbTag = item.seriesThumbImageTag ?: item.thumbImageTag,
                backdropTag = item.seriesBackdropImageTag ?: item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
                primaryImageItemId = seriesItemId,
                thumbImageItemId = seriesItemId,
                artImageItemId = seriesItemId,
                bannerImageItemId = seriesItemId,
                backdropImageItemId = seriesItemId,
                logoImageItemId = seriesItemId,
                logoTag = item.seriesLogoImageTag ?: item.parentLogoImageTag ?: item.logoImageTag,
                artTag = item.seriesArtImageTag ?: item.artImageTag,
                bannerTag = item.seriesBannerImageTag ?: item.bannerImageTag,
                contentScale = ContentScale.Fit,
                preferLandscapeArtwork = true,
                extraCandidates =
                    buildList {
                        addCandidate(item.id, item.artImageTag, "Art")
                        addCandidate(item.id, item.bannerImageTag, "Banner")
                        addCandidate(item.id, item.thumbImageTag, "Thumb")
                        addCandidate(item.id, item.backdropImageTag, "Backdrop")
                        addCandidate(item.id, item.primaryImageTag, "Primary")
                    },
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                ),
                            ),
                        ),
            )
            if (isDownloaded) {
                DownloadedIconBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                )
            }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showPlayProgress) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (showPlayProgress && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier =
                            Modifier
                                .width(58.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    136.dp
                } else {
                    148.dp
                },
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box {
            val seriesItemId = item.seriesId ?: item.parentId
            val fallbackCandidates =
                buildList<ImageCandidate> {
                    addCandidate(item.id, item.primaryImageTag, "Primary")
                    addCandidate(item.id, item.thumbImageTag, "Thumb")
                    addCandidate(item.id, item.backdropImageTag, "Backdrop")
                }
            PosterImage(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                baseUrl = baseUrl,
                itemId = seriesItemId ?: item.id,
                primaryTag = item.seriesPrimaryImageTag ?: item.primaryImageTag,
                thumbTag = item.seriesThumbImageTag ?: item.thumbImageTag,
                backdropTag = item.seriesBackdropImageTag ?: item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
                primaryImageItemId = seriesItemId,
                thumbImageItemId = seriesItemId,
                backdropImageItemId = seriesItemId,
                logoImageItemId = seriesItemId,
                logoTag = item.parentLogoImageTag,
                extraCandidates = fallbackCandidates,
            )
            if (isDownloaded) {
                DownloadedBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                )
            }
            val progress = progressFraction(item)
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        val isEpisode = item.type.equals("Episode", ignoreCase = true)
        val primaryText =
            when {
                isEpisode -> item.seriesName ?: item.name
                else -> item.name
            }
        val secondaryText =
            if (isEpisode) {
                formatEpisodeLabel(
                    seasonNumber = item.parentIndexNumber,
                    episodeNumber = item.indexNumber,
                )
            } else {
                item.officialRating?.takeIf { it.isNotBlank() }
            }
        val tertiaryText =
            if (isEpisode) {
                item.episodeTitle?.takeIf { it.isNotBlank() } ?: item.name
            } else {
                item.productionYear?.takeIf { it > 0 }?.toString()
            }
        MediaCardMetadata(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
        )
    }
}

@Composable
private fun formatEpisodeLabel(
    seasonNumber: Int?,
    episodeNumber: Int?,
): String? {
    val hasSeason = seasonNumber != null && seasonNumber > 0
    val hasEpisode = episodeNumber != null && episodeNumber > 0
    return when {
        hasSeason && hasEpisode -> stringResource(Res.string.season_episode_number, seasonNumber!!, episodeNumber!!)
        hasEpisode -> stringResource(Res.string.episode_number, episodeNumber!!)
        hasSeason -> stringResource(Res.string.season_number, seasonNumber!!)
        else -> null
    }
}

private fun OfflineMediaMetadata.toJellyfinItem(): JellyfinItem =
    JellyfinItem(
        id = itemId,
        libraryId = libraryId,
        name = name,
        sortName = sortName,
        overview = overview,
        type = type,
        mediaType = mediaType,
        locationType = null,
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = primaryImageTag,
        thumbImageTag = thumbImageTag,
        backdropImageTag = backdropImageTag,
        seriesId = seriesId,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag,
        parentLogoImageTag = parentLogoImageTag,
        runTimeTicks = runTimeTicks,
        positionTicks = positionTicks,
        playedPercentage = playedPercentage,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = null,
        officialRating = officialRating,
        indexNumber = indexNumber,
        parentIndexNumber = parentIndexNumber,
        seriesName = seriesName,
        seasonId = seasonId,
        episodeTitle = episodeTitle,
        lastPlayed = null,
        dateCreated = dateCreated,
        logoImageTag = logoImageTag,
        artImageTag = artImageTag,
        bannerImageTag = bannerImageTag,
        seriesLogoImageTag = seriesLogoImageTag,
        seriesArtImageTag = seriesArtImageTag,
        seriesBannerImageTag = seriesBannerImageTag,
    )

@Composable
private fun NextUpSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.home_next_up))
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptySectionMessage(stringResource(Res.string.home_no_upcoming_episodes))
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                val isDownloaded = downloadStatuses[item.id] is DownloadStatus.Completed
                LandscapeMediaCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    isDownloaded = isDownloaded,
                    showPlayProgress = false,
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

@Composable
private fun NextUpCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    136.dp
                } else {
                    148.dp
                },
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                PosterImage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                    baseUrl = baseUrl,
                    itemId = item.parentId ?: item.seriesId ?: item.id,
                    primaryTag = item.primaryImageTag ?: item.seriesPrimaryImageTag,
                    thumbTag = item.thumbImageTag ?: item.seriesThumbImageTag,
                    backdropTag = item.backdropImageTag ?: item.seriesBackdropImageTag,
                    accessToken = accessToken,
                    contentDescription = item.name,
                    primaryImageItemId = item.parentId ?: item.seriesId,
                    thumbImageItemId = item.parentId ?: item.seriesId,
                    backdropImageItemId = item.parentId ?: item.seriesId,
                    logoImageItemId = item.parentId ?: item.seriesId,
                    logoTag = item.parentLogoImageTag,
                )
                if (isDownloaded) {
                    DownloadedBadge(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                    )
                }
            }

            val isEpisode = item.type.equals("Episode", ignoreCase = true)
            val primaryText =
                when {
                    isEpisode -> item.seriesName ?: item.name
                    else -> item.name
                }
            val secondaryText =
                if (isEpisode) {
                    formatEpisodeLabel(
                        seasonNumber = item.parentIndexNumber,
                        episodeNumber = item.indexNumber,
                    )
                } else {
                    item.officialRating?.takeIf { it.isNotBlank() }
                }
            val tertiaryText =
                if (isEpisode) {
                    item.episodeTitle?.takeIf { it.isNotBlank() } ?: item.name
                } else {
                    item.productionYear?.takeIf { it > 0 }?.toString()
                }

            MediaCardMetadata(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                primaryText = primaryText,
                secondaryText = secondaryText,
                tertiaryText = tertiaryText,
            )
        }
    }
}

@Composable
private fun MediaCardMetadata(
    primaryText: String,
    secondaryText: String?,
    tertiaryText: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tertiaryText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tertiaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f, fill = true))
        }
    }
}

@Composable
private fun RecentlyAddedShowsSection(
    groups: List<TvSeriesGroup>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.home_recent_shows))
        Spacer(modifier = Modifier.height(12.dp))
        if (groups.isEmpty()) {
            EmptySectionMessage(stringResource(Res.string.library_no_shows))
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(groups, key = { it.id }) { group ->
                val isDownloaded =
                    group.episodes.any { episode -> downloadStatuses[episode.id] is DownloadStatus.Completed } ||
                        (group.series?.let { downloadStatuses[it.id] is DownloadStatus.Completed } == true)
                LandscapeSeriesCard(
                    group = group,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    isDownloaded = isDownloaded,
                    onOpenSeries = onOpenItem,
                )
            }
        }
    }
}

@Composable
private fun LandscapeSeriesCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onOpenSeries: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetItem = group.openItem
    val width =
        if (LocalResponsiveProfile.current.isCompact) {
            300.dp
        } else {
            360.dp
        }
    val extraCandidates =
        remember(group) {
            buildList<ImageCandidate> {
                val hasSeriesArtwork =
                    listOf(group.artTag, group.bannerTag, group.thumbTag, group.backdropTag, group.logoTag)
                        .any { !it.isNullOrBlank() }
                if (!hasSeriesArtwork) {
                    group.episodes.forEach { episode ->
                        addCandidate(episode.id, episode.artImageTag, "Art")
                        addCandidate(episode.id, episode.bannerImageTag, "Banner")
                        addCandidate(episode.id, episode.thumbImageTag, "Thumb")
                        addCandidate(episode.id, episode.backdropImageTag, "Backdrop")
                        addCandidate(episode.id, episode.primaryImageTag, "Primary")
                    }
                }
            }
        }
    Card(
        modifier = modifier.width(width),
        onClick = { targetItem?.let(onOpenSeries) },
        enabled = targetItem != null,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = group.posterItemId,
                primaryTag = group.primaryImageTag,
                thumbTag = group.thumbTag,
                backdropTag = group.backdropTag,
                accessToken = accessToken,
                contentDescription = group.title,
                primaryImageItemId = group.primaryImageItemId,
                thumbImageItemId = group.primaryImageItemId,
                artImageItemId = group.primaryImageItemId,
                bannerImageItemId = group.primaryImageItemId,
                backdropImageItemId = group.primaryImageItemId,
                logoImageItemId = group.primaryImageItemId,
                logoTag = group.logoTag,
                artTag = group.artTag,
                bannerTag = group.bannerTag,
                contentScale = ContentScale.Fit,
                preferLandscapeArtwork = true,
                extraCandidates = extraCandidates,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                ),
                            ),
                        ),
            )
            if (isDownloaded) {
                DownloadedIconBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                )
            }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.fallbackEpisode?.let { episodeLabel(it) } ?: group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SeriesPosterCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onOpenSeries: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetItem = group.openItem
    val extraCandidates =
        remember(group) {
            buildList<ImageCandidate> {
                group.episodes.forEach { episode ->
                    addCandidate(episode.id, episode.primaryImageTag, "Primary")
                    addCandidate(episode.id, episode.thumbImageTag, "Thumb")
                    addCandidate(episode.id, episode.backdropImageTag, "Backdrop")
                }
            }
        }
    Card(
        modifier = modifier.width(148.dp),
        onClick = { targetItem?.let(onOpenSeries) },
        enabled = targetItem != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = group.posterItemId,
                primaryTag = group.primaryImageTag,
                thumbTag = group.thumbTag,
                backdropTag = group.backdropTag,
                accessToken = accessToken,
                contentDescription = group.title,
                primaryImageItemId = group.primaryImageItemId,
                thumbImageItemId = group.primaryImageItemId,
                backdropImageItemId = group.primaryImageItemId,
                logoImageItemId = group.primaryImageItemId,
                logoTag = group.logoTag,
                extraCandidates = extraCandidates,
            )
            if (isDownloaded) {
                DownloadedBadge(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val overview = group.overview?.takeIf { it.isNotBlank() } ?: " "
            Text(
                text = overview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentlyAddedMoviesSection(
    items: List<JellyfinItem>,
    baseUrl: String?,
    accessToken: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    onOpenItem: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(Res.string.home_recent_movies))
        Spacer(modifier = Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptySectionMessage(stringResource(Res.string.library_no_movies))
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                val isDownloaded = downloadStatuses[item.id] is DownloadStatus.Completed
                LandscapeMediaCard(
                    item = item,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    onClick = { onOpenItem(item) },
                    isDownloaded = isDownloaded,
                    showPlayProgress = false,
                )
            }
        }
    }
}

@Composable
private fun MoviePosterCard(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = cardColors,
    ) {
        Column {
            Box {
                PosterImage(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                    baseUrl = baseUrl,
                    itemId = item.id,
                    primaryTag = item.primaryImageTag,
                    thumbTag = item.thumbImageTag,
                    backdropTag = item.backdropImageTag,
                    accessToken = accessToken,
                    contentDescription = item.name,
                )
                if (isDownloaded) {
                    DownloadedBadge(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                    )
                }
                val progress = progressFraction(item)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                        progress = progress,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detailParts =
                    buildList {
                        item.productionYear?.takeIf { it > 0 }?.let { add(it.toString()) }
                        item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                val detailText = detailParts.takeIf { it.isNotEmpty() }?.joinToString(" - ") ?: " "
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val overview = item.overview?.takeIf { it.isNotBlank() } ?: " "
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DownloadedBadge(modifier: Modifier = Modifier) {
    val downloadedLabel = stringResource(Res.string.downloaded)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = downloadedLabel,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DownloadedIconBadge(modifier: Modifier = Modifier) {
    val downloadedLabel = stringResource(Res.string.downloaded)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Icon(
            imageVector = Icons.Filled.CloudDone,
            contentDescription = downloadedLabel,
            modifier =
                Modifier
                    .padding(6.dp)
                    .size(16.dp),
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    eyebrow: String? = null,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            eyebrow?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        badge?.let {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(it) },
            )
        }
    }
}

@Composable
private fun HomeSpotlightTitle(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    logoPainter: Painter?,
) {
    val title = item.seriesName ?: item.name
    val logoArtwork = remember(item) { item.selectSpotlightLogoArtwork() }
    val logoUrl =
        remember(baseUrl, accessToken, logoArtwork) {
            logoArtwork?.let { artwork ->
                buildImageUrl(
                    baseUrl = baseUrl,
                    itemId = artwork.itemId,
                    tag = artwork.tag,
                    imageType = artwork.imageType,
                    accessToken = accessToken,
                )
            }
        }
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    val injectedLogo = logoPainter?.takeIf { logoArtwork != null }

    when {
        injectedLogo != null -> {
            Image(
                painter = injectedLogo,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth(0.78f)
                        .height(72.dp)
                        .testTag(SpotlightTestTags.TITLE_LOGO),
            )
        }

        logoUrl != null && !logoFailed -> {
            val context = LocalPlatformContext.current
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onError = { logoFailed = true },
                modifier =
                    Modifier
                        .fillMaxWidth(0.78f)
                        .height(72.dp)
                        .testTag(SpotlightTestTags.TITLE_LOGO),
            )
        }

        else -> SpotlightTitleText(title)
    }
}

@Composable
private fun SpotlightTitleText(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeSpotlightCard(
    item: JellyfinItem,
    actionItem: JellyfinItem = item,
    baseUrl: String?,
    accessToken: String?,
    onOpenItem: (JellyfinItem) -> Unit,
    onPlayItem: ((JellyfinItem) -> Unit)?,
    modifier: Modifier = Modifier,
    artworkPainter: Painter? = null,
    logoPainter: Painter? = null,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SpotlightTestTags.HERO),
        onClick = { onOpenItem(actionItem) },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val profile = LocalResponsiveProfile.current
            val maxHeroHeight =
                when {
                    profile.isShortHeight -> JellystackLayoutTokens.spotlightShortHeightMax
                    profile.widthClass == JellystackWidthClass.Compact ->
                        JellystackLayoutTokens.spotlightCompactMax
                    profile.widthClass == JellystackWidthClass.Medium ->
                        JellystackLayoutTokens.spotlightMediumMax
                    else -> JellystackLayoutTokens.spotlightExpandedMax
                }
            val heroHeight = minOf(maxWidth * 0.78f, maxHeroHeight)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = heroHeight),
            ) {
                if (artworkPainter != null) {
                    Image(
                        painter = artworkPainter,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    val spotlightArtwork = remember(item) { item.selectSpotlightArtwork() }
                    PosterImage(
                        modifier = Modifier.matchParentSize(),
                        baseUrl = baseUrl,
                        itemId = item.id,
                        primaryTag = null,
                        thumbTag = null,
                        backdropTag = null,
                        accessToken = accessToken,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        extraCandidates =
                            listOfNotNull(
                                spotlightArtwork?.let { artwork ->
                                    ImageCandidate(
                                        itemId = artwork.itemId,
                                        tag = artwork.tag,
                                        type = artwork.imageType,
                                    )
                                },
                            ),
                    )
                }
                ImageTextScrim(modifier = Modifier.matchParentSize())
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeSpotlightTitle(
                        item = item,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        logoPainter = logoPainter,
                    )
                    val runtimeMinutes =
                        item.runTimeTicks?.let(::ticksToMinutes)?.takeIf { minutes -> minutes > 0 }
                    val runtimeLabel =
                        runtimeMinutes?.let { minutes ->
                            stringResource(Res.string.minutes, minutes)
                        }
                    val supporting =
                        buildList {
                            item.productionYear?.takeIf { it > 0 }?.let { add(it.toString()) }
                            runtimeLabel?.let(::add)
                            item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }.joinToString("  •  ")
                            .ifBlank {
                                item.episodeTitle?.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.ready_to_play)
                            }
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onPlayItem != null) {
                            val isEpisode = actionItem.type.equals("Episode", ignoreCase = true)
                            val episodeLabel =
                                formatEpisodeLabel(
                                    actionItem.parentIndexNumber,
                                    actionItem.indexNumber,
                                )
                                    ?: actionItem.episodeTitle?.takeIf { it.isNotBlank() }
                                    ?: actionItem.name
                            val hasProgress = (actionItem.positionTicks ?: 0L) > 0L
                            val playLabel =
                                when {
                                    isEpisode && hasProgress ->
                                        stringResource(Res.string.continue_episode, episodeLabel)
                                    isEpisode -> stringResource(Res.string.play_episode, episodeLabel)
                                    hasProgress -> stringResource(Res.string.continue_playback)
                                    else -> stringResource(Res.string.play)
                                }
                            FilledTonalButton(
                                modifier =
                                    Modifier
                                        .heightIn(min = JellystackLayoutTokens.minimumTouchTarget)
                                        .testTag(SpotlightTestTags.PLAY),
                                onClick = { onPlayItem(actionItem) },
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = playLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        IconButton(
                            modifier =
                                Modifier
                                    .size(JellystackLayoutTokens.minimumTouchTarget)
                                    .testTag(SpotlightTestTags.DETAILS),
                            onClick = { onOpenItem(actionItem) },
                            colors =
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(Res.string.open_details),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySectionMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LibraryGridSkeleton(contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                shape = RoundedCornerShape(16.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
        items(8) {
            LibraryPosterSkeleton()
        }
    }
}

@Composable
private fun LibraryPosterSkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
        )
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            shape = RoundedCornerShape(6.dp),
        )
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp),
            shape = RoundedCornerShape(6.dp),
        )
    }
}

@Composable
private fun LibraryEmptyState(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

@Composable
internal fun HomeSkeleton(contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "spotlightSkeleton") {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.28f),
                    shape = RoundedCornerShape(20.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(6) {
                        ShimmerPlaceholder(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }
        }
        items(4) { index ->
            val widthFraction =
                when (index) {
                    0 -> 0.45f
                    1 -> 0.35f
                    else -> 0.5f
                }
            HomeSectionSkeleton(titleWidthFraction = widthFraction)
        }
    }
}

@Composable
private fun HomeSectionSkeleton(titleWidthFraction: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .height(24.dp),
            shape = RoundedCornerShape(8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(4) {
                HorizontalCardSkeleton()
            }
        }
    }
}

@Composable
private fun HorizontalCardSkeleton() {
    Card(
        modifier =
            Modifier.width(
                if (LocalResponsiveProfile.current.isCompact) {
                    136.dp
                } else {
                    148.dp
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
    }
}

@Composable
internal fun JellyfinDetailLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            shape = RoundedCornerShape(8.dp),
        )
        ShimmerPlaceholder(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            )
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            )
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                EpisodeSkeleton()
            }
        }
    }
}

@Composable
private fun EpisodeSkeleton() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ShimmerPlaceholder(
                modifier =
                    Modifier
                        .width(96.dp)
                        .height(144.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(12.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                ShimmerPlaceholder(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
        }
    }
}

private data class TvSeriesGroup(
    val id: String,
    val series: JellyfinItem?,
    val episodes: List<JellyfinItem>,
) {
    val fallbackEpisode: JellyfinItem?
        get() = episodes.firstOrNull()
    val title: String
        get() = series?.name ?: fallbackEpisode?.seriesName ?: fallbackEpisode?.name ?: id
    val overview: String?
        get() = series?.overview ?: fallbackEpisode?.overview
    val posterItemId: String
        get() = primaryImageItemId ?: series?.id ?: fallbackEpisode?.id ?: id
    val primaryImageTag: String?
        get() =
            series?.primaryImageTag
                ?: series?.seriesPrimaryImageTag
                ?: fallbackEpisode?.seriesPrimaryImageTag
                ?: fallbackEpisode?.primaryImageTag
    val thumbTag: String?
        get() =
            series?.thumbImageTag
                ?: series?.seriesThumbImageTag
                ?: fallbackEpisode?.seriesThumbImageTag
                ?: fallbackEpisode?.thumbImageTag
    val artTag: String?
        get() =
            series?.artImageTag
                ?: series?.seriesArtImageTag
                ?: fallbackEpisode?.seriesArtImageTag
                ?: fallbackEpisode?.artImageTag
    val bannerTag: String?
        get() =
            series?.bannerImageTag
                ?: series?.seriesBannerImageTag
                ?: fallbackEpisode?.seriesBannerImageTag
                ?: fallbackEpisode?.bannerImageTag
    val backdropTag: String?
        get() =
            series?.backdropImageTag
                ?: series?.seriesBackdropImageTag
                ?: fallbackEpisode?.seriesBackdropImageTag
                ?: fallbackEpisode?.backdropImageTag
    val primaryImageItemId: String?
        get() = series?.id ?: fallbackEpisode?.seriesId ?: fallbackEpisode?.parentId
    val logoTag: String?
        get() =
            series?.logoImageTag
                ?: series?.seriesLogoImageTag
                ?: series?.parentLogoImageTag
                ?: fallbackEpisode?.seriesLogoImageTag
                ?: fallbackEpisode?.parentLogoImageTag
                ?: fallbackEpisode?.logoImageTag
    val openItem: JellyfinItem?
        get() = series ?: fallbackEpisode
}

private fun groupTvSeries(items: List<JellyfinItem>): List<TvSeriesGroup> {
    val groups = linkedMapOf<String, MutableTvSeriesGroup>()
    val nameToKey = mutableMapOf<String, String>()

    fun normalize(value: String?): String? = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun ensureGroup(key: String): MutableTvSeriesGroup = groups.getOrPut(key) { MutableTvSeriesGroup(key = key) }

    items
        .filter { it.type.equals("Series", ignoreCase = true) }
        .forEach { series ->
            val key = "series:${series.id}"
            val group = ensureGroup(key)
            group.series = series
            normalize(series.name)?.let { normalized ->
                nameToKey[normalized] = key
            }
        }

    items
        .filter { it.type.equals("Episode", ignoreCase = true) }
        .forEach { episode ->
            val normalizedName = normalize(episode.seriesName)
            val key =
                normalizedName?.let { nameToKey[it] }
                    ?: episode.parentId?.let { "parent:$it" }
                    ?: episode.seasonId?.let { "season:$it" }
                    ?: normalizedName?.let { "series-name:$it" }
                    ?: "episode:${episode.id}"
            val group = ensureGroup(key)
            group.episodes += episode
            if (group.series == null) {
                group.series = episode.toSeriesPlaceholder()
            }
            if (normalizedName != null && nameToKey[normalizedName] == null) {
                nameToKey[normalizedName] = key
            }
        }

    return groups.values
        .map { it.toImmutable() }
        .sortedWith(
            compareBy<TvSeriesGroup> {
                it.series?.sortName?.lowercase()
                    ?: it.title.lowercase()
            }.thenBy { it.series?.name ?: it.title },
        )
}

private data class TvSeriesPosterEntry(
    val id: String,
    val poster: JellyfinItem,
    val openItem: JellyfinItem,
)

@Composable
private fun episodeLabel(item: JellyfinItem): String {
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    val parts = mutableListOf<String>()
    when {
        season != null && episode != null -> parts += stringResource(Res.string.season_episode_number, season, episode)
        season != null -> parts += stringResource(Res.string.season_number, season)
        episode != null -> parts += stringResource(Res.string.episode_number, episode)
    }
    val title = item.episodeTitle ?: item.name
    if (!title.isNullOrBlank()) {
        parts += title
    }
    return parts.joinToString(" · ").ifBlank { item.name }
}

private fun audioTrackLabel(track: AudioTrack): String =
    track.title?.takeIf { it.isNotBlank() }
        ?: track.language?.takeIf { it.isNotBlank() }?.uppercase()
        ?: track.codec?.uppercase()
        ?: track.id

private fun audioTrackSummary(track: AudioTrack): String {
    val title = track.title.orEmpty()
    val language =
        readableLanguage(title)
            ?: track.language?.takeIf { it.isNotBlank() }?.uppercase()
    val channels = Regex("""\d(?:\.\d)?""").find(title)?.value
    return listOfNotNull(language, channels ?: track.codec?.uppercase())
        .joinToString(" ")
        .ifBlank { audioTrackLabel(track) }
}

private fun subtitleTrackLabel(
    track: SubtitleTrack,
    defaultLabel: String,
    forcedLabel: String,
): String =
    buildList {
        track.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        track.title
            ?.takeIf { it.isNotBlank() && !it.equals(track.language, ignoreCase = true) }
            ?.let { add(it) }
        if (track.isDefault) add("[$defaultLabel]")
        if (track.isForced) add("[$forcedLabel]")
    }.joinToString(" - ").ifBlank { track.format.name }

private fun subtitleTrackSummary(
    track: SubtitleTrack?,
    offLabel: String,
    defaultLabel: String,
    forcedLabel: String,
): String {
    if (track == null) return offLabel
    val title = track.title.orEmpty()
    return buildList {
        add(readableLanguage(title) ?: track.language?.takeIf { it.isNotBlank() }?.uppercase())
        when {
            track.isForced -> add(forcedLabel)
            track.isDefault -> add(defaultLabel)
        }
    }.filterNotNull()
        .joinToString(" ")
        .ifBlank { subtitleTrackLabel(track, defaultLabel, forcedLabel) }
}

private fun readableLanguage(text: String): String? =
    Regex(
        pattern = """\b(Arabic|Chinese|Dutch|English|French|German|Italian|Japanese|Korean|Portuguese|Spanish)\b""",
        option = RegexOption.IGNORE_CASE,
    ).find(text)?.value?.replaceFirstChar(Char::uppercase)

private data class MutableTvSeriesGroup(
    val key: String,
    var series: JellyfinItem? = null,
    val episodes: MutableList<JellyfinItem> = mutableListOf(),
) {
    fun toImmutable(): TvSeriesGroup =
        TvSeriesGroup(
            id = series?.id ?: key,
            series = series,
            episodes = episodes.distinctBy { it.id },
        )
}

internal data class SeasonEpisodes(
    val seasonNumber: Int?,
    val episodes: List<JellyfinItem>,
    val sortKey: Int,
)

internal fun buildSeasonEpisodes(episodes: List<JellyfinItem>): List<SeasonEpisodes> =
    episodes
        .groupBy { it.parentIndexNumber }
        .map { (seasonNumber, episodesInSeason) ->
            val sortedEpisodes =
                episodesInSeason.sortedWith(
                    compareBy<JellyfinItem> { it.parentIndexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.indexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.name },
                )
            SeasonEpisodes(
                seasonNumber = seasonNumber,
                episodes = sortedEpisodes,
                sortKey = seasonNumber ?: Int.MAX_VALUE,
            )
        }.sortedBy { it.sortKey }

@Composable
private fun TvSeriesCard(
    group: TvSeriesGroup,
    baseUrl: String?,
    accessToken: String?,
    onOpenSeries: (JellyfinItem) -> Unit,
    onOpenEpisode: ((JellyfinItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val seasonGroups = remember(group) { buildSeasonEpisodes(group.episodes) }
    val showEpisodeOverview = seasonGroups.isNotEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            group.openItem?.let(onOpenSeries)
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val fallbackCandidates =
                    buildList<ImageCandidate> {
                        val episode = group.fallbackEpisode
                        addCandidate(episode?.id, episode?.primaryImageTag, "Primary")
                        addCandidate(episode?.id, episode?.thumbImageTag, "Thumb")
                        addCandidate(episode?.id, episode?.backdropImageTag, "Backdrop")
                    }
                val seriesItemId = group.primaryImageItemId
                PosterImage(
                    modifier =
                        Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f),
                    baseUrl = baseUrl,
                    itemId = group.posterItemId,
                    primaryTag = group.primaryImageTag,
                    thumbTag = group.thumbTag,
                    backdropTag = group.backdropTag,
                    accessToken = accessToken,
                    contentDescription = group.title,
                    primaryImageItemId = seriesItemId,
                    thumbImageItemId = seriesItemId,
                    backdropImageItemId = seriesItemId,
                    logoImageItemId = seriesItemId,
                    logoTag = group.logoTag,
                    extraCandidates = fallbackCandidates,
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    group.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showEpisodeOverview) {
                Divider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                )
                SeasonEpisodeSelector(
                    seasons = seasonGroups,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    onOpenEpisode = onOpenEpisode,
                )
            }
        }
    }
}

@Composable
private fun LibraryItemRow(
    item: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosterImage(
                modifier =
                    Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f),
                baseUrl = baseUrl,
                itemId = item.id,
                primaryTag = item.primaryImageTag,
                thumbTag = item.thumbImageTag,
                backdropTag = item.backdropImageTag,
                accessToken = accessToken,
                contentDescription = item.name,
            )
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.overview.isNullOrBlank()) {
                    Text(
                        text = item.overview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.productionYear?.let {
                        Text(
                            text = it.toString(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    item.officialRating?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    val runtimeMinutes = item.runTimeTicks?.let(::ticksToMinutes)
                    if (runtimeMinutes != null && runtimeMinutes > 0) {
                        Text(
                            text = stringResource(Res.string.minutes, runtimeMinutes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UnusedParameter")
internal fun PosterImage(
    modifier: Modifier,
    baseUrl: String?,
    itemId: String,
    primaryTag: String?,
    thumbTag: String?,
    backdropTag: String?,
    accessToken: String?,
    contentDescription: String,
    primaryImageItemId: String? = null,
    thumbImageItemId: String? = null,
    artImageItemId: String? = null,
    bannerImageItemId: String? = null,
    backdropImageItemId: String? = null,
    logoImageItemId: String? = null,
    logoTag: String? = null,
    artTag: String? = null,
    bannerTag: String? = null,
    preferLogo: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    preferLandscapeArtwork: Boolean = false,
    extraCandidates: List<ImageCandidate> = emptyList(),
) {
    val shape = MaterialTheme.shapes.medium
    val fallbackColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholder =
        remember(contentDescription) {
            contentDescription
                .split(" ")
                .firstOrNull()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.toString()
        }
    val imageUrl =
        remember(
            baseUrl,
            accessToken,
            itemId,
            primaryTag,
            thumbTag,
            backdropTag,
            primaryImageItemId,
            thumbImageItemId,
            artImageItemId,
            bannerImageItemId,
            backdropImageItemId,
            logoImageItemId,
            logoTag,
            artTag,
            bannerTag,
            preferLogo,
            contentScale,
            preferLandscapeArtwork,
            extraCandidates,
        ) {
            buildList {
                if (preferLogo) {
                    addCandidate(logoImageItemId, logoTag, "Logo")
                }
                if (preferLandscapeArtwork) {
                    addCandidate(artImageItemId ?: itemId, artTag, "Art")
                    addCandidate(bannerImageItemId ?: itemId, bannerTag, "Banner")
                    addCandidate(thumbImageItemId ?: itemId, thumbTag, "Thumb")
                    addCandidate(backdropImageItemId ?: itemId, backdropTag, "Backdrop")
                    addAll(extraCandidates)
                    addCandidate(primaryImageItemId ?: itemId, primaryTag, "Primary")
                } else {
                    addCandidate(primaryImageItemId ?: itemId, primaryTag, "Primary")
                    addCandidate(thumbImageItemId ?: itemId, thumbTag, "Thumb")
                    addCandidate(backdropImageItemId ?: itemId, backdropTag, "Backdrop")
                    addAll(extraCandidates)
                }
                if (!preferLogo) {
                    addCandidate(logoImageItemId, logoTag, "Logo")
                }
            }.firstNotNullOfOrNull { candidate ->
                buildImageUrl(baseUrl, candidate.itemId, candidate.tag, candidate.type, accessToken)
            }
        }
    val context = LocalPlatformContext.current
    var imageLoading by remember(imageUrl) { mutableStateOf(imageUrl != null) }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(fallbackColor),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            if (imageLoading) {
                ShimmerPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                )
            }
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onSuccess = { imageLoading = false },
                onError = { imageLoading = false },
            )
        } else if (!placeholder.isNullOrBlank()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class ImageCandidate(
    val itemId: String,
    val tag: String,
    val type: String,
)

private fun MutableList<ImageCandidate>.addCandidate(
    itemId: String?,
    tag: String?,
    type: String,
) {
    if (!itemId.isNullOrBlank() && !tag.isNullOrBlank()) {
        add(ImageCandidate(itemId = itemId, tag = tag, type = type))
    }
}

private fun JellyfinItem.toSeriesPlaceholder(): JellyfinItem {
    val placeholderId = seriesId ?: parentId ?: id
    return copy(
        id = placeholderId,
        type = "Series",
        name = seriesName ?: name,
        sortName = sortName ?: seriesName,
        overview = null,
        taglines = emptyList(),
        primaryImageTag = seriesPrimaryImageTag ?: primaryImageTag,
        thumbImageTag = seriesThumbImageTag ?: thumbImageTag,
        backdropImageTag = seriesBackdropImageTag ?: backdropImageTag,
        seriesId = placeholderId,
        seriesPrimaryImageTag = seriesPrimaryImageTag ?: primaryImageTag,
        seriesThumbImageTag = seriesThumbImageTag ?: thumbImageTag,
        seriesBackdropImageTag = seriesBackdropImageTag ?: backdropImageTag,
        logoImageTag = seriesLogoImageTag ?: parentLogoImageTag ?: logoImageTag,
        artImageTag = seriesArtImageTag ?: artImageTag,
        bannerImageTag = seriesBannerImageTag ?: bannerImageTag,
        seriesLogoImageTag = seriesLogoImageTag ?: parentLogoImageTag ?: logoImageTag,
        seriesArtImageTag = seriesArtImageTag ?: artImageTag,
        seriesBannerImageTag = seriesBannerImageTag ?: bannerImageTag,
    )
}

internal fun buildImageUrl(
    baseUrl: String?,
    itemId: String,
    tag: String?,
    imageType: String,
    accessToken: String?,
): String? {
    if (baseUrl.isNullOrBlank() || tag.isNullOrBlank()) {
        return null
    }
    val normalizedBase =
        if (baseUrl.endsWith("/")) {
            baseUrl.dropLast(1)
        } else {
            baseUrl
        }
    val tokenQuery = accessToken?.let { "&api_key=$it" }.orEmpty()
    return "$normalizedBase/Items/$itemId/Images/$imageType?tag=$tag$tokenQuery"
}

@Composable
private fun StatusBanner(
    state: JellyfinHomeState,
    errorMessage: String?,
    onRetry: () -> Unit,
    onConnect: () -> Unit,
) {
    when {
        errorMessage != null -> {
            val message = errorMessage.ifBlank { stringResource(Res.string.something_went_wrong) }
            AssistChip(
                onClick = onRetry,
                label = { Text(message) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
        state.selectedLibraryId == null && state.libraries.isNotEmpty() ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stringResource(Res.string.library_select_library_status),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        state.libraries.isEmpty() ->
            AssistChip(
                onClick = onConnect,
                enabled = true,
                label = { Text(stringResource(Res.string.library_connect_server_status)) },
            )
        else -> Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun LibraryErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message.ifBlank { stringResource(Res.string.something_went_wrong) },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
private fun LoadMoreListener(
    gridState: LazyGridState,
    shouldLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(gridState, shouldLoadMore) {
        if (!shouldLoadMore) {
            return@LaunchedEffect
        }
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.filter { index -> index >= 0 }
            .distinctUntilChanged()
            .collect { index ->
                val nearingEnd = index >= gridState.layoutInfo.totalItemsCount - 4
                if (nearingEnd) {
                    onLoadMore()
                }
            }
    }
}

@Composable
private fun LoadMoreListener(
    listState: LazyListState,
    shouldLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(listState, shouldLoadMore) {
        if (!shouldLoadMore) {
            return@LaunchedEffect
        }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.filter { index -> index >= 0 }
            .distinctUntilChanged()
            .collect { index ->
                val nearingEnd = index >= listState.layoutInfo.totalItemsCount - 4
                if (nearingEnd) {
                    onLoadMore()
                }
            }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
internal fun JellyfinDetailContent(
    detail: JellyfinItemDetail,
    baseUrl: String?,
    accessToken: String?,
    seasons: List<SeasonEpisodes>,
    isEpisode: Boolean,
    onPlay: () -> Unit,
    onTrailer: (() -> Unit)? = null,
    showPlayAction: Boolean = true,
    playActionLabel: String? = null,
    playActionEnabled: Boolean? = null,
    playActionLoading: Boolean = false,
    emptyPlaybackMessage: String? = null,
    downloadStatus: DownloadStatus? = null,
    episodeDownloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onQueueDownload: () -> Unit,
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {},
    onDownloadSeries: (() -> Unit)? = null,
    onDownloadSeason: ((SeasonEpisodes) -> Unit)? = null,
    onViewSeries: (() -> Unit)? = null,
    onOpenEpisode: ((JellyfinItem) -> Unit)? = null,
    audioTracks: List<AudioTrack> = emptyList(),
    selectedAudioTrack: AudioTrack? = null,
    onSelectAudioTrack: (AudioTrack) -> Unit = {},
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleTrack: SubtitleTrack? = null,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    favoriteError: String? = null,
    showPlayedAction: Boolean = isEpisode,
    isPlayed: Boolean = detail.isPlayed,
    playedPending: Boolean = false,
    onTogglePlayed: () -> Unit = {},
    playedError: String? = null,
    onPlayerOptionsModalChange: (ShellModalOwner?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val resolvedPlayActionLabel = playActionLabel ?: stringResource(Res.string.play)
    val audioLabel = stringResource(Res.string.audio)
    val subtitlesLabel = stringResource(Res.string.subtitles)
    val offLabel = stringResource(Res.string.off)
    val defaultLabel = stringResource(Res.string.default_label)
    val forcedLabel = stringResource(Res.string.forced_label)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(LocalResponsiveProfile.current.horizontalContentPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        when {
                            LocalResponsiveProfile.current.isShortHeight -> 200.dp
                            LocalResponsiveProfile.current.isExpanded -> 360.dp
                            else -> 280.dp
                        },
                    ),
        ) {
            PosterImage(
                modifier = Modifier.fillMaxSize(),
                baseUrl = baseUrl,
                itemId = detail.id,
                primaryTag = detail.primaryImageTag,
                thumbTag = null,
                backdropTag = detail.backdropImageTags.firstOrNull(),
                accessToken = accessToken,
                contentDescription = detail.name,
            )
            ImageTextScrim(modifier = Modifier.matchParentSize())
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .semantics { heading() },
            )
        }
        var downloadLabel = stringResource(Res.string.download)
        var downloadEnabled = true
        var primaryAction: () -> Unit = onDownloadSeries ?: onQueueDownload
        var secondaryLabel: String? = null
        var secondaryAction: (() -> Unit)? = null
        var progressFraction: Float? = null
        var statusMessage: String? = null
        var statusColor = MaterialTheme.colorScheme.onSurfaceVariant

        when (downloadStatus) {
            null -> Unit
            is DownloadStatus.Failed -> {
                downloadLabel = stringResource(Res.string.retry_download)
                statusMessage = downloadStatus.cause.message ?: stringResource(Res.string.download_failed)
                statusColor = MaterialTheme.colorScheme.error
                secondaryLabel = stringResource(Res.string.clear)
                secondaryAction = onRemoveDownload
            }
            is DownloadStatus.Queued -> {
                downloadLabel = stringResource(Res.string.download_queued)
                downloadEnabled = false
                statusMessage = stringResource(Res.string.download_waiting)
                secondaryLabel = stringResource(Res.string.cancel)
                secondaryAction = onRemoveDownload
            }
            is DownloadStatus.InProgress -> {
                val total = downloadStatus.totalBytes
                progressFraction =
                    if (total != null && total > 0) {
                        (downloadStatus.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                downloadLabel = stringResource(Res.string.pause)
                primaryAction = onPauseDownload
                secondaryLabel = stringResource(Res.string.cancel)
                secondaryAction = onRemoveDownload
                statusMessage =
                    if (total != null && total > 0) {
                        "${formatBytes(downloadStatus.bytesDownloaded)} / ${formatBytes(total)}"
                    } else {
                        stringResource(Res.string.downloaded_bytes, formatBytes(downloadStatus.bytesDownloaded))
                    }
            }
            is DownloadStatus.WaitingForNetwork -> {
                downloadLabel = stringResource(Res.string.download_waiting)
                downloadEnabled = false
                secondaryLabel = stringResource(Res.string.cancel)
                secondaryAction = onRemoveDownload
                statusMessage = stringResource(Res.string.download_waiting)
            }
            is DownloadStatus.Paused -> {
                downloadLabel = stringResource(Res.string.resume)
                primaryAction = onResumeDownload
                secondaryLabel = stringResource(Res.string.remove_server)
                secondaryAction = onRemoveDownload
                statusMessage = stringResource(Res.string.paused_at, formatBytes(downloadStatus.bytesDownloaded))
            }
            is DownloadStatus.Completed -> {
                downloadLabel = stringResource(Res.string.offline_ready)
                downloadEnabled = false
                secondaryLabel = stringResource(Res.string.remove_server)
                secondaryAction = onRemoveDownload
                statusMessage = stringResource(Res.string.stored_bytes, formatBytes(downloadStatus.bytesDownloaded))
            }
        }
        val hasOfflineSource = downloadStatus is DownloadStatus.Completed
        val hasRemoteSource = detail.mediaSources.isNotEmpty()
        val playEnabled = showPlayAction && (playActionEnabled ?: (hasOfflineSource || hasRemoteSource))
        if (showPlayAction && !playEnabled && statusMessage == null) {
            statusMessage =
                if (isEpisode) {
                    stringResource(Res.string.playback_unavailable_episode)
                } else {
                    stringResource(Res.string.playback_select_episode)
                }
        }
        DetailActionBar(
            showPrimary = showPlayAction,
            primaryLabel = resolvedPlayActionLabel,
            primaryEnabled = playEnabled,
            primaryLoading = playActionLoading,
            onPrimary = onPlay,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onTrailer = onTrailer,
            downloadLabel = downloadLabel,
            downloadIcon =
                when (downloadStatus) {
                    is DownloadStatus.Completed -> Icons.Filled.CloudDone
                    is DownloadStatus.InProgress -> Icons.Filled.Pause
                    is DownloadStatus.Failed -> Icons.Filled.Error
                    else -> Icons.Filled.Download
                },
            downloadEnabled = downloadEnabled,
            onDownload = primaryAction,
            showPlayedAction = showPlayedAction,
            isPlayed = isPlayed,
            playedPending = playedPending,
            onTogglePlayed = onTogglePlayed,
        )
        secondaryLabel?.let { label ->
            TextButton(onClick = { secondaryAction?.invoke() }) {
                Text(text = label)
            }
        }
        progressFraction?.let { progress ->
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
            )
        }
        favoriteError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        playedError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (isEpisode) {
            detail.overview
                ?.takeIf { it.isNotBlank() }
                ?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
        }
        if (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()) {
            val compactTrackLayout = LocalResponsiveProfile.current.isCompact
            val audioPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                if (audioTracks.isNotEmpty()) {
                    val selected = selectedAudioTrack ?: audioTracks.firstOrNull()
                    CompactTrackPicker(
                        label = audioLabel,
                        selectedSummary = selected?.let(::audioTrackSummary) ?: defaultLabel,
                        options =
                            audioTracks.map { track ->
                                TrackPickerOption(
                                    value = track,
                                    fullLabel = audioTrackLabel(track),
                                    selected = track.id == selected?.id,
                                )
                            },
                        onSelect = onSelectAudioTrack,
                        modifier = pickerModifier.testTag(TrackPickerTestTags.AUDIO),
                        onShellModalChange = onPlayerOptionsModalChange,
                    )
                }
            }
            val subtitlePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                if (subtitleTracks.isNotEmpty()) {
                    CompactTrackPicker<SubtitleSelection>(
                        label = subtitlesLabel,
                        selectedSummary =
                            subtitleTrackSummary(
                                selectedSubtitleTrack,
                                offLabel = offLabel,
                                defaultLabel = defaultLabel,
                                forcedLabel = forcedLabel,
                            ),
                        options =
                            listOf<TrackPickerOption<SubtitleSelection>>(
                                TrackPickerOption(
                                    value = SubtitleSelection.Off,
                                    fullLabel = offLabel,
                                    selected = selectedSubtitleTrack == null,
                                ),
                            ) +
                                subtitleTracks.map { track ->
                                    TrackPickerOption(
                                        value = SubtitleSelection.Track(track),
                                        fullLabel = subtitleTrackLabel(track, defaultLabel, forcedLabel),
                                        selected = track.id == selectedSubtitleTrack?.id,
                                    )
                                },
                        onSelect = { selection ->
                            onSelectSubtitleTrack(
                                when (selection) {
                                    SubtitleSelection.Off -> null
                                    is SubtitleSelection.Track -> selection.track
                                },
                            )
                        },
                        modifier = pickerModifier.testTag(TrackPickerTestTags.SUBTITLES),
                        onShellModalChange = onPlayerOptionsModalChange,
                    )
                }
            }
            if (compactTrackLayout) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    audioPicker(Modifier.fillMaxWidth())
                    subtitlePicker(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    audioPicker(Modifier.weight(1f))
                    subtitlePicker(Modifier.weight(1f))
                }
            }
        }
        if (detail.taglines.isNotEmpty()) {
            Text(
                text = detail.taglines.joinToString(separator = "\n"),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (!detail.overview.isNullOrBlank()) {
            if (!isEpisode) {
                Text(
                    text = detail.overview!!,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        if (isEpisode) {
            onViewSeries?.let { openSeries ->
                TextButton(onClick = openSeries) {
                    Text(stringResource(Res.string.view_series))
                }
            }
        }
        val facts =
            buildList {
                detail.productionYear?.let { add(it.toString()) }
                detail.runTimeTicks
                    ?.let(::ticksToMinutes)
                    ?.takeIf { it > 0 }
                    ?.let { add(stringResource(Res.string.minutes, it)) }
                detail.communityRating?.let { add("★ ${((it * 10).roundToInt() / 10.0)}") }
                detail.officialRating?.takeIf { it.isNotBlank() }?.let(::add)
            }
        if (facts.isNotEmpty() || detail.genres.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (facts + detail.genres).forEach { fact ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = fact,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (detail.studios.isNotEmpty()) {
            Text(
                text = detail.studios.joinToString(separator = " · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        emptyPlaybackMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SeasonEpisodeSelector(
            seasons = seasons,
            baseUrl = baseUrl,
            accessToken = accessToken,
            modifier = Modifier.fillMaxWidth(),
            episodeDownloadStatuses = episodeDownloadStatuses,
            onDownloadSeason = onDownloadSeason,
            onOpenEpisode = onOpenEpisode,
        )
        if (detail.mediaSources.isNotEmpty()) {
            var technicalExpanded by remember(detail.id) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.technical_details),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                detail.mediaSources.forEach { source ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { technicalExpanded = !technicalExpanded },
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = mediaSourceSummary(source),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = if (technicalExpanded) 3 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (technicalExpanded && source.streams.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    source.streams.take(12).forEach { stream ->
                                        Text(
                                            text =
                                                "${localizedMediaStreamType(stream.type)}: " +
                                                    (stream.displayTitle ?: stream.codec.orEmpty()),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
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
}

@Composable
private fun DetailActionBar(
    showPrimary: Boolean,
    primaryLabel: String,
    primaryEnabled: Boolean,
    primaryLoading: Boolean,
    onPrimary: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onTrailer: (() -> Unit)?,
    downloadLabel: String,
    downloadIcon: ImageVector,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    showPlayedAction: Boolean,
    isPlayed: Boolean,
    playedPending: Boolean,
    onTogglePlayed: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showPrimary) {
            FilledTonalButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag(DetailActionTestTags.PRIMARY),
                onClick = onPrimary,
                enabled = primaryEnabled && !primaryLoading,
                shape = RoundedCornerShape(28.dp),
            ) {
                if (primaryLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val favoriteLabel =
                if (isFavorite) {
                    stringResource(Res.string.remove_from_favorites)
                } else {
                    stringResource(Res.string.add_to_favorites)
                }
            DetailSecondaryAction(
                modifier = Modifier.weight(1f).testTag(DetailActionTestTags.FAVORITE),
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = stringResource(Res.string.favorite),
                contentDescription = favoriteLabel,
                onClick = onToggleFavorite,
            )
            onTrailer?.let { playTrailer ->
                DetailSecondaryAction(
                    modifier = Modifier.weight(1f).testTag(DetailActionTestTags.TRAILER),
                    icon = Icons.Filled.PlayArrow,
                    label = stringResource(Res.string.trailer),
                    contentDescription = stringResource(Res.string.watch_trailer),
                    onClick = playTrailer,
                )
            }
            DetailSecondaryAction(
                modifier = Modifier.weight(1f).testTag(DetailActionTestTags.DOWNLOAD),
                icon = downloadIcon,
                label = stringResource(Res.string.download),
                contentDescription = downloadLabel,
                enabled = downloadEnabled,
                onClick = onDownload,
            )
            if (showPlayedAction) {
                DetailSecondaryAction(
                    modifier = Modifier.weight(1f).testTag(DetailActionTestTags.PLAYED),
                    icon = Icons.Filled.CheckCircle,
                    label = stringResource(if (isPlayed) Res.string.unseen else Res.string.seen),
                    contentDescription =
                        stringResource(if (isPlayed) Res.string.mark_as_unseen else Res.string.mark_as_seen),
                    enabled = !playedPending,
                    onClick = onTogglePlayed,
                )
            }
        }
    }
}

@Composable
private fun DetailSecondaryAction(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        modifier =
            modifier
                .heightIn(min = 64.dp)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                },
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun mediaSourceSummary(source: dev.jellystack.core.jellyfin.JellyfinMediaSource): String {
    val video = source.streams.firstOrNull { it.type == dev.jellystack.core.jellyfin.JellyfinMediaStreamType.VIDEO }
    val audio = source.streams.firstOrNull { it.type == dev.jellystack.core.jellyfin.JellyfinMediaStreamType.AUDIO }
    val subtitleCount = source.streams.count { it.type == dev.jellystack.core.jellyfin.JellyfinMediaStreamType.SUBTITLE }
    return buildList {
        video?.displayTitle?.takeIf { it.isNotBlank() }?.let(::add)
        video
            ?.codec
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
        source.container
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
        audio?.displayTitle?.takeIf { it.isNotBlank() }?.let(::add)
        if (subtitleCount > 0) add(stringResource(Res.string.subtitle_count, subtitleCount))
        source.runTimeTicks
            ?.let(::ticksToMinutes)
            ?.takeIf { it > 0 }
            ?.let { add(stringResource(Res.string.minutes, it)) }
    }.joinToString(" · ").ifBlank { source.name ?: stringResource(Res.string.media_source) }
}

@Composable
private fun localizedMediaStreamType(type: dev.jellystack.core.jellyfin.JellyfinMediaStreamType): String =
    when (type) {
        dev.jellystack.core.jellyfin.JellyfinMediaStreamType.VIDEO -> stringResource(Res.string.video)
        dev.jellystack.core.jellyfin.JellyfinMediaStreamType.AUDIO -> stringResource(Res.string.audio)
        dev.jellystack.core.jellyfin.JellyfinMediaStreamType.SUBTITLE -> stringResource(Res.string.subtitles)
        dev.jellystack.core.jellyfin.JellyfinMediaStreamType.OTHER -> stringResource(Res.string.other)
    }

@Composable
private fun localizedSeasonLabel(seasonNumber: Int?): String =
    when {
        seasonNumber == null -> stringResource(Res.string.episodes)
        seasonNumber <= 0 -> stringResource(Res.string.specials)
        else -> stringResource(Res.string.season_number, seasonNumber)
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SeasonEpisodeSelector(
    seasons: List<SeasonEpisodes>,
    baseUrl: String?,
    accessToken: String?,
    modifier: Modifier = Modifier,
    episodeDownloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    onDownloadSeason: ((SeasonEpisodes) -> Unit)? = null,
    onOpenEpisode: ((JellyfinItem) -> Unit)? = null,
) {
    if (seasons.isEmpty()) return
    val downloadSeasonLabel = stringResource(Res.string.download_season)

    var selectedSeasonIndex by remember(seasons) { mutableStateOf(0) }
    var userSelectedSeason by remember(seasons) { mutableStateOf(false) }

    val targetDefault =
        remember(seasons, episodeDownloadStatuses) {
            defaultSeasonSelectionIndex(seasons, episodeDownloadStatuses)
                .coerceIn(0, seasons.lastIndex)
        }

    LaunchedEffect(seasons, episodeDownloadStatuses, targetDefault, userSelectedSeason) {
        if (!userSelectedSeason) {
            selectedSeasonIndex = targetDefault
        } else {
            selectedSeasonIndex = selectedSeasonIndex.coerceIn(0, seasons.lastIndex)
        }
    }

    selectedSeasonIndex = selectedSeasonIndex.coerceIn(0, seasons.lastIndex)
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(Res.string.seasons),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value =
                            selectedSeason?.let { localizedSeasonLabel(it.seasonNumber) }
                                ?: stringResource(Res.string.select_season),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.season)) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                            ),
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        seasons.forEachIndexed { index, season ->
                            DropdownMenuItem(
                                text = { Text(localizedSeasonLabel(season.seasonNumber)) },
                                onClick = {
                                    selectedSeasonIndex = index
                                    userSelectedSeason = true
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
            if (selectedSeason != null) {
                onDownloadSeason?.let { downloadSeason ->
                    IconButton(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = downloadSeasonLabel
                                },
                        onClick = { downloadSeason(selectedSeason) },
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        val episodes = selectedSeason?.episodes.orEmpty()
        if (episodes.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_episodes_for_season),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                episodes.forEach { episode ->
                    val isDownloaded = episodeDownloadStatuses[episode.id] is DownloadStatus.Completed
                    EpisodeCard(
                        episode = episode,
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        isDownloaded = isDownloaded,
                        onClick = onOpenEpisode?.let { open -> { open(episode) } },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AudioTrackDropdown(
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    onSelect: (AudioTrack) -> Unit,
) {
    val label = stringResource(Res.string.audio_track)
    var expanded by remember(tracks) { mutableStateOf(false) }
    val current = selectedTrack ?: tracks.firstOrNull()
    val currentLabel = current?.let { audioTrackLabel(it) }.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(audioTrackLabel(track)) },
                    onClick = {
                        onSelect(track)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SubtitleTrackDropdown(
    tracks: List<SubtitleTrack>,
    selectedTrack: SubtitleTrack?,
    onSelect: (SubtitleTrack?) -> Unit,
) {
    val label = stringResource(Res.string.subtitles)
    val offLabel = stringResource(Res.string.off)
    val defaultLabel = stringResource(Res.string.default_label)
    val forcedLabel = stringResource(Res.string.forced_label)
    var expanded by remember(tracks) { mutableStateOf(false) }
    val currentLabel = selectedTrack?.let { subtitleTrackLabel(it, defaultLabel, forcedLabel) } ?: offLabel
    val options = remember(tracks) { listOf<SubtitleTrack?>(null) + tracks }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track?.let { subtitleTrackLabel(it, defaultLabel, forcedLabel) } ?: offLabel) },
                    onClick = {
                        onSelect(track)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private fun progressPercentage(item: JellyfinItem): Double = progressFraction(item).toDouble() * 100.0

private fun defaultSeasonSelectionIndex(
    seasons: List<SeasonEpisodes>,
    episodeDownloadStatuses: Map<String, DownloadStatus> = emptyMap(),
): Int {
    if (seasons.isEmpty()) return 0

    val downloadedSeasonIndex =
        seasons
            .withIndex()
            .firstOrNull { (_, season) ->
                season.episodes.any { episodeDownloadStatuses[it.id] is DownloadStatus.Completed }
            }?.index
    if (downloadedSeasonIndex != null) {
        return downloadedSeasonIndex
    }

    val localSeasonIndex =
        seasons
            .withIndex()
            .firstOrNull { (_, season) ->
                season.episodes.any { it.hasLocalMedia() }
            }?.index
    if (localSeasonIndex != null) {
        return localSeasonIndex
    }

    data class Candidate(
        val seasonIndex: Int,
        val hasProgress: Boolean,
        val inProgress: Boolean,
        val lastPlayedMillis: Long,
        val hasMedia: Boolean,
    )

    val candidates =
        seasons.flatMapIndexed { index, season ->
            season.episodes.map { episode ->
                val progress = progressPercentage(episode)
                val hasProgress = progress > 0.0
                val inProgress = progress > 0.0 && progress < 98.0
                val lastPlayedMillis =
                    episode.lastPlayed
                        ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
                        ?: Long.MIN_VALUE
                val hasMedia = episode.runTimeTicks != null || !episode.mediaType.isNullOrBlank()
                Candidate(
                    seasonIndex = index,
                    hasProgress = hasProgress,
                    inProgress = inProgress,
                    lastPlayedMillis = lastPlayedMillis,
                    hasMedia = hasMedia,
                )
            }
        }
    val watchedCandidate =
        candidates
            .filter { it.hasProgress }
            .maxWithOrNull(
                compareBy<Candidate>({ if (it.inProgress) 1 else 0 }, { it.lastPlayedMillis }),
            )
    if (watchedCandidate != null) {
        return watchedCandidate.seasonIndex
    }
    val withMedia = candidates.firstOrNull { it.hasMedia }
    return withMedia?.seasonIndex ?: 0
}

internal fun JellyfinItem.hasLocalMedia(): Boolean {
    val normalized = locationType?.lowercase() ?: return false
    return normalized == "filesystem" || normalized == "offline"
}

@Composable
private fun EpisodeCard(
    episode: JellyfinItem,
    baseUrl: String?,
    accessToken: String?,
    isDownloaded: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val content: @Composable () -> Unit = {
        val progress = progressFraction(episode)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(
                            if (LocalResponsiveProfile.current.isCompact) {
                                124.dp
                            } else {
                                168.dp
                            },
                        ).aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp)),
            ) {
                PosterImage(
                    modifier = Modifier.fillMaxSize(),
                    baseUrl = baseUrl,
                    itemId = episode.id,
                    primaryTag = episode.primaryImageTag,
                    thumbTag = episode.thumbImageTag,
                    backdropTag = episode.backdropImageTag,
                    accessToken = accessToken,
                    contentDescription = episode.name,
                )
                if (isDownloaded) {
                    DownloadedIconBadge(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = episodeLabel(episode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress > 0f) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                        )
                        Text(
                            text = stringResource(Res.string.watched_percent, (progress * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                episode.overview
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (LocalResponsiveProfile.current.isCompact) 3 else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
        }
    }
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = colors,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = colors,
        ) {
            content()
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    val mb = kb / 1024.0
    if (mb < 1.0) {
        val rounded = (kb * 10).roundToInt() / 10.0
        return "$rounded KB"
    }
    val gb = mb / 1024.0
    if (gb < 1.0) {
        val rounded = (mb * 10).roundToInt() / 10.0
        return "$rounded MB"
    }
    val rounded = (gb * 10).roundToInt() / 10.0
    return "$rounded GB"
}

private fun progressFraction(item: JellyfinItem): Float {
    val percentage = item.playedPercentage
    if (percentage != null) {
        return (percentage / 100.0).coerceIn(0.0, 1.0).toFloat()
    }
    val position = item.positionTicks ?: return 0f
    val runtime = item.runTimeTicks ?: return 0f
    if (runtime <= 0) return 0f
    return (position.toDouble() / runtime).coerceIn(0.0, 1.0).toFloat()
}

private fun ticksToMinutes(ticks: Long): Int = (ticks / 600_000_000L).toInt()
