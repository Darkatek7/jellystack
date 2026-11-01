package app.jellystack.tv.watchnext

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WatchNextReconcilerTest {
    private val gateway = FakeGateway()
    private val state = FakeState()
    private val failures = mutableListOf<String>()
    private val reconciler = WatchNextReconciler(gateway, state) { operation, _ -> failures += operation }

    @Test
    fun newItemsAreInsertedAndRemovedOnesDeleted() {
        var written = reconciler.reconcile(listOf(entry("a"), entry("b")), emptyMap())
        assertEquals(setOf("a", "b"), gateway.idsInTable())

        written = reconciler.reconcile(listOf(entry("b"), entry("c")), written)

        assertEquals(setOf("b", "c"), gateway.idsInTable())
        assertEquals(setOf("b", "c"), written.keys)
    }

    @Test
    fun anUnchangedItemIsNotWrittenAgainAndAChangedOneIsUpdatedInPlace() {
        var written = reconciler.reconcile(listOf(entry("a", positionMs = 60_000L)), emptyMap())
        gateway.operations.clear()

        written = reconciler.reconcile(listOf(entry("a", positionMs = 60_000L)), written)
        assertTrue(gateway.operations.isEmpty())

        reconciler.reconcile(listOf(entry("a", positionMs = 120_000L)), written)
        assertEquals(listOf("update a"), gateway.operations)
        assertEquals(
            120_000L,
            gateway.rows
                .single()
                .entry.positionMs,
        )
    }

    @Test
    fun aCardHiddenByTheUserIsNeitherReinsertedNorUpdated() {
        var written = reconciler.reconcile(listOf(entry("a")), emptyMap())
        gateway.userRemoves("a", keepHidden = true)
        gateway.operations.clear()

        written = reconciler.reconcile(listOf(entry("a", positionMs = 120_000L)), written)
        reconciler.reconcile(listOf(entry("a", positionMs = 180_000L)), written)

        assertTrue(gateway.operations.isEmpty())
        assertEquals(1, gateway.rows.size)
    }

    @Test
    fun aCardDeletedByTheLauncherIsNotReinsertedWhileTheItemIsStillWanted() {
        var written = reconciler.reconcile(listOf(entry("a"), entry("b")), emptyMap())
        gateway.userRemoves("a", keepHidden = false)
        gateway.operations.clear()

        written = reconciler.reconcile(listOf(entry("a"), entry("b")), written)
        reconciler.reconcile(listOf(entry("a"), entry("b")), written)

        assertTrue(gateway.operations.isEmpty())
        assertEquals(setOf("b"), gateway.idsInTable())
        assertEquals(setOf("a"), state.dismissed)
    }

    @Test
    fun aRemovedItemMayReturnAfterItLeftTheList() {
        var written = reconciler.reconcile(listOf(entry("a")), emptyMap())
        gateway.userRemoves("a", keepHidden = true)
        written = reconciler.reconcile(listOf(entry("a")), written)

        written = reconciler.reconcile(emptyList(), written)
        assertTrue(gateway.rows.isEmpty())
        assertTrue(state.dismissed.isEmpty())

        reconciler.reconcile(listOf(entry("a")), written)
        assertEquals(setOf("a"), gateway.idsInTable())
        assertTrue(gateway.rows.single().browsable)
    }

    @Test
    fun dismissalIsKeptInTheStateStoreAcrossRuns() {
        reconciler.reconcile(listOf(entry("a")), emptyMap())
        gateway.userRemoves("a", keepHidden = false)
        reconciler.reconcile(listOf(entry("a")), emptyMap())

        // A new reconciler with a new in-memory baseline, as after a process restart.
        WatchNextReconciler(gateway, state) { _, _ -> }.reconcile(listOf(entry("a")), emptyMap())

        assertTrue(gateway.rows.isEmpty())
        assertEquals(setOf("a"), state.dismissed)
    }

    @Test
    fun aDuplicateRowOfTheSameItemIsDeleted() {
        reconciler.reconcile(listOf(entry("a")), emptyMap())
        gateway.rows += FakeGateway.Row(rowId = 90L, entry = entry("a"))

        reconciler.reconcile(listOf(entry("a")), emptyMap())

        assertEquals(1, gateway.rows.size)
    }

    @Test
    fun aFailingWriteIsReportedAndTheOtherItemsStillGoThrough() {
        gateway.failOn = "insert"

        val written = reconciler.reconcile(listOf(entry("a"), entry("b")), emptyMap())

        assertEquals(listOf("insert", "insert"), failures)
        assertTrue(written.isEmpty())
        gateway.failOn = null
        assertEquals(setOf("a", "b"), reconciler.reconcile(listOf(entry("a"), entry("b")), written).keys)
    }

    @Test
    fun anUnreadableProviderFailsTheRunWithoutTouchingState() {
        state.known = setOf("a")
        gateway.failRead = true

        assertFailsWith<SecurityException> { reconciler.reconcile(listOf(entry("a")), emptyMap()) }

        assertEquals(setOf("a"), state.known)
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        val cancelling =
            object : WatchNextGateway by gateway {
                override fun insert(entry: WatchNextEntry): Boolean = throw CancellationException("stop")
            }

        assertFailsWith<CancellationException> {
            WatchNextReconciler(cancelling, state) { _, _ -> }.reconcile(listOf(entry("a")), emptyMap())
        }
    }

    @Test
    fun clearDeletesEverythingAndForgetsTheState() {
        reconciler.reconcile(listOf(entry("a"), entry("b")), emptyMap())
        gateway.userRemoves("a", keepHidden = true)
        state.owner = "owner"

        reconciler.clear()

        assertTrue(gateway.rows.isEmpty())
        assertEquals(null, state.owner)
        assertTrue(state.known.isEmpty())
        assertTrue(state.dismissed.isEmpty())
    }
}
