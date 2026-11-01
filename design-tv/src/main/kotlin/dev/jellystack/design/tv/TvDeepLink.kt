package dev.jellystack.design.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import java.security.MessageDigest

/** A parsed `jellystack://item/{id}?s={server hash}` link. Both parts are format-checked, nothing else is kept. */
data class TvDeepLinkTarget(
    val itemId: String,
    val serverHash: String,
)

/**
 * Item links shared with the system (the Watch Next row). The server is an opaque hash of the connection id, so
 * a card of another server, or a stale one from a removed server, can never open an item of the active server.
 */
object TvDeepLink {
    private const val SCHEME_PREFIX = "jellystack://item/"
    private const val SERVER_HASH_LENGTH = 16
    private val itemIdPattern = Regex("^[0-9a-fA-F]{32}$")
    private val link = Regex("""^jellystack://item/([0-9a-fA-F]{32})\?s=([0-9a-f]{$SERVER_HASH_LENGTH})$""")

    /** Jellyfin item ids are 32 hex digits; anything else is not worth a link. */
    fun isValidItemId(id: String): Boolean = itemIdPattern.matches(id)

    fun serverHash(serverKey: String): String = sha256Hex("jellystack-link:$serverKey").take(SERVER_HASH_LENGTH)

    /** Null unless [serverKey] and [itemId] make a link [parse] accepts again. */
    fun build(
        serverKey: String,
        itemId: String,
    ): String? = "$SCHEME_PREFIX$itemId?s=${serverHash(serverKey)}".takeIf { parse(it) != null }

    /** Strict on purpose: any other scheme, host, path, extra query or fragment is rejected. */
    fun parse(uri: String?): TvDeepLinkTarget? {
        val match = uri?.takeIf { it.length <= MAX_LENGTH }?.let(link::matchEntire) ?: return null
        return TvDeepLinkTarget(itemId = match.groupValues[1], serverHash = match.groupValues[2])
    }

    fun belongsTo(
        target: TvDeepLinkTarget,
        serverKey: String,
    ): Boolean = target.serverHash == serverHash(serverKey)

    private const val MAX_LENGTH = 128
}

internal fun sha256Hex(value: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Holds one link from the activity (cold start or `onNewIntent`) until the signed-in app can open it.
 * A link is used once, only for the matching server, and only while fresh; otherwise it is dropped.
 */
class TvDeepLinkInbox(
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    data class Pending(
        val target: TvDeepLinkTarget,
        val receivedAtMillis: Long,
    )

    private val mutablePending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = mutablePending.asStateFlow()

    /** Stores [uri] if it is a valid link and replaces an older one; anything else is ignored. */
    fun offer(uri: String?) {
        val target = TvDeepLink.parse(uri) ?: return
        mutablePending.value = Pending(target, nowMillis())
    }

    /** The item to open for the active [serverKey], or null. The pending link is gone afterwards either way. */
    fun consume(serverKey: String): String? {
        val current = mutablePending.value ?: return null
        mutablePending.value = null
        val fresh = nowMillis() - current.receivedAtMillis in 0..LINK_LIFETIME_MILLIS
        return current.target.itemId.takeIf { fresh && TvDeepLink.belongsTo(current.target, serverKey) }
    }

    fun drop() {
        mutablePending.value = null
    }

    companion object {
        /** Long enough for a PIN entry, short enough that an abandoned link does not open minutes later. */
        const val LINK_LIFETIME_MILLIS = 120_000L
    }
}
