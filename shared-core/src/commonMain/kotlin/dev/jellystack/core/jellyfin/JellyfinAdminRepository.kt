package dev.jellystack.core.jellyfin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class JellyfinAdminOperation {
    REFRESH,
    LIBRARY_SCAN,
    RESTART,
    CREATE_USER,
    UPDATE_USER,
    RESET_PASSWORD,
    DELETE_USER,
}

enum class JellyfinAdminNotice {
    LIBRARY_SCAN_STARTED,
    RESTART_REQUESTED,
    USER_CREATED,
    USER_UPDATED,
    PASSWORD_RESET,
    USER_DELETED,
}

data class JellyfinAdminState(
    val overview: JellyfinAdminOverview? = null,
    val users: List<JellyfinAdminUser> = emptyList(),
    val activity: List<JellyfinActivityEntry> = emptyList(),
    val lastRefreshedAt: Instant? = null,
    val operation: JellyfinAdminOperation? = null,
    val notice: JellyfinAdminNotice? = null,
    val error: String? = null,
) {
    val isLoading: Boolean
        get() = operation != null
}

class JellyfinAdminRepository(
    private val sessionRepository: JellyfinSessionRepository,
) {
    private val mutableState = MutableStateFlow(JellyfinAdminState())
    val state: StateFlow<JellyfinAdminState> = mutableState.asStateFlow()

    suspend fun refresh() {
        mutableState.value = state.value.copy(operation = JellyfinAdminOperation.REFRESH, error = null)
        try {
            val api = sessionRepository.api() ?: error("No active Jellyfin server")
            val loaded =
                supervisorScope {
                    val system = async { api.systemInfo() }
                    val counts = async { api.itemCounts() }
                    val users = async { api.users() }
                    val activity = async { api.activity(startIndex = 0, limit = 40) }
                    JellyfinAdminState(
                        overview =
                            system.await().let { server ->
                                counts.await().let { itemCounts ->
                                    JellyfinAdminOverview(
                                        serverName = server.serverName,
                                        version = server.version,
                                        operatingSystem = server.operatingSystem,
                                        counts =
                                            JellyfinAdminCounts(
                                                movies = itemCounts.movieCount,
                                                series = itemCounts.seriesCount,
                                                episodes = itemCounts.episodeCount,
                                                albums = itemCounts.albumCount,
                                                songs = itemCounts.songCount,
                                                artists = itemCounts.artistCount,
                                                books = itemCounts.bookCount,
                                            ),
                                    )
                                }
                            },
                        users = users.await().map { it.toAdminUser() }.sortedBy { it.name.lowercase() },
                        activity =
                            activity
                                .await()
                                .items
                                .map { it.toDomain() }
                                .sortedByDescending { it.date.orEmpty() },
                        lastRefreshedAt = Clock.System.now(),
                    )
                }
            mutableState.value = loaded.copy(notice = state.value.notice)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableState.value =
                state.value.copy(
                    operation = null,
                    error = failure.message ?: "Unable to load administrator data",
                )
        }
    }

    suspend fun startLibraryScan() =
        perform(JellyfinAdminOperation.LIBRARY_SCAN, JellyfinAdminNotice.LIBRARY_SCAN_STARTED) {
            refreshLibrary()
        }

    suspend fun restartServer() =
        perform(JellyfinAdminOperation.RESTART, JellyfinAdminNotice.RESTART_REQUESTED, refreshAfter = false) {
            restartServer()
        }

    suspend fun createUser(
        name: String,
        password: String,
    ) = perform(JellyfinAdminOperation.CREATE_USER, JellyfinAdminNotice.USER_CREATED) {
        require(name.isNotBlank()) { "Username is required" }
        createUser(name.trim(), password.takeIf { it.isNotBlank() })
    }

    suspend fun setUserDisabled(
        userId: String,
        disabled: Boolean,
    ) = perform(JellyfinAdminOperation.UPDATE_USER, JellyfinAdminNotice.USER_UPDATED) {
        setUserDisabled(userId, disabled)
    }

    suspend fun resetPassword(
        userId: String,
        password: String,
    ) = perform(JellyfinAdminOperation.RESET_PASSWORD, JellyfinAdminNotice.PASSWORD_RESET) {
        resetUserPassword(userId, password)
    }

    suspend fun deleteUser(userId: String) =
        perform(JellyfinAdminOperation.DELETE_USER, JellyfinAdminNotice.USER_DELETED) {
            deleteUser(userId)
        }

    fun clearFeedback() {
        mutableState.value = state.value.copy(notice = null, error = null)
    }

    private suspend fun perform(
        operation: JellyfinAdminOperation,
        notice: JellyfinAdminNotice,
        refreshAfter: Boolean = true,
        block: suspend dev.jellystack.network.jellyfin.JellyfinSessionApi.() -> Unit,
    ) {
        if (state.value.operation != null) return
        mutableState.value = state.value.copy(operation = operation, notice = null, error = null)
        try {
            val api = sessionRepository.api() ?: error("No active Jellyfin server")
            api.block()
            mutableState.value = state.value.copy(operation = null, notice = notice)
            if (refreshAfter) refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableState.value =
                state.value.copy(
                    operation = null,
                    error = failure.message ?: "Administrator action failed",
                )
        }
    }
}
