package dev.jellystack.design

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
internal actual fun platformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    if (!LocalInspectionMode.current) BackHandler(enabled = enabled, onBack = onBack)
}
