package dev.jellystack.design.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jellystack.core.jellyfin.HomeSection
import dev.jellystack.core.jellyfin.HomeSectionAction
import dev.jellystack.core.jellyfin.HomeSectionItem
import dev.jellystack.core.jellyfin.HomeSectionViewMode
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.design.layout.LocalResponsiveProfile
import kotlinx.datetime.Clock

internal enum class HomeSectionJellyfinTarget { Detail, Library }

internal fun JellyfinItem.homeSectionJellyfinTarget(): HomeSectionJellyfinTarget =
    if (type.equals("CollectionFolder", ignoreCase = true)) {
        HomeSectionJellyfinTarget.Library
    } else {
        HomeSectionJellyfinTarget.Detail
    }

@Composable
internal fun HomeSectionsScreen(
    state: HomeSectionsState.Ready,
    browseState: JellyfinHomeState,
    selectedSpotlightId: String?,
    onSelectedSpotlightIdChange: (String?) -> Unit,
    onOpenSpotlightItem: (JellyfinItem) -> Unit,
    onPlaySpotlightItem: (JellyfinItem) -> Unit,
    spotlightAutoAdvanceEnabled: Boolean,
    spotlightAutoAdvanceIntervalMillis: Long,
    contentPadding: PaddingValues,
    onOpenJellyfinItem: (JellyfinItem) -> Unit,
    onOpenJellyfinLibrary: (JellyfinItem) -> Unit,
    onOpenSeerrItem: (HomeSectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spotlightEligibilityNow = remember { Clock.System.now() }
    val spotlightCandidates =
        remember(
            browseState.recentShows,
            browseState.recentMovies,
            browseState.libraryItems,
            spotlightEligibilityNow,
        ) {
            buildSpotlightCandidates(
                recentShows = browseState.recentShows,
                recentMovies = browseState.recentMovies,
                now = spotlightEligibilityNow,
                libraryItems = browseState.libraryItems,
            )
        }
    val spotlightCandidateIds = remember(spotlightCandidates) { spotlightCandidates.map { it.displayItem.id } }
    LaunchedEffect(spotlightCandidateIds.isEmpty(), selectedSpotlightId) {
        if (spotlightCandidateIds.isEmpty() && selectedSpotlightId != null) {
            onSelectedSpotlightIdChange(null)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (spotlightCandidates.isNotEmpty()) {
            item(key = "spotlight") {
                key(spotlightCandidateIds) {
                    HomeSpotlight(
                        candidates = spotlightCandidates,
                        selectedId = selectedSpotlightId,
                        onSelected = onSelectedSpotlightIdChange,
                        autoAdvanceEnabled = spotlightAutoAdvanceEnabled,
                        autoAdvanceIntervalMillis = spotlightAutoAdvanceIntervalMillis,
                    ) { candidate, _, _ ->
                        HomeSpotlightCard(
                            item = candidate.displayItem,
                            actionItem = candidate.actionItem,
                            baseUrl = browseState.imageBaseUrl,
                            accessToken = browseState.imageAccessToken,
                            onOpenItem = onOpenSpotlightItem,
                            onPlayItem = onPlaySpotlightItem,
                        )
                    }
                }
            }
        }
        items(state.sections, key = HomeSection::id) { section ->
            HomeSectionRail(
                section = section,
                imageBaseUrl = state.imageBaseUrl,
                imageAccessToken = state.imageAccessToken,
                onOpenJellyfinItem = onOpenJellyfinItem,
                onOpenJellyfinLibrary = onOpenJellyfinLibrary,
                onOpenSeerrItem = onOpenSeerrItem,
            )
        }
    }
}

@Composable
private fun HomeSectionRail(
    section: HomeSection,
    imageBaseUrl: String,
    imageAccessToken: String,
    onOpenJellyfinItem: (JellyfinItem) -> Unit,
    onOpenJellyfinLibrary: (JellyfinItem) -> Unit,
    onOpenSeerrItem: (HomeSectionItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (section.displayTitle) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.items, key = HomeSectionItem::id) { item ->
                HomeSectionCard(
                    item = item,
                    viewMode = section.viewMode,
                    showDetailsMenu = section.showDetailsMenu,
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                    onClick = {
                        when (item.action) {
                            HomeSectionAction.JELLYFIN ->
                                item.jellyfinItem?.let { jellyfinItem ->
                                    when (jellyfinItem.homeSectionJellyfinTarget()) {
                                        HomeSectionJellyfinTarget.Detail -> onOpenJellyfinItem(jellyfinItem)
                                        HomeSectionJellyfinTarget.Library -> onOpenJellyfinLibrary(jellyfinItem)
                                    }
                                }
                            HomeSectionAction.SEERR -> onOpenSeerrItem(item)
                            HomeSectionAction.INFORMATION -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionCard(
    item: HomeSectionItem,
    viewMode: HomeSectionViewMode,
    showDetailsMenu: Boolean,
    imageBaseUrl: String,
    imageAccessToken: String,
    onClick: () -> Unit,
) {
    val artwork = item.jellyfinItem?.selectHomeSectionArtwork(viewMode)
    val layout = homeSectionCardLayout(viewMode, LocalResponsiveProfile.current.isCompact)
    Card(
        modifier = Modifier.width(layout.widthDp.dp),
        onClick = onClick,
        enabled = item.action != HomeSectionAction.INFORMATION,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (layout.usesOverlay) {
            HomeSectionOverlayCard(
                item = item,
                viewMode = viewMode,
                artwork = artwork,
                imageBaseUrl = imageBaseUrl,
                imageAccessToken = imageAccessToken,
                aspectRatio = layout.aspectRatio,
                showDetailsMenu = showDetailsMenu,
            )
        } else {
            HomeSectionPortraitCard(
                item = item,
                artwork = artwork,
                imageBaseUrl = imageBaseUrl,
                imageAccessToken = imageAccessToken,
                aspectRatio = layout.aspectRatio,
                metadataHeightDp = layout.metadataHeightDp,
            )
        }
    }
}

@Composable
private fun HomeSectionOverlayCard(
    item: HomeSectionItem,
    viewMode: HomeSectionViewMode,
    artwork: SpotlightArtwork?,
    imageBaseUrl: String,
    imageAccessToken: String,
    aspectRatio: Float,
    showDetailsMenu: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)) {
        HomeSectionArtwork(
            item = item,
            artwork = artwork,
            imageBaseUrl = imageBaseUrl,
            imageAccessToken = imageAccessToken,
            contentScale = ContentScale.Crop,
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
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            ),
                        ),
                    ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = if (showDetailsMenu) 44.dp else 12.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style =
                    if (viewMode == HomeSectionViewMode.SMALL) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata = item.homeSectionMetadata()
            if (viewMode == HomeSectionViewMode.LANDSCAPE && metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDetailsMenu) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(24.dp),
            )
        }
    }
}

@Composable
private fun HomeSectionPortraitCard(
    item: HomeSectionItem,
    artwork: SpotlightArtwork?,
    imageBaseUrl: String,
    imageAccessToken: String,
    aspectRatio: Float,
    metadataHeightDp: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        ) {
            HomeSectionArtwork(
                item = item,
                artwork = artwork,
                imageBaseUrl = imageBaseUrl,
                imageAccessToken = imageAccessToken,
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(metadataHeightDp.dp)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.homeSectionMetadata().ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSectionArtwork(
    item: HomeSectionItem,
    artwork: SpotlightArtwork?,
    imageBaseUrl: String,
    imageAccessToken: String,
    contentScale: ContentScale,
) {
    val externalImageUrl = item.imageUrl?.takeIf(String::isNotBlank)
    when {
        externalImageUrl != null ->
            AsyncImage(
                model = externalImageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        artwork != null ->
            artwork.toHomeSectionArtworkSlots().let { slots ->
                PosterImage(
                    modifier = Modifier.fillMaxSize(),
                    baseUrl = imageBaseUrl,
                    itemId = artwork.itemId,
                    primaryTag = slots.primaryTag,
                    thumbTag = slots.thumbTag,
                    backdropTag = slots.backdropTag,
                    accessToken = imageAccessToken,
                    contentDescription = item.name,
                    primaryImageItemId = slots.primaryItemId,
                    thumbImageItemId = slots.thumbItemId,
                    backdropImageItemId = slots.backdropItemId,
                    contentScale = contentScale,
                )
            }
        else ->
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        item.name
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            .orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

internal data class HomeSectionArtworkSlots(
    val primaryTag: String? = null,
    val thumbTag: String? = null,
    val backdropTag: String? = null,
    val primaryItemId: String? = null,
    val thumbItemId: String? = null,
    val backdropItemId: String? = null,
)

internal fun SpotlightArtwork.toHomeSectionArtworkSlots(): HomeSectionArtworkSlots =
    when (imageType.lowercase()) {
        "thumb" -> HomeSectionArtworkSlots(thumbTag = tag, thumbItemId = itemId)
        "backdrop" -> HomeSectionArtworkSlots(backdropTag = tag, backdropItemId = itemId)
        else -> HomeSectionArtworkSlots(primaryTag = tag, primaryItemId = itemId)
    }

internal data class HomeSectionCardLayout(
    val widthDp: Int,
    val aspectRatio: Float,
    val metadataHeightDp: Int,
    val usesOverlay: Boolean,
) {
    val totalHeightDp: Float
        get() = widthDp / aspectRatio + metadataHeightDp
}

internal fun homeSectionCardLayout(
    viewMode: HomeSectionViewMode,
    compact: Boolean,
): HomeSectionCardLayout =
    when (viewMode) {
        HomeSectionViewMode.PORTRAIT -> HomeSectionCardLayout(if (compact) 142 else 154, 2f / 3f, 78, false)
        HomeSectionViewMode.LANDSCAPE -> HomeSectionCardLayout(if (compact) 260 else 300, 16f / 9f, 0, true)
        HomeSectionViewMode.SQUARE -> HomeSectionCardLayout(if (compact) 164 else 180, 1f, 0, true)
        HomeSectionViewMode.SMALL -> HomeSectionCardLayout(if (compact) 118 else 132, 1f, 0, true)
    }

private fun HomeSectionItem.homeSectionMetadata(): String =
    listOfNotNull(
        productionYear?.toString(),
        communityRating?.takeIf { it > 0 }?.let { "\u2605 ${formatRating(it)}" },
    ).joinToString(" \u2022 ")

internal fun JellyfinItem.selectHomeSectionArtwork(viewMode: HomeSectionViewMode): SpotlightArtwork? {
    val inheritedOwner = seriesId?.takeIf { it.isNotBlank() && it != id }

    fun inherited(
        tag: String?,
        imageType: String,
    ): SpotlightArtwork? =
        tag?.takeIf { inheritedOwner != null }?.let {
            SpotlightArtwork(itemId = requireNotNull(inheritedOwner), tag = it, imageType = imageType)
        }

    fun direct(
        tag: String?,
        imageType: String,
    ): SpotlightArtwork? = tag?.let { SpotlightArtwork(itemId = id, tag = it, imageType = imageType) }

    return if (viewMode == HomeSectionViewMode.LANDSCAPE) {
        selectSpotlightArtwork()
            ?: inherited(seriesPrimaryImageTag, "Primary")
            ?: direct(primaryImageTag, "Primary")
    } else {
        inherited(seriesPrimaryImageTag, "Primary")
            ?: direct(primaryImageTag, "Primary")
            ?: inherited(seriesThumbImageTag, "Thumb")
            ?: direct(thumbImageTag, "Thumb")
            ?: selectSpotlightArtwork()
    }
}

private fun formatRating(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return rounded.toString()
}
