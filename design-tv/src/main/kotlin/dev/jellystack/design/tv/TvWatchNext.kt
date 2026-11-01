package dev.jellystack.design.tv

/** What a Watch Next card means; maps to the system's CONTINUE (resume) and NEXT (next episode) types. */
enum class TvWatchNextKind { CONTINUE, NEXT }

enum class TvWatchNextMediaType { MOVIE, EPISODE }

/** Aspect ratio of the artwork, so the launcher crops it right. */
enum class TvWatchNextArtAspect { LANDSCAPE_16_9, PORTRAIT_2_3 }

/**
 * Which Jellyfin image to show. It carries no URL and no token on purpose: the publisher downloads the
 * image itself and hands the launcher a local content URI, so credentials never reach the shared provider.
 */
data class TvWatchNextArtwork(
    val itemId: String,
    val imageTag: String,
    val imageType: String,
    val aspect: TvWatchNextArtAspect,
)

data class TvWatchNextItem(
    val itemId: String,
    val mediaType: TvWatchNextMediaType,
    val kind: TvWatchNextKind,
    /** The movie title, or the series name of an episode. */
    val title: String,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val lastEngagementMillis: Long,
    val artwork: TvWatchNextArtwork? = null,
)

/** The account the items belong to. Opaque to the publisher; any change clears what was published before. */
data class TvWatchNextAccount(
    val serverKey: String,
    val principalId: String,
    val profileId: String?,
)

/**
 * Publishes items to the system's Watch Next row. Implemented in the TV app (Android TV provider access);
 * implementations never throw except for cancellation.
 */
interface TvWatchNextPublisher {
    /** False on Fire TV and on devices without the TV provider: nothing is published and no setting is shown. */
    val supported: Boolean

    /** Makes the Watch Next row show exactly [items] for [account] (minus those the user removed there). */
    suspend fun sync(
        account: TvWatchNextAccount,
        items: List<TvWatchNextItem>,
    )

    /** Removes everything this app published, including cached artwork. */
    suspend fun clear()
}

object UnsupportedTvWatchNextPublisher : TvWatchNextPublisher {
    override val supported: Boolean = false

    override suspend fun sync(
        account: TvWatchNextAccount,
        items: List<TvWatchNextItem>,
    ) = Unit

    override suspend fun clear() = Unit
}

/** Hooks the TV host app supplies to the root; the defaults do nothing, so previews and tests need no setup. */
class TvPlatformIntegrations(
    val watchNext: TvWatchNextPublisher = UnsupportedTvWatchNextPublisher,
    val deepLinks: TvDeepLinkInbox = TvDeepLinkInbox(),
)
