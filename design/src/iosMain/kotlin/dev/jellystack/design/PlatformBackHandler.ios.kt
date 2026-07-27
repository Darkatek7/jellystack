package dev.jellystack.design

import androidx.compose.runtime.Composable

@Composable
internal actual fun platformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS has no back stack to handle yet.
}
