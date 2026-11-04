package dev.jellystack.design.tv

/** Live TV copy. Kept apart from [TvStrings], whose constructor is at the JVM argument limit. */
internal data class TvLiveTvStrings(
    val title: String,
    /** The label in the player where other videos have a seek bar. */
    val live: String,
    val now: String,
    val next: String,
    val noProgramInfo: String,
    val loading: String,
    val emptyTitle: String,
    val emptyHint: String,
    val failedTitle: String,
    val failedHint: String,
    /** The header button that switches to the guide grid. */
    val guide: String,
    /** The header button that switches back to the channel list. */
    val channelList: String,
    private val timeRangeFormat: String,
    private val startsAtFormat: String,
    private val channelFormat: String,
) {
    fun timeRange(
        start: String,
        end: String,
    ): String = timeRangeFormat.format(start, end)

    fun startsAt(start: String): String = startsAtFormat.format(start)

    /** What a screen reader says for a channel row: "Channel 12, Example One, Example News". */
    fun channelDescription(
        number: String?,
        name: String,
        program: String?,
    ): String = listOfNotNull(number?.let { channelFormat.format(it) }, name, program).joinToString(", ")

    companion object {
        val ENGLISH =
            TvLiveTvStrings(
                title = "Live TV",
                live = "LIVE",
                now = "Now",
                next = "Next",
                noProgramInfo = "No program information",
                loading = "Loading channels",
                emptyTitle = "No channels",
                emptyHint = "This server has no Live TV channels for this profile.",
                failedTitle = "Live TV could not be loaded",
                failedHint = "Check your server, then try again.",
                guide = "Guide",
                channelList = "Channel list",
                timeRangeFormat = "%s – %s",
                startsAtFormat = "Starts at %s",
                channelFormat = "Channel %s",
            )

        val GERMAN =
            TvLiveTvStrings(
                title = "Live-TV",
                live = "LIVE",
                now = "Jetzt",
                next = "Danach",
                noProgramInfo = "Keine Programminformationen",
                loading = "Sender werden geladen",
                emptyTitle = "Keine Sender",
                emptyHint = "Dieser Server hat für dieses Profil keine Live-TV-Sender.",
                failedTitle = "Live-TV konnte nicht geladen werden",
                failedHint = "Prüfe deinen Server und versuche es erneut.",
                guide = "Programmführer",
                channelList = "Senderliste",
                timeRangeFormat = "%s – %s",
                startsAtFormat = "Beginnt um %s",
                channelFormat = "Sender %s",
            )
    }
}
