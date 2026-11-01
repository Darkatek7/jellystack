package dev.jellystack.core.jellyfin

/**
 * A collection (BoxSet), a playlist, an album, or an artist: it lists other titles instead of having a detail page of
 * its own.
 */
fun JellyfinItem.opensAsItemList(): Boolean = isPlaylist() || type.equals("BoxSet", ignoreCase = true) || isMusicContainer()

fun JellyfinItem.isMusicAlbum(): Boolean = type.equals("MusicAlbum", ignoreCase = true)

fun JellyfinItem.isMusicArtist(): Boolean = type.equals("MusicArtist", ignoreCase = true)

/** An album or an artist. */
fun JellyfinItem.isMusicContainer(): Boolean = isMusicAlbum() || isMusicArtist()

/** A single music track (or audiobook) the player can start. Missing files are not playable. */
fun JellyfinItem.isPlayableAudio(): Boolean =
    (type.equals("Audio", ignoreCase = true) || type.equals("AudioBook", ignoreCase = true)) &&
        !locationType.equals("Virtual", ignoreCase = true)

fun JellyfinItem.isPlaylist(): Boolean = type.equals("Playlist", ignoreCase = true)

/** A series or season: playing it means playing one of its episodes. */
fun JellyfinItem.isEpisodeParent(): Boolean = type.equals("Series", ignoreCase = true) || type.equals("Season", ignoreCase = true)

/** A single video the player can start. Missing files (listed as virtual by the server) are not playable. */
fun JellyfinItem.isPlayableVideo(): Boolean = type.lowercase() in PLAYABLE_VIDEO_TYPES && !locationType.equals("Virtual", ignoreCase = true)

private val PLAYABLE_VIDEO_TYPES = setOf("movie", "episode", "video", "musicvideo")
