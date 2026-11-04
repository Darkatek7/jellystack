package dev.jellystack.design.tv

/** Labels of the library screens: header buttons, the filter panel, the A-Z rail, and the landing page rows. */
internal data class TvLibraryStrings(
    val filter: String,
    val filterPanelTitle: String,
    val sortBy: String,
    val direction: String,
    val watchedState: String,
    val decade: String,
    val any: String,
    val jumpToLetterFormat: String,
    val recentlyAddedInFormat: String,
    val decadeFormat: String,
) {
    fun jumpToLetter(letter: String): String = jumpToLetterFormat.format(letter)

    fun recentlyAddedIn(library: String): String = recentlyAddedInFormat.format(library)

    fun decadeLabel(decade: Int): String = decadeFormat.format(decade)
}

internal val englishLibraryStrings =
    TvLibraryStrings(
        filter = "Filter",
        filterPanelTitle = "Sort and filter",
        sortBy = "Sort by",
        direction = "Direction",
        watchedState = "Watched",
        decade = "Decade",
        any = "All",
        jumpToLetterFormat = "Jump to %s",
        recentlyAddedInFormat = "Recently added in %s",
        decadeFormat = "%ds",
    )

internal val germanLibraryStrings =
    TvLibraryStrings(
        filter = "Filter",
        filterPanelTitle = "Sortieren und filtern",
        sortBy = "Sortieren nach",
        direction = "Reihenfolge",
        watchedState = "Gesehen",
        decade = "Jahrzehnt",
        any = "Alle",
        jumpToLetterFormat = "Springe zu %s",
        recentlyAddedInFormat = "Neu hinzugefügt in %s",
        decadeFormat = "%der",
    )
