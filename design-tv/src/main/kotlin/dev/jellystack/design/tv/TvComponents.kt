@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength")

package dev.jellystack.design.tv

import android.view.KeyEvent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

@Composable
internal fun Modifier.tvFocusable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    scale: Float = 1.045f,
    onFocused: (() -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
): Modifier =
    this.then(
        Modifier
            .tvFocusDecoration(shape, scale, onFocused)
            .tvReturnToNavigationRailOnLeft(focusToNavigationRailOnLeft)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }.clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
    )

@Composable
private fun Modifier.tvFocusDecoration(
    shape: RoundedCornerShape,
    scale: Float,
    onFocused: (() -> Unit)?,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(if (focused) scale else 1f, label = "tv-focus-scale")
    val borderColor by animateColorAsState(if (focused) TvPurple else Color.Transparent, label = "tv-focus-color")
    return this
        .onFocusChanged {
            val becameFocused = it.isFocused && !focused
            focused = it.isFocused
            if (becameFocused) onFocused?.invoke()
        }.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }.drawBehind {
            if (focused) {
                drawRoundRect(
                    color = TvPurpleStrong.copy(alpha = 0.38f),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(22.dp.toPx()),
                )
            }
        }.border(if (focused) 3.dp else 0.dp, borderColor, shape)
        .clip(shape)
}

@Composable
internal fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusToNavigationRailOnLeft: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            modifier
                .height(58.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .background(if (primary) TvPurple else TvSurfaceRaised, shape)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    selected = primary
                }
                .tvFocusable(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape,
                    focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                )
                .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color(0xFF251450) else TvText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TvMediaCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    landscape: Boolean = true,
    fillWidth: Boolean = false,
    onFocused: (() -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(if (landscape) 276.dp else 166.dp))
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
                }
                .tvFocusable(
                    onClick = onClick,
                    shape = shape,
                    onFocused = onFocused,
                    focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                )
                .background(TvSurface, shape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                    .clip(shape),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(TvSurfaceRaised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = TvTextMuted)
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    color = TvText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                subtitle?.let { Text(it, color = TvTextMuted, fontSize = 14.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun TvLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = TvPurple)
        Spacer(Modifier.height(16.dp))
        Text(label, color = TvTextMuted)
    }
}

@Composable
internal fun TvSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier.padding(horizontal = 6.dp),
        color = TvText,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

internal val TvScreenPadding = PaddingValues(start = 42.dp, end = 42.dp, top = 32.dp, bottom = 54.dp)

@Composable
internal fun tvOutlinedTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = TvText,
        unfocusedTextColor = TvText,
        disabledTextColor = TvTextMuted,
        cursorColor = TvPurple,
        focusedBorderColor = TvPurple,
        unfocusedBorderColor = TvTextMuted,
        focusedLabelColor = TvPurple,
        unfocusedLabelColor = TvTextMuted,
        focusedPlaceholderColor = TvTextMuted,
        unfocusedPlaceholderColor = TvTextMuted,
    )

internal val LocalTvNavigationRailFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }
internal val LocalTvScreenEntryFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun Modifier.tvScreenEntryFocus(enabled: Boolean = true): Modifier {
    val requester = LocalTvScreenEntryFocusRequester.current
    return if (enabled && requester != null) focusRequester(requester) else this
}

@Composable
internal fun Modifier.tvReturnToNavigationRailOnLeft(enabled: Boolean = true): Modifier {
    val requester = LocalTvNavigationRailFocusRequester.current
    return if (!enabled || requester == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            if (
                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                requester.requestFocus()
                true
            } else {
                false
            }
        }
    }
}

internal fun jellyfinImageUrl(
    baseUrl: String?,
    token: String?,
    itemId: String,
    tag: String?,
    type: String = "Primary",
    maxWidth: Int = 1000,
): String? {
    if (baseUrl.isNullOrBlank() || itemId.isBlank()) return null
    val tagQuery = tag?.takeIf(String::isNotBlank)?.let { "tag=$it&" }.orEmpty()
    return "${baseUrl.trimEnd('/')}/Items/$itemId/Images/$type?${tagQuery}maxWidth=$maxWidth&quality=90" +
        token?.takeIf { it.isNotBlank() }?.let { "&api_key=$it" }.orEmpty()
}

internal fun tmdbImageUrl(
    path: String?,
    backdrop: Boolean = false,
): String? = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/${if (backdrop) "w1280" else "w500"}$it" }
