package dev.jellystack.core.config

import dev.jellystack.core.security.SecretValue
import dev.jellystack.core.security.SecureStore
import dev.jellystack.core.security.secretValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConfigRepositoryTest {
    private val store = InMemorySecureStore()
    private val repository = ServerConfigRepository(store)

    @Test
    fun roundTripPersistsSecrets() =
        runTest {
            val config =
                ServerConfig(
                    serverUrl = "https://demo.jellyfin.org",
                    apiKey = secretValue("dummy-api-key"),
                    userId = "user-42",
                )

            repository.save(config)
            val restored = repository.load()

            assertEquals(config.serverUrl, restored?.serverUrl)
            assertEquals(config.apiKey.reveal(), restored?.apiKey?.reveal())
            assertEquals(config.userId, restored?.userId)
        }

    @Test
    fun clearRemovesStoredValues() =
        runTest {
            repository.save(
                ServerConfig(
                    serverUrl = "https://demo",
                    apiKey = secretValue("dummy-token"),
                    userId = null,
                ),
            )

            repository.clear()

            assertNull(repository.load())
        }

    private class InMemorySecureStore : SecureStore {
        private val data = mutableMapOf<String, SecretValue>()

        override suspend fun write(
            key: String,
            value: SecretValue,
        ) {
            data[key] = value
        }

        override suspend fun read(key: String): SecretValue? = data[key]

        override suspend fun remove(key: String) {
            data.remove(key)
        }
    }
}
