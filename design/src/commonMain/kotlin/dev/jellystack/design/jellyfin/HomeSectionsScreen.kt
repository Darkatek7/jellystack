package dev.jellystack.design.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.jellystack.core.jellyfin.JellyfinItem

@Composable
internal fun HomeSectionsScreen(
    state: HomeSectionsState.Ready,
    contentPadding: PaddingValues,
    onOpenJellyfinItem: (JellyfinItem) -> Unit,
    onOpenSeerrItem: (HomeSectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        items(state.sections, key = HomeSection::id) { section ->
            HomeSectionRail(
                section = section,
                imageBaseUrl = state.imageBaseUrl,
                imageAccessToken = state.imageAccessToken,
                onOpenJellyfinItem = onOpenJellyfinItem,
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
                    imageBaseUrl = imageBaseUrl,
                    imageAccessToken = imageAccessToken,
                    onClick = {
                        when (item.action) {
                            HomeSectionAction.JELLYFIN -> item.jellyfinItem?.let(onOpenJellyfinItem)
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
    imageBaseUrl: String,
    imageAccessToken: String,
    onClick: () -> Unit,
) {
    val localItem = item.jellyfinItem
    val imageType = if (viewMode == HomeSectionViewMode.LANDSCAPE) "Backdrop" else "Primary"
    val tag =
        if (imageType == "Backdrop") {
            localItem?.backdropImageTag ?: localItem?.thumbImageTag ?: localItem?.primaryImageTag
        } else {
            localItem?.primaryImageTag ?: localItem?.thumbImageTag ?: localItem?.backdropImageTag
        }
    val imageUrl =
        item.imageUrl
            ?: localItem?.let { buildImageUrl(imageBaseUrl, it.id, tag, imageType, imageAccessToken) }
    val width =
        when (viewMode) {
            HomeSectionViewMode.PORTRAIT -> 142.dp
            HomeSectionViewMode.LANDSCAPE -> 260.dp
            HomeSectionViewMode.SQUARE -> 164.dp
            HomeSectionViewMode.SMALL -> 118.dp
        }
    val ratio =
        when (viewMode) {
            HomeSectionViewMode.PORTRAIT -> 2f / 3f
            HomeSectionViewMode.LANDSCAPE -> 16f / 9f
            HomeSectionViewMode.SQUARE -> 1f
            HomeSectionViewMode.SMALL -> 1f
        }
    Card(
        modifier =
            Modifier
                .width(width)
                .then(
                    if (item.action != HomeSectionAction.INFORMATION) Modifier.clickable(onClick = onClick) else Modifier,
                ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata =
                listOfNotNull(
                    item.productionYear?.toString(),
                    item.communityRating?.takeIf { it > 0 }?.let { "★ ${formatRating(it)}" },
                ).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatRating(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return rounded.toString()
}
