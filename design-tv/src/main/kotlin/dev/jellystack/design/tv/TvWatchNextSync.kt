@file:Suppress("FunctionName")

package dev.jellystack.design.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant

/** Items per row: the launcher shows few cards, and a long list would only be noise on the home screen. */
internal const val TV_WATCH_NEXT_LIMIT_PER_KIND = 10

/** Waits for the home feeds to settle, so a burst of refreshes becomes one provider update. */
internal const val TV_WATCH_NEXT_DEBOUNCE_MILLIS = 2_000L

private const val TICKS_PER_MILLISECOND = 10_000L
private const val FALLBACK_ENGAGEMENT_BASE_MILLIS = 1_000_000L

/**
 * Continue Watching first, then Next Up, each limited and without items that appear twice. Only movies and
 * episodes qualify; a resume card needs a position and a duration, so one without them is left out.
 */
internal fun buildTvWatchNextItems(
    continueWatching: List<JellyfinItem>,
    nextUp: List<JellyfinItem>,
): List<TvWatchNextItem> {
    val seen = HashSet<String>()
    val resume =
        continueWatching
            .mapIndexedNotNull { index, item -> item.toWatchNextItem(TvWatchNextKind.CONTINUE, index) }
            .filter { seen.add(it.itemId) }
            .take(TV_WATCH_NEXT_LIMIT_PER_KIND)
    val next =
        nextUp
            .mapIndexedNotNull { index, item -> item.toWatchNextItem(TvWatchNextKind.NEXT, index) }
            .filter { seen.add(it.itemId) }
            .take(TV_WATCH_NEXT_LIMIT_PER_KIND)
    return resume + next
}

private fun JellyfinItem.toWatchNextItem(
    kind: TvWatchNextKind,
    index: Int,
): TvWatchNextItem? {
    val mediaType = watchNextMediaType()
    val durationMs = runTimeTicks?.takeIf { it > 0L }?.div(TICKS_PER_MILLISECOND)
    val positionMs = positionTicks?.takeIf { it > 0L }?.div(TICKS_PER_MILLISECOND)
    val resumable = positionMs != null && durationMs != null && positionMs < durationMs
    val qualifies = TvDeepLink.isValidItemId(id) && (resumable || kind != TvWatchNextKind.CONTINUE)
    if (mediaType == null || !qualifies) return null
    val isEpisode = mediaType == TvWatchNextMediaType.EPISODE
    return TvWatchNextItem(
        itemId = id,
        mediaType = mediaType,
        kind = kind,
        title = if (isEpisode) seriesName?.takeIf(String::isNotBlank) ?: name else name,
        episodeTitle = if (isEpisode) episodeTitle?.takeIf(String::isNotBlank) ?: name else null,
        seasonNumber = parentIndexNumber.takeIf { isEpisode },
        episodeNumber = indexNumber.takeIf { isEpisode },
        durationMs = durationMs,
        positionMs = positionMs.takeIf { kind == TvWatchNextKind.CONTINUE },
        lastEngagementMillis = engagementMillis(fallback = FALLBACK_ENGAGEMENT_BASE_MILLIS - index),
        artwork = watchNextArtwork(),
    )
}

private fun JellyfinItem.watchNextMediaType(): TvWatchNextMediaType? =
    when {
        type.equals("Movie", ignoreCase = true) -> TvWatchNextMediaType.MOVIE
        type.equals("Episode", ignoreCase = true) -> TvWatchNextMediaType.EPISODE
        else -> null
    }

private fun JellyfinItem.engagementMillis(fallback: Long): Long =
    lastPlayed.epochMillisOrNull() ?: dateCreated.epochMillisOrNull() ?: fallback

private fun JellyfinItem.watchNextArtwork(): TvWatchNextArtwork? =
    resolveTvJellyfinArtwork(this, landscape = true)?.let {
        val portrait = it.imageType.equals("Primary", ignoreCase = true)
        TvWatchNextArtwork(
            itemId = it.itemId,
            imageTag = it.imageTag,
            imageType = it.imageType,
            aspect = if (portrait) TvWatchNextArtAspect.PORTRAIT_2_3 else TvWatchNextArtAspect.LANDSCAPE_16_9,
        )
    }

private fun String?.epochMillisOrNull(): Long? =
    this?.takeIf(String::isNotBlank)?.let { raw ->
        try {
            Instant.parse(raw).toEpochMilliseconds()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

/**
 * Keeps the system Watch Next row in step with the home feeds while the signed-in app is composed. It never
 * publishes before the feeds have loaded (an empty list would wipe the row), and clears it when turned off.
 * Clearing on sign-out and profile switches is the host's job, since leaving composition also happens on exit.
 */
@Composable
internal fun TvWatchNextSyncEffect(
    publisher: TvWatchNextPublisher,
    enabled: Boolean,
    account: TvWatchNextAccount,
    home: JellyfinHomeState,
) {
    val feedsLoaded = home.libraries.isNotEmpty() && !home.isInitialLoading && !home.isHomeLoading
    LaunchedEffect(publisher, enabled, account, feedsLoaded, home.continueWatching, home.nextUp) {
        if (!publisher.supported) return@LaunchedEffect
        if (!enabled) {
            publisher.clear()
        } else if (feedsLoaded) {
            delay(TV_WATCH_NEXT_DEBOUNCE_MILLIS)
            publisher.sync(account, buildTvWatchNextItems(home.continueWatching, home.nextUp))
        }
    }
}
