package dev.jellystack.design.preview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinItemDetail
import dev.jellystack.core.jellyfin.JellyfinLibrary
import dev.jellystack.core.jellyfin.JellyfinMediaSource
import dev.jellystack.core.jellyfin.JellyfinMediaStream
import dev.jellystack.core.jellyfin.JellyfinMediaStreamType
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfileOption
import dev.jellystack.core.jellyseerr.JellyseerrLanguageProfiles
import dev.jellystack.core.jellyseerr.JellyseerrMediaAvailability
import dev.jellystack.core.jellyseerr.JellyseerrMediaStatus
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRail
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationRailState
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestCounts
import dev.jellystack.core.jellyseerr.JellyseerrRequestFilter
import dev.jellystack.core.jellyseerr.JellyseerrRequestStatus
import dev.jellystack.core.jellyseerr.JellyseerrRequestSummary
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.jellyseerr.JellyseerrSeasonStatus
import dev.jellystack.core.jellyseerr.JellyseerrUser
import dev.jellystack.core.preferences.ThemeMode
import dev.jellystack.core.preferences.TutorialStep
import dev.jellystack.core.security.BiometricCapability
import dev.jellystack.core.security.BiometricLockState
import dev.jellystack.core.server.ServerType
import dev.jellystack.design.ServerFormState
import dev.jellystack.design.onboarding.OnboardingField
import dev.jellystack.design.onboarding.OnboardingUiState
import dev.jellystack.design.onboarding.OnboardingValidationError
import dev.jellystack.design.onboarding.onboardingProgress
import dev.jellystack.design.settings.SettingsConnectionHealth
import dev.jellystack.design.settings.SettingsConnectionUi
import dev.jellystack.design.settings.SettingsSection
import dev.jellystack.design.settings.SettingsUiState
import kotlinx.datetime.Instant

internal enum class JellystackPreviewScenario(
    val fixtureName: String,
) {
    Home("home"),
    Library("library"),
    Discover("discover"),
    Requests("requests"),
    Settings("settings"),
    Onboarding("onboarding"),
    Detail("detail"),
}

internal object JellystackPreviewData {
    val acceptedNames: Set<String> = JellystackPreviewScenario.entries.mapTo(linkedSetOf()) { it.fixtureName }
    val fixedClock: Instant = Instant.parse("2026-07-12T12:00:00Z")

    fun scenario(name: String): JellystackPreviewScenario =
        JellystackPreviewScenario.entries.firstOrNull { it.fixtureName == name }
            ?: throw IllegalArgumentException("Unknown Jellystack preview fixture: $name")

    private val movies = JellyfinLibrary("library-movies", "Cinema", "movies", 42, null)
    private val shows = JellyfinLibrary("library-shows", "Series", "tvshows", 28, null)

    private val horizon =
        item("movie-horizon", "Neon Horizon", "Movie", year = 2026, overview = "A pilot follows a signal beyond the mapped solar system.")
    private val harbor =
        item("movie-harbor", "The Quiet Harbor", "Movie", year = 2025, overview = "A coastal mystery returns with the tide.")
    private val atlas =
        item("series-atlas", "Atlas Station", "Series", year = 2026, overview = "A small crew keeps a distant research station alive.")
    private val episode =
        item(
            id = "episode-atlas-203",
            name = "The Long Signal",
            type = "Episode",
            year = 2026,
            overview = "The crew receives a message that should not exist.",
            seriesId = atlas.id,
            seriesName = atlas.name,
            season = 2,
            episode = 3,
            playedPercentage = 38.0,
        )

    val homeState =
        JellyfinHomeState(
            libraries = listOf(movies, shows),
            continueWatching = listOf(episode, harbor),
            nextUp = listOf(episode.copy(id = "episode-atlas-204", name = "False Dawn", indexNumber = 4, playedPercentage = 0.0)),
            recentShows = listOf(atlas),
            recentMovies = listOf(horizon, harbor),
            selectedLibraryId = movies.id,
            libraryItems = listOf(horizon, harbor, atlas, episode),
            currentPage = 1,
            endReached = true,
            totalLibraryItemCount = 4,
            favorites = setOf(harbor.id),
        )

    val detailItem: JellyfinItem = horizon
    val detail =
        JellyfinItemDetail(
            id = horizon.id,
            name = horizon.name,
            overview = horizon.overview,
            taglines = listOf("The edge of the map is only the beginning."),
            runTimeTicks = 7_080_000_000L,
            productionYear = 2026,
            premiereDate = "2026-06-18",
            communityRating = 8.4,
            officialRating = "PG-13",
            genres = listOf("Science fiction", "Adventure"),
            studios = listOf("Example Pictures"),
            primaryImageTag = null,
            backdropImageTags = emptyList(),
            mediaSources =
                listOf(
                    JellyfinMediaSource(
                        id = "source-horizon",
                        name = "4K HDR",
                        runTimeTicks = 7_080_000_000L,
                        container = "mkv",
                        videoBitrate = 18_000_000,
                        supportsDirectPlay = true,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                        streams =
                            listOf(
                                JellyfinMediaStream(JellyfinMediaStreamType.VIDEO, 0, "2160p HEVC", "hevc", null, true, false),
                                JellyfinMediaStream(JellyfinMediaStreamType.AUDIO, 1, "English 5.1", "eac3", "eng", true, false),
                                JellyfinMediaStream(JellyfinMediaStreamType.SUBTITLE, 2, "Deutsch", "srt", "deu", false, false),
                            ),
                    ),
                ),
            isFavorite = true,
        )

    private val availability = JellyseerrMediaAvailability(JellyseerrMediaStatus.AVAILABLE, JellyseerrMediaStatus.UNKNOWN)
    private val pendingAvailability = JellyseerrMediaAvailability(JellyseerrMediaStatus.PROCESSING, JellyseerrMediaStatus.UNKNOWN)
    private val searchItems =
        listOf(
            searchItem(8101, "Orbit City", JellyseerrMediaType.MOVIE, "2026", availability),
            searchItem(8102, "Northstar", JellyseerrMediaType.TV, "2025", pendingAvailability),
            searchItem(8103, "Paper Moons", JellyseerrMediaType.MOVIE, "2024", availability),
        )
    val requestSelectionItem: JellyseerrSearchItem = searchItems.first()

    val recommendationsState =
        JellyseerrRecommendationsState.Ready(
            JellyseerrRecommendationRail.entries.associateWith { rail ->
                JellyseerrRecommendationRailState(
                    rail = rail,
                    items = searchItems.mapIndexed { index, item -> item.copy(tmdbId = rail.ordinal * 100 + index + 1) },
                    isLoading = false,
                    errorMessage = null,
                    canLoadMore = false,
                    nextPage = 2,
                    lastUpdated = fixedClock,
                    isStale = false,
                )
            },
        )

    private val requester = JellyseerrUser(7, "Alex Example", "alex", 2)
    private val requestItems =
        listOf(
            request(71, 8102, "Northstar", JellyseerrMediaType.TV, JellyseerrRequestStatus.APPROVED, pendingAvailability),
            request(72, 8103, "Paper Moons", JellyseerrMediaType.MOVIE, JellyseerrRequestStatus.PENDING, availability),
        )
    val languageProfiles =
        JellyseerrLanguageProfiles(
            movies = listOf(JellyseerrLanguageProfileOption(1, "Original + subtitles", 2, "Movies", false, true, 3)),
            tv = listOf(JellyseerrLanguageProfileOption(2, "German + original", 4, "Series", false, true, 5)),
        )
    val requestsState =
        JellyseerrRequestsState.Ready(
            filter = JellyseerrRequestFilter.ALL,
            requests = requestItems,
            counts = JellyseerrRequestCounts(total = 2, movie = 1, tv = 1, pending = 1, approved = 1, processing = 1),
            query = "",
            searchResults = searchItems,
            isSearching = false,
            isRefreshing = false,
            isPerformingAction = false,
            message = null,
            isAdmin = true,
            lastUpdated = fixedClock,
            languageProfiles = languageProfiles,
        )

    val settingsState =
        SettingsUiState(
            selectedSection = SettingsSection.Connections,
            themeMode = ThemeMode.SYSTEM,
            appLockEnabled = true,
            appLockState = BiometricLockState.Unlocked,
            appLockCapability = BiometricCapability(BiometricCapability.Status.AVAILABLE, secureCredentialAvailable = true),
            connections =
                listOf(
                    SettingsConnectionUi("server-jellyfin", ServerType.JELLYFIN, "Living Room", true, SettingsConnectionHealth.Ready),
                    SettingsConnectionUi("server-seerr", ServerType.JELLYSEERR, "Requests", true, SettingsConnectionHealth.NeedsAttention),
                ),
            appVersion = "0.14.2",
        )

    val onboardingState =
        OnboardingUiState(
            step = TutorialStep.ConnectJellyfin,
            progress = onboardingProgress(TutorialStep.ConnectJellyfin),
            form = ServerFormState(name = "Home", baseUrl = "example.invalid", username = "alex"),
            fieldErrors =
                mapOf(
                    OnboardingField.Url to OnboardingValidationError.InvalidUrl,
                    OnboardingField.Password to OnboardingValidationError.Required,
                ),
            manualSeerrCredentialsRequired = false,
            isSaving = false,
            serviceErrorDetail = null,
            canStartExploring = false,
        )

    private fun searchItem(
        id: Int,
        title: String,
        type: JellyseerrMediaType,
        year: String,
        availability: JellyseerrMediaAvailability,
    ) = JellyseerrSearchItem(
        id,
        type,
        title,
        "Deterministic preview synopsis for $title.",
        year,
        null,
        null,
        id + 1000,
        null,
        availability,
        emptyList(),
    )

    private fun request(
        id: Int,
        tmdbId: Int,
        title: String,
        type: JellyseerrMediaType,
        status: JellyseerrRequestStatus,
        availability: JellyseerrMediaAvailability,
    ) = JellyseerrRequestSummary(
        id = id,
        mediaId = id + 100,
        tmdbId = tmdbId,
        tvdbId = null,
        title = title,
        originalTitle = title,
        mediaType = type,
        requestStatus = status,
        availability = availability,
        is4k = false,
        canRemoveFromService = true,
        createdAt = fixedClock,
        updatedAt = fixedClock,
        requestedBy = requester,
        profileName = "Default",
        seasons = if (type == JellyseerrMediaType.TV) listOf(JellyseerrSeasonStatus(1, status)) else emptyList(),
        posterPath = null,
        backdropPath = null,
    )

    @Suppress("LongParameterList")
    private fun item(
        id: String,
        name: String,
        type: String,
        year: Int,
        overview: String,
        seriesId: String? = null,
        seriesName: String? = null,
        season: Int? = null,
        episode: Int? = null,
        playedPercentage: Double? = null,
    ) = JellyfinItem(
        id = id,
        libraryId = if (type == "Movie") movies.id else shows.id,
        name = name,
        sortName = name,
        overview = overview,
        type = type,
        mediaType = "Video",
        locationType = "FileSystem",
        taglines = emptyList(),
        parentId = null,
        primaryImageTag = null,
        thumbImageTag = null,
        backdropImageTag = null,
        seriesId = seriesId,
        seriesPrimaryImageTag = null,
        seriesThumbImageTag = null,
        seriesBackdropImageTag = null,
        parentLogoImageTag = null,
        runTimeTicks = 3_000_000_000L,
        positionTicks = playedPercentage?.let { (3_000_000_000L * it / 100.0).toLong() },
        playedPercentage = playedPercentage,
        productionYear = year,
        premiereDate = "$year-01-01",
        communityRating = 8.1,
        officialRating = "PG",
        indexNumber = episode,
        parentIndexNumber = season,
        seriesName = seriesName,
        seasonId = season?.let { "season-$it" },
        episodeTitle = if (type == "Episode") name else null,
        lastPlayed = fixedClock.toString(),
        dateCreated = fixedClock.toString(),
    )
}

internal object BrightArtworkPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = drawRect(Color(0xFFFFC857))
}

internal object DarkArtworkPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = drawRect(Color(0xFF10223A))
}

internal object SplitLuminanceArtworkPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRect(Color(0xFFFFE3A1), size = Size(size.width / 2f, size.height))
        drawRect(Color(0xFF172033), topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
    }
}
