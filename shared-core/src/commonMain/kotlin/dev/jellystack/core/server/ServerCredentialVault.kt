package dev.jellystack.core.server

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue

class ServerCredentialVault(
    private val secureStore: SecureStore,
) {
    suspend fun saveJellyfinPassword(
        serverId: String,
        password: String,
    ) {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.write(primaryKey, secretValue(password))
        legacyJellyfinPasswordKeys(serverId).forEach { key -> secureStore.remove(key) }
    }

    suspend fun readJellyfinPassword(serverId: String): SecretValue? {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.read(primaryKey)?.let { return it }

        legacyJellyfinPasswordKeys(serverId).forEach { legacyKey ->
            val legacySecret = secureStore.read(legacyKey)
            if (legacySecret != null) {
                saveJellyfinPassword(serverId, legacySecret.reveal())
                return legacySecret
            }
        }

        return null
    }

    suspend fun removeJellyfinPassword(serverId: String) {
        val primaryKey = jellyfinPasswordKey(serverId)
        secureStore.remove(primaryKey)
        legacyJellyfinPasswordKeys(serverId).forEach { key -> secureStore.remove(key) }
    }

    private fun jellyfinPasswordKey(serverId: String): String = "servers.$serverId.jellyfin.password"

    private fun legacyJellyfinPasswordKeys(serverId: String): List<String> =
        listOf(
            "servers.$serverId.password",
            "servers.$serverId.jellyfinPassword",
        )
}
