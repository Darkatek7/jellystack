package dev.jellystack.core.jellyseerr

import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.core.logging.sanitizeUrl
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.NetworkJson
import dev.jellystack.network.jellyseerr.JellyseerrApi
import dev.jellystack.network.jellyseerr.JellyseerrCastDto
import dev.jellystack.network.jellyseerr.JellyseerrCollectionDto
import dev.jellystack.network.jellyseerr.JellyseerrCombinedRatingsDto
import dev.jellystack.network.jellyseerr.JellyseerrContentRatingsDto
import dev.jellystack.network.jellyseerr.JellyseerrCreateRequestPayload
import dev.jellystack.network.jellyseerr.JellyseerrCreatedByDto
import dev.jellystack.network.jellyseerr.JellyseerrCrewDto
import dev.jellystack.network.jellyseerr.JellyseerrDiscoverResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrDiscoverResultDto
import dev.jellystack.network.jellyseerr.JellyseerrHttpException
import dev.jellystack.network.jellyseerr.JellyseerrLanguageProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrMediaInfoDto
import dev.jellystack.network.jellyseerr.JellyseerrMovieDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrMovieReleasesDto
import dev.jellystack.network.jellyseerr.JellyseerrProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrQualityProfileDto
import dev.jellystack.network.jellyseerr.JellyseerrRatingsDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestCountsDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestDto
import dev.jellystack.network.jellyseerr.JellyseerrRequestsResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrRtRatingDto
import dev.jellystack.network.jellyseerr.JellyseerrScoreDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResponseDto
import dev.jellystack.network.jellyseerr.JellyseerrSearchResultDto
import dev.jellystack.network.jellyseerr.JellyseerrSeasonDto
import dev.jellystack.network.jellyseerr.JellyseerrServiceDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrServiceSummaryDto
import dev.jellystack.network.jellyseerr.JellyseerrSpokenLanguageDto
import dev.jellystack.network.jellyseerr.JellyseerrTvDetailsDto
import dev.jellystack.network.jellyseerr.JellyseerrTvSeasonDto
import dev.jellystack.network.jellyseerr.JellyseerrUserDto
import dev.jellystack.network.jellyseerr.JellyseerrVideoDto
import dev.jellystack.network.jellyseerr.seasonsAll
import dev.jellystack.network.jellyseerr.seasonsList
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JellyseerrRepository(
    httpClient: HttpClient? = null,
    private val json: Json = NetworkJson.default,
    private val recommendationsStore: JellyseerrRecommendationStore? = null,
    private val clock: Clock = Clock.System,
) {
    private val client: HttpClient =
        httpClient ?: NetworkClientFactory.create(ClientConfig(installLogging = false))

    private data class CachedApi(
        val api: JellyseerrApi,
    )

    private val apiCache = mutableMapOf<String, CachedApi>()
    private val credentialCache = mutableMapOf<String, Pair<String?, String?>>()
    private val metadataCache = mutableMapOf<MetadataCacheKey, JellyseerrMediaMetadata>()
    private val metadataOrder = ArrayDeque<MetadataCacheKey>()
    private val metadataMutex = Mutex()

    private suspend fun api(environment: JellyseerrEnvironment): JellyseerrApi {
        val cacheEntry = apiCache[environment.serverId]
        if (
            cacheEntry != null &&
            credentialCache[environment.serverId] == environment.apiKey to environment.sessionCookie
        ) {
            return cacheEntry.api
        }
        val api =
            JellyseerrApi
                .create(
                    baseUrl = environment.baseUrl,
                    apiKey = environment.apiKey,
                    sessionCookie = environment.sessionCookie,
                    apiUserId = environment.apiUserId,
                    client = client,
                )
        apiCache[environment.serverId] = CachedApi(api)
        credentialCache[environment.serverId] = environment.apiKey to environment.sessionCookie
        return api
    }

    private suspend fun getCachedMetadata(key: MetadataCacheKey): JellyseerrMediaMetadata? = metadataMutex.withLock { metadataCache[key] }

    private suspend fun setCachedMetadata(
        key: MetadataCacheKey,
        metadata: JellyseerrMediaMetadata,
    ) {
        metadataMutex.withLock {
            metadataCache[key] = metadata
            metadataOrder.remove(key)
            metadataOrder.addLast(key)
            while (metadataOrder.size > METADATA_CACHE_MAX_ENTRIES) {
                val oldest = metadataOrder.removeFirstOrNull() ?: break
                metadataCache.remove(oldest)
            }
        }
    }

    private suspend fun enrichRequestMetadata(
        environment: JellyseerrEnvironment,
        page: JellyseerrRequestsPage,
    ): JellyseerrRequestsPage {
        val results = page.results
        if (results.isEmpty()) {
            return page
        }
        val keysNeedingMetadata =
            results
                .filter { summary ->
                    summary.tmdbId != null &&
                        (
                            summary.title.isNullOrBlank() ||
                                summary.originalTitle.isNullOrBlank() ||
                                summary.posterPath.isNullOrBlank() ||
                                summary.backdropPath.isNullOrBlank()
                        )
                }.mapNotNull { summary ->
                    summary.tmdbId?.let { MetadataCacheKey(environment.serverId, summary.mediaType, it) }
                }.distinct()
        if (keysNeedingMetadata.isEmpty()) {
            return page
        }
        val metadataByKey = mutableMapOf<MetadataCacheKey, JellyseerrMediaMetadata>()
        for (key in keysNeedingMetadata) {
            val cached = getCachedMetadata(key)
            if (cached != null) {
                metadataByKey[key] = cached
                continue
            }
            val fetched = fetchMetadata(environment, key)
            if (fetched != null) {
                metadataByKey[key] = fetched
                setCachedMetadata(key, fetched)
            }
        }
        if (metadataByKey.isEmpty()) {
            return page
        }
        val enrichedResults =
            results.map { summary ->
                val tmdbId = summary.tmdbId ?: return@map summary
                val key = MetadataCacheKey(environment.serverId, summary.mediaType, tmdbId)
                val metadata = metadataByKey[key] ?: return@map summary
                summary.copy(
                    title = summary.title ?: metadata.title,
                    originalTitle = summary.originalTitle ?: metadata.originalTitle,
                    posterPath = summary.posterPath ?: metadata.posterPath,
                    backdropPath = summary.backdropPath ?: metadata.backdropPath,
                )
            }
        return page.copy(results = enrichedResults)
    }

    suspend fun cachedRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
    ): List<JellyseerrRecommendationPage> {
        val store = recommendationsStore ?: return emptyList()
        val records = store.list(environment.serverId, rail)
        if (records.isEmpty()) {
            return emptyList()
        }
        return records
            .mapNotNull { record ->
                runCatching {
                    val response = json.decodeFromString<JellyseerrDiscoverResponseDto>(record.json)
                    response.toRecommendationPage(record.page, record.updatedAt)
                }.getOrNull()
            }.sortedBy { it.page }
    }

    suspend fun fetchRecommendations(
        environment: JellyseerrEnvironment,
        rail: JellyseerrRecommendationRail,
        page: Int,
    ): JellyseerrRecommendationPage {
        val api = api(environment)
        val response =
            when (rail) {
                JellyseerrRecommendationRail.TRENDS -> api.discoverTrending(page)
                JellyseerrRecommendationRail.POPULAR_MOVIES -> api.discoverPopularMovies(page)
                JellyseerrRecommendationRail.POPULAR_SHOWS -> api.discoverPopularTv(page)
                JellyseerrRecommendationRail.UPCOMING_MOVIES -> api.discoverUpcomingMovies(page)
                JellyseerrRecommendationRail.UPCOMING_SHOWS -> api.discoverUpcomingTv(page)
            }
        val now = clock.now()
        val recommendationPage = response.toRecommendationPage(page, now)
        recommendationsStore?.let { store ->
            if (page == 1) {
                store.clear(environment.serverId, rail)
            } else {
                store.clearAfter(environment.serverId, rail, page)
            }
            val serialized = json.encodeToString(response)
            store.upsert(
                JellyseerrRecommendationRecord(
                    serverId = environment.serverId,
                    rail = rail,
                    page = page,
                    json = serialized,
                    updatedAt = now,
                ),
            )
        }
        return recommendationPage
    }

    suspend fun fetchRecommendationDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail {
        val primary = fetchRecommendationPrimaryDetail(environment, tmdbId, mediaType)
        val enrichment = fetchRecommendationDetailEnrichment(environment, primary)
        return primary.copy(
            ratings = enrichment.ratings,
            enrichment = enrichment,
        )
    }

    suspend fun fetchRecommendationPrimaryDetail(
        environment: JellyseerrEnvironment,
        tmdbId: Int,
        mediaType: JellyseerrMediaType,
    ): JellyseerrMediaDetail {
        val apiInstance = api(environment)
        return when (mediaType) {
            JellyseerrMediaType.MOVIE ->
                apiInstance.getMovieDetails(tmdbId).toDomainDetail(
                    environment = environment,
                    mediaType = JellyseerrMediaType.MOVIE,
                )
            JellyseerrMediaType.TV ->
                apiInstance.getTvDetails(tmdbId).toDomainDetail(
                    environment = environment,
                    mediaType = JellyseerrMediaType.TV,
                )
            else ->
                throw IllegalArgumentException("Unsupported media type $mediaType for Jellyseerr detail")
        }
    }

    suspend fun fetchRecommendationDetailEnrichment(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
    ): JellyseerrMediaDetailEnrichment =
        supervisorScope {
            val apiInstance = api(environment)
            when (primaryDetail.mediaType) {
                JellyseerrMediaType.MOVIE -> {
                    val ratings =
                        async {
                            captureOptional {
                                apiInstance.getMovieRatingsCombined(primaryDetail.tmdbId)
                            }
                        }
                    val similar =
                        async {
                            captureOptional {
                                apiInstance.getMovieSimilar(primaryDetail.tmdbId)
                            }
                        }
                    val recommendations =
                        async {
                            captureOptional {
                                apiInstance.getMovieRecommendations(primaryDetail.tmdbId)
                            }
                        }
                    buildMovieEnrichment(
                        primaryDetail = primaryDetail,
                        ratings = ratings.await(),
                        similar = similar.await(),
                        recommendations = recommendations.await(),
                    )
                }
                JellyseerrMediaType.TV -> {
                    val ratings =
                        async {
                            captureOptional {
                                apiInstance.getTvRatings(primaryDetail.tmdbId)
                            }
                        }
                    val similar =
                        async {
                            captureOptional {
                                apiInstance.getTvSimilar(primaryDetail.tmdbId)
                            }
                        }
                    val recommendations =
                        async {
                            captureOptional {
                                apiInstance.getTvRecommendations(primaryDetail.tmdbId)
                            }
                        }
                    buildTvEnrichment(
                        primaryDetail = primaryDetail,
                        ratings = ratings.await(),
                        similar = similar.await(),
                        recommendations = recommendations.await(),
                    )
                }
                else ->
                    throw IllegalArgumentException(
                        "Unsupported media type ${primaryDetail.mediaType} for Jellyseerr detail enrichment",
                    )
            }
        }

    suspend fun fetchRecommendationDetailEnrichmentSection(
        environment: JellyseerrEnvironment,
        primaryDetail: JellyseerrMediaDetail,
        section: JellyseerrDetailEnrichmentSection,
    ): JellyseerrMediaDetailEnrichment {
        val apiInstance = api(environment)
        return when (primaryDetail.mediaType) {
            JellyseerrMediaType.MOVIE ->
                when (section) {
                    JellyseerrDetailEnrichmentSection.RATINGS -> {
                        val result =
                            captureOptional {
                                apiInstance.getMovieRatingsCombined(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            ratings =
                                result.valueOrNull()?.toDomainRatings(primaryDetail.ratings)
                                    ?: primaryDetail.ratings,
                            failedSections = result.failedSections(section),
                        )
                    }
                    JellyseerrDetailEnrichmentSection.SIMILAR -> {
                        val result =
                            captureOptional {
                                apiInstance.getMovieSimilar(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            similar =
                                result
                                    .valueOrNull()
                                    ?.toRelatedItems(JellyseerrMediaType.MOVIE)
                                    .orEmpty(),
                            failedSections = result.failedSections(section),
                        )
                    }
                    JellyseerrDetailEnrichmentSection.RECOMMENDATIONS -> {
                        val result =
                            captureOptional {
                                apiInstance.getMovieRecommendations(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            recommendations =
                                result
                                    .valueOrNull()
                                    ?.toRelatedItems(JellyseerrMediaType.MOVIE)
                                    .orEmpty(),
                            failedSections = result.failedSections(section),
                        )
                    }
                }
            JellyseerrMediaType.TV ->
                when (section) {
                    JellyseerrDetailEnrichmentSection.RATINGS -> {
                        val result =
                            captureOptional {
                                apiInstance.getTvRatings(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            ratings =
                                result.valueOrNull()?.toDomainRatings(primaryDetail.ratings)
                                    ?: primaryDetail.ratings,
                            failedSections = result.failedSections(section),
                        )
                    }
                    JellyseerrDetailEnrichmentSection.SIMILAR -> {
                        val result =
                            captureOptional {
                                apiInstance.getTvSimilar(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            similar =
                                result
                                    .valueOrNull()
                                    ?.toRelatedItems(JellyseerrMediaType.TV)
                                    .orEmpty(),
                            failedSections = result.failedSections(section),
                        )
                    }
                    JellyseerrDetailEnrichmentSection.RECOMMENDATIONS -> {
                        val result =
                            captureOptional {
                                apiInstance.getTvRecommendations(primaryDetail.tmdbId)
                            }
                        JellyseerrMediaDetailEnrichment(
                            recommendations =
                                result
                                    .valueOrNull()
                                    ?.toRelatedItems(JellyseerrMediaType.TV)
                                    .orEmpty(),
                            failedSections = result.failedSections(section),
                        )
                    }
                }
            else ->
                throw IllegalArgumentException(
                    "Unsupported media type ${primaryDetail.mediaType} for Jellyseerr detail enrichment",
                )
        }
    }

    private suspend fun fetchMetadata(
        environment: JellyseerrEnvironment,
        key: MetadataCacheKey,
    ): JellyseerrMediaMetadata? =
        when (key.mediaType) {
            JellyseerrMediaType.MOVIE ->
                captureOptional { api(environment).getMovieDetails(key.tmdbId) }
                    .valueOrNull()
                    ?.toMetadata()
            JellyseerrMediaType.TV ->
                captureOptional { api(environment).getTvDetails(key.tmdbId) }
                    .valueOrNull()
                    ?.toMetadata()
            else -> null
        }

    private fun JellyseerrMovieDetailsDto.toMetadata(): JellyseerrMediaMetadata =
        JellyseerrMediaMetadata(
            title = firstNonBlank(title, originalTitle),
            originalTitle = firstNonBlank(originalTitle, title),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
        )

    private fun JellyseerrTvDetailsDto.toMetadata(): JellyseerrMediaMetadata =
        JellyseerrMediaMetadata(
            title = firstNonBlank(name, originalName),
            originalTitle = firstNonBlank(originalName, name),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
        )

    private fun JellyseerrMovieDetailsDto.toDomainDetail(
        environment: JellyseerrEnvironment,
        mediaType: JellyseerrMediaType,
        externalRatings: JellyseerrCombinedRatingsDto? = null,
    ): JellyseerrMediaDetail {
        val resolvedTitle = firstNonBlank(title, originalTitle, mediaInfo?.title, mediaInfo?.name).orEmpty()
        val resolvedReleaseDate = releaseDate.ifNotBlank()
        val year = resolvedReleaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
        val genresList = genres.mapNotNull { it.name.ifNotBlank() }
        val countries =
            productionCountries
                .mapNotNull { country -> country.name.ifNotBlank() ?: country.isoCode.ifNotBlank() }
                .distinct()
        val studios = productionCompanies.mapNotNull { it.name.ifNotBlank() }.distinct()
        val domainVideos = videos.toDomainVideos()
        val cast = credits?.cast.orEmpty().mapNotNull { it.toDomainPerson() }
        val crew = credits?.crew.orEmpty().mapNotNull { it.toDomainPerson() }
        val resolvedRatings = toDomainRatings(externalRatings)
        return JellyseerrMediaDetail(
            tmdbId = id,
            mediaType = mediaType,
            title = resolvedTitle,
            year = year,
            overview = overview.ifNotBlank(),
            runtimeMinutes = runtime,
            genres = genresList,
            releaseDate = resolvedReleaseDate,
            revenue = revenue,
            originalLanguage = originalLanguage.ifNotBlank(),
            productionCountries = countries,
            studios = studios,
            ratings = resolvedRatings,
            trailer = videos.toDomainTrailer(),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
            jellyseerrUrl = buildMediaUrl(environment.baseUrl, mediaType, id),
            jellyfinUrl = mediaInfo?.externalServiceSlug.ifNotBlank(),
            imdbId = firstNonBlank(externalIds?.imdbId, imdbId),
            tvdbId = externalIds?.tvdbId ?: mediaInfo?.tvdbId,
            originalTitle = originalTitle.ifNotBlank(),
            tagline = tagline.ifNotBlank(),
            certification = certification.ifNotBlank() ?: releases.toCertification(),
            status = status.ifNotBlank(),
            budget = budget?.takeIf { it > 0L },
            languages = spokenLanguages.toDomainLanguages(),
            cast = cast,
            crew = crew,
            collection = (collection ?: belongsToCollection)?.toDomain(),
            keywords = keywords.mapNotNull { it.name.ifNotBlank() }.distinct(),
            videos = domainVideos,
        )
    }

    private fun JellyseerrTvDetailsDto.toDomainDetail(
        environment: JellyseerrEnvironment,
        mediaType: JellyseerrMediaType,
        externalRatings: JellyseerrRtRatingDto? = null,
    ): JellyseerrMediaDetail {
        val resolvedTitle = firstNonBlank(name, originalName, mediaInfo?.name, mediaInfo?.title).orEmpty()
        val resolvedAirDate = firstAirDate.ifNotBlank()
        val year = resolvedAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)
        val runtime = episodeRunTime.firstOrNull()
        val genresList = genres.mapNotNull { it.name.ifNotBlank() }
        val countryNames =
            (
                productionCountries.mapNotNull { it.name.ifNotBlank() ?: it.isoCode.ifNotBlank() } +
                    originCountry.mapNotNull { it.ifBlank { null } }
            ).distinct()
        val studios =
            (networks.mapNotNull { it.name.ifNotBlank() } + productionCompanies.mapNotNull { it.name.ifNotBlank() })
                .distinct()
        val domainSeasons = seasons.mapNotNull { it.toDomain() }.sortedBy { it.seasonNumber }
        val positiveSeasonNumbers =
            domainSeasons
                .map { it.seasonNumber }
                .filter { it > 0 }
                .distinct()
                .sorted()
        val availableSeasonNumbers =
            positiveSeasonNumbers.ifEmpty {
                (1..(numberOfSeasons ?: 0)).toList()
            }
        val domainVideos = videos.toDomainVideos()
        val cast = credits?.cast.orEmpty().mapNotNull { it.toDomainPerson() }
        val creditedCrew = credits?.crew.orEmpty().mapNotNull { it.toDomainPerson() }
        val creators = createdBy.mapNotNull { it.toDomainCreator() }
        val resolvedRatings = toDomainRatings(externalRatings)
        return JellyseerrMediaDetail(
            tmdbId = id,
            mediaType = mediaType,
            title = resolvedTitle,
            year = year,
            overview = overview.ifNotBlank(),
            runtimeMinutes = runtime,
            genres = genresList,
            releaseDate = resolvedAirDate,
            revenue = null,
            originalLanguage = originalLanguage.ifNotBlank(),
            productionCountries = countryNames,
            studios = studios,
            ratings = resolvedRatings,
            trailer = videos.toDomainTrailer(),
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
            jellyseerrUrl = buildMediaUrl(environment.baseUrl, mediaType, id),
            jellyfinUrl = mediaInfo?.externalServiceSlug.ifNotBlank(),
            imdbId = externalIds?.imdbId.ifNotBlank(),
            tvdbId = externalIds?.tvdbId ?: mediaInfo?.tvdbId,
            availableSeasons = availableSeasonNumbers,
            originalTitle = originalName.ifNotBlank(),
            tagline = tagline.ifNotBlank(),
            certification = certification.ifNotBlank() ?: contentRatings.toCertification(),
            status = status.ifNotBlank(),
            languages = spokenLanguages.toDomainLanguages().ifEmpty { languages.mapNotNull { it.ifNotBlank() }.distinct() },
            cast = cast,
            crew = (creators + creditedCrew).distinctBy { it.id to it.job },
            seasons = domainSeasons,
            keywords = keywords.mapNotNull { it.name.ifNotBlank() }.distinct(),
            videos = domainVideos,
        )
    }

    private fun JellyseerrMovieDetailsDto.toDomainRatings(externalRatings: JellyseerrCombinedRatingsDto?): JellyseerrMediaRatings? =
        ratings.toDomainRatings(
            tmdbFallback = voteAverage,
            imdbFallback = externalRatings?.imdb?.criticsScore,
            rottenTomatoesFallback = externalRatings?.rt,
        )

    private fun JellyseerrTvDetailsDto.toDomainRatings(externalRatings: JellyseerrRtRatingDto?): JellyseerrMediaRatings? =
        ratings.toDomainRatings(
            tmdbFallback = voteAverage,
            rottenTomatoesFallback = externalRatings,
        )

    private fun JellyseerrRatingsDto?.toDomainRatings(
        tmdbFallback: Double? = null,
        imdbFallback: Double? = null,
        rottenTomatoesFallback: JellyseerrRtRatingDto? = null,
    ): JellyseerrMediaRatings? {
        val tmdbScore = this?.tmdb.extractScore() ?: tmdbFallback.validScore()
        val imdbScore = this?.imdb.extractScore() ?: imdbFallback.validScore()
        val critics = this?.rottenTomatoes?.critics.extractScore() ?: rottenTomatoesFallback?.criticsScore.validScore()
        val audience = this?.rottenTomatoes?.audience.extractScore() ?: rottenTomatoesFallback?.audienceScore.validScore()
        if (tmdbScore == null && imdbScore == null && critics == null && audience == null) {
            return null
        }
        return JellyseerrMediaRatings(
            tmdb = tmdbScore,
            imdb = imdbScore,
            rottenTomatoesCritics = critics,
            rottenTomatoesAudience = audience,
        )
    }

    private fun JellyseerrScoreDto?.extractScore(): Double? =
        this
            ?.let { score ->
                score.rating ?: score.voteAverage ?: score.value
            }.validScore()

    private fun Double?.validScore(): Double? = this?.takeIf { it > 0.0 }

    private fun List<JellyseerrSpokenLanguageDto>.toDomainLanguages(): List<String> =
        mapNotNull { language ->
            firstNonBlank(language.englishName, language.name, language.isoCode)
        }.distinct()

    private fun JellyseerrCastDto.toDomainPerson(): JellyseerrPerson? {
        val resolvedId = id ?: return null
        val resolvedName = name.ifNotBlank() ?: return null
        return JellyseerrPerson(
            id = resolvedId,
            name = resolvedName,
            character = character.ifNotBlank(),
            profilePath = profilePath.ifNotBlank(),
            order = order,
        )
    }

    private fun JellyseerrCrewDto.toDomainPerson(): JellyseerrPerson? {
        val resolvedId = id ?: return null
        val resolvedName = name.ifNotBlank() ?: return null
        return JellyseerrPerson(
            id = resolvedId,
            name = resolvedName,
            job = job.ifNotBlank(),
            department = department.ifNotBlank(),
            profilePath = profilePath.ifNotBlank(),
        )
    }

    private fun JellyseerrCreatedByDto.toDomainCreator(): JellyseerrPerson? {
        val resolvedId = id ?: return null
        val resolvedName = name.ifNotBlank() ?: return null
        return JellyseerrPerson(
            id = resolvedId,
            name = resolvedName,
            job = "Creator",
            department = "Creator",
            profilePath = profilePath.ifNotBlank(),
        )
    }

    private fun JellyseerrTvSeasonDto.toDomain(): JellyseerrSeason? {
        val resolvedNumber = seasonNumber ?: return null
        return JellyseerrSeason(
            id = id,
            seasonNumber = resolvedNumber,
            name = name.ifNotBlank(),
            overview = overview.ifNotBlank(),
            airDate = airDate.ifNotBlank(),
            episodeCount = episodeCount,
            posterPath = posterPath.ifNotBlank(),
        )
    }

    private fun JellyseerrCollectionDto.toDomain(): JellyseerrCollection? {
        val resolvedId = id ?: return null
        val resolvedName = name.ifNotBlank() ?: return null
        return JellyseerrCollection(
            id = resolvedId,
            name = resolvedName,
            posterPath = posterPath.ifNotBlank(),
            backdropPath = backdropPath.ifNotBlank(),
        )
    }

    private fun JellyseerrMovieReleasesDto?.toCertification(): String? =
        this
            ?.results
            .orEmpty()
            .firstNotNullOfOrNull { region ->
                region.rating.ifNotBlank()
                    ?: region.releaseDates.firstNotNullOfOrNull { it.certification.ifNotBlank() }
            }

    private fun JellyseerrContentRatingsDto?.toCertification(): String? =
        this
            ?.results
            .orEmpty()
            .firstNotNullOfOrNull { it.rating.ifNotBlank() }

    private fun List<JellyseerrVideoDto>?.toDomainVideos(): List<JellyseerrMediaVideo> = orEmpty().mapNotNull { it.toDomainVideo() }

    private fun JellyseerrVideoDto.toDomainVideo(): JellyseerrMediaVideo? {
        val resolvedKey = key.ifNotBlank() ?: return null
        val resolvedSite = site.ifNotBlank()
        return JellyseerrMediaVideo(
            id = id.ifNotBlank(),
            name = name.ifNotBlank(),
            site = resolvedSite,
            type = type.ifNotBlank(),
            key = resolvedKey,
            url = url.ifNotBlank() ?: buildVideoUrl(resolvedSite, resolvedKey),
            official = official == true,
            publishedAt = publishedAt.ifNotBlank(),
            size = size,
        )
    }

    private fun List<JellyseerrVideoDto>?.toDomainTrailer(): JellyseerrMediaTrailer? {
        val videos = this.orEmpty()
        if (videos.isEmpty()) {
            return null
        }
        val preferred =
            videos.firstOrNull { video ->
                video.site.equals("YouTube", ignoreCase = true) &&
                    video.type.equals("Trailer", ignoreCase = true) &&
                    video.official == true &&
                    !video.key.isNullOrBlank()
            }
                ?: videos.firstOrNull { video ->
                    video.site.equals("YouTube", ignoreCase = true) &&
                        !video.key.isNullOrBlank()
                }
                ?: videos.firstOrNull { !it.key.isNullOrBlank() }
        return preferred?.toDomainTrailer()
    }

    private fun JellyseerrVideoDto.toDomainTrailer(): JellyseerrMediaTrailer? {
        val resolvedKey = key.ifNotBlank() ?: return null
        val resolvedSite = site.ifNotBlank()
        return JellyseerrMediaTrailer(
            name = name,
            site = resolvedSite,
            type = type,
            key = resolvedKey,
            url = url.ifNotBlank() ?: buildVideoUrl(resolvedSite, resolvedKey),
        )
    }

    private fun buildVideoUrl(
        site: String?,
        key: String,
    ): String? =
        when {
            site.equals("YouTube", ignoreCase = true) ->
                "https://www.youtube.com/watch?v=$key"
            site.equals("Vimeo", ignoreCase = true) ->
                "https://vimeo.com/$key"
            else -> null
        }

    private fun buildMovieEnrichment(
        primaryDetail: JellyseerrMediaDetail,
        ratings: OptionalEnrichment<JellyseerrCombinedRatingsDto>,
        similar: OptionalEnrichment<JellyseerrDiscoverResponseDto>,
        recommendations: OptionalEnrichment<JellyseerrDiscoverResponseDto>,
    ): JellyseerrMediaDetailEnrichment =
        JellyseerrMediaDetailEnrichment(
            ratings = ratings.valueOrNull()?.toDomainRatings(primaryDetail.ratings) ?: primaryDetail.ratings,
            similar = similar.valueOrNull()?.toRelatedItems(JellyseerrMediaType.MOVIE).orEmpty(),
            recommendations =
                recommendations
                    .valueOrNull()
                    ?.toRelatedItems(JellyseerrMediaType.MOVIE)
                    .orEmpty(),
            failedSections = failedSections(ratings, similar, recommendations),
        )

    private fun buildTvEnrichment(
        primaryDetail: JellyseerrMediaDetail,
        ratings: OptionalEnrichment<JellyseerrRtRatingDto>,
        similar: OptionalEnrichment<JellyseerrDiscoverResponseDto>,
        recommendations: OptionalEnrichment<JellyseerrDiscoverResponseDto>,
    ): JellyseerrMediaDetailEnrichment =
        JellyseerrMediaDetailEnrichment(
            ratings = ratings.valueOrNull()?.toDomainRatings(primaryDetail.ratings) ?: primaryDetail.ratings,
            similar = similar.valueOrNull()?.toRelatedItems(JellyseerrMediaType.TV).orEmpty(),
            recommendations =
                recommendations
                    .valueOrNull()
                    ?.toRelatedItems(JellyseerrMediaType.TV)
                    .orEmpty(),
            failedSections = failedSections(ratings, similar, recommendations),
        )

    private fun JellyseerrCombinedRatingsDto.toDomainRatings(primaryRatings: JellyseerrMediaRatings?): JellyseerrMediaRatings? =
        mediaRatingsOrNull(
            tmdb = primaryRatings?.tmdb,
            imdb = imdb?.criticsScore.validScore() ?: primaryRatings?.imdb,
            rottenTomatoesCritics = rt?.criticsScore.validScore() ?: primaryRatings?.rottenTomatoesCritics,
            rottenTomatoesAudience = rt?.audienceScore.validScore() ?: primaryRatings?.rottenTomatoesAudience,
        )

    private fun JellyseerrRtRatingDto.toDomainRatings(primaryRatings: JellyseerrMediaRatings?): JellyseerrMediaRatings? =
        mediaRatingsOrNull(
            tmdb = primaryRatings?.tmdb,
            imdb = primaryRatings?.imdb,
            rottenTomatoesCritics = criticsScore.validScore() ?: primaryRatings?.rottenTomatoesCritics,
            rottenTomatoesAudience = audienceScore.validScore() ?: primaryRatings?.rottenTomatoesAudience,
        )

    private fun mediaRatingsOrNull(
        tmdb: Double?,
        imdb: Double?,
        rottenTomatoesCritics: Double?,
        rottenTomatoesAudience: Double?,
    ): JellyseerrMediaRatings? =
        if (tmdb == null && imdb == null && rottenTomatoesCritics == null && rottenTomatoesAudience == null) {
            null
        } else {
            JellyseerrMediaRatings(
                tmdb = tmdb,
                imdb = imdb,
                rottenTomatoesCritics = rottenTomatoesCritics,
                rottenTomatoesAudience = rottenTomatoesAudience,
            )
        }

    private fun failedSections(
        ratings: OptionalEnrichment<*>,
        similar: OptionalEnrichment<*>,
        recommendations: OptionalEnrichment<*>,
    ): Set<JellyseerrDetailEnrichmentSection> =
        buildSet {
            if (ratings is OptionalEnrichment.Failure) {
                add(JellyseerrDetailEnrichmentSection.RATINGS)
            }
            if (similar is OptionalEnrichment.Failure) {
                add(JellyseerrDetailEnrichmentSection.SIMILAR)
            }
            if (recommendations is OptionalEnrichment.Failure) {
                add(JellyseerrDetailEnrichmentSection.RECOMMENDATIONS)
            }
        }

    private fun OptionalEnrichment<*>.failedSections(section: JellyseerrDetailEnrichmentSection): Set<JellyseerrDetailEnrichmentSection> =
        if (this is OptionalEnrichment.Failure) setOf(section) else emptySet()

    private fun buildMediaUrl(
        baseUrl: String,
        mediaType: JellyseerrMediaType,
        tmdbId: Int,
    ): String {
        val trimmed = baseUrl.trimEnd('/')
        val path =
            when (mediaType) {
                JellyseerrMediaType.MOVIE -> "movie"
                JellyseerrMediaType.TV -> "tv"
                else -> "media"
            }
        return "$trimmed/$path/$tmdbId"
    }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun String?.ifNotBlank(): String? = this?.takeUnless { it.isBlank() }

    private data class MetadataCacheKey(
        val serverId: String,
        val mediaType: JellyseerrMediaType,
        val tmdbId: Int,
    )

    private data class JellyseerrMediaMetadata(
        val title: String?,
        val originalTitle: String?,
        val posterPath: String?,
        val backdropPath: String?,
    )

    suspend fun fetchRequests(
        environment: JellyseerrEnvironment,
        filter: JellyseerrRequestFilter,
        take: Int = DEFAULT_PAGE_SIZE,
        skip: Int = 0,
    ): JellyseerrRequestsPage =
        try {
            val response = api(environment).listRequests(take = take, skip = skip, filter = filter.queryValue)
            val page = response.toDomain()
            enrichRequestMetadata(environment, page)
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to fetch Seerr requests for ${environment.serverId} at ${sanitizeUrl(environment.baseUrl)}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun fetchCounts(environment: JellyseerrEnvironment): JellyseerrRequestCounts =
        try {
            api(environment).getRequestCounts().toDomain()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to fetch Seerr counts for ${environment.serverId} at ${sanitizeUrl(environment.baseUrl)}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun search(
        environment: JellyseerrEnvironment,
        query: String,
        page: Int = 1,
    ): List<JellyseerrSearchItem> =
        try {
            api(environment)
                .search(query = query, page = page)
                .toDomainSearchResults()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to search Seerr for ${environment.serverId} at ${sanitizeUrl(
                    environment.baseUrl,
                )} with query <redacted>: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun createRequest(
        environment: JellyseerrEnvironment,
        request: JellyseerrCreateRequest,
    ): JellyseerrCreateResult {
        val payload =
            JellyseerrCreateRequestPayload(
                mediaType = request.mediaType.toWireValue(),
                mediaId = request.mediaId,
                tvdbId = request.tvdbId,
                seasons =
                    when (val selection = request.seasons) {
                        JellyseerrCreateSelection.AllSeasons -> seasonsAll()
                        is JellyseerrCreateSelection.Seasons -> seasonsList(selection.numbers)
                        null -> null
                    },
                is4k =
                    request.is4k.takeIf {
                        request.mediaType == JellyseerrMediaType.MOVIE || request.mediaType == JellyseerrMediaType.TV
                    },
                serverId = request.serverId,
                profileId = request.profileId,
                languageProfileId = request.languageProfileId,
            )
        return try {
            JellystackLog.d(
                "Creating Seerr request payload id=${payload.mediaId} type=${request.mediaType} title='${request.title ?: "?"}'",
            )
            val response = api(environment).createRequest(payload)
            JellyseerrCreateResult.Success(response.toDomain())
        } catch (error: JellyseerrHttpException) {
            JellystackLog.e(
                "Seerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = parseErrorMessage(error.responseBody)
            if (error.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message.orEmpty())
            } else {
                JellyseerrCreateResult.Failure(message.orEmpty(), error)
            }
        } catch (error: ClientRequestException) {
            JellystackLog.e(
                "Seerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = extractErrorMessage(error)
            if (error.response.status == HttpStatusCode.Conflict) {
                JellyseerrCreateResult.Duplicate(message.orEmpty())
            } else {
                JellyseerrCreateResult.Failure(message.orEmpty(), error)
            }
        } catch (error: ServerResponseException) {
            JellystackLog.e(
                "Seerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            val message = extractErrorMessage(error)
            JellyseerrCreateResult.Failure(message.orEmpty(), error)
        } catch (error: Throwable) {
            JellystackLog.e(
                "Seerr create request failed for ${environment.serverId}: ${error.message}",
                error,
            )
            JellyseerrCreateResult.Failure(error.message.orEmpty(), error)
        }
    }

    suspend fun deleteRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<Unit> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.deleteRequest(requestId) }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to delete Seerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun removeMediaFromService(
        environment: JellyseerrEnvironment,
        mediaId: Int,
        is4k: Boolean = false,
    ): Result<Unit> {
        val apiInstance = api(environment)
        return runCatching {
            apiInstance.deleteMediaFiles(mediaId, is4k)
        }.onFailure { error ->
            JellystackLog.e(
                "Failed to remove Seerr media $mediaId for ${environment.serverId}: ${error.message}",
                error,
            )
        }
    }

    suspend fun retryRequest(
        environment: JellyseerrEnvironment,
        requestId: Int,
    ): Result<JellyseerrRequestSummary> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.retryRequest(requestId).toDomain() }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to retry Seerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun updateRequestStatus(
        environment: JellyseerrEnvironment,
        requestId: Int,
        status: String,
    ): Result<JellyseerrRequestSummary> {
        val apiInstance = api(environment)
        return runCatching { apiInstance.updateRequestStatus(requestId, status).toDomain() }
            .onFailure { error ->
                JellystackLog.e(
                    "Failed to update Seerr request $requestId for ${environment.serverId}: ${error.message}",
                    error,
                )
            }
    }

    suspend fun profile(environment: JellyseerrEnvironment): JellyseerrProfile =
        try {
            api(environment).getProfile().toDomain()
        } catch (error: Throwable) {
            JellystackLog.e(
                "Failed to load Seerr profile for ${environment.serverId} at ${sanitizeUrl(environment.baseUrl)}: ${error.message}",
                error,
            )
            throw error
        }

    suspend fun fetchLanguageProfiles(environment: JellyseerrEnvironment): JellyseerrLanguageProfiles {
        val apiInstance = api(environment)
        val radarrSummaries =
            runCatching { apiInstance.listRadarrServices() }
                .onFailure { error ->
                    JellystackLog.e(
                        "Failed to load Radarr services for ${environment.serverId}: ${error.message}",
                        error,
                    )
                }.getOrDefault(emptyList())
        val radarrProfiles =
            radarrSummaries.flatMap { summary ->
                val details =
                    runCatching { apiInstance.getRadarrServiceDetails(summary.id) }
                        .onFailure { error ->
                            JellystackLog.e(
                                "Failed to load Radarr service details ${summary.id} for ${environment.serverId}: ${error.message}",
                                error,
                            )
                        }.getOrNull()
                summary.toDomainLanguageProfiles(details)
            }
        val sonarrSummaries =
            runCatching { apiInstance.listSonarrServices() }
                .onFailure { error ->
                    JellystackLog.e(
                        "Failed to load Sonarr services for ${environment.serverId}: ${error.message}",
                        error,
                    )
                }.getOrDefault(emptyList())
        val sonarrProfiles =
            sonarrSummaries.flatMap { summary ->
                val details =
                    runCatching { apiInstance.getSonarrServiceDetails(summary.id) }
                        .onFailure { error ->
                            JellystackLog.e(
                                "Failed to load Sonarr service details ${summary.id} for ${environment.serverId}: ${error.message}",
                                error,
                            )
                        }.getOrNull()
                summary.toDomainLanguageProfiles(details)
            }
        return JellyseerrLanguageProfiles(
            movies = radarrProfiles,
            tv = sonarrProfiles,
        )
    }

    private suspend fun extractErrorMessage(error: ClientRequestException): String? =
        runCatching {
            val body = error.response.bodyAsText()
            parseErrorMessage(body)
        }.getOrNull()

    private suspend fun extractErrorMessage(error: ServerResponseException): String? =
        runCatching {
            val body = error.response.bodyAsText()
            parseErrorMessage(body)
        }.getOrNull()

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<JellyseerrErrorDto>(body).message
        }.getOrNull()
            ?: runCatching {
                json
                    .parseToJsonElement(body)
                    .jsonObject["message"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
            ?: body
    }

    private fun JellyseerrRequestsResponseDto.toDomain(): JellyseerrRequestsPage {
        val pageInfo = pageInfo
        val mapped =
            results.map { it.toDomain() }
        return JellyseerrRequestsPage(
            page = pageInfo?.page ?: 1,
            pageSize = pageInfo?.pageSize ?: mapped.size,
            totalResults = pageInfo?.results ?: mapped.size,
            totalPages = pageInfo?.pages ?: 1,
            results = mapped,
        )
    }

    private fun JellyseerrRequestCountsDto.toDomain(): JellyseerrRequestCounts =
        JellyseerrRequestCounts(
            total = total ?: 0,
            movie = movie ?: 0,
            tv = tv ?: 0,
            pending = pending ?: 0,
            approved = approved ?: 0,
            declined = declined ?: 0,
            processing = processing ?: 0,
            available = available ?: 0,
            completed = completed ?: 0,
        )

    private fun JellyseerrRequestDto.toDomain(): JellyseerrRequestSummary {
        val effectiveMedia = media
        val availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.from(effectiveMedia?.status),
                `4k` = JellyseerrMediaStatus.from(effectiveMedia?.status4k),
            )
        val resolvedTitle =
            firstNonBlank(
                effectiveMedia?.title,
                effectiveMedia?.name,
                effectiveMedia?.originalTitle,
                effectiveMedia?.originalName,
            )
        val resolvedOriginalTitle =
            firstNonBlank(
                effectiveMedia?.originalTitle,
                effectiveMedia?.originalName,
            )
        return JellyseerrRequestSummary(
            id = id,
            mediaId = effectiveMedia?.id ?: mediaId,
            tmdbId = effectiveMedia?.tmdbId,
            tvdbId = effectiveMedia?.tvdbId,
            title = resolvedTitle,
            originalTitle = resolvedOriginalTitle,
            mediaType = JellyseerrMediaType.from(type),
            requestStatus = JellyseerrRequestStatus.from(status),
            availability = availability,
            is4k = is4k,
            canRemoveFromService = canRemoveFromService(),
            createdAt = parseInstant(createdAt),
            updatedAt = parseInstant(updatedAt),
            requestedBy = requestedBy?.toDomain(),
            profileName = profileName,
            seasons = seasons.mapNotNull { it.toDomain() },
            posterPath = effectiveMedia?.posterPath.ifNotBlank(),
            backdropPath = effectiveMedia?.backdropPath.ifNotBlank(),
        )
    }

    private fun JellyseerrRequestDto.canRemoveFromService(): Boolean =
        when {
            canRemove == true -> true
            else -> false
        }

    private fun JellyseerrSearchResponseDto.toDomainSearchResults(): List<JellyseerrSearchItem> =
        results
            .mapNotNull { result ->
                if (result.mediaType.equals("person", ignoreCase = true) || result.mediaType.equals("collection", ignoreCase = true)) {
                    return@mapNotNull null
                }
                result.toDomain()
            }

    private fun JellyseerrSearchResultDto.toDomain(): JellyseerrSearchItem =
        mapToSearchItem(
            id = id,
            mediaType = mediaType,
            title = title,
            name = name,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = releaseDate,
            firstAirDate = firstAirDate,
            mediaInfo = mediaInfo,
        ) ?: error("Unsupported media type $mediaType for search result $id")

    private fun JellyseerrDiscoverResponseDto.toRecommendationPage(
        page: Int,
        fetchedAt: Instant,
    ): JellyseerrRecommendationPage {
        val total = if (totalPages <= 0) maxOf(page, 1) else totalPages
        val items = results.mapNotNull { it.toDomainRecommendation() }
        return JellyseerrRecommendationPage(
            page = page,
            totalPages = total,
            items = items,
            fetchedAt = fetchedAt,
        )
    }

    private fun JellyseerrDiscoverResultDto.toDomainRecommendation(): JellyseerrSearchItem? =
        mapToSearchItem(
            id = id,
            mediaType = mediaType,
            title = title,
            name = name,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            releaseDate = releaseDate,
            firstAirDate = firstAirDate,
            mediaInfo = mediaInfo,
        )

    private fun JellyseerrDiscoverResponseDto.toRelatedItems(fallbackMediaType: JellyseerrMediaType): List<JellyseerrSearchItem> =
        results.mapNotNull { result ->
            mapToSearchItem(
                id = result.id,
                mediaType = result.mediaType,
                title = result.title,
                name = result.name,
                overview = result.overview,
                posterPath = result.posterPath,
                backdropPath = result.backdropPath,
                releaseDate = result.releaseDate,
                firstAirDate = result.firstAirDate,
                mediaInfo = result.mediaInfo,
                fallbackMediaType = fallbackMediaType,
            )
        }

    private fun mapToSearchItem(
        id: Int,
        mediaType: String?,
        title: String?,
        name: String?,
        overview: String?,
        posterPath: String?,
        backdropPath: String?,
        releaseDate: String?,
        firstAirDate: String?,
        mediaInfo: JellyseerrMediaInfoDto?,
        fallbackMediaType: JellyseerrMediaType? = null,
    ): JellyseerrSearchItem? {
        val parsedType = JellyseerrMediaType.from(mediaType)
        val type =
            if (parsedType == JellyseerrMediaType.UNKNOWN) {
                fallbackMediaType ?: parsedType
            } else {
                parsedType
            }
        if (type == JellyseerrMediaType.PERSON || type == JellyseerrMediaType.COLLECTION) {
            return null
        }
        val movieOrTvTitle = title ?: name ?: ""
        val releaseYear =
            when {
                !releaseDate.isNullOrBlank() && releaseDate.length >= 4 -> releaseDate.substring(0, 4)
                !firstAirDate.isNullOrBlank() && firstAirDate.length >= 4 -> firstAirDate.substring(0, 4)
                else -> null
            }
        val availability =
            JellyseerrMediaAvailability(
                standard = JellyseerrMediaStatus.from(mediaInfo?.status),
                `4k` = JellyseerrMediaStatus.from(mediaInfo?.status4k),
            )
        val requests = mediaInfo?.requests?.map { it.toDomainWith(mediaInfo) } ?: emptyList()
        return JellyseerrSearchItem(
            tmdbId = id,
            mediaType = type,
            title = movieOrTvTitle,
            overview = overview,
            releaseYear = releaseYear,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaInfoId = mediaInfo?.id,
            tvdbId = mediaInfo?.tvdbId,
            availability = availability,
            requests = requests,
        )
    }

    private fun JellyseerrRequestDto.toDomainWith(mediaInfo: JellyseerrMediaInfoDto): JellyseerrRequestSummary =
        this.copy(media = mediaInfo).toDomain()

    private fun JellyseerrSeasonDto.toDomain(): JellyseerrSeasonStatus? {
        val seasonNumber = seasonNumber ?: return null
        return JellyseerrSeasonStatus(
            seasonNumber = seasonNumber,
            status = JellyseerrRequestStatus.from(status),
        )
    }

    private fun JellyseerrUserDto.toDomain(): JellyseerrUser =
        JellyseerrUser(
            id = id,
            displayName = displayName,
            username = username,
            permissions = permissions,
        )

    private fun JellyseerrProfileDto.toDomain(): JellyseerrProfile =
        JellyseerrProfile(
            id = id,
            displayName = displayName ?: username,
            permissions = permissions,
        )

    private fun JellyseerrServiceSummaryDto.toDomainLanguageProfiles(
        details: JellyseerrServiceDetailsDto?,
    ): List<JellyseerrLanguageProfileOption> {
        val server = details?.server ?: this
        val resolvedName = server.name?.takeIf { it.isNotBlank() } ?: server.id.toString()
        val fallbackProfileId = details?.server?.activeProfileId ?: activeProfileId
        val fallbackLanguageProfileId = details?.server?.activeLanguageProfileId ?: activeLanguageProfileId
        val languageProfiles = details?.languageProfiles.orEmpty()
        val qualityProfiles = details?.profiles.orEmpty()
        if (languageProfiles.isNotEmpty()) {
            return languageProfiles.map { profile ->
                profile.toDomain(
                    server = server,
                    resolvedName = resolvedName,
                    fallbackProfileId = fallbackProfileId,
                    activeLanguageProfileId = fallbackLanguageProfileId,
                )
            }
        }
        if (qualityProfiles.isNotEmpty()) {
            return qualityProfiles.map { profile ->
                profile.toDomain(
                    server = server,
                    resolvedName = resolvedName,
                    activeProfileId = fallbackProfileId,
                )
            }
        }
        return listOf(
            JellyseerrLanguageProfileOption(
                languageProfileId = fallbackLanguageProfileId,
                name = resolvedName,
                serviceId = server.id,
                serviceName = server.name,
                is4k = server.is4k ?: false,
                isDefault = (server.isDefault ?: false) || fallbackLanguageProfileId != null,
                profileId = fallbackProfileId,
            ),
        )
    }

    private fun JellyseerrLanguageProfileDto.toDomain(
        server: JellyseerrServiceSummaryDto,
        resolvedName: String,
        fallbackProfileId: Int?,
        activeLanguageProfileId: Int?,
    ): JellyseerrLanguageProfileOption =
        JellyseerrLanguageProfileOption(
            languageProfileId = id,
            name = name.takeIf { it.isNotBlank() } ?: resolvedName,
            serviceId = server.id,
            serviceName = server.name,
            is4k = server.is4k ?: false,
            isDefault =
                when {
                    activeLanguageProfileId != null -> id == activeLanguageProfileId
                    else -> server.isDefault ?: false
                },
            profileId = profileId ?: fallbackProfileId,
        )

    private fun JellyseerrQualityProfileDto.toDomain(
        server: JellyseerrServiceSummaryDto,
        resolvedName: String,
        activeProfileId: Int?,
    ): JellyseerrLanguageProfileOption =
        JellyseerrLanguageProfileOption(
            languageProfileId = null,
            name = name.takeIf { it.isNotBlank() } ?: resolvedName,
            serviceId = server.id,
            serviceName = server.name,
            is4k = server.is4k ?: false,
            isDefault =
                when {
                    activeProfileId != null -> id == activeProfileId
                    else -> server.isDefault ?: false
                },
            profileId = id,
        )

    private fun JellyseerrMediaType.toWireValue(): String =
        when (this) {
            JellyseerrMediaType.MOVIE -> "movie"
            JellyseerrMediaType.TV -> "tv"
            JellyseerrMediaType.PERSON -> "person"
            JellyseerrMediaType.COLLECTION -> "collection"
            JellyseerrMediaType.UNKNOWN -> "movie"
        }

    private fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }

    @Serializable
    private data class JellyseerrErrorDto(
        val status: Int? = null,
        val message: String? = null,
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        private const val METADATA_CACHE_MAX_ENTRIES = 100
    }
}

private sealed interface OptionalEnrichment<out T> {
    data class Success<T>(
        val value: T,
    ) : OptionalEnrichment<T>

    data object Failure : OptionalEnrichment<Nothing>
}

private suspend fun <T> captureOptional(block: suspend () -> T): OptionalEnrichment<T> =
    try {
        OptionalEnrichment.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        OptionalEnrichment.Failure
    }

private fun <T> OptionalEnrichment<T>.valueOrNull(): T? =
    when (this) {
        is OptionalEnrichment.Success -> value
        OptionalEnrichment.Failure -> null
    }
