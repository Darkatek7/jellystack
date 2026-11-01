package dev.jellystack.design.tv

/** Album, artist and now-playing copy. Kept apart from [TvStrings], whose constructor is at the JVM argument limit. */
internal data class TvMusicStrings(
    val shuffle: String,
    val albums: String,
    val album: String,
    val artist: String,
    val nowPlaying: String,
    val previousTrack: String,
    val nextTrack: String,
    private val trackOne: String,
    private val trackMany: String,
    private val discFormat: String,
) {
    fun trackCount(count: Int): String = (if (count == 1) trackOne else trackMany).format(count)

    fun disc(number: Int): String = discFormat.format(number)

    /** What a screen reader says for a row: "3. Title 3:42". */
    fun trackDescription(
        number: Int?,
        title: String,
        duration: String?,
    ): String = listOfNotNull(number?.let { "$it." }, title, duration).joinToString(" ")

    companion object {
        val ENGLISH =
            TvMusicStrings(
                shuffle = "Shuffle",
                albums = "Albums",
                album = "Album",
                artist = "Artist",
                nowPlaying = "Now playing",
                previousTrack = "Previous track",
                nextTrack = "Next track",
                trackOne = "%d track",
                trackMany = "%d tracks",
                discFormat = "Disc %d",
            )

        val GERMAN =
            TvMusicStrings(
                shuffle = "Zufällig",
                albums = "Alben",
                album = "Album",
                artist = "Interpret",
                nowPlaying = "Aktuelle Wiedergabe",
                previousTrack = "Vorheriger Titel",
                nextTrack = "Nächster Titel",
                trackOne = "%d Titel",
                trackMany = "%d Titel",
                discFormat = "CD %d",
            )
    }
}
