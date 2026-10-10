package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavEntry

/**
 * Navigation 3 builds an entry once per back stack and keeps it until the back stack changes, so an entry's
 * content keeps the values it captured then. The home route stayed on its first, still-loading home state
 * until the user navigated away and back.
 *
 * The returned provider gives Navigation 3 entries with the same key, content key and metadata whose content
 * always draws the entry that [entryProvider] builds from the current composition.
 */
@Composable
internal fun <T : Any> rememberLatestNavEntryProvider(entryProvider: (T) -> NavEntry<T>): (T) -> NavEntry<T> {
    val latestEntryProvider = rememberUpdatedState(entryProvider)
    return remember(latestEntryProvider) {
        { key ->
            NavEntry(navEntry = latestEntryProvider.value(key)) { entryKey ->
                latestEntryProvider.value(entryKey).Content()
            }
        }
    }
}
