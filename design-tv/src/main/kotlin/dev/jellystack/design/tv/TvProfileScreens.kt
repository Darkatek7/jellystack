@file:Suppress("FunctionName", "LongParameterList")

package dev.jellystack.design.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jellystack.core.profile.HouseholdProfile

@Composable
internal fun TvProfilePickerScreen(
    profiles: List<HouseholdProfile>,
    strings: TvStrings,
    onSelect: (HouseholdProfile) -> Unit,
    onAdd: () -> Unit,
    onRemove: (HouseholdProfile) -> Unit,
    onManagePin: (HouseholdProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstProfileRequester = remember { FocusRequester() }
    LaunchedEffect(profiles.firstOrNull()?.id) {
        if (profiles.isNotEmpty()) {
            withFrameNanos { }
            firstProfileRequester.requestFocus()
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
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(
            strings.chooseProfile,
            color = TvText,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(profiles, key = HouseholdProfile::id) { profile ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvActionButton(
                        label = profile.displayName,
                        onClick = { onSelect(profile) },
                        modifier = Modifier.weight(1f),
                        leading = {
                            Box(
                                Modifier
                                    .background(profileAvatarColor(profile.avatarSeed), CircleShape)
                                    .padding(6.dp),
                            ) {
                                Icon(Icons.Default.AccountCircle, null, tint = Color.White)
                            }
                        },
                        primary = true,
                        focusTargetId = "profile:${profile.id}:select",
                        focusRequester = firstProfileRequester.takeIf { profile.id == profiles.first().id },
                    )
                    TvActionButton(
                        label = "${strings.profilePin}: ${profile.displayName}",
                        onClick = { onManagePin(profile) },
                        focusTargetId = "profile:${profile.id}:pin",
                    )
                    TvActionButton(
                        label = "${strings.removeProfile}: ${profile.displayName}",
                        onClick = { onRemove(profile) },
                        leading = { Icon(Icons.Default.Delete, null) },
                        destructive = true,
                        focusTargetId = "profile:${profile.id}:remove",
                    )
                }
            }
        }
        TvActionButton(
            label = strings.addProfile,
            onClick = onAdd,
            leading = { Icon(Icons.Default.Add, null) },
            focusTargetId = "profile:add",
        )
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
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(TvBackground)
                .padding(horizontal = 220.dp, vertical = TvLayoutTokens.SafeInsets.vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
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
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(strings.back, onDelete, enabled = pin.isNotEmpty())
                TvActionButton(strings.continueLabel, onSubmit, primary = true, enabled = pin.length == 4)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TvActionButton(secondaryActionLabel, onSecondaryAction)
        }
        TvActionButton(strings.cancel, onCancel)
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
