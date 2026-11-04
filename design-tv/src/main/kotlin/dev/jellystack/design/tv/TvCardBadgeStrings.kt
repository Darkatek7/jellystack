package dev.jellystack.design.tv

/** Short pill labels on media cards; the full availability and request labels are too long for a card corner. */
internal data class TvCardBadgeStrings(
    val partial: String,
    val requested: String,
    private val unplayedCountFormat: String,
) {
    /** Spoken form of the unplayed-episode pill, since the pill itself only shows a number. */
    fun unplayedCount(count: Int): String = unplayedCountFormat.format(count)

    companion object {
        val ENGLISH =
            TvCardBadgeStrings(
                partial = "Partial",
                requested = "Requested",
                unplayedCountFormat = "%d unplayed",
            )
        val GERMAN =
            TvCardBadgeStrings(
                partial = "Teilweise",
                requested = "Angefragt",
                unplayedCountFormat = "%d ungesehen",
            )
    }
}
