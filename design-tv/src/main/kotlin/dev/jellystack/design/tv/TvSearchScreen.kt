@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem

/** What the search screen reports back; the root turns these into coordinator calls and navigation. */
internal interface TvSearchActions {
    fun onQueryChanged(query: String)

    fun onSourceChanged(source: TvSearchSource) = Unit

    fun onEnterEditMode() = Unit

    fun onEnterBrowseMode() = Unit

    fun onRetryJellyfin() = Unit

    fun onRetrySeerr() = Unit

    fun onVoiceSearch() = Unit

    fun onJellyfinItem(item: JellyfinItem) = Unit

    fun onSeerrItem(item: JellyseerrSearchItem) = Unit
}

/** Search actions that drive [coordinator] and open results through the given navigation. */
internal fun tvSearchActions(
    coordinator: TvSearchCoordinator,
    openJellyfin: (JellyfinItem) -> Unit,
    openSeerr: (JellyseerrSearchItem) -> Unit,
): TvSearchActions =
    object : TvSearchActions {
        override fun onQueryChanged(query: String) = coordinator.search(query)

        override fun onSourceChanged(source: TvSearchSource) = coordinator.selectSource(source)

        override fun onEnterEditMode() = coordinator.enterEditMode()

        override fun onEnterBrowseMode() = coordinator.enterBrowseMode()

        override fun onRetryJellyfin() = coordinator.retryJellyfin()

        override fun onRetrySeerr() = coordinator.retrySeerr()

        override fun onVoiceSearch() = coordinator.launchVoiceSearch()

        override fun onJellyfinItem(item: JellyfinItem) = openJellyfin(item)

        override fun onSeerrItem(item: JellyseerrSearchItem) = openSeerr(item)
    }

/**
 * The search field and the source chips stay at the top in every state, so typing never moves or rebuilds the
 * field and the keyboard stays open while results arrive. Results sit in rows below, over the focused result's
 * backdrop.
 */
@Composable
internal fun TvSearchScreen(
    searchState: TvSearchUiState,
    homeState: JellyfinHomeState,
    strings: TvStrings,
    focusMemory: TvFocusMemory,
    actions: TvSearchActions,
) {
    val sessionState = searchState.session
    val queryFocusRequester = remember { FocusRequester() }
    val sourceFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var retryFocusRequest by remember { mutableStateOf<TvRetryFocusRequest?>(null) }
    var backdropUrl by remember { mutableStateOf<String?>(null) }
    val presentation = tvSearchPresentation(searchState)
    val listState = rememberLazyListState()
    val rowStates = rememberTvLazyRowStates(listOf("jellyfin", "seerr"))
    val indices = TvSearchListIndices(presentation)
    val locations = tvSearchFocusLocations(searchState, presentation, indices)
    TvRouteFocusMaterializer(
        ownerId = "search-lists",
        targetIds = locations.keys,
        fallbackTargetIds = setOf(TV_SEARCH_QUERY_TARGET),
    ) { targetId -> locations[targetId]?.let { materializeTvLazyTarget(listState, rowStates, it) } ?: false }
    TvRetryFocusRecovery(retryFocusRequest)
    BackHandler(enabled = sessionState.mode == TvSearchMode.EDIT) {
        keyboardController?.hide()
        actions.onEnterBrowseMode()
    }
    LaunchedEffect(sessionState.mode) {
        // Waiting for placement keeps a fresh Search route from losing its first focus request.
        withFrameNanos { }
        when (sessionState.mode) {
            TvSearchMode.EDIT -> {
                queryFocusRequester.requestFocus()
                keyboardController?.show()
            }
            TvSearchMode.BROWSE -> {
                keyboardController?.hide()
                sourceFocusRequester.requestFocus()
            }
        }
    }
    val results =
        TvSearchResultsContext(
            presentation = presentation,
            indices = indices,
            homeState = homeState,
            strings = strings,
            focusMemory = focusMemory,
        )
    Box(Modifier.fillMaxSize()) {
        TvSearchBackdrop(backdropUrl)
        Column(Modifier.fillMaxSize()) {
            TvSearchHeader(searchState, strings, queryFocusRequester, sourceFocusRequester, actions)
            TvSearchResults(
                context = results,
                lists = TvSearchListStates(listState, rowStates),
                actions = actions,
                onRetry = { retrySource ->
                    retrySource()
                    retryFocusRequest =
                        TvRetryFocusRequest(
                            revision = (retryFocusRequest?.revision ?: 0L) + 1L,
                            preferredTargetId = TV_SEARCH_QUERY_TARGET,
                        )
                },
                onBackdrop = { backdropUrl = it },
            )
        }
    }
}

private class TvSearchResultsContext(
    val presentation: TvSearchPresentation,
    val indices: TvSearchListIndices,
    val homeState: JellyfinHomeState,
    val strings: TvStrings,
    val focusMemory: TvFocusMemory,
)

private class TvSearchListStates(
    val list: LazyListState,
    val rows: Map<String, LazyListState>,
)

@Composable
private fun ColumnScope.TvSearchResults(
    context: TvSearchResultsContext,
    lists: TvSearchListStates,
    actions: TvSearchActions,
    onRetry: (() -> Unit) -> Unit,
    onBackdrop: (String?) -> Unit,
) {
    val strings = context.strings
    val indices = context.indices
    LazyColumn(
        state = lists.list,
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentPadding =
            PaddingValues(
                start = TvLayoutTokens.ContentStart - TV_SEARCH_ROW_EDGE,
                end = TvLayoutTokens.SafeInsets.horizontal,
                top = 12.dp,
                bottom = TvLayoutTokens.SafeInsets.vertical,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        indices.searching?.let { item("searching") { TvStatusAnchor(strings.searching) } }
        indices.jellyfinFailure?.let {
            item("jellyfin-error") {
                TvSearchSourceFailure(
                    message = strings.jellyfinSearchFailed,
                    retryLabel = strings.retry,
                    focusTargetId = TV_SEARCH_JELLYFIN_RETRY_TARGET,
                    modifier = Modifier.padding(start = TV_SEARCH_ROW_EDGE),
                ) { onRetry(actions::onRetryJellyfin) }
            }
        }
        indices.jellyfinRow?.let {
            item("jellyfin-results") {
                TvJellyfinRow(
                    title = TV_BRAND_JELLYFIN,
                    items = context.presentation.jellyfinItems,
                    state = context.homeState,
                    strings = strings,
                    focusMemory = context.focusMemory,
                    onItem = actions::onJellyfinItem,
                    onPreviewFocus = { item, _ ->
                        val home = context.homeState
                        onBackdrop(tvJellyfinBackdropUrl(home.imageBaseUrl, home.imageAccessToken, item))
                    },
                    routeKey = "search",
                    listState = lists.rows.getValue("jellyfin"),
                    focusTargetId = { itemId -> tvSearchResultTargetId("jellyfin", itemId) },
                    edgePadding = TV_SEARCH_ROW_EDGE,
                )
            }
        }
        indices.seerrFailure?.let {
            item("seerr-error") {
                TvSearchSourceFailure(
                    message = strings.seerrSearchFailed,
                    retryLabel = strings.retry,
                    focusTargetId = TV_SEARCH_SEERR_RETRY_TARGET,
                    modifier = Modifier.padding(start = TV_SEARCH_ROW_EDGE),
                ) { onRetry(actions::onRetrySeerr) }
            }
        }
        indices.seerrRow?.let {
            item("seerr-results") {
                TvSeerrRow(
                    title = TV_BRAND_SEERR,
                    items = context.presentation.seerrItems,
                    focusMemory = context.focusMemory,
                    routeKey = "search",
                    onItem = actions::onSeerrItem,
                    listState = lists.rows.getValue("seerr"),
                    focusTargetId = { id -> tvSearchResultTargetId("seerr", id) },
                    edgePadding = TV_SEARCH_ROW_EDGE,
                    onFocused = { item ->
                        val backdrop = item.backdropPath
                        onBackdrop(tmdbImageUrl(backdrop ?: item.posterPath, backdrop = backdrop != null))
                    },
                )
            }
        }
        indices.empty?.let { item("empty") { TvStatusAnchor(strings.noResults) } }
    }
}

/** Positions of the result list's items; the header is not part of the list. */
internal class TvSearchListIndices(
    presentation: TvSearchPresentation,
) {
    private var next = 0
    val searching = if (presentation.showSearching) next++ else null
    val jellyfinFailure = if (presentation.showJellyfinFailure) next++ else null
    val jellyfinRow = if (presentation.jellyfinItems.isNotEmpty()) next++ else null
    val seerrFailure = if (presentation.showSeerrFailure) next++ else null
    val seerrRow = if (presentation.seerrItems.isNotEmpty()) next++ else null
    val empty = if (presentation.showNoResults) next else null
}

private fun tvSearchFocusLocations(
    searchState: TvSearchUiState,
    presentation: TvSearchPresentation,
    indices: TvSearchListIndices,
): Map<String, TvLazyFocusLocation> =
    buildMap {
        // The header targets are always composed; materializing them scrolls the results back to the top.
        put(TV_SEARCH_QUERY_TARGET, TvLazyFocusLocation(0))
        if (searchState.showVoiceAction) put(TV_SEARCH_VOICE_TARGET, TvLazyFocusLocation(0))
        TvSearchSource.entries.forEach { put(tvSearchSourceTargetId(it.name.lowercase()), TvLazyFocusLocation(0)) }
        indices.jellyfinFailure?.let { put(TV_SEARCH_JELLYFIN_RETRY_TARGET, TvLazyFocusLocation(it)) }
        indices.jellyfinRow?.let { rowIndex ->
            presentation.jellyfinItems.forEachIndexed { index, item ->
                put(tvSearchResultTargetId("jellyfin", item.id), TvLazyFocusLocation(rowIndex, "jellyfin", index))
            }
        }
        indices.seerrFailure?.let { put(TV_SEARCH_SEERR_RETRY_TARGET, TvLazyFocusLocation(it)) }
        indices.seerrRow?.let { rowIndex ->
            presentation.seerrItems.forEachIndexed { index, item ->
                put(
                    tvSearchResultTargetId("seerr", "${item.mediaType}:${item.tmdbId}"),
                    TvLazyFocusLocation(rowIndex, "seerr", index),
                )
            }
        }
    }

@Composable
private fun TvSearchHeader(
    searchState: TvSearchUiState,
    strings: TvStrings,
    queryFocusRequester: FocusRequester,
    sourceFocusRequester: FocusRequester,
    actions: TvSearchActions,
) {
    val sessionState = searchState.session
    Column(
        Modifier.padding(
            start = TvLayoutTokens.ContentStart,
            end = TvLayoutTokens.SafeInsets.horizontal,
            top = TvLayoutTokens.SafeInsets.vertical,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = sessionState.query,
            onValueChange = actions::onQueryChanged,
            placeholder = { Text(strings.searchHint) },
            singleLine = true,
            readOnly = sessionState.mode == TvSearchMode.BROWSE,
            colors = tvOutlinedTextFieldColors(),
            modifier =
                Modifier
                    .tvScreenEntryFocus(focusTargetId = TV_SEARCH_QUERY_TARGET)
                    .tvFocusTarget(queryFocusRequester, focusTargetId = TV_SEARCH_QUERY_TARGET)
                    .focusRequester(queryFocusRequester)
                    .fillMaxWidth(0.6f)
                    .height(60.dp)
                    .testTag("tv-search-query")
                    .onPreviewKeyEvent { event ->
                        val opensEditing =
                            sessionState.mode == TvSearchMode.BROWSE &&
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        if (opensEditing) actions.onEnterEditMode()
                        opensEditing
                    }.tvReturnToNavigationRailOnLeft()
                    .focusProperties {
                        down = sourceFocusRequester
                        right = sourceFocusRequester
                    },
        )
        TvSearchSourceChips(searchState, strings, sourceFocusRequester, actions)
        searchState.voiceError?.let { message -> TvStatusAnchor("${strings.requestFailed}: $message") }
    }
}

@Composable
private fun TvSearchSourceChips(
    searchState: TvSearchUiState,
    strings: TvStrings,
    sourceFocusRequester: FocusRequester,
    actions: TvSearchActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvSearchSource.entries.forEachIndexed { index, source ->
            val label =
                when (source) {
                    TvSearchSource.ALL -> strings.all
                    TvSearchSource.JELLYFIN -> TV_BRAND_JELLYFIN
                    TvSearchSource.SEERR -> TV_BRAND_SEERR
                }
            TvActionButton(
                label = label,
                onClick = { actions.onSourceChanged(source) },
                modifier =
                    Modifier
                        .then(if (index == 0) Modifier.focusRequester(sourceFocusRequester) else Modifier)
                        .testTag("tv-search-source-${source.name.lowercase()}"),
                primary = searchState.session.source == source,
                selected = searchState.session.source == source,
                focusToNavigationRailOnLeft = index == 0,
                focusTargetId = tvSearchSourceTargetId(source.name.lowercase()),
            )
        }
        if (searchState.showVoiceAction) {
            TvActionButton(
                label = if (searchState.isVoiceListening) strings.searching else strings.search,
                onClick = actions::onVoiceSearch,
                enabled = !searchState.isVoiceListening,
                leading = { Icon(Icons.Default.Mic, contentDescription = null, tint = TvText) },
                modifier = Modifier.testTag("tv-search-voice"),
                focusTargetId = TV_SEARCH_VOICE_TARGET,
            )
        }
    }
}

@Composable
internal fun TvSearchSourceFailure(
    message: String,
    retryLabel: String,
    focusTargetId: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = TvTextMuted, modifier = Modifier.weight(1f).tvStatusSemantics(message))
        TvActionButton(label = retryLabel, onClick = onRetry, focusTargetId = focusTargetId)
    }
}

/** The focused result's artwork behind the whole screen; dark until a result has focus. */
@Composable
private fun TvSearchBackdrop(url: String?) {
    val fadeMs = tvHomeHeroFadeMillis()
    AnimatedContent(
        targetState = url,
        transitionSpec = { tvHomeHeroFade(fadeMs) },
        modifier = Modifier.fillMaxSize(),
        label = "tv-search-backdrop",
    ) { shown ->
        if (shown != null) {
            Box(Modifier.fillMaxSize()) {
                TvHomeBackdropImage(shown)
                TvHomeBackdropScrims()
            }
        }
    }
}

/** Room for a focused card's halo at the start of each result row. */
private val TV_SEARCH_ROW_EDGE = 12.dp
