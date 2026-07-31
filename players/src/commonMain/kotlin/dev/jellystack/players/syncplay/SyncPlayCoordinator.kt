package dev.jellystack.players.syncplay

import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinSyncPlayApi
import dev.jellystack.network.jellyfin.JellyfinSyncPlayGroupDto
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SyncPlayGroup(
    val id: String,
    val name: String,
    val state: String,
    val participants: List<String>,
)

data class SyncPlayUiState(
    val groups: List<SyncPlayGroup> = emptyList(),
    val currentGroup: SyncPlayGroup? = null,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val playlistItemId: String? = null,
)

class SyncPlayCoordinator(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val playbackController: PlaybackController,
    private val playItem: suspend (itemId: String, startPositionMs: Long) -> Unit,
    private val client: HttpClient =
        NetworkClientFactory.create(
            ClientConfig(
                installLogging = false,
                requestTimeoutMillis = 20_000,
                socketTimeoutMillis = 0,
                configure = { install(WebSockets) },
            ),
        ),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableState = MutableStateFlow(SyncPlayUiState())
    val state: StateFlow<SyncPlayUiState> = mutableState.asStateFlow()

    private var api: JellyfinSyncPlayApi? = null
    private var apiEnvironmentKey: String? = null
    private var socketJob: Job? = null
    private var playbackJob: Job? = null
    private var queuedItemIds: List<String> = emptyList()
    private var playingQueueIndex: Int = 0
    private var lastReportedPhase: PlaybackPhase? = null

    init {
        playbackJob =
            scope.launch {
                playbackController.state.collect { playback ->
                    val active = playback as? PlaybackState.Active ?: return@collect
                    if (state.value.currentGroup == null) return@collect
                    val playlistItemId = state.value.playlistItemId ?: return@collect
                    if (active.phase == lastReportedPhase) return@collect
                    lastReportedPhase = active.phase
                    val whenUtc = Clock.System.now().toString()
                    runCatching {
                        when (active.phase) {
                            PlaybackPhase.Buffering ->
                                api?.buffering(whenUtc, active.positionMs * TICKS_PER_MILLISECOND, !active.isPaused, playlistItemId)
                            PlaybackPhase.Ready ->
                                api?.ready(whenUtc, active.positionMs * TICKS_PER_MILLISECOND, !active.isPaused, playlistItemId)
                            PlaybackPhase.Ended -> Unit
                        }
                    }
                }
            }
    }

    fun refresh() {
        scope.launch { refreshInternal() }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        scope.launch {
            withApi { service ->
                val group = service.createGroup(name)
                setJoinedGroup(group.toDomain())
                refreshInternal()
            }
        }
    }

    fun joinGroup(group: SyncPlayGroup) {
        scope.launch {
            withApi { service ->
                service.joinGroup(group.id)
                setJoinedGroup(group)
            }
        }
    }

    fun leaveGroup() {
        scope.launch {
            runCatching { api?.leaveGroup() }
            clearJoinedGroup()
            refreshInternal()
        }
    }

    fun requestPause() = request { pause() }

    fun requestUnpause() = request { unpause() }

    fun requestSeek(positionMs: Long) = request { seek(positionMs * TICKS_PER_MILLISECOND) }

    fun requestStop() = request { stop() }

    fun requestNext() {
        state.value.playlistItemId?.let { id -> request { next(id) } }
    }

    fun requestPrevious() {
        state.value.playlistItemId?.let { id -> request { previous(id) } }
    }

    fun setCurrentPlaybackAsQueue() {
        val active = playbackController.state.value as? PlaybackState.Active ?: return
        request {
            setNewQueue(
                itemIds = listOf(active.mediaId),
                playingItemPosition = 0,
                startPositionTicks = active.positionMs * TICKS_PER_MILLISECOND,
            )
        }
    }

    fun close() {
        socketJob?.cancel()
        playbackJob?.cancel()
        client.close()
    }

    private fun request(block: suspend JellyfinSyncPlayApi.() -> Unit) {
        if (state.value.currentGroup == null) return
        scope.launch {
            runCatching { api?.block() }.onFailure(::publishError)
        }
    }

    private suspend fun refreshInternal() {
        mutableState.value = state.value.copy(loading = true, error = null)
        withApi { service ->
            val groups = service.groups().map { it.toDomain() }
            val currentId = state.value.currentGroup?.id
            mutableState.value =
                state.value.copy(
                    groups = groups,
                    currentGroup = groups.firstOrNull { it.id == currentId } ?: state.value.currentGroup,
                    loading = false,
                    connected = true,
                )
        }
    }

    private suspend fun withApi(block: suspend (JellyfinSyncPlayApi) -> Unit) {
        try {
            val environment = environmentProvider.current()
                ?: error("No active Jellyfin server")
            val service = apiFor(environment)
            block(service)
            mutableState.value = state.value.copy(loading = false, connected = true, error = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            publishError(failure)
        }
    }

    private fun apiFor(environment: JellyfinEnvironment): JellyfinSyncPlayApi {
        val existing = api
        if (existing != null && apiEnvironmentKey == environment.serverKey) return existing
        socketJob?.cancel()
        clearJoinedGroup()
        return JellyfinSyncPlayApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            deviceId = environment.deviceId ?: "jellystack-android",
        ).also {
            api = it
            apiEnvironmentKey = environment.serverKey
            startSocket(it)
        }
    }

    private fun startSocket(service: JellyfinSyncPlayApi) {
        socketJob?.cancel()
        socketJob =
            scope.launch {
                try {
                    service.events().collect(::handleEvent)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    publishError(failure)
                }
            }
    }

    private fun setJoinedGroup(group: SyncPlayGroup) {
        playbackController.setPlaybackSpeed(1f)
        mutableState.value = state.value.copy(currentGroup = group, connected = true, error = null)
        setCurrentPlaybackAsQueue()
    }

    private fun clearJoinedGroup() {
        queuedItemIds = emptyList()
        playingQueueIndex = 0
        lastReportedPhase = null
        mutableState.value = state.value.copy(currentGroup = null, playlistItemId = null, error = null)
    }

    private fun handleEvent(message: JsonObject) {
        when (message.string("MessageType")) {
            "SyncPlayCommand" -> handleCommand(message.obj("Data"))
            "SyncPlayGroupUpdate" -> handleGroupUpdate(message.obj("Data"))
        }
    }

    private fun handleCommand(data: JsonObject?) {
        data ?: return
        val positionMs = data.long("PositionTicks")?.div(TICKS_PER_MILLISECOND)
        val scheduledAt = data.string("When")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val lateness = scheduledAt?.let { (Clock.System.now() - it).inWholeMilliseconds.coerceAtLeast(0L) } ?: 0L
        data.string("PlaylistItemId")?.let { updatePlaylistItemId(it) }
        when (data.string("Command")) {
            "Pause" -> playbackController.pause()
            "Unpause" -> {
                positionMs?.let { playbackController.seekTo(it + lateness) }
                playbackController.resume()
            }
            "Seek" -> positionMs?.let(playbackController::seekTo)
            "Stop" -> playbackController.stop()
        }
    }

    private fun handleGroupUpdate(data: JsonObject?) {
        data ?: return
        val type = data.string("Type") ?: data.string("UpdateType")
        val payload = data.obj("Data")
        when (type) {
            "GroupJoined" -> payload?.toGroup()?.let(::setJoinedGroup)
            "GroupLeft", "NotInGroup", "GroupDoesNotExist" -> clearJoinedGroup()
            "PlayQueue" -> handlePlayQueue(payload)
            "StateUpdate" -> {
                when (payload?.string("State")) {
                    "Paused", "Waiting" -> playbackController.pause()
                    "Playing" -> playbackController.resume()
                }
            }
        }
    }

    private fun handlePlayQueue(payload: JsonObject?) {
        payload ?: return
        val playlist = payload["Playlist"]?.jsonArray.orEmpty()
        queuedItemIds = playlist.mapNotNull { it.jsonObject.string("ItemId") }
        playingQueueIndex = payload.int("PlayingItemIndex") ?: 0
        val playlistItem = playlist.getOrNull(playingQueueIndex)?.jsonObject
        playlistItem?.string("PlaylistItemId")?.let(::updatePlaylistItemId)
        val itemId = playlistItem?.string("ItemId") ?: return
        val active = playbackController.state.value as? PlaybackState.Active
        if (active?.mediaId != itemId) {
            val startMs = payload.long("StartPositionTicks")?.div(TICKS_PER_MILLISECOND) ?: 0L
            scope.launch { playItem(itemId, startMs) }
        }
    }

    private fun updatePlaylistItemId(id: String) {
        mutableState.value = state.value.copy(playlistItemId = id)
    }

    private fun publishError(failure: Throwable) {
        mutableState.value = state.value.copy(loading = false, error = failure.message ?: "SyncPlay connection failed")
    }

    private fun JellyfinSyncPlayGroupDto.toDomain(): SyncPlayGroup =
        SyncPlayGroup(groupId, groupName, state, participants)

    private fun JsonObject.toGroup(): SyncPlayGroup? {
        val id = string("GroupId") ?: return null
        return SyncPlayGroup(
            id = id,
            name = string("GroupName") ?: "SyncPlay",
            state = string("State") ?: "Idle",
            participants = get("Participants")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
        )
    }

    private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()
    private fun JsonObject.int(name: String): Int? = string(name)?.toIntOrNull()
    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.let { runCatching { it.jsonObject }.getOrNull() }

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L
    }
}
