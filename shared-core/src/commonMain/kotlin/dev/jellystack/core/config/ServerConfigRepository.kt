package dev.jellystack.core.config

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue

data class ServerConfig(
    val serverUrl: String,
    val apiKey: SecretValue,
    val userId: String?,
)

class ServerConfigRepository(
    private val secureStore: SecureStore,
) {
    suspend fun save(config: ServerConfig) {
        secureStore.write(KEY_BASE_URL, secretValue(config.serverUrl))
        secureStore.write(KEY_API_KEY, config.apiKey)
        if (config.userId != null) {
            secureStore.write(KEY_USER_ID, secretValue(config.userId))
        } else {
            secureStore.remove(KEY_USER_ID)
        }
    }

    suspend fun load(): ServerConfig? {
        val baseUrl = secureStore.read(KEY_BASE_URL)?.reveal() ?: return null
        val apiKey = secureStore.read(KEY_API_KEY) ?: return null
        val userId = secureStore.read(KEY_USER_ID)?.reveal()
        return ServerConfig(baseUrl, apiKey, userId)
    }

    suspend fun clear() {
        secureStore.remove(KEY_BASE_URL)
        secureStore.remove(KEY_API_KEY)
        secureStore.remove(KEY_USER_ID)
    }

    companion object {
        private const val KEY_BASE_URL = "config.server.baseUrl"
        private const val KEY_API_KEY = "config.server.apiKey"
        private const val KEY_USER_ID = "config.server.userId"
    }
}
