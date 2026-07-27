package dev.jellystack.core.tmdb

private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

enum class TmdbPosterSize(
    val pathSegment: String,
) {
    W154("w154"),
    W342("w342"),
    W500("w500"),
    ORIGINAL("original"),
}

fun tmdbPosterUrl(
    path: String?,
    size: TmdbPosterSize = TmdbPosterSize.W342,
): String? {
    if (path.isNullOrBlank()) {
        return null
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
        return path
    }
    val normalizedPath = if (path.startsWith('/')) path else "/$path"
    return TMDB_IMAGE_BASE_URL + size.pathSegment + normalizedPath
}
