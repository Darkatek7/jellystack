package dev.jellystack.design.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvDeepLinkTest {
    private val itemId = "0123456789abcdef0123456789abcdef"

    @Test
    fun aBuiltLinkParsesBackToItsItemAndServer() {
        val link = checkNotNull(TvDeepLink.build("server-a", itemId))

        val target = checkNotNull(TvDeepLink.parse(link))

        assertEquals(itemId, target.itemId)
        assertTrue(TvDeepLink.belongsTo(target, "server-a"))
        assertFalse(TvDeepLink.belongsTo(target, "server-b"))
    }

    @Test
    fun theServerKeyItselfNeverAppearsInALink() {
        val link = checkNotNull(TvDeepLink.build("server-connection-1234", itemId))

        assertFalse("server-connection-1234" in link)
    }

    @Test
    fun malformedOrForeignLinksAreRejected() {
        val hash = TvDeepLink.serverHash("server-a")
        val rejected =
            listOf(
                null,
                "",
                "jellystack://item/$itemId",
                "jellystack://item/$itemId?s=",
                "jellystack://item/short?s=$hash",
                "jellystack://item/${itemId}0?s=$hash",
                "jellystack://item/zzzz456789abcdef0123456789abcdef?s=$hash",
                "jellystack://item/../$itemId?s=$hash",
                "jellystack://item/$itemId/extra?s=$hash",
                "jellystack://other/$itemId?s=$hash",
                "https://item/$itemId?s=$hash",
                "JELLYSTACK://item/$itemId?s=$hash",
                "jellystack://item/$itemId?s=$hash&x=1",
                "jellystack://item/$itemId?s=$hash#frag",
                "jellystack://user@item/$itemId?s=$hash",
                "jellystack://item/$itemId?s=${hash.uppercase()}",
                "jellystack://item/$itemId?s=${hash}0",
            )

        rejected.forEach { assertNull(TvDeepLink.parse(it), "should reject $it") }
    }

    @Test
    fun linksAreNotBuiltForInvalidItemIds() {
        assertNull(TvDeepLink.build("server-a", "not-an-id"))
        assertNull(TvDeepLink.build("server-a", "$itemId/.."))
    }

    @Test
    fun inboxOpensAMatchingFreshLinkExactlyOnce() {
        val inbox = TvDeepLinkInbox(nowMillis = { 1_000L })
        inbox.offer(TvDeepLink.build("server-a", itemId))

        assertEquals(itemId, inbox.consume("server-a"))
        assertNull(inbox.consume("server-a"))
    }

    @Test
    fun inboxDropsALinkOfAnotherServer() {
        val inbox = TvDeepLinkInbox(nowMillis = { 1_000L })
        inbox.offer(TvDeepLink.build("server-a", itemId))

        assertNull(inbox.consume("server-b"))
        assertNull(inbox.pending.value)
    }

    @Test
    fun inboxIgnoresInvalidLinksAndKeepsTheEarlierPendingOne() {
        val inbox = TvDeepLinkInbox(nowMillis = { 1_000L })
        inbox.offer(TvDeepLink.build("server-a", itemId))

        inbox.offer("jellystack://item/nope")

        assertEquals(itemId, inbox.consume("server-a"))
    }

    @Test
    fun inboxDropsAStaleLink() {
        var now = 1_000L
        val inbox = TvDeepLinkInbox(nowMillis = { now })
        inbox.offer(TvDeepLink.build("server-a", itemId))
        now += TvDeepLinkInbox.LINK_LIFETIME_MILLIS + 1

        assertNull(inbox.consume("server-a"))
        assertNull(inbox.pending.value)
    }

    @Test
    fun dropForgetsThePendingLink() {
        val inbox = TvDeepLinkInbox(nowMillis = { 1_000L })
        inbox.offer(TvDeepLink.build("server-a", itemId))

        inbox.drop()

        assertNull(inbox.consume("server-a"))
    }
}
