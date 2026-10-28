package dev.jellystack.players.syncplay

import dev.jellystack.core.coroutines.runSuspendCatching
import dev.jellystack.core.jellyfin.JellyfinEnvironment
import dev.jellystack.core.jellyfin.JellyfinEnvironmentProvider
import dev.jellystack.core.jellyfin.JellyfinSyncPlayAccess
import dev.jellystack.core.logging.JellystackLog
import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinClientIdentity
import dev.jellystack.network.jellyfin.JellyfinSyncPlayApi
import dev.jellystack.network.jellyfin.JellyfinSyncPlayException
import dev.jellystack.network.jellyfin.JellyfinSyncPlayFailure
import dev.jellystack.network.jellyfin.JellyfinSyncPlayGroupDto
import dev.jellystack.players.PlaybackController
import dev.jellystack.players.PlaybackPhase
import dev.jellystack.players.PlaybackState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    val error: SyncPlayErrorCode? = null,
    val playlistItemId: String? = null,
    val access: JellyfinSyncPlayAccess = JellyfinSyncPlayAccess.NONE,
) {
    val canJoin: Boolean
        get() = access != JellyfinSyncPlayAccess.NONE

    val canCreate: Boolean
        get() = access == JellyfinSyncPlayAccess.CREATE_AND_JOIN_GROUPS
}

enum class SyncPlayErrorCode {
    ACCESS_DENIED,
    UNAUTHORIZED,
    NETWORK,
    INVALID_RESPONSE,
}

class SyncPlayCoordinator(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val playbackController: PlaybackController,
    private val playItem: suspend (itemId: String, startPositionMs: Long) -> Unit,
    private val client: HttpClient =
        NetworkClientFactory.create(
            ClientConfig(
                installLogging = false,
                requestTimeoutMillis = 20_000,
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
                configure = { install(WebSockets) },
            ),
        ),
    private val onAccessDenied: suspend () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val socketEvents: (JellyfinSyncPlayApi) -> Flow<JsonObject> = JellyfinSyncPlayApi::events,
) {
    private val mutableState = MutableStateFlow(SyncPlayUiState())
    val state: StateFlow<SyncPlayUiState> = mutableState.asStateFlow()

    private var api: JellyfinSyncPlayApi? = null
    private var apiEnvironmentKey: String? = null
    private var apiAccessToken: String? = null
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
        if (!state.value.canJoin) return
        scope.launch { refreshInternal() }
    }

    fun updateAccess(access: JellyfinSyncPlayAccess) {
        if (state.value.access == access) return
        if (access == JellyfinSyncPlayAccess.NONE) {
            socketJob?.cancel()
            socketJob = null
            api = null
            apiEnvironmentKey = null
            apiAccessToken = null
            clearJoinedGroup()
        }
        mutableState.value =
            state.value.copy(
                access = access,
                groups = if (access == JellyfinSyncPlayAccess.NONE) emptyList() else state.value.groups,
                connected = if (access == JellyfinSyncPlayAccess.NONE) false else state.value.connected,
                loading = false,
                error = null,
            )
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        if (!state.value.canCreate) {
            publishError(SyncPlayErrorCode.ACCESS_DENIED)
            return
        }
        scope.launch {
            withApi { service ->
                val group = service.createGroup(name)
                setJoinedGroup(group.toDomain())
                refreshInternal()
            }
        }
    }

    fun joinGroup(group: SyncPlayGroup) {
        if (!state.value.canJoin) {
            publishError(SyncPlayErrorCode.ACCESS_DENIED)
            return
        }
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

    private suspend fun refreshInternal(dropMissingGroup: Boolean = false) {
        mutableState.value = state.value.copy(loading = true, error = null)
        withApi { service ->
            val groups = service.groups().map { it.toDomain() }
            val currentId = state.value.currentGroup?.id
            // After an outage the server may have dropped this client from its group.
            if (dropMissingGroup && currentId != null && groups.none { it.id == currentId }) clearJoinedGroup()
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
            val environment =
                environmentProvider.current()
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
        if (existing != null && apiEnvironmentKey == environment.serverKey) {
            ensureSocket()
            return existing
        }
        socketJob?.cancel()
        clearJoinedGroup()
        return createApi(environment).also {
            api = it
            apiEnvironmentKey = environment.serverKey
            apiAccessToken = environment.accessToken
            startSocket(it)
        }
    }

    private fun createApi(environment: JellyfinEnvironment) =
        JellyfinSyncPlayApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            deviceId = environment.deviceId ?: "jellystack-android",
            identity =
                JellyfinClientIdentity(
                    appVersion = environment.clientVersion,
                    deviceName = environment.deviceName,
                    deviceId = environment.deviceId ?: "unknown",
                ),
        )

    /** Starts a socket again when the last one ended while no group was joined. */
    private fun ensureSocket() {
        if (socketJob?.isActive == true) return
        api?.let(::startSocket)
    }

    private fun startSocket(service: JellyfinSyncPlayApi) {
        socketJob?.cancel()
        socketJob = scope.launch { superviseSocket(service) }
    }

    /**
     * Keeps the socket open. While a group is joined, a dropped socket (Wi-Fi loss, sleep, server restart) is opened
     * again with [SyncPlayReconnectBackoff]; without a group the supervisor ends and the next group action starts a
     * new socket. It also ends when the environment changes, the group is left or the coordinator is closed.
     */
    private suspend fun superviseSocket(first: JellyfinSyncPlayApi) {
        var service = first
        var failedAttempts = 0
        var reconnecting = false
        while (true) {
            var opened = false
            try {
                socketEvents(service).collect { message ->
                    if (!opened) {
                        opened = true
                        failedAttempts = 0
                        if (reconnecting) onSocketRestored()
                    }
                    handleEvent(message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                JellystackLog.w("SyncPlay socket dropped", failure)
                publishError(failure)
            }
            if (state.value.currentGroup == null) return
            mutableState.value = state.value.copy(connected = false)
            delay(SyncPlayReconnectBackoff.delayMillis(failedAttempts))
            failedAttempts += 1
            if (state.value.currentGroup == null) return
            service = currentApiForReconnect() ?: return
            reconnecting = true
        }
    }

    private suspend fun currentApiForReconnect(): JellyfinSyncPlayApi? {
        val environment =
            try {
                environmentProvider.current()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return null
        if (environment.serverKey != apiEnvironmentKey) return null
        if (environment.accessToken != apiAccessToken) {
            apiAccessToken = environment.accessToken
            api = createApi(environment)
        }
        return api
    }

    private fun onSocketRestored() {
        scope.launch { refreshInternal(dropMissingGroup = true) }
    }

    private fun setJoinedGroup(group: SyncPlayGroup) {
        playbackController.setPlaybackSpeed(1f)
        mutableState.value = state.value.copy(currentGroup = group, connected = true, error = null)
        ensureSocket()
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
            scope.launch { runSuspendCatching { playItem(itemId, startMs) }.onFailure(::publishError) }
        }
    }

    private fun updatePlaylistItemId(id: String) {
        mutableState.value = state.value.copy(playlistItemId = id)
    }

    private fun publishError(failure: Throwable) {
        val error =
            when (failure) {
                is JellyfinSyncPlayException ->
                    when (failure.failure) {
                        JellyfinSyncPlayFailure.ACCESS_DENIED -> SyncPlayErrorCode.ACCESS_DENIED
                        JellyfinSyncPlayFailure.UNAUTHORIZED -> SyncPlayErrorCode.UNAUTHORIZED
                        JellyfinSyncPlayFailure.INVALID_RESPONSE -> SyncPlayErrorCode.INVALID_RESPONSE
                    }
                else -> SyncPlayErrorCode.NETWORK
            }
        publishError(error)
    }

    private fun publishError(error: SyncPlayErrorCode) {
        mutableState.value = state.value.copy(loading = false, connected = false, error = error)
        if (error == SyncPlayErrorCode.ACCESS_DENIED) {
            scope.launch { onAccessDenied() }
        }
    }

    private fun JellyfinSyncPlayGroupDto.toDomain(): SyncPlayGroup = SyncPlayGroup(groupId, groupName, state, participants)

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

/** Waits between attempts to open the SyncPlay socket again: 1 s, doubling, capped at 30 s. */
internal object SyncPlayReconnectBackoff {
    private const val INITIAL_DELAY_MILLIS = 1_000L
    private const val MAX_DELAY_MILLIS = 30_000L
    private const val MAX_DOUBLINGS = 5

    /** The wait before the reconnect attempt that follows [failedAttempts] consecutive failures (0 for the first). */
    fun delayMillis(failedAttempts: Int): Long =
        (INITIAL_DELAY_MILLIS shl failedAttempts.coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(MAX_DELAY_MILLIS)
}
