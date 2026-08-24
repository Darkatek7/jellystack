@file:Suppress("FunctionName", "FunctionNaming", "LongParameterList", "MaxLineLength", "TooManyFunctions")

package dev.jellystack.design.tv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
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

internal val TvDestructiveActionKey = SemanticsPropertyKey<Boolean>("TvDestructiveAction")
private var SemanticsPropertyReceiver.tvDestructiveAction by TvDestructiveActionKey

internal fun tvDetailActionRowRequiredWidthDp(): Int =
    TV_DETAIL_PRIMARY_ACTION_WIDTH_DP + (TV_DETAIL_COMPACT_ACTION_WIDTH_DP * 3) + (TV_DETAIL_ACTION_GAP_DP * 3)

internal fun tvCompactActionRequiredHeightDp(fontScale: Float): Float = 25f + 3f + (13f * fontScale) + 16f

internal fun tvCompactActionRequiredWidthDp(
    characterCount: Int,
    fontScale: Float,
): Float = (characterCount * 6.5f * fontScale) + 16f

@Composable
internal fun Modifier.tvFocusable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    scale: Float = TvLayoutTokens.FOCUS_SCALE,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
    focusTargetId: String? = null,
    providedFocusRequester: FocusRequester? = null,
    showFocusBorder: Boolean = true,
): Modifier {
    val rememberedFocusRequester = remember { FocusRequester() }
    val restorationRequester = providedFocusRequester ?: rememberedFocusRequester
    val focusContext = LocalTvFocusContext.current
    var horizontalCenter by remember(focusTargetId) { mutableStateOf(0f) }
    val semanticTarget =
        focusTargetId?.let { targetId ->
            tvFocusTarget(
                targetId = targetId,
                horizontalCenter = horizontalCenter,
                actionable = enabled && onClick != null,
            )
        }
    if (focusContext != null && focusTargetId != null) {
        DisposableEffect(focusContext, focusTargetId, restorationRequester, semanticTarget) {
            focusContext.coordinator.register(
                focusContext.routeKey,
                focusTargetId,
                restorationRequester,
                focusTarget = semanticTarget,
            )
            onDispose {
                focusContext.coordinator.unregister(focusContext.routeKey, focusTargetId, restorationRequester)
            }
        }
    }
    val centerActionModifier =
        if (onClick != null) {
            Modifier
                .semantics {
                    role = Role.Button
                    if (!enabled) disabled()
                }.clickable(enabled = enabled, onClick = onClick)
        } else {
            Modifier
        }
    return this.then(
        Modifier
            .focusRequester(restorationRequester)
            .tvFocusDecoration(
                shape,
                scale,
                onFocused,
                onFocusChanged = { focused ->
                    if (focused && focusTargetId != null) {
                        focusContext
                            ?.coordinator
                            ?.rememberFocused(focusContext.routeKey, focusTargetId, restorationRequester)
                            ?.let { target ->
                                focusContext.focusMemory?.remember(
                                    routeKey = focusContext.routeKey,
                                    anchor = target.anchor,
                                    horizontalCenter = target.horizontalCenter,
                                    horizontalIndex = target.horizontalIndex,
                                )
                            }
                    }
                    onFocusChanged?.invoke(focused)
                },
                showFocusBorder = showFocusBorder,
            ).tvReturnToNavigationRailOnLeft(focusToNavigationRailOnLeft)
            .onGloballyPositioned { coordinates ->
                horizontalCenter = coordinates.boundsInRoot().center.x
            }.then(centerActionModifier)
            .focusable(enabled),
    )
}

@Composable
private fun Modifier.tvFocusDecoration(
    shape: RoundedCornerShape,
    scale: Float,
    onFocused: (() -> Unit)?,
    onFocusChanged: ((Boolean) -> Unit)?,
    showFocusBorder: Boolean = true,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(if (focused) scale else 1f, label = "tv-focus-scale")
    return this
        .onFocusChanged {
            val becameFocused = it.isFocused && !focused
            focused = it.isFocused
            if (becameFocused) onFocused?.invoke()
            // The semantic callback runs last so stable production anchors cannot be replaced by
            // legacy callbacks that use localized row titles as section identifiers.
            onFocusChanged?.invoke(it.isFocused)
        }.graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
            shadowElevation = if (focused) 12.dp.toPx() else 0f
            ambientShadowColor = Color.Black
            spotShadowColor = TvPurpleStrong
        }.drawBehind {
            if (focused) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.24f),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(22.dp.toPx()),
                )
            }
        }.drawWithContent {
            drawContent()
            if (focused && showFocusBorder) {
                drawRoundRect(
                    color = TvLayoutTokens.FocusDarkRing,
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 5.dp.toPx()),
                )
                drawRoundRect(
                    color = TvLayoutTokens.FocusLightRing,
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(17.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawRoundRect(
                    color = TvLayoutTokens.FocusAccentRing,
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }.clip(shape)
}

@Composable
internal fun TvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    primary: Boolean = false,
    selected: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    focusToNavigationRailOnLeft: Boolean = false,
    focusTargetId: String? = null,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            modifier
                .height(58.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
                .background(
                    when {
                        destructive -> Color(0xFFB3261E)
                        primary -> TvPurple
                        else -> TvSurfaceRaised
                    },
                    shape,
                ).drawBehind {
                    if (selected) {
                        drawRoundRect(
                            color = TvPurple,
                            topLeft =
                                androidx.compose.ui.geometry
                                    .Offset(3.dp.toPx(), size.height * 0.2f),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(4.dp.toPx(), size.height * 0.6f),
                            cornerRadius =
                                androidx.compose.ui.geometry
                                    .CornerRadius(2.dp.toPx()),
                        )
                    }
                }.semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                    if (destructive) tvDestructiveAction = true
                }.tvFocusable(
                    onClick = onClick,
                    enabled = enabled,
                    shape = shape,
                    focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                    focusTargetId = focusTargetId,
                    providedFocusRequester = focusRequester,
                    onFocusChanged = onFocusChanged,
                ).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.invoke()
        if (leading != null) Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (primary && !destructive) Color(0xFF251450) else TvText,
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
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    this.selected = selected
                }.tvFocusable(onClick = onClick, shape = shape, scale = 1.06f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = if (selected) TvPurple else Color.White, modifier = Modifier.size(25.dp))
        Text(label, color = TvText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal enum class TvMediaCardFormat { LANDSCAPE, CAST_PORTRAIT }

internal enum class TvMediaCardArtworkFit { CROP, CONTAIN_PORTRAIT }

@Composable
internal fun TvMediaCard(
    title: String,
    imageUrl: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    format: TvMediaCardFormat = TvMediaCardFormat.LANDSCAPE,
    artworkFit: TvMediaCardArtworkFit = TvMediaCardArtworkFit.CROP,
    fillWidth: Boolean = false,
    focusable: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusToNavigationRailOnLeft: Boolean = false,
    previewing: Boolean = false,
    previewEngine: AndroidPlayerEngine? = null,
    previewSoundEnabled: Boolean = true,
    previewProgress: State<Float>? = null,
    previewSurfaceTestTag: String? = null,
    focusTargetId: String? = null,
    providedFocusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    var focused by remember { mutableStateOf(false) }
    val cardWidth = if (format == TvMediaCardFormat.LANDSCAPE) TvLayoutTokens.LandscapeArtworkWidth else 140.dp
    val aspectRatio =
        if (format == TvMediaCardFormat.LANDSCAPE) {
            TvLayoutTokens.LandscapeArtworkWidth.value / TvLayoutTokens.LandscapeArtworkHeight.value
        } else {
            2f / 3f
        }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            delay(250L)
            bringIntoViewRequester.bringIntoView()
        }
    }
    val interactionModifier =
        if (focusable) {
            Modifier.tvFocusable(
                onClick = onClick,
                shape = shape,
                onFocused = onFocused,
                onFocusChanged = {
                    focused = it
                    onFocusChanged?.invoke(it)
                },
                focusToNavigationRailOnLeft = focusToNavigationRailOnLeft,
                focusTargetId = focusTargetId,
                providedFocusRequester = providedFocusRequester,
            )
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
                .then(interactionModifier)
                .bringIntoViewRequester(bringIntoViewRequester)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(title, subtitle).joinToString(", ")
                }.background(TvSurface, shape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (format == TvMediaCardFormat.LANDSCAPE) {
                            Modifier.height(TvLayoutTokens.LandscapeArtworkHeight)
                        } else {
                            Modifier.aspectRatio(aspectRatio)
                        },
                    ).clip(shape),
        ) {
            TvMediaCardContent(
                title = title,
                imageUrl = imageUrl,
                subtitle = subtitle,
                artworkFit = artworkFit,
                previewing = previewing,
                previewEngine = previewEngine,
                previewSoundEnabled = previewSoundEnabled,
                previewProgress = previewProgress,
                previewSurfaceTestTag = previewSurfaceTestTag,
                showMetadataOverlay = format != TvMediaCardFormat.LANDSCAPE,
            )
        }
        if (format == TvMediaCardFormat.LANDSCAPE) {
            TvMediaCardMetadataBand(title = title, subtitle = subtitle)
        }
    }
}

@Composable
private fun TvMediaCardMetadataBand(
    title: String,
    subtitle: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TvLayoutTokens.LandscapeMetadataBandHeight)
                .background(Color(0xFF11121B))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            color = TvText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
        subtitle?.let { Text(it, color = TvTextMuted, fontSize = 13.sp, maxLines = 1) }
    }
}

@Composable
private fun BoxScope.TvMediaCardContent(
    title: String,
    imageUrl: String?,
    subtitle: String?,
    artworkFit: TvMediaCardArtworkFit,
    previewing: Boolean,
    previewEngine: AndroidPlayerEngine?,
    previewSoundEnabled: Boolean,
    previewProgress: State<Float>?,
    previewSurfaceTestTag: String?,
    showMetadataOverlay: Boolean,
) {
    if (previewing && previewEngine != null) {
        TvTrailerPreviewSurface(
            previewEngine = previewEngine,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(previewSurfaceTestTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
    } else if (imageUrl != null && artworkFit == TvMediaCardArtworkFit.CONTAIN_PORTRAIT) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)))
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    } else if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF292A3D), Color(0xFF151622), Color(0xFF30234A)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = TvTextMuted,
                modifier = Modifier.size(38.dp),
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops =
                        arrayOf(
                            0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.5f),
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                ),
            ),
    )
    if (previewing) {
        TvTrailerPreviewChrome(
            previewSoundEnabled = previewSoundEnabled,
            previewProgress = previewProgress?.value ?: 0f,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (showMetadataOverlay) {
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
        modifier = modifier.fillMaxSize().tvStatusSemantics(label),
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
        modifier = modifier.padding(horizontal = 6.dp).tvHeading(),
        color = TvText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
}

internal val TvScreenPadding =
    PaddingValues(
        start = 92.dp,
        end = TvLayoutTokens.SafeInsets.horizontal,
        top = TvLayoutTokens.SafeInsets.vertical,
        bottom = 54.dp,
    )

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

internal data class TvFocusContext(
    val coordinator: TvFocusCoordinator<FocusRequester>,
    val routeKey: String,
    val focusMemory: TvFocusMemory? = null,
)

internal val LocalTvFocusContext = staticCompositionLocalOf<TvFocusContext?> { null }

@Composable
internal fun Modifier.tvScreenEntryFocus(
    enabled: Boolean = true,
    focusTargetId: String,
): Modifier {
    val requester = LocalTvScreenEntryFocusRequester.current
    return if (enabled && requester != null) {
        tvFocusTarget(requester, fallback = true, focusTargetId = focusTargetId).focusRequester(requester)
    } else {
        this
    }
}

@Composable
internal fun Modifier.tvFocusTarget(
    requester: FocusRequester,
    fallback: Boolean = false,
    focusTargetId: String,
): Modifier {
    val focusContext = LocalTvFocusContext.current
    var horizontalCenter by remember(focusTargetId) { mutableStateOf(0f) }
    val semanticTarget = tvFocusTarget(focusTargetId, horizontalCenter = horizontalCenter)
    if (focusContext != null) {
        DisposableEffect(focusContext, focusTargetId, requester, fallback, semanticTarget) {
            focusContext.coordinator.register(
                focusContext.routeKey,
                focusTargetId,
                requester,
                fallback,
                semanticTarget,
            )
            onDispose {
                focusContext.coordinator.unregister(focusContext.routeKey, focusTargetId, requester)
            }
        }
    }
    return onGloballyPositioned { coordinates ->
        horizontalCenter = coordinates.boundsInRoot().center.x
    }.onFocusChanged { state ->
        if (state.isFocused) {
            focusContext
                ?.coordinator
                ?.rememberFocused(focusContext.routeKey, focusTargetId, requester)
                ?.let { target ->
                    focusContext.focusMemory?.remember(
                        routeKey = focusContext.routeKey,
                        anchor = target.anchor,
                        horizontalCenter = target.horizontalCenter,
                        horizontalIndex = target.horizontalIndex,
                    )
                }
        }
    }
}

@Composable
internal fun TvRouteFocusMaterializer(
    ownerId: String,
    targetIds: Set<String>,
    fallbackTargetIds: Set<String>,
    materialize: suspend (String) -> Boolean,
) {
    val focusContext = LocalTvFocusContext.current ?: return
    val currentMaterialize = rememberUpdatedState(materialize)
    DisposableEffect(focusContext, ownerId, targetIds, fallbackTargetIds) {
        focusContext.coordinator.registerMaterializer(
            routeKey = focusContext.routeKey,
            ownerId = ownerId,
            targetIds = targetIds,
            fallbackTargetIds = fallbackTargetIds,
        ) { targetId -> currentMaterialize.value(targetId) }
        onDispose { focusContext.coordinator.unregisterMaterializer(focusContext.routeKey, ownerId) }
    }
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
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
                openNavigationRail()
                true
            } else {
                false
            }
        }
    }
}

internal fun isTvGridLeftEdge(
    itemIndex: Int,
    columnCount: Int,
): Boolean = itemIndex >= 0 && columnCount > 0 && itemIndex % columnCount == 0

internal fun jellyfinImageUrl(
    baseUrl: String?,
    token: String?,
    itemId: String,
    tag: String?,
    type: String = "Primary",
    maxWidth: Int = TvArtworkSize.LANDSCAPE_CARD.maxWidth,
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
