package dev.jellystack.design.lifecycle

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberAppForegroundActive(): Boolean
