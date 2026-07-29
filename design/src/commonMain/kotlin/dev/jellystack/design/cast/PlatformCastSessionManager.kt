package dev.jellystack.design.cast

import androidx.compose.runtime.Composable
import dev.jellystack.players.cast.CastSessionManager

/**
 * Provides a platform-specific [CastSessionManager] implementation when available.
 *
 * Platforms that do not support Cast should return `null`.
 */
@Composable
expect fun rememberPlatformCastSessionManager(): CastSessionManager?
