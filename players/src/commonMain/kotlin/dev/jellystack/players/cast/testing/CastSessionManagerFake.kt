package dev.jellystack.players.cast.testing

import dev.jellystack.players.cast.CastConnectionState
import dev.jellystack.players.cast.CastSessionManager
import dev.jellystack.players.cast.CastSessionSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Lightweight in-memory implementation of [CastSessionManager] that allows tests to assert
 * connection-state transitions and the commands routed to the cast layer.
 *
 * The fake keeps track of the most recent connected snapshot so that tests can exercise reconnect
 * flows without having to rebuild complicated playback state.
 */
class CastSessionManagerFake(
    initialState: CastConnectionState = CastConnectionState.Idle,
) : CastSessionManager {
    private val stateFlow =
        MutableSharedFlow<CastConnectionState>(replay = 1).apply {
            tryEmit(initialState)
        }
    private val progressFlow =
        MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 16)

    override val connectionState: SharedFlow<CastConnectionState> = stateFlow
    override val remoteProgress: SharedFlow<Long> = progressFlow

    private var lastDeviceName: String? =
        (initialState as? CastConnectionState.Connected)?.deviceName
    private var lastSnapshot: CastSessionSnapshot? =
        (initialState as? CastConnectionState.Connected)?.snapshot

    val commands: MutableList<Command> = mutableListOf()

    sealed interface Command {
        data object Play : Command

        data object Pause : Command

        data class Seek(
            val positionMs: Long,
        ) : Command

        data object Stop : Command

        data class SelectSubtitleTrack(
            val trackId: String?,
        ) : Command

        data object Disconnect : Command
    }

    override suspend fun play() {
        commands += Command.Play
    }

    override suspend fun pause() {
        commands += Command.Pause
    }

    override suspend fun seek(positionMs: Long) {
        commands += Command.Seek(positionMs)
    }

    override suspend fun stop() {
        commands += Command.Stop
    }

    override suspend fun selectSubtitleTrack(trackId: String?) {
        commands += Command.SelectSubtitleTrack(trackId)
        val current = (stateFlow.replayCache.lastOrNull() as? CastConnectionState.Connected) ?: return
        emitState(
            current.copy(
                snapshot = current.snapshot.copy(selectedSubtitleTrackId = trackId),
            ),
        )
    }

    override suspend fun disconnect() {
        commands += Command.Disconnect
        emitState(CastConnectionState.Idle)
    }

    suspend fun connecting(deviceName: String? = lastDeviceName) {
        emitState(CastConnectionState.Connecting(deviceName))
    }

    suspend fun connected(
        deviceName: String,
        snapshot: CastSessionSnapshot,
    ) {
        emitState(CastConnectionState.Connected(deviceName, snapshot))
    }

    suspend fun drop(cause: Throwable? = null) {
        if (lastSnapshot != null) {
            emitState(CastConnectionState.Error(cause))
        } else {
            emitState(CastConnectionState.Idle)
        }
    }

    suspend fun reconnect(): Boolean {
        val snapshot = lastSnapshot ?: return false
        val deviceName = lastDeviceName ?: "Cast device"
        emitState(CastConnectionState.Connecting(deviceName))
        emitState(CastConnectionState.Connected(deviceName, snapshot))
        return true
    }

    suspend fun emitState(state: CastConnectionState) {
        when (state) {
            is CastConnectionState.Connected -> {
                lastDeviceName = state.deviceName
                lastSnapshot = state.snapshot
            }
            CastConnectionState.Idle -> {
                lastDeviceName = null
                lastSnapshot = null
            }
            else -> Unit
        }
        stateFlow.emit(state)
    }

    suspend fun emitProgress(positionMs: Long) {
        progressFlow.emit(positionMs)
    }

    fun clearCommands() {
        commands.clear()
    }

    fun lastSnapshot(): CastSessionSnapshot? = lastSnapshot

    fun lastDeviceName(): String? = lastDeviceName
}
