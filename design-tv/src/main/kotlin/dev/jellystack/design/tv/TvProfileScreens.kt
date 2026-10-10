@file:Suppress("FunctionName", "LongParameterList", "TooManyFunctions")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jellystack.core.profile.HouseholdProfile

@Composable
internal fun TvProfilePickerScreen(
    profiles: List<TvProfilePresentation>,
    rememberedProfileId: String?,
    strings: TvStrings,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialProfileId =
        remember(profiles, rememberedProfileId) {
            selectInitialTvProfileId(profiles, rememberedProfileId)
        }
    val initialFocusRequester = remember(initialProfileId) { FocusRequester() }
    LaunchedEffect(initialProfileId) {
        if (initialProfileId != null) {
            withFrameNanos { }
            initialFocusRequester.requestFocus()
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(
                    horizontal = TvLayoutTokens.SafeInsets.horizontal,
                    vertical = TvLayoutTokens.SafeInsets.vertical,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            strings.chooseProfile,
            color = TvText,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(PROFILE_TILE_WIDTH),
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 230.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(profiles, key = TvProfilePresentation::id) { profile ->
                TvProfileTile(
                    profile = profile,
                    pinLabel = strings.profilePin,
                    onClick = { onSelect(profile.id) },
                    focusRequester = initialFocusRequester.takeIf { profile.id == initialProfileId },
                    tagPrefix = "profile",
                )
            }
            item(key = "profile-add") { TvAddProfileTile(strings.addProfile, onAdd) }
        }
        TvActionButton(strings.manageProfiles, onManage, focusTargetId = "profile:manage")
    }
}

private val PROFILE_TILE_WIDTH = 150.dp

@Composable
private fun TvProfileTile(
    profile: TvProfilePresentation,
    pinLabel: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    tagPrefix: String,
) {
    var focused by remember(profile.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(PROFILE_TILE_WIDTH).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(profileAvatarColor(profile.id))
                .profileAvatarFocusRing(focused)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        if (profile.pinRequired) "${profile.displayName}, $pinLabel" else profile.displayName
                }.tvFocusable(
                    onClick = onClick,
                    shape = CircleShape,
                    focusTargetId = "$tagPrefix:${profile.id}:select",
                    providedFocusRequester = focusRequester,
                    onFocusChanged = { focused = it },
                    focusIndication = TvFocusIndication.NO_RING,
                ).testTag("$tagPrefix:${profile.id}:tile"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.fallbackInitial,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("profile:${profile.id}:avatar-fallback"),
            )
            profile.avatarUrl?.let { avatarUrl ->
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("profile:${profile.id}:avatar-image"),
                )
            }
        }
        Text(
            profile.displayName,
            color = TvText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun TvProfileManagementScreen(
    profiles: List<TvProfilePresentation>,
    selectedProfileId: String?,
    strings: TvStrings,
    onSelectProfile: (String) -> Unit,
    onManagePin: (String) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(
                    horizontal = TvLayoutTokens.SafeInsets.horizontal,
                    vertical = TvLayoutTokens.SafeInsets.vertical,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            selected?.displayName ?: strings.manageProfiles,
            color = TvText,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (selected == null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(PROFILE_TILE_WIDTH),
                modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 250.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(profiles, key = TvProfilePresentation::id) { profile ->
                    TvProfileTile(
                        profile = profile,
                        pinLabel = strings.profilePin,
                        onClick = { onSelectProfile(profile.id) },
                        focusRequester = null,
                        tagPrefix = "profile-management",
                    )
                }
            }
        } else {
            TvProfileAvatar(profile = selected, size = 132.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    label = "${strings.profilePin}: ${selected.displayName}",
                    onClick = { onManagePin(selected.id) },
                    primary = true,
                    focusTargetId = "profile-management:${selected.id}:pin",
                )
                TvActionButton(
                    label = "${strings.removeProfile}: ${selected.displayName}",
                    onClick = { onRemove(selected.id) },
                    leading = { Icon(Icons.Default.Delete, null) },
                    destructive = true,
                    focusTargetId = "profile-management:${selected.id}:remove",
                )
            }
        }
        TvActionButton(
            label = strings.back,
            onClick = onBack,
            leading = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
            focusTargetId = "profile-management:back",
        )
    }
}

@Composable
private fun TvProfileAvatar(
    profile: TvProfilePresentation,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(profileAvatarColor(profile.id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(profile.fallbackInitial, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
        profile.avatarUrl?.let { avatarUrl ->
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TvAddProfileTile(
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(PROFILE_TILE_WIDTH).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(TvSurface)
                .profileAvatarFocusRing(focused)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .tvFocusable(
                    onClick = onClick,
                    shape = CircleShape,
                    focusTargetId = "profile:add",
                    onFocusChanged = { focused = it },
                    focusIndication = TvFocusIndication.NO_RING,
                ).testTag("profile:add:tile"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, null, tint = TvText, modifier = Modifier.size(46.dp))
        }
        Text(
            label,
            color = TvText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Modifier.profileAvatarFocusRing(focused: Boolean): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val outerRadius = size.minDimension / 2f - 3.dp.toPx()
            drawCircle(TvLayoutTokens.FocusDarkRing, outerRadius, style = Stroke(5.dp.toPx()))
            drawCircle(TvLayoutTokens.FocusLightRing, outerRadius - 3.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(TvLayoutTokens.FocusAccentRing, outerRadius - 5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
    }

@Composable
internal fun TvProfilePinScreen(
    profile: HouseholdProfile,
    pin: String,
    strings: TvStrings,
    remainingAttempts: Int?,
    locked: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    title: String = "${strings.enterProfilePin}: ${profile.displayName}",
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    // The PIN pad can be the first screen after a cold start, so it takes focus itself instead of
    // waiting for a direction key before Center does anything.
    val firstDigitFocus = remember { FocusRequester() }
    LaunchedEffect(locked) {
        if (!locked) {
            withFrameNanos { }
            firstDigitFocus.requestFocus()
        }
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(horizontal = 220.dp, vertical = TvLayoutTokens.SafeInsets.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            title,
            color = TvText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (locked) {
                strings.profileLocked
            } else {
                "●".repeat(pin.length) + "○".repeat((4 - pin.length).coerceAtLeast(0))
            },
            color = if (locked) Color(0xFFFFB4AB) else TvText,
            fontSize = 28.sp,
            modifier =
                Modifier.semantics {
                    contentDescription = if (locked) strings.profileLocked else "${pin.length} of 4 digits entered"
                    liveRegion = LiveRegionMode.Polite
                },
        )
        if (remainingAttempts != null && !locked) {
            Text(strings.pinAttemptsRemaining.format(remainingAttempts), color = TvTextMuted, fontSize = 17.sp)
        }
        if (!locked) {
            listOf("123", "456", "789", "0").forEach { rowDigits ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowDigits.forEach { digit ->
                        TvActionButton(
                            label = digit.toString(),
                            onClick = { onDigit(digit) },
                            modifier = Modifier.width(74.dp),
                            enabled = pin.length < 4,
                            focusTargetId = "profile:pin:$digit",
                            focusRequester = firstDigitFocus.takeIf { digit == '1' },
                        )
                    }
                }
            }
            // Cancel shares the row with delete and continue so the whole pad fits on a 1080p screen.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(strings.back, onDelete, enabled = pin.isNotEmpty())
                TvActionButton(strings.continueLabel, onSubmit, primary = true, enabled = pin.length == 4)
                TvActionButton(strings.cancel, onCancel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TvActionButton(secondaryActionLabel, onSecondaryAction)
        }
        if (locked) TvActionButton(strings.cancel, onCancel)
    }
}

@Composable
internal fun TvProfileStatusScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvBackground)
                .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TvText, fontSize = 26.sp)
    }
}

@Composable
internal fun TvProfileReconnectScreen(
    profile: HouseholdProfile,
    strings: TvStrings,
    onReconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(TvBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(profile.displayName, color = TvText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        TvActionButton(
            strings.reconnectProfile,
            onReconnect,
            leading = { Icon(Icons.Default.Refresh, null) },
            primary = true,
        )
        TvActionButton(strings.cancel, onCancel)
    }
}

@Composable
internal fun TvRemoveProfileDialog(
    profile: HouseholdProfile,
    strings: TvStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(560.dp)
                .background(TvSurface, RoundedCornerShape(26.dp))
                .padding(30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "${strings.removeProfile}: ${profile.displayName}?",
                color = TvText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(strings.removeProfileMessage, color = TvTextMuted, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(strings.cancel, onDismiss)
                TvActionButton(strings.removeProfile, onConfirm, destructive = true)
            }
        }
    }
}

private fun profileAvatarColor(seed: String): Color {
    val palette = listOf(0xFF7C4DFF, 0xFF00897B, 0xFF1565C0, 0xFFC62828, 0xFF6A1B9A)
    return Color(palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size])
}
