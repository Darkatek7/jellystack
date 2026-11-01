package dev.jellystack.core.server

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ActiveServerPreferenceRepository(
    private val settings: Settings,
) {
    private val requestType =
        MutableStateFlow(
            settings
                .getStringOrNull("servers.requestProvider")
                ?.let { runCatching { ServerType.fromStored(it) }.getOrNull() }
                ?.takeIf { it.isRequestProvider } ?: ServerType.JELLYSEERR,
        )

    fun requestProviderType(): ServerType = requestType.value

    fun observeRequestProviderType(): Flow<ServerType> = requestType

    fun selectRequestProvider(type: ServerType) {
        require(type.isRequestProvider)
        settings.putString("servers.requestProvider", type.name)
        requestType.value = type
    }

    private val selections =
        MutableStateFlow(
            ServerType.entries.associateWith(::read),
        )

    fun activeServerId(type: ServerType): String? = selections.value[type]

    fun observeActiveServerId(type: ServerType): Flow<String?> = selections.map { values -> values[type] }.distinctUntilChanged()

    fun setActiveServerId(
        type: ServerType,
        serverId: String?,
    ) {
        val key = key(type)
        if (serverId.isNullOrBlank()) {
            settings.remove(key)
        } else {
            settings.putString(key, serverId)
        }
        selections.value = selections.value + (type to serverId?.takeIf { it.isNotBlank() })
    }

    private fun read(type: ServerType): String? = settings.getStringOrNull(key(type))?.takeIf { it.isNotBlank() }

    private fun key(type: ServerType): String = "servers.active.${type.name.lowercase()}"
}
