package dev.jellystack.core.jellyseerr

/** Reuse only an identity established for this exact request ID/type in the current server environment. */
internal fun JellyseerrRequestSummary.withVerifiedMediaIdentity(
    known: Map<Pair<JellyseerrMediaType, Int>, JellyseerrRequestSummary>,
): JellyseerrRequestSummary {
    if (tmdbId != null) return this
    val entry = known.entries.firstOrNull { it.value.id == id && it.key.first == mediaType } ?: return this
    return copy(tmdbId = entry.key.second)
}
