@file:Suppress("FunctionNaming", "LongParameterList", "MatchingDeclarationName")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/** The kinds of library the fallback tile has an icon for. */
internal enum class TvLibraryTileKind { MOVIES, TV_SHOWS, MUSIC, LIVE_TV, BOOKS, BOX_SETS, OTHER }

internal fun tvLibraryTileKind(collectionType: String?): TvLibraryTileKind =
    when (collectionType?.lowercase()) {
        "movies" -> TvLibraryTileKind.MOVIES
        "tvshows", "series" -> TvLibraryTileKind.TV_SHOWS
        "music", "musicvideos" -> TvLibraryTileKind.MUSIC
        "livetv" -> TvLibraryTileKind.LIVE_TV
        "books" -> TvLibraryTileKind.BOOKS
        "boxsets", "collections" -> TvLibraryTileKind.BOX_SETS
        else -> TvLibraryTileKind.OTHER
    }

private fun TvLibraryTileKind.icon(): ImageVector =
    when (this) {
        TvLibraryTileKind.MOVIES -> Icons.Default.LocalMovies
        TvLibraryTileKind.TV_SHOWS -> Icons.Default.Tv
        TvLibraryTileKind.MUSIC -> Icons.Default.MusicNote
        TvLibraryTileKind.LIVE_TV -> Icons.Default.LiveTv
        TvLibraryTileKind.BOOKS -> Icons.Default.MenuBook
        TvLibraryTileKind.BOX_SETS -> Icons.Default.Collections
        TvLibraryTileKind.OTHER -> Icons.Default.VideoLibrary
    }

/**
 * A library on the landing page and in Home's "My media" row. The server's own Primary image shows as it is, with the
 * name and size below it: that image often already carries the library name, so no text goes on top of it. A
 * library without an image gets a tile of its own with the icon of its kind.
 */
@Composable
internal fun TvLibraryTile(
    name: String,
    collectionType: String?,
    imageUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TV_LIBRARY_TILE_WIDTH_DP.dp,
    onFocused: (() -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
    focusTargetId: String? = null,
    tileModifier: Modifier = Modifier,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val shape = TvShapes.Card
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val shownImage = imageUrl?.takeUnless { imageFailed }
    Column(
        modifier =
            modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                tileModifier
                    .fillMaxWidth()
                    .aspectRatio(TV_LIBRARY_TILE_ASPECT)
                    .tvFocusable(
                        onClick = onClick,
                        shape = shape,
                        onFocused = onFocused,
                        onFocusChanged = onFocusChanged,
                        focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                        focusTargetId = focusTargetId,
                    )
                    // The focusable tile reads the name and count itself, so it is one node with the click action.
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(name, subtitle).joinToString(", ")
                    }.background(TvSurface, shape)
                    .clip(shape),
        ) {
            if (shownImage != null) {
                AsyncImage(
                    model = shownImage,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { imageFailed = true },
                )
            } else {
                TvLibraryTileFallback(tvLibraryTileKind(collectionType))
            }
        }
        Column(Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {}) {
            Text(
                name,
                color = TvText,
                fontSize = TvTextSize.CardTitle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle.orEmpty(),
                color = TvTextMuted,
                fontSize = TvTextSize.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvLibraryTileFallback(kind: TvLibraryTileKind) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF292A3D), Color(0xFF151622), Color(0xFF30234A)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon(), contentDescription = null, tint = TvPurple, modifier = Modifier.size(44.dp))
    }
}

internal const val TV_LIBRARY_TILE_WIDTH_DP = 200
internal const val TV_LIBRARY_TILE_ASPECT = 16f / 9f

/** The library's Primary image at a size that fits the tile; null for a library without one. */
internal fun tvLibraryTileImageUrl(
    baseUrl: String?,
    accessToken: String?,
    library: dev.jellystack.core.jellyfin.JellyfinLibrary,
): String? =
    library.primaryImageTag?.let {
        jellyfinImageUrl(baseUrl, accessToken, library.id, it, maxWidth = TvArtworkSize.LANDSCAPE_CARD.maxWidth)
    }
