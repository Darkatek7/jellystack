package dev.jellystack.design.tv

/**
 * Copy of the detail screens. Kept apart from [TvStrings], whose constructor is at the JVM argument limit; use
 * [TvStrings.detailScreen] to get the group of the UI language.
 */
internal data class TvDetailStrings(
    val startOver: String,
    val playEpisodeFormat: String,
    val continueEpisodeFormat: String,
    val continueRemainingFormat: String,
    val runtimeHoursMinutesFormat: String,
    val runtimeMinutesFormat: String,
    val unplayedFormat: String,
    val moreFromSeasonFormat: String,
    val castNotInLibrary: String,
    val goToSeries: String,
    val playbackOptions: String,
    val audioLabel: String,
    val subtitlesLabel: String,
    val versionLabel: String,
    val trackLineFormat: String,
    val directors: String,
    val writers: String,
    val studios: String,
    val audioLanguages: String,
    val subtitleLanguages: String,
    val genres: String,
    val ratings: String,
    val episodeShortFormat: String,
    val episodeRuntimeFormat: String,
    val availability: String,
) {
    companion object {
        val ENGLISH =
            TvDetailStrings(
                startOver = "Start over",
                playEpisodeFormat = "Play S%1\$d E%2\$d",
                continueEpisodeFormat = "Continue S%1\$d E%2\$d",
                continueRemainingFormat = "Continue · %s",
                runtimeHoursMinutesFormat = "%1\$dh %2\$dm",
                runtimeMinutesFormat = "%dm",
                unplayedFormat = "%d unplayed",
                moreFromSeasonFormat = "More from season %d",
                castNotInLibrary = "Not in your library",
                goToSeries = "Go to series",
                playbackOptions = "Playback options",
                audioLabel = "Audio",
                subtitlesLabel = "Subtitles",
                versionLabel = "Version",
                trackLineFormat = "%1\$s: %2\$s",
                directors = "Director",
                writers = "Writers",
                studios = "Studios",
                audioLanguages = "Audio languages",
                subtitleLanguages = "Subtitle languages",
                genres = "Genres",
                ratings = "Ratings",
                episodeShortFormat = "E%d",
                episodeRuntimeFormat = "%1\$s · %2\$s",
                availability = "Availability",
            )

        val GERMAN =
            TvDetailStrings(
                startOver = "Von vorn",
                playEpisodeFormat = "S%1\$d F%2\$d abspielen",
                continueEpisodeFormat = "S%1\$d F%2\$d fortsetzen",
                continueRemainingFormat = "Fortsetzen · %s",
                runtimeHoursMinutesFormat = "%1\$d Std %2\$d Min",
                runtimeMinutesFormat = "%d Min",
                unplayedFormat = "%d ungesehen",
                moreFromSeasonFormat = "Mehr aus Staffel %d",
                castNotInLibrary = "Nicht in deiner Bibliothek",
                goToSeries = "Zur Serie",
                playbackOptions = "Wiedergabe-Optionen",
                audioLabel = "Ton",
                subtitlesLabel = "Untertitel",
                versionLabel = "Version",
                trackLineFormat = "%1\$s: %2\$s",
                directors = "Regie",
                writers = "Drehbuch",
                studios = "Studios",
                audioLanguages = "Tonspuren",
                subtitleLanguages = "Untertitel",
                genres = "Genres",
                ratings = "Bewertungen",
                episodeShortFormat = "F%d",
                episodeRuntimeFormat = "%1\$s · %2\$s",
                availability = "Verfügbarkeit",
            )
    }
}

/** The detail copy in the language of these strings. */
internal val TvStrings.detailScreen: TvDetailStrings
    get() = if (locale.language == "de") TvDetailStrings.GERMAN else TvDetailStrings.ENGLISH
