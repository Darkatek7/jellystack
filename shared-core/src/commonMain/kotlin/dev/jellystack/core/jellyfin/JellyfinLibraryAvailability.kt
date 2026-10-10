package dev.jellystack.core.jellyfin

/**
 * Whether the library can be browsed as a media library. Live TV needs a channel guide and a channel
 * player, which do not exist yet, so its library would only open an empty or broken grid.
 */
fun JellyfinLibrary.isBrowsable(): Boolean = !collectionType.equals(LIVE_TV_COLLECTION_TYPE, ignoreCase = true)

/** The same home state without libraries that cannot be browsed. */
fun JellyfinHomeState.withBrowsableLibraries(): JellyfinHomeState =
    if (libraries.all(JellyfinLibrary::isBrowsable)) this else copy(libraries = libraries.filter(JellyfinLibrary::isBrowsable))

private const val LIVE_TV_COLLECTION_TYPE = "livetv"
