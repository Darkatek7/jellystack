@file:Suppress("FunctionName", "FunctionNaming")

package dev.jellystack.design.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.logging.JellystackLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NOT_FOUND_MESSAGE_MILLIS = 3_000L

/** What a Seerr cast card can do: look the person up on the Jellyfin server and open their page. */
internal class TvSeerrCastActions(
    val resolvePerson: suspend (name: String) -> JellyfinPerson?,
    val onOpenPerson: (JellyfinPerson) -> Unit,
) {
    companion object {
        /** No Jellyfin to look in: every card says it is not in the library. */
        val None = TvSeerrCastActions(resolvePerson = { null }, onOpenPerson = {})
    }
}

/**
 * The Jellyfin person for a Seerr cast member, or null when Jellyfin does not know them or cannot answer.
 * Seerr never blocks Jellyfin, so a failed lookup is the same as no match.
 */
internal suspend fun resolveSeerrCastPerson(
    name: String,
    resolvePerson: suspend (String) -> JellyfinPerson?,
): JellyfinPerson? =
    runSuspendCatching { resolvePerson(name) }
        .onFailure { JellystackLog.w("Could not look up a cast member on the Jellyfin server", it) }
        .getOrNull()

/** The Seerr cast row; OK on a person opens their Jellyfin page, else a short note appears under the row. */
@Composable
internal fun TvSeerrCastSection(
    title: String,
    people: List<TvDetailCastItem.Seerr>,
    notFoundMessage: String,
    actions: TvSeerrCastActions,
    focusModifiers: TvDetailSectionFocusModifiers,
) {
    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }
    var showNotFound by remember { mutableStateOf(false) }
    val openPerson: (String) -> Unit = { name ->
        // Repeat presses while a lookup runs are ignored.
        if (!resolving) {
            resolving = true
            showNotFound = false
            scope.launch {
                val person =
                    try {
                        resolveSeerrCastPerson(name, actions.resolvePerson)
                    } finally {
                        resolving = false
                    }
                if (person != null) {
                    actions.onOpenPerson(person)
                } else {
                    showNotFound = true
                    delay(NOT_FOUND_MESSAGE_MILLIS)
                    showNotFound = false
                }
            }
        }
    }
    val cards =
        people.map { castItem ->
            TvDetailCastCard(
                id = castItem.id,
                name = castItem.person.name,
                role = castItem.person.character,
                imageUrl = tmdbImageUrl(castItem.person.profilePath),
                onClick = { openPerson(castItem.person.name) },
            )
        }
    Column {
        TvDetailCastRow(title, cards, focusModifiers)
        if (showNotFound) {
            Text(
                notFoundMessage,
                color = TvTextMuted,
                fontSize = TvTextSize.Body,
                modifier = Modifier.padding(start = TV_DETAIL_START_DP.dp),
            )
        }
    }
}
