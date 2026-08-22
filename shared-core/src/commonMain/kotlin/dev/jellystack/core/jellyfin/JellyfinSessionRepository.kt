package dev.jellystack.core.jellyfin

import dev.jellystack.network.ClientConfig
import dev.jellystack.network.NetworkClientFactory
import dev.jellystack.network.jellyfin.JellyfinActivityEntryDto
import dev.jellystack.network.jellyfin.JellyfinSessionApi
import dev.jellystack.network.jellyfin.JellyfinUserDto
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class JellyfinSyncPlayAccess {
    NONE,
    JOIN_GROUPS,
    CREATE_AND_JOIN_GROUPS,
}

data class JellyfinSessionCapabilities(
    val userId: String? = null,
    val username: String? = null,
    val isAdministrator: Boolean = false,
    val syncPlayAccess: JellyfinSyncPlayAccess = JellyfinSyncPlayAccess.NONE,
) {
    val canJoinSyncPlay: Boolean
        get() = syncPlayAccess != JellyfinSyncPlayAccess.NONE

    val canCreateSyncPlay: Boolean
        get() = syncPlayAccess == JellyfinSyncPlayAccess.CREATE_AND_JOIN_GROUPS

    companion object {
        val NONE = JellyfinSessionCapabilities()
    }
}

sealed interface JellyfinSessionState {
    data object Disconnected : JellyfinSessionState

    data object Loading : JellyfinSessionState

    data class Ready(
        val capabilities: JellyfinSessionCapabilities,
    ) : JellyfinSessionState

    data class Error(
        val message: String,
    ) : JellyfinSessionState
}

typealias JellyfinSessionApiFactory = (JellyfinEnvironment) -> JellyfinSessionApi

class JellyfinSessionRepository(
    private val environmentProvider: JellyfinEnvironmentProvider,
    private val apiFactory: JellyfinSessionApiFactory,
) {
    private val mutableState = MutableStateFlow<JellyfinSessionState>(JellyfinSessionState.Disconnected)
    val state: StateFlow<JellyfinSessionState> = mutableState.asStateFlow()

    suspend fun refresh(): JellyfinSessionCapabilities {
        val environment = environmentProvider.current()
        if (environment == null) {
            mutableState.value = JellyfinSessionState.Disconnected
            return JellyfinSessionCapabilities.NONE
        }
        mutableState.value = JellyfinSessionState.Loading
        return try {
            val user = apiFactory(environment).currentUser()
            user.toCapabilities().also { mutableState.value = JellyfinSessionState.Ready(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            mutableState.value = JellyfinSessionState.Error(failure.message ?: "Unable to load Jellyfin user policy")
            JellyfinSessionCapabilities.NONE
        }
    }

    suspend fun api(): JellyfinSessionApi? = environmentProvider.current()?.let(apiFactory)
}

data class JellyfinAdminOverview(
    val serverName: String?,
    val version: String?,
    val operatingSystem: String?,
    val counts: JellyfinAdminCounts,
)

data class JellyfinAdminCounts(
    val movies: Int = 0,
    val series: Int = 0,
    val episodes: Int = 0,
    val albums: Int = 0,
    val songs: Int = 0,
    val artists: Int = 0,
    val books: Int = 0,
)

data class JellyfinAdminUser(
    val id: String,
    val name: String,
    val isAdministrator: Boolean,
    val isDisabled: Boolean,
    val lastActivityDate: String?,
)

data class JellyfinActivityEntry(
    val id: Long,
    val name: String,
    val overview: String?,
    val type: String?,
    val date: String?,
    val userId: String?,
    val severity: String?,
)

internal fun JellyfinUserDto.toCapabilities(): JellyfinSessionCapabilities =
    JellyfinSessionCapabilities(
        userId = id,
        username = name,
        isAdministrator = policy.isAdministrator,
        syncPlayAccess =
            when (policy.syncPlayAccess.lowercase()) {
                "createandjoingroups" -> JellyfinSyncPlayAccess.CREATE_AND_JOIN_GROUPS
                "joingroups" -> JellyfinSyncPlayAccess.JOIN_GROUPS
                else -> JellyfinSyncPlayAccess.NONE
            },
    )

internal fun JellyfinUserDto.toAdminUser(): JellyfinAdminUser =
    JellyfinAdminUser(
        id = id,
        name = name,
        isAdministrator = policy.isAdministrator,
        isDisabled = policy.isDisabled,
        lastActivityDate = lastActivityDate,
    )

internal fun JellyfinActivityEntryDto.toDomain(): JellyfinActivityEntry =
    JellyfinActivityEntry(
        id = id,
        name = name,
        overview = shortOverview,
        type = type,
        date = date,
        userId = userId,
        severity = severity,
    )

fun defaultJellyfinSessionApiFactory(
    clientProvider: () -> HttpClient = {
        NetworkClientFactory.create(ClientConfig(installLogging = false))
    },
): JellyfinSessionApiFactory {
    val client by lazy(clientProvider)
    return { environment ->
        JellyfinSessionApi(
            client = client,
            baseUrl = environment.baseUrl,
            accessToken = environment.accessToken,
            deviceId = environment.deviceId,
            clientVersion = environment.clientVersion,
        )
    }
}
