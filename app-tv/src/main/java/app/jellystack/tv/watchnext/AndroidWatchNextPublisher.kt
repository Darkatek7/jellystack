package app.jellystack.tv.watchnext

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.design.tv.TvDeepLink
import dev.jellystack.design.tv.TvWatchNextAccount
import dev.jellystack.design.tv.TvWatchNextItem
import dev.jellystack.design.tv.TvWatchNextPublisher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Fills the system Watch Next row from the app's Continue Watching and Next Up lists.
 *
 * All provider and file work runs on [ioDispatcher] under one lock. Nothing here throws except for
 * cancellation: a provider that is missing, refuses writes or is rewritten by an OEM is logged and skipped.
 */
internal class AndroidWatchNextPublisher(
    gateway: WatchNextGateway,
    private val state: WatchNextStateStore,
    private val art: WatchNextArt,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFailure: (operation: String, cause: Throwable) -> Unit = ::logFailure,
) : TvWatchNextPublisher {
    override val supported: Boolean = true
    private val lock = Mutex()
    private val reconciler = WatchNextReconciler(gateway, state, onFailure)
    private var lastWritten: Map<String, WatchNextEntry> = emptyMap()

    override suspend fun sync(
        account: TvWatchNextAccount,
        items: List<TvWatchNextItem>,
    ) {
        lock.withLock {
            withContext(ioDispatcher) {
                guarded("sync") {
                    val owner = ownerKey(account)
                    if (state.owner != owner) {
                        if (state.owner != null) removeEverything()
                        state.owner = owner
                    }
                    val entries = buildEntries(account, items)
                    lastWritten = reconciler.reconcile(entries, lastWritten)
                    art.retain(entries.mapNotNullTo(HashSet()) { it.artUri })
                }
            }
        }
    }

    override suspend fun clear() {
        lock.withLock {
            withContext(ioDispatcher) { guarded("clear") { removeEverything() } }
        }
    }

    private fun removeEverything() {
        reconciler.clear()
        art.clear()
        lastWritten = emptyMap()
    }

    private suspend fun buildEntries(
        account: TvWatchNextAccount,
        items: List<TvWatchNextItem>,
    ): List<WatchNextEntry> {
        val serverHash = TvDeepLink.serverHash(account.serverKey)
        // One failed download (server unreachable) is enough: the cards go in without art and get it next run.
        var artAvailable = true
        return items.mapNotNull { item ->
            val deepLink = TvDeepLink.build(account.serverKey, item.itemId) ?: return@mapNotNull null
            val artUri =
                item.artwork?.takeIf { artAvailable }?.let { artwork ->
                    art.uriFor(account.serverKey, artwork).also { if (it == null) artAvailable = false }
                }
            WatchNextEntry(
                internalId = "$serverHash:${item.itemId}",
                mediaType = item.mediaType,
                kind = item.kind,
                title = item.title,
                episodeTitle = item.episodeTitle,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                durationMs = item.durationMs,
                positionMs = item.positionMs,
                lastEngagementMillis = item.lastEngagementMillis,
                artUri = artUri,
                artAspect = item.artwork?.aspect.takeIf { artUri != null },
                deepLink = deepLink,
            )
        }
    }

    private suspend fun guarded(
        operation: String,
        block: suspend () -> Unit,
    ) {
        runSuspendCatching { block() }.onFailure { onFailure(operation, it) }
    }

    private fun ownerKey(account: TvWatchNextAccount): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("${account.profileId}|${account.serverKey}|${account.principalId}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Only the exception type is logged: provider messages can contain content URIs. */
private fun logFailure(
    operation: String,
    cause: Throwable,
) {
    JellystackLog.w("Watch Next $operation failed: ${cause::class.java.simpleName}")
}
