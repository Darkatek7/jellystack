package dev.jellystack.design.tv

import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.profile.ActiveProfileRepository
import dev.jellystack.core.profile.ProfilePinRepository
import dev.jellystack.core.profile.ProfilePinState
import kotlinx.coroutines.CancellationException

/**
 * The slides the system screensaver may show. Reads the local cache only: no refresh, no sign-in, no request to the
 * server (the images load afterwards through the shared image loader). It yields nothing, so the screensaver stays
 * dark, when no profile has been picked (the picker clears the active profile), when the active profile is protected
 * by a PIN (a cold start locks those again, and a screensaver must not reveal what the lock hides), when the cache
 * is empty, or on any failure.
 */
class TvScreensaverSource internal constructor(
    private val activeProfileId: () -> String?,
    private val isPinProtected: suspend (profileId: String) -> Boolean,
    private val cachedItems: suspend () -> List<JellyfinItem>,
    private val connection: suspend () -> TvScreensaverConnection,
) {
    constructor(
        browse: JellyfinBrowseRepository,
        activeProfiles: ActiveProfileRepository,
        pins: ProfilePinRepository,
    ) : this(
        activeProfileId = { activeProfiles.profileId.value },
        isPinProtected = { pins.state(it) !is ProfilePinState.NotConfigured },
        cachedItems = {
            browse.cachedRecentMovies(libraryId = null, limit = SOURCE_ITEMS_PER_KIND) +
                browse.cachedRecentShows(libraryId = null, limit = SOURCE_ITEMS_PER_KIND)
        },
        connection = { TvScreensaverConnection(browse.currentServerBaseUrl(), browse.currentAccessToken()) },
    )

    suspend fun load(): List<TvScreensaverSlide> {
        val profileId = activeProfileId() ?: return emptyList()
        return try {
            if (isPinProtected(profileId)) {
                emptyList()
            } else {
                val connection = connection()
                tvScreensaverSlides(cachedItems(), connection.baseUrl, connection.token)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Fail closed: a dark screen is better than a crash or a half-checked profile.
            JellystackLog.w("Screensaver source failed")
            emptyList()
        }
    }

    private companion object {
        const val SOURCE_ITEMS_PER_KIND = 40
    }
}

internal class TvScreensaverConnection(
    val baseUrl: String?,
    val token: String?,
)
