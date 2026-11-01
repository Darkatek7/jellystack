package app.jellystack.tv.watchnext

import dev.jellystack.design.tv.TvDeepLink
import dev.jellystack.design.tv.TvWatchNextAccount
import dev.jellystack.design.tv.TvWatchNextItem
import dev.jellystack.design.tv.TvWatchNextKind
import dev.jellystack.design.tv.TvWatchNextMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidWatchNextPublisherTest {
    private val itemA = "0123456789abcdef0123456789abcdef"
    private val itemB = "fedcba9876543210fedcba9876543210"
    private val account = TvWatchNextAccount(serverKey = "server-a", principalId = "user-1", profileId = "profile-1")
    private val gateway = FakeGateway()
    private val state = FakeState()
    private val failures = mutableListOf<String>()

    private fun TestScope.publisher(
        art: WatchNextArt = FakeArt(),
        gateway: WatchNextGateway = this@AndroidWatchNextPublisherTest.gateway,
    ) = AndroidWatchNextPublisher(
        gateway = gateway,
        state = state,
        art = art,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        onFailure = { operation, _ -> failures += operation },
    )

    private fun item(
        id: String,
        withArt: Boolean = false,
    ) = TvWatchNextItem(
        itemId = id,
        mediaType = TvWatchNextMediaType.MOVIE,
        kind = TvWatchNextKind.CONTINUE,
        title = "Example Movie",
        durationMs = 3_600_000L,
        positionMs = 60_000L,
        lastEngagementMillis = 1_000L,
        artwork = artwork(itemId = id).takeIf { withArt },
    )

    @Test
    fun publishesDeepLinkInternalIdAndArtForEveryItem() =
        runTest {
            val art = FakeArt(mapOf(itemA to "content://app.example.watchnext.art/art/abc"))

            publisher(art).sync(account, listOf(item(itemA, withArt = true)))

            val published = gateway.rows.single().entry
            assertEquals("${TvDeepLink.serverHash("server-a")}:$itemA", published.internalId)
            assertEquals(TvDeepLink.build("server-a", itemA), published.deepLink)
            assertEquals("content://app.example.watchnext.art/art/abc", published.artUri)
            assertEquals(setOf("content://app.example.watchnext.art/art/abc"), art.retained)
        }

    @Test
    fun noRowCarriesAnythingThatLooksLikeAServerUrlOrToken() =
        runTest {
            publisher().sync(account, listOf(item(itemA)))

            val published =
                gateway.rows
                    .single()
                    .entry
                    .toString()
            assertFalse("server-a" in published)
            assertFalse("http" in published)
        }

    @Test
    fun aFailedDownloadStopsFurtherDownloadsButStillPublishesTheCards() =
        runTest {
            val art = FakeArt(emptyMap())

            publisher(art).sync(account, listOf(item(itemA, withArt = true), item(itemB, withArt = true)))

            assertEquals(listOf(itemA), art.requested)
            assertEquals(2, gateway.rows.size)
            assertTrue(gateway.rows.all { it.entry.artUri == null && it.entry.artAspect == null })
        }

    @Test
    fun aSecondSyncWithTheSameItemsWritesNothing() =
        runTest {
            val publisher = publisher()
            publisher.sync(account, listOf(item(itemA)))
            gateway.operations.clear()

            publisher.sync(account, listOf(item(itemA)))

            assertTrue(gateway.operations.isEmpty())
        }

    @Test
    fun changingProfileOrAccountFirstRemovesWhatWasPublishedBefore() =
        runTest {
            val art = FakeArt()
            val publisher = publisher(art)
            publisher.sync(account, listOf(item(itemA)))

            publisher.sync(account.copy(profileId = "profile-2"), listOf(item(itemB)))

            assertEquals(1, gateway.rows.size)
            assertTrue(
                gateway.rows
                    .single()
                    .entry.internalId
                    .endsWith(itemB),
            )
            assertTrue(art.cleared)
        }

    @Test
    fun clearRemovesAllCardsAndArt() =
        runTest {
            val art = FakeArt()
            val publisher = publisher(art)
            publisher.sync(account, listOf(item(itemA), item(itemB)))

            publisher.clear()

            assertTrue(gateway.rows.isEmpty())
            assertTrue(art.cleared)
            assertNull(state.owner)
        }

    @Test
    fun aRefusingProviderIsLoggedWithoutBeingThrown() =
        runTest {
            gateway.failRead = true

            publisher().sync(account, listOf(item(itemA)))
            publisher().clear()

            assertEquals(listOf("sync", "clear"), failures)
        }

    @Test
    fun cancellationPropagates() =
        runTest {
            val cancelling =
                object : WatchNextGateway by gateway {
                    override fun readPublished(): List<PublishedProgram> = throw CancellationException("stop")
                }

            val publisher = publisher(gateway = cancelling)
            assertFailsWith<CancellationException> { publisher.sync(account, listOf(item(itemA))) }
            assertTrue(failures.isEmpty())
        }

    @Test
    fun itemsWithoutAValidIdAreSkipped() =
        runTest {
            publisher().sync(account, listOf(item("not-a-guid"), item(itemA)))

            assertNotNull(gateway.rows.singleOrNull())
            assertTrue(
                gateway.rows
                    .single()
                    .entry.internalId
                    .endsWith(itemA),
            )
        }
}
