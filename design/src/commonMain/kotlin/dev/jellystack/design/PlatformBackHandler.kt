package dev.jellystack.design

import androidx.compose.runtime.Composable

@Composable
internal expect fun platformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
