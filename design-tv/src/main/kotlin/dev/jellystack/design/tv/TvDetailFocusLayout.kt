@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "MatchingDeclarationName",
    "ReturnCount",
)

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal data class TvDetailSectionFocusModifiers(
    val navigationModifier: Modifier,
    val itemModifiers: Map<String, Modifier>,
    val itemFocusRequesters: Map<String, FocusRequester>,
    val horizontalListState: LazyListState,
) {
    fun itemModifier(itemId: String): Modifier = itemModifiers[itemId] ?: Modifier

    fun itemFocusRequester(itemId: String): FocusRequester? = itemFocusRequesters[itemId]
}

/** Section id of the body focus anchor: the Details section at the bottom of the page. */
private const val TV_DETAIL_BODY_SECTION_ID = "details"

private data class TvPendingFocusRecovery(
    val transactionId: Long,
    val source: TvFocusAnchor,
    val target: TvFocusAnchor,
)

private fun TvDetailSection.participatesInSectionFocus(): Boolean =
    this is TvDetailSection.Seasons ||
        this is TvDetailSection.Episodes ||
        this is TvDetailSection.Cast ||
        this is TvDetailSection.Similar

@Composable
internal fun TvDetailFocusLayout(
    uiState: TvDetailUiState,
    heroContentDescription: String,
    hasPrimaryAction: Boolean = true,
    heroHeightDp: Int = TV_DETAIL_HERO_HEIGHT_DP,
    modifier: Modifier = Modifier,
    awaitFocusStart: suspend (TvFocusDestination) -> Unit = {},
    awaitFocusReady: suspend (TvFocusDestination) -> Unit = { withFrameNanos { } },
    heroContent: @Composable BoxScope.(primaryActionModifier: Modifier, actionRowModifier: Modifier) -> Unit,
    content: LazyListScope.(bodyFocusModifier: Modifier, sectionFocusModifiers: Map<String, TvDetailSectionFocusModifiers>) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentUiState by rememberUpdatedState(uiState)
    val routeKey = uiState.routeKey
    val focusContext = LocalTvFocusContext.current
    val persistedRouteKey = focusContext?.routeKey ?: routeKey
    val focusMemory = focusContext?.focusMemory
    val primaryActionFocusRequester = remember(routeKey) { FocusRequester() }
    val bodyFocusRequester = remember(routeKey) { FocusRequester() }
    val itemFocusRequesters = remember(routeKey) { mutableMapOf<String, FocusRequester>() }
    val focusSections = uiState.sections.filter { it.participatesInSectionFocus() && it.itemIds.isNotEmpty() }
    val horizontalListStates =
        focusSections.associate { section ->
            section.id to key(section.id) { rememberLazyListState() }
        }
    val bodySectionIndex = uiState.sections.indexOfFirst { it is TvDetailSection.Details }.coerceAtLeast(0)
    val bodyLazyItemIndex = bodySectionIndex + 1
    var lastConfirmedItemAnchor by remember(routeKey) { mutableStateOf<TvFocusAnchor?>(null) }
    var focusOwnership by remember(routeKey) { mutableStateOf<TvFocusDestination?>(null) }
    var destinationIntent by
        remember(routeKey) {
            mutableStateOf<TvFocusDestination?>(
                if (hasPrimaryAction) TvFocusDestination.PRIMARY_ACTION else TvFocusDestination.BODY,
            )
        }
    var pendingFocusRecovery by remember(routeKey) { mutableStateOf<TvPendingFocusRecovery?>(null) }
    var focusTransactionId by remember(routeKey) { mutableStateOf(0L) }
    var activeSectionIndex by remember(routeKey) { mutableStateOf(0) }
    var activeItemIndex by remember(routeKey) { mutableStateOf(0) }
    var pendingPersistedAnchor by
        remember(routeKey, focusMemory) {
            mutableStateOf(focusMemory?.restore(persistedRouteKey)?.anchor)
        }
    var restoringPersistedAnchor by remember(routeKey) { mutableStateOf<TvFocusAnchor?>(null) }
    val restorationTargets =
        buildList {
            if (hasPrimaryAction) {
                add(
                    tvFocusTarget("detail:primary", actionable = true)
                        .copy(anchor = TvFocusAnchor("actions", "primary", TvFocusDestination.PRIMARY_ACTION)),
                )
            }
            add(
                tvFocusTarget(
                    "detail:body",
                    actionable = true,
                ).copy(anchor = TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY)),
            )
            focusSections.forEach { section ->
                section.itemIds.forEachIndexed { index, itemId ->
                    add(
                        TvFocusTarget(
                            targetId = "detail:${section.id}:$itemId",
                            anchor = TvFocusAnchor(section.id, itemId, TvFocusDestination.SECTION_ITEM),
                            horizontalCenter = index.toFloat(),
                            horizontalIndex = index,
                        ),
                    )
                }
            }
        }

    fun rememberFocus(
        anchor: TvFocusAnchor,
        horizontalIndex: Int = 0,
    ) {
        if (pendingPersistedAnchor != null) {
            if (restoringPersistedAnchor != anchor) return
            pendingPersistedAnchor = null
            restoringPersistedAnchor = null
        }
        focusMemory?.remember(
            routeKey = persistedRouteKey,
            anchor = anchor,
            horizontalCenter = horizontalIndex.toFloat(),
            horizontalIndex = horizontalIndex,
        )
    }

    fun requesterKey(
        sectionId: String,
        itemId: String,
    ): String = "$sectionId\u0000$itemId"

    fun requester(
        sectionId: String,
        itemId: String,
    ): FocusRequester = itemFocusRequesters.getOrPut(requesterKey(sectionId, itemId)) { FocusRequester() }

    fun ownDestination(
        destination: TvFocusDestination,
        cancelPersistedRestoration: Boolean = true,
    ) {
        if (cancelPersistedRestoration) {
            pendingPersistedAnchor = null
            restoringPersistedAnchor = null
        }
        focusTransactionId += 1
        pendingFocusRecovery = null
        destinationIntent = destination
        focusOwnership = null
    }

    fun focusPrimaryAction(cancelPersistedRestoration: Boolean = true) {
        ownDestination(TvFocusDestination.PRIMARY_ACTION, cancelPersistedRestoration)
        val transactionId = focusTransactionId
        scope.launch {
            awaitFocusStart(TvFocusDestination.PRIMARY_ACTION)
            if (focusTransactionId != transactionId) return@launch
            listState.scrollToItem(0)
            if (focusTransactionId != transactionId) return@launch
            awaitFocusReady(TvFocusDestination.PRIMARY_ACTION)
            if (focusTransactionId != transactionId) return@launch
            val focused = runCatching { primaryActionFocusRequester.requestFocus() }.getOrDefault(false)
            if (focused && focusTransactionId == transactionId) {
                withFrameNanos { }
                if (focusTransactionId == transactionId) listState.scrollToItem(0)
            }
        }
    }

    fun focusBody(cancelPersistedRestoration: Boolean = true) {
        ownDestination(TvFocusDestination.BODY, cancelPersistedRestoration)
        val transactionId = focusTransactionId
        scope.launch {
            awaitFocusStart(TvFocusDestination.BODY)
            if (focusTransactionId != transactionId) return@launch
            listState.scrollToItem(bodyLazyItemIndex)
            if (focusTransactionId != transactionId) return@launch
            awaitFocusReady(TvFocusDestination.BODY)
            if (focusTransactionId != transactionId) return@launch
            bodyFocusRequester.requestFocus()
        }
    }

    fun focusSection(
        sectionId: String,
        preferredItemId: String? = null,
        cancelPersistedRestoration: Boolean = true,
    ) {
        ownDestination(TvFocusDestination.SECTION_ITEM, cancelPersistedRestoration)
        val section = uiState.section(sectionId) ?: return focusBody(cancelPersistedRestoration)
        val itemId =
            preferredItemId?.takeIf(section.itemIds::contains)
                // Entering the season row lands on the season being shown, not on the first one.
                ?: (section as? TvDetailSection.Seasons)?.let { it.itemIds.getOrNull(it.selectedIndex) }
                // and the season siblings of an episode land on that episode.
                ?: (section as? TvDetailSection.Episodes)?.currentEpisodeId?.takeIf(section.itemIds::contains)
                ?: section.itemIds.firstOrNull()
                ?: return focusBody(cancelPersistedRestoration)
        val lazyItemIndex =
            uiState.sections
                .indexOfFirst { it.id == sectionId }
                .takeIf { it >= 0 }
                ?.plus(1) ?: return focusBody(cancelPersistedRestoration)
        val focusRequester = requester(sectionId, itemId)
        val horizontalListState = horizontalListStates.getValue(sectionId)
        val horizontalItemIndex = section.itemIds.indexOf(itemId)
        val transactionId = focusTransactionId
        scope.launch {
            awaitFocusStart(TvFocusDestination.SECTION_ITEM)
            if (focusTransactionId != transactionId) return@launch
            listState.scrollToItem(lazyItemIndex)
            if (focusTransactionId != transactionId) return@launch
            horizontalListState.scrollToItem(horizontalItemIndex)
            if (focusTransactionId != transactionId) return@launch
            awaitFocusReady(TvFocusDestination.SECTION_ITEM)
            if (focusTransactionId != transactionId) return@launch
            val focused = runCatching { focusRequester.requestFocus() }.getOrDefault(false)
            if (!focused && focusTransactionId == transactionId) focusBody(cancelPersistedRestoration)
        }
    }

    /** Down from the hero enters the first row of the page; a page without rows goes to the Details at its end. */
    fun focusAfterHero(cancelPersistedRestoration: Boolean = true) {
        val first = focusSections.firstOrNull()
        if (first != null) {
            focusSection(first.id, cancelPersistedRestoration = cancelPersistedRestoration)
        } else {
            focusBody(cancelPersistedRestoration)
        }
    }

    fun recoveryTarget(source: TvFocusAnchor): TvFocusAnchor {
        val currentState = currentUiState
        if (currentState.resolve(source) != null) return source
        val currentFocusSections = currentState.sections.filter { it.participatesInSectionFocus() && it.itemIds.isNotEmpty() }
        val sameSection = currentState.section(source.sectionId.orEmpty())?.takeIf { it.participatesInSectionFocus() }
        val fallbackSection =
            sameSection ?: currentFocusSections.getOrNull(activeSectionIndex.coerceIn(0, currentFocusSections.lastIndex.coerceAtLeast(0)))
        val fallbackItem =
            fallbackSection?.itemIds?.getOrNull(activeItemIndex.coerceIn(0, fallbackSection.itemIds.lastIndex.coerceAtLeast(0)))
        return if (fallbackSection != null && fallbackItem != null) {
            TvFocusAnchor(fallbackSection.id, fallbackItem, TvFocusDestination.SECTION_ITEM)
        } else {
            TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY)
        }
    }

    val recoverySource =
        lastConfirmedItemAnchor?.takeIf { source ->
            focusOwnership == TvFocusDestination.SECTION_ITEM && uiState.resolve(source) == null
        }

    LaunchedEffect(routeKey) {
        if (pendingPersistedAnchor == null) {
            if (hasPrimaryAction) {
                focusPrimaryAction(cancelPersistedRestoration = false)
            } else {
                focusAfterHero(cancelPersistedRestoration = false)
            }
        } else if (
            pendingPersistedAnchor?.destination == TvFocusDestination.SECTION_ITEM &&
            uiState.section(pendingPersistedAnchor?.sectionId.orEmpty()) == null
        ) {
            if (hasPrimaryAction) {
                focusPrimaryAction(cancelPersistedRestoration = false)
            } else {
                focusAfterHero(cancelPersistedRestoration = false)
            }
        }
    }
    LaunchedEffect(uiState.sections, pendingPersistedAnchor) {
        val pending = pendingPersistedAnchor ?: return@LaunchedEffect
        if (
            pending.destination == TvFocusDestination.SECTION_ITEM &&
            uiState.section(pending.sectionId.orEmpty()) == null
        ) {
            return@LaunchedEffect
        }
        val target = focusMemory?.resolve(persistedRouteKey, restorationTargets) ?: return@LaunchedEffect
        restoringPersistedAnchor = target.anchor
        when (target.anchor.destination) {
            TvFocusDestination.HERO ->
                if (hasPrimaryAction) {
                    focusPrimaryAction(cancelPersistedRestoration = false)
                } else {
                    focusAfterHero(cancelPersistedRestoration = false)
                }
            TvFocusDestination.PRIMARY_ACTION -> focusPrimaryAction(cancelPersistedRestoration = false)
            TvFocusDestination.BODY -> focusBody(cancelPersistedRestoration = false)
            TvFocusDestination.SECTION_ITEM ->
                focusSection(
                    sectionId = target.anchor.sectionId.orEmpty(),
                    preferredItemId = target.anchor.itemId,
                    cancelPersistedRestoration = false,
                )
        }
    }
    LaunchedEffect(uiState.sections, lastConfirmedItemAnchor) {
        val anchor = lastConfirmedItemAnchor ?: return@LaunchedEffect
        val resolved = uiState.resolve(anchor) ?: return@LaunchedEffect
        activeSectionIndex = focusSections.indexOfFirst { it.id == anchor.sectionId }.coerceAtLeast(0)
        activeItemIndex = resolved.itemIndex
    }
    LaunchedEffect(uiState.sections, recoverySource) {
        val source = recoverySource ?: return@LaunchedEffect
        focusTransactionId += 1
        pendingFocusRecovery = TvPendingFocusRecovery(focusTransactionId, source, recoveryTarget(source))
    }
    LaunchedEffect(pendingFocusRecovery, uiState.sections) {
        val pending = pendingFocusRecovery ?: return@LaunchedEffect

        fun isCurrent(): Boolean =
            pendingFocusRecovery?.transactionId == pending.transactionId &&
                focusOwnership == TvFocusDestination.SECTION_ITEM &&
                lastConfirmedItemAnchor == pending.source

        val currentTarget =
            pending.target.takeIf { it.destination == TvFocusDestination.BODY }
                ?: recoveryTarget(pending.source)
        if (currentTarget != pending.target) {
            if (isCurrent()) pendingFocusRecovery = pending.copy(target = currentTarget)
            return@LaunchedEffect
        }
        awaitFocusStart(currentTarget.destination)
        if (!isCurrent()) return@LaunchedEffect
        if (currentTarget.destination == TvFocusDestination.SECTION_ITEM) {
            val sectionId = currentTarget.sectionId ?: return@LaunchedEffect
            val itemId = currentTarget.itemId ?: return@LaunchedEffect
            val lazyItemIndex =
                uiState.sections
                    .indexOfFirst { it.id == sectionId }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            if (lazyItemIndex == null) {
                if (isCurrent()) {
                    pendingFocusRecovery = pending.copy(target = TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY))
                }
                return@LaunchedEffect
            }
            val section = uiState.section(sectionId) ?: return@LaunchedEffect
            val horizontalItemIndex = section.itemIds.indexOf(itemId).takeIf { it >= 0 }
            val horizontalListState = horizontalListStates[sectionId]
            if (horizontalItemIndex == null || horizontalListState == null) {
                if (isCurrent()) {
                    pendingFocusRecovery = pending.copy(target = TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY))
                }
                return@LaunchedEffect
            }
            listState.scrollToItem(lazyItemIndex)
            if (!isCurrent()) return@LaunchedEffect
            horizontalListState.scrollToItem(horizontalItemIndex)
            if (!isCurrent()) return@LaunchedEffect
            withFrameNanos { }
            if (!isCurrent()) return@LaunchedEffect
            val focused = runCatching { requester(sectionId, itemId).requestFocus() }.getOrDefault(false)
            if (!focused && isCurrent()) {
                pendingFocusRecovery = pending.copy(target = TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY))
            }
        } else {
            destinationIntent = TvFocusDestination.BODY
            listState.scrollToItem(bodyLazyItemIndex)
            if (!isCurrent()) return@LaunchedEffect
            withFrameNanos { }
            if (!isCurrent()) return@LaunchedEffect
            val focused = runCatching { bodyFocusRequester.requestFocus() }.getOrDefault(false)
            if (isCurrent()) {
                if (!focused) focusOwnership = null
                destinationIntent = null
                pendingFocusRecovery = null
            }
        }
    }

    val primaryActionModifier =
        Modifier
            .focusRequester(primaryActionFocusRequester)
            .testTag("tv-detail-primary-action")
            .onFocusChanged { focusState ->
                if (focusState.isFocused && destinationIntent == TvFocusDestination.PRIMARY_ACTION) {
                    focusOwnership = TvFocusDestination.PRIMARY_ACTION
                    destinationIntent = null
                }
                if (focusState.isFocused) {
                    rememberFocus(TvFocusAnchor("actions", "primary", TvFocusDestination.PRIMARY_ACTION))
                }
            }.onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    true
                } else {
                    false
                }
            }

    val actionRowModifier =
        Modifier.onPreviewKeyEvent { event ->
            if (
                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
                focusAfterHero()
                true
            } else {
                false
            }
        }
    val bodyFocusModifier =
        Modifier
            .focusRequester(bodyFocusRequester)
            .testTag("tv-detail-body-focus")
            .onPreviewKeyEvent { event ->
                val isInitialDownPress =
                    event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.repeatCount == 0
                if (isInitialDownPress && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    // The Details close the page: up goes back to the last row, or to the actions without rows.
                    val last = focusSections.lastOrNull()
                    when {
                        last != null -> focusSection(last.id)
                        hasPrimaryAction -> focusPrimaryAction()
                    }
                    last != null || hasPrimaryAction
                } else {
                    false
                }
            }.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    rememberFocus(TvFocusAnchor(TV_DETAIL_BODY_SECTION_ID, null, TvFocusDestination.BODY))
                    val pending = pendingFocusRecovery
                    if (pending?.target?.destination == TvFocusDestination.BODY) {
                        focusOwnership = TvFocusDestination.BODY
                        destinationIntent = null
                        pendingFocusRecovery = null
                    } else if (destinationIntent == TvFocusDestination.BODY) {
                        focusOwnership = TvFocusDestination.BODY
                        destinationIntent = null
                    }
                }
            }.focusable()

    fun missingConfirmedSectionAnchor(): TvFocusAnchor? =
        lastConfirmedItemAnchor?.takeIf { anchor ->
            focusOwnership == TvFocusDestination.SECTION_ITEM &&
                destinationIntent == null &&
                currentUiState.resolve(anchor) == null
        }

    fun confirmSectionItemFocus(itemAnchor: TvFocusAnchor) {
        val currentState = currentUiState
        val resolved = currentState.resolve(itemAnchor) ?: return
        val currentFocusSections = currentState.sections.filter { it.participatesInSectionFocus() && it.itemIds.isNotEmpty() }
        focusTransactionId += 1
        pendingFocusRecovery = null
        activeSectionIndex = currentFocusSections.indexOfFirst { it.id == itemAnchor.sectionId }.coerceAtLeast(0)
        activeItemIndex = resolved.itemIndex
        lastConfirmedItemAnchor = itemAnchor
        rememberFocus(itemAnchor, activeItemIndex)
        focusOwnership = TvFocusDestination.SECTION_ITEM
        destinationIntent = null
    }

    val sectionFocusModifiers =
        focusSections
            .mapIndexed { sectionIndex, section ->
                val focusRequesters = section.itemIds.associateWith { itemId -> requester(section.id, itemId) }
                val itemModifiers =
                    section.itemIds.associateWith { itemId ->
                        Modifier
                            .testTag("tv-detail-section-${section.id}-item-$itemId")
                            .onFocusChanged { focusState ->
                                val itemAnchor = TvFocusAnchor(section.id, itemId, TvFocusDestination.SECTION_ITEM)
                                val missingAnchor = missingConfirmedSectionAnchor()
                                if (focusState.isFocused &&
                                    (missingAnchor == null || itemAnchor == recoveryTarget(missingAnchor))
                                ) {
                                    // Default focus after removal must not replace the recovery anchor.
                                    confirmSectionItemFocus(itemAnchor)
                                }
                            }.onPreviewKeyEvent { event ->
                                val isNavigationOrActivation =
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP,
                                        KeyEvent.KEYCODE_DPAD_DOWN,
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER,
                                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                                        KeyEvent.KEYCODE_SPACE,
                                        KeyEvent.KEYCODE_BUTTON_A,
                                        KeyEvent.KEYCODE_BACK,
                                        -> true
                                        else -> false
                                    }
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    isNavigationOrActivation &&
                                    missingConfirmedSectionAnchor() != null
                                ) {
                                    // Real input owns even a temporary default target before clickable handles it.
                                    ownDestination(TvFocusDestination.SECTION_ITEM)
                                    confirmSectionItemFocus(TvFocusAnchor(section.id, itemId, TvFocusDestination.SECTION_ITEM))
                                }
                                false
                            }
                    }
                val navigationModifier =
                    Modifier
                        .onPreviewKeyEvent { event ->
                            val isInitialPress =
                                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    event.nativeKeyEvent.repeatCount == 0
                            if (!isInitialPress) {
                                false
                            } else {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        when {
                                            sectionIndex > 0 -> focusSection(focusSections[sectionIndex - 1].id)
                                            hasPrimaryAction -> focusPrimaryAction()
                                            else -> return@onPreviewKeyEvent false
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (sectionIndex < focusSections.lastIndex) {
                                            focusSection(focusSections[sectionIndex + 1].id)
                                        } else {
                                            focusBody()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                section.id to
                    TvDetailSectionFocusModifiers(
                        navigationModifier = navigationModifier,
                        itemModifiers = itemModifiers,
                        itemFocusRequesters = focusRequesters,
                        horizontalListState = horizontalListStates.getValue(section.id),
                    )
            }.toMap()

    SideEffect {
        val retainedRequesterKeys =
            buildSet {
                focusSections.forEach { section ->
                    section.itemIds.forEach { itemId -> add(requesterKey(section.id, itemId)) }
                }
                listOfNotNull(pendingFocusRecovery?.source, pendingFocusRecovery?.target)
                    .filter { it.destination == TvFocusDestination.SECTION_ITEM }
                    .forEach { anchor -> add(requesterKey(anchor.sectionId.orEmpty(), anchor.itemId.orEmpty())) }
            }
        itemFocusRequesters.keys.retainAll(retainedRequesterKeys)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item("hero-$routeKey") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeightDp.dp)
                    .testTag("tv-detail-hero")
                    .semantics { contentDescription = heroContentDescription },
            ) {
                heroContent(primaryActionModifier, actionRowModifier)
            }
        }
        content(bodyFocusModifier, sectionFocusModifiers)
    }
}
