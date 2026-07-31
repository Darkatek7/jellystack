package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.HomeSectionInfoDto
import dev.jellystack.network.jellyfin.HomeSectionItemDto
import dev.jellystack.network.jellyfin.HomeSectionsApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HomeSectionViewMode { PORTRAIT, LANDSCAPE, SQUARE, SMALL }

enum class HomeSectionAction { JELLYFIN, SEERR, INFORMATION }

data class HomeSectionItem(
    val id: String,
    val name: String,
    val overview: String?,
    val productionYear: Int?,
    val communityRating: Double?,
    val imageUrl: String?,
    val jellyfinItem: JellyfinItem?,
    val action: HomeSectionAction,
    val seerrTmdbId: Int? = null,
    val seerrMediaType: String? = null,
)

data class HomeSection(
    val id: String,
    val title: String,
    val viewMode: HomeSectionViewMode,
    val displayTitle: Boolean,
    val showDetailsMenu: Boolean,
    val items: List<HomeSectionItem>,
)

sealed interface HomeSectionsState {
    data object Unavailable : HomeSectionsState
    data object Loading : HomeSectionsState
    data class Ready(
        val sections: List<HomeSection>,
        val imageBaseUrl: String,
        val imageAccessToken: String,
    ) : HomeSectionsState
}

typealias HomeSectionsApiFactory = (JellyfinEnvironment) -> HomeSectionsApi

class HomeSectionsRepository(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val apiFactory: HomeSectionsApiFactory,
) {
    private val mutableState = MutableStateFlow<HomeSectionsState>(HomeSectionsState.Unavailable)
    val state: StateFlow<HomeSectionsState> = mutableState.asStateFlow()

    suspend fun refresh(
        enabledByUser: Boolean,
        language: String?,
    ) {
        if (!enabledByUser) {
            mutableState.value = HomeSectionsState.Unavailable
            return
        }
        val environment = environmentProvider.current()
        if (environment == null) {
            mutableState.value = HomeSectionsState.Unavailable
            return
        }
        mutableState.value = HomeSectionsState.Loading
        try {
            val api = apiFactory(environment)
            val meta = api.meta()
            if (!meta.enabled || !api.ready()) {
                mutableState.value = HomeSectionsState.Unavailable
                return
            }
            val descriptors = loadAllDescriptors(api, environment.userId, language, meta.paginationEnabled, meta.numResultsPerPage)
            val sections =
                supervisorScope {
                    descriptors
                        .sortedBy(HomeSectionInfoDto::orderIndex)
                        .map { descriptor ->
                            async {
                                runCatching {
                                    val type = descriptor.section?.takeIf(String::isNotBlank) ?: return@runCatching null
                                    val items =
                                        api.sectionItems(type, environment.userId, descriptor.additionalData, language)
                                            .items
                                            .take(descriptor.limit.coerceAtLeast(1) * 40)
                                            .mapNotNull { it.toDomain(environment.baseUrl) }
                                    HomeSection(
                                        id = "$type:${descriptor.additionalData.orEmpty()}",
                                        title = descriptor.displayText?.takeIf(String::isNotBlank) ?: type,
                                        viewMode = descriptor.viewMode.toViewMode(),
                                        displayTitle = descriptor.displayTitleText,
                                        showDetailsMenu = descriptor.showDetailsMenu,
                                        items = items,
                                    )
                                }.getOrNull()
                            }
                        }.awaitAll()
                        .filterNotNull()
                        .filter { it.items.isNotEmpty() }
                }
            if (sections.isEmpty()) {
                mutableState.value = HomeSectionsState.Unavailable
            } else {
                mutableState.value =
                    HomeSectionsState.Ready(
                        sections = sections,
                        imageBaseUrl = environment.baseUrl,
                        imageAccessToken = environment.accessToken,
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableState.value = HomeSectionsState.Unavailable
        }
    }

    private suspend fun loadAllDescriptors(
        api: HomeSectionsApi,
        userId: String,
        language: String?,
        paginated: Boolean,
        configuredPageSize: Int?,
    ): List<HomeSectionInfoDto> {
        val pageSize = configuredPageSize?.coerceIn(1, 50) ?: 10
        val seen = linkedSetOf<String>()
        val result = mutableListOf<HomeSectionInfoDto>()
        var page = 1
        do {
            val response = api.sections(userId, language, page, if (paginated) pageSize else null)
            val newItems =
                response.items.filter { descriptor ->
                    seen.add("${descriptor.section}:${descriptor.additionalData}:${descriptor.orderIndex}")
                }
            result += newItems
            page += 1
            if (!paginated || response.items.size < pageSize || newItems.isEmpty()) break
        } while (page <= 20)
        return result
    }
}

private fun String?.toViewMode(): HomeSectionViewMode =
    when (this?.lowercase()) {
        "portrait" -> HomeSectionViewMode.PORTRAIT
        "square" -> HomeSectionViewMode.SQUARE
        "small" -> HomeSectionViewMode.SMALL
        else -> HomeSectionViewMode.LANDSCAPE
    }

private fun HomeSectionItemDto.toDomain(baseUrl: String): HomeSectionItem? {
    val resolvedName = name?.takeIf(String::isNotBlank) ?: return null
    val seerrId = providerIds?.get("Jellyseerr")?.toIntOrNull()
    val externalImage =
        providerIds?.get("JellyseerrPoster")
            ?: providerIds?.get("RadarrPoster")
            ?: providerIds?.get("SonarrPoster")
            ?: providerIds?.get("LidarrPoster")
            ?: providerIds?.get("ReadarrPoster")
    val resolvedExternalImage =
        externalImage?.let { value ->
            when {
                value.startsWith("http://") || value.startsWith("https://") -> value
                value.startsWith("/") -> baseUrl.trimEnd('/') + value
                else -> value
            }
        }
    val localId = id?.takeIf(String::isNotBlank)
    val localItem =
        localId?.takeIf { seerrId == null && providerIds.orEmpty().keys.none { key -> key.endsWith("Poster") } }?.let {
            JellyfinItem(
                id = it,
                libraryId = parentId,
                name = resolvedName,
                sortName = sortName,
                overview = overview,
                type = type ?: "Unknown",
                mediaType = mediaType,
                locationType = null,
                taglines = emptyList(),
                parentId = parentId,
                primaryImageTag = imageTags?.get("Primary"),
                thumbImageTag = imageTags?.get("Thumb"),
                backdropImageTag = backdropImageTags?.firstOrNull() ?: parentBackdropImageTags?.firstOrNull(),
                seriesId = seriesId ?: parentId,
                seriesPrimaryImageTag = null,
                seriesThumbImageTag = null,
                seriesBackdropImageTag = parentBackdropImageTags?.firstOrNull(),
                parentLogoImageTag = imageTags?.get("Logo"),
                runTimeTicks = runTimeTicks,
                positionTicks = userData?.playbackPositionTicks,
                playedPercentage = userData?.playedPercentage,
                productionYear = productionYear,
                premiereDate = premiereDate,
                communityRating = communityRating,
                officialRating = officialRating,
                indexNumber = indexNumber,
                parentIndexNumber = parentIndexNumber,
                seriesName = seriesName,
                seasonId = seasonId,
                episodeTitle = null,
                lastPlayed = userData?.lastPlayedDate,
            )
        }
    val action =
        when {
            localItem != null -> HomeSectionAction.JELLYFIN
            seerrId != null -> HomeSectionAction.SEERR
            else -> HomeSectionAction.INFORMATION
        }
    return HomeSectionItem(
        id = localId ?: "${providerIds.orEmpty().values.joinToString(":")}:$resolvedName",
        name = resolvedName,
        overview = overview,
        productionYear = productionYear,
        communityRating = communityRating,
        imageUrl = resolvedExternalImage,
        jellyfinItem = localItem,
        action = action,
        seerrTmdbId = seerrId,
        seerrMediaType = sourceType,
    )
}

fun defaultHomeSectionsApiFactory(
    clientProvider: () -> HttpClient = { NetworkClientFactory.create(ClientConfig(installLogging = false)) },
): HomeSectionsApiFactory {
    val client by lazy(clientProvider)
    return { environment -> HomeSectionsApi(client, environment.baseUrl, environment.accessToken, environment.deviceId) }
}
