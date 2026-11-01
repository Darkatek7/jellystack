package app.jellystack.tv.watchnext

import kotlinx.coroutines.CancellationException

/**
 * Brings the provider rows in line with the wanted entries, matching by internal provider id.
 *
 * A card the user removed in the launcher must not come back while the item is still wanted. The system
 * either keeps such a row with `browsable = 0` or deletes it; both are detected (a hidden row, or a row we
 * published that vanished without us deleting it) and remembered as dismissed until the item leaves the list.
 */
internal class WatchNextReconciler(
    private val gateway: WatchNextGateway,
    private val state: WatchNextStateStore,
    private val onFailure: (operation: String, cause: Throwable) -> Unit,
) {
    /**
     * @param previous the entries written by the last run; an unchanged one is not written again.
     * @return the entries now in the provider and written by this process.
     * @throws Exception when the provider cannot be read at all; single write failures are reported to
     * [onFailure] and retried by the next run.
     */
    fun reconcile(
        desired: List<WatchNextEntry>,
        previous: Map<String, WatchNextEntry>,
    ): Map<String, WatchNextEntry> {
        val published = gateway.readPublished()
        val wantedIds = desired.mapTo(LinkedHashSet()) { it.internalId }
        val rows = published.filter { it.internalId != null }.groupBy { it.internalId }

        val dismissed = dismissedIds(published, rows.keys, wantedIds)
        val remaining = deleteUnwanted(published, rows, wantedIds)
        val written = HashMap<String, WatchNextEntry>()
        desired.filter { it.internalId !in dismissed }.forEach { entry ->
            val id = entry.internalId
            val row = rows[id]?.first()
            when {
                row == null ->
                    if (attempt("insert") { gateway.insert(entry) }) {
                        remaining += id
                        written[id] = entry
                    }
                !row.browsable -> Unit
                previous[id] == entry -> written[id] = entry
                attempt("update") { gateway.update(row.rowId, entry) } -> written[id] = entry
            }
        }

        state.dismissed = dismissed
        state.known = remaining
        return written
    }

    /** Deletes every row this app has in the provider and forgets all state. */
    fun clear() {
        gateway.readPublished().forEach { row -> attempt("delete") { gateway.delete(row.rowId) } }
        state.reset()
    }

    /** Known dismissals plus new ones (hidden rows, rows that vanished), minus items no longer wanted. */
    private fun dismissedIds(
        published: List<PublishedProgram>,
        idsWithRow: Set<String?>,
        wantedIds: Set<String>,
    ): Set<String> {
        val hidden = published.filter { !it.browsable }.mapNotNull { it.internalId }
        val vanished = state.known.filter { it !in idsWithRow }
        return (state.dismissed + hidden + vanished).filterTo(HashSet()) { it in wantedIds }
    }

    /** Deletes rows of unwanted items, duplicates and rows without an id; returns the ids that still have a row. */
    private fun deleteUnwanted(
        published: List<PublishedProgram>,
        rows: Map<String?, List<PublishedProgram>>,
        wantedIds: Set<String>,
    ): MutableSet<String> {
        val remaining = HashSet<String>()
        published.forEach { row ->
            val id = row.internalId
            val keep = id != null && id in wantedIds && rows.getValue(id).first() == row
            if (keep || !attempt("delete") { gateway.delete(row.rowId) }) id?.let(remaining::add)
        }
        return remaining
    }

    private inline fun attempt(
        operation: String,
        block: () -> Boolean,
    ): Boolean =
        runCatching(block).getOrElse { failure ->
            if (failure is CancellationException) throw failure
            onFailure(operation, failure)
            false
        }
}
