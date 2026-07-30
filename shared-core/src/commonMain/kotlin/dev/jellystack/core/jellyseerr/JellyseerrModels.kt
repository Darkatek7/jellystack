package dev.jellystack.core.jellyseerr

import kotlinx.datetime.Instant

data class JellyseerrEnvironment(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val apiKey: String?,
    val sessionCookie: String?,
    val apiUserId: Int? = null,
)

enum class JellyseerrMediaType {
    MOVIE,
    TV,
    PERSON,
    COLLECTION,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): JellyseerrMediaType =
            when (value?.lowercase()) {
                "movie" -> MOVIE
                "tv" -> TV
                "person" -> PERSON
                "collection" -> COLLECTION
                else -> UNKNOWN
            }
    }
}

enum class JellyseerrRecommendationRail {
    TRENDS,
    POPULAR_MOVIES,
    POPULAR_SHOWS,
    UPCOMING_MOVIES,
    UPCOMING_SHOWS,
}

enum class JellyseerrRequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    FAILED,
    COMPLETED,
    UNKNOWN,
    ;

    companion object {
        fun from(value: Int?): JellyseerrRequestStatus =
            when (value) {
                1 -> PENDING
                2 -> APPROVED
                3 -> DECLINED
                4 -> FAILED
                5 -> COMPLETED
                else -> UNKNOWN
            }
    }
}

enum class JellyseerrMediaStatus {
    UNKNOWN,
    PENDING,
    PROCESSING,
    PARTIALLY_AVAILABLE,
    AVAILABLE,
    BLACKLISTED,
    DELETED,
    ;

    companion object {
        fun from(value: Int?): JellyseerrMediaStatus =
            when (value) {
                1 -> UNKNOWN
                2 -> PENDING
                3 -> PROCESSING
                4 -> PARTIALLY_AVAILABLE
                5 -> AVAILABLE
                6 -> BLACKLISTED
                7 -> DELETED
                else -> UNKNOWN
            }
    }
}

data class JellyseerrUser(
    val id: Int?,
    val displayName: String?,
    val username: String?,
    val permissions: Int?,
)

data class JellyseerrSeasonStatus(
    val seasonNumber: Int,
    val status: JellyseerrRequestStatus,
)

data class JellyseerrMediaAvailability(
    val standard: JellyseerrMediaStatus?,
    val `4k`: JellyseerrMediaStatus?,
) {
    val isAvailable: Boolean
        get() = standard == JellyseerrMediaStatus.AVAILABLE
}

data class JellyseerrRequestSummary(
    val id: Int,
    val mediaId: Int?,
    val tmdbId: Int?,
    val tvdbId: Int?,
    val title: String?,
    val originalTitle: String?,
    val mediaType: JellyseerrMediaType,
    val requestStatus: JellyseerrRequestStatus,
    val availability: JellyseerrMediaAvailability,
    val is4k: Boolean,
    val canRemoveFromService: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val requestedBy: JellyseerrUser?,
    val profileName: String?,
    val seasons: List<JellyseerrSeasonStatus>,
    val posterPath: String?,
    val backdropPath: String?,
)

data class JellyseerrRequestCounts(
    val total: Int = 0,
    val movie: Int = 0,
    val tv: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val declined: Int = 0,
    val processing: Int = 0,
    val available: Int = 0,
    val completed: Int = 0,
)

data class JellyseerrRequestsPage(
    val page: Int,
    val pageSize: Int,
    val totalResults: Int,
    val totalPages: Int,
    val results: List<JellyseerrRequestSummary>,
)

data class JellyseerrSearchItem(
    val tmdbId: Int,
    val mediaType: JellyseerrMediaType,
    val title: String,
    val overview: String?,
    val releaseYear: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaInfoId: Int?,
    val tvdbId: Int?,
    val availability: JellyseerrMediaAvailability,
    val requests: List<JellyseerrRequestSummary>,
) {
    val isRequested: Boolean
        get() = requests.isNotEmpty()
}

data class JellyseerrRecommendationPage(
    val page: Int,
    val totalPages: Int,
    val items: List<JellyseerrSearchItem>,
    val fetchedAt: Instant,
)

data class JellyseerrRecommendationRailState(
    val rail: JellyseerrRecommendationRail,
    val items: List<JellyseerrSearchItem>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val canLoadMore: Boolean,
    val nextPage: Int,
    val lastUpdated: Instant?,
    val isStale: Boolean,
)

data class JellyseerrMediaRatings(
    val tmdb: Double?,
    val imdb: Double?,
    val rottenTomatoesCritics: Double?,
    val rottenTomatoesAudience: Double?,
)

data class JellyseerrMediaTrailer(
    val name: String?,
    val site: String?,
    val type: String?,
    val key: String?,
    val url: String?,
)

data class JellyseerrPerson(
    val id: Int,
    val name: String,
    val character: String? = null,
    val job: String? = null,
    val department: String? = null,
    val profilePath: String? = null,
    val order: Int? = null,
)

data class JellyseerrSeason(
    val id: Int?,
    val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    val airDate: String?,
    val episodeCount: Int?,
    val posterPath: String?,
)

data class JellyseerrCollection(
    val id: Int,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
)

data class JellyseerrMediaVideo(
    val id: String?,
    val name: String?,
    val site: String?,
    val type: String?,
    val key: String,
    val url: String?,
    val official: Boolean,
    val publishedAt: String?,
    val size: Int? = null,
)

enum class JellyseerrDetailEnrichmentSection {
    RATINGS,
    SIMILAR,
    RECOMMENDATIONS,
}

data class JellyseerrMediaDetailEnrichment(
    val ratings: JellyseerrMediaRatings? = null,
    val similar: List<JellyseerrSearchItem> = emptyList(),
    val recommendations: List<JellyseerrSearchItem> = emptyList(),
    val failedSections: Set<JellyseerrDetailEnrichmentSection> = emptySet(),
)

data class JellyseerrMediaDetail(
    val tmdbId: Int,
    val mediaType: JellyseerrMediaType,
    val title: String,
    val year: String?,
    val overview: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val releaseDate: String?,
    val revenue: Long?,
    val originalLanguage: String?,
    val productionCountries: List<String>,
    val studios: List<String>,
    val ratings: JellyseerrMediaRatings?,
    val trailer: JellyseerrMediaTrailer?,
    val posterPath: String?,
    val backdropPath: String?,
    val jellyseerrUrl: String?,
    val jellyfinUrl: String?,
    val imdbId: String?,
    val tvdbId: Int?,
    val availableSeasons: List<Int> = emptyList(),
    val originalTitle: String? = null,
    val tagline: String? = null,
    val certification: String? = null,
    val status: String? = null,
    val budget: Long? = null,
    val languages: List<String> = emptyList(),
    val cast: List<JellyseerrPerson> = emptyList(),
    val crew: List<JellyseerrPerson> = emptyList(),
    val seasons: List<JellyseerrSeason> = emptyList(),
    val collection: JellyseerrCollection? = null,
    val keywords: List<String> = emptyList(),
    val videos: List<JellyseerrMediaVideo> = emptyList(),
    val enrichment: JellyseerrMediaDetailEnrichment = JellyseerrMediaDetailEnrichment(ratings = ratings),
)

sealed interface JellyseerrMediaDetailState {
    data object Loading : JellyseerrMediaDetailState

    data class Loaded(
        val detail: JellyseerrMediaDetail,
        val enrichmentLoadingSections: Set<JellyseerrDetailEnrichmentSection> = emptySet(),
    ) : JellyseerrMediaDetailState {
        val enrichmentLoading: Boolean
            get() = enrichmentLoadingSections.isNotEmpty()
    }

    data class Error(
        val message: String,
    ) : JellyseerrMediaDetailState
}

enum class JellyseerrRequestFilter(
    val queryValue: String?,
) {
    ALL(null),
    PENDING("pending"),
    APPROVED("approved"),
    PROCESSING("processing"),
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    FAILED("failed"),
    DELETED("deleted"),
}

sealed interface JellyseerrCreateSelection {
    data object AllSeasons : JellyseerrCreateSelection

    data class Seasons(
        val numbers: List<Int>,
    ) : JellyseerrCreateSelection
}

data class JellyseerrCreateRequest(
    val mediaId: Int,
    val tvdbId: Int?,
    val mediaType: JellyseerrMediaType,
    val is4k: Boolean = false,
    val seasons: JellyseerrCreateSelection? = null,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val languageProfileId: Int? = null,
    val title: String? = null,
)

data class JellyseerrLanguageProfileOption(
    val languageProfileId: Int?,
    val name: String,
    val serviceId: Int?,
    val serviceName: String?,
    val is4k: Boolean,
    val isDefault: Boolean,
    val profileId: Int?,
)

sealed interface JellyseerrRequestProfileSelection {
    data object ServerDefault : JellyseerrRequestProfileSelection

    data class Profile(
        val option: JellyseerrLanguageProfileOption,
    ) : JellyseerrRequestProfileSelection
}

enum class JellyseerrRequestVariant {
    STANDARD,
    FOUR_K,
}

data class JellyseerrLanguageProfiles(
    val movies: List<JellyseerrLanguageProfileOption>,
    val tv: List<JellyseerrLanguageProfileOption>,
) {
    companion object {
        val EMPTY = JellyseerrLanguageProfiles(emptyList(), emptyList())
    }
}

data class JellyseerrProfile(
    val id: Int,
    val displayName: String?,
    val permissions: Int,
) {
    fun canManageRequests(): Boolean = permissions.hasPermission(JellyseerrPermission.MANAGE_REQUESTS)

    fun requestCapabilities(): JellyseerrRequestCapabilities = JellyseerrRequestCapabilities.fromPermissions(permissions)
}

data class JellyseerrRequestCapabilities(
    val canRequestMovie: Boolean,
    val canRequestTv: Boolean,
    val canRequest4kMovie: Boolean,
    val canRequest4kTv: Boolean,
    val canUseAdvancedRequests: Boolean,
    val canManageRequests: Boolean,
) {
    fun canRequest(
        mediaType: JellyseerrMediaType,
        variant: JellyseerrRequestVariant = JellyseerrRequestVariant.STANDARD,
    ): Boolean =
        when (mediaType) {
            JellyseerrMediaType.MOVIE ->
                when (variant) {
                    JellyseerrRequestVariant.STANDARD -> canRequestMovie
                    JellyseerrRequestVariant.FOUR_K -> canRequest4kMovie
                }
            JellyseerrMediaType.TV ->
                when (variant) {
                    JellyseerrRequestVariant.STANDARD -> canRequestTv
                    JellyseerrRequestVariant.FOUR_K -> canRequest4kTv
                }
            else -> false
        }

    companion object {
        val NONE =
            JellyseerrRequestCapabilities(
                canRequestMovie = false,
                canRequestTv = false,
                canRequest4kMovie = false,
                canRequest4kTv = false,
                canUseAdvancedRequests = false,
                canManageRequests = false,
            )

        val ALL =
            JellyseerrRequestCapabilities(
                canRequestMovie = true,
                canRequestTv = true,
                canRequest4kMovie = true,
                canRequest4kTv = true,
                canUseAdvancedRequests = true,
                canManageRequests = true,
            )

        fun fromPermissions(permissions: Int?): JellyseerrRequestCapabilities {
            val isAdmin = permissions.hasPermission(JellyseerrPermission.ADMIN)
            val canManage = permissions.hasPermission(JellyseerrPermission.MANAGE_REQUESTS)
            return JellyseerrRequestCapabilities(
                canRequestMovie =
                    isAdmin ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST) ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_MOVIE),
                canRequestTv =
                    isAdmin ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST) ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_TV),
                canRequest4kMovie =
                    isAdmin ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_4K) ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_4K_MOVIE),
                canRequest4kTv =
                    isAdmin ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_4K) ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_4K_TV),
                canUseAdvancedRequests =
                    isAdmin ||
                        canManage ||
                        permissions.hasPermissionDirectly(JellyseerrPermission.REQUEST_ADVANCED),
                canManageRequests = isAdmin || canManage,
            )
        }
    }
}

enum class JellyseerrMessageKind { INFO, ERROR }

enum class JellyseerrMessageCode {
    SearchFailed,
    RequestSubmitted,
    RequestPermissionDenied,
    RequestDuplicate,
    RequestFailed,
    RequestRemoved,
    DeleteFailed,
    RequestApproved,
    ApprovalFailed,
    MediaIdMissing,
    MediaRequeued,
    MediaRequeueFailed,
    RemoveMediaFailed,
    RefreshFailed,
}

enum class JellyseerrMessageRecovery { None, RefreshRequests }

sealed interface JellyseerrOperationKey {
    data class Submit(
        val mediaType: JellyseerrMediaType,
        val tmdbId: Int,
    ) : JellyseerrOperationKey

    data class Request(
        val requestId: Int,
    ) : JellyseerrOperationKey
}

data class JellyseerrMessage(
    val id: Long,
    val kind: JellyseerrMessageKind,
    val code: JellyseerrMessageCode,
    val subject: String? = null,
    val detail: String? = null,
    val recovery: JellyseerrMessageRecovery = JellyseerrMessageRecovery.None,
    val operationKey: JellyseerrOperationKey? = null,
)

sealed interface JellyseerrRequestsState {
    data object Loading : JellyseerrRequestsState

    data object MissingServer : JellyseerrRequestsState

    data class Ready(
        val filter: JellyseerrRequestFilter,
        val requests: List<JellyseerrRequestSummary>,
        val counts: JellyseerrRequestCounts?,
        val query: String,
        val searchResults: List<JellyseerrSearchItem>,
        val isSearching: Boolean,
        val isRefreshing: Boolean,
        val isPerformingAction: Boolean,
        val pendingApprovals: Set<Int> = emptySet(),
        val message: JellyseerrMessage?,
        val isAdmin: Boolean,
        val lastUpdated: Instant?,
        val languageProfiles: JellyseerrLanguageProfiles,
        val currentRequestsByMedia: Map<Pair<JellyseerrMediaType, Int>, JellyseerrRequestSummary> = emptyMap(),
        val currentUserId: Int? = null,
        val capabilities: JellyseerrRequestCapabilities = JellyseerrRequestCapabilities.NONE,
    ) : JellyseerrRequestsState

    data class Error(
        val message: String,
    ) : JellyseerrRequestsState
}

sealed interface JellyseerrRecommendationsState {
    data object Loading : JellyseerrRecommendationsState

    data object MissingServer : JellyseerrRecommendationsState

    data class Ready(
        val rails: Map<JellyseerrRecommendationRail, JellyseerrRecommendationRailState>,
    ) : JellyseerrRecommendationsState

    data class Error(
        val message: String,
    ) : JellyseerrRecommendationsState
}

sealed interface JellyseerrCreateResult {
    data class Success(
        val request: JellyseerrRequestSummary,
    ) : JellyseerrCreateResult

    data class Duplicate(
        val message: String,
    ) : JellyseerrCreateResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : JellyseerrCreateResult
}

object JellyseerrPermission {
    const val ADMIN: Int = 2
    const val MANAGE_REQUESTS: Int = 16
    const val REQUEST: Int = 32
    const val REQUEST_4K: Int = 1024
    const val REQUEST_4K_MOVIE: Int = 2048
    const val REQUEST_4K_TV: Int = 4096
    const val REQUEST_ADVANCED: Int = 8192
    const val REQUEST_MOVIE: Int = 262_144
    const val REQUEST_TV: Int = 524_288
}

fun Int?.hasPermission(permission: Int): Boolean {
    val value = this ?: return false
    return value and permission == permission || value and JellyseerrPermission.ADMIN == JellyseerrPermission.ADMIN
}

private fun Int?.hasPermissionDirectly(permission: Int): Boolean {
    val value = this ?: return false
    return value and permission == permission
}
