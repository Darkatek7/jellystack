@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength", "TooManyFunctions")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.players.AndroidPlayerEngine
import kotlinx.coroutines.delay

internal const val TV_DETAIL_PRIMARY_ACTION_WIDTH_DP = 230
internal const val TV_DETAIL_COMPACT_ACTION_WIDTH_DP = 132
internal const val TV_DETAIL_ACTION_GAP_DP = 14
internal const val TV_DETAIL_COMPACT_ACTION_HEIGHT_DP = 72

internal fun tvDetailActionRowRequiredWidthDp(): Int =
    TV_DETAIL_PRIMARY_ACTION_WIDTH_DP + (TV_DETAIL_COMPACT_ACTION_WIDTH_DP * 3) + (TV_DETAIL_ACTION_GAP_DP * 3)

internal fun tvCompactActionRequiredHeightDp(fontScale: Float): Float = 25f + 3f + (13f * fontScale) + 16f

internal fun tvCompactActionRequiredWidthDp(
    characterCount: Int,
    fontScale: Float,
): Float = (characterCount * 6.5f * fontScale) + 16f

@Composable
internal fun Modifier.tvFocusable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    scale: Float = 1.045f,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
): Modifier {
    val restorationRequester = remember { FocusRequester() }
    val registerContentFocus = LocalTvContentFocusRegistrar.current
    return this.then(
        Modifier
            .focusRequester(restorationRequester)
            .tvFocusDecoration(
                shape,
                scale,
                onFocused,
                onFocusChanged = { focused ->
                    if (focused) registerContentFocus?.invoke(restorationRequester)
                    onFocusChanged?.invoke(focused)
                },
            ).tvReturnToNavigationRailOnLeft(focusToNavigationRailOnLeft)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }.clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
    )
}

@Composable
private fun Modifier.tvFocusDecoration(
    shape: RoundedCornerShape,
    scale: Float,
    onFocused: (() -> Unit)?,
    onFocusChanged: ((Boolean) -> Unit)?,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(if (focused) scale else 1f, label = "tv-focus-scale")
    val borderColor by animateColorAsState(if (focused) TvPurple else Color.Transparent, label = "tv-focus-color")
    return this
        .onFocusChanged {
            val becameFocused = it.isFocused && !focused
            focused = it.isFocused
            onFocusChanged?.invoke(it.isFocused)
            if (becameFocused) onFocused?.invoke()
        }.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }.drawBehind {
            if (focused) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.24f),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(22.dp.toPx()),
                )
            }
        }.border(if (focused) 1.5.dp else 0.dp, borderColor.copy(alpha = 0.9f), shape)
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
                }.tvFocusable(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape,
                    focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                ).padding(horizontal = 24.dp),
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
internal fun TvPlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 60.dp,
    iconSize: androidx.compose.ui.unit.Dp = 30.dp,
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    scaleX = if (focused) 1.08f else 1f
                    scaleY = if (focused) 1.08f else 1f
                }.background(Color.Black.copy(alpha = 0.68f), shape)
                .border(if (focused) 3.dp else 1.dp, if (focused) TvPurple else Color.White.copy(alpha = 0.18f), shape)
                .clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    role = Role.Button
                    contentDescription = description
                }.clickable(onClick = onClick)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun TvCompactActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            modifier
                .width(TV_DETAIL_COMPACT_ACTION_WIDTH_DP.dp)
                .height(TV_DETAIL_COMPACT_ACTION_HEIGHT_DP.dp)
                .background(if (selected) TvPurpleStrong.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.52f), shape)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .tvFocusable(onClick = onClick, shape = shape, scale = 1.06f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = if (selected) TvPurple else Color.White, modifier = Modifier.size(25.dp))
        Text(label, color = TvText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
    previewing: Boolean = false,
    previewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: Float = 0f,
) {
    val shape = RoundedCornerShape(18.dp)
    var focused by remember { mutableStateOf(false) }
    val targetWidth by
        animateDpAsState(
            targetValue =
                when {
                    fillWidth -> 250.dp
                    landscape && focused -> 266.dp
                    landscape -> 250.dp
                    focused -> 300.dp
                    else -> 140.dp
                },
            animationSpec = tween(240),
            label = "tv-card-width",
        )
    val aspectRatio = if (landscape || focused) 16f / 9f else 2f / 3f
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            delay(250L)
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(
        modifier =
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(targetWidth))
                .bringIntoViewRequester(bringIntoViewRequester)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
                }.tvFocusable(
                    onClick = onClick,
                    shape = shape,
                    onFocused = onFocused,
                    onFocusChanged = {
                        focused = it
                        onFocusChanged?.invoke(it)
                    },
                    focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                ).background(TvSurface, shape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(shape),
        ) {
            TvMediaCardContent(
                title = title,
                imageUrl = imageUrl,
                subtitle = subtitle,
                previewing = previewing,
                previewEngine = previewEngine,
                previewSoundEnabled = previewSoundEnabled,
                previewProgress = previewProgress,
            )
        }
    }
}

@Composable
private fun BoxScope.TvMediaCardContent(
    title: String,
    imageUrl: String?,
    subtitle: String?,
    previewing: Boolean,
    previewEngine: AndroidPlayerEngine?,
    previewSoundEnabled: Boolean,
    previewProgress: Float,
) {
    if (previewing && previewEngine != null) {
        TvTrailerPreviewSurface(
            previewEngine = previewEngine,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (imageUrl != null) {
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
    if (previewing) {
        TvTrailerPreviewChrome(
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress,
            modifier = Modifier.fillMaxSize(),
        )
    }
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

@Composable
internal fun TvTrailerPreviewSurface(
    previewEngine: AndroidPlayerEngine,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { previewEngine.createVideoSurface(it) },
        update = previewEngine::updateVideoSurface,
        onRelease = previewEngine::releaseVideoSurface,
        modifier = modifier,
    )
}

@Composable
internal fun TvTrailerPreviewChrome(
    previewSoundEnabled: Boolean,
    previewProgress: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(TvPurpleStrong.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Trailer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (previewSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        if (previewProgress > 0f) {
            LinearProgressIndicator(
                progress = { previewProgress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = TvPurple,
                trackColor = Color.White.copy(alpha = 0.22f),
            )
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
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
}

internal val TvScreenPadding = PaddingValues(start = 92.dp, end = 36.dp, top = 20.dp, bottom = 54.dp)

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

internal val LocalTvNavigationRailOpener = staticCompositionLocalOf<(() -> Unit)?> { null }
internal val LocalTvScreenEntryFocusRequester = staticCompositionLocalOf<FocusRequester?> { null }
internal val LocalTvContentFocusRegistrar = staticCompositionLocalOf<((FocusRequester) -> Unit)?> { null }

@Composable
internal fun Modifier.tvScreenEntryFocus(enabled: Boolean = true): Modifier {
    val requester = LocalTvScreenEntryFocusRequester.current
    return if (enabled && requester != null) focusRequester(requester) else this
}

@Composable
internal fun Modifier.tvReturnToNavigationRailOnLeft(enabled: Boolean = true): Modifier {
    val openNavigationRail = LocalTvNavigationRailOpener.current
    return if (!enabled || openNavigationRail == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            if (
                event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                openNavigationRail()
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
