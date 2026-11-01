package dev.jellystack.core.jellyfin

/** Whether the library is the server's Live TV entry, which holds channels, not titles. */
fun JellyfinLibrary.isLiveTv(): Boolean = collectionType.equals(LIVE_TV_COLLECTION_TYPE, ignoreCase = true)

/**
 * Whether the library can be browsed as a media library. Live TV needs a channel guide and a channel player, so a UI
 * without one (the phone app) filters it out; the TV app has them and routes the Live TV library to its own screen.
 */
fun JellyfinLibrary.isBrowsable(): Boolean = !isLiveTv()

/** The same home state without libraries that cannot be browsed. */
fun JellyfinHomeState.withBrowsableLibraries(): JellyfinHomeState =
    if (libraries.all(JellyfinLibrary::isBrowsable)) this else copy(libraries = libraries.filter(JellyfinLibrary::isBrowsable))

private const val LIVE_TV_COLLECTION_TYPE = "livetv"
