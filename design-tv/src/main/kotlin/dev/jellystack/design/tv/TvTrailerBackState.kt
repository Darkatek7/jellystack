package dev.jellystack.design.tv

/**
 * What the app-level Back handler needs to know about preview trailers: whether one plays on the current screen and
 * how to stop it. The first Back press only stops the trailer; the normal Back walk continues with the next one.
 */
internal class TvTrailerBackState(
    private val isPlaying: () -> Boolean = { false },
    private val stopTrailer: () -> Unit = {},
) {
    val playing: Boolean get() = isPlaying()

    fun stop() = stopTrailer()
}
