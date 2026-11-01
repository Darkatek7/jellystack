package dev.jellystack.design.tv

/** Names of the library media types, for the type filter. */
internal data class TvMediaTypeStrings(
    val movie: String,
    val series: String,
    val episode: String,
    val musicVideo: String,
    val audio: String,
)

/** Titles and hints of the empty states of lists and grids. */
internal data class TvEmptyStateStrings(
    val nothingYet: String,
    val noLibraries: String,
    val librariesHint: String,
    val libraryHint: String,
    val discoverHint: String,
    val searchHint: String,
    val filterHint: String,
)

internal val englishMediaTypeStrings =
    TvMediaTypeStrings(
        movie = "Movies",
        series = "Series",
        episode = "Episodes",
        musicVideo = "Music videos",
        audio = "Music",
    )

internal val germanMediaTypeStrings =
    TvMediaTypeStrings(
        movie = "Filme",
        series = "Serien",
        episode = "Folgen",
        musicVideo = "Musikvideos",
        audio = "Musik",
    )

internal val englishEmptyStateStrings =
    TvEmptyStateStrings(
        nothingYet = "Nothing here yet",
        noLibraries = "No libraries",
        librariesHint = "No libraries are available for this profile. Check your server, then try again.",
        libraryHint = "This library has no titles to show yet.",
        discoverHint = "No recommendations are available right now. Try again in a moment.",
        searchHint = "Check the spelling or try another title, actor or genre.",
        filterHint = "Try different filters.",
    )

internal val germanEmptyStateStrings =
    TvEmptyStateStrings(
        nothingYet = "Hier ist noch nichts",
        noLibraries = "Keine Bibliotheken",
        librariesHint = "Für dieses Profil gibt es keine Bibliotheken. Prüfe deinen Server und versuche es erneut.",
        libraryHint = "Diese Bibliothek enthält noch keine Titel.",
        discoverHint = "Derzeit sind keine Empfehlungen verfügbar. Versuche es gleich noch einmal.",
        searchHint = "Prüfe die Schreibweise oder suche nach einem anderen Titel, Schauspieler oder Genre.",
        filterHint = "Probiere andere Filter.",
    )
