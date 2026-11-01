package dev.jellystack.design.tv

/**
 * The card that last had focus in each row, so moving Up and back Down returns to it instead of the row's first card.
 * Only the id is kept: the row's own list state keeps its scroll offset, and an id that is no longer in the row, as
 * after a refresh, lands on the first card. Rows are told apart by route and row id; the memory is cleared with the
 * rest of the focus memory when the account or profile changes.
 */
internal class TvRowFocusMemory {
    private val remembered = mutableMapOf<Pair<String, String>, String>()

    fun remember(
        routeKey: String,
        rowId: String,
        itemId: String,
    ) {
        remembered[routeKey to rowId] = itemId
    }

    /** Index of the card to focus when entering the row: the remembered one if it is still there, else the first. */
    fun landingIndex(
        routeKey: String,
        rowId: String,
        itemIds: List<String>,
    ): Int = remembered[routeKey to rowId]?.let(itemIds::indexOf)?.takeIf { it >= 0 } ?: 0

    /** Drops the rows of [routeKey] that are no longer shown, so one that comes back starts at its first card. */
    fun retainRows(
        routeKey: String,
        rowIds: Collection<String>,
    ) {
        remembered.keys.removeAll { (route, rowId) -> route == routeKey && rowId !in rowIds }
    }

    /** Forgets every row of [routeKey], for a route that is closed. */
    fun forgetRoute(routeKey: String) {
        remembered.keys.removeAll { (route, _) -> route == routeKey }
    }

    fun clear() = remembered.clear()
}
