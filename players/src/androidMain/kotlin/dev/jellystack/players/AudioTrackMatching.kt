package dev.jellystack.players

/** One audio track the player exposes, in container order, with its language already normalized. */
internal data class AudioTrackCandidate(
    val formatId: String?,
    val language: String?,
    val label: String?,
    val codecMatches: Boolean,
    val isSupported: Boolean,
)

/**
 * Picks the player track for [track] and returns its position in [candidates].
 *
 * Container ids are only Jellyfin stream indexes for HLS. Direct and local files expose container track
 * numbers instead (Matroska and MP4 count from 1, including the video track), so an index hit there names
 * a different track; those sources are matched by their position among the audio tracks, which Jellyfin
 * and the container list in the same order.
 */
internal fun matchAudioTrackCandidate(
    candidates: List<AudioTrackCandidate>,
    track: AudioTrack,
    trackLanguage: String?,
    idsAreStreamIndexes: Boolean,
    serverSelectedAudioStreamIndex: Int?,
): Int? {
    val supported = candidates.indices.filter { candidates[it].isSupported }
    val primaryTrackLanguage = trackLanguage?.let(::primaryLanguage)

    fun languageMatches(index: Int): Boolean =
        primaryTrackLanguage != null && candidates[index].language?.let(::primaryLanguage) == primaryTrackLanguage

    fun languageAgrees(index: Int): Boolean = primaryTrackLanguage == null || candidates[index].language == null || languageMatches(index)

    val idMatch =
        if (idsAreStreamIndexes) {
            supported.firstOrNull { index ->
                val id = candidates[index].formatId
                formatIdMatches(id, track.id) ||
                    (track.streamIndex != null && formatIdMatches(id, track.streamIndex.toString()))
            }
        } else {
            null
        }
    val positionMatch =
        track.audioIndex?.takeIf { position ->
            position in candidates.indices && candidates[position].isSupported && languageAgrees(position)
        }
    return idMatch
        ?: positionMatch
        ?: supported.firstOrNull { index ->
            languageMatches(index) &&
                (track.title == null || candidates[index].label?.equals(track.title, ignoreCase = true) == true) &&
                candidates[index].codecMatches
        } ?: supported.filter(::languageMatches).singleOrNull()
        // The server transcoded exactly the requested stream into the HLS source, so its only audio
        // rendition is the track even though it carries none of the original stream's metadata.
        ?: supported.singleOrNull()?.takeIf {
            track.streamIndex != null && track.streamIndex == serverSelectedAudioStreamIndex
        }
}

// Containers may carry a region ("en-US") that Jellyfin's ISO 639-2 code never has.
private fun primaryLanguage(language: String): String = language.substringBefore('-').lowercase()
