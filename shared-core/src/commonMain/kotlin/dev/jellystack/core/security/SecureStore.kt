package dev.jellystack.core.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SecureStore {
    suspend fun write(
        key: String,
        value: SecretValue,
    )

    suspend fun read(key: String): SecretValue?

    suspend fun remove(key: String)
}

class SecureStoreImpl(
    private val engine: SecureStoreEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val logger: SecureStoreLogger,
) : SecureStore {
    override suspend fun write(
        key: String,
        value: SecretValue,
    ) {
        execute(
            action = SecureStoreAction.WRITE,
            key = key,
            onSuccess = { logger.onSuccess(SecureStoreAction.WRITE, key, value.toString()) },
        ) {
            engine.write(key, value.reveal())
        }
    }

    override suspend fun read(key: String): SecretValue? =
        execute(
            action = SecureStoreAction.READ,
            key = key,
            onSuccess = { result ->
                val redacted = (result as SecretValue?)?.toString()
                logger.onSuccess(SecureStoreAction.READ, key, redacted)
            },
        ) {
            engine.read(key)?.let(::secretValue)
        }

    override suspend fun remove(key: String) {
        execute(
            action = SecureStoreAction.REMOVE,
            key = key,
            onSuccess = { logger.onSuccess(SecureStoreAction.REMOVE, key, null) },
        ) {
            engine.delete(key)
        }
    }

    private suspend fun <T> execute(
        action: SecureStoreAction,
        key: String,
        onSuccess: (T) -> Unit,
        block: suspend () -> T,
    ): T =
        withContext(dispatcher) {
            runCatching { block() }
                .onSuccess { result -> return@withContext result.also(onSuccess) }
                .getOrElse { throwable ->
                    logger.onFailure(action, key, throwable)
                    throw SecureStoreException(action, key, throwable)
                }
        }
}

enum class SecureStoreAction {
    WRITE,
    READ,
    REMOVE,
}

class SecureStoreException(
    val action: SecureStoreAction,
    val key: String,
    cause: Throwable,
) : RuntimeException("SecureStore ${action.name.lowercase()} failed for key '$key'", cause)
