package dev.jellystack.design.tv

import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.media3.common.util.UnstableApi
import dev.jellystack.core.jellyfin.DetailTrailerResolver
import dev.jellystack.core.jellyfin.HomeSectionsState
import dev.jellystack.core.jellyfin.JellyfinBrowseCoordinator
import dev.jellystack.core.jellyfin.JellyfinBrowseRepository
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinHomeState
import dev.jellystack.core.jellyfin.JellyfinItem
import dev.jellystack.core.jellyfin.JellyfinPerson
import dev.jellystack.core.jellyseerr.JellyseerrMediaDetailState
import dev.jellystack.core.jellyseerr.JellyseerrMediaType
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRecommendationsState
import dev.jellystack.core.jellyseerr.JellyseerrRequestsCoordinator
import dev.jellystack.core.jellyseerr.JellyseerrRequestsState
import dev.jellystack.core.jellyseerr.JellyseerrSearchItem
import dev.jellystack.core.preferences.AppSettings
import dev.jellystack.core.preferences.AppSettingsRepository
import dev.jellystack.core.profile.HouseholdProfileRepository
import dev.jellystack.core.profile.MyListEntry
import dev.jellystack.core.profile.ProfileMyListRepository
import dev.jellystack.core.profile.ProfilePreferencesRepository
import dev.jellystack.core.profile.SavedMediaRecord
import dev.jellystack.core.server.ServerRepository
import dev.jellystack.players.AndroidPlayerEngine
import dev.jellystack.players.PlaybackContinuationState
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackExtrasState
import dev.jellystack.players.PlaybackQueue
import dev.jellystack.players.PlaybackSegment
import dev.jellystack.players.PlaybackSegmentState
import dev.jellystack.players.PlaybackState
import dev.jellystack.players.syncplay.SyncPlayCoordinator
import dev.jellystack.players.syncplay.SyncPlayUiState
import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin

/**
 * Everything the per-route content of the signed-in TV app reads, grouped so that each route composable takes one
 * object. [TvJellystackRoot] builds it once per composition; the route files only read from it.
 */
internal class TvRouteContext(
    val ui: TvRouteUi,
    val nav: TvRouteNavigation,
    val data: TvRouteData,
    val coordinators: TvRouteCoordinators,
    val services: TvRouteServices,
) {
    val playback: TvRoutePlayback get() = data.playback
}

/** Presentation inputs shared by every route. */
internal class TvRouteUi(
    val strings: TvStrings,
    val focusMemory: TvFocusMemory,
    val settings: AppSettings,
    val appVersion: String,
    val profile: TvRouteProfile,
)

/** The profile the signed-in app runs for. */
internal class TvRouteProfile(
    val activeProfileId: String?,
    val myListProfileId: String,
    val watchNextAvailable: Boolean,
)

/** Navigation actions of the signed-in app; each one clears the trailer preview focus where the root did. */
internal class TvRouteNavigation(
    val push: (TvRoute) -> Unit,
    val openLibrary: (libraryId: String, title: String?) -> Unit,
    val openJellyfinDetail: (JellyfinItem) -> Unit,
    val openPerson: (JellyfinPerson) -> Unit,
    val openSeerr: (JellyseerrSearchItem) -> Unit,
) {
    fun openSeerrRoute(route: TvRoute.SeerrDetail) {
        push(route)
    }

    fun openSettingsConnections() {
        push(tvConnectionsSettingsRoute())
    }
}

/** Observed state the routes render. */
internal class TvRouteData(
    val browse: TvRouteBrowseData,
    val seerr: TvRouteSeerrData,
    val trailer: TvRouteTrailer,
    val player: TvRoutePlayerState,
    val playback: TvRoutePlayback,
)

internal class TvRouteBrowseData(
    val homeState: JellyfinHomeState,
    val homeSections: HomeSectionsState,
    val myList: List<MyListEntry>,
    val savedMedia: List<SavedMediaRecord>,
    val lists: TvRouteListData,
)

/** Item-list and Live TV state, read by one route each. */
internal class TvRouteListData(
    val itemListStates: Map<String, TvItemListState>,
    val liveTv: TvLiveTvAccess,
)

internal class TvRouteSeerrData(
    val recommendations: JellyseerrRecommendationsState,
    val requests: JellyseerrRequestsState,
    val details: Map<Pair<JellyseerrMediaType, Int>, JellyseerrMediaDetailState>,
    val searchState: TvSearchUiState,
)

/** Trailer preview state plus the card-focus hook the cinematic browse modes call. */
@OptIn(UnstableApi::class)
internal class TvRouteTrailer(
    val state: TvTrailerPreviewState,
    val engine: AndroidPlayerEngine,
    val progress: State<Float>,
    val coordinator: TvTrailerPreviewController,
    val hooks: TvRouteTrailerHooks,
)

/** What the routes need to start trailer previews and resolve detail trailers. */
internal class TvRouteTrailerHooks(
    val serverKey: String?,
    val currentRoute: TvRoute,
    val focusCinematicTrailer: (item: JellyfinItem, presentationId: String) -> Unit,
    val detailResolver: DetailTrailerResolver,
)

/** Playback state read by the player route only. */
internal class TvRoutePlayerState(
    val stateHolder: State<PlaybackState>,
    val syncPlayState: SyncPlayUiState,
    val continuationState: PlaybackContinuationState,
    val extrasState: PlaybackExtrasState,
    val segments: TvRouteSegments,
)

/** The skippable segments of the playing item and the timeline markers. */
internal class TvRouteSegments(
    val state: PlaybackSegmentState,
    val timeline: List<PlaybackSegment>,
)

internal class TvRouteCoordinators(
    val browse: JellyfinBrowseCoordinator,
    val recommendations: JellyseerrRecommendationsCoordinator,
    val requests: JellyseerrRequestsCoordinator,
    val search: TvSearchCoordinator,
    val itemLists: TvItemListCoordinator,
)

internal class TvRouteServices(
    val repositories: TvRouteRepositories,
    val scope: CoroutineScope,
    val koin: Koin,
    val appStateHolder: TvAppStateHolder,
    val environmentProvider: JellyfinEnvironmentProvider,
)

internal class TvRouteRepositories(
    val browse: JellyfinBrowseRepository,
    val myList: ProfileMyListRepository,
    val settings: AppSettingsRepository,
    val server: ServerRepository,
    val profiles: TvRouteProfileRepositories,
)

/** The profile-scoped repositories. */
internal class TvRouteProfileRepositories(
    val preferences: ProfilePreferencesRepository,
    val household: HouseholdProfileRepository,
)

@OptIn(UnstableApi::class)
internal class TvRoutePlayback(
    val controller: PlaybackController,
    val launcher: TvPlaybackLauncher,
    val syncPlay: SyncPlayCoordinator,
    val commandRouter: TvPlaybackCommandRouter,
    val extras: TvRoutePlaybackExtras,
)

@OptIn(UnstableApi::class)
internal class TvRoutePlaybackExtras(
    val coordinators: TvPlaybackCoordinators,
    val queue: PlaybackQueue,
    val stopPlayback: () -> Unit,
    val engine: AndroidPlayerEngine,
)
